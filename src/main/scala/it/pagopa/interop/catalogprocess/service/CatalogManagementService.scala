package it.pagopa.interop.catalogprocess.service

import akka.http.scaladsl.server.directives.FileInfo
import it.pagopa.interop.catalogmanagement.client.model._

import java.io.File
import scala.concurrent.Future

trait CatalogManagementService {
  def listEServices(producerId: Option[String], status: Option[EServiceDescriptorState])(implicit
    contexts: Seq[(String, String)]
  ): Future[Seq[EService]]
  def getEService(eServiceId: String)(implicit contexts: Seq[(String, String)]): Future[EService]
  def createEService(eServiceSeed: EServiceSeed)(implicit contexts: Seq[(String, String)]): Future[EService]
  def deleteDraft(eServiceId: String, descriptorId: String)(implicit contexts: Seq[(String, String)]): Future[Unit]
  def updateEservice(eServiceId: String, updateEServiceSeed: UpdateEServiceSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[EService]
  def cloneEservice(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[EService]
  def deleteEService(eServiceId: String)(implicit contexts: Seq[(String, String)]): Future[Unit]

  def createDescriptor(eServiceId: String, eServiceDescriptorSeed: EServiceDescriptorSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[EServiceDescriptor]
  def deprecateDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit]
  def archiveDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit]
  def publishDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit]
  def draftDescriptor(eServiceId: String, descriptorId: String)(implicit contexts: Seq[(String, String)]): Future[Unit]
  def suspendDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit]
  def hasNotDraftDescriptor(eService: EService)(implicit contexts: Seq[(String, String)]): Future[Boolean]
  def updateDraftDescriptor(eServiceId: String, descriptorId: String, seed: UpdateEServiceDescriptorSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[EService]

  def createEServiceDocument(
    eServiceId: String,
    descriptorId: String,
    kind: String,
    prettyName: String,
    doc: (FileInfo, File)
  )(implicit contexts: Seq[(String, String)]): Future[EService]

  def getEServiceDocument(eServiceId: String, descriptorId: String, documentId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[EServiceDoc]

  def updateEServiceDocument(
    eServiceId: String,
    descriptorId: String,
    documentId: String,
    updateEServiceDescriptorDocumentSeed: UpdateEServiceDescriptorDocumentSeed
  )(implicit contexts: Seq[(String, String)]): Future[EServiceDoc]

  def deleteEServiceDocument(eServiceId: String, descriptorId: String, documentId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit]
}
