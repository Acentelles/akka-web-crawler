package app.actors

import java.net.URL

import akka.actor.{Actor, ActorRef, Props}
import akka.event.Logging
import app.Model._

object Master {
  // FSM messages
  sealed trait Status
  case class Connected(outgoing: ActorRef) extends Status
  case object Disconnected extends Status

  // Incoming messages
  case object StopCrawling
  // It can also receive a URL type, which is also used in other actors

  // Outgoing messages
  sealed trait OutgoingMsg
  case class Error(value: String, domain: String) extends OutgoingMsg
  case class PageMsg(value: Page, domain: String) extends OutgoingMsg
  case class Success(value: Sitemap) extends OutgoingMsg
}

class Master extends Actor {
  import Master._

  val log = Logging(context.system, this)

  var currentDomain: Option[String] = None
  var stoppedDomains: Set[String] = Set.empty

  // Behaviour 1: Not connected
  def receive: Receive = {
    case Connected(client) =>
      log.info("User connected")
      context.become(connected(client))
  }

  // Behaviour 2: Client connected
  def connected(client: ActorRef): Receive = {
    // Incoming messages: URL, StopCrawling
    case validUrl: ValidUrl =>
      log.info(s"Received valid url: ${validUrl.value}")
      val worker: ActorRef = context.actorOf(Props(new Worker))
      val url = new URL(validUrl.value)
      val domain = s"${url.getProtocol}://${url.getHost}"

      // Add current domain to stopped domains in case there was one
      currentDomain.foreach(c => {
        stoppedDomains = stoppedDomains + c
      })

      // Update current domain
      currentDomain = Some(domain)

      if (stoppedDomains(domain)) {
        // A worker is already crawling that domain.
        // There's no need to start another worker from this actor
        stoppedDomains = stoppedDomains - domain
      } else {
        // Start crawling
        worker ! validUrl
      }

    case url: NotValidUrl =>
      log.info(s"Receiving invalid url: ${url.value}")
      client ! Error(s"${url.value} is not a valid url", url.value)

    case StopCrawling =>
      currentDomain.foreach(domain => {
        stoppedDomains = stoppedDomains + domain
      })
      currentDomain = None

    // Outgoing messages: PageMsg, Error, Success
    case pageMsg: PageMsg if currentDomain.contains(pageMsg.domain) =>
      client ! pageMsg

    case error: Error if currentDomain.contains(error.domain) =>
      client ! error

    case success: Success
      if currentDomain.contains(success.value.domain) =>
      currentDomain = None
      client ! success

    case success: Success
      if !currentDomain.contains(success.value.domain) =>
      stoppedDomains = stoppedDomains - success.value.domain

    // FSM messages
    case Disconnected =>
      log.info("Client disconnected")
      context.unbecome()
  }
}