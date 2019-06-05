package de.unistuttgart.iaas.stud.blockseife.converter.to.problem

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.{TestKit, TestProbe}
import de.unistuttgart.iaas.stud.blockseife.Data.{Pddl_1_2_MinimalPredicates, RawProblem}
import de.unistuttgart.iaas.stud.blockseife.MyJsonSupport
import de.unistuttgart.iaas.stud.blockseife.actor.collector
import de.unistuttgart.iaas.stud.blockseife.actor.collector.PredicatesState
import de.unistuttgart.iaas.stud.blockseife.actor.pipeline.RawGoal
import de.unistuttgart.iaas.stud.blockseife.parser.Pddl_1_2_MinimalParsedDomain
import de.unistuttgart.iaas.stud.blockseife.parser.domains.pddl._1._2.minimal.{
  ActionDef,
  ActionDefBody,
  AtomicFormulaSkeleton,
  AtomicFormulaTerms,
  DomainRoot,
  Name,
  NegatedAtomicFormulaTerms,
  PredicatesDef,
  RequireDef,
  Variable
}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}
import spray.json.{JsArray, JsString}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

class ProblemPddl_1_2_MinimalConverterSpec
    extends TestKit(ActorSystem("ProblemPddl_1_2_MinimalConverterSpec"))
    with WordSpecLike
    with MyJsonSupport
    with BeforeAndAfterAll {

  implicit val executionContext = system.dispatcher
  implicit val materializer     = ActorMaterializer()

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }
  "The converter" should {
    "correctly transform a goal into a problem" in {

      val collectorProbe = TestProbe()

      val eventualProblem = problemPddl_1_2_MinimalConverter(
        RawGoal("""(define (problem save_energy)
          |  (:domain lighting)
          |  (:objects l1 l2)
          |  (:goal (and (or (and (light_on l1) (not (sun_light l1)))
          |                  (and (sun_light l1) (not (light_on l1))))
          |              (or (and (light_on l2) (not (sun_light l2)))
          |                  (and (sun_light l2) (not (light_on l2))))))
          |)""".stripMargin),
        Pddl_1_2_MinimalParsedDomain(
          DomainRoot(
            Name("LIGHTING"),
            Some(RequireDef(Seq(":STRIPS"))),
            None,
            None,
            None,
            Some(PredicatesDef(Seq(AtomicFormulaSkeleton("LIGHT_ON", Seq(Variable("X"))),
                                   AtomicFormulaSkeleton("SUN_LIGHT", Seq(Variable("X")))))),
            None,
            None,
            Seq(
              ActionDef(
                "SWITCH_LIGHT_ON",
                Seq(Variable("X")),
                ActionDefBody(Some(NegatedAtomicFormulaTerms("LIGHT_ON", Seq(Variable("X")))),
                              Some(AtomicFormulaTerms("LIGHT_ON", Seq(Variable("X")))))
              ),
              ActionDef(
                "SWITCH_LIGHT_OFF",
                Seq(Variable("X")),
                ActionDefBody(Some(AtomicFormulaTerms("LIGHT_ON", Seq(Variable("X")))),
                              Some(NegatedAtomicFormulaTerms("LIGHT_ON", Seq(Variable("X")))))
              )
            )
          )),
        collectorProbe.ref
      )

      collectorProbe.expectMsg(500.milliseconds, collector.GetPredicatesState)
      collectorProbe.reply(PredicatesState(
        Pddl_1_2_MinimalPredicates(Map(("light_on", JsArray(JsString("l1"))), ("sun_light", JsArray(JsString("l1")))))))

      val problemApproximation =
        raw"\(define\s*\(problem\s*save_energy\)\s*\(:domain\s*lighting\)\s*\(:objects\s*l1\s*l2\)\s*\(:goal\s*\(and\s*\(or\s*\(and\s*\(light_on\s*l1\)\s*\(not\s*\(sun_light\s*l1\)\s*\)\s*\)\s*\(and\s*\(sun_light\s*l1\)\s*\(not\s*\(light_on\s*l1\)\s*\)\s*\)\s*\)\s*\(or\s*\(and\s*\(light_on\s*l2\)\s*\(not\s*\(sun_light\s*l2\)\s*\)\s*\)\s*\(and\s*\(sun_light\s*l2\)\s*\(not\s*\(light_on\s*l2\)\s*\)\s*\)\s*\)\s*\)\s*\)\s*\(:init\s*\(light_on\s*l1\)\s*\(sun_light\s*l1\)\s*\)\s*\)\s*".r

      Await.result(eventualProblem, 10.seconds) match {
        case RawProblem(problemApproximation(_*)) => // Success
        case _                                    => fail()
      }
    }
  }
}
