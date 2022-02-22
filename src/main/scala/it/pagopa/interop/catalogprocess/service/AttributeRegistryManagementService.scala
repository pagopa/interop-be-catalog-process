package it.pagopa.interop.catalogprocess.service

import it.pagopa.interop.attributeregistrymanagement.client.model.Attribute

import scala.concurrent.Future

trait AttributeRegistryManagementService {

  def getAttributesBulk(attributeIds: Seq[String])(bearerToken: String): Future[Seq[Attribute]]

}
