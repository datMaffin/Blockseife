package de.unistuttgart.iaas.stud.blockseife

import de.unistuttgart.iaas.stud.blockseife.Data.{Pddl_1_2_MinimalPredicates, Predicates, RawDomain, Step}
import de.unistuttgart.iaas.stud.blockseife.parser.domains.pddl._1._2.minimal.{
  ActionDef,
  ActionDefBody,
  AtomicFormulaTerms,
  DomainCompiler,
  DomainLexerError,
  DomainParserError,
  DomainRoot,
  GoalDescription,
  GoalDescriptionAnd,
  Name,
  NegatedAtomicFormulaTerms,
  Term,
  Variable
}
import spray.json.{JsArray, JsBoolean, JsObject, JsString, JsValue}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

package object parser {
  sealed trait ParsedDomain
  final case class NotSupportedParsedDomain(domainName: String, parsedDomain: JsObject) extends ParsedDomain // directly pass a not supported domain to the problem creation and scheduler
  final case class Pddl_1_2_MinimalParsedDomain(parsedDomain: domains.pddl._1._2.minimal.DomainRoot)
      extends ParsedDomain

  def pddl_1_2_MinimalDomainParser(rawDomain: RawDomain)(implicit ec: ExecutionContext): Future[ParsedDomain] = {
    DomainCompiler(rawDomain.body) match {
      case Left(DomainLexerError(location, msg)) =>
        Future.failed(new Throwable(s"Lexer Error at line ${location.line}, column ${location.column}: $msg"))
      case Left(DomainParserError(location, msg)) =>
        Future.failed(new Throwable(s"Parser Error at line ${location.line}, column ${location.column}: $msg"))
      case Right(d @ DomainRoot(_, _, _, _, _, _, _, _, _)) => Future { Pddl_1_2_MinimalParsedDomain(d) }
      case _                                                => Future.failed(new Throwable("The domain compiler did not return a domain root!"))
    }
  }

  def preconditionCheckerPddl_1_2_Minimal(
      parsedDomain: ParsedDomain,
      predicates: Predicates,
      step: Step
  )(implicit ec: ExecutionContext): Future[Boolean] = {

    Future {
      (parsedDomain, predicates) match {
        case (Pddl_1_2_MinimalParsedDomain(parsedDomain), Pddl_1_2_MinimalPredicates(predicates)) =>
          // make keys of predicates upper case
          val allCapsPredicates = predicates.map(t => (t._1.toUpperCase, t._2))

          // find goal description for precondition (for action with the action functor actionFunctor)

          (parsedDomain.structureDefs.find {
            case ActionDef(step.action, _, _) => true
            case _                            => false
          } match {
            case Some(ActionDef(_, typedListVariable, ActionDefBody(preconditionGoalDescription, _)))
                if typedListVariable.size == step.vars.size =>
              Future {
                (preconditionGoalDescription, typedListVariable.map(_.variable).zip(step.vars).toMap[String, String])
              }
            case None =>
              Future.failed(
                new Throwable(
                  s"Action-def for step $step not found"
                )
              )
          }).map[Future[Boolean]] {
              case (None, _) =>
                Future {
                  true
                } // No preconditions => preconditions are fulfilled

              case (Some(AtomicFormulaTerms(predicate, terms)), variableToValueMap) => // TODO: compare parameter with terms
                checkPDDL_1_2_MinimalPredicate(
                  allCapsPredicates(predicate),
                  termsToParameter(terms, variableToValueMap)
                )

              case (Some(NegatedAtomicFormulaTerms(predicate, terms)), variableToValueMap) => // TODO: compare parameter with terms
                checkPDDL_1_2_MinimalPredicate(
                  allCapsPredicates(predicate),
                  termsToParameter(terms, variableToValueMap)
                ).map(
                  !_
                )

              case (Some(g: GoalDescriptionAnd), variableToValueMap) => // iterate over goal descriptions...

                // init vars needed during and after calculation
                val goalDescriptionQueue: mutable.Queue[GoalDescription]      = mutable.Queue(g)
                val predicateTruthyList: mutable.MutableList[Future[Boolean]] = mutable.MutableList()

                while (goalDescriptionQueue.nonEmpty) {
                  goalDescriptionQueue.dequeue() match {
                    case GoalDescriptionAnd(goalDescriptions) =>
                      goalDescriptionQueue ++= goalDescriptions

                    case AtomicFormulaTerms(predicate, terms) => // TODO: compare parameter with terms
                      predicateTruthyList += checkPDDL_1_2_MinimalPredicate(
                        allCapsPredicates(predicate),
                        termsToParameter(terms, variableToValueMap)
                      )

                    case NegatedAtomicFormulaTerms(predicate, terms) => // TODO: compare parameter with terms
                      predicateTruthyList += checkPDDL_1_2_MinimalPredicate(
                        allCapsPredicates(predicate),
                        termsToParameter(terms, variableToValueMap)
                      ).map(!_)
                  }
                }
                Future.foldLeft(predicateTruthyList.toList)(true)(_ && _)
            }
            .flatten

        case _ =>
          Future.failed(new Throwable("The predicate and/or domain format is not supported by this converter."))
      }
    }.flatten
  }

  /** Takes terms and variable to value map and calculates the resulting parameters for the atomic formula
    *
    * @param terms the terms to use; it contains the variable names and names
    * @param variableToValueMap the values of the variables in a map with the variable names as key
    * @return parameters fo the atomic formula
    */
  private def termsToParameter(terms: Seq[Term], variableToValueMap: Map[String, String]): Seq[String] = {
    terms.map {
      case Variable(variable) =>
        variableToValueMap.getOrElse(variable, "") // TODO: better error handling than using "" empty string??
      case Name(name) => name
    }
  }

  /**
    * Checks if the predicate is true for the following terms
    *
    * @param predicate the predicate to check
    * @param parameter the parameter for the predicate
    * @return a future that holds the information if the predicate is true, false or if an error occurred
    */
  private def checkPDDL_1_2_MinimalPredicate(predicate: JsValue, parameter: Seq[String])(
      implicit ec: ExecutionContext
  ): Future[Boolean] = {

    Future {

      predicate match {
        case JsBoolean(value) if parameter.isEmpty =>
          Future {
            value
          }

        case JsArray(setElements) =>
          setElements
            .map {
              case JsArray(tupleElements) if tupleElements.size == parameter.size => // Tuple

                tupleElements
                  .zip(parameter)
                  .map {
                    case (JsString(value), termString) =>
                      Future {
                        value.toUpperCase == termString.toUpperCase
                      }
                    case _ =>
                      Future.failed(
                        new Throwable(
                          "Predicate value is not as expected: Either boolean or an array in an array (that represents a tuple) with strings!"
                        )
                      ) // TODO: better error message
                  }
                  .reduce((a1, a2) => Future.reduceLeft(List(a1, a2))(_ && _))

              case JsString(value) if parameter.size == 1 =>
                Future {
                  value.toUpperCase == parameter.head.toUpperCase
                }

              case test => //
                Future.failed(
                  new Throwable(
                    "Predicate value is not as expected: Either boolean or an array in an array (that represents a tuple) with strings!"
                  )
                ) // TODO: better error message
            }
            .reduce((a1, a2) => Future.reduceLeft(List(a1, a2))(_ || _))

        case _ =>
          Future.failed(
            new Throwable(
              "Predicate value is not as expected: Either boolean or an array in an array (that represents a tuple) with strings!"
            )
          ) // TODO: better error message
      }
    }.flatten
  }
}
