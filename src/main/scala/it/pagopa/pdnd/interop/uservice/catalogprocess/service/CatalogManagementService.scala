package it.pagopa.pdnd.interop.uservice.catalogprocess.service

import akka.http.scaladsl.server.directives.FileInfo
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.invoker.BearerToken
import it.pagopa.pdnd.interopuservice.catalogprocess.model._

import java.io.File
import scala.concurrent.Future

trait CatalogManagementService {
  def listEServices(
    bearerToken: BearerToken
  )(producerId: Option[String], consumerId: Option[String], status: Option[String]): Future[Seq[EService]]
  def getEService(bearerToken: BearerToken)(eServiceId: String): Future[EService]
  def createEService(bearerToken: BearerToken)(eServiceSeed: EServiceSeed): Future[EService]
  def deleteDraft(bearerToken: BearerToken)(eServiceId: String, descriptorId: String): Future[Unit]
  def updateEservice(bearer: BearerToken)(eServiceId: String, updateEServiceSeed: UpdateEServiceSeed): Future[EService]

  def createDescriptor(
    bearer: BearerToken
  )(eServiceId: String, eServiceDescriptorSeed: EServiceDescriptorSeed): Future[EServiceDescriptor]
  def deprecateDescriptor(bearerToken: BearerToken)(eServiceId: String, descriptorId: String): Future[Unit]
  def archiveDescriptor(bearerToken: BearerToken)(eServiceId: String, descriptorId: String): Future[Unit]
  def publishDescriptor(bearerToken: BearerToken)(eServiceId: String, descriptorId: String): Future[Unit]
  def draftDescriptor(bearerToken: BearerToken)(eServiceId: String, descriptorId: String): Future[Unit]
  def hasNotDraftDescriptor(eService: EService): Future[Boolean]
  def updateDraftDescriptor(
    bearerToken: BearerToken
  )(eServiceId: String, descriptorId: String, seed: UpdateEServiceDescriptorSeed): Future[EService]

  def createEServiceDocument(bearerToken: BearerToken)(
    eServiceId: String,
    descriptorId: String,
    kind: String,
    description: String,
    doc: (FileInfo, File)
  ): Future[EService]

  def getEServiceDocument(
    bearerToken: BearerToken
  )(eServiceId: String, descriptorId: String, documentId: String): Future[File]

  def updateEServiceDocument(bearer: BearerToken)(
    eServiceId: String,
    descriptorId: String,
    documentId: String,
    updateEServiceDescriptorDocumentSeed: UpdateEServiceDescriptorDocumentSeed
  ): Future[EServiceDoc]

  def deleteEServiceDocument(
    bearer: BearerToken
  )(eServiceId: String, descriptorId: String, documentId: String): Future[Unit]
}
