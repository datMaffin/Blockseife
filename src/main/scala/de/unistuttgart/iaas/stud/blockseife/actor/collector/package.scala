package de.unistuttgart.iaas.stud.blockseife.actor

import de.unistuttgart.iaas.stud.blockseife.Data.Predicates

package object collector {
  //
  // Messages collector actors can handle
  // ====================================

  /** Message instructs the collector to get the object names. If successful, the collector sends an answer of the subtype
    * [[de.unistuttgart.iaas.stud.blockseife.actor.collector.Objects]] back to the sender.
    */
  case object GetObjects

  /** Message instructs the collector to get the predicate states. If successful, the collector sends an answer of the subtype
    * [[de.unistuttgart.iaas.stud.blockseife.actor.collector.PredicatesState]] back to the sender.
    */
  case object GetPredicatesState

  //
  // Response messages from collector actors
  // =======================================

  /** See [[de.unistuttgart.iaas.stud.blockseife.actor.collector.GetObjects]]
    * @param objects the names of the objects
    */
  final case class Objects(objects: Seq[String])

  /** See [[de.unistuttgart.iaas.stud.blockseife.actor.collector.GetPredicatesState]]
    * @param predicates the state of the predicates in form of a subtype of [[de.unistuttgart.iaas.stud.blockseife.Data.Predicates]]
    */
  final case class PredicatesState(predicates: Predicates)

  //
  // Custom exceptions thrown by collector actors
  // ============================================

  /** This error gets thrown when either the http request or the unmarshalling failed */
  case object CommunicationWithExternalCollectorServiceFailed
      extends Throwable("This error gets thrown when either the http request or the unmarshalling failed")
}
