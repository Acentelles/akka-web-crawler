package app.routes

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}

import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.PathDirectives.path

import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import app.Model._
import app.actors.Master
import app.actors.Master._
import io.circe.generic.auto._
import io.circe.syntax._

class Crawl(implicit val system: ActorSystem) {

  def routes: Route = path("") {
    get {
      handleWebSocketMessages(start)
    }
  }

  private def start: Flow[Message, Message, NotUsed] = {
    val masterRef: ActorRef = system.actorOf(Props(new Master))
    Flow.fromSinkAndSource(
      sinkMessages(masterRef),
      sourceMessages(masterRef)
    )
  }

  def sinkMessages(master: ActorRef): Sink[Message, NotUsed] =
    Flow[Message].map {
      // transform websocket message to domain message
      case TextMessage.Strict(msg) =>
        if (msg == "stop") {
          StopCrawling
        } else {
          NotCheckedUrl(msg).validate
        }
    }.to(Sink.actorRef(master, PoisonPill))

  def sourceMessages(masterRef: ActorRef): Source[Message, NotUsed] =
    Source.actorRef(Int.MaxValue, OverflowStrategy.fail)
      // Give the crawler a way to send messages out
      .mapMaterializedValue { actor =>
        masterRef ! Connected(actor)
        NotUsed
      }
      // transform domain message to websocket message
      .map((msg: OutgoingMsg) => TextMessage(msg.asJson.toString))
}
