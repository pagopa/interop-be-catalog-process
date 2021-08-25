package it.pagopa.pdnd.interop.uservice.catalogprocess.service

import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.invoker.BearerToken
import it.pagopa.pdnd.interop.uservice.catalogprocess.model.UpdateDescriptorSeed
import it.pagopa.pdnd.interopuservice.catalogprocess.model.{EService, EServiceSeed}

import scala.concurrent.Future

trait CatalogManagementService {
  def getEServices( // TODO rename to listEServices
    bearerToken: BearerToken,
    producerId: Option[String],
    consumerId: Option[String],
    status: Option[String]
  ): Future[Seq[EService]]
  def getEService(bearerToken: BearerToken, eServiceId: String): Future[EService]
  def createEService(bearerToken: BearerToken, eServiceSeed: EServiceSeed): Future[EService]
  def deleteDraft(bearerToken: BearerToken, eServiceId: String, descriptorId: String): Future[Unit]

  def updateDescriptor(
    bearerToken: BearerToken,
    eServiceId: String,
    descriptorId: String,
    seed: UpdateDescriptorSeed
  ): Future[EService]
}
