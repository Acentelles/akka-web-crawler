package app

import java.net.URL

import scala.util.Try

object Model {
  case class Sitemap(
    domain: String,
    pages: Map[String, Page],
    edges: Set[Edge]
  )

  case class Page(
    path: String,
    links: Links,
    assets: Assets,
    statusCode: Int,
    title: Option[String] = None,
  )

  case class Edge(from: String, to: String)
  type Assets = Set[String]
  case class Links(
    external: Set[String] = Set.empty,
    internal: Set[String] = Set.empty
  )

  sealed trait Path
  case class Internal(path: String) extends Path
  case class External(path: String) extends Path


  sealed trait Url
  case class NotCheckedUrl(value: String) extends Url {
    def validate: Url = {
      Try { new URL(value) }.toOption match {
        case Some(_) => ValidUrl(value)
        case None => NotValidUrl(value)
      }
    }
  }
  case class ValidUrl(value: String) extends Url
  case class NotValidUrl(value: String) extends Url
}
