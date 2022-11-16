package it.pagopa.interop.catalogprocess.util

import cats.syntax.all._
import akka.http.scaladsl.server.directives.FileInfo
import it.pagopa.interop.agreementmanagement.client.model.{Agreement, AgreementState}
import it.pagopa.interop.attributeregistrymanagement.client.model.Attribute
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
  UpdateEServiceSeed
}
import it.pagopa.interop.catalogprocess.service.{
  AgreementManagementService,
  AttributeRegistryManagementService,
  AuthorizationManagementService,
  CatalogManagementService,
  PartyManagementService
}
import it.pagopa.interop.selfcare.partymanagement.client.model.{BulkInstitutions, Institution}

import java.io.File
import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import it.pagopa.interop.catalogprocess.service.TenantManagementService
import it.pagopa.interop.tenantmanagement.client.model.Tenant
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier

/**
 * Holds fake implementation of dependencies for tests not requiring neither mocks or stubs
 */
object FakeDependencies {

  class FakePartyManagementService extends PartyManagementService {
    override def getInstitution(
      id: String
    )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Institution] =
      Future.successful(
        Institution(
          id = UUID.randomUUID(),
          externalId = "test",
          originId = "test",
          description = "test",
          digitalAddress = "???",
          address = "???",
          zipCode = "???",
          taxCode = "???",
          origin = "???",
          institutionType = "???",
          attributes = Seq.empty
        )
      )

    override def getBulkInstitutions(
      identifiers: List[String]
    )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[BulkInstitutions] =
      Future.successful(BulkInstitutions(Seq.empty, Seq.empty))
  }

  class FakeAttributeRegistryManagementService extends AttributeRegistryManagementService {
    override def getAttributesBulk(attributeIds: Seq[UUID])(implicit
      contexts: Seq[(String, String)]
    ): Future[Seq[Attribute]] = Future.successful(Seq.empty)
  }

  class FakeAgreementManagementService extends AgreementManagementService {
    override def getAgreements(
      consumerId: Option[String],
      producerId: Option[String],
      states: List[AgreementState],
      eServiceId: Option[String]
    )(implicit contexts: Seq[(String, String)]): Future[Seq[Agreement]] = Future.successful(Seq.empty)
  }
  class FakeCatalogManagementService   extends CatalogManagementService   {

    override def listEServices(producerId: Option[String], status: Option[EServiceDescriptorState])(implicit
      contexts: Seq[(String, String)]
    ): Future[Seq[EService]] = Future.successful(Seq.empty)

    override def getEService(eServiceId: String)(implicit contexts: Seq[(String, String)]): Future[EService] =
      Future.successful(
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

    override def updateEservice(eServiceId: String, updateEServiceSeed: UpdateEServiceSeed)(implicit
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

    override def cloneEservice(eServiceId: UUID, descriptorId: UUID)(implicit
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
        agreementApprovalPolicy = AgreementApprovalPolicy.AUTOMATIC
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

    override def hasNotDraftDescriptor(eService: EService)(implicit contexts: Seq[(String, String)]): Future[Boolean] =
      Future.successful(true)

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
      eServiceId: String,
      descriptorId: String,
      kind: String,
      prettyName: String,
      doc: (FileInfo, File)
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

    override def getEServiceDocument(eServiceId: String, descriptorId: String, documentId: String)(implicit
      contexts: Seq[(String, String)]
    ): Future[EServiceDoc] = Future.successful(
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

  class FakeTenantManagementService extends TenantManagementService {
    override def getTenant(tenantId: UUID)(implicit contexts: Seq[(String, String)]): Future[Tenant] =
      Future.successful(
        Tenant(
          id = UUID.randomUUID(),
          name = "a_name",
          selfcareId = UUID.randomUUID.toString.some,
          externalId = null,
          features = Nil,
          attributes = Nil,
          createdAt = OffsetDateTimeSupplier.get(),
          updatedAt = None,
          mails = Nil,
          name = "test_name"
        )
      )
  }

}
