package de.unistuttgart.iaas.stud.blockseife.actor.collector

import akka.actor.{ActorSystem, ExtendedActorSystem, Props}
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, HttpResponse, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.testkit.{TestKit, TestProbe}
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import de.unistuttgart.iaas.stud.blockseife.Data.Pddl_1_2_MinimalPredicates
import de.unistuttgart.iaas.stud.blockseife.MyJsonSupport
import de.unistuttgart.iaas.stud.blockseife.actor.collector.DefaultCollector.DefaultCollectorSettings
import org.mockito.{ArgumentMatchersSugar, IdiomaticMockito}
import org.mockito.captor.ArgCaptor
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.concurrent.Future

class DefaultCollectorSpec
    extends TestKit(
      ActorSystem(
        "PlannerSpec",
        ConfigFactory.parseString("""
    akka.loggers = ["akka.testkit.TestEventListener"]
    akka.stdout-loglevel = "OFF"
    akka.loglevel = "OFF"
  """)
      ))
    with WordSpecLike
    with Matchers
    with IdiomaticMockito
    with ArgumentMatchersSugar
    with MyJsonSupport
    with BeforeAndAfterAll {

  implicit val materializer     = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  // we need a mock system to mock http because system.log gets called
  private val mockSystem = mock[ExtendedActorSystem]

  class HttpInjectedDefaultCollector(override val http: HttpExt, collectorSettings: DefaultCollectorSettings)
      extends DefaultCollector(collectorSettings)

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "The default collector actor" when {

    "a correct collector url was given" should {

      "get the objects from the correct url" in {

        val mockHttp = mock[HttpExt]
        mockHttp.system shouldReturn mockSystem

        mockHttp.singleRequest(*, *, *, *) shouldReturn Future(
          HttpResponse(
            entity = HttpEntity(contentType = ContentTypes.`application/json`, data = Source.single(ByteString("[]"))))
        )

        val collector = system.actorOf(
          Props(
            new HttpInjectedDefaultCollector(
              mockHttp,
              DefaultCollectorSettings(Uri("http://localhost:333"),
                                       DefaultCollector.unmarshalToPddl_1_2_MinimalPredicates))))

        val captor = ArgCaptor[HttpRequest]
        val probe  = TestProbe()

        collector.tell(GetObjects, probe.ref)

        awaitAssert({ mockHttp.singleRequest(captor.capture, *, *, *) wasCalled once }, 5.seconds)

        captor.value.uri shouldBe Uri("http://localhost:333/objects")

      }

      "get the predicates from the correct url" in {

        val mockHttp = mock[HttpExt]
        mockHttp.system shouldReturn mockSystem

        mockHttp.singleRequest(*, *, *, *) shouldReturn Future(
          HttpResponse(
            entity = HttpEntity(contentType = ContentTypes.`application/json`, data = Source.single(ByteString("{}"))))
        )

        val collector = system.actorOf(
          Props(
            new HttpInjectedDefaultCollector(
              mockHttp,
              DefaultCollectorSettings(Uri("http://localhost:333"),
                                       DefaultCollector.unmarshalToPddl_1_2_MinimalPredicates))))

        val captor = ArgCaptor[HttpRequest]
        val probe  = TestProbe()

        collector.tell(GetPredicatesState, probe.ref)

        awaitAssert({ mockHttp.singleRequest(captor.capture, *, *, *) wasCalled once }, 5.seconds)

        captor.value.uri shouldBe Uri("http://localhost:333/predicates")

      }

      "return the correct message for 'get object' to the sender" in {

        val mockHttp = mock[HttpExt]
        mockHttp.system shouldReturn mockSystem

        mockHttp.singleRequest(*, *, *, *) shouldReturn Future(
          HttpResponse(
            entity = HttpEntity(contentType = ContentTypes.`application/json`, data = Source.single(ByteString("[]"))))
        )

        val collector = system.actorOf(
          Props(
            new HttpInjectedDefaultCollector(
              mockHttp,
              DefaultCollectorSettings(Uri("http://localhost:333"),
                                       DefaultCollector.unmarshalToPddl_1_2_MinimalPredicates))))

        val captor = ArgCaptor[HttpRequest]
        val probe  = TestProbe()

        collector.tell(GetObjects, probe.ref)

        probe.expectMsg(1000.milliseconds, Objects(Seq()))

      }
      "return the correct message for 'get predicate state' to the sender" in {

        val mockHttp = mock[HttpExt]
        mockHttp.system shouldReturn mockSystem

        mockHttp.singleRequest(*, *, *, *) shouldReturn Future(
          HttpResponse(
            entity = HttpEntity(contentType = ContentTypes.`application/json`, data = Source.single(ByteString("{}"))))
        )

        val collector = system.actorOf(
          Props(
            new HttpInjectedDefaultCollector(
              mockHttp,
              DefaultCollectorSettings(Uri("http://localhost:333"),
                                       DefaultCollector.unmarshalToPddl_1_2_MinimalPredicates))))

        val captor = ArgCaptor[HttpRequest]
        val probe  = TestProbe()

        collector.tell(GetPredicatesState, probe.ref)

        probe.expectMsg(500.milliseconds, PredicatesState(Pddl_1_2_MinimalPredicates(Map.empty)))

      }
    }
    "a incorrect collector url was given" should {
      // TODO: test exceptions; does not seam to be trivial
      //      "throw the correct exception for 'get object'" in {}
      //      "throw the correct exception for 'get predicate state'" in {}

      val mockHttp = mock[HttpExt]
      mockHttp.system shouldReturn mockSystem

      mockHttp.singleRequest(*, *, *, *) shouldReturn Future.failed(new Throwable("Request failed"))

      val collector = system.actorOf(
        Props(
          new HttpInjectedDefaultCollector(
            mockHttp,
            DefaultCollectorSettings(Uri("http://localhost:333/validurl"),
                                     DefaultCollector.unmarshalToPddl_1_2_MinimalPredicates))))

      "return no message for 'get object' to the sender" in {

        val probe = TestProbe()
        collector.tell(GetObjects, probe.ref)
        probe.expectNoMessage(500.milliseconds)

      }
      "return no message for 'get predicate state' to the sender" in {

        val probe = TestProbe()
        collector.tell(GetPredicatesState, probe.ref)
        probe.expectNoMessage(500.milliseconds)

      }
    }
  }
}
