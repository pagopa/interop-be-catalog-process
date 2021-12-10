package it.pagopa.pdnd.interop.uservice.catalogprocess.common

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.util.Timeout
import akka.{actor => classic}
import it.pagopa.pdnd.interop.commons.jwt.service.JWTReader
import it.pagopa.pdnd.interop.commons.utils.AkkaUtils.getFutureBearer
import it.pagopa.pdnd.interop.commons.utils.TypeConversions.TryOps

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}

package object system {

  implicit val actorSystem: ActorSystem[Nothing] =
    ActorSystem[Nothing](Behaviors.empty[Nothing], "pdnd-interop-uservice-catalog-process")
  implicit val classicActorSystem: classic.ActorSystem    = actorSystem.toClassic
  implicit val executionContext: ExecutionContextExecutor = actorSystem.executionContext

  implicit val timeout: Timeout = 300.seconds

  def validateBearer(contexts: Seq[(String, String)], jwt: JWTReader): Future[String] =
    for {
      bearer <- getFutureBearer(contexts)
      _      <- jwt.getClaims(bearer).toFuture
    } yield bearer
}
