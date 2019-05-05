package de.unistuttgart.iaas.stud.blockseife.converter.to.step

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ContentTypes
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Source}
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.TestKit
import akka.util.ByteString
import de.unistuttgart.iaas.stud.blockseife.Data.{SolverOutput, Step}
import de.unistuttgart.iaas.stud.blockseife.MyJsonSupport
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}
import spray.json._

class AlreadyJsonStepsConverterSpec
    extends TestKit(ActorSystem("AlreadyJsonStepsConverterSpec"))
    with WordSpecLike
    with MyJsonSupport
    with BeforeAndAfterAll {

  implicit val executionContext = system.dispatcher
  implicit val materializer     = ActorMaterializer()

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "The converter" should {
    "correctly convert a json in form of a byte string into a stream of steps" in {

      // Compact print a list of steps as json
      val steps: String =
        List(Step(0, "test", List("a", "b")), Step(1, "more testing", List("c", "d"))).toJson.compactPrint

      // Create the byte stream and source of steps
      val byteStream = Source(List(ByteString(steps)))

      val srcOfSteps = alreadyJsonStepsConverter(SolverOutput(ContentTypes.`application/json`, byteStream))

      // Use the test sink subscriber to enable checking the results
      val sub = srcOfSteps.toMat(TestSink.probe[Step])(Keep.right).run()

      // Check if results are correct
      sub.request(3)
      sub.expectNext(Step(0, "test", List("a", "b")), Step(1, "more testing", List("c", "d")))
      sub.expectComplete()
    }

    "correctly convert a json in form of a sliced byte string into a stream of steps" in {

      // Pretty print a list of steps as json
      val steps: String =
        List(Step(0, "test", List("a", "b")), Step(1, "more testing", List("c", "d"))).toJson.prettyPrint

      // Arbitrary slicing of the input into multiple byte strings
      val slicedSteps = List(
        steps.slice(0, 20),
        steps.slice(20, 40),
        steps.slice(40, 60),
        steps.slice(60, 80),
        steps.slice(80, 100),
        steps.slice(100, Int.MaxValue)
      ).map(ByteString.apply)

      // Create the byte stream and source of steps
      val byteStream = Source(slicedSteps)
      val srcOfSteps = alreadyJsonStepsConverter(SolverOutput(ContentTypes.`application/json`, byteStream))

      // Use the test sink subscriber to enable checking the results
      val sub = srcOfSteps.toMat(TestSink.probe[Step])(Keep.right).run()

      // Check if results are correct
      sub.request(3)
      sub.expectNext(Step(0, "test", List("a", "b")), Step(1, "more testing", List("c", "d")))
      sub.expectComplete()
    }
  }
}
