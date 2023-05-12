package it.pagopa.interop.catalogprocess

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
    attributes = CatalogManagement.CatalogAttributes(Seq.empty, Seq.empty, Seq.empty),
    descriptors = Seq.empty,
    createdAt = OffsetDateTimeSupplier.get()
  )
}
