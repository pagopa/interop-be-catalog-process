package it.pagopa.pdnd.interop.uservice.catalogprocess.service.impl

import akka.http.scaladsl.server.directives.FileInfo
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.api.EServiceApi
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.invoker.{ApiRequest, BearerToken}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.UpdateEServiceDescriptorSeedEnums
import it.pagopa.pdnd.interop.uservice.catalogprocess.common.system._
import it.pagopa.pdnd.interop.uservice.catalogprocess.errors.ForbiddenOperation
import it.pagopa.pdnd.interop.uservice.catalogprocess.service.{CatalogManagementInvoker, CatalogManagementService}
import it.pagopa.pdnd.interop.uservice.catalogprocess.model._
import org.slf4j.{Logger, LoggerFactory}

import java.io.File
import scala.concurrent.Future

@SuppressWarnings(
  Array(
    "org.wartremover.warts.ImplicitParameter",
    "org.wartremover.warts.StringPlusAny",
    "org.wartremover.warts.Any",
    "org.wartremover.warts.Nothing",
    "org.wartremover.warts.Equals"
  )
)
final case class CatalogManagementServiceImpl(invoker: CatalogManagementInvoker, api: EServiceApi)
    extends CatalogManagementService {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def createEService(bearerToken: String)(eServiceSeed: EServiceSeed): Future[EService] = {
    for {
      clientSeed <- Future.fromTry(eServiceSeedToCatalogClientSeed(eServiceSeed).toTry)
      request: ApiRequest[client.model.EService] = api.createEService(clientSeed)(BearerToken(bearerToken))
      result <- invoker
        .execute[client.model.EService](request)
        .map { result =>
          logger.info(s"E-Service created with id ${result.content.id.toString}")
          result.content
        }
        .recoverWith { case ex =>
          logger.error(s"Error while creating E-Service ${ex.getMessage}")
          Future.failed[client.model.EService](ex)
        }
        .map(eServiceFromCatalogClient)
    } yield result

  }

  override def deleteDraft(bearerToken: String)(eServiceId: String, descriptorId: String): Future[Unit] = {
    val request: ApiRequest[Unit] = api.deleteDraft(eServiceId, descriptorId)(BearerToken(bearerToken))
    invoker
      .execute[Unit](request)
      .map { result =>
        logger.info(s"Draft E-Service deleted. E-Service Id: $eServiceId Descriptor Id: $descriptorId")
        result.content
      }
      .recoverWith { case ex =>
        logger.error(
          s"Error while deleting E-Service with Id $eServiceId and descriptor Id $descriptorId. Error: ${ex.getMessage}"
        )
        Future.failed[Unit](ex)
      }
  }

  override def listEServices(
    bearerToken: String
  )(producerId: Option[String], status: Option[String]): Future[Seq[EService]] = {
    val request: ApiRequest[Seq[client.model.EService]] =
      api.getEServices(producerId = producerId, status = status)(BearerToken(bearerToken))
    invoker
      .execute[Seq[client.model.EService]](request)
      .map { result =>
        logger.info(s"E-Services list retrieved for filters: producerId = $producerId,  status = $status")
        result.content
      }
      .recoverWith { case ex =>
        logger.error(s"Error while retrieving E-Services for filters: producerId = $producerId, status = $status")
        Future.failed[Seq[client.model.EService]](ex)
      }
      .map(_.map(eServiceFromCatalogClient))
  }

  override def getEService(bearerToken: String)(eServiceId: String): Future[EService] = {
    val request: ApiRequest[client.model.EService] = api.getEService(eServiceId)(BearerToken(bearerToken))
    invoker
      .execute[client.model.EService](request)
      .map { result =>
        logger.info(s"E-Service with id $eServiceId retrieved")
        result.content
      }
      .recoverWith { case ex =>
        logger.error(s"Error while retrieving E-Service with id $eServiceId")
        Future.failed[client.model.EService](ex)
      }
      .map(eServiceFromCatalogClient)
  }

  override def updateDraftDescriptor(
    bearerToken: String
  )(eServiceId: String, descriptorId: String, seed: UpdateEServiceDescriptorSeed): Future[EService] = {

    val clientSeed = client.model.UpdateEServiceDescriptorSeed(
      description = seed.description,
      audience = seed.audience,
      voucherLifespan = seed.voucherLifespan,
      status = UpdateEServiceDescriptorSeedEnums.Status.Draft
    )

    val request: ApiRequest[client.model.EService] =
      api.updateDescriptor(eServiceId, descriptorId, clientSeed)(BearerToken(bearerToken))
    invoker
      .execute[client.model.EService](request)
      .map { result =>
        logger.info(s"Descriptor $descriptorId updated for E-Services $eServiceId")
        result.content
      }
      .recoverWith { case ex =>
        logger.error(s"Error while updating descriptor $descriptorId for E-Services $eServiceId")
        Future.failed[client.model.EService](ex)
      }
      .map(eServiceFromCatalogClient)
  }

  override def updateEservice(
    bearerToken: String
  )(eServiceId: String, updateEServiceSeed: UpdateEServiceSeed): Future[EService] = {
    for {
      eservice          <- this.getEService(bearerToken)(eServiceId)
      updatableEService <- this.updatableEservice(eservice)
      updatedService    <- this.updateEServiceById(bearerToken)(updatableEService.id.toString, updateEServiceSeed)
    } yield updatedService
  }

  private def updateEServiceById(
    bearerToken: String
  )(eServiceId: String, updateEServiceSeed: UpdateEServiceSeed): Future[EService] = {
    for {
      clientSeed <- Future.fromTry(updateEServiceSeedToCatalogClientSeed(updateEServiceSeed).toTry)
      request: ApiRequest[client.model.EService] = api.updateEServiceById(eServiceId, clientSeed)(
        BearerToken(bearerToken)
      )
      result <- invoker
        .execute[client.model.EService](request)
        .map { result =>
          logger.info(s"E-Service $eServiceId updated.")
          result.content
        }
        .recoverWith { case ex =>
          logger.error(s"Error while updating E-Service $eServiceId: ${ex.getMessage}")
          Future.failed[client.model.EService](ex)
        }
        .map(eServiceFromCatalogClient)
    } yield result
  }

  def deprecateDescriptor(bearerToken: String)(eServiceId: String, descriptorId: String): Future[Unit] = {
    val request: ApiRequest[Unit] = api.deprecateDescriptor(eServiceId, descriptorId)(BearerToken(bearerToken))
    invoker
      .execute[Unit](request)
      .map { result =>
        logger.info(s"Eservice $eServiceId descriptor $descriptorId has been deprecated")
        result.content
      }
      .recoverWith { case ex =>
        logger.error(s"Error while $eServiceId deprecating $descriptorId")
        Future.failed[Unit](ex)
      }
  }

  def archiveDescriptor(bearerToken: String)(eServiceId: String, descriptorId: String): Future[Unit] = {
    val request: ApiRequest[Unit] = api.archiveDescriptor(eServiceId, descriptorId)(BearerToken(bearerToken))
    invoker
      .execute[Unit](request)
      .map { result =>
        logger.info(s"Eservice $eServiceId descriptor $descriptorId has been archived")
        result.content
      }
      .recoverWith { case ex =>
        logger.error(s"Error while $eServiceId archiving $descriptorId")
        Future.failed[Unit](ex)
      }
  }

  def publishDescriptor(bearerToken: String)(eServiceId: String, descriptorId: String): Future[Unit] = {
    val request: ApiRequest[Unit] = api.publishDescriptor(eServiceId, descriptorId)(BearerToken(bearerToken))
    invoker
      .execute[Unit](request)
      .map { result =>
        logger.info(s"Eservice $eServiceId descriptor $descriptorId has been published")
        result.content
      }
      .recoverWith { case ex =>
        logger.error(s"Error while $eServiceId publishing $descriptorId")
        Future.failed[Unit](ex)
      }
  }

  def draftDescriptor(bearerToken: String)(eServiceId: String, descriptorId: String): Future[Unit] = {
    val request: ApiRequest[Unit] = api.draftDescriptor(eServiceId, descriptorId)(BearerToken(bearerToken))
    invoker
      .execute[Unit](request)
      .map { result =>
        logger.info(s"Eservice $eServiceId descriptor $descriptorId has been moved to draft")
        result.content
      }
      .recoverWith { case ex =>
        logger.error(s"Error while $eServiceId moving $descriptorId to draft")
        Future.failed[Unit](ex)
      }
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
        eService.descriptors.count(_.status == "draft") < 1,
        true,
        ForbiddenOperation(s"E-service ${eService.id} already has a draft version.")
      )
      .toFuture
  }

  def createDescriptor(
    bearerToken: String
  )(eServiceId: String, eServiceDescriptorSeed: EServiceDescriptorSeed): Future[EServiceDescriptor] = {
    for {
      clientSeed <- Future.fromTry(eServiceDescriptorSeedToCatalogClientSeed(eServiceDescriptorSeed).toTry)
      request: ApiRequest[client.model.EServiceDescriptor] = api.createDescriptor(eServiceId, clientSeed)(
        BearerToken(bearerToken)
      )
      result <- invoker
        .execute[client.model.EServiceDescriptor](request)
        .map { result =>
          logger.info(s"Descriptor created with id ${result.content.id.toString}")
          result.content
        }
        .recoverWith { case ex =>
          logger.error(s"Error while creating Descrriptor ${ex.getMessage}")
          Future.failed[client.model.EServiceDescriptor](ex)
        }
        .map(descriptorFromCatalogClientDescriptor)
    } yield result
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
      .execute[client.model.EService](request)
      .map { result =>
        logger.info(
          s"Document with description $description created on Descriptor $descriptorId for E-Services $eServiceId"
        )
        result.content
      }
      .recoverWith { case ex =>
        logger.error(
          s"Error while creating document with description $description created on Descriptor $descriptorId for E-Services $eServiceId"
        )
        Future.failed[client.model.EService](ex)
      }
      .map(eServiceFromCatalogClient)
  }

  override def getEServiceDocument(
    bearerToken: String
  )(eServiceId: String, descriptorId: String, documentId: String): Future[File] = {
    val request: ApiRequest[File] =
      api.getEServiceDocument(eServiceId, descriptorId, documentId)(BearerToken(bearerToken))
    invoker
      .execute[File](request)
      .map { result =>
        logger.info(s"Document with id $documentId retrieved")
        result.content
      }
      .recoverWith { case ex =>
        logger.error(s"Error while retrieving document with id $eServiceId")
        Future.failed[File](ex)
      }
  }

  override def deleteEServiceDocument(
    bearerToken: String
  )(eServiceId: String, descriptorId: String, documentId: String): Future[Unit] = {
    val request: ApiRequest[Unit] =
      api.deleteEServiceDocument(eServiceId = eServiceId, descriptorId = descriptorId, documentId = documentId)(
        BearerToken(bearerToken)
      )
    invoker
      .execute[Unit](request)
      .map { result =>
        logger.info(s"Document with id $documentId deleted on Descriptor $descriptorId for E-Services $eServiceId")
        result.content
      }
      .recoverWith { case ex =>
        logger.error(
          s"Error while DELETING Document with id $documentId deleted on Descriptor $descriptorId for E-Services $eServiceId"
        )
        Future.failed[Unit](ex)
      }
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
        updateEServiceDescriptorDocumentSeed = updateEServiceDescriptorDocumentSeedToCatalogClientSeed(seed)
      )(BearerToken(bearerToken))
    invoker
      .execute[client.model.EServiceDoc](request)
      .map { result =>
        logger.info(s"Document with id $documentId updated on Descriptor $descriptorId for E-Services $eServiceId")
        result.content
      }
      .recoverWith { case ex =>
        logger.error(
          s"Error while UPDATING Document with id $documentId deleted on Descriptor $descriptorId for E-Services $eServiceId"
        )
        Future.failed[client.model.EServiceDoc](ex)
      }
      .map(docFromCatalogClientDoc)
  }
}
