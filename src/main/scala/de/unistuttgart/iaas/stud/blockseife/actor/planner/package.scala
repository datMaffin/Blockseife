package de.unistuttgart.iaas.stud.blockseife.actor

import de.unistuttgart.iaas.stud.blockseife.Data.{RawDomain, RawProblem, SolverOutput}

package object planner {
  //
  // Messages planner actors can handle
  // ==================================

  /** Message instructs the planner to post or save the domain for a future solve message
    *
    * @param rawDomain the raw domain contains in its body the string/text/code of a domain readable by the solver
    * @param id a id of the domain. This id is used to check if a specific version of the problem was posted/saved.
    *           It should be therefore be different when a different domain was posted.
    */
  final case class PostDomain(rawDomain: RawDomain, id: Int)

  /** Message instructs the planner to post or save the problem for a future solve message
    *
    * @param rawProblem the raw problem contians in it's body the string/text/code of a problem readable by the solver
    * @param id a id of problem. This id is used to check if a specific version of the problem was posted/saved.
    *           It should be therefore be different when a different problem was posted.
    */
  final case class PostProblem(rawProblem: RawProblem, id: Int)

  /** Message instructs the planner to solve the domain using it's solver. The planner is discarding the old solver
    * response when applicable.
    * (See [[https://doc.akka.io/docs/akka-http/current/implications-of-streaming-http-entity.html#implications-of-the-streaming-nature-of-request-response-entities]])
    */
  case object Solve

  /** Message instructs the planner to check if the ids correspond to the ids it has posted/saved. The planner sends a
    * message with the subtype of [[de.unistuttgart.iaas.stud.blockseife.actor.planner.IdStatus]] back to the sender
    *
    * @param domainId the id of the corresponding domain
    * @param problemId the id of the corresponding problem
    */
  final case class CheckIds(domainId: Int, problemId: Int)

  /** Message instructs the planner to return the solver response. The planner sends a message with the subtype
    * of [[de.unistuttgart.iaas.stud.blockseife.actor.planner.SolverResponse]] back to the sender
    */
  case object GetSolverResponse

  //
  // Response messages from planner actors
  // =====================================

  /** See [[de.unistuttgart.iaas.stud.blockseife.actor.planner.CheckIds]] */
  sealed trait IdStatus

  /** Message indicating, that the checked domain or problem ids are successfully posted/saved */
  case object CorrectIds extends IdStatus

  /** Message indicating, that the id check of the posted/saved domain and problem was not successful
    * @param domainId the domain id of the domain that was posted/saved or None if no domain was posted/saved
    * @param problemId the problem id of the problem that was posted/saved or None if no problem was posted/saved
    */
  final case class IncorrectDomainAndProblemId(domainId: Option[Int], problemId: Option[Int]) extends IdStatus

  /** Message indicating, that the id check of the posted/saved domain was not successful
    * @param domainId the domain id of the domain that was posted/saved or None if no domain was posted/saved
    */
  final case class IncorrectDomainId(domainId: Option[Int]) extends IdStatus

  /** Message indicating, that the id check of the posted/saved problem was not successful
    * @param problemId the problem id of the problem that was posted/saved or None if no problem was posted/saved
    */
  final case class IncorrectProblemId(problemId: Option[Int]) extends IdStatus

  /** See [[de.unistuttgart.iaas.stud.blockseife.actor.planner.GetSolverResponse]] */
  sealed trait SolverResponse

  /** Message indicating, that the solver response was successful
    * @param solverOutput the (successful) solver output
    */
  final case class SuccessfulSolverResponse(solverOutput: SolverOutput) extends SolverResponse

  /** Message indicating, that the solver is still running */
  case object WaitingForSolverResponse extends SolverResponse

  /** Message indicating, that either solve was never requested or the solver failed */
  case object NoSuccessfulResponseOccurred extends SolverResponse

  /** Message indicating, that no domain or problem was posted/saved */
  case object MissingDomainAndProblem extends SolverResponse

  /** Message indicating, that no domain was posted/saved */
  case object MissingDomain extends SolverResponse

  /** Message indicating, that no problem was posted/saved */
  case object MissingProblem extends SolverResponse

  //
  // Custom exceptions thrown by planner actors
  // ==========================================
}
