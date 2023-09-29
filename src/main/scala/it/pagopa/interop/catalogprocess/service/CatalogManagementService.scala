package it.pagopa.interop.catalogprocess.service

import it.pagopa.interop.catalogmanagement.client.model._
import it.pagopa.interop.catalogmanagement.model.{CatalogDocument, CatalogItem, CatalogDescriptorState}
import it.pagopa.interop.catalogprocess.common.readmodel.{PaginatedResult, Consumers}
import it.pagopa.interop.commons.cqrs.service.ReadModelService

import java.util.UUID
import scala.concurrent.{Future, ExecutionContext}

trait CatalogManagementService {
  def createEService(eServiceSeed: EServiceSeed)(implicit contexts: Seq[(String, String)]): Future[EService]
  def getEServiceById(eServiceId: UUID)(implicit ec: ExecutionContext, readModel: ReadModelService): Future[CatalogItem]

  def deleteDraft(eServiceId: String, descriptorId: String)(implicit contexts: Seq[(String, String)]): Future[Unit]
  def updateEServiceById(eServiceId: String, updateEServiceSeed: UpdateEServiceSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[EService]
  def cloneEService(eServiceId: UUID, descriptorId: UUID)(implicit contexts: Seq[(String, String)]): Future[EService]
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
  def updateDraftDescriptor(eServiceId: String, descriptorId: String, seed: UpdateEServiceDescriptorSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[EService]

  def createEServiceDocument(eServiceId: UUID, descriptorId: UUID, documentSeed: CreateEServiceDescriptorDocumentSeed)(
    implicit contexts: Seq[(String, String)]
  ): Future[EService]
  def getEServiceDocument(eServiceId: UUID, descriptorId: UUID, documentId: UUID)(implicit
    ec: ExecutionContext,
    readModel: ReadModelService
  ): Future[CatalogDocument]
  def updateEServiceDocument(
    eServiceId: String,
    descriptorId: String,
    documentId: String,
    updateEServiceDescriptorDocumentSeed: UpdateEServiceDescriptorDocumentSeed
  )(implicit contexts: Seq[(String, String)]): Future[EServiceDoc]

  def deleteEServiceDocument(eServiceId: String, descriptorId: String, documentId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit]

  def getConsumers(eServiceId: UUID, offset: Int, limit: Int)(implicit
    ec: ExecutionContext,
    readModel: ReadModelService
  ): Future[PaginatedResult[Consumers]]

  def getEServices(
    name: Option[String],
    eServicesIds: Seq[UUID],
    producersIds: Seq[UUID],
    attributesIds: Seq[UUID],
    states: Seq[CatalogDescriptorState],
    offset: Int,
    limit: Int,
    exactMatchOnName: Boolean = false
  )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[PaginatedResult[CatalogItem]]

  def createRiskAnalysis(eServiceId: UUID, riskAnalysisSeed: RiskAnalysisSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit]

  def updateRiskAnalysis(eServiceId: UUID, riskAnalysisId: UUID, riskAnalysisSeed: RiskAnalysisSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[EServiceRiskAnalysis]

  def deleteRiskAnalysis(eServiceId: UUID, riskAnalysisId: UUID)(implicit contexts: Seq[(String, String)]): Future[Unit]
}
