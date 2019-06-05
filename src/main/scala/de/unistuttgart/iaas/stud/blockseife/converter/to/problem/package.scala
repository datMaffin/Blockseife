package de.unistuttgart.iaas.stud.blockseife.converter.to

import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.pattern.ask
import akka.util.Timeout
import spray.json.JsArray
import spray.json.JsBoolean
import spray.json.JsString

import de.unistuttgart.iaas.stud.blockseife.actor.collector._
import de.unistuttgart.iaas.stud.blockseife.actor.pipeline._
import de.unistuttgart.iaas.stud.blockseife.actor.pipeline.Pipeline.CollectorActor
import de.unistuttgart.iaas.stud.blockseife.Data.{RawProblem, Pddl_1_2_MinimalPredicates}
import de.unistuttgart.iaas.stud.blockseife.parser.{ParsedDomain, Pddl_1_2_MinimalParsedDomain}


package object problem {
  def problemPddl_1_2_MinimalConverter(goal: Goal, parsedDomain: ParsedDomain, collector: CollectorActor)(
      implicit ec: ExecutionContext
  ): Future[RawProblem] = {

    (goal, parsedDomain) match {
      case (RawGoal(rawGoal), Pddl_1_2_MinimalParsedDomain(parsedDomain)) =>
        implicit val timeout   = Timeout(2.seconds)
        val eventualPredicates = collector ? GetPredicatesState

        eventualPredicates.map {
          case PredicatesState(Pddl_1_2_MinimalPredicates(predicates)) =>
            val initField = "(:init " + predicates
              .map {
                {
                  case (predicate, JsArray(arr)) if arr.nonEmpty =>
                    arr
                      .map {
                        case JsArray(elements) =>
                          val names = elements
                            .filter {
                              case JsString(_) => true
                              case _           => false
                            }
                            .map {
                              case JsString(name) => name
                              case _              => ""
                            }
                          s"($predicate ${names.fold("")(_ + " " + _)})"

                        case JsString(name) => s"($predicate $name)"
                        case _              => "" // TODO: Error??
                      }
                      .fold[String]("")(_ + " " + _)

                  case (str, JsArray(arr)) if arr.isEmpty => ""
                  case (str, JsBoolean(true))             => s"($str)"
                  case (str, JsBoolean(false))            => ""
                }
              }
              .fold[String]("")(_ + " " + _) + " )"

            Future { RawProblem(s"${rawGoal.subSequence(0, rawGoal.lastIndexOf(')') - 1)} $initField )") }

          case _ => Future.failed(new Throwable("The predicate format is not supported by this converter"))
        }.flatten

      case _ =>
        Future.failed(new Throwable("The goal and/or domain format is not supported by this converter."))
    }
  }
}
