package it.pagopa.interop.catalogprocess.server.impl

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.SecurityDirectives
import com.atlassian.oai.validator.report.ValidationReport
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import it.pagopa.interop.agreementmanagement.client.api.AgreementApi
import it.pagopa.interop.attributeregistrymanagement.client.api.AttributeApi
import it.pagopa.interop.authorizationmanagement.client.api.PurposeApi
import it.pagopa.interop.catalogmanagement.client.api.EServiceApi
import it.pagopa.interop.catalogprocess.api.impl.{
  HealthApiMarshallerImpl,
  HealthServiceApiImpl,
  ProcessApiMarshallerImpl,
  ProcessApiServiceImpl,
  entityMarshallerProblem,
  problemOf
}
import it.pagopa.interop.catalogprocess.api.{HealthApi, ProcessApi}
import it.pagopa.interop.catalogprocess.common.system.ApplicationConfiguration
import it.pagopa.interop.catalogprocess.service._
import it.pagopa.interop.catalogprocess.service.impl._
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.jwt.service.impl.{DefaultJWTReader, getClaimsVerifier}
import it.pagopa.interop.commons.jwt.{JWTConfiguration, KID, PublicKeysHolder, SerializedKey}
import it.pagopa.interop.commons.utils.TypeConversions.TryOps
import it.pagopa.interop.commons.utils.{AkkaUtils, OpenapiUtils}
import it.pagopa.interop.selfcare.partymanagement.client.api.PartyApi

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

trait Dependencies {

  implicit val partyManagementApiKeyValue: PartyManagementApiKeyValue = PartyManagementApiKeyValue()

  def getFileManager(blockingEc: ExecutionContextExecutor): FileManager =
    FileManager.get(ApplicationConfiguration.storageKind match {
      case "S3"   => FileManager.S3
      case "file" => FileManager.File
      case _      => throw new Exception("Incorrect File Manager")
    })(blockingEc)

  def getJwtReader(): Future[JWTReader] = JWTConfiguration.jwtReader
    .loadKeyset()
    .map(keyset =>
      new DefaultJWTReader with PublicKeysHolder {
        var publicKeyset: Map[KID, SerializedKey]                                        = keyset
        override protected val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
          getClaimsVerifier(audience = ApplicationConfiguration.jwtAudience)
      }
    )
    .toFuture

  def processApi(jwtReader: JWTReader, fileManager: FileManager, blockingEc: ExecutionContextExecutor)(implicit
    ec: ExecutionContext,
    actorSystem: ActorSystem[_]
  ): ProcessApi =
    new ProcessApi(
      ProcessApiServiceImpl(
        catalogManagementService = catalogManagementService(blockingEc),
        partyManagementService = partyManagementService(),
        attributeRegistryManagementService = attributeRegistryManagementService(blockingEc),
        agreementManagementService = agreementManagementService(blockingEc),
        authorizationManagementService = authorizationManagementService(blockingEc),
        fileManager = fileManager,
        jwtReader = jwtReader
      ),
      ProcessApiMarshallerImpl,
      jwtReader.OAuth2JWTValidatorAsContexts
    )

  val healthApi: HealthApi                        = new HealthApi(
    new HealthServiceApiImpl(),
    HealthApiMarshallerImpl,
    SecurityDirectives.authenticateOAuth2("SecurityRealm", AkkaUtils.PassThroughAuthenticator),
    loggingEnabled = false
  )

  private def agreementManagementInvoker(blockingEc: ExecutionContextExecutor)(implicit
    actorSystem: ActorSystem[_]
  ): AgreementManagementInvoker =
    AgreementManagementInvoker(blockingEc)(actorSystem.classicSystem)
  private val agreementApi: AgreementApi          = AgreementApi(ApplicationConfiguration.agreementManagementUrl)
  def agreementManagementService(blockingEc: ExecutionContextExecutor)(implicit
    actorSystem: ActorSystem[_]
  ): AgreementManagementService =
    AgreementManagementServiceImpl(agreementManagementInvoker(blockingEc), agreementApi)

  private def authorizationManagementInvoker(blockingEc: ExecutionContextExecutor)(implicit
    actorSystem: ActorSystem[_]
  ): AuthorizationManagementInvoker =
    AuthorizationManagementInvoker(blockingEc)(actorSystem.classicSystem)
  private def authorizationPurposeApi: PurposeApi =
    PurposeApi(ApplicationConfiguration.authorizationManagementUrl)
  def authorizationManagementService(
    blockingEc: ExecutionContextExecutor
  )(implicit ec: ExecutionContext, actorSystem: ActorSystem[_]): AuthorizationManagementService =
    AuthorizationManagementServiceImpl(authorizationManagementInvoker(blockingEc), authorizationPurposeApi)

  private def attributeRegistryManagementInvoker(blockingEc: ExecutionContextExecutor)(implicit
    actorSystem: ActorSystem[_]
  ): AttributeRegistryManagementInvoker =
    AttributeRegistryManagementInvoker(blockingEc)(actorSystem.classicSystem)
  private def attributeApi: AttributeApi = AttributeApi(ApplicationConfiguration.attributeRegistryManagementUrl)
  def attributeRegistryManagementService(
    blockingEc: ExecutionContextExecutor
  )(implicit ec: ExecutionContext, actorSystem: ActorSystem[_]): AttributeRegistryManagementService =
    AttributeRegistryManagementServiceImpl(attributeRegistryManagementInvoker(blockingEc), attributeApi)

  private def catalogManagementInvoker(blockingEc: ExecutionContextExecutor)(implicit
    actorSystem: ActorSystem[_]
  ): CatalogManagementInvoker =
    CatalogManagementInvoker(blockingEc)(actorSystem.classicSystem)
  private def catalogApi: EServiceApi    = EServiceApi(ApplicationConfiguration.catalogManagementUrl)
  def catalogManagementService(
    blockingEc: ExecutionContextExecutor
  )(implicit actorSystem: ActorSystem[_], ec: ExecutionContext): CatalogManagementService =
    CatalogManagementServiceImpl(catalogManagementInvoker(blockingEc), catalogApi)

  private def partyManagementInvoker()(implicit actorSystem: ActorSystem[_]): PartyManagementInvoker =
    PartyManagementInvoker()(actorSystem.classicSystem)
  private def partyApi: PartyApi = PartyApi(ApplicationConfiguration.partyManagementUrl)
  def partyManagementService()(implicit actorSystem: ActorSystem[_]): PartyManagementService =
    PartyManagementServiceImpl(partyManagementInvoker(), partyApi)

  val validationExceptionToRoute: ValidationReport => Route = report => {
    val error =
      problemOf(StatusCodes.BadRequest, OpenapiUtils.errorFromRequestValidationReport(report))
    complete(error.status, error)(entityMarshallerProblem)
  }

}
