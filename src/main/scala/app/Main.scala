package app

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import app.routes.Crawl

object Main extends App {
  implicit val system: ActorSystem = ActorSystem("web-crawler")
  implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(system))

  val routes: Route = (new Crawl).routes
  val PORT = 8080
  println(s"Listening on port $PORT")
  Http().bindAndHandle(routes, "0.0.0.0", PORT)
}