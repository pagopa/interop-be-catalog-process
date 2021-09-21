package it.pagopa.pdnd.interop.uservice.catalogprocess.service

import akka.http.scaladsl.server.directives.FileInfo
import it.pagopa.pdnd.interop.uservice.catalogprocess.model.UpdateDescriptorSeed
import it.pagopa.pdnd.interopuservice.catalogprocess.model.{EService, EServiceSeed}

import java.io.File
import scala.concurrent.Future

trait CatalogManagementService {
  def listEServices(bearerToken: String, producerId: Option[String], status: Option[String]): Future[Seq[EService]]
  def getEService(bearerToken: String, eServiceId: String): Future[EService]
  def createEService(bearerToken: String, eServiceSeed: EServiceSeed): Future[EService]
  def deleteDraft(bearerToken: String, eServiceId: String, descriptorId: String): Future[Unit]

  def updateDescriptor(
    bearerToken: String,
    eServiceId: String,
    descriptorId: String,
    seed: UpdateDescriptorSeed
  ): Future[EService]

  def createEServiceDocument(
    bearerToken: String,
    eServiceId: String,
    descriptorId: String,
    kind: String,
    description: String,
    doc: (FileInfo, File)
  ): Future[EService]

  def getEServiceDocument(
    bearerToken: String,
    eServiceId: String,
    descriptorId: String,
    documentId: String
  ): Future[File]

}
