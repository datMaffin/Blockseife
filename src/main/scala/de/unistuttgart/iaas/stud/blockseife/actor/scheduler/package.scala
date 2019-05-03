package de.unistuttgart.iaas.stud.blockseife.actor

package object scheduler {
  //
  // Messages scheduler actors can handle
  // ====================================

  /** Message instructs the scheduler to start executing the stream of steps */
  case object Start

  /** Message instructs the scheduler to return the status. The scheduler sends an answer of the subtype
    * [[de.unistuttgart.iaas.stud.blockseife.actor.scheduler.Status]] back to the sender. */
  case object GetStatus

  //
  // Response messages from scheduler actors
  // =======================================

  /** See [[de.unistuttgart.iaas.stud.blockseife.actor.scheduler.GetStatus]] */
  sealed trait Status

  /** Message indicating, that the planner did not receive a [[de.unistuttgart.iaas.stud.blockseife.actor.scheduler.Start]]
    * message and therefore did not start the execution.
    */
  case object NotRunning extends Status

  /** Message indicating, that the planner received a [[de.unistuttgart.iaas.stud.blockseife.actor.scheduler.Start]] message
    * and therefore started the execution and is still scheduling.
    */
  case object Running extends Status

  /** Message indicating, that the planner received a [[de.unistuttgart.iaas.stud.blockseife.actor.scheduler.Start]] message
    * and therefore started the execution, finished the execution of all steps in the stream (the stream completed).
    */
  case object FinishedRunning extends Status

  //
  // Custom exceptions thrown by scheduler actors
  // ============================================
}
