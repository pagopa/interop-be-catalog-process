package it.pagopa.pdnd.interop.uservice.catalogprocess.service

import akka.http.scaladsl.server.directives.FileInfo
import it.pagopa.pdnd.interop.uservice.catalogprocess.model._

import java.io.File
import scala.concurrent.Future

trait CatalogManagementService {
  def listEServices(bearerToken: String)(producerId: Option[String], status: Option[String]): Future[Seq[EService]]
  def getEService(bearerToken: String)(eServiceId: String): Future[EService]
  def createEService(bearerToken: String)(eServiceSeed: EServiceSeed): Future[EService]
  def deleteDraft(bearerToken: String)(eServiceId: String, descriptorId: String): Future[Unit]
  def updateEservice(bearerToken: String)(eServiceId: String, updateEServiceSeed: UpdateEServiceSeed): Future[EService]
  def cloneEservice(bearer: String)(eServiceId: String, descriptorId: String): Future[EService]

  def createDescriptor(
    bearerToken: String
  )(eServiceId: String, eServiceDescriptorSeed: EServiceDescriptorSeed): Future[EServiceDescriptor]
  def deprecateDescriptor(bearerToken: String)(eServiceId: String, descriptorId: String): Future[Unit]
  def archiveDescriptor(bearerToken: String)(eServiceId: String, descriptorId: String): Future[Unit]
  def publishDescriptor(bearerToken: String)(eServiceId: String, descriptorId: String): Future[Unit]
  def draftDescriptor(bearerToken: String)(eServiceId: String, descriptorId: String): Future[Unit]
  def hasNotDraftDescriptor(eService: EService): Future[Boolean]
  def updateDraftDescriptor(
    bearerToken: String
  )(eServiceId: String, descriptorId: String, seed: UpdateEServiceDescriptorSeed): Future[EService]

  def createEServiceDocument(bearerToken: String)(
    eServiceId: String,
    descriptorId: String,
    kind: String,
    description: String,
    doc: (FileInfo, File)
  ): Future[EService]

  def getEServiceDocument(
    bearerToken: String
  )(eServiceId: String, descriptorId: String, documentId: String): Future[File]

  def updateEServiceDocument(bearerToken: String)(
    eServiceId: String,
    descriptorId: String,
    documentId: String,
    updateEServiceDescriptorDocumentSeed: UpdateEServiceDescriptorDocumentSeed
  ): Future[EServiceDoc]

  def deleteEServiceDocument(
    bearerToken: String
  )(eServiceId: String, descriptorId: String, documentId: String): Future[Unit]
}
