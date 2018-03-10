package com.example

import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model._
import org.scalamock.scalatest.MockFactory

import scala.concurrent.duration._
import scala.language.postfixOps

class AwsAkkaQuickstartSpec(_system: ActorSystem)
  extends TestKit(_system)
  with Matchers
  with FlatSpecLike
  with MockFactory
  with ImplicitSender
  with BeforeAndAfterAll {

  import S3GlacierRestoreActor._

  def this() = this(ActorSystem("AwsAkkaQuickstartSpec"))

  override def afterAll: Unit = {
    shutdown(system)
  }

  "An S3GlacierRestoreActor" should "return RestoreComplete for the happy path" in {
    val testBucket = "testBucket"
    val testKey = "testKey"

    val glacierJobParams = new GlacierJobParameters().withTier(Tier.Expedited)
    val restoreRequest= new RestoreObjectRequest(testBucket, testKey)
      .withGlacierJobParameters(glacierJobParams)
      .withExpirationInDays(5)

    // mock out s3 and expected calls
    val mockS3 = mock[AmazonS3]

    (mockS3.restoreObjectV2 _).expects(restoreRequest).returning(new RestoreObjectResult())

    val ongoingRestoreTrue = new ObjectMetadata()
    ongoingRestoreTrue.setOngoingRestore(true)
    (mockS3.getObjectMetadata(_: String, _: String)).expects(testBucket, testKey).returning(ongoingRestoreTrue)

    val ongoingRestoreFalse = new ObjectMetadata()
    ongoingRestoreFalse.setOngoingRestore(false)
    (mockS3.getObjectMetadata(_: String, _: String)).expects(testBucket, testKey).returning(ongoingRestoreFalse)

    // run our test using the mock s3 implementation
    val s3RestoreActor = system.actorOf(S3GlacierRestoreActor.props(mockS3))
    s3RestoreActor ! RestoreObject(restoreRequest)

    expectMsg(5 seconds, RestoreComplete(testBucket, testKey))
  }
}
