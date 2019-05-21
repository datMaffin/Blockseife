package de.unistuttgart.iaas.stud.blockseife.actor

import de.unistuttgart.iaas.stud.blockseife.Data.RawDomain

package object pipeline {
  //
  // Messages pipeline actors can handle
  // ===================================

  final case class Start(goal: Goal, domain: RawDomain)

  sealed trait Goal
  final case class RawGoal(body: String) extends Goal

  //
  // Response messages from pipeline actors
  // ======================================
  case object Finished

  //
  // Custom exceptions thrown by pipeline actors
  // ===========================================
}
