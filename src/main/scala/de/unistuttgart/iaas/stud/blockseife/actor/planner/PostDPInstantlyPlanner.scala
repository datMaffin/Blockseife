package de.unistuttgart.iaas.stud.blockseife.actor.planner

import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpMethod, HttpMethods, HttpRequest, RequestEntity, Uri}
import akka.http.scaladsl.model.RequestEntityAcceptance.Tolerated
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.stream.scaladsl.Sink
import de.unistuttgart.iaas.stud.blockseife.Data.{RawDomain, RawProblem, SolverOutput}
import de.unistuttgart.iaas.stud.blockseife.actor.planner.PostDPInstantlyPlanner.Settings

import scala.util.{Failure, Success}

object PostDPInstantlyPlanner {
  def props(plannerSetting: Settings): Props = Props(new PostDPInstantlyPlanner(plannerSetting))

  final case class Settings(solverUrl: Uri)

  // Intern messages
  private final case class SuccessfulDomainUpload(id: Int)
  private case object FailedDomainUpload
  private final case class SuccessfulProblemUpload(id: Int)
  private case object FailedProblemUpload
  private final case class SaveSolverResponse(solverOutput: SolverOutput)
  private case object FailedSolverResponse

  private sealed trait SolverStatus
  private case object SolverStatusQuerying                                   extends SolverStatus
  private final case class SolverStatusCompleted(solverOutput: SolverOutput) extends SolverStatus
}

/** This planner is intended for using it in a single planner execution pipeline.
  * Reason: It contains the state of only ONE solver interaction
  *
  * @param plannerSetting the settings needed to create a planning actor
  */
class PostDPInstantlyPlanner(plannerSetting: Settings) extends Actor with ActorLogging {

  import PostDPInstantlyPlanner._

  val http                      = Http(context.system)
  implicit val executionContext = context.system.dispatcher
  implicit val materializer     = ActorMaterializer(ActorMaterializerSettings(context.system))

  // State
  private var maybeDomainId: Option[Int]  = None
  private var maybeProblemId: Option[Int] = None

  /** None: No solver was started or error occurred
    * SolverStatusQuerying: Waiting for solver response
    * SolverStatusCompleted: Solver completed
    */
  private var maybeSolverStatus: Option[SolverStatus] = None

  override def receive: Receive = {
    case PostDomain(RawDomain(body), id) =>
      Marshal(body)
        .to[RequestEntity]
        .map(
          entity =>
            HttpRequest(
              method = HttpMethods.POST,
              uri = plannerSetting.solverUrl + "/domain",
              entity = entity
          )
        )
        .map(request => http.singleRequest(request))
        .flatten
        .onComplete {
          case Success(r) =>
            r.discardEntityBytes()
            context.self ! SuccessfulDomainUpload(id)

          case Failure(f) =>
            context.self ! FailedDomainUpload
        }

    case PostProblem(RawProblem(body), id) =>
      Marshal(body)
        .to[RequestEntity]
        .map(
          entity =>
            HttpRequest(
              method = HttpMethods.POST,
              uri = plannerSetting.solverUrl + "/problem",
              entity = entity
          )
        )
        .map(request => http.singleRequest(request))
        .flatten
        .onComplete {
          case Success(r) =>
            r.discardEntityBytes()
            context.self ! SuccessfulProblemUpload(id)

          case Failure(f) =>
            context.self ! FailedProblemUpload
        }

    case Solve =>
      maybeSolverStatus match {
        case Some(SolverStatusQuerying) =>
        // Do not query again (because the state would become non-deterministic (we would not know which request finishes first)

        case Some(SolverStatusCompleted(solverOutput)) =>
          solverOutput.byteStream.runWith(Sink.ignore) // Discard old solver output
          querySolving()

        case None =>
          querySolving()
      }

    case CheckIds(domainId, problemId) =>
      (maybeDomainId, maybeProblemId) match {
        case (Some(`domainId`), Some(`problemId`)) => sender() ! CorrectIds
        case (Some(`domainId`), _)                 => sender() ! IncorrectProblemId(maybeProblemId)
        case (_, Some(`problemId`))                => sender() ! IncorrectDomainId(maybeDomainId)
        case _                                     => sender() ! IncorrectDomainAndProblemId(maybeDomainId, maybeProblemId)
      }

    case GetSolverResponse =>
      maybeSolverStatus match {
        case Some(SolverStatusQuerying)                => sender() ! WaitingForSolverResponse
        case Some(SolverStatusCompleted(solverOutput)) => sender() ! SuccessfulSolverResponse(solverOutput)

        case None =>
          (maybeDomainId, maybeProblemId) match {
            case (Some(_), Some(_)) => sender() ! NoSuccessfulResponseOccurred
            case (Some(_), _)       => sender() ! MissingProblem
            case (_, Some(_))       => sender() ! MissingDomain
            case (_, _)             => sender() ! MissingDomainAndProblem
          }
      }

    case SuccessfulDomainUpload(id) => maybeDomainId = Some(id)
    case FailedDomainUpload =>
      log.warning("Planner failed to upload the domain")
      maybeDomainId = None

    case SuccessfulProblemUpload(id) => maybeProblemId = Some(id)
    case FailedProblemUpload =>
      log.warning("Planner failed to upload the problem")
      maybeProblemId = None

    case SaveSolverResponse(solverResponse) => maybeSolverStatus = Some(SolverStatusCompleted(solverResponse))
    case FailedSolverResponse =>
      log.warning("Planner failed to start solving")
      maybeSolverStatus = None
  }

  private def querySolving(): Unit = {
    maybeSolverStatus = Some(SolverStatusQuerying)

    val restUrl = plannerSetting.solverUrl + "/solve"
    val httpRequest = HttpRequest(
      uri = restUrl,
      // identical to normal GET except: isIdempotent is false
      // Reason: If the request times out sending another GET request does not work; it will just time out in the
      //         same way!
      method = HttpMethod("GET", isSafe = true, isIdempotent = false, requestEntityAcceptance = Tolerated)
    )

    // TODO: Reimplement on lower abstraction for possibly longer waiting times (default is 1 minute)

    http
      .singleRequest(httpRequest)
      .map(response => SolverOutput(response.entity.contentType, response.entity.dataBytes))
      .onComplete {
        case Success(r) => context.self ! SaveSolverResponse(r)
        case Failure(f) => context.self ! FailedSolverResponse
      }
  }
}
