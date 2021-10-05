package it.pagopa.pdnd.interop.uservice.catalogprocess.common

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.server.Directives.Authenticator
import akka.http.scaladsl.server.directives.Credentials
import akka.util.Timeout
import akka.{actor => classic}
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.http.scaladsl.server.directives.Credentials.{Missing, Provided}
import it.pagopa.pdnd.interop.uservice.catalogprocess.common.system.ApplicationConfiguration.awsCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.{S3Client, S3Configuration}

import java.util.UUID
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration.DurationInt
import scala.util.Try

package object system {

  implicit val actorSystem: ActorSystem[Nothing] =
    ActorSystem[Nothing](Behaviors.empty[Nothing], "pdnd-interop-uservice-catalog-process")

  implicit val classicActorSystem: classic.ActorSystem = actorSystem.toClassic

  implicit val executionContext: ExecutionContextExecutor = actorSystem.executionContext

  implicit val timeout: Timeout = 300.seconds

  lazy val s3Client: S3Client = {
    val s3 = S3Client
      .builder()
      .region(Region.EU_CENTRAL_1)
      .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
      .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
      .build()
    s3
  }

  object Authenticator extends Authenticator[Seq[(String, String)]] {

    override def apply(credentials: Credentials): Option[Seq[(String, String)]] = {
      credentials match {
        case Provided(identifier) => Some(Seq("bearer" -> identifier))
        case Missing              => None
      }
    }
  }

  implicit class TryOps[A](val tryOp: Try[A]) extends AnyVal {
    def toFuture: Future[A] = tryOp.fold(e => Future.failed[A](e), a => Future.successful[A](a))
  }

  implicit class EitherOps[A](val either: Either[Throwable, A]) extends AnyVal {
    def toFuture: Future[A] = either.fold(e => Future.failed[A](e), a => Future.successful[A](a))
  }

  implicit class OptionOps[A](val option: Option[A]) extends AnyVal {
    def toFuture(error: Throwable): Future[A] = option.fold(Future.failed[A](error))(a => Future.successful[A](a))
  }

  implicit class StringOps(val str: String) extends AnyVal {
    def parseUUID: Either[Throwable, UUID] = Try { UUID.fromString(str) }.toEither
  }
}
