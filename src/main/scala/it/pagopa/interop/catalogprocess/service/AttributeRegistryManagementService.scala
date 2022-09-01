package it.pagopa.interop.catalogprocess.service

import it.pagopa.interop.attributeregistrymanagement.client.model.Attribute

import scala.concurrent.Future
import java.util.UUID

trait AttributeRegistryManagementService {

  def getAttributesBulk(attributeIds: Seq[UUID])(implicit contexts: Seq[(String, String)]): Future[Seq[Attribute]]

}
