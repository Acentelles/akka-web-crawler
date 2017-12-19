import java.net.URL

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestKitBase, TestProbe}
import app.Model.{NotCheckedUrl, Sitemap}
import app.actors.Master
import app.actors.Master._
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, Matchers, WordSpecLike}
import app.Cache._

import scala.concurrent.duration._

class CacheTest extends TestKit(ActorSystem("CrawlerSpec"))
  with TestKitBase
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  "The master actor" should  {
    redisClient.flushdb

    // some websites are uploaded to s3 buckets for testing purposes
    val awsExt = "s3-website.eu-west-2.amazonaws.com"
    val someLinksUrl = s"http://web-crawler-test-links-some.$awsExt"

    "receive PageMsg from the worker if url is not cached" in {
      val crawlerActor = TestActorRef(new Master)
      val testProbe = TestProbe()
      crawlerActor ! Connected(testProbe.ref)

      crawlerActor ! NotCheckedUrl(someLinksUrl).validate

      testProbe.expectMsgType[PageMsg]
      testProbe.expectMsgType[PageMsg]
      testProbe.expectMsgType[PageMsg]

      testProbe.expectMsgType[Success]
    }
    "only receive a Success message from the worker if url is cached" in {
      val crawlerActor = TestActorRef(new Master)
      val testProbe = TestProbe()
      crawlerActor ! Connected(testProbe.ref)

      crawlerActor ! NotCheckedUrl(someLinksUrl).validate

      redisClient.get(someLinksUrl) should not be None
      testProbe.expectMsgType[Success]
    }
  }
}
