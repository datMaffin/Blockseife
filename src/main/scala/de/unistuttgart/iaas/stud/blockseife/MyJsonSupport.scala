package de.unistuttgart.iaas.stud.blockseife

import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import de.unistuttgart.iaas.stud.blockseife.Data.Step
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

trait MyJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val jsonStreamingSupport: JsonEntityStreamingSupport = EntityStreamingSupport.json()
  implicit val stepFormat: RootJsonFormat[Step]                 = jsonFormat3(Step.apply)
}
