package it.pagopa.interop.catalogprocess

import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagementDependency}
import it.pagopa.interop.catalogmanagement.{model => CatalogManagement}
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier

import java.util.UUID

object SpecData {
  val eServiceId   = UUID.randomUUID()
  val docId        = UUID.randomUUID()
  val descriptorId = UUID.randomUUID()

  val catalogItem: CatalogManagement.CatalogItem = CatalogManagement.CatalogItem(
    id = eServiceId,
    producerId = UUID.randomUUID(),
    name = "CatalogItemName",
    description = "CatalogItemDescription",
    technology = CatalogManagement.Rest,
    attributes = None,
    descriptors = Seq.empty,
    createdAt = OffsetDateTimeSupplier.get()
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
    descriptors = Seq.empty
  )
}
