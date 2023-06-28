package it.pagopa.interop.catalogprocess

import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagementDependency}
import it.pagopa.interop.catalogmanagement.{model => CatalogManagement}
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier

import java.util.UUID

object SpecData {
  val catalogItem: CatalogManagement.CatalogItem = CatalogManagement.CatalogItem(
    id = UUID.randomUUID(),
    producerId = UUID.randomUUID(),
    name = "CatalogItemName",
    description = "CatalogItemDescription",
    technology = CatalogManagement.Rest,
    attributes = None,
    descriptors = Seq.empty,
    createdAt = OffsetDateTimeSupplier.get()
  )

  val catalogDocument: CatalogManagement.CatalogDocument     = CatalogManagement.CatalogDocument(
    id = UUID.randomUUID(),
    name = "name",
    contentType = "application/pdf",
    prettyName = "prettyName",
    path = "path",
    checksum = "checksum",
    uploadDate = OffsetDateTimeSupplier.get().minusDays(30)
  )
  val catalogDescriptor: CatalogManagement.CatalogDescriptor = CatalogManagement.CatalogDescriptor(
    id = UUID.randomUUID(),
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
    archivedAt = None
  )

  val eServiceDescriptor: CatalogManagementDependency.EServiceDescriptor =
    CatalogManagementDependency.EServiceDescriptor(
      id = UUID.randomUUID(),
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
      serverUrls = Nil
    )

  val eServiceDoc: CatalogManagementDependency.EServiceDoc =
    CatalogManagementDependency.EServiceDoc(
      id = UUID.randomUUID(),
      name = "name",
      contentType = "application/pdf",
      prettyName = "pretty",
      path = "path",
      checksum = "checksum",
      uploadDate = OffsetDateTimeSupplier.get().minusDays(10)
    )

  val eService: CatalogManagementDependency.EService = CatalogManagementDependency.EService(
    id = UUID.randomUUID(),
    producerId = UUID.randomUUID(),
    name = "EService1",
    description = "description",
    technology = CatalogManagementDependency.EServiceTechnology.REST,
    attributes = CatalogManagementDependency.Attributes(Seq.empty, Seq.empty, Seq.empty),
    descriptors = Seq.empty
  )
}
