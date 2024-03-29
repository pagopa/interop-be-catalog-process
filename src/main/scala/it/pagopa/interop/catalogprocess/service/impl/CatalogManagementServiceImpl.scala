package it.pagopa.interop.catalogprocess.service.impl

import it.pagopa.interop.commons.jwt.{ADMIN_ROLE, API_ROLE}
import it.pagopa.interop.commons.utils.AkkaUtils._
import it.pagopa.interop.catalogmanagement.client.api.EServiceApi
import it.pagopa.interop.catalogprocess.common.readmodel.ReadModelCatalogQueries
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import it.pagopa.interop.catalogmanagement.model.{
  CatalogItem,
  CatalogDocument,
  CatalogDescriptor,
  CatalogDescriptorState,
  CatalogItemMode,
  Draft
}
import it.pagopa.interop.catalogprocess.common.readmodel.{PaginatedResult, Consumers}
import it.pagopa.interop.catalogmanagement.client.invoker.{ApiError, BearerToken}
import it.pagopa.interop.catalogmanagement.client.model._
import it.pagopa.interop.catalogprocess.service.{CatalogManagementInvoker, CatalogManagementService}
import it.pagopa.interop.commons.utils._
import it.pagopa.interop.commons.utils.TypeConversions._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.catalogprocess.errors.CatalogProcessErrors.{
  DescriptorDocumentNotFound,
  EServiceDescriptorNotFound,
  EServiceNotFound,
  EServiceRiskAnalysisNotFound
}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final case class CatalogManagementServiceImpl(invoker: CatalogManagementInvoker, api: EServiceApi)(implicit
  ec: ExecutionContext
) extends CatalogManagementService {
  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def createEService(eServiceSeed: EServiceSeed)(implicit contexts: Seq[(String, String)]): Future[EService] =
    withHeaders { (bearerToken, correlationId) =>
      val request =
        api.createEService(xCorrelationId = correlationId, eServiceSeed)(BearerToken(bearerToken))
      invoker.invoke(request, s"E-Service created")
    }

  override def cloneEService(eServiceId: UUID, descriptorId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[EService] = withHeaders { (bearerToken, correlationId) =>
    val request = api.cloneEServiceByDescriptor(
      xCorrelationId = correlationId,
      eServiceId = eServiceId,
      descriptorId = descriptorId
    )(BearerToken(bearerToken))

    invoker.invoke(request, s"E-Service cloned").recoverWith {
      case err: ApiError[_] if err.code == 404 =>
        Future.failed(EServiceDescriptorNotFound(eServiceId.toString, descriptorId.toString))
    }
  }

  override def deleteDraft(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = withHeaders { (bearerToken, correlationId) =>
    val request = api.deleteDraft(xCorrelationId = correlationId, eServiceId, descriptorId)(BearerToken(bearerToken))
    invoker
      .invoke(request, s"Draft E-Service deleted. E-Service Id: $eServiceId Descriptor Id: $descriptorId")
      .recoverWith {
        case err: ApiError[_] if err.code == 404 => Future.failed(EServiceDescriptorNotFound(eServiceId, descriptorId))
      }
  }

  override def deleteEService(eServiceId: String)(implicit contexts: Seq[(String, String)]): Future[Unit] =
    withHeaders { (bearerToken, correlationId) =>
      val request =
        api.deleteEService(xCorrelationId = correlationId, eServiceId)(BearerToken(bearerToken))
      invoker.invoke(request, s"E-Service $eServiceId deleted").recoverWith {
        case err: ApiError[_] if err.code == 404 =>
          Future.failed(EServiceNotFound(eServiceId))
      }
    }

  override def updateDescriptor(eServiceId: String, descriptorId: String, seed: UpdateEServiceDescriptorSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[EService] =
    withHeaders { (bearerToken, correlationId) =>
      val request =
        api.updateDescriptor(xCorrelationId = correlationId, eServiceId, descriptorId, seed)(BearerToken(bearerToken))
      invoker
        .invoke(request, s"Descriptor $descriptorId updated for E-Services $eServiceId")
        .recoverWith {
          case err: ApiError[_] if err.code == 404 =>
            Future.failed(EServiceDescriptorNotFound(eServiceId, descriptorId))
        }
    }

  override def updateEServiceById(eServiceId: String, updateEServiceSeed: UpdateEServiceSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[EService] = withHeaders { (bearerToken, correlationId) =>
    val request =
      api.updateEServiceById(xCorrelationId = correlationId, eServiceId, updateEServiceSeed)(BearerToken(bearerToken))
    invoker.invoke(request, s"E-Service $eServiceId updated.").recoverWith {
      case err: ApiError[_] if err.code == 404 => Future.failed(EServiceNotFound(eServiceId))
    }
  }

  def deprecateDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] =
    withHeaders { (bearerToken, correlationId) =>
      val request =
        api.deprecateDescriptor(xCorrelationId = correlationId, eServiceId, descriptorId)(BearerToken(bearerToken))
      invoker
        .invoke(request, s"Eservice $eServiceId descriptor $descriptorId has been deprecated")
        .recoverWith {
          case err: ApiError[_] if err.code == 404 =>
            Future.failed(EServiceDescriptorNotFound(eServiceId, descriptorId))
        }
    }

  def archiveDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = withHeaders { (bearerToken, correlationId) =>
    val request =
      api.archiveDescriptor(xCorrelationId = correlationId, eServiceId, descriptorId)(BearerToken(bearerToken))
    invoker
      .invoke(request, s"Eservice $eServiceId descriptor $descriptorId has been archived")
      .recoverWith {
        case err: ApiError[_] if err.code == 404 =>
          Future.failed(EServiceDescriptorNotFound(eServiceId, descriptorId))
      }
  }

  def publishDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = withHeaders { (bearerToken, correlationId) =>
    val request =
      api.publishDescriptor(xCorrelationId = correlationId, eServiceId, descriptorId)(BearerToken(bearerToken))
    invoker
      .invoke(request, s"Eservice $eServiceId descriptor $descriptorId has been published")
      .recoverWith {
        case err: ApiError[_] if err.code == 404 =>
          Future.failed(EServiceDescriptorNotFound(eServiceId, descriptorId))
      }
  }

  def draftDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = withHeaders { (bearerToken, correlationId) =>
    val request =
      api.draftDescriptor(xCorrelationId = correlationId, eServiceId, descriptorId)(BearerToken(bearerToken))
    invoker.invoke(request, s"Eservice $eServiceId descriptor $descriptorId has been moved to draft")
  }

  def createDescriptor(eServiceId: String, eServiceDescriptorSeed: EServiceDescriptorSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[EServiceDescriptor] = withHeaders { (bearerToken, correlationId) =>
    val request =
      api.createDescriptor(xCorrelationId = correlationId, eServiceId, eServiceDescriptorSeed)(BearerToken(bearerToken))
    invoker
      .invoke(request, "Descriptor created")
      .recoverWith {
        case err: ApiError[_] if err.code == 404 => Future.failed(EServiceNotFound(eServiceId))
      }
  }

  override def createEServiceDocument(
    eServiceId: UUID,
    descriptorId: UUID,
    documentSeed: CreateEServiceDescriptorDocumentSeed
  )(implicit contexts: Seq[(String, String)]): Future[EService] = withHeaders { (bearerToken, correlationId) =>
    val request = api.createEServiceDocument(
      xCorrelationId = correlationId,
      eServiceId = eServiceId,
      descriptorId = descriptorId,
      createEServiceDescriptorDocumentSeed = documentSeed
    )(BearerToken(bearerToken))
    invoker
      .invoke(
        request,
        s"Creating Document ${documentSeed.documentId.toString} of kind ${documentSeed.kind} ,name ${documentSeed.fileName}, path ${documentSeed.filePath} for EService $eServiceId and Descriptor $descriptorId"
      )
      .recoverWith {
        case err: ApiError[_] if err.code == 404 =>
          Future.failed(EServiceDescriptorNotFound(eServiceId.toString, descriptorId.toString))
      }
  }

  override def deleteEServiceDocument(eServiceId: String, descriptorId: String, documentId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = withHeaders { (bearerToken, correlationId) =>
    val request = api.deleteEServiceDocument(xCorrelationId = correlationId, eServiceId, descriptorId, documentId)(
      BearerToken(bearerToken)
    )
    invoker
      .invoke(request, s"Document with id $documentId deleted on Descriptor $descriptorId for E-Services $eServiceId")
      .recoverWith {
        case err: ApiError[_] if err.code == 404 =>
          Future.failed(DescriptorDocumentNotFound(eServiceId, descriptorId, documentId))
      }
  }

  override def updateEServiceDocument(
    eServiceId: String,
    descriptorId: String,
    documentId: String,
    seed: UpdateEServiceDescriptorDocumentSeed
  )(implicit contexts: Seq[(String, String)]): Future[EServiceDoc] =
    withHeaders { (bearerToken, correlationId) =>
      val request = api.updateEServiceDocument(
        xCorrelationId = correlationId,
        eServiceId = eServiceId,
        descriptorId = descriptorId,
        documentId = documentId,
        updateEServiceDescriptorDocumentSeed = seed
      )(BearerToken(bearerToken))
      invoker
        .invoke(request, s"Document with id $documentId updated on Descriptor $descriptorId for E-Services $eServiceId")
        .recoverWith {
          case err: ApiError[_] if err.code == 404 =>
            Future.failed(DescriptorDocumentNotFound(eServiceId, descriptorId, documentId))
        }
    }

  override def suspendDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = withHeaders { (bearerToken, correlationId) =>
    val request =
      api.suspendDescriptor(xCorrelationId = correlationId, eServiceId = eServiceId, descriptorId = descriptorId)(
        BearerToken(bearerToken)
      )
    invoker
      .invoke(request, s"Descriptor $descriptorId suspended for E-Services $eServiceId")
      .recoverWith {
        case err: ApiError[_] if err.code == 404 =>
          Future.failed(EServiceDescriptorNotFound(eServiceId, descriptorId))
      }
  }

  override def getEServiceById(
    eServiceId: UUID
  )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[CatalogItem] =
    ReadModelCatalogQueries.getEServiceById(eServiceId).flatMap(_.toFuture(EServiceNotFound(eServiceId.toString)))

  override def getEServiceDocument(eServiceId: UUID, descriptorId: UUID, documentId: UUID)(implicit
    ec: ExecutionContext,
    readModel: ReadModelService
  ): Future[(CatalogItem, CatalogDocument)] = for {
    catalogItem     <- ReadModelCatalogQueries
      .getEServiceDocument(eServiceId, descriptorId, documentId)
      .flatMap(_.toFuture(DescriptorDocumentNotFound(eServiceId.toString, descriptorId.toString, documentId.toString)))
    catalogDocument <- getDocument(catalogItem, descriptorId, documentId).toFuture(
      DescriptorDocumentNotFound(eServiceId.toString, descriptorId.toString, documentId.toString)
    )
  } yield (catalogItem, catalogDocument)

  override def getConsumers(eServiceId: UUID, offset: Int, limit: Int)(implicit
    ec: ExecutionContext,
    readModel: ReadModelService
  ): Future[PaginatedResult[Consumers]] = ReadModelCatalogQueries.getConsumers(eServiceId, offset, limit)

  override def getEServices(
    requesterId: UUID,
    name: Option[String],
    eServicesIds: Seq[UUID],
    producersIds: Seq[UUID],
    attributesIds: Seq[UUID],
    states: Seq[CatalogDescriptorState],
    mode: Option[CatalogItemMode],
    offset: Int,
    limit: Int,
    exactMatchOnName: Boolean = false
  )(implicit
    ec: ExecutionContext,
    readModel: ReadModelService,
    contexts: Seq[(String, String)]
  ): Future[PaginatedResult[CatalogItem]] = for {
    role <- getUserRolesFuture(contexts)
    roles        = Seq(ADMIN_ROLE, API_ROLE)
    isSuperVisor = roles.intersect(role.split(",").toList.map(_.trim())).nonEmpty
    eservices <- ReadModelCatalogQueries.getEServices(
      requesterId,
      name,
      eServicesIds,
      producersIds,
      attributesIds,
      states,
      mode,
      offset,
      limit,
      exactMatchOnName,
      isSuperVisor
    )
    filtered =
      if (!isSuperVisor)
        eservices.results.map(ese => ese.copy(descriptors = ese.descriptors.filterNot(_.state == Draft)))
      else
        eservices.results.map(ese =>
          if (ese.producerId != requesterId) ese.copy(descriptors = ese.descriptors.filterNot(_.state == Draft))
          else ese
        )
  } yield eservices.copy(results = filtered)

  override def createRiskAnalysis(eServiceId: UUID, riskAnalysisSeed: RiskAnalysisSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = withHeaders { (bearerToken, correlationId) =>
    val request = api.createRiskAnalysis(
      xCorrelationId = correlationId,
      eServiceId = eServiceId,
      riskAnalysisSeed = riskAnalysisSeed
    )(BearerToken(bearerToken))
    invoker
      .invoke(request, s"Create Risk Analysis for E-Services $eServiceId")
      .recoverWith {
        case err: ApiError[_] if err.code == 404 =>
          Future.failed(EServiceNotFound(eServiceId.toString))
      }
  }

  override def updateRiskAnalysis(eServiceId: UUID, riskAnalysisId: UUID, riskAnalysisSeed: RiskAnalysisSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[EServiceRiskAnalysis] = withHeaders { (bearerToken, correlationId) =>
    val request = api.updateRiskAnalysis(
      xCorrelationId = correlationId,
      eServiceId = eServiceId,
      riskAnalysisId = riskAnalysisId,
      riskAnalysisSeed = riskAnalysisSeed
    )(BearerToken(bearerToken))
    invoker
      .invoke(request, s"Update Risk Analysis $riskAnalysisId for E-Services $eServiceId")
      .recoverWith {
        case err: ApiError[_] if err.code == 404 =>
          Future.failed(EServiceRiskAnalysisNotFound(eServiceId, riskAnalysisId))
      }
  }

  override def deleteRiskAnalysis(eServiceId: UUID, riskAnalysisId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = withHeaders { (bearerToken, correlationId) =>
    val request =
      api.deleteRiskAnalysis(xCorrelationId = correlationId, eServiceId = eServiceId, riskAnalysisId = riskAnalysisId)(
        BearerToken(bearerToken)
      )
    invoker
      .invoke(request, s"Delete Risk Analysis $riskAnalysisId for E-Services $eServiceId")
      .recoverWith {
        case err: ApiError[_] if err.code == 404 =>
          Future.failed(EServiceRiskAnalysisNotFound(eServiceId, riskAnalysisId))
      }
  }

  private def getDocument(eService: CatalogItem, descriptorId: UUID, documentId: UUID): Option[CatalogDocument] = {

    def lookup(catalogDescriptor: CatalogDescriptor): Option[CatalogDocument]               = {
      val interface = catalogDescriptor.interface.fold(Seq.empty[CatalogDocument])(doc => Seq(doc))
      (interface ++: catalogDescriptor.docs).find(_.id == documentId)
    }
    def getDescriptor(eService: CatalogItem, descriptorId: UUID): Option[CatalogDescriptor] =
      eService.descriptors.find(_.id == descriptorId)

    for {
      descriptor <- getDescriptor(eService, descriptorId)
      document   <- lookup(descriptor)
    } yield document
  }
}
