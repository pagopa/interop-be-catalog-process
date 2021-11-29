package it.pagopa.pdnd.interop.uservice.catalogprocess.common

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.util.Timeout
import akka.{actor => classic}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt

package object system {

  implicit val actorSystem: ActorSystem[Nothing] =
    ActorSystem[Nothing](Behaviors.empty[Nothing], "pdnd-interop-uservice-catalog-process")
  implicit val classicActorSystem: classic.ActorSystem    = actorSystem.toClassic
  implicit val executionContext: ExecutionContextExecutor = actorSystem.executionContext

  implicit val timeout: Timeout = 300.seconds
}
