package com.example


import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import com.amazonaws.services.s3.model.{GlacierJobParameters, RestoreObjectRequest, Tier}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Example application that makes use of our example s3 restore actor to initiate multi file glacier restore
  * requests and tracks their status.
  */
object AwsAkkaQuickstart extends App {
  import S3GlacierRestoreActor._

  val system: ActorSystem = ActorSystem("awsAkkaQuickstart")

  // Create our test actor passing in a profile name for retrieving credentials
  val s3RestoreActor: ActorRef = system.actorOf(S3GlacierRestoreActor.props("acc1Profile"), "s3RestoreActor")

  // S3 Bucket name
  val bucketName = "<bucketname>"

  // S3 Test files (residing in the bucket above)
  val file1 = "<filename1>"
  val file2 = "<filename2>"

  // Define restore requests
  val glacierJobParams = new GlacierJobParameters().withTier(Tier.Expedited)
  val restoreFile1 = new RestoreObjectRequest(bucketName, file1)
    .withGlacierJobParameters(glacierJobParams)
    .withExpirationInDays(5)
  val restoreFile2 = new RestoreObjectRequest(bucketName, file2)
    .withGlacierJobParameters(glacierJobParams)
    .withExpirationInDays(5)

  // Start simultaneous glacier restores and terminate when done
  import system.dispatcher
  implicit val timeout: Timeout = Timeout(60 minutes)

  val file1Result: Future[Any] = s3RestoreActor ? RestoreObject(restoreFile1)
  val file2Result: Future[Any] = s3RestoreActor ? RestoreObject(restoreFile2)

  for {
    f1 <- file1Result
    f2 <- file2Result
  } yield {
    // The above for comprehension will block until both have completed.
    // If either fails the above will throw an exception.
    // On complete of both shutdown the actor system so the process can exit.
    system.terminate()
  }
}