package it.pagopa.interop.catalogprocess.server.impl

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.directives.SecurityDirectives
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import it.pagopa.interop.commons.files.StorageConfiguration
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.jwt.service.impl.{DefaultJWTReader, getClaimsVerifier}
import it.pagopa.interop.commons.jwt.{JWTConfiguration, KID, PublicKeysHolder, SerializedKey}
import it.pagopa.interop.commons.utils.TypeConversions.TryOps
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.ValidationRequestError
import it.pagopa.interop.commons.utils.{AkkaUtils, OpenapiUtils}
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
import it.pagopa.interop.catalogprocess.common.system.ApplicationConfiguration
import it.pagopa.interop.catalogprocess.service._
import it.pagopa.interop.catalogprocess.service.impl.{
  AgreementManagementServiceImpl,
  AttributeRegistryManagementServiceImpl,
  AuthorizationManagementServiceImpl,
  CatalogManagementServiceImpl,
  PartyManagementServiceImpl
}
import it.pagopa.interop.authorizationmanagement.client.api.PurposeApi
import it.pagopa.interop.selfcare.partymanagement.client.api.PartyApi

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import akka.actor.typed.ActorSystem
import com.atlassian.oai.validator.report.ValidationReport
import akka.http.scaladsl.server.Route

trait Dependencies {

  implicit val partyManagementApiKeyValue: PartyManagementApiKeyValue = PartyManagementApiKeyValue()

  def getFileManager(): Future[FileManager] =
    FileManager.getConcreteImplementation(StorageConfiguration.runtimeFileManager).toFuture

  def getJwtReader()(implicit ec: ExecutionContext): Future[JWTReader] = JWTConfiguration.jwtReader
    .loadKeyset()
    .toFuture
    .map(keyset =>
      new DefaultJWTReader with PublicKeysHolder {
        var publicKeyset: Map[KID, SerializedKey]                                        = keyset
        override protected val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
          getClaimsVerifier(audience = ApplicationConfiguration.jwtAudience)
      }
    )

  def processApi(jwtReader: JWTReader, fileManager: FileManager)(implicit
    ec: ExecutionContext,
    actorSystem: ActorSystem[_]
  ): ProcessApi =
    new ProcessApi(
      ProcessApiServiceImpl(
        catalogManagementService = catalogManagementService(),
        partyManagementService = partyManagementService(),
        attributeRegistryManagementService = attributeRegistryManagementService(),
        agreementManagementService = agreementManagementService(),
        authorizationManagementService = authorizationManagementService(),
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

  private def agreementManagementInvoker()(implicit actorSystem: ActorSystem[_]): AgreementManagementInvoker =
    AgreementManagementInvoker()(actorSystem.classicSystem)
  private val agreementApi: AgreementApi          = AgreementApi(ApplicationConfiguration.agreementManagementUrl)
  def agreementManagementService()(implicit
    ec: ExecutionContext,
    actorSystem: ActorSystem[_]
  ): AgreementManagementService =
    AgreementManagementServiceImpl(agreementManagementInvoker(), agreementApi)

  private def authorizationManagementInvoker()(implicit
    actorSystem: ActorSystem[_],
    blockingEc: ExecutionContext
  ): AuthorizationManagementInvoker =
    AuthorizationManagementInvoker()(actorSystem.classicSystem, blockingEc)
  private def authorizationPurposeApi: PurposeApi =
    PurposeApi(ApplicationConfiguration.authorizationManagementUrl)
  def authorizationManagementService()(implicit
    ec: ExecutionContext,
    actorSystem: ActorSystem[_]
  ): AuthorizationManagementService =
    AuthorizationManagementServiceImpl(authorizationManagementInvoker(), authorizationPurposeApi)

  private def attributeRegistryManagementInvoker()(implicit
    actorSystem: ActorSystem[_]
  ): AttributeRegistryManagementInvoker =
    AttributeRegistryManagementInvoker()(actorSystem.classicSystem)
  private def attributeApi: AttributeApi = AttributeApi(ApplicationConfiguration.attributeRegistryManagementUrl)
  def attributeRegistryManagementService()(implicit
    ec: ExecutionContext,
    actorSystem: ActorSystem[_]
  ): AttributeRegistryManagementService =
    AttributeRegistryManagementServiceImpl(attributeRegistryManagementInvoker(), attributeApi)

  private def catalogManagementInvoker()(implicit actorSystem: ActorSystem[_]): CatalogManagementInvoker =
    CatalogManagementInvoker()(actorSystem.classicSystem)
  private def catalogApi: EServiceApi = EServiceApi(ApplicationConfiguration.catalogManagementUrl)
  def catalogManagementService()(implicit actorSystem: ActorSystem[_], ec: ExecutionContext): CatalogManagementService =
    CatalogManagementServiceImpl(catalogManagementInvoker(), catalogApi)

  private def partyManagementInvoker()(implicit actorSystem: ActorSystem[_]): PartyManagementInvoker =
    PartyManagementInvoker()(actorSystem.classicSystem)
  private def partyApi: PartyApi = PartyApi(ApplicationConfiguration.partyManagementUrl)
  def partyManagementService()(implicit actorSystem: ActorSystem[_]): PartyManagementService =
    PartyManagementServiceImpl(partyManagementInvoker(), partyApi)

  val validationExceptionToRoute: ValidationReport => Route = report => {
    val error =
      problemOf(StatusCodes.BadRequest, ValidationRequestError(OpenapiUtils.errorFromRequestValidationReport(report)))
    complete(error.status, error)(HealthApiMarshallerImpl.toEntityMarshallerProblem)
  }

}
