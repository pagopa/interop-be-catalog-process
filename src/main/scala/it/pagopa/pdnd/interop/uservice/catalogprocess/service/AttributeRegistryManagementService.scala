package it.pagopa.pdnd.interop.uservice.catalogprocess.service

import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.client.model.Attribute

import scala.concurrent.Future

trait AttributeRegistryManagementService {

  def getAttributesBulk(attributeIds: Seq[String]): Future[Seq[Attribute]]

}
