package it.pagopa.interop.catalogprocess.service.impl

import akka.http.scaladsl.server.directives.FileInfo
import it.pagopa.interop.catalogmanagement.client
import it.pagopa.interop.catalogmanagement.client.api.EServiceApi
import it.pagopa.interop.catalogmanagement.client.invoker.{ApiError, BearerToken}
import it.pagopa.interop.catalogmanagement.client.model._
import it.pagopa.interop.catalogprocess.service.{CatalogManagementInvoker, CatalogManagementService}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.catalogprocess.errors.CatalogProcessErrors.{
  DescriptorDocumentNotFound,
  DraftDescriptorAlreadyExists,
  EServiceDescriptorNotFound,
  EServiceNotFound
}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}

import java.io.File
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final case class CatalogManagementServiceImpl(invoker: CatalogManagementInvoker, api: EServiceApi)(implicit
  ec: ExecutionContext
) extends CatalogManagementService {
  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def createEService(eServiceSeed: EServiceSeed)(implicit contexts: Seq[(String, String)]): Future[EService] =
    withHeaders { (bearerToken, correlationId, ip) =>
      val request =
        api.createEService(xCorrelationId = correlationId, eServiceSeed, xForwardedFor = ip)(BearerToken(bearerToken))
      invoker.invoke(request, s"E-Service created")
    }

  override def cloneEService(eServiceId: UUID, descriptorId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[EService] = withHeaders { (bearerToken, correlationId, ip) =>
    val request = api.cloneEServiceByDescriptor(
      xCorrelationId = correlationId,
      eServiceId = eServiceId,
      descriptorId = descriptorId,
      xForwardedFor = ip
    )(BearerToken(bearerToken))

    invoker.invoke(request, s"E-Service cloned").recoverWith {
      case err: ApiError[_] if err.code == 404 =>
        Future.failed(EServiceDescriptorNotFound(eServiceId.toString, descriptorId.toString))
    }
  }

  override def deleteDraft(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = withHeaders { (bearerToken, correlationId, ip) =>
    val request = api.deleteDraft(xCorrelationId = correlationId, eServiceId, descriptorId, xForwardedFor = ip)(
      BearerToken(bearerToken)
    )
    invoker
      .invoke(request, s"Draft E-Service deleted. E-Service Id: $eServiceId Descriptor Id: $descriptorId")
      .recoverWith {
        case err: ApiError[_] if err.code == 404 => Future.failed(EServiceDescriptorNotFound(eServiceId, descriptorId))
      }
  }

  override def deleteEService(eServiceId: String)(implicit contexts: Seq[(String, String)]): Future[Unit] =
    withHeaders { (bearerToken, correlationId, ip) =>
      val request =
        api.deleteEService(xCorrelationId = correlationId, eServiceId, xForwardedFor = ip)(BearerToken(bearerToken))
      invoker.invoke(request, s"E-Service $eServiceId deleted").recoverWith {
        case err: ApiError[_] if err.code == 404 =>
          Future.failed(EServiceNotFound(eServiceId))
      }
    }

  override def listEServices(producerId: Option[String], state: Option[EServiceDescriptorState])(implicit
    contexts: Seq[(String, String)]
  ): Future[Seq[EService]] = withHeaders { (bearerToken, correlationId, ip) =>
    val request =
      api.getEServices(xCorrelationId = correlationId, producerId = producerId, state = state, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
    invoker.invoke(request, s"E-Services list retrieved for filters: producerId = $producerId,  status = $state")
  }

  override def getEService(eServiceId: String)(implicit contexts: Seq[(String, String)]): Future[EService] =
    withHeaders { (bearerToken, correlationId, ip) =>
      val request =
        api.getEService(xCorrelationId = correlationId, eServiceId, xForwardedFor = ip)(BearerToken(bearerToken))
      invoker.invoke(request, s"E-Service with id $eServiceId retrieved").recoverWith {
        case err: ApiError[_] if err.code == 404 => Future.failed(EServiceNotFound(eServiceId))
      }
    }

  override def updateDraftDescriptor(eServiceId: String, descriptorId: String, seed: UpdateEServiceDescriptorSeed)(
    implicit contexts: Seq[(String, String)]
  ): Future[EService] =
    withHeaders { (bearerToken, correlationId, ip) =>
      val request =
        api.updateDescriptor(xCorrelationId = correlationId, eServiceId, descriptorId, seed, xForwardedFor = ip)(
          BearerToken(bearerToken)
        )
      invoker
        .invoke(request, s"Descriptor $descriptorId updated for E-Services $eServiceId")
        .recoverWith {
          case err: ApiError[_] if err.code == 404 =>
            Future.failed(EServiceDescriptorNotFound(eServiceId, descriptorId))
        }
    }

  override def updateEServiceById(eServiceId: String, updateEServiceSeed: UpdateEServiceSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[EService] = withHeaders { (bearerToken, correlationId, ip) =>
    val request =
      api.updateEServiceById(xCorrelationId = correlationId, eServiceId, updateEServiceSeed, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
    invoker.invoke(request, s"E-Service $eServiceId updated.").recoverWith {
      case err: ApiError[_] if err.code == 404 => Future.failed(EServiceNotFound(eServiceId))
    }
  }

  def deprecateDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] =
    withHeaders { (bearerToken, correlationId, ip) =>
      val request =
        api.deprecateDescriptor(xCorrelationId = correlationId, eServiceId, descriptorId, xForwardedFor = ip)(
          BearerToken(bearerToken)
        )
      invoker
        .invoke(request, s"Eservice $eServiceId descriptor $descriptorId has been deprecated")
        .recoverWith {
          case err: ApiError[_] if err.code == 404 =>
            Future.failed(EServiceDescriptorNotFound(eServiceId, descriptorId))
        }
    }

  def archiveDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = withHeaders { (bearerToken, correlationId, ip) =>
    val request = api.archiveDescriptor(xCorrelationId = correlationId, eServiceId, descriptorId, xForwardedFor = ip)(
      BearerToken(bearerToken)
    )
    invoker
      .invoke(request, s"Eservice $eServiceId descriptor $descriptorId has been archived")
      .recoverWith {
        case err: ApiError[_] if err.code == 404 =>
          Future.failed(EServiceDescriptorNotFound(eServiceId, descriptorId))
      }
  }

  def publishDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = withHeaders { (bearerToken, correlationId, ip) =>
    val request = api.publishDescriptor(xCorrelationId = correlationId, eServiceId, descriptorId, xForwardedFor = ip)(
      BearerToken(bearerToken)
    )
    invoker
      .invoke(request, s"Eservice $eServiceId descriptor $descriptorId has been published")
      .recoverWith {
        case err: ApiError[_] if err.code == 404 =>
          Future.failed(EServiceDescriptorNotFound(eServiceId, descriptorId))
      }
  }

  def draftDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = withHeaders { (bearerToken, correlationId, ip) =>
    val request = api.draftDescriptor(xCorrelationId = correlationId, eServiceId, descriptorId, xForwardedFor = ip)(
      BearerToken(bearerToken)
    )
    invoker.invoke(request, s"Eservice $eServiceId descriptor $descriptorId has been moved to draft")
  }

  override def hasNotDraftDescriptor(eService: EService)(implicit contexts: Seq[(String, String)]): Future[Boolean] =
    Either
      .cond(
        eService.descriptors.count(_.state == EServiceDescriptorState.DRAFT) < 1,
        true,
        DraftDescriptorAlreadyExists(eService.id.toString)
      )
      .toFuture

  def createDescriptor(eServiceId: String, eServiceDescriptorSeed: EServiceDescriptorSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[EServiceDescriptor] = withHeaders { (bearerToken, correlationId, ip) =>
    val request =
      api.createDescriptor(xCorrelationId = correlationId, eServiceId, eServiceDescriptorSeed, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
    invoker
      .invoke(request, "Descriptor created")
      .recoverWith {
        case err: ApiError[_] if err.code == 404 => Future.failed(EServiceNotFound(eServiceId))
      }
  }

  override def createEServiceDocument(
    eServiceId: String,
    descriptorId: String,
    kind: String,
    prettyName: String,
    doc: (FileInfo, File)
  )(implicit contexts: Seq[(String, String)]): Future[EService] = withHeaders { (bearerToken, correlationId, ip) =>
    val request = api.createEServiceDocument(
      xCorrelationId = correlationId,
      eServiceId = eServiceId,
      descriptorId = descriptorId,
      kind = kind,
      prettyName = prettyName,
      doc = doc._2,
      xForwardedFor = ip
    )(BearerToken(bearerToken))
    invoker
      .invoke(
        request,
        s"Document with pretty name $prettyName created on Descriptor $descriptorId for E-Services $eServiceId"
      )
      .recoverWith {
        case err: ApiError[_] if err.code == 404 => Future.failed(EServiceDescriptorNotFound(eServiceId, descriptorId))
      }
  }

  override def getEServiceDocument(eServiceId: String, descriptorId: String, documentId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[client.model.EServiceDoc] = withHeaders { (bearerToken, correlationId, ip) =>
    val request =
      api.getEServiceDocument(xCorrelationId = correlationId, eServiceId, descriptorId, documentId, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
    invoker
      .invoke(request, s"Document with id $documentId retrieval")
      .recoverWith {
        case err: ApiError[_] if err.code == 404 =>
          Future.failed(DescriptorDocumentNotFound(eServiceId, descriptorId, documentId))
      }
  }

  override def deleteEServiceDocument(eServiceId: String, descriptorId: String, documentId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = withHeaders { (bearerToken, correlationId, ip) =>
    val request = api.deleteEServiceDocument(
      xCorrelationId = correlationId,
      eServiceId,
      descriptorId,
      documentId,
      xForwardedFor = ip
    )(BearerToken(bearerToken))
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
    withHeaders { (bearerToken, correlationId, ip) =>
      val request = api.updateEServiceDocument(
        xCorrelationId = correlationId,
        eServiceId = eServiceId,
        descriptorId = descriptorId,
        documentId = documentId,
        updateEServiceDescriptorDocumentSeed = seed,
        xForwardedFor = ip
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
  ): Future[Unit] = withHeaders { (bearerToken, correlationId, ip) =>
    val request = api.suspendDescriptor(
      xCorrelationId = correlationId,
      eServiceId = eServiceId,
      descriptorId = descriptorId,
      xForwardedFor = ip
    )(BearerToken(bearerToken))
    invoker
      .invoke(request, s"Descriptor $descriptorId suspended for E-Services $eServiceId")
      .recoverWith {
        case err: ApiError[_] if err.code == 404 =>
          Future.failed(EServiceDescriptorNotFound(eServiceId, descriptorId))
      }
  }

}
