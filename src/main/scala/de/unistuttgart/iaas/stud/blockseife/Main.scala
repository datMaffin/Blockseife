package de.unistuttgart.iaas.stud.blockseife

import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.stream.ActorMaterializer

import de.unistuttgart.iaas.stud.blockseife.actor.collector.DefaultCollector
import de.unistuttgart.iaas.stud.blockseife.actor.pipeline
import de.unistuttgart.iaas.stud.blockseife.actor.pipeline.Pipeline
import de.unistuttgart.iaas.stud.blockseife.actor.planner.PostDPInstantlyPlanner
import de.unistuttgart.iaas.stud.blockseife.actor.scheduler.PreconditionCheckingScheduler
import de.unistuttgart.iaas.stud.blockseife.converter.to.problem.problemPddl_1_2_MinimalConverter
import de.unistuttgart.iaas.stud.blockseife.converter.to.step.rawFFOutputStepsConverter
import de.unistuttgart.iaas.stud.blockseife.parser.{pddl_1_2_MinimalDomainParser, preconditionCheckerPddl_1_2_Minimal}

object Main extends App {

  println()
  println("Starting the demo pipeline")
  println("==========================")
  println()
  println("For more information look into the example folder.")
  println()
  println("To exit press ENTER")
  println()

  val system = ActorSystem("example-system")

  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  final implicit val materializer: ActorMaterializer      = ActorMaterializer.create(system)

  try {
    // Create top level supervisor
    val p = system.actorOf(
      Pipeline.props(
        Pipeline.Settings(
          Pipeline.UseDefaultCollector(
            DefaultCollector
              .Settings(Uri("http://localhost:5001"), DefaultCollector.unmarshalToPddl_1_2_MinimalPredicates)
          ),
          Pipeline.UsePostDPInstantlyPlanner(
            PostDPInstantlyPlanner.Settings(Uri("http://localhost:5002"))
          ),
          Pipeline.UsePreconditionCheckingScheduler(
            PreconditionCheckingScheduler
              .Settings(Uri("http://localhost:5001/action"), 1.second, preconditionCheckerPddl_1_2_Minimal)
          ),
          problemPddl_1_2_MinimalConverter,
          rawFFOutputStepsConverter,
          pddl_1_2_MinimalDomainParser
        )
      )
    )

    p ! pipeline.Start(
      pipeline.RawGoal("""(define (problem save_energy)
                         |  (:domain lighting)
                         |  (:objects l1 l2)
                         |  (:goal (and (or (and (light_on l1) (not (sun_light l1)))
                         |                  (and (sun_light l1) (not (light_on l1))))
                         |              (or (and (light_on l2) (not (sun_light l2)))
                         |                  (and (sun_light l2) (not (light_on l2))))))
                         |)""".stripMargin),
      Data.RawDomain("""(define (domain lighting)
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
                       |)
                       |""".stripMargin)
    )

    // Exit the system after ENTER is pressed
    StdIn.readLine()
  } finally {
    system.terminate()
  }

}
