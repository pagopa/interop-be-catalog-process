package it.pagopa.interop.catalogprocess.service

import akka.http.scaladsl.server.directives.FileInfo
import it.pagopa.interop.catalogmanagement.client.model._

import java.io.File
import scala.concurrent.Future

trait CatalogManagementService {
  def listEServices(
    contexts: Seq[(String, String)]
  )(producerId: Option[String], status: Option[EServiceDescriptorState]): Future[Seq[EService]]
  def getEService(contexts: Seq[(String, String)])(eServiceId: String): Future[EService]
  def createEService(contexts: Seq[(String, String)])(eServiceSeed: EServiceSeed): Future[EService]
  def deleteDraft(contexts: Seq[(String, String)])(eServiceId: String, descriptorId: String): Future[Unit]
  def updateEservice(
    contexts: Seq[(String, String)]
  )(eServiceId: String, updateEServiceSeed: UpdateEServiceSeed): Future[EService]
  def cloneEservice(contexts: Seq[(String, String)])(eServiceId: String, descriptorId: String): Future[EService]
  def deleteEService(contexts: Seq[(String, String)])(eServiceId: String): Future[Unit]

  def createDescriptor(
    contexts: Seq[(String, String)]
  )(eServiceId: String, eServiceDescriptorSeed: EServiceDescriptorSeed): Future[EServiceDescriptor]
  def deprecateDescriptor(contexts: Seq[(String, String)])(eServiceId: String, descriptorId: String): Future[Unit]
  def archiveDescriptor(contexts: Seq[(String, String)])(eServiceId: String, descriptorId: String): Future[Unit]
  def publishDescriptor(contexts: Seq[(String, String)])(eServiceId: String, descriptorId: String): Future[Unit]
  def draftDescriptor(contexts: Seq[(String, String)])(eServiceId: String, descriptorId: String): Future[Unit]
  def suspendDescriptor(contexts: Seq[(String, String)])(eServiceId: String, descriptorId: String): Future[Unit]
  def hasNotDraftDescriptor(eService: EService): Future[Boolean]
  def updateDraftDescriptor(
    contexts: Seq[(String, String)]
  )(eServiceId: String, descriptorId: String, seed: UpdateEServiceDescriptorSeed): Future[EService]

  def createEServiceDocument(contexts: Seq[(String, String)])(
    eServiceId: String,
    descriptorId: String,
    kind: String,
    description: String,
    doc: (FileInfo, File)
  ): Future[EService]

  def getEServiceDocument(
    contexts: Seq[(String, String)]
  )(eServiceId: String, descriptorId: String, documentId: String): Future[EServiceDoc]

  def updateEServiceDocument(contexts: Seq[(String, String)])(
    eServiceId: String,
    descriptorId: String,
    documentId: String,
    updateEServiceDescriptorDocumentSeed: UpdateEServiceDescriptorDocumentSeed
  ): Future[EServiceDoc]

  def deleteEServiceDocument(
    contexts: Seq[(String, String)]
  )(eServiceId: String, descriptorId: String, documentId: String): Future[Unit]
}
