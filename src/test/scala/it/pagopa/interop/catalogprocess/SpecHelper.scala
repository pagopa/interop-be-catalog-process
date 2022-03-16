package it.pagopa.interop.catalogprocess

import akka.actor
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives}
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagementDependency}
import it.pagopa.interop.commons.utils.AkkaUtils.Authenticator
import it.pagopa.interop.catalogprocess.server.Controller

import java.util.UUID
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

abstract class SpecHelper extends ScalaTestWithActorTestKit(SpecConfiguration.config) with SpecConfiguration {

  var bindServer: Option[Future[Http.ServerBinding]] = None

  val bearerToken: String               = "token"
  val authorization: Seq[Authorization] = Seq(headers.Authorization(OAuth2BearerToken(bearerToken)))

  val httpSystem: ActorSystem[Any] =
    ActorSystem(Behaviors.ignore[Any], name = system.name, config = system.settings.config)
  implicit val executionContext: ExecutionContextExecutor = httpSystem.executionContext
  implicit val classicSystem: actor.ActorSystem           = httpSystem.classicSystem

  val wrappingDirective: AuthenticationDirective[Seq[(String, String)]] =
    SecurityDirectives.authenticateOAuth2("SecurityRealm", Authenticator)

  def descriptorStub: CatalogManagementDependency.EServiceDescriptor = CatalogManagementDependency.EServiceDescriptor(
    id = UUID.randomUUID(),
    version = "1",
    description = None,
    audience = Seq.empty,
    voucherLifespan = 0,
    dailyCallsPerConsumer = 0,
    dailyCallsTotal = 0,
    interface = None,
    docs = Seq.empty,
    state = CatalogManagementDependency.EServiceDescriptorState.PUBLISHED
  )

  def eServiceStub: CatalogManagementDependency.EService = CatalogManagementDependency.EService(
    id = UUID.randomUUID(),
    producerId = UUID.randomUUID(),
    name = "EService1",
    description = "",
    technology = CatalogManagementDependency.EServiceTechnology.REST,
    attributes = CatalogManagementDependency.Attributes(Seq.empty, Seq.empty, Seq.empty),
    descriptors = Seq.empty
  )

  def startServer(controller: Controller): Http.ServerBinding = {
    bindServer = Some(
      Http()
        .newServerAt("0.0.0.0", servicePort)
        .bind(controller.routes)
    )

    Await.result(bindServer.get, 100.seconds)
  }

  def shutDownServer(): Unit = {
    bindServer.foreach(_.foreach(_.unbind()))
    ActorTestKit.shutdown(httpSystem, 5.seconds)
  }

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
