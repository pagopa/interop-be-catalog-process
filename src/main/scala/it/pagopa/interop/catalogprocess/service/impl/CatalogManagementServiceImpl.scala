package it.pagopa.interop.catalogprocess.service.impl

import akka.http.scaladsl.server.directives.FileInfo
import it.pagopa.interop.catalogmanagement.client
import it.pagopa.interop.catalogmanagement.client.api.EServiceApi
import it.pagopa.interop.catalogmanagement.client.invoker.{ApiRequest, BearerToken}
import it.pagopa.interop.catalogmanagement.client.model._
import it.pagopa.interop.catalogprocess.common.system._
import it.pagopa.interop.catalogprocess.errors.ForbiddenOperation
import it.pagopa.interop.catalogprocess.service.{CatalogManagementInvoker, CatalogManagementService}
import it.pagopa.interop.commons.utils.TypeConversions.{EitherOps, StringOps}
import it.pagopa.interop.commons.utils.extractHeaders
import org.slf4j.{Logger, LoggerFactory}

import java.io.File
import scala.concurrent.Future

final case class CatalogManagementServiceImpl(invoker: CatalogManagementInvoker, api: EServiceApi)
    extends CatalogManagementService {
  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def createEService(contexts: Seq[(String, String)])(eServiceSeed: EServiceSeed): Future[EService] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.createEService(xCorrelationId = correlationId, eServiceSeed, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      result <- invoker.invoke(request, s"E-Service created")
      _ = logger.info(s"E-Service created with id ${result.id}")
    } yield result
  }

  override def cloneEservice(
    contexts: Seq[(String, String)]
  )(eServiceId: String, descriptorId: String): Future[EService] = {
    for {
      eServiceUUID                     <- eServiceId.toFutureUUID
      descriptorUUID                   <- descriptorId.toFutureUUID
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request: ApiRequest[client.model.EService] = api.cloneEServiceByDescriptor(
        xCorrelationId = correlationId,
        eServiceId = eServiceUUID,
        descriptorId = descriptorUUID,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
      result <- invoker.invoke(request, s"E-Service cloned")
      _ = logger.info(s"E-Service cloned with id ${result.id}")
    } yield result
  }

  override def deleteDraft(contexts: Seq[(String, String)])(eServiceId: String, descriptorId: String): Future[Unit] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.deleteDraft(xCorrelationId = correlationId, eServiceId, descriptorId, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      result <- invoker.invoke(
        request,
        s"Draft E-Service deleted. E-Service Id: $eServiceId Descriptor Id: $descriptorId"
      )
    } yield result
  }

  override def deleteEService(contexts: Seq[(String, String)])(eServiceId: String): Future[Unit] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.deleteEService(xCorrelationId = correlationId, eServiceId, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      result <- invoker.invoke(request, s"E-Service deleted. E-Service Id: $eServiceId")
    } yield result
  }

  override def listEServices(
    contexts: Seq[(String, String)]
  )(producerId: Option[String], state: Option[EServiceDescriptorState]): Future[Seq[EService]] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.getEServices(
        xCorrelationId = correlationId,
        producerId = producerId,
        state = state,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
      result <- invoker.invoke(
        request,
        s"E-Services list retrieved for filters: producerId = $producerId,  status = $state"
      )
    } yield result
  }

  override def getEService(contexts: Seq[(String, String)])(eServiceId: String): Future[EService] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.getEService(xCorrelationId = correlationId, eServiceId, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      result <- invoker.invoke(request, s"E-Service with id $eServiceId retrieved")
    } yield result
  }

  override def updateDraftDescriptor(
    contexts: Seq[(String, String)]
  )(eServiceId: String, descriptorId: String, seed: UpdateEServiceDescriptorSeed): Future[EService] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.updateDescriptor(
        xCorrelationId = correlationId,
        eServiceId,
        descriptorId,
        seed,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
      result <- invoker.invoke(request, s"Descriptor $descriptorId updated for E-Services $eServiceId")
    } yield result
  }

  override def updateEservice(
    contexts: Seq[(String, String)]
  )(eServiceId: String, updateEServiceSeed: UpdateEServiceSeed): Future[EService] = {
    for {
      eservice          <- getEService(contexts)(eServiceId)
      updatableEService <- updatableEservice(eservice)
      updatedService    <- updateEServiceById(contexts)(updatableEService.id.toString, updateEServiceSeed)
    } yield updatedService
  }

  private def updateEServiceById(
    contexts: Seq[(String, String)]
  )(eServiceId: String, updateEServiceSeed: UpdateEServiceSeed): Future[EService] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.updateEServiceById(
        xCorrelationId = correlationId,
        eServiceId,
        updateEServiceSeed,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
      result <- invoker.invoke(request, s"E-Service $eServiceId updated.")
    } yield result
  }

  def deprecateDescriptor(contexts: Seq[(String, String)])(eServiceId: String, descriptorId: String): Future[Unit] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.deprecateDescriptor(xCorrelationId = correlationId, eServiceId, descriptorId, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      result <- invoker.invoke(request, s"Eservice $eServiceId descriptor $descriptorId has been deprecated")
    } yield result
  }

  def archiveDescriptor(contexts: Seq[(String, String)])(eServiceId: String, descriptorId: String): Future[Unit] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.archiveDescriptor(xCorrelationId = correlationId, eServiceId, descriptorId, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      result <- invoker.invoke(request, s"Eservice $eServiceId descriptor $descriptorId has been archived")
    } yield result
  }

  def publishDescriptor(contexts: Seq[(String, String)])(eServiceId: String, descriptorId: String): Future[Unit] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.publishDescriptor(xCorrelationId = correlationId, eServiceId, descriptorId, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      result <- invoker.invoke(request, s"Eservice $eServiceId descriptor $descriptorId has been published")
    } yield result
  }

  def draftDescriptor(contexts: Seq[(String, String)])(eServiceId: String, descriptorId: String): Future[Unit] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.draftDescriptor(xCorrelationId = correlationId, eServiceId, descriptorId, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      result <- invoker.invoke(request, s"Eservice $eServiceId descriptor $descriptorId has been moved to draft")
    } yield result
  }

  private def updatableEservice(eService: EService): Future[EService] = {
    Either
      .cond(
        eService.descriptors.length <= 1,
        eService,
        ForbiddenOperation(s"E-service ${eService.id} cannot be updated since it has more than one versions.")
      )
      .toFuture
  }

  override def hasNotDraftDescriptor(eService: EService): Future[Boolean] = {
    Either
      .cond(
        eService.descriptors.count(_.state == EServiceDescriptorState.DRAFT) < 1,
        true,
        ForbiddenOperation(s"E-service ${eService.id} already has a draft version.")
      )
      .toFuture
  }

  def createDescriptor(
    contexts: Seq[(String, String)]
  )(eServiceId: String, eServiceDescriptorSeed: EServiceDescriptorSeed): Future[EServiceDescriptor] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.createDescriptor(
        xCorrelationId = correlationId,
        eServiceId,
        eServiceDescriptorSeed,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
      result <- invoker.invoke(request, "Descriptor created")
    } yield result
  }

  override def createEServiceDocument(contexts: Seq[(String, String)])(
    eServiceId: String,
    descriptorId: String,
    kind: String,
    prettyName: String,
    doc: (FileInfo, File)
  ): Future[EService] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.createEServiceDocument(
        xCorrelationId = correlationId,
        eServiceId = eServiceId,
        descriptorId = descriptorId,
        kind = kind,
        prettyName = prettyName,
        doc = doc._2,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
      result <- invoker
        .invoke(
          request,
          s"Document with pretty name $prettyName created on Descriptor $descriptorId for E-Services $eServiceId"
        )
    } yield result
  }

  override def getEServiceDocument(
    contexts: Seq[(String, String)]
  )(eServiceId: String, descriptorId: String, documentId: String): Future[client.model.EServiceDoc] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.getEServiceDocument(
        xCorrelationId = correlationId,
        eServiceId,
        descriptorId,
        documentId,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
      result <- invoker.invoke(request, s"Document with id $documentId retrieval")
    } yield result

  }

  override def deleteEServiceDocument(
    contexts: Seq[(String, String)]
  )(eServiceId: String, descriptorId: String, documentId: String): Future[Unit] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.deleteEServiceDocument(
        xCorrelationId = correlationId,
        eServiceId,
        descriptorId,
        documentId,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
      result <- invoker.invoke(
        request,
        s"Document with id $documentId deleted on Descriptor $descriptorId for E-Services $eServiceId"
      )
    } yield result
  }

  override def updateEServiceDocument(contexts: Seq[(String, String)])(
    eServiceId: String,
    descriptorId: String,
    documentId: String,
    seed: UpdateEServiceDescriptorDocumentSeed
  ): Future[EServiceDoc] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.updateEServiceDocument(
        xCorrelationId = correlationId,
        eServiceId = eServiceId,
        descriptorId = descriptorId,
        documentId = documentId,
        updateEServiceDescriptorDocumentSeed = seed,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
      result <- invoker.invoke(
        request,
        s"Document with id $documentId updated on Descriptor $descriptorId for E-Services $eServiceId"
      )
    } yield result
  }

  override def suspendDescriptor(
    contexts: Seq[(String, String)]
  )(eServiceId: String, descriptorId: String): Future[Unit] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.suspendDescriptor(
        xCorrelationId = correlationId,
        eServiceId = eServiceId,
        descriptorId = descriptorId,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
      result <- invoker.invoke(request, s"Descriptor $descriptorId suspended for E-Services $eServiceId")
    } yield result
  }
}
