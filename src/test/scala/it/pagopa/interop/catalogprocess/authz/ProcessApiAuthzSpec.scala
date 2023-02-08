package it.pagopa.interop.catalogprocess.authz

import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import it.pagopa.interop.catalogprocess.api.impl.ProcessApiMarshallerImpl._
import it.pagopa.interop.catalogprocess.api.impl.ProcessApiServiceImpl
import it.pagopa.interop.catalogprocess.model.AgreementApprovalPolicy.AUTOMATIC
import it.pagopa.interop.catalogprocess.model._
import it.pagopa.interop.catalogprocess.service._
import it.pagopa.interop.catalogprocess.util.FakeDependencies._
import it.pagopa.interop.catalogprocess.util.{AuthorizedRoutes, AuthzScalatestRouteTest}
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.jwt.service.impl.{DefaultJWTReader, getClaimsVerifier}
import it.pagopa.interop.commons.jwt.{KID, PublicKeysHolder, SerializedKey}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.concurrent.{ExecutorService, Executors}
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import java.util.UUID

class ProcessApiAuthzSpec extends AnyWordSpecLike with BeforeAndAfterAll with AuthzScalatestRouteTest {

  val fakeCatalogManagementService: CatalogManagementService                     = new FakeCatalogManagementService()
  val fakeAttributeRegistryManagementService: AttributeRegistryManagementService =
    new FakeAttributeRegistryManagementService()
  val fakeAgreementManagementService: AgreementManagementService                 = new FakeAgreementManagementService()
  val fakeAuthorizationManagementService: AuthorizationManagementService = new FakeAuthorizationManagementService()
  val fakeTenantManagementService: TenantManagementService               = new FakeTenantManagementService()
  private val threadPool: ExecutorService                                = Executors.newSingleThreadExecutor()
  private val blockingEc: ExecutionContextExecutor = ExecutionContext.fromExecutorService(threadPool)
  val fakeFileManager: FileManager                 = FileManager.get(FileManager.File)(blockingEc)
  val fakeReadModel: ReadModelService              = new FakeReadModelService
  val fakeJwtReader: JWTReader                     = new DefaultJWTReader with PublicKeysHolder {
    var publicKeyset: Map[KID, SerializedKey]                                        = Map.empty
    override protected val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
      getClaimsVerifier(audience = Set("fake"))
  }

  override def afterAll(): Unit = { threadPool.shutdown() }

  val service: ProcessApiServiceImpl =
    ProcessApiServiceImpl(
      fakeCatalogManagementService,
      fakeAttributeRegistryManagementService,
      fakeAgreementManagementService,
      fakeAuthorizationManagementService,
      fakeTenantManagementService,
      fakeReadModel,
      fakeFileManager,
      fakeJwtReader
    )(ExecutionContext.global)

  "E-Service api operation authorization spec" should {

    "accept authorized roles for createEService" in {
      val endpoint = AuthorizedRoutes.endpoints("createEService")
      val fakeSeed =
        EServiceSeed("test", "test", EServiceTechnology.REST, AttributesSeed(Seq.empty, Seq.empty, Seq.empty))
      validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.createEService(fakeSeed) })
    }

    "accept authorized roles for createEServiceDocument" in {
      val endpoint = AuthorizedRoutes.endpoints("createEServiceDocument")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] =>
          service.createEServiceDocument(
            eServiceId = UUID.randomUUID().toString,
            descriptorId = UUID.randomUUID().toString,
            CreateEServiceDescriptorDocumentSeed(
              documentId = UUID.randomUUID(),
              kind = EServiceDocumentKind.INTERFACE,
              prettyName = "fake",
              filePath = "fake",
              fileName = "fake",
              contentType = "fake",
              checksum = "fake",
              serverUrls = List()
            )
          )
        }
      )
    }

    "accept authorized roles for getEServiceById" in {
      val endpoint = AuthorizedRoutes.endpoints("getEServiceById")
      validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.getEServiceById("fake") })
    }

    "accept authorized roles for getEServices" in {
      val endpoint = AuthorizedRoutes.endpoints("getEServices")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.getEServices(None, "fake", "fake", "fake", 0, 0) }
      )
    }

    "accept authorized roles for getEServiceDocumentById" in {
      val endpoint = AuthorizedRoutes.endpoints("getEServiceDocumentById")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.getEServiceDocumentById("fake", "fake", "fake") }
      )
    }

    "accept authorized roles for deleteDraft" in {
      val endpoint = AuthorizedRoutes.endpoints("deleteDraft")
      validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.deleteDraft("fake", "fake") })
    }

    "accept authorized roles for updateDraftDescriptor" in {
      val endpoint = AuthorizedRoutes.endpoints("updateDraftDescriptor")
      val fakeSeed = UpdateEServiceDescriptorSeed(None, Seq.empty, 0, 0, 0, AUTOMATIC)
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.updateDraftDescriptor("fake", "fake", fakeSeed) }
      )
    }

    "accept authorized roles for updateEServiceById" in {
      val endpoint = AuthorizedRoutes.endpoints("updateEServiceById")
      val fakeSeed =
        UpdateEServiceSeed("test", "test", EServiceTechnology.REST, AttributesSeed(Seq.empty, Seq.empty, Seq.empty))
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.updateEServiceById("fake", fakeSeed) }
      )
    }

    "accept authorized roles for createDescriptor" in {
      val endpoint = AuthorizedRoutes.endpoints("createDescriptor")
      val fakeSeed = EServiceDescriptorSeed(None, Seq.empty, 0, 0, 0, AUTOMATIC)
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.createDescriptor("fake", fakeSeed) }
      )
    }

    "accept authorized roles for deleteEServiceDocumentById" in {
      val endpoint = AuthorizedRoutes.endpoints("deleteEServiceDocumentById")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.deleteEServiceDocumentById("fake", "fake", "fake") }
      )
    }

    "accept authorized roles for suspendDescriptor" in {
      val endpoint = AuthorizedRoutes.endpoints("suspendDescriptor")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.suspendDescriptor("fake", "fake") }
      )
    }

    "accept authorized roles for publishDescriptor" in {
      val endpoint = AuthorizedRoutes.endpoints("publishDescriptor")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.publishDescriptor("fake", "fake") }
      )
    }

    "accept authorized roles for updateEServiceDocumentById" in {
      val endpoint = AuthorizedRoutes.endpoints("updateEServiceDocumentById")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] =>
          service.updateEServiceDocumentById(
            eServiceId = "fake",
            descriptorId = "fake",
            documentId = "fake",
            updateEServiceDescriptorDocumentSeed = UpdateEServiceDescriptorDocumentSeed("fake")
          )
        }
      )
    }

    "accept authorized roles for cloneEServiceByDescriptor" in {
      val endpoint = AuthorizedRoutes.endpoints("cloneEServiceByDescriptor")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.cloneEServiceByDescriptor("fake", "fake") }
      )
    }

    "accept authorized roles for deleteEService" in {
      val endpoint = AuthorizedRoutes.endpoints("deleteEService")
      validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.deleteEService("fake") })
    }
  }

}
