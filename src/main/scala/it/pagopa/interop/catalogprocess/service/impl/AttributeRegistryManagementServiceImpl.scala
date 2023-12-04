package it.pagopa.interop.catalogprocess.service.impl

import it.pagopa.interop.catalogprocess.service.AttributeRegistryManagementService
import it.pagopa.interop.attributeregistrymanagement.model.persistence.attribute.PersistentAttribute
import it.pagopa.interop.catalogprocess.errors.CatalogProcessErrors.AttributeNotFound
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.catalogprocess.common.readmodel.ReadModelAttributeQueries

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final object AttributeRegistryManagementServiceImpl extends AttributeRegistryManagementService {

  override def getAttributeById(
    attributeId: UUID
  )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[PersistentAttribute] =
    ReadModelAttributeQueries.getAttributeById(attributeId).flatMap(_.toFuture(AttributeNotFound(attributeId)))
}
