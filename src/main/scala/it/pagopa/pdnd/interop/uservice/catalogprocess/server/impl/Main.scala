package it.pagopa.pdnd.interop.uservice.catalogprocess.server.impl

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.directives.SecurityDirectives
import akka.management.scaladsl.AkkaManagement
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.api.EServiceApi
import it.pagopa.pdnd.interop.uservice.catalogprocess.api.impl.{
  HealthApiMarshallerImpl,
  HealthServiceApiImpl,
  ProcessApiMarshallerImpl,
  ProcessApiServiceImpl
}
import it.pagopa.pdnd.interop.uservice.catalogprocess.common.system.{
  ApplicationConfiguration,
  Authenticator,
  CorsSupport,
  classicActorSystem,
  executionContext
}
import it.pagopa.pdnd.interop.uservice.catalogprocess.service.{CatalogManagementInvoker, CatalogManagementService}
import it.pagopa.pdnd.interop.uservice.catalogprocess.service.impl.CatalogManagementServiceImpl
import it.pagopa.pdnd.interopuservice.catalogprocess.api.{HealthApi, ProcessApi}
import it.pagopa.pdnd.interopuservice.catalogprocess.server.Controller
import kamon.Kamon

import scala.concurrent.Future

trait CatalogManagementAPI {
  private final val catalogManagementInvoker: CatalogManagementInvoker = CatalogManagementInvoker()
  private final val catalogApi: EServiceApi                            = EServiceApi(ApplicationConfiguration.catalogManagementUrl)
  val catalogManagementService: CatalogManagementService =
    CatalogManagementServiceImpl(catalogManagementInvoker, catalogApi)
}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.StringPlusAny",
    "org.wartremover.warts.Nothing",
    "org.wartremover.warts.ImplicitConversion",
    "org.wartremover.warts.NonUnitStatements"
  )
)
object Main extends App with CorsSupport with CatalogManagementAPI {

  Kamon.init()

  val processApi: ProcessApi = new ProcessApi(
    ProcessApiServiceImpl(catalogManagementService),
    ProcessApiMarshallerImpl(),
    SecurityDirectives.authenticateOAuth2("SecurityRealm", Authenticator)
  )

  val healthApi: HealthApi = new HealthApi(
    new HealthServiceApiImpl(),
    new HealthApiMarshallerImpl(),
    SecurityDirectives.authenticateOAuth2("SecurityRealm", Authenticator)
  )

  locally {
    AkkaManagement.get(classicActorSystem).start()
  }

  val controller: Controller = new Controller(healthApi, processApi)

  val bindingFuture: Future[Http.ServerBinding] =
    Http().newServerAt("0.0.0.0", ApplicationConfiguration.serverPort).bind(corsHandler(controller.routes))

}
