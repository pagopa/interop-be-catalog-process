package it.pagopa.interop.catalogprocess.util

import it.pagopa.interop.authorizationmanagement.client.model._
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import it.pagopa.interop.agreementmanagement.model.agreement.{Active, PersistentAgreement, PersistentAgreementState}
import it.pagopa.interop.catalogmanagement.client.model.{
  AgreementApprovalPolicy,
  Attributes,
  EService,
  EServiceDescriptor,
  EServiceDescriptorSeed,
  EServiceDescriptorState,
  EServiceDoc,
  EServiceSeed,
  EServiceTechnology,
  UpdateEServiceDescriptorDocumentSeed,
  UpdateEServiceDescriptorSeed,
  UpdateEServiceSeed,
  CreateEServiceDescriptorDocumentSeed,
  RiskAnalysisSeed
}
import it.pagopa.interop.catalogprocess.common.readmodel.{PaginatedResult, Consumers}
import it.pagopa.interop.catalogmanagement.model.{
  CatalogItem,
  Rest,
  CatalogAttributes,
  CatalogDocument,
  Published,
  CatalogDescriptorState,
  Deliver
}
import it.pagopa.interop.tenantmanagement.model.tenant.{PersistentTenant, PersistentTenantKind, PersistentExternalId}
import it.pagopa.interop.catalogprocess.service.{
  AuthorizationManagementService,
  CatalogManagementService,
  AgreementManagementService,
  TenantManagementService
}
import it.pagopa.interop.catalogmanagement.client.model.{EServiceMode, EServiceRiskAnalysis, RiskAnalysisForm}

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/**
 * Holds fake implementation of dependencies for tests not requiring neither mocks or stubs
 */
object FakeDependencies {

  class FakeCatalogManagementService extends CatalogManagementService {

    override def getEServiceById(
      eServiceId: UUID
    )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[CatalogItem] =
      Future.successful(
        CatalogItem(
          id = UUID.randomUUID(),
          producerId = UUID.randomUUID(),
          name = "fake",
          description = "fake",
          technology = Rest,
          descriptors = Seq.empty,
          attributes = Some(CatalogAttributes.empty),
          createdAt = OffsetDateTime.now(),
          riskAnalysis = Seq.empty,
          mode = Deliver
        )
      )

    override def getEServiceDocument(eServiceId: UUID, descriptorId: UUID, documentId: UUID)(implicit
      ec: ExecutionContext,
      readModel: ReadModelService
    ): Future[CatalogDocument] = Future.successful(
      CatalogDocument(
        id = UUID.randomUUID(),
        name = "fake",
        contentType = "fake",
        prettyName = "fake",
        path = "fake",
        checksum = "fake",
        uploadDate = OffsetDateTime.now()
      )
    )

    override def createEService(
      eServiceSeed: EServiceSeed
    )(implicit contexts: Seq[(String, String)]): Future[EService] = Future.successful(
      EService(
        id = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        name = "fake",
        description = "fake",
        technology = EServiceTechnology.REST,
        descriptors = Seq.empty,
        riskAnalysis = Seq.empty,
        mode = EServiceMode.DELIVER
      )
    )

    override def deleteDraft(eServiceId: String, descriptorId: String)(implicit
      contexts: Seq[(String, String)]
    ): Future[Unit] = Future.successful(())

    override def updateEServiceById(eServiceId: String, updateEServiceSeed: UpdateEServiceSeed)(implicit
      contexts: Seq[(String, String)]
    ): Future[EService] = Future.successful(
      EService(
        id = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        name = "fake",
        description = "fake",
        technology = EServiceTechnology.REST,
        descriptors = Seq.empty,
        riskAnalysis = Seq.empty,
        mode = EServiceMode.DELIVER
      )
    )

    override def cloneEService(eServiceId: UUID, descriptorId: UUID)(implicit
      contexts: Seq[(String, String)]
    ): Future[EService] = Future.successful(
      EService(
        id = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        name = "fake",
        description = "fake",
        technology = EServiceTechnology.REST,
        descriptors = Seq.empty,
        riskAnalysis = Seq.empty,
        mode = EServiceMode.DELIVER
      )
    )

    override def deleteEService(eServiceId: String)(implicit contexts: Seq[(String, String)]): Future[Unit] =
      Future.successful(())

    override def createDescriptor(eServiceId: String, eServiceDescriptorSeed: EServiceDescriptorSeed)(implicit
      contexts: Seq[(String, String)]
    ): Future[EServiceDescriptor] = Future.successful(
      EServiceDescriptor(
        id = UUID.randomUUID(),
        version = "???",
        description = None,
        audience = Seq.empty,
        voucherLifespan = 0,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        interface = None,
        docs = Seq.empty,
        state = EServiceDescriptorState.PUBLISHED,
        agreementApprovalPolicy = AgreementApprovalPolicy.AUTOMATIC,
        serverUrls = Nil,
        attributes = Attributes(Nil, Nil, Nil)
      )
    )

    override def deprecateDescriptor(eServiceId: String, descriptorId: String)(implicit
      contexts: Seq[(String, String)]
    ): Future[Unit] = Future.successful(())

    override def archiveDescriptor(eServiceId: String, descriptorId: String)(implicit
      contexts: Seq[(String, String)]
    ): Future[Unit] = Future.successful(())

    override def publishDescriptor(eServiceId: String, descriptorId: String)(implicit
      contexts: Seq[(String, String)]
    ): Future[Unit] = Future.successful(())

    override def draftDescriptor(eServiceId: String, descriptorId: String)(implicit
      contexts: Seq[(String, String)]
    ): Future[Unit] = Future.successful(())

    override def suspendDescriptor(eServiceId: String, descriptorId: String)(implicit
      contexts: Seq[(String, String)]
    ): Future[Unit] = Future.successful(())

    override def updateDraftDescriptor(eServiceId: String, descriptorId: String, seed: UpdateEServiceDescriptorSeed)(
      implicit contexts: Seq[(String, String)]
    ): Future[EService] = Future.successful(
      EService(
        id = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        name = "fake",
        description = "fake",
        technology = EServiceTechnology.REST,
        descriptors = Seq.empty,
        riskAnalysis = Seq.empty,
        mode = EServiceMode.DELIVER
      )
    )

    override def createEServiceDocument(
      eServiceId: UUID,
      descriptorId: UUID,
      documentSeed: CreateEServiceDescriptorDocumentSeed
    )(implicit contexts: Seq[(String, String)]): Future[EService] = Future.successful(
      EService(
        id = eServiceId,
        producerId = UUID.randomUUID(),
        name = "fake",
        description = "fake",
        technology = EServiceTechnology.REST,
        descriptors = Seq(
          EServiceDescriptor(
            id = descriptorId,
            version = "???",
            description = None,
            audience = Seq.empty,
            voucherLifespan = 0,
            dailyCallsPerConsumer = 0,
            dailyCallsTotal = 0,
            interface = None,
            docs = Seq.empty,
            state = EServiceDescriptorState.PUBLISHED,
            agreementApprovalPolicy = AgreementApprovalPolicy.AUTOMATIC,
            serverUrls = Nil,
            attributes = Attributes(Nil, Nil, Nil)
          )
        ),
        riskAnalysis = Seq.empty,
        mode = EServiceMode.DELIVER
      )
    )

    override def updateEServiceDocument(
      eServiceId: String,
      descriptorId: String,
      documentId: String,
      updateEServiceDescriptorDocumentSeed: UpdateEServiceDescriptorDocumentSeed
    )(implicit contexts: Seq[(String, String)]): Future[EServiceDoc] =
      Future.successful(
        EServiceDoc(
          id = UUID.randomUUID(),
          name = "a",
          contentType = "b",
          prettyName = "c",
          path = "d",
          checksum = "e",
          uploadDate = OffsetDateTime.now()
        )
      )

    override def deleteEServiceDocument(eServiceId: String, descriptorId: String, documentId: String)(implicit
      contexts: Seq[(String, String)]
    ): Future[Unit] = Future.successful(())

    override def getConsumers(eServiceId: UUID, offset: Int, limit: Int)(implicit
      ec: ExecutionContext,
      readModel: ReadModelService
    ): Future[PaginatedResult[Consumers]] = Future.successful(
      PaginatedResult(
        results = Seq(
          Consumers(
            descriptorVersion = "fake",
            descriptorState = Published,
            agreementState = Active,
            consumerName = "fake",
            consumerExternalId = "fake"
          )
        ),
        totalCount = 1
      )
    )

    override def getEServices(
      name: Option[String],
      eServicesIds: Seq[UUID],
      producersIds: Seq[UUID],
      attributesIds: Seq[UUID],
      states: Seq[CatalogDescriptorState],
      offset: Int,
      limit: Int,
      exactMatchOnName: Boolean = false
    )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[PaginatedResult[CatalogItem]] =
      Future.successful(PaginatedResult(results = Seq.empty, totalCount = 0))

    override def createRiskAnalysis(eServiceId: UUID, riskAnalysisSeed: RiskAnalysisSeed)(implicit
      contexts: Seq[(String, String)]
    ): Future[Unit] = Future.successful(())

    override def updateRiskAnalysis(eServiceId: UUID, riskAnalysisId: UUID, riskAnalysisSeed: RiskAnalysisSeed)(implicit
      contexts: Seq[(String, String)]
    ): Future[EServiceRiskAnalysis] = Future.successful(
      (
        EServiceRiskAnalysis(
          id = UUID.randomUUID(),
          name = "name",
          riskAnalysisForm = RiskAnalysisForm(
            id = UUID.randomUUID(),
            version = "version",
            singleAnswers = Seq.empty,
            multiAnswers = Seq.empty
          ),
          createdAt = OffsetDateTime.now()
        )
      )
    )

    override def deleteRiskAnalysis(eServiceId: UUID, riskAnalysisId: UUID)(implicit
      contexts: Seq[(String, String)]
    ): Future[Unit] = Future.successful(())
  }
  class FakeAuthorizationManagementService extends AuthorizationManagementService {
    override def updateStateOnClients(
      eServiceId: UUID,
      descriptorId: UUID,
      state: ClientComponentState,
      audience: Seq[String],
      voucherLifespan: Int
    )(implicit contexts: Seq[(String, String)]): Future[Unit] = Future.successful(())
  }

  class FakeAgreementManagementService extends AgreementManagementService {
    override def getAgreements(
      eServicesIds: Seq[UUID],
      consumersIds: Seq[UUID],
      producersIds: Seq[UUID],
      states: Seq[PersistentAgreementState]
    )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[Seq[PersistentAgreement]] =
      Future.successful(Seq.empty)
  }

  class FakeTenantManagementService extends TenantManagementService {
    override def getTenantById(
      tenantId: UUID
    )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[PersistentTenant] = Future.successful(
      PersistentTenant(
        id = UUID.randomUUID(),
        kind = Some(PersistentTenantKind.PA),
        selfcareId = None,
        externalId = PersistentExternalId("IPA", "value"),
        features = Nil,
        attributes = Nil,
        createdAt = OffsetDateTime.now(),
        updatedAt = Some(OffsetDateTime.now().plusDays(10)),
        mails = Nil,
        name = "name"
      )
    )
  }
}
