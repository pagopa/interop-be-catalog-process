package it.pagopa.interop.catalogprocess

import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagementDependency}
import it.pagopa.interop.catalogmanagement.{model => CatalogManagement}
import it.pagopa.interop.agreementmanagement.model.agreement.{PersistentAgreement, Active, PersistentStamps}
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier

import java.util.UUID

object SpecData {
  val eServiceId   = UUID.randomUUID()
  val docId        = UUID.randomUUID()
  val descriptorId = UUID.randomUUID()

  val agreement: PersistentAgreement = PersistentAgreement(
    id = UUID.randomUUID(),
    eserviceId = UUID.randomUUID(),
    descriptorId = UUID.randomUUID(),
    producerId = UUID.randomUUID(),
    consumerId = UUID.randomUUID(),
    state = Active,
    verifiedAttributes = Seq.empty,
    certifiedAttributes = Seq.empty,
    declaredAttributes = Seq.empty,
    suspendedByConsumer = None,
    suspendedByProducer = None,
    suspendedByPlatform = None,
    consumerDocuments = Seq.empty,
    createdAt = OffsetDateTimeSupplier.get(),
    updatedAt = None,
    consumerNotes = None,
    contract = None,
    stamps = PersistentStamps(),
    rejectionReason = None,
    suspendedAt = None
  )

  val catalogItem: CatalogManagement.CatalogItem = CatalogManagement.CatalogItem(
    id = eServiceId,
    producerId = UUID.randomUUID(),
    name = "CatalogItemName",
    description = "CatalogItemDescription",
    technology = CatalogManagement.Rest,
    attributes = None,
    descriptors = Seq.empty,
    createdAt = OffsetDateTimeSupplier.get(),
    riskAnalysis = Seq.empty,
    mode = CatalogManagement.Deliver
  )

  val catalogDocument: CatalogManagement.CatalogDocument     = CatalogManagement.CatalogDocument(
    id = docId,
    name = "name",
    contentType = "application/pdf",
    prettyName = "prettyName",
    path = "path",
    checksum = "checksum",
    uploadDate = OffsetDateTimeSupplier.get().minusDays(30)
  )
  val catalogDescriptor: CatalogManagement.CatalogDescriptor = CatalogManagement.CatalogDescriptor(
    id = descriptorId,
    version = "1",
    description = None,
    audience = Seq.empty,
    voucherLifespan = 0,
    dailyCallsPerConsumer = 0,
    dailyCallsTotal = 0,
    interface = None,
    docs = Seq.empty,
    state = CatalogManagement.Published,
    agreementApprovalPolicy = Some(CatalogManagement.Automatic),
    serverUrls = Nil,
    createdAt = OffsetDateTimeSupplier.get(),
    publishedAt = Some(OffsetDateTimeSupplier.get()),
    suspendedAt = None,
    deprecatedAt = None,
    archivedAt = None,
    attributes = CatalogManagement.CatalogAttributes.empty
  )

  val eServiceDescriptor: CatalogManagementDependency.EServiceDescriptor =
    CatalogManagementDependency.EServiceDescriptor(
      id = descriptorId,
      version = "1",
      description = None,
      audience = Seq.empty,
      voucherLifespan = 0,
      dailyCallsPerConsumer = 0,
      dailyCallsTotal = 0,
      interface = None,
      docs = Seq.empty,
      state = CatalogManagementDependency.EServiceDescriptorState.PUBLISHED,
      agreementApprovalPolicy = CatalogManagementDependency.AgreementApprovalPolicy.AUTOMATIC,
      serverUrls = Nil,
      attributes = CatalogManagementDependency.Attributes(Nil, Nil, Nil)
    )

  val eServiceDoc: CatalogManagementDependency.EServiceDoc =
    CatalogManagementDependency.EServiceDoc(
      id = docId,
      name = "name",
      contentType = "application/pdf",
      prettyName = "pretty",
      path = "path",
      checksum = "checksum",
      uploadDate = OffsetDateTimeSupplier.get().minusDays(10)
    )

  val eService: CatalogManagementDependency.EService = CatalogManagementDependency.EService(
    id = eServiceId,
    producerId = UUID.randomUUID(),
    name = "EService1",
    description = "description",
    technology = CatalogManagementDependency.EServiceTechnology.REST,
    descriptors = Seq.empty,
    riskAnalysis = Seq.empty,
    mode = CatalogManagementDependency.EServiceMode.DELIVER
  )
}
