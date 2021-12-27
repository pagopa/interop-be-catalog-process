package it.pagopa.pdnd.interop.uservice.catalogprocess.server.impl

import akka.actor.CoordinatedShutdown
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.directives.SecurityDirectives
import akka.management.scaladsl.AkkaManagement
import it.pagopa.pdnd.interop.commons.files.StorageConfiguration
import it.pagopa.pdnd.interop.commons.files.service.FileManager
import it.pagopa.pdnd.interop.commons.jwt.service.JWTReader
import it.pagopa.pdnd.interop.commons.jwt.service.impl.DefaultJWTReader
import it.pagopa.pdnd.interop.commons.jwt.{JWTConfiguration, PublicKeysHolder}
import it.pagopa.pdnd.interop.commons.utils.TypeConversions.TryOps
import it.pagopa.pdnd.interop.commons.utils.{AkkaUtils, CORSSupport, OpenapiUtils}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.api.AgreementApi
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.client.api.AttributeApi
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.api.EServiceApi
import it.pagopa.pdnd.interop.uservice.catalogprocess.api.impl.{
  HealthApiMarshallerImpl,
  HealthServiceApiImpl,
  ProcessApiMarshallerImpl,
  ProcessApiServiceImpl,
  problemOf
}
import it.pagopa.pdnd.interop.uservice.catalogprocess.api.{HealthApi, ProcessApi}
import it.pagopa.pdnd.interop.uservice.catalogprocess.common.system.{
  ApplicationConfiguration,
  classicActorSystem,
  executionContext
}
import it.pagopa.pdnd.interop.uservice.catalogprocess.server.Controller
import it.pagopa.pdnd.interop.uservice.catalogprocess.service._
import it.pagopa.pdnd.interop.uservice.catalogprocess.service.impl.{
  AgreementManagementServiceImpl,
  AttributeRegistryManagementServiceImpl,
  CatalogManagementServiceImpl,
  PartyManagementServiceImpl
}
import it.pagopa.pdnd.interop.uservice.partymanagement.client.api.PartyApi
import kamon.Kamon

import scala.concurrent.Future
import scala.util.{Failure, Success}

//shuts down the actor system in case of startup errors
case object StartupErrorShutdown extends CoordinatedShutdown.Reason

trait AgreementManagementDependency {
  private final val agreementManagementInvoker: AgreementManagementInvoker = AgreementManagementInvoker()
  private final val agreementApi: AgreementApi                             = AgreementApi(ApplicationConfiguration.agreementManagementUrl)
  val agreementManagementService: AgreementManagementService =
    AgreementManagementServiceImpl(agreementManagementInvoker, agreementApi)
}

trait AttributeRegistryManagementDependency {
  private final val attributeRegistryManagementInvoker: AttributeRegistryManagementInvoker =
    AttributeRegistryManagementInvoker()
  private final val attributeApi: AttributeApi = AttributeApi(ApplicationConfiguration.attributeRegistryManagementUrl)
  val attributeRegistryManagementService: AttributeRegistryManagementService =
    AttributeRegistryManagementServiceImpl(attributeRegistryManagementInvoker, attributeApi)
}

trait CatalogManagementDependency {
  private final val catalogManagementInvoker: CatalogManagementInvoker = CatalogManagementInvoker()
  private final val catalogApi: EServiceApi                            = EServiceApi(ApplicationConfiguration.catalogManagementUrl)
  val catalogManagementService: CatalogManagementService =
    CatalogManagementServiceImpl(catalogManagementInvoker, catalogApi)
}

trait PartyManagementDependency {
  private final val partyManagementInvoker: PartyManagementInvoker = PartyManagementInvoker()
  private final val partyApi: PartyApi                             = PartyApi(ApplicationConfiguration.partyManagementUrl)
  val partyManagementService: PartyManagementService =
    PartyManagementServiceImpl(partyManagementInvoker, partyApi)
}

object Main
    extends App
    with CORSSupport
    with AgreementManagementDependency
    with AttributeRegistryManagementDependency
    with PartyManagementDependency
    with CatalogManagementDependency {

  val dependenciesLoaded: Future[(FileManager, JWTReader)] = for {
    fileManager <- FileManager.getConcreteImplementation(StorageConfiguration.runtimeFileManager).toFuture
    keyset      <- JWTConfiguration.jwtReader.loadKeyset().toFuture
    jwtValidator = new DefaultJWTReader with PublicKeysHolder {
      var publicKeyset = keyset
    }
  } yield (fileManager, jwtValidator)

  dependenciesLoaded.transformWith {
    case Success((fileManager, jwtValidator)) => launchApp(fileManager, jwtValidator)
    case Failure(ex) => {
      classicActorSystem.log.error("Startup error: {}", ex.getMessage)
      classicActorSystem.log.error(ex.getStackTrace.mkString("\n"))
      CoordinatedShutdown(classicActorSystem).run(StartupErrorShutdown)
    }
  }

  private def launchApp(fileManager: FileManager, jwtReader: JWTReader): Future[Http.ServerBinding] = {
    Kamon.init()

    val processApi: ProcessApi = new ProcessApi(
      ProcessApiServiceImpl(
        catalogManagementService = catalogManagementService,
        partyManagementService = partyManagementService,
        attributeRegistryManagementService = attributeRegistryManagementService,
        agreementManagementService = agreementManagementService,
        fileManager = fileManager,
        jwtReader = jwtReader
      ),
      ProcessApiMarshallerImpl(),
      SecurityDirectives.authenticateOAuth2("SecurityRealm", AkkaUtils.Authenticator)
    )

    val healthApi: HealthApi = new HealthApi(
      new HealthServiceApiImpl(),
      HealthApiMarshallerImpl,
      SecurityDirectives.authenticateOAuth2("SecurityRealm", AkkaUtils.Authenticator)
    )

    locally {
      AkkaManagement.get(classicActorSystem).start()
    }

    val controller: Controller = new Controller(
      healthApi,
      processApi,
      validationExceptionToRoute = Some(report => {
        val error =
          problemOf(
            StatusCodes.BadRequest,
            "0000",
            defaultMessage = OpenapiUtils.errorFromRequestValidationReport(report)
          )
        complete(error.status, error)(HealthApiMarshallerImpl.toEntityMarshallerProblem)
      })
    )

    val server: Future[Http.ServerBinding] =
      Http().newServerAt("0.0.0.0", ApplicationConfiguration.serverPort).bind(corsHandler(controller.routes))

    server
  }

}
