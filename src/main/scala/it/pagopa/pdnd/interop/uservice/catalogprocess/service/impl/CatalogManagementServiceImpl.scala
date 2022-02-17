package it.pagopa.pdnd.interop.uservice.catalogprocess.service.impl

import akka.http.scaladsl.server.directives.FileInfo
import it.pagopa.pdnd.interop.commons.utils.TypeConversions.{EitherOps, StringOps}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.api.EServiceApi
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.invoker.{ApiRequest, BearerToken}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model._
import it.pagopa.pdnd.interop.uservice.catalogprocess.common.system._
import it.pagopa.pdnd.interop.uservice.catalogprocess.errors.ForbiddenOperation
import it.pagopa.pdnd.interop.uservice.catalogprocess.service.{CatalogManagementInvoker, CatalogManagementService}
import org.slf4j.{Logger, LoggerFactory}

import java.io.File
import scala.concurrent.Future

final case class CatalogManagementServiceImpl(invoker: CatalogManagementInvoker, api: EServiceApi)
    extends CatalogManagementService {
  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def createEService(bearerToken: String)(eServiceSeed: EServiceSeed): Future[EService] = {
    val request: ApiRequest[client.model.EService] = api.createEService(eServiceSeed)(BearerToken(bearerToken))
    for {
      result <- invoker.invoke(request, s"E-Service created")
      _ = logger.info(s"E-Service created with id ${result.id}")
    } yield result
  }

  override def cloneEservice(bearer: String)(eServiceId: String, descriptorId: String): Future[EService] = {
    for {
      eServiceUUID   <- eServiceId.toFutureUUID
      descriptorUUID <- descriptorId.toFutureUUID
      request: ApiRequest[client.model.EService] = api.cloneEServiceByDescriptor(
        eServiceId = eServiceUUID,
        descriptorId = descriptorUUID
      )(BearerToken(bearer))
      result <- invoker.invoke(request, s"E-Service cloned")
      _ = logger.info(s"E-Service cloned with id ${result.id}")
    } yield result
  }

  override def deleteDraft(bearerToken: String)(eServiceId: String, descriptorId: String): Future[Unit] = {
    val request: ApiRequest[Unit] = api.deleteDraft(eServiceId, descriptorId)(BearerToken(bearerToken))
    invoker.invoke(request, s"Draft E-Service deleted. E-Service Id: $eServiceId Descriptor Id: $descriptorId")
  }

  override def deleteEService(bearer: String)(eServiceId: String): Future[Unit] = {
    val request: ApiRequest[Unit] = api.deleteEService(eServiceId)(BearerToken(bearer))
    invoker.invoke(request, s"E-Service deleted. E-Service Id: $eServiceId")
  }

  override def listEServices(
    bearerToken: String
  )(producerId: Option[String], state: Option[EServiceDescriptorState]): Future[Seq[EService]] = {
    val request: ApiRequest[Seq[client.model.EService]] =
      api.getEServices(producerId = producerId, state = state)(BearerToken(bearerToken))
    invoker.invoke(request, s"E-Services list retrieved for filters: producerId = $producerId,  status = $state")

  }

  override def getEService(bearerToken: String)(eServiceId: String): Future[EService] = {
    val request: ApiRequest[client.model.EService] = api.getEService(eServiceId)(BearerToken(bearerToken))
    invoker.invoke(request, s"E-Service with id $eServiceId retrieved")
  }

  override def updateDraftDescriptor(
    bearerToken: String
  )(eServiceId: String, descriptorId: String, seed: UpdateEServiceDescriptorSeed): Future[EService] = {

    val request: ApiRequest[client.model.EService] =
      api.updateDescriptor(eServiceId, descriptorId, seed)(BearerToken(bearerToken))

    invoker.invoke(request, s"Descriptor $descriptorId updated for E-Services $eServiceId")
  }

  override def updateEservice(
    bearerToken: String
  )(eServiceId: String, updateEServiceSeed: UpdateEServiceSeed): Future[EService] = {
    for {
      eservice          <- getEService(bearerToken)(eServiceId)
      updatableEService <- updatableEservice(eservice)
      updatedService    <- updateEServiceById(bearerToken)(updatableEService.id.toString, updateEServiceSeed)
    } yield updatedService
  }

  private def updateEServiceById(
    bearerToken: String
  )(eServiceId: String, updateEServiceSeed: UpdateEServiceSeed): Future[EService] = {
    val request: ApiRequest[client.model.EService] =
      api.updateEServiceById(eServiceId, updateEServiceSeed)(BearerToken(bearerToken))
    invoker.invoke(request, s"E-Service $eServiceId updated.")
  }

  def deprecateDescriptor(bearerToken: String)(eServiceId: String, descriptorId: String): Future[Unit] = {
    val request: ApiRequest[Unit] = api.deprecateDescriptor(eServiceId, descriptorId)(BearerToken(bearerToken))
    invoker.invoke(request, s"Eservice $eServiceId descriptor $descriptorId has been deprecated")
  }

  def archiveDescriptor(bearerToken: String)(eServiceId: String, descriptorId: String): Future[Unit] = {
    val request: ApiRequest[Unit] = api.archiveDescriptor(eServiceId, descriptorId)(BearerToken(bearerToken))
    invoker.invoke(request, s"Eservice $eServiceId descriptor $descriptorId has been archived")
  }

  def publishDescriptor(bearerToken: String)(eServiceId: String, descriptorId: String): Future[Unit] = {
    val request: ApiRequest[Unit] = api.publishDescriptor(eServiceId, descriptorId)(BearerToken(bearerToken))
    invoker.invoke(request, s"Eservice $eServiceId descriptor $descriptorId has been published")
  }

  def draftDescriptor(bearerToken: String)(eServiceId: String, descriptorId: String): Future[Unit] = {
    val request: ApiRequest[Unit] = api.draftDescriptor(eServiceId, descriptorId)(BearerToken(bearerToken))
    invoker.invoke(request, s"Eservice $eServiceId descriptor $descriptorId has been moved to draft")
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
    bearerToken: String
  )(eServiceId: String, eServiceDescriptorSeed: EServiceDescriptorSeed): Future[EServiceDescriptor] = {
    val request: ApiRequest[client.model.EServiceDescriptor] =
      api.createDescriptor(eServiceId, eServiceDescriptorSeed)(BearerToken(bearerToken))
    invoker.invoke(request, "Descriptor created")
  }

  override def createEServiceDocument(bearerToken: String)(
    eServiceId: String,
    descriptorId: String,
    kind: String,
    description: String,
    doc: (FileInfo, File)
  ): Future[EService] = {
    val request: ApiRequest[client.model.EService] =
      api.createEServiceDocument(eServiceId, descriptorId, kind, description, doc._2)(BearerToken(bearerToken))
    invoker
      .invoke(
        request,
        s"Document with description $description created on Descriptor $descriptorId for E-Services $eServiceId"
      )

  }

  override def getEServiceDocument(
    bearerToken: String
  )(eServiceId: String, descriptorId: String, documentId: String): Future[client.model.EServiceDoc] = {
    val request: ApiRequest[client.model.EServiceDoc] =
      api.getEServiceDocument(eServiceId, descriptorId, documentId)(BearerToken(bearerToken))
    invoker.invoke(request, s"Document with id $documentId retrieval")
  }

  override def deleteEServiceDocument(
    bearerToken: String
  )(eServiceId: String, descriptorId: String, documentId: String): Future[Unit] = {
    val request: ApiRequest[Unit] =
      api.deleteEServiceDocument(eServiceId = eServiceId, descriptorId = descriptorId, documentId = documentId)(
        BearerToken(bearerToken)
      )
    invoker.invoke(
      request,
      s"Document with id $documentId deleted on Descriptor $descriptorId for E-Services $eServiceId"
    )
  }

  override def updateEServiceDocument(bearerToken: String)(
    eServiceId: String,
    descriptorId: String,
    documentId: String,
    seed: UpdateEServiceDescriptorDocumentSeed
  ): Future[EServiceDoc] = {

    val request: ApiRequest[client.model.EServiceDoc] =
      api.updateEServiceDocument(
        eServiceId = eServiceId,
        descriptorId = descriptorId,
        documentId = documentId,
        updateEServiceDescriptorDocumentSeed = seed
      )(BearerToken(bearerToken))
    invoker.invoke(
      request,
      s"Document with id $documentId updated on Descriptor $descriptorId for E-Services $eServiceId"
    )
  }

  override def suspendDescriptor(bearerToken: String)(eServiceId: String, descriptorId: String): Future[Unit] = {
    val request: ApiRequest[Unit] =
      api.suspendDescriptor(eServiceId = eServiceId, descriptorId = descriptorId)(BearerToken(bearerToken))
    invoker.invoke(request, s"Descriptor $descriptorId suspended for E-Services $eServiceId")

  }
}
