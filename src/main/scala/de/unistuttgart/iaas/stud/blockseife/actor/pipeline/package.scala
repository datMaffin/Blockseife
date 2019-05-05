package de.unistuttgart.iaas.stud.blockseife.actor

import de.unistuttgart.iaas.stud.blockseife.Data.RawDomain

package object pipeline {
  // Messages this actor can handle
  case object Start
  final case class ParseDomain(rawDomain: RawDomain)
  final case class SaveGoal(goal: Goal)

  sealed trait Goal
  final case class RawGoal(body: String) extends Goal
}
