package de.unistuttgart.iaas.stud.blockseife.converter.to

import akka.NotUsed
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{Framing, Source}
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.ByteString
import de.unistuttgart.iaas.stud.blockseife.Data.{SolverOutput, Step}
import de.unistuttgart.iaas.stud.blockseife.MyJsonSupport

import scala.concurrent.{ExecutionContext, Future}
import scala.util.matching.Regex

package object step extends MyJsonSupport {
  def alreadyJsonStepsConverter(solverOutput: SolverOutput)(implicit materializer: Materializer): Source[Step, Any] = {
    if (solverOutput.contentType != ContentTypes.`application/json`) {
      // TODO: better error handling (With an own class that extends Throwable for pattern matching)
      return Source.failed(new Throwable("The solver did not specify the mimetype as 'application/json'."))
    }

    solverOutput.byteStream
      .via(jsonStreamingSupport.framingDecoder)
      .mapAsync(2)(Unmarshal(_).to[Step])
  }

  // declare outside to prevent compilation for the regex at every call of the converter function
  // (StepNr) (Action) (Variables)
  val fastForwardRegex: Regex = raw"\D*(\d+): (\S*) (.*)\s*".r

  def rawFFOutputStepsConverter(solverOutput: SolverOutput)(implicit materializer: Materializer): Source[Step, Any] = {
    solverOutput.byteStream
      .via(Framing.delimiter(ByteString("\n"), 1024, allowTruncation = true))
      .map(_.utf8String)
      .mapConcat({ // using map concat to enable returning "nothing" (TODO: maybe map + filter is faster?)
        case fastForwardRegex(idx, action, variables) =>
          List(Step(idx.toInt, action.toUpperCase, variables.map(_.toUpper).split(' ').toList))
        case _ =>
          List.empty
      })
  }

  def restStepsConverter(contentType: ContentType, byteStringSource: Source[ByteString, Any])(restUrl: Uri,
                                                                                              http: HttpExt)(
      implicit executionContext: ExecutionContext,
      materializer: ActorMaterializer): Source[Step, Future[NotUsed]] = {

    // create the request entity with the data from the solver response
    val httpEntity     = HttpEntity(contentType, byteStringSource)
    val httpRequest    = HttpRequest(uri = restUrl, entity = httpEntity)
    val responseFuture = http.singleRequest(httpRequest)

    Source.fromFutureSource(
      responseFuture.map(response => Unmarshal(response.entity).to[Source[Step, NotUsed]]).flatten)
  }
}
