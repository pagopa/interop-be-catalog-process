package it.pagopa.pdnd.interop.uservice.catalogprocess.service

import akka.http.scaladsl.server.directives.FileInfo
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.invoker.BearerToken
import it.pagopa.pdnd.interop.uservice.catalogprocess.model.UpdateDescriptorSeed
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.EService
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.EServiceSeed

import java.io.File
import scala.concurrent.Future

trait CatalogManagementService {
  def listEServices(
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

  def createEServiceDocument(
    bearerToken: BearerToken,
    eServiceId: String,
    descriptorId: String,
    kind: String,
    description: String,
    doc: (FileInfo, File)
  ): Future[EService]

  def getEServiceDocument(
    bearerToken: BearerToken,
    eServiceId: String,
    descriptorId: String,
    documentId: String
  ): Future[File]

}
