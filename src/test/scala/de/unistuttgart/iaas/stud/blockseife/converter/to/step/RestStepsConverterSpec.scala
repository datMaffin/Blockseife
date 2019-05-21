package de.unistuttgart.iaas.stud.blockseife.converter.to.step

import akka.NotUsed
import akka.actor.{ActorSystem, ExtendedActorSystem}
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Source}
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.TestKit
import akka.util.ByteString
import de.unistuttgart.iaas.stud.blockseife.Data.Step
import de.unistuttgart.iaas.stud.blockseife.MyJsonSupport
import org.mockito.{ArgumentMatchersSugar, IdiomaticMockito}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import spray.json._

import scala.concurrent.Future

class RestStepsConverterSpec
    extends TestKit(ActorSystem("RestStepConverterSpec"))
    with WordSpecLike
    with Matchers
    with IdiomaticMockito
    with ArgumentMatchersSugar
    with MyJsonSupport
    with BeforeAndAfterAll {

  implicit val executionContext = system.dispatcher
  implicit val materializer     = ActorMaterializer()

  val mockHttp = mock[HttpExt]

  // we need to add an mock system to mock http because system.log gets called
  val mockSystem = mock[ExtendedActorSystem]
  mockHttp.system shouldReturn mockSystem

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "The rest converter" should {
    "provide a rest interface for parsing steps" in {

      // Compact print a list of steps as json
      val steps: String =
        List(Step(0, "test", List("a", "b")), Step(1, "more testing", List("c", "d"))).toJson.compactPrint

      // Create the byte string and source of steps
      val byteStream = Source(List(ByteString(steps)))

      // mock the http call to return the steps
      mockHttp.singleRequest(*, *, *, *) shouldReturn Future(
        HttpResponse(entity = HttpEntity(contentType = ContentTypes.`application/json`, data = byteStream))
      )

      // no real data required; we are mocking the http response
      val srcOfSteps: Source[Step, Future[NotUsed]] =
        restStepsConverter(ContentTypes.NoContentType, Source(List(ByteString(""))))(
          Uri("http://localhost:333"),
          mockHttp
        )

      // Use the test sink subscriber to enable checking the results
      val sub = srcOfSteps.toMat(TestSink.probe[Step])(Keep.right).run()

      // Check if results are correct
      sub.request(3)
      sub.expectNext(Step(0, "test", List("a", "b")), Step(1, "more testing", List("c", "d")))
      sub.expectComplete()
    }
  }
}
