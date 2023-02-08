package it.pagopa.interop.catalogprocess.util

import cats.syntax.all._
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
  UpdateEServiceSeed,
  CreateEServiceDescriptorDocumentSeed
}
import it.pagopa.interop.catalogprocess.service.{
  AgreementManagementService,
  AttributeRegistryManagementService,
  AuthorizationManagementService,
  CatalogManagementService,
  TenantManagementService
}
import it.pagopa.interop.commons.cqrs.service.ReadModelService

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import it.pagopa.interop.tenantmanagement.client.model.Tenant
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier
import org.mongodb.scala.bson.conversions.Bson
import spray.json.JsonReader

/**
 * Holds fake implementation of dependencies for tests not requiring neither mocks or stubs
 */
object FakeDependencies {
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

  class FakeReadModelService extends ReadModelService {
    override def findOne[T](collectionName: String, filter: Bson)(implicit
      evidence$1: JsonReader[T],
      ec: ExecutionContext
    ): Future[Option[T]] = Future.successful(None)
    override def find[T](collectionName: String, filter: Bson, offset: Int, limit: Int)(implicit
      evidence$2: JsonReader[T],
      ec: ExecutionContext
    ): Future[Seq[T]] = Future.successful(Nil)
    override def find[T](collectionName: String, filter: Bson, projection: Bson, offset: Int, limit: Int)(implicit
      evidence$3: JsonReader[T],
      ec: ExecutionContext
    ): Future[Seq[T]] = Future.successful(Nil)
    override def aggregate[T](collectionName: String, pipeline: Seq[Bson], offset: Int, limit: Int)(implicit
      evidence$4: JsonReader[T],
      ec: ExecutionContext
    ): Future[Seq[T]] = Future.successful(Nil)
    override def close(): Unit = ()
  }

}
