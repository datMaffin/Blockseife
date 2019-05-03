package de.unistuttgart.iaas.stud.blockseife

import akka.http.scaladsl.model.ContentType
import akka.stream.scaladsl.Source
import akka.util.ByteString
import spray.json.{JsObject, JsValue}

object Data {
  sealed trait Predicates
  final case class Pddl_1_2_MinimalPredicates(predicates: Map[String, JsValue])              extends Predicates // JsValue should either contain an array of strings or string tuple (represented by another array) or a boolean value
  final case class NotSupportedPredicates(predicateName: String, parsedPredicates: JsObject) extends Predicates

  final case class RawDomain(body: String)
  final case class RawGoal(body: String)
  final case class RawProblem(body: String)

  final case class SolverOutput(contentType: ContentType, byteStream: Source[ByteString, Any])

  final case class Step(idx: Int, action: String, vars: List[String])
}
