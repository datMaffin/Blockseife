package de.unistuttgart.iaas.stud.blockseife.actor.pipeline

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Timers}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import de.unistuttgart.iaas.stud.blockseife.Data.{RawDomain, RawProblem, SolverOutput, Step}
import de.unistuttgart.iaas.stud.blockseife.actor.collector.DefaultCollector
import de.unistuttgart.iaas.stud.blockseife.actor.collector
import de.unistuttgart.iaas.stud.blockseife.actor.pipeline.Pipeline.Settings
import de.unistuttgart.iaas.stud.blockseife.actor.planner
import de.unistuttgart.iaas.stud.blockseife.actor.planner.{PostDPInstantlyPlanner, PostDPWithSolvePlanner}
import de.unistuttgart.iaas.stud.blockseife.actor.scheduler.{IntervalScheduler, PreconditionCheckingScheduler}
import de.unistuttgart.iaas.stud.blockseife.actor.{planner, scheduler}
import de.unistuttgart.iaas.stud.blockseife.parser.ParsedDomain

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import de.unistuttgart.iaas.stud.blockseife.actor.planner.CorrectIds
import de.unistuttgart.iaas.stud.blockseife.actor.planner.IncorrectDomainAndProblemId
import de.unistuttgart.iaas.stud.blockseife.actor.planner.IncorrectDomainId
import de.unistuttgart.iaas.stud.blockseife.actor.planner.IncorrectProblemId
import de.unistuttgart.iaas.stud.blockseife.actor.planner.WaitingForSolverResponse
import de.unistuttgart.iaas.stud.blockseife.actor.planner.MissingDomain
import de.unistuttgart.iaas.stud.blockseife.actor.planner.MissingProblem
import de.unistuttgart.iaas.stud.blockseife.actor.planner.SuccessfulSolverResponse
import de.unistuttgart.iaas.stud.blockseife.actor.planner.NoSuccessfulResponseOccurred
import de.unistuttgart.iaas.stud.blockseife.actor.planner.MissingDomainAndProblem
import de.unistuttgart.iaas.stud.blockseife.actor.pipeline.Pipeline.UseIntervalScheduler
import de.unistuttgart.iaas.stud.blockseife.actor.pipeline.Pipeline.UsePreconditionCheckingScheduler
import de.unistuttgart.iaas.stud.blockseife.actor.scheduler.FinishedRunning
import de.unistuttgart.iaas.stud.blockseife.actor.scheduler.NotRunning
import de.unistuttgart.iaas.stud.blockseife.actor.scheduler.Running

object Pipeline {
  def props(pipelineSettings: Settings): Props =
    Props(new Pipeline(pipelineSettings))

  /** Settings for the planner execution pipeline actor to create a corresponding pipeline
    *
    * @param collectorSetting the setting for the collector
    * @param plannerSetting the setting for the planner
    * @param schedulerSetting the setting for the scheduler
    * @param toProblemConverter a function that takes the unedited goals, parsed domain and the collector state to create the problem for the solver
    * @param toStepsConverter a function that takes the byte string of a given content type (from the http response entity answer of the solver) and creates a corresponding source of steps
    * @param domainParser a function that takes a planning domain and parses it
    */
  final case class Settings(
      collectorSetting: WhichCollector,
      plannerSetting: WhichPlanner,
      schedulerSetting: WhichScheduler,
      toProblemConverter: (Goal, ParsedDomain, CollectorActor) => Future[RawProblem],
      toStepsConverter: SolverOutput => Source[Step, Any],
      domainParser: RawDomain => Future[ParsedDomain]
  )

  type CollectorActor = ActorRef

  sealed trait WhichCollector
  final case class UseDefaultCollector(s: DefaultCollector.Settings) extends WhichCollector

  sealed trait WhichPlanner
  final case class UsePostDPInstantlyPlanner(plannerSettings: PostDPInstantlyPlanner.Settings) extends WhichPlanner
  final case class UsePostDPWithSolvePlanner(plannerSettings: PostDPWithSolvePlanner.Settings) extends WhichPlanner

  sealed trait WhichScheduler
  final case class UseIntervalScheduler(s: IntervalScheduler.Settings)                         extends WhichScheduler
  final case class UsePreconditionCheckingScheduler(s: PreconditionCheckingScheduler.Settings) extends WhichScheduler

  // Intern messages
  case object NextPoll
}

class Pipeline(pipelineSettings: Settings) extends Actor with Timers with ActorLogging {
  // TODO: Polling not yet identical to description in paper
  // TODO: Error handling

  import Pipeline._

  private var collectorActor: ActorRef              = _
  private var plannerActor: ActorRef                = _
  private var maybeSchedulerActor: Option[ActorRef] = None
  private val whichScheduler                        = pipelineSettings.schedulerSetting

