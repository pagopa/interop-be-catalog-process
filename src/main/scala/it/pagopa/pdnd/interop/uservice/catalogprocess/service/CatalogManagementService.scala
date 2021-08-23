package it.pagopa.pdnd.interop.uservice.catalogprocess.service

import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.invoker.BearerToken
import it.pagopa.pdnd.interopuservice.catalogprocess.model.{EService, EServiceSeed}

import scala.concurrent.Future

trait CatalogManagementService {
  def getEServices(
    bearerToken: BearerToken,
    producerId: Option[String],
    consumerId: Option[String],
    status: Option[String]
  ): Future[Seq[EService]]
  def createEService(bearerToken: BearerToken, eServiceSeed: EServiceSeed): Future[EService]
  def deleteDraft(bearerToken: BearerToken, eServiceId: String, descriptorId: String): Future[Unit]
}
