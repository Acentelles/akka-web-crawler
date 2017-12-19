package app.actors

import java.net._

import akka.actor._
import akka.event.Logging
import app.Model._
import app.actors.Master._
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL._
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import app.Cache._
import better.files.File
import net.ruippeixotog.scalascraper.model.Document
import org.jsoup.{HttpStatusException, UnsupportedMimeTypeException}

import scala.annotation.tailrec
import scala.util.Try

class Worker extends Actor {
  val log = Logging(context.system, this)

  val browser = JsoupBrowser()

  def extractPath(link: String, domain: String, path: String): Option[Path] = {
    if (link.startsWith("http://") || link.startsWith("https://")) {
      if (new URL(domain).getHost == new URL(link).getHost) {
        Some(Internal(s"${new URL(link).getPath}"))
      } else {
        Some(External(link))
      }
    } else if (link.startsWith("/")) {
      Some(Internal(s"${new URL(s"$domain$link").getPath}"))
    } else { // It's a relative link
      Try {
        val root = if (path == "" || path.startsWith("/") || path.startsWith("#")) {
          ""
        } else {
          File(path).parentOption.getOrElse(File("")).path
        }
        val p = File(s"$root/$link").path
        Internal(s"${new URL(s"$domain$p").getPath}")
      }.toOption
    }
  }

  def extractLinksFrom(domain: String, document: Document, path: String): Links =
    document >?> elementList("a") match {
      case None => Links()
      case Some(l) => l.foldLeft(Links()) { (links, a) =>
        a >?> attr("href") match {
          case None => links
          case Some(link) => extractPath(link, domain, path) match {
            case Some(Internal(p)) if !isAsset(p) => Links(links.external, links.internal + p)
            case Some(External(p)) => Links(links.external + p, links.internal)
            case _ => links
          }
        }
      }
    }

  def isAsset(path: String): Boolean = {
    val regEx = """.*\.(\w+)""".r
    path match {
      case regEx(ext) if !path.endsWith("html") => true
      case _ => false
    }
  }

  def extractAssetsFrom(domain: String, document: Document, path: String, tagName: String, attrName: String): Assets = {
    document >?> elementList(tagName) match {
      case None => Set.empty
      case Some(l) => l.foldLeft(Set.empty: Assets) { (links, asset) =>
        asset >?> attr(attrName) match {
          case None => links
          case Some(link) => extractPath(link.toString, domain, path) match {
            case Some(Internal(p)) if isAsset(p) => links + p
            case _ => links
          }
        }
      }
    }
  }

  def assemblePage(domain: String, path: String): Option[Page] = {
    try {
      val document = browser.get(s"$domain$path")
      val links = extractLinksFrom(domain, document, path)
      val assets =
        extractAssetsFrom(domain, document, path, "img", "src") ++
          extractAssetsFrom(domain, document, path, "link", "href") ++
          extractAssetsFrom(domain, document, path, "script", "src")
      val title = document >?> text("title")
      log.info(s"Logging page: $path")
      Some(Page(path, links, assets, 200, title))
    } catch {
      case e: HttpStatusException => // Can't load the page
        log.warning(s"HttpStatusException: $e")
        Some(Page(path, Links(), Set.empty, e.getStatusCode))
      case e: UnsupportedMimeTypeException => // There was a link to some asset
        log.warning(s"UnsupportedMimeTypeException: $e")
        Some(Page(path, Links(), Set.empty, 200))
      case e: UnknownHostException => // There's no internet
        log.warning(s"UnknownHostException: $e")
        None
      case e: Throwable =>
        log.error(s"Unexpected $e")
        None
    }
  }

  @tailrec
  private def crawl(
    domain: String,
    toVisit: Set[String],
    visited: Set[String] = Set.empty,
    pages: Map[String, Page] = Map.empty,
    edges: Set[Edge] = Set.empty
  ): Option[Sitemap] = toVisit.headOption match {
    case None => Some(Sitemap(domain, pages, edges))
    case Some(path) =>
      if (visited.contains(path)) {
        crawl(domain, toVisit.tail, visited, pages, edges)
      } else {
        assemblePage(domain, path) match {
          case Some(page) =>
            val newEdges = page.links.internal.map(Edge(path, _))
            context.parent ! PageMsg(page, domain)
            crawl(domain, page.links.internal ++ toVisit.tail, visited + path, pages + (path -> page), newEdges ++ edges)
          case None => None
        }
      }
  }

  def retrieveFromCache(domain: String): Option[Sitemap] = {
    redisClient.get(domain) match {
      case Some(value) => decode[Sitemap](value) match {
        case Right(data) => Some(data)
        case Left(e) =>
          log.warning(s"Error decoding cached value for domain: $domain")
          None
      }
      case None =>
        log.info(s"No cached value for domain: $domain")
        None
    }
  }

  def receive: Receive = {
    case ValidUrl(value: String) =>
      val url = new URL(value)
      val domain = s"${url.getProtocol}://${url.getHost}"
      val path = if (url.getPath == "") "/" else url.getPath
      retrieveFromCache(domain) match {
        case Some(cached) => sender ! Success(cached)
        case None =>
          crawl(domain, Set(path)) match {
            case Some(sitemap) =>
              log.info(s"Caching domain: $domain")
              redisClient.set(sitemap.domain, sitemap.asJson.toString)
              sender ! Success(sitemap)
            case None => sender ! Error("There was a problem with your request. Please try again", domain)
          }
      }
  }
}