  private val toProblemConverter = pipelineSettings.toProblemConverter
  private val toStepConverter    = pipelineSettings.toStepsConverter

  private val domainParser = pipelineSettings.domainParser

  private var maybeRawDomain: Option[RawDomain]                         = None
  private var maybeEventuallyParsedDomain: Option[Future[ParsedDomain]] = None
  private var maybeGoal: Option[Goal]                                   = None
  private var maybeEventuallyRawProblem: Option[Future[RawProblem]]     = None

  private var finishedPlannerPost = false
  private var finishedSolving     = false
  private var finishedScheduling  = false

  implicit val executionContext = context.dispatcher
  implicit val materializer     = ActorMaterializer()

  override def preStart(): Unit = {

    log.info("PreStart: Initializing actors...")

    collectorActor = pipelineSettings.collectorSetting match {
      case UseDefaultCollector(s) => context.actorOf(DefaultCollector.props(s))
    }

    plannerActor = pipelineSettings.plannerSetting match {
      case UsePostDPInstantlyPlanner(s) => context.actorOf(PostDPInstantlyPlanner.props(s))
      case UsePostDPWithSolvePlanner(s) => context.actorOf(PostDPWithSolvePlanner.props(s))
    }

    log.info("PreStart: Initialization of actors complete")
  }

  override def receive: Receive = {
    case Start(goal, rawDomain) =>
      maybeGoal = Some(goal)
      maybeRawDomain = Some(rawDomain)
      maybeEventuallyParsedDomain = Some(domainParser(rawDomain))

      collectorActor ! collector.GetPredicatesState

    case collector.PredicatesState(predicates) =>
      maybeEventuallyRawProblem = Some(
        maybeEventuallyParsedDomain.get.map(toProblemConverter(maybeGoal.get, _, collectorActor)).flatten
      )

      timers.startPeriodicTimer(NextPoll, NextPoll, 1.second)

    case NextPoll =>
      if (!finishedPlannerPost) {
        plannerActor ! planner.CheckIds(0, 0)
      } else if (!finishedSolving) {
        plannerActor ! planner.GetSolverResponse
      } else if (!finishedScheduling) {
        maybeSchedulerActor.get ! scheduler.GetStatus
      }

    case idStatus: planner.IdStatus =>
      idStatus match {
        case CorrectIds => finishedPlannerPost = true
        case IncorrectDomainAndProblemId(_, _) =>
          plannerActor ! planner.PostDomain(maybeRawDomain.get, 0)
          maybeEventuallyRawProblem.get.value match {
            case Some(Success(rawProblem)) => plannerActor ! planner.PostProblem(rawProblem, 0)
            case Some(Failure(f))          => // TODO: Error handling
            case _                         => // do nothing; wait
          }

        case IncorrectDomainId(_) =>
          plannerActor ! planner.PostDomain(maybeRawDomain.get, 0)

        case IncorrectProblemId(_) =>
          maybeEventuallyRawProblem.get.value match {
            case Some(Success(rawProblem)) => plannerActor ! planner.PostProblem(rawProblem, 0)
            case Some(Failure(f))          => // TODO: Error handling
            case _                         => // do nothing; wait
          }
      }

    case solverResponse: planner.SolverResponse =>
      solverResponse match {
        case WaitingForSolverResponse => // do nothing; wait
        case MissingDomain            => // should not be possible
        case MissingProblem           => // should not be possible
        case MissingDomainAndProblem  => // should not be possible

        case SuccessfulSolverResponse(solverOutput) =>
          finishedSolving = true
          whichScheduler match {
            case UseIntervalScheduler(s) =>
              maybeSchedulerActor = Some(context.actorOf(IntervalScheduler.props(toStepConverter(solverOutput), s)))

            case UsePreconditionCheckingScheduler(s) =>
              maybeSchedulerActor = maybeEventuallyParsedDomain.get.value match {
                case Some(Success(parsedDomain)) =>
                  // TODO: create an own poll for scheduler creation?
                  Some(
                    context.actorOf(
                      PreconditionCheckingScheduler
                        .props(collectorActor, toStepConverter(solverOutput), parsedDomain, s)
                    )
                  )
                case Some(Failure(f)) => // TODO: Error handling
                  finishedSolving = false
                  None
                case _ =>
                  finishedSolving = false
                  None // do nothing; wait
              }

          }

        case NoSuccessfulResponseOccurred =>
          plannerActor ! planner.Solve
      }

    case schedulerStatus: scheduler.Status =>
      schedulerStatus match {
        case FinishedRunning => context.parent ! Finished
        case NotRunning      => maybeSchedulerActor.get ! scheduler.Start
        case Running         => // do nothing
      }
  }
}
