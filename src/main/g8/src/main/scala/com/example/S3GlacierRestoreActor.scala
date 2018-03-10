package com.example

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.s3.model.{GlacierJobParameters, RestoreObjectRequest, Tier}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Example actor to show how the actor model might be used to handle concurrent / async use cases when using the
  * AWS Java SDK.
  *
  * This actor initiates a Glacier restore request for an object that has been archived from S3. The
  * restore operation is asynchronous requiring the user to poll to check for completion. Upon receipt of a
  * RestoreObject message the actor initiates the restore and then sends itself a CheckRestoreJob every second to
  * trigger a check for job completion.
  */
object S3GlacierRestoreActor {
  def props(profileName: String): Props = {
    val s3 = AmazonS3ClientBuilder
      .standard()
      .withCredentials(new ProfileCredentialsProvider(profileName))
      .build()
    props(s3)
  }
  def props(s3: AmazonS3): Props = Props(new S3GlacierRestoreActor(s3))

  final case class RestoreObject(restoreObjectRequest: RestoreObjectRequest)
  final case class CheckRestoreJob(bucketName: String, key: String, initialStartTime: LocalDateTime, replyTo: ActorRef)
  final case class RestoreComplete(bucketName: String, key: String)
}

class S3GlacierRestoreActor(s3: AmazonS3) extends Actor with ActorLogging {
  import S3GlacierRestoreActor._
  import context.dispatcher

  def receive = {
    // Start a restore job
    case RestoreObject(ror) => {
      log.info(s"About to restore s3 object from glacier with details: \$ror")
      s3.restoreObjectV2(ror)

      // Send a check restore job status message to arrive in 1 second
      context.system.scheduler.scheduleOnce(1 second, self,
        CheckRestoreJob(ror.getBucketName, ror.getKey, LocalDateTime.now, sender))
    }

    // Check the status of a restore job
    case CheckRestoreJob(bucketName, key, initialStartTime, replyTo) => {
      val restoreInProgress = s3.getObjectMetadata(bucketName, key).getOngoingRestore

      if (restoreInProgress) {
        // If not completed send a message to check again in 1 second
        log.info(s"Object restore for file (\$bucketName/\$key) still in progress")
        context.system.scheduler.scheduleOnce(1 second, self,
          CheckRestoreJob(bucketName, key, initialStartTime, replyTo))
      }
      else {
        val elapsedSeconds = ChronoUnit.SECONDS.between(initialStartTime, LocalDateTime.now)
        log.info(s"Object restore for file (\$bucketName/\$key) completed in \$elapsedSeconds")
        replyTo ! RestoreComplete(bucketName, key)
      }
    }
  }
}