package it.pagopa.interop.catalogprocess.util

import it.pagopa.interop.authorizationmanagement.client.model._
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
  CreateEServiceDescriptorDocumentSeed
}
import it.pagopa.interop.catalogprocess.service.{AuthorizationManagementService, CatalogManagementService}

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.Future

/**
 * Holds fake implementation of dependencies for tests not requiring neither mocks or stubs
 */
object FakeDependencies {

  class FakeCatalogManagementService extends CatalogManagementService {

    override def createEService(
      eServiceSeed: EServiceSeed
    )(implicit contexts: Seq[(String, String)]): Future[EService] = Future.successful(
      EService(
        id = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        name = "fake",
        description = "fake",
        technology = EServiceTechnology.REST,
        attributes = Attributes(Seq.empty, Seq.empty, Seq.empty),
        descriptors = Seq.empty
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
        attributes = Attributes(Seq.empty, Seq.empty, Seq.empty),
        descriptors = Seq.empty
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
        attributes = Attributes(Seq.empty, Seq.empty, Seq.empty),
        descriptors = Seq.empty
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
        serverUrls = Nil
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
        attributes = Attributes(Seq.empty, Seq.empty, Seq.empty),
        descriptors = Seq.empty
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
        attributes = Attributes(Seq.empty, Seq.empty, Seq.empty),
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
            serverUrls = Nil
          )
        )
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
}
