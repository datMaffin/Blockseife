package de.unistuttgart.iaas.stud.blockseife.actor.planner

import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.RequestEntityAcceptance.Tolerated
import akka.http.scaladsl.model.{HttpEntity, HttpMethod, HttpRequest, Uri}
import akka.stream.scaladsl.Sink
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import de.unistuttgart.iaas.stud.blockseife.Data.{RawDomain, RawProblem, SolverOutput}
import de.unistuttgart.iaas.stud.blockseife.actor.planner.PostDPWithSolvePlanner.Settings

import scala.util.{Failure, Success}

object PostDPWithSolvePlanner {
  def props(plannerSetting: Settings): Props = Props(new PostDPWithSolvePlanner(plannerSetting))

  final case class Settings(solverUrl: Uri)

  // Intern messages
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
class PostDPWithSolvePlanner(plannerSetting: Settings) extends Actor with ActorLogging {

  import PostDPWithSolvePlanner._

  val http                      = Http(context.system)
  implicit val executionContext = context.system.dispatcher
  implicit val materializer     = ActorMaterializer(ActorMaterializerSettings(context.system))

  // State
  private var maybeDomainWithId: Option[(RawDomain, Int)]   = None
  private var maybeProblemWithId: Option[(RawProblem, Int)] = None

  /** None: No solver was started or error occurred
    * SolverStatusQuerying: Waiting for solver response
    * SolverStatusCompleted: Solver completed
    */
  private var maybeSolverStatus: Option[SolverStatus] = None

  override def receive: Receive = {
    case PostDomain(d: RawDomain, id) =>
      maybeDomainWithId = Some((d, id))

    case PostProblem(p: RawProblem, id) =>
      maybeProblemWithId = Some((p, id))

    case Solve =>
      (maybeSolverStatus, maybeDomainWithId, maybeProblemWithId) match {
        case (Some(SolverStatusQuerying), _, _) =>
        // Do not query again (because the state would become non-deterministic (we would not know which request finishes first)

        case (Some(SolverStatusCompleted(solverOutput)), Some((domain, _)), Some((problem, _))) =>
          solverOutput.byteStream.runWith(Sink.ignore) // Discard old solver output
          querySolving(domain, problem)

        case (None, Some((domain, _)), Some((problem, _))) =>
          querySolving(domain, problem)

        case _ => maybeSolverStatus = None // domain or problem is missing; doing nothing would also work
      }

    case CheckIds(domainId, problemId) =>
      (maybeDomainWithId, maybeProblemWithId) match {
        case (Some((_, `domainId`)), Some((_, `problemId`))) => sender() ! CorrectIds
        case (Some((_, `domainId`)), _)                      => sender() ! IncorrectProblemId(maybeProblemWithId.map(_._2))
        case (_, Some((_, `problemId`)))                     => sender() ! IncorrectDomainId(maybeDomainWithId.map(_._2))
        case _                                               => sender() ! IncorrectDomainAndProblemId(maybeDomainWithId.map(_._2), maybeProblemWithId.map(_._2))
      }

    case GetSolverResponse =>
      maybeSolverStatus match {
        case Some(SolverStatusQuerying)                => sender() ! WaitingForSolverResponse
        case Some(SolverStatusCompleted(solverOutput)) => sender() ! SuccessfulSolverResponse(solverOutput)

        case None =>
          (maybeDomainWithId, maybeProblemWithId) match {
            case (Some(_), Some(_)) => sender() ! NoSuccessfulResponseOccurred
            case (Some(_), _)       => sender() ! MissingProblem
            case (_, Some(_))       => sender() ! MissingDomain
            case (_, _)             => sender() ! MissingDomainAndProblem
          }
      }

    case SaveSolverResponse(solverResponse) => maybeSolverStatus = Some(SolverStatusCompleted(solverResponse))
    case FailedSolverResponse =>
      log.warning("Planner failed to start solving")
      maybeSolverStatus = None
  }

  private def querySolving(domain: RawDomain, problem: RawProblem): Unit = {
    maybeSolverStatus = Some(SolverStatusQuerying)

    val restUrl = plannerSetting.solverUrl + "/solve"
    val httpRequest = HttpRequest(
      uri = restUrl,
      // identical to normal GET except: isIdempotent is false
      // Reason: If the request times out sending another GET request does not work; it will just time out in the
      //         same way!
      method = HttpMethod("GET", isSafe = true, isIdempotent = false, requestEntityAcceptance = Tolerated),
      entity = HttpEntity(domain.body + "\n\n" + problem.body)
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
