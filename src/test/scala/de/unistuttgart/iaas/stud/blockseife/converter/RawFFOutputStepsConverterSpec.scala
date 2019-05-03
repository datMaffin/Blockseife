package de.unistuttgart.iaas.stud.blockseife.converter

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ContentTypes
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Source}
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.TestKit
import akka.util.ByteString
import de.unistuttgart.iaas.stud.blockseife.Data.{SolverOutput, Step}
import de.unistuttgart.iaas.stud.blockseife.MyJsonSupport
import de.unistuttgart.iaas.stud.blockseife.converter.to.step.rawFFOutputStepsConverter
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class RawFFOutputStepsConverterSpec
    extends TestKit(ActorSystem("RawFFOutputStepsConverterSpec"))
    with WordSpecLike
    with MyJsonSupport
    with BeforeAndAfterAll {

  implicit val executionContext = system.dispatcher
  implicit val materializer     = ActorMaterializer()

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "The converter" should {

    val fastForwardOutput = """
                              |ff: parsing domain file
                              |domain 'BLOCKS' defined
                              | ... done.
                              |ff: parsing problem file
                              |problem 'BLOCKS-10-0' defined
                              | ... done.
                              |
                              |
                              |
                              |Cueing down from goal distance:    9 into depth [1][2]
                              |                                   8            [1][2]
                              |                                   7            [1][2]
                              |                                   6            [1][2]
                              |                                   5            [1][2][3][4]
                              |                                   4            [1][2]
                              |                                   3            [1][2]
                              |                                   2            [1]
                              |                                   1            [1]
                              |                                   0
                              |
                              |Cueing down from goal distance:    2 into depth [1]
                              |                                   1            [1]
                              |                                   0
                              |
                              |Cueing down from goal distance:    2 into depth [1]
                              |                                   1            [1]
                              |                                   0
                              |
                              |Cueing down from goal distance:    2 into depth [1]
                              |                                   1            [1]
                              |                                   0
                              |
                              |Cueing down from goal distance:    2 into depth [1]
                              |                                   1            [1]
                              |                                   0
                              |
                              |Cueing down from goal distance:    2 into depth [1]
                              |                                   1            [1]
                              |                                   0
                              |
                              |Cueing down from goal distance:    2 into depth [1]
                              |                                   1            [1]
                              |                                   0
                              |
                              |Cueing down from goal distance:    2 into depth [1]
                              |                                   1            [1]
                              |                                   0
                              |
                              |Cueing down from goal distance:    2 into depth [1]
                              |                                   1            [1]
                              |                                   0
                              |
                              |ff: found legal plan as follows
                              |
                              |step    0: UNSTACK C E
                              |        1: PUT-DOWN C
                              |        2: UNSTACK E J
                              |        3: PUT-DOWN E
                              |        4: UNSTACK J B
                              |        5: PUT-DOWN J
                              |        6: UNSTACK B G
                              |        7: PUT-DOWN B
                              |        8: UNSTACK G H
                              |        9: PUT-DOWN G
                              |       10: UNSTACK H A
                              |       11: PUT-DOWN H
                              |       12: UNSTACK A D
                              |       13: PUT-DOWN A
                              |       14: UNSTACK D I
                              |       15: PUT-DOWN D
                              |       16: PICK-UP G
                              |       17: STACK G I
                              |       18: PICK-UP A
                              |       19: STACK A G
                              |       20: PICK-UP B
                              |       21: STACK B A
                              |       22: PICK-UP H
                              |       23: STACK H B
                              |       24: PICK-UP E
                              |       25: STACK E H
                              |       26: PICK-UP J
                              |       27: STACK J E
                              |       28: PICK-UP F
                              |       29: STACK F J
                              |       30: PICK-UP C
                              |       31: STACK C F
                              |       32: PICK-UP D
                              |       33: STACK D C
                              |
                              |
                              |time spent:    0.00 seconds instantiating 220 easy, 0 hard action templates
                              |               0.00 seconds reachability analysis, yielding 131 facts and 220 actions
                              |               0.00 seconds creating final representation with 131 relevant facts
                              |               0.00 seconds building connectivity graph
                              |               0.00 seconds searching, evaluating 63 states, to a max depth of 4
                              |               0.00 seconds total time
                              |
                              |""".stripMargin

    "correctly convert the output of the FastForward solver as a byte string into a stream of steps" in {

      // Create the byte stream and source of steps
      val byteStream = Source(List(ByteString(fastForwardOutput)))
      val srcOfSteps = rawFFOutputStepsConverter(SolverOutput(ContentTypes.`text/plain(UTF-8)`, byteStream))

      // Use the test sink subscriber to enable checking the results
      val sub = srcOfSteps.toMat(TestSink.probe[Step])(Keep.right).run()

      // Check if results are correct
      sub.request(10)
      sub.expectNext(
        Step(0, "UNSTACK", List("C", "E")),
        Step(1, "PUT-DOWN", List("C")),
        Step(2, "UNSTACK", List("E", "J")),
        Step(3, "PUT-DOWN", List("E")),
        Step(4, "UNSTACK", List("J", "B")),
        Step(5, "PUT-DOWN", List("J")),
        Step(6, "UNSTACK", List("B", "G")),
        Step(7, "PUT-DOWN", List("B")),
        Step(8, "UNSTACK", List("G", "H")),
        Step(9, "PUT-DOWN", List("G"))
      )
      sub.request(25)
      sub.expectNextN(24)
      sub.expectComplete()
    }

    "correctly convert a sliced output of the FastForward solver as a byte string stream into a stream of steps" in {

      // Arbitrary slicing of the input into multiple byte strings
      val slicedFastForwardOutput =
        fastForwardOutput
          .split(" ")
          .map(_ + " ") // need to add the space that was removed while executing the split
          .map(ByteString.apply)
          .toList

      // Create the byte stream and source of steps
      val byteStream = Source(slicedFastForwardOutput)
      val srcOfSteps = rawFFOutputStepsConverter(SolverOutput(ContentTypes.`text/plain(UTF-8)`, byteStream))

      // Use the test sink subscriber to enable checking the results
      val sub = srcOfSteps.toMat(TestSink.probe[Step])(Keep.right).run()

      // Check if results are correct
      sub.request(10)
      sub.expectNext(
        Step(0, "UNSTACK", List("C", "E")),
        Step(1, "PUT-DOWN", List("C")),
        Step(2, "UNSTACK", List("E", "J")),
        Step(3, "PUT-DOWN", List("E")),
        Step(4, "UNSTACK", List("J", "B")),
        Step(5, "PUT-DOWN", List("J")),
        Step(6, "UNSTACK", List("B", "G")),
        Step(7, "PUT-DOWN", List("B")),
        Step(8, "UNSTACK", List("G", "H")),
        Step(9, "PUT-DOWN", List("G"))
      )
      sub.request(25)
      sub.expectNextN(24)
      sub.expectComplete()
    }
  }
}
