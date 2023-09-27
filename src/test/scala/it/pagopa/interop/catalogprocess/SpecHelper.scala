package it.pagopa.interop.catalogprocess

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import spray.json._
import org.scalamock.scalatest.MockFactory
import com.typesafe.config.{Config, ConfigFactory}

import it.pagopa.interop.commons.cqrs.service.ReadModelService
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.catalogprocess.service._
import it.pagopa.interop.catalogprocess.model._
import it.pagopa.interop.catalogprocess.api.ProcessApiService
import it.pagopa.interop.catalogprocess.api.impl._

import scala.concurrent.ExecutionContext

trait SpecHelper extends SprayJsonSupport with DefaultJsonProtocol with MockFactory {

  final val bearerToken: String = "token"

  val config: Config = ConfigFactory
    .parseResourcesAnySyntax("application-test")
    .resolve()

  implicit val mockReadModel: ReadModelService                           = mock[ReadModelService]
  val mockfileManager: FileManager                                       = mock[FileManager]
  val mockAuthorizationManagementService: AuthorizationManagementService = mock[AuthorizationManagementService]
  val mockCatalogManagementService: CatalogManagementService             = mock[CatalogManagementService]
  val mockAgreementManagementService: AgreementManagementService         = mock[AgreementManagementService]
  val mockTenantManagementService: TenantManagementService               = mock[TenantManagementService]

  val service: ProcessApiService = ProcessApiServiceImpl(
    mockCatalogManagementService,
    mockAgreementManagementService,
    mockAuthorizationManagementService,
    mockTenantManagementService,
    mockfileManager
  )(ExecutionContext.global, mockReadModel)

  implicit def fromResponseUnmarshallerProblem: FromEntityUnmarshaller[Problem]   =
    sprayJsonUnmarshaller[Problem]
  implicit def fromResponseUnmarshallerEService: FromEntityUnmarshaller[EService] =
    sprayJsonUnmarshaller[EService]
}
