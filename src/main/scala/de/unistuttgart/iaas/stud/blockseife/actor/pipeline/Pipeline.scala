package de.unistuttgart.iaas.stud.blockseife.actor.pipeline

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Timers}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import de.unistuttgart.iaas.stud.blockseife.Data.{RawDomain, RawProblem, SolverOutput, Step}
import de.unistuttgart.iaas.stud.blockseife.actor.collector.DefaultCollector
import de.unistuttgart.iaas.stud.blockseife.actor.pipeline.Pipeline.Settings
import de.unistuttgart.iaas.stud.blockseife.actor.planner._
import de.unistuttgart.iaas.stud.blockseife.actor.scheduler.{IntervalScheduler, PreconditionCheckingScheduler}
import de.unistuttgart.iaas.stud.blockseife.actor.{planner, scheduler}
import de.unistuttgart.iaas.stud.blockseife.parser.ParsedDomain

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

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
  final case class UseDefaultCollector(defaultCollectorSettings: DefaultCollector.Settings) extends WhichCollector

  sealed trait WhichPlanner
  final case class UsePostDPInstantlyPlanner(plannerSettings: PostDPInstantlyPlanner.Settings) extends WhichPlanner
  final case class UsePostDPWithSolvePlanner(plannerSettings: PostDPWithSolvePlanner.Settings) extends WhichPlanner

  sealed trait WhichScheduler
  final case class UseIntervalScheduler(schedulerSettings: IntervalScheduler.Settings) extends WhichScheduler
  final case class UsePreconditionCheckingScheduler(schedulerSettings: PreconditionCheckingScheduler.Settings)
      extends WhichScheduler

  // Intern messages
  case object StartPipeline
  final case class SaveParsedDomain(parsedDomain: ParsedDomain)
}

class Pipeline(pipelineSettings: Settings) extends Actor with Timers with ActorLogging {
  // TODO: Polling not yet identical to description in paper
  // TODO: Error handling

  import Pipeline._

  private var collectorActor: ActorRef              = _
  private var plannerActor: ActorRef                = _
  private var maybeSchedulerActor: Option[ActorRef] = None

  private val toProblemConverter = pipelineSettings.toProblemConverter
  private val toStepConverter    = pipelineSettings.toStepsConverter

  private val domainParser = pipelineSettings.domainParser

  private var maybeRawDomain: Option[RawDomain]       = None
  private var maybeParsedDomain: Option[ParsedDomain] = None
  private var maybeGoal: Option[Goal]                 = None

  implicit val executionContext = context.dispatcher
  implicit val materializer     = ActorMaterializer()

  override def preStart(): Unit = {

    log.info("PreStart: Initializing actors...")

    pipelineSettings.collectorSetting match {
      case UseDefaultCollector(defaultCollectorSettings) =>
        collectorActor = context.actorOf(DefaultCollector.props(defaultCollectorSettings))
    }

    plannerActor = pipelineSettings.plannerSetting match {
      case UsePostDPInstantlyPlanner(s) => context.actorOf(PostDPInstantlyPlanner.props(s))
      case UsePostDPWithSolvePlanner(s) => context.actorOf(PostDPWithSolvePlanner.props(s))
    }

    log.info("PreStart: Initialization of actors complete")
  }

  override def receive: Receive = {
    case Start =>
      timers.startPeriodicTimer(StartPipeline, StartPipeline, 1.second)

    case ParseDomain(rawDomain) =>
      maybeRawDomain = Some(rawDomain)
      domainParser(rawDomain).onComplete {
        case Success(parsedDomain) => context.self ! SaveParsedDomain(parsedDomain)
        case Failure(exception)    => // TODO: failed to parse domain
      }

    case SaveGoal(goal) =>
      maybeGoal = Some(goal)

    case StartPipeline =>
      (maybeGoal, maybeParsedDomain, maybeRawDomain) match {
        case (Some(goal), Some(parsedDomain), Some(rawDomain)) => // start the pipeline
          plannerActor ! PostDomain(rawDomain, 0) // TODO: ids...

          toProblemConverter(goal, parsedDomain, collectorActor).onComplete({
            case Success(value) =>
              plannerActor ! PostProblem(value, 0) // TODO: ids...
              plannerActor ! Solve

            case Failure(exception) => // TODO: failed to convert problem
          })

          timers.cancelAll() // TODO: better handling for eventual missing messages

        case _ => // not ready yet...

      }

    case SaveParsedDomain(parsedDomain) =>
      maybeParsedDomain = Some(parsedDomain)

    case planner.SuccessfulSolverResponse(solverOutput) => // 2. step: Continue pipeline from solver

      maybeSchedulerActor = (pipelineSettings.schedulerSetting, maybeParsedDomain) match {
        case (UseIntervalScheduler(s: IntervalScheduler.Settings), _) =>
          Some(
            context.actorOf(IntervalScheduler.props(toStepConverter(solverOutput), s))
          )
        case (UsePreconditionCheckingScheduler(s: PreconditionCheckingScheduler.Settings), Some(parsedDomain)) =>
          Some(
            context.actorOf(
              PreconditionCheckingScheduler.props(collectorActor, toStepConverter(solverOutput), parsedDomain, s))
          )
        //case s @ UseRestScheduler(_, _)   => scheduler = context.actorOf(RestScheduler.props(s, collector))
      }
      maybeSchedulerActor.get ! scheduler.Start
  }
}
