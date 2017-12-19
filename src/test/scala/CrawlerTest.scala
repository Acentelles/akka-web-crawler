import java.net.URL

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestKitBase, TestProbe}
import app.Model._
import app.actors.Master
import app.actors.Master._
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, Matchers, WordSpecLike}
import app.Cache._

import scala.concurrent.duration._

class CrawlerTest extends TestKit(ActorSystem("CrawlerSpec"))
  with TestKitBase
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfter
  with BeforeAndAfterAll {

  val badUrl = "badUrl"

  // some websites are uploaded to s3 buckets for testing purposes
  val awsExt = "s3-website.eu-west-2.amazonaws.com"

  val emptyAssetsUrl = s"http://web-crawler-test-assets-empty.$awsExt"
  val someAssetsUrl = s"http://web-crawler-test-assets-some.$awsExt"
  val emptyLinksUrl = s"http://web-crawler-test-links-empty.$awsExt"
  val someLinksUrl = s"http://web-crawler-test-links-some.$awsExt"

  before {
    redisClient.flushdb
  }

  "If crawler is not connected, it" should {
    "not respond to messages" in {
      val crawlerActor = TestActorRef(new Master)
      val testProbe = TestProbe()
      crawlerActor ! NotCheckedUrl(someLinksUrl).validate
      testProbe.expectNoMessage(1.second)
    }
  }

  "If crawler is connected, it" should {
    "return a successful response when it receives a valid url" in {
      val crawlerActor = TestActorRef(new Master)
      val testProbe = TestProbe()
      crawlerActor ! Connected(testProbe.ref)

      crawlerActor ! NotCheckedUrl(someLinksUrl).validate

      testProbe.fishForMessage(5.second) {
        case msg: Success => true
        case _ => false
      }
    }

    "return an error when it receives a not valid url" in {
      val crawlerActor = TestActorRef(new Master)
      val testProbe = TestProbe()
      crawlerActor ! Connected(testProbe.ref)
      crawlerActor ! NotCheckedUrl(badUrl).validate
      testProbe.expectMsgType[Error]
    }
  }

  "A request to a website with multiple linked pages" should {
    "receive pages one by one before getting a successful response" in {
      val crawlerActor = TestActorRef(new Master)
      val testProbe = TestProbe()
      crawlerActor ! Connected(testProbe.ref)

      crawlerActor ! NotCheckedUrl(someLinksUrl).validate

      testProbe.expectMsgType[PageMsg]
      testProbe.expectMsgType[PageMsg]
      testProbe.expectMsgType[PageMsg]

      testProbe.expectMsgType[Success]
    }

    "show each page assets" in {

    }

    "show each page links" in {
      val crawlerActor = TestActorRef(new Master)
      val testProbe = TestProbe()
      crawlerActor ! Connected(testProbe.ref)

      crawlerActor ! NotCheckedUrl(someLinksUrl).validate

      testProbe.expectMsgType[PageMsg]
      testProbe.expectMsgType[PageMsg]
      testProbe.expectMsgType[PageMsg]

      testProbe.expectMsg(1.second,
        Success(Sitemap(
          someLinksUrl,
          Map(
            "/" -> Page("/", Links(Set.empty, Set("/page-1.html", "/page-2.html")), Set.empty, 200, Some("Some links")),
            "/page-1.html" -> Page("/page-1.html", Links(Set.empty, Set("/page-2.html")), Set.empty, 200, Some("Page 1")),
            "/page-2.html" -> Page("/page-2.html", Links(Set.empty, Set.empty), Set.empty, 200, Some("Page 2"))
          ),
          Set(Edge("/page-1.html", "/page-2.html"), Edge("/","/page-1.html"), Edge("/", "/page-2.html"))
        )))


    }
  }

  "A client" should {
    "trigger a new worker if no worker is crawling that url yet" in {
      val crawlerActor = TestActorRef(new Master)
      val underlyingTrintsActor: Master = crawlerActor.underlyingActor
      val testProbe = TestProbe()

      underlyingTrintsActor.currentDomain should be(None)
      underlyingTrintsActor.stoppedDomains should be(Set.empty)

      crawlerActor ! Connected(testProbe.ref)
      crawlerActor ! NotCheckedUrl(someLinksUrl).validate

      underlyingTrintsActor.currentDomain should be(Some(someLinksUrl))
      underlyingTrintsActor.stoppedDomains should be(Set.empty)

      testProbe.expectMsgType[PageMsg]
    }
    "stop listening to changes from a worker after stopping it" in {
      val crawlerActor = TestActorRef(new Master)
      val underlyingTrintsActor: Master = crawlerActor.underlyingActor
      val testProbe = TestProbe()

      crawlerActor ! Connected(testProbe.ref)
      crawlerActor ! NotCheckedUrl(someLinksUrl).validate

      testProbe.expectMsgType[PageMsg]

      crawlerActor ! StopCrawling

      testProbe.expectNoMessage(1.second)
    }
    "not trigger a new worker if there is already a worker crawling it" in {
      val crawlerActor = TestActorRef(new Master)
      val underlyingTrintsActor: Master = crawlerActor.underlyingActor
      val testProbe = TestProbe()

      crawlerActor ! Connected(testProbe.ref)
      crawlerActor ! NotCheckedUrl(someLinksUrl).validate

      crawlerActor ! StopCrawling

      underlyingTrintsActor.stoppedDomains should be(Set(someLinksUrl))
      underlyingTrintsActor.currentDomain should be(None)

      crawlerActor ! NotCheckedUrl(someLinksUrl).validate

      underlyingTrintsActor.stoppedDomains should be(Set.empty)
      underlyingTrintsActor.currentDomain should be(Some(someLinksUrl))
    }
  }
  "A successful crawl" should {
    "set currentDomain back to None" in {
      val crawlerActor = TestActorRef(new Master)
      val underlyingTrintsActor: Master = crawlerActor.underlyingActor
      val testProbe = TestProbe()
      crawlerActor ! Connected(testProbe.ref)
      crawlerActor ! NotCheckedUrl(someLinksUrl).validate

      underlyingTrintsActor.currentDomain should be(Some(someLinksUrl))
      underlyingTrintsActor.stoppedDomains should be(Set.empty)

      testProbe.fishForMessage(2.second) {
        case msg: Success => true
        case _ => false
      }

      underlyingTrintsActor.currentDomain should be(None)
      underlyingTrintsActor.stoppedDomains should be(Set.empty)
    }
  }
}
