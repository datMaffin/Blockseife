package de.unistuttgart.iaas.stud.blockseife.actor.scheduler

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Timers}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, RequestEntity, Uri}
import akka.stream.scaladsl.{Keep, Sink, SinkQueueWithCancel, Source}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import de.unistuttgart.iaas.stud.blockseife.Data.{Predicates, Step}
import de.unistuttgart.iaas.stud.blockseife.MyJsonSupport
import de.unistuttgart.iaas.stud.blockseife.actor.collector
import de.unistuttgart.iaas.stud.blockseife.actor.scheduler.PreconditionCheckingScheduler.Settings
import de.unistuttgart.iaas.stud.blockseife.parser.ParsedDomain

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

object PreconditionCheckingScheduler {
  def props(
      collector: ActorRef,
      sourceOfSteps: Source[Step, Any],
      domain: ParsedDomain,
      schedulerSettings: Settings
  ): Props =
    Props(
      new PreconditionCheckingScheduler(
        collector: ActorRef,
        sourceOfSteps: Source[Step, Any],
        domain: ParsedDomain,
        schedulerSettings: Settings
      )
    )

  final case class Settings(
      executionUrl: Uri,
      stepQueuingInterval: FiniteDuration,
      preconditionChecker: (ParsedDomain, Predicates, Step) => Future[Boolean]
  )

  // Intern messages
  private final case class HandlePreconditionFailure(throwable: Throwable)
  private case object HandlePreconditionIsFalse
  private final case class HandleExecutionTransmissionFailure(throwable: Throwable)
  private final case class ExecutionTransmissionSuccessful(executedStep: Step)
  private case object TryExecutingNextStep
}

class PreconditionCheckingScheduler(
    collectorActor: ActorRef,
    sourceOfSteps: Source[Step, Any],
    domain: ParsedDomain,
    schedulerSettings: Settings
) extends Actor
    with Timers
    with ActorLogging
    with MyJsonSupport {

  import PreconditionCheckingScheduler._

  val http                      = Http(context.system)
  implicit val executionContext = context.system.dispatcher
  implicit val materializer     = ActorMaterializer(ActorMaterializerSettings(context.system))

  private val steps: SinkQueueWithCancel[Step] = sourceOfSteps.toMat(Sink.queue())(Keep.right).run()

  // State
  private var started               = false
  private var finished              = false
  private var eventualMaybeNextStep = steps.pull()

  override def postStop(): Unit = {
    steps.cancel()
  }

  override def receive: Receive = {
    case Start if started => // do nothing
    case Start if !started =>
      started = true
      timers.startPeriodicTimer(
        TryExecutingNextStep,
        TryExecutingNextStep,
        schedulerSettings.stepQueuingInterval
      )

    case TryExecutingNextStep =>
      collectorActor ! collector.GetPredicatesState

    case collector.PredicatesState(stateOfPredicates) =>
      eventualMaybeNextStep.value match {
        case Some(Success(Some(step))) => // try to execute next step
          schedulerSettings.preconditionChecker(domain, stateOfPredicates, step).onComplete {
            case Success(true) =>
              Marshal(step)
                .to[RequestEntity]
                .map { entity =>
                  http.singleRequest(
                    HttpRequest(HttpMethods.POST, schedulerSettings.executionUrl, entity = entity)
                  )
                }
                .flatten
                .onComplete {
                  case Success(response) =>
                    response.discardEntityBytes()
                    // TODO: check if response was successful response
                    context.self ! ExecutionTransmissionSuccessful(step)

                  case Failure(e) =>
                    context.self ! HandleExecutionTransmissionFailure(e)
                }

            case Success(false) =>
              context.self ! HandlePreconditionIsFalse

            case Failure(e) =>
              context.self ! HandlePreconditionFailure(e)
          }

        case Some(Success(None)) => finished = true

        case Some(Failure(e)) => // TODO: stream failure; throw exception to planner execution pipeline?
        case None             => // future not completed; do nothing
      }

    case ExecutionTransmissionSuccessful(executedStep) =>
      eventualMaybeNextStep.value match {
        case Some(Success(Some(step))) =>
          if (step.idx == executedStep.idx) {
            eventualMaybeNextStep = steps.pull()
          }
        case _ => // do nothing; this could have been a duplicate message
      }

    case HandlePreconditionIsFalse    => // TODO: Try multiple times or duration till throwing exception to planner execution pipeline
    case HandlePreconditionFailure(e) => // TODO: throw exception to planner execution pipeline?

    case GetStatus =>
      (started, finished) match {
        case (false, false) => sender() ! NotRunning
        case (true, false)  => sender() ! Running
        case (true, true)   => sender() ! FinishedRunning
        case (false, true)  => // TODO: should never happen
      }
  }
}
