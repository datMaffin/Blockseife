package de.unistuttgart.iaas.stud.blockseife.parser.domains.pddl._1._2.minimal

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import de.unistuttgart.iaas.stud.blockseife.Data.{Pddl_1_2_MinimalPredicates, Step}
import de.unistuttgart.iaas.stud.blockseife.{MyJsonSupport, parser}
import de.unistuttgart.iaas.stud.blockseife.parser.Pddl_1_2_MinimalParsedDomain
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import spray.json.{JsArray, JsString}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class PreconditionCheckerSpec
    extends TestKit(ActorSystem("PreconditionCheckerSpec"))
    with WordSpecLike
    with BeforeAndAfterAll
    with Matchers {

  implicit val executionContext = system.dispatcher
  implicit val materializer     = ActorMaterializer()

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "The precondition checker" should {
    "evaluate to true" in {
      val parsedDomain = Pddl_1_2_MinimalParsedDomain(
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
        ))

      val predicates = Pddl_1_2_MinimalPredicates(Map(("LIGHT_ON", JsArray()), ("SUN_LIGHT", JsArray(JsString("l1")))))
      val step       = Step(1, "SWITCH_LIGHT_ON", List("L2"))

      val eventualResult = parser.preconditionCheckerPddl_1_2_Minimal(parsedDomain, predicates, step)
      Await.result(eventualResult, 500.milliseconds) shouldBe true
    }
  }
}
