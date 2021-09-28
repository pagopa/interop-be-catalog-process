package it.pagopa.pdnd.interop.uservice.catalogprocess.server.impl

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.directives.SecurityDirectives
import akka.management.scaladsl.AkkaManagement
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.api.AgreementApi
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.client.api.AttributeApi
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.api.EServiceApi
import it.pagopa.pdnd.interop.uservice.catalogprocess.api.impl.{
  HealthApiMarshallerImpl,
  HealthServiceApiImpl,
  ProcessApiMarshallerImpl,
  ProcessApiServiceImpl
}
import it.pagopa.pdnd.interop.uservice.catalogprocess.api.{HealthApi, ProcessApi}
import it.pagopa.pdnd.interop.uservice.catalogprocess.common.system.{
  ApplicationConfiguration,
  Authenticator,
  CorsSupport,
  classicActorSystem,
  executionContext,
  s3Client
}
import it.pagopa.pdnd.interop.uservice.catalogprocess.server.Controller
import it.pagopa.pdnd.interop.uservice.catalogprocess.service.impl.{
  AgreementManagementServiceImpl,
  AttributeRegistryManagementServiceImpl,
  CatalogManagementServiceImpl,
  PartyManagementServiceImpl,
  S3ManagerImpl
}
import it.pagopa.pdnd.interop.uservice.catalogprocess.service.{
  AgreementManagementInvoker,
  AgreementManagementService,
  AttributeRegistryManagementInvoker,
  AttributeRegistryManagementService,
  CatalogManagementInvoker,
  CatalogManagementService,
  FileManager,
  PartyManagementInvoker,
  PartyManagementService
}
import it.pagopa.pdnd.interop.uservice.partymanagement.client.api.PartyApi
import kamon.Kamon

import scala.concurrent.Future

trait AgreementManagementAPI {
  private final val agreementManagementInvoker: AgreementManagementInvoker = AgreementManagementInvoker()
  private final val agreementApi: AgreementApi                             = AgreementApi(ApplicationConfiguration.agreementManagementUrl)
  val agreementManagementService: AgreementManagementService =
    AgreementManagementServiceImpl(agreementManagementInvoker, agreementApi)
}

trait AttributeRegistryManagementAPI {
  private final val attributeRegistryManagementInvoker: AttributeRegistryManagementInvoker =
    AttributeRegistryManagementInvoker()
  private final val attributeApi: AttributeApi = AttributeApi(ApplicationConfiguration.attributeRegistryManagementUrl)
  val attributeRegistryManagementService: AttributeRegistryManagementService =
    AttributeRegistryManagementServiceImpl(attributeRegistryManagementInvoker, attributeApi)
}

trait CatalogManagementAPI {
  private final val catalogManagementInvoker: CatalogManagementInvoker = CatalogManagementInvoker()
  private final val catalogApi: EServiceApi                            = EServiceApi(ApplicationConfiguration.catalogManagementUrl)
  val catalogManagementService: CatalogManagementService =
    CatalogManagementServiceImpl(catalogManagementInvoker, catalogApi)
}

trait PartyManagementAPI {
  private final val partyManagementInvoker: PartyManagementInvoker = PartyManagementInvoker()
  private final val partyApi: PartyApi                             = PartyApi(ApplicationConfiguration.partyManagementUrl)
  val partyManagementService: PartyManagementService =
    PartyManagementServiceImpl(partyManagementInvoker, partyApi)
}

trait FileManagerAPI {
  val fileManager: FileManager = S3ManagerImpl(s3Client)
}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.StringPlusAny",
    "org.wartremover.warts.Nothing",
    "org.wartremover.warts.ImplicitConversion",
    "org.wartremover.warts.NonUnitStatements"
  )
)
object Main
    extends App
    with CorsSupport
    with AgreementManagementAPI
    with AttributeRegistryManagementAPI
    with PartyManagementAPI
    with CatalogManagementAPI
    with FileManagerAPI {

  Kamon.init()

  val processApi: ProcessApi = new ProcessApi(
    ProcessApiServiceImpl(
      catalogManagementService = catalogManagementService,
      partyManagementService = partyManagementService,
      attributeRegistryManagementService = attributeRegistryManagementService,
      agreementManagementService = agreementManagementService,
      fileManager = fileManager
    ),
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
