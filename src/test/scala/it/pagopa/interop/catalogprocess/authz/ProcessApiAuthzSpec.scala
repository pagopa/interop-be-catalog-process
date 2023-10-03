package it.pagopa.interop.catalogprocess.authz

import it.pagopa.interop.catalogprocess.api.impl.ProcessApiMarshallerImpl._
import it.pagopa.interop.catalogprocess.api.impl.ProcessApiServiceImpl
import it.pagopa.interop.catalogprocess.model.AgreementApprovalPolicy.AUTOMATIC
import it.pagopa.interop.catalogprocess.model._
import it.pagopa.interop.catalogprocess.service._
import it.pagopa.interop.catalogprocess.util.FakeDependencies._
import it.pagopa.interop.catalogprocess.util.{AuthorizedRoutes, AuthzScalatestRouteTest}
import it.pagopa.interop.commons.cqrs.service.{ReadModelService, MongoDbReadModelService}
import it.pagopa.interop.commons.cqrs.model.ReadModelConfig
import it.pagopa.interop.commons.files.service.FileManager
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.concurrent.{ExecutorService, Executors}
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import java.util.UUID

class ProcessApiAuthzSpec extends AnyWordSpecLike with BeforeAndAfterAll with AuthzScalatestRouteTest {

  val fakeAgreementManagementService: AgreementManagementService         = new FakeAgreementManagementService()
  val fakeCatalogManagementService: CatalogManagementService             = new FakeCatalogManagementService()
  val fakeAuthorizationManagementService: AuthorizationManagementService = new FakeAuthorizationManagementService()
  val fakeTenantManagementService: TenantManagementService               = new FakeTenantManagementService()
  private val threadPool: ExecutorService                                = Executors.newSingleThreadExecutor()
  private val blockingEc: ExecutionContextExecutor = ExecutionContext.fromExecutorService(threadPool)
  val fakeFileManager: FileManager                 = FileManager.get(FileManager.File)(blockingEc)
  implicit val fakeReadModel: ReadModelService     = new MongoDbReadModelService(
    ReadModelConfig(
      "mongodb://localhost/?socketTimeoutMS=1&serverSelectionTimeoutMS=1&connectTimeoutMS=1&&autoReconnect=false&keepAlive=false",
      "db"
    )
  )

  override def afterAll(): Unit = { threadPool.shutdown() }

  val service: ProcessApiServiceImpl =
    ProcessApiServiceImpl(
      fakeCatalogManagementService,
      fakeAgreementManagementService,
      fakeAuthorizationManagementService,
      fakeTenantManagementService,
      fakeFileManager
    )(ExecutionContext.global, fakeReadModel)

  "E-Service api operation authorization spec" should {

    "accept authorized roles for createEService" in {
      val endpoint = AuthorizedRoutes.endpoints("createEService")
      val fakeSeed =
        EServiceSeed("test", "test", EServiceTechnology.REST, mode = EServiceMode.DELIVER)
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
        { implicit c: Seq[(String, String)] =>
          service.getEServices(None, "fake", "fake", "fake", "fake", "fake", 0, 0)
        }
      )
    }

    "accept authorized roles for getEServiceConsumers" in {
      val endpoint = AuthorizedRoutes.endpoints("getEServiceConsumers")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.getEServiceConsumers(0, 0, "fake") }
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
      val fakeSeed = UpdateEServiceDescriptorSeed(
        None,
        Seq.empty,
        0,
        0,
        0,
        AUTOMATIC,
        AttributesSeed(Seq.empty, Seq.empty, Seq.empty)
      )
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.updateDraftDescriptor("fake", "fake", fakeSeed) }
      )
    }

    "accept authorized roles for updateEServiceById" in {
      val endpoint = AuthorizedRoutes.endpoints("updateEServiceById")
      val fakeSeed =
        UpdateEServiceSeed("test", "test", EServiceTechnology.REST, EServiceMode.DELIVER)
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.updateEServiceById("fake", fakeSeed) }
      )
    }

    "accept authorized roles for createDescriptor" in {
      val endpoint = AuthorizedRoutes.endpoints("createDescriptor")
      val fakeSeed =
        EServiceDescriptorSeed(None, Seq.empty, 0, 0, 0, AUTOMATIC, AttributesSeed(Seq.empty, Seq.empty, Seq.empty))
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

    "accept authorized roles for archiveDescriptor" in {
      val endpoint = AuthorizedRoutes.endpoints("archiveDescriptor")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.archiveDescriptor("fake", "fake") }
      )
    }

    "accept authorized roles for createRiskAnalysis" in {
      val endpoint = AuthorizedRoutes.endpoints("createRiskAnalysis")
      val fakeSeed =
        EServiceRiskAnalysisSeed("test", EServiceRiskAnalysisFormSeed(version = "fake", answers = Map.empty))
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.createRiskAnalysis(UUID.randomUUID().toString, fakeSeed) }
      )
    }

    "accept authorized roles for updateRiskAnalysis" in {
      val endpoint = AuthorizedRoutes.endpoints("updateRiskAnalysis")
      val fakeSeed =
        EServiceRiskAnalysisSeed("test", EServiceRiskAnalysisFormSeed(version = "fake", answers = Map.empty))
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] =>
          service.updateRiskAnalysis(UUID.randomUUID().toString, UUID.randomUUID().toString, fakeSeed)
        }
      )
    }

    "accept authorized roles for deleteRiskAnalysis" in {
      val endpoint = AuthorizedRoutes.endpoints("deleteRiskAnalysis")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] =>
          service.deleteRiskAnalysis(UUID.randomUUID().toString, UUID.randomUUID().toString)
        }
      )
    }
  }
}
