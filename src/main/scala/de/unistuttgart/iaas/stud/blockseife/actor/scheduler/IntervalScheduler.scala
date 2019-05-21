package de.unistuttgart.iaas.stud.blockseife.actor.scheduler

import akka.actor.{Actor, ActorLogging, Props, Timers}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, RequestEntity, Uri}
import akka.stream.scaladsl.{Keep, Sink, SinkQueueWithCancel, Source}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import de.unistuttgart.iaas.stud.blockseife.Data.Step
import de.unistuttgart.iaas.stud.blockseife.MyJsonSupport
import de.unistuttgart.iaas.stud.blockseife.actor.scheduler.IntervalScheduler.Settings

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

object IntervalScheduler {
  def props(
      sourceOfSteps: Source[Step, Any],
      schedulerSettings: Settings
  ): Props =
    Props(
      new IntervalScheduler(
        sourceOfSteps: Source[Step, Any],
        schedulerSettings: Settings
      )
    )

  final case class Settings(
      executionUrl: Uri,
      stepQueuingInterval: FiniteDuration
  )

  // Intern messages
  private final case class HandlePreconditionFailure(throwable: Throwable)
  private case object HandlePreconditionIsFalse
  private final case class HandleExecutionTransmissionFailure(throwable: Throwable)
  private final case class ExecutionTransmissionSuccessful(executedStep: Step)
  private case object TryExecutingNextStep
}

class IntervalScheduler(
    sourceOfSteps: Source[Step, Any],
    schedulerSettings: Settings
) extends Actor
    with Timers
    with ActorLogging
    with MyJsonSupport {

  import IntervalScheduler._

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
      eventualMaybeNextStep.value match {
        case Some(Success(Some(step))) =>
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
