package it.pagopa.interop.catalogprocess.service

import it.pagopa.interop.attributeregistrymanagement.client.model.Attribute

import scala.concurrent.Future

trait AttributeRegistryManagementService {

  def getAttributesBulk(attributeIds: Seq[String])(contexts: Seq[(String, String)]): Future[Seq[Attribute]]

}
