package it.pagopa.pdnd.interop.uservice.catalogprocess

import akka.actor
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{
  ContentTypes,
  HttpEntity,
  HttpMethod,
  HttpRequest,
  HttpResponse,
  RequestEntity,
  headers
}
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.invoker.BearerToken
import it.pagopa.pdnd.interop.uservice.catalogprocess.common.system.Authenticator
import it.pagopa.pdnd.interopuservice.catalogprocess.server.Controller

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.concurrent.duration._

abstract class SpecHelper extends ScalaTestWithActorTestKit(SpecConfiguration.config) with SpecConfiguration {

  var bindServer: Option[Future[Http.ServerBinding]] = None

  val bearerToken: BearerToken          = BearerToken("token")
  val authorization: Seq[Authorization] = Seq(headers.Authorization(OAuth2BearerToken(bearerToken.token)))

  val httpSystem: ActorSystem[Any] =
    ActorSystem(Behaviors.ignore[Any], name = system.name, config = system.settings.config)
  implicit val executionContext: ExecutionContextExecutor = httpSystem.executionContext
  implicit val classicSystem: actor.ActorSystem           = httpSystem.classicSystem

  val wrappingDirective: AuthenticationDirective[Seq[(String, String)]] =
    SecurityDirectives.authenticateOAuth2("SecurityRealm", Authenticator)

  def startServer(controller: Controller): Http.ServerBinding = {
    bindServer = Some(
      Http()
        .newServerAt("0.0.0.0", servicePort)
        .bind(controller.routes)
    )

    Await.result(bindServer.get, 100.seconds)
  }

  def shutDownServer(): Unit = {
    println("****** Cleaning resources ********")
    bindServer.foreach(_.foreach(_.unbind()))
    ActorTestKit.shutdown(httpSystem, 5.seconds)
    println("Resources cleaned")
  }

  @SuppressWarnings(
    Array(
      "org.wartremover.warts.DefaultArguments",
      "org.wartremover.warts.ImplicitParameter",
      "org.wartremover.warts.JavaSerializable"
    )
  )
  def request(path: String, verb: HttpMethod, data: Option[String] = None): HttpResponse = {
    val entity: RequestEntity = data match {
      case Some(d) => HttpEntity(ContentTypes.`application/json`, d)
      case None    => HttpEntity.Empty.withContentType(ContentTypes.`application/json`)
    }
    Await.result(
      Http().singleRequest(
        HttpRequest(uri = s"$serviceURL/$path", method = verb, entity = entity, headers = authorization)
      ),
      10.seconds
    )
  }
}
