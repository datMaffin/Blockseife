package de.unistuttgart.iaas.stud.blockseife.actor.planner

import akka.actor.{ActorSystem, ExtendedActorSystem, Props}
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, HttpResponse, Uri}
import akka.stream.scaladsl.Source
import akka.testkit.{TestKit, TestProbe}
import akka.util.ByteString
import de.unistuttgart.iaas.stud.blockseife.Data.{RawDomain, RawProblem}
import de.unistuttgart.iaas.stud.blockseife.MyJsonSupport
import org.mockito.{ArgumentMatchersSugar, IdiomaticMockito}
import org.mockito.captor.ArgCaptor
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.concurrent.Future

class PostDPInstantlyPlannerSpec
    extends TestKit(ActorSystem("PostDPInstantlyPlannerSpec"))
    with WordSpecLike
    with Matchers
    with IdiomaticMockito
    with ArgumentMatchersSugar
    with MyJsonSupport
    with BeforeAndAfterAll {

  implicit val executionContext = system.dispatcher

  // we need a mock system to mock http because system.log gets called
  private val mockSystem = mock[ExtendedActorSystem]

  class HttpInjectedDefaultPlanner(override val http: HttpExt, plannerSettings: PostDPInstantlyPlanner.Settings)
      extends PostDPInstantlyPlanner(plannerSettings)

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "The default planner actor" when {

    "no domain or problem was sent" should {

      val mockHttp = mock[HttpExt]
      mockHttp.system shouldReturn mockSystem

      mockHttp.singleRequest(*, *, *, *) shouldReturn Future(
        HttpResponse(entity = HttpEntity(contentType = ContentTypes.`application/json`, data = Source.empty))
      )

      val planner =
        system.actorOf(
          Props(new HttpInjectedDefaultPlanner(mockHttp, PostDPInstantlyPlanner.Settings(Uri("http://localhost:333"))))
        )

      "answer with the correct response to 'check id' message" in {

        val probe = TestProbe()
        planner.tell(CheckIds(0, 0), probe.ref)
        probe.expectMsg(700.milliseconds, IncorrectDomainAndProblemId(None, None))

      }
      "answer with the correct response to 'get solver response' message" in {

        val probe = TestProbe()
        planner.tell(GetSolverResponse, probe.ref)
        probe.expectMsg(700.milliseconds, MissingDomainAndProblem)

      }
    }
    "no domain was sent" should {

      val mockHttp = mock[HttpExt]
      mockHttp.system shouldReturn mockSystem

      mockHttp.singleRequest(*, *, *, *) shouldReturn Future(
        HttpResponse(entity = HttpEntity(contentType = ContentTypes.`application/json`, data = Source.empty))
      )

      val planner =
        system.actorOf(
          Props(new HttpInjectedDefaultPlanner(mockHttp, PostDPInstantlyPlanner.Settings(Uri("http://localhost:333"))))
        )

      val placeholderActor = TestProbe()

      planner.tell(PostProblem(RawProblem(""), 0), placeholderActor.ref)

      "answer with the correct response to 'check id' message" in {

        val probe = TestProbe()

        // trying multiple times is allowed / by design
        awaitAssert({
          planner.tell(CheckIds(0, 0), probe.ref)
          probe.expectMsg(500.milliseconds, IncorrectDomainId(None))
        }, 2.seconds)

      }
      "answer with the correct response to 'get solver response' message" in {

        val probe = TestProbe()

        // trying multiple times is allowed / by design
        awaitAssert({
          planner.tell(GetSolverResponse, probe.ref)
          probe.expectMsg(500.milliseconds, MissingDomain)
        }, 2.seconds)

      }
    }
    "no problem was sent" should {

      val mockHttp = mock[HttpExt]
      mockHttp.system shouldReturn mockSystem

      mockHttp.singleRequest(*, *, *, *) shouldReturn Future(
        HttpResponse(entity = HttpEntity(contentType = ContentTypes.`application/json`, data = Source.empty))
      )

      val planner =
        system.actorOf(
          Props(new HttpInjectedDefaultPlanner(mockHttp, PostDPInstantlyPlanner.Settings(Uri("http://localhost:333"))))
        )

      val placeholderActor = TestProbe()

      planner.tell(PostDomain(RawDomain(""), 0), placeholderActor.ref)

      "answer with the correct response to 'check id' message" in {

        val probe = TestProbe()

        // trying multiple times is allowed / by design
        awaitAssert({
          planner.tell(CheckIds(0, 0), probe.ref)
          probe.expectMsg(500.milliseconds, IncorrectProblemId(None))
        }, 2.seconds)

      }
      "answer with the correct response to 'get solver response' message" in {

        val probe = TestProbe()

        // trying multiple times is allowed / by design
        awaitAssert({
          planner.tell(GetSolverResponse, probe.ref)
          probe.expectMsg(500.milliseconds, MissingProblem)
        }, 2.seconds)

      }
    }
    "domain and problem was correctly sent" should {

      val mockHttp = mock[HttpExt]
      mockHttp.system shouldReturn mockSystem

      mockHttp.singleRequest(*, *, *, *) shouldReturn Future(
        HttpResponse(entity = HttpEntity(contentType = ContentTypes.`application/json`, data = Source.empty))
      )

      val planner =
        system.actorOf(
          Props(new HttpInjectedDefaultPlanner(mockHttp, PostDPInstantlyPlanner.Settings(Uri("http://localhost:333"))))
        )

      val placeholderActor = TestProbe()

      planner.tell(PostDomain(RawDomain(""), 0), placeholderActor.ref)
      planner.tell(PostProblem(RawProblem(""), 0), placeholderActor.ref)
      planner.tell(Solve, placeholderActor.ref)

      "answer with the correct response to 'check id' message" in {

        val probe = TestProbe()

        // trying multiple times is allowed / by design
        awaitAssert({
          planner.tell(CheckIds(0, 0), probe.ref)
          probe.expectMsg(500.milliseconds, CorrectIds)
        }, 2.seconds)

      }
      "answer with the correct response to 'get solver response' message" in {

        val probe = TestProbe()

        // trying multiple times is allowed
        awaitAssert({
          planner.tell(GetSolverResponse, probe.ref)
          probe.expectMsgType[SuccessfulSolverResponse](500.milliseconds)
        }, 2.seconds)

      }
      "domain should be posted to correct url" in {

        val localMockHttp = mock[HttpExt]
        localMockHttp.system shouldReturn mockSystem

        localMockHttp.singleRequest(*, *, *, *) shouldReturn Future(
          HttpResponse(entity = HttpEntity(contentType = ContentTypes.`application/json`, data = Source.empty))
        )

        val localPlanner =
          system.actorOf(
            Props(
              new HttpInjectedDefaultPlanner(localMockHttp,
                                             PostDPInstantlyPlanner.Settings(Uri("http://localhost:333"))))
          )

        val localCaptor = ArgCaptor[HttpRequest]
        val localProbe  = TestProbe()

        localPlanner.tell(PostDomain(RawDomain(""), 0), localProbe.ref)

        awaitAssert({ localMockHttp.singleRequest(localCaptor.capture, *, *, *) wasCalled once }, 5.seconds)

        localCaptor.value.uri shouldBe Uri("http://localhost:333/domain")

      }
      "problem should be posted to correct url" in {

        val localMockHttp = mock[HttpExt]
        localMockHttp.system shouldReturn mockSystem

        localMockHttp.singleRequest(*, *, *, *) shouldReturn Future(
          HttpResponse(entity = HttpEntity(contentType = ContentTypes.`application/json`, data = Source.empty))
        )

        val localPlanner =
          system.actorOf(
            Props(
              new HttpInjectedDefaultPlanner(localMockHttp,
                                             PostDPInstantlyPlanner.Settings(Uri("http://localhost:333"))))
          )

        val localCaptor = ArgCaptor[HttpRequest]
        val localProbe  = TestProbe()

        localPlanner.tell(PostProblem(RawProblem(""), 0), localProbe.ref)

        awaitAssert({ localMockHttp.singleRequest(localCaptor.capture, *, *, *) wasCalled once }, 5.seconds)

        localCaptor.value.uri shouldBe Uri("http://localhost:333/problem")

      }
    }

    "wrong domain and problem were sent and no solve attempted" should {

      val mockHttp = mock[HttpExt]
      mockHttp.system shouldReturn mockSystem

      mockHttp.singleRequest(*, *, *, *) shouldReturn Future(
        HttpResponse(entity = HttpEntity(contentType = ContentTypes.`application/json`, data = Source.empty))
      )

      val planner =
        system.actorOf(
          Props(new HttpInjectedDefaultPlanner(mockHttp, PostDPInstantlyPlanner.Settings(Uri("http://localhost:333"))))
        )

      val placeholderActor = TestProbe()

      planner.tell(PostDomain(RawDomain(""), 0), placeholderActor.ref)
      planner.tell(PostProblem(RawProblem(""), 0), placeholderActor.ref)

      "answer with the correct response to 'check id' message" in {

        val probe = TestProbe()

        // trying multiple times is allowed / by design
        awaitAssert({
          planner.tell(CheckIds(1, 1), probe.ref)
          probe.expectMsg(500.milliseconds, IncorrectDomainAndProblemId(Some(0), Some(0)))
        }, 2.seconds)

      }
      "answer with the correct response to 'get solver response' message" in {

        val probe = TestProbe()

        // trying multiple times is allowed / by design
        awaitAssert({
          planner.tell(GetSolverResponse, probe.ref)
          probe.expectMsg(500.milliseconds, NoSuccessfulResponseOccurred)
        }, 2.seconds)

      }
    }

    // TODO: Testing if a stream was discarded does not seam to work...
    //
    //    "a second successful solve was sent" should {
    //
    //      val mockHttp = mock[HttpExt]
    //      mockHttp.system shouldReturn mockSystem
    //
    //      mockHttp.singleRequest(*, *, *, *) shouldReturn Future(
    //        HttpResponse(
    //          entity =
    //            HttpEntity(contentType = ContentTypes.`application/json`, data = Source.single(ByteString("testing")))
    //        )
    //      )
    //
    //      val planner =
    //        system.actorOf(
    //          Props(new HttpInjectedDefaultPlanner(mockHttp, DefaultPlannerSettings(Uri("http://localhost:333"))))
    //        )
    //
    //      val placeholderActor = TestProbe()
    //
    //      planner.tell(Planner.PostDomain(RawDomain(""), 0), placeholderActor.ref)
    //      planner.tell(Planner.PostProblem(RawProblem(""), 0), placeholderActor.ref)
    //
    //      "the old solver response stream is discarded" in {
    //
    //        val probe = TestProbe()
    //        planner.tell(Planner.Solve, probe.ref)
    //
    //        // trying multiple times is allowed / by design
    //        val solverResponse = awaitAssert({
    //          planner.tell(Planner.GetSolverResponse, probe.ref)
    //          probe.expectMsgType[Planner.SuccessfulSolverResponse](500.milliseconds)
    //        }, 2.seconds)
    //
    //        val stream = solverResponse.solverOutput.byteStream
    //
    //        planner.tell(Planner.Solve, probe.ref)
    //        // trying multiple times is allowed / by design
    //        awaitAssert({
    //          planner.tell(Planner.GetSolverResponse, probe.ref)
    //          probe.expectMsgType[Planner.SuccessfulSolverResponse](500.milliseconds)
    //        }, 2.seconds)
    //
    //        awaitAssert({
    //          // stream should have been discarded
    //          stream.runWith(TestSink.probe[ByteString])(ActorMaterializer()).request(1).expectComplete()
    //        }, 10.seconds)
    //
    //      }
    //    }
    "a solve takes a long time" should {
      val mockHttp = mock[HttpExt]
      mockHttp.system shouldReturn mockSystem

      mockHttp.singleRequest(*, *, *, *) shouldReturn Future {
        Thread.sleep(2000) // wait 4 * 500ms for slow pcs
        HttpResponse(
          entity =
            HttpEntity(contentType = ContentTypes.`application/json`, data = Source.single(ByteString("testing")))
        )
      }

      val planner =
        system.actorOf(
          Props(new HttpInjectedDefaultPlanner(mockHttp, PostDPInstantlyPlanner.Settings(Uri("http://localhost:333"))))
        )

      val placeholderActor = TestProbe()

      planner.tell(PostDomain(RawDomain(""), 0), placeholderActor.ref)
      planner.tell(PostProblem(RawProblem(""), 0), placeholderActor.ref)
      planner.tell(Solve, placeholderActor.ref)

      "asking for solver status responds with the 'waiting for solver response' message" in {
        val probe = TestProbe()
        planner.tell(GetSolverResponse, probe.ref)
        probe.expectMsg(500.milliseconds, WaitingForSolverResponse)
        Thread.sleep(2000) // wait for single request to prevent error messages
      }
    }
  }
}
