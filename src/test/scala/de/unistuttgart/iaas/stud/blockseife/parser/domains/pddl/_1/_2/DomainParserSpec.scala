package de.unistuttgart.iaas.stud.blockseife.parser.domains.pddl._1._2.minimal

import org.scalatest.WordSpec
import org.scalatest.Matchers

class DomainParserSpec extends WordSpec with Matchers {
  "The parser" should {
    "parse important metadata correctly" in {
      DomainCompiler("""(define (domain lighting)
                       |  (:requirements :strips)
                       |)""".stripMargin) match {

        case Right(ast: DomainRoot) =>
          ast.name.name shouldBe "LIGHTING"
          ast.requireDef.get.requireKeys.size shouldBe 1
          ast.requireDef.get.requireKeys(0) shouldBe ":STRIPS"

        case _ => fail()
      }
    }

    "parse predicates correctly" in {
      DomainCompiler("""(define (domain lighting)
                       |  (:requirements :strips)
                       |  (:predicates
                       |    (light_on ?x)
                       |    (sun_light ?x)
                       |  )
                       |)""".stripMargin) match {

        case Right(ast: DomainRoot) =>
          ast.predicatesDef.get.atomicFormulaSkeletons.size shouldBe 2
          ast.predicatesDef.get.atomicFormulaSkeletons(0).predicate shouldBe "LIGHT_ON"
          ast.predicatesDef.get.atomicFormulaSkeletons(0).typedListOfVariables.size shouldBe 1
          ast.predicatesDef.get.atomicFormulaSkeletons(0).typedListOfVariables(0).variable shouldBe "X"
          ast.predicatesDef.get.atomicFormulaSkeletons(1).predicate shouldBe "SUN_LIGHT"
          ast.predicatesDef.get.atomicFormulaSkeletons(1).typedListOfVariables.size shouldBe 1
          ast.predicatesDef.get.atomicFormulaSkeletons(1).typedListOfVariables(0).variable shouldBe "X"

        case _ => fail()
      }
    }

    "parse actions correctly" in {
      DomainCompiler("""(define (domain lighting)
                       |  (:requirements :strips)
                       |  (:predicates
                       |    (light_on ?x)
                       |    (sun_light ?x)
                       |  )
                       |  (:action switch_light_on
                       |    :parameters(?x)
                       |    :precondition (not (light_on ?x))
                       |    :effect (light_on ?x ) 
                       |  )
                       |  (:action switch_light_off
                       |    :parameters(?x)
                       |    :precondition (light_on ?x)
                       |    :effect (not (light_on ?x ))
                       |  )
                       |)""".stripMargin) match {

        case Right(ast: DomainRoot) =>
          ast.structureDefs.size shouldBe 2
          ast.structureDefs(0) match {
            case ActionDef(actionFunctor, typedListVariable, body) =>
              actionFunctor shouldBe "SWITCH_LIGHT_ON"

              typedListVariable.size shouldBe 1
              typedListVariable(0).variable shouldBe "X"

              body.preconditionGoalDescription.get match {
                case AtomicFormulaTerms(predicate, terms) => fail()
                case GoalDescriptionAnd(goalDescriptions) => fail()
                case NegatedAtomicFormulaTerms(predicate, terms) =>
                  predicate shouldBe "LIGHT_ON"
                  terms.size shouldBe 1
                  terms(0) match {
                    case Name(name)         => fail()
                    case Variable(variable) => variable shouldBe "X"
                  }
              }

              body.effect.get match {
                case AtomicFormulaTerms(predicate, terms) =>
                  predicate shouldBe "LIGHT_ON"
                  terms.size shouldBe 1
                  terms(0) match {
                    case Name(name)         => fail()
                    case Variable(variable) => variable shouldBe "X"
                  }
                case EffectAnd(effects)                          => fail()
                case NegatedAtomicFormulaTerms(predicate, terms) => fail()
              }

            case _ => fail()
          }
          ast.structureDefs(1) match {
            case ActionDef(actionFunctor, typedListVariable, body) =>
              actionFunctor shouldBe "SWITCH_LIGHT_OFF"

              typedListVariable.size shouldBe 1
              typedListVariable(0).variable shouldBe "X"

              body.preconditionGoalDescription.get match {
                case AtomicFormulaTerms(predicate, terms) =>
                  predicate shouldBe "LIGHT_ON"
                  terms.size shouldBe 1
                  terms(0) match {
                    case Name(name)         => fail()
                    case Variable(variable) => variable shouldBe "X"
                  }
                case GoalDescriptionAnd(goalDescriptions)        => fail()
                case NegatedAtomicFormulaTerms(predicate, terms) => fail()
              }

              body.effect.get match {
                case AtomicFormulaTerms(predicate, terms) => fail()
                case EffectAnd(effects)                   => fail()
                case NegatedAtomicFormulaTerms(predicate, terms) =>
                  predicate shouldBe "LIGHT_ON"
                  terms.size shouldBe 1
                  terms(0) match {
                    case Name(name)         => fail()
                    case Variable(variable) => variable shouldBe "X"
                  }
              }

            case _ => fail()
          }

        case _ => fail()
      }
    }

    "parse a more complex pddl 1.2 domain with strips without an error" in {
      // This domain is only syntactically correct!
      DomainCompiler("""(define (domain the_three_towers)
                       |  (:requirements :strips)
                       |  (:predicates
                       |    (on_tower1 ?x ?y)
                       |    (on_tower2 ?x ?y)
                       |    (on_tower3 ?x ?y)
                       |    (on_top ?x)
                       |    (hand_empty)
                       |    (hand_holding ?x)
                       |  )
                       |  (:action take_from_tower1
                       |    :parameters(?x ?y)
                       |    :precondition (and (on_tower1 ?x ?y)
                       |                       (on_top ?x)
                       |                       (hand_empty))
                       |    :effect (and (not (hand_empty)) 
                       |                 (hand_holding ?x) 
                       |                 (not (on_top ?x)) 
                       |                 (on_top ?y)
                       |                 (not (on_tower1 ?x ?y)))
                       |  )
                       |  (:action put_on_tower1
                       |    :parameters(?x ?y)
                       |    :precondition (and (not (hand_empty))
                       |                       (hand_holding ?x)
                       |                       (on_top ?y))
                       |    :effect (and (hand_empty)
                       |                 (not (hand_holding ?x))
                       |                 (not (on_top ?y))
                       |                 (on_top ?x)
                       |                 (on_tower1 ?x ?y))
                       |  )
                       |  (:action take_from_tower2
                       |    :parameters(?x ?y)
                       |    :precondition (and (on_tower2 ?x ?y)
                       |                       (on_top ?x)
                       |                       (hand_empty))
                       |    :effect (and (not (hand_empty)) 
                       |                 (hand_holding ?x) 
                       |                 (not (on_top ?x)) 
                       |                 (on_top ?y)
                       |                 (not (on_tower2 ?x ?y)))
                       |  )
                       |  (:action put_on_tower2
                       |    :parameters(?x ?y)
                       |    :precondition (and (not (hand_empty))
                       |                       (hand_holding ?x)
                       |                       (on_top ?y))
                       |    :effect (and (hand_empty)
                       |                 (not (hand_holding ?x))
                       |                 (not (on_top ?y))
                       |                 (on_top ?x)
                       |                 (on_tower2 ?x ?y))
                       |  )
                       |  (:action take_from_tower3
                       |    :parameters(?x ?y)
                       |    :precondition (and (on_tower3 ?x ?y)
                       |                       (on_top ?x)
                       |                       (hand_empty))
                       |    :effect (and (not (hand_empty)) 
                       |                 (hand_holding ?x) 
                       |                 (not (on_top ?x)) 
                       |                 (on_top ?y)
                       |                 (not (on_tower3 ?x ?y)))
                       |  )
                       |  (:action put_on_tower1
                       |    :parameters(?x ?y)
                       |    :precondition (and (not (hand_empty))
                       |                       (hand_holding ?x)
                       |                       (on_top ?y))
                       |    :effect (and (hand_empty)
                       |                 (not (hand_holding ?x))
                       |                 (not (on_top ?y))
                       |                 (on_top ?x)
                       |                 (on_tower3 ?x ?y))
                       |  )
                       |)""".stripMargin) match {
        case Left(_) => fail()
        case _       =>
      }
    }
  }
}
