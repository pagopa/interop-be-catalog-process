package it.pagopa.interop.catalogprocess.server.impl

import akka.actor.CoordinatedShutdown
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.directives.SecurityDirectives
import akka.management.scaladsl.AkkaManagement
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import it.pagopa.interop.commons.files.StorageConfiguration
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.jwt.service.impl.{DefaultJWTReader, getClaimsVerifier}
import it.pagopa.interop.commons.jwt.{JWTConfiguration, KID, PublicKeysHolder, SerializedKey}
import it.pagopa.interop.commons.utils.TypeConversions.TryOps
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.ValidationRequestError
import it.pagopa.interop.commons.utils.{AkkaUtils, CORSSupport, OpenapiUtils}
import it.pagopa.interop.agreementmanagement.client.api.AgreementApi
import it.pagopa.interop.attributeregistrymanagement.client.api.AttributeApi
import it.pagopa.interop.catalogmanagement.client.api.EServiceApi
import it.pagopa.interop.catalogprocess.api.impl.{
  HealthApiMarshallerImpl,
  HealthServiceApiImpl,
  ProcessApiMarshallerImpl,
  ProcessApiServiceImpl,
  problemOf
}
import it.pagopa.interop.catalogprocess.api.{HealthApi, ProcessApi}
import it.pagopa.interop.catalogprocess.common.system.{ApplicationConfiguration, classicActorSystem, executionContext}
import it.pagopa.interop.catalogprocess.server.Controller
import it.pagopa.interop.catalogprocess.service._
import it.pagopa.interop.catalogprocess.service.impl.{
  AgreementManagementServiceImpl,
  AttributeRegistryManagementServiceImpl,
  AuthorizationManagementServiceImpl,
  CatalogManagementServiceImpl,
  PartyManagementServiceImpl
}
import it.pagopa.interop.authorizationmanagement.client.api.PurposeApi
import it.pagopa.interop.partymanagement.client.api.PartyApi
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

trait AuthorizationManagementDependency {
  private final val authorizationManagementInvoker: AuthorizationManagementInvoker = AuthorizationManagementInvoker()
  private final val authorizationPurposeApi: PurposeApi =
    PurposeApi(ApplicationConfiguration.authorizationManagementUrl)
  val authorizationManagementService: AuthorizationManagementService =
    AuthorizationManagementServiceImpl(authorizationManagementInvoker, authorizationPurposeApi)
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
    with AuthorizationManagementDependency
    with PartyManagementDependency
    with CatalogManagementDependency {

  val dependenciesLoaded: Future[(FileManager, JWTReader)] = for {
    fileManager <- FileManager.getConcreteImplementation(StorageConfiguration.runtimeFileManager).toFuture
    keyset      <- JWTConfiguration.jwtReader.loadKeyset().toFuture
    jwtValidator = new DefaultJWTReader with PublicKeysHolder {
      var publicKeyset: Map[KID, SerializedKey] = keyset
      override protected val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
        getClaimsVerifier(audience = ApplicationConfiguration.jwtAudience)
    }
  } yield (fileManager, jwtValidator)

  dependenciesLoaded.transformWith {
    case Success((fileManager, jwtValidator)) => launchApp(fileManager, jwtValidator)
    case Failure(ex) =>
      classicActorSystem.log.error("Startup error: {}", ex.getMessage)
      classicActorSystem.log.error(ex.getStackTrace.mkString("\n"))
      CoordinatedShutdown(classicActorSystem).run(StartupErrorShutdown)
  }

  private def launchApp(fileManager: FileManager, jwtReader: JWTReader): Future[Http.ServerBinding] = {
    Kamon.init()

    val processApi: ProcessApi = new ProcessApi(
      ProcessApiServiceImpl(
        catalogManagementService = catalogManagementService,
        partyManagementService = partyManagementService,
        attributeRegistryManagementService = attributeRegistryManagementService,
        agreementManagementService = agreementManagementService,
        authorizationManagementService = authorizationManagementService,
        fileManager = fileManager,
        jwtReader = jwtReader
      ),
      ProcessApiMarshallerImpl,
      jwtReader.OAuth2JWTValidatorAsContexts
    )

    val healthApi: HealthApi = new HealthApi(
      new HealthServiceApiImpl(),
      HealthApiMarshallerImpl,
      SecurityDirectives.authenticateOAuth2("SecurityRealm", AkkaUtils.PassThroughAuthenticator)
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
            ValidationRequestError(OpenapiUtils.errorFromRequestValidationReport(report))
          )
        complete(error.status, error)(HealthApiMarshallerImpl.toEntityMarshallerProblem)
      })
    )

    val server: Future[Http.ServerBinding] =
      Http().newServerAt("0.0.0.0", ApplicationConfiguration.serverPort).bind(corsHandler(controller.routes))

    server
  }

}
