package it.pagopa.interop.catalogprocess.server.impl

import cats.syntax.all._
import akka.http.scaladsl.Http
import akka.management.scaladsl.AkkaManagement
import it.pagopa.interop.commons.utils.CORSSupport
import it.pagopa.interop.catalogprocess.server.Controller
import it.pagopa.interop.catalogprocess.common.system.ApplicationConfiguration
import kamon.Kamon

import scala.concurrent.Future
import scala.util.{Failure, Success}
import com.typesafe.scalalogging.Logger
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import scala.concurrent.ExecutionContext
import buildinfo.BuildInfo

object Main extends App with CORSSupport with Dependencies {

  private val logger: Logger = Logger(this.getClass)
  System.setProperty("kanela.show-banner", "false")

  val system = ActorSystem[Nothing](
    Behaviors.setup[Nothing] { context =>
      implicit val actorSystem: ActorSystem[_]        = context.system
      implicit val executionContext: ExecutionContext = actorSystem.executionContext

      Kamon.init()
      AkkaManagement.get(actorSystem.classicSystem).start()

      val serverBinding: Future[Http.ServerBinding] = for {
        jwtReader   <- getJwtReader()
        fileManager <- getFileManager()
        controller = new Controller(healthApi, processApi(jwtReader, fileManager), validationExceptionToRoute.some)(
          actorSystem.classicSystem
        )
        binding <-
          Http().newServerAt("0.0.0.0", ApplicationConfiguration.serverPort).bind(corsHandler(controller.routes))
      } yield binding

      serverBinding.onComplete {
        case Success(b) =>
          logger.info(s"Started server at ${b.localAddress.getHostString()}:${b.localAddress.getPort()}")
        case Failure(e) =>
          actorSystem.terminate()
          logger.error("Startup error: ", e)
      }

      Behaviors.empty
    },
    BuildInfo.name
  )

  system.whenTerminated.onComplete { case _ => Kamon.stop() }(scala.concurrent.ExecutionContext.global)

}
