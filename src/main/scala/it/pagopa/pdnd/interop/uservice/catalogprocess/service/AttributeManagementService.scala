package it.pagopa.pdnd.interop.uservice.catalogprocess.service

import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.client.model.Attribute

import scala.concurrent.Future

trait AttributeManagementService {

  def getAttribute(attributeId: String): Future[Attribute]

  def getAttributesBulk(attributeIds: Seq[String]): Future[Seq[Attribute]]

}
