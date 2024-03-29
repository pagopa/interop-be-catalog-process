package it.pagopa.interop.catalogprocess.service

import it.pagopa.interop.attributeregistrymanagement.model.persistence.attribute.PersistentAttribute
import it.pagopa.interop.commons.cqrs.service.ReadModelService

import java.util.UUID
import scala.concurrent.{Future, ExecutionContext}

trait AttributeRegistryManagementService {
  def getAttributeById(
    attributeId: UUID
  )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[PersistentAttribute]
}
