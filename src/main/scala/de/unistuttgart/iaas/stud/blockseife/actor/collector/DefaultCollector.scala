package de.unistuttgart.iaas.stud.blockseife.actor.collector

import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Materializer}
import de.unistuttgart.iaas.stud.blockseife.Data.{NotSupportedPredicates, Pddl_1_2_MinimalPredicates, Predicates}
import de.unistuttgart.iaas.stud.blockseife.MyJsonSupport
import de.unistuttgart.iaas.stud.blockseife.actor.collector.DefaultCollector.DefaultCollectorSettings
import spray.json.JsValue

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object DefaultCollector extends MyJsonSupport {
  def props(collectorSetting: DefaultCollectorSettings): Props = Props(new DefaultCollector(collectorSetting))

  final case class DefaultCollectorSettings(
      collectorUrl: Uri,
      unmarshallHttpResponseToPredicates: HttpResponse => Future[Predicates]
  )

  def unmarshalToPddl_1_2_MinimalPredicates(
      httpResponse: HttpResponse
  )(implicit materializer: Materializer, ec: ExecutionContext): Future[Pddl_1_2_MinimalPredicates] = {

    Unmarshal(httpResponse)
      .to[Map[String, JsValue]]
      .map { predicates =>
        Pddl_1_2_MinimalPredicates(predicates.map { t =>
          (t._1.toUpperCase, t._2)
        })
      }
  }

  def unmarshalToUnsupportedPredicates(
      httpResponse: HttpResponse
  )(implicit materializer: Materializer): Future[NotSupportedPredicates] = {

    Unmarshal(httpResponse).to[NotSupportedPredicates]
  }
}

class DefaultCollector(collectorSettings: DefaultCollectorSettings) extends Actor with ActorLogging with MyJsonSupport {

  val http                      = Http(context.system)
  implicit val executionContext = context.system.dispatcher
  implicit val materializer     = ActorMaterializer(ActorMaterializerSettings(context.system))

  override def receive: Receive = {
    case GetObjects =>
      val answerMessageDestination = sender()

      http
        .singleRequest(HttpRequest(uri = collectorSettings.collectorUrl + "/objects"))
        .map(Unmarshal(_).to[List[String]])
        .flatten
        .onComplete {
          case Success(planningObjects) =>
            answerMessageDestination ! Objects(planningObjects)
          case Failure(e) =>
            log.warning("Failed to get the object names: " + e.getMessage)
            throw CommunicationWithExternalCollectorServiceFailed // supervisor should probably just resume this actor
        }

    case GetPredicatesState =>
      val answerMessageDestination = sender()

      http
        .singleRequest(HttpRequest(uri = collectorSettings.collectorUrl + "/predicates"))
        .map(collectorSettings.unmarshallHttpResponseToPredicates)
        .flatten
        .onComplete {
          case Success(predicates) => answerMessageDestination ! PredicatesState(predicates)
          case Failure(e) =>
            log.warning("Failed to get the predicates: " + e.getMessage)
            throw CommunicationWithExternalCollectorServiceFailed // supervisor should probably just resume this actor
        }
  }
}
