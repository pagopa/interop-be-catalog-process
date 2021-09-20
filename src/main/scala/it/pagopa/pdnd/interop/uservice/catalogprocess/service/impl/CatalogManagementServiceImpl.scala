package it.pagopa.pdnd.interop.uservice.catalogprocess.service.impl

import akka.http.scaladsl.server.directives.FileInfo
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.api.EServiceApi
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.invoker.{ApiRequest, BearerToken}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.{UpdateEServiceDescriptorSeedEnums}
import it.pagopa.pdnd.interop.uservice.catalogprocess.common.system._
import it.pagopa.pdnd.interop.uservice.catalogprocess.errors.ForbiddenOperation
import it.pagopa.pdnd.interop.uservice.catalogprocess.service.{CatalogManagementInvoker, CatalogManagementService}
import it.pagopa.pdnd.interopuservice.catalogprocess.model._
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

  override def createEService(bearerToken: BearerToken, eServiceSeed: EServiceSeed): Future[EService] = {
    for {
      clientSeed <- Future.fromTry(eServiceSeedToCatalogClientSeed(eServiceSeed).toTry)
      request: ApiRequest[client.model.EService] = api.createEService(clientSeed)(bearerToken)
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

  override def deleteDraft(bearerToken: BearerToken, eServiceId: String, descriptorId: String): Future[Unit] = {
    val request: ApiRequest[Unit] = api.deleteDraft(eServiceId, descriptorId)(bearerToken)
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
    bearerToken: BearerToken,
    producerId: Option[String],
    consumerId: Option[String],
    status: Option[String]
  ): Future[Seq[EService]] = {
    val request: ApiRequest[Seq[client.model.EService]] = api.getEServices(producerId, consumerId, status)(bearerToken)
    invoker
      .execute[Seq[client.model.EService]](request)
      .map { result =>
        logger.info(
          s"E-Services list retrieved for filters: producerId = $producerId, consumerId = $consumerId, status = $status"
        )
        result.content
      }
      .recoverWith { case ex =>
        logger.error(
          s"Error while retrieving E-Services for filters: producerId = $producerId, consumerId = $consumerId, status = $status"
        )
        Future.failed[Seq[client.model.EService]](ex)
      }
      .map(_.map(eServiceFromCatalogClient))
  }

  override def getEService(bearerToken: BearerToken, eServiceId: String): Future[EService] = {
    val request: ApiRequest[client.model.EService] = api.getEService(eServiceId)(bearerToken)
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
    bearerToken: BearerToken
  )(eServiceId: String, descriptorId: String, seed: UpdateEServiceDescriptorSeed): Future[EService] = {

    val clientSeed = client.model.UpdateEServiceDescriptorSeed(
      description = seed.description,
      audience = seed.audience,
      voucherLifespan = seed.voucherLifespan,
      status = UpdateEServiceDescriptorSeedEnums.Status.Draft
    )

    val request: ApiRequest[client.model.EService] =
      api.updateDescriptor(eServiceId, descriptorId, clientSeed)(bearerToken)
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

  override def createEServiceDocument(
    bearerToken: BearerToken,
    eServiceId: String,
    descriptorId: String,
    kind: String,
    description: String,
    doc: (FileInfo, File)
  ): Future[EService] = {
    val request: ApiRequest[client.model.EService] =
      api.createEServiceDocument(eServiceId, descriptorId, kind, description, doc._2)(bearerToken)
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
    bearerToken: BearerToken,
    eServiceId: String,
    descriptorId: String,
    documentId: String
  ): Future[File] = {
    val request: ApiRequest[File] = api.getEServiceDocument(eServiceId, descriptorId, documentId)(bearerToken)
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

  override def updateEservice(
    bearer: BearerToken
  )(eServiceId: String, updateEServiceSeed: UpdateEServiceSeed): Future[EService] = {
    for {
      eservice          <- getEService(bearer, eServiceId)
      updatableEService <- updatableEservice(eservice)
      updatedService    <- updateEServiceById(bearer)(updatableEService.id.toString, updateEServiceSeed)
    } yield updatedService
  }

  private def updateEServiceById(
    bearerToken: BearerToken
  )(eServiceId: String, updateEServiceSeed: UpdateEServiceSeed): Future[EService] = {
    for {
      clientSeed <- Future.fromTry(updateEServiceSeedToCatalogClientSeed(updateEServiceSeed).toTry)
      request: ApiRequest[client.model.EService] = api.updateEServiceById(eServiceId, clientSeed)(bearerToken)
      result <- invoker
        .execute[client.model.EService](request)
        .map { result =>
          logger.info(s"E-Service ${eServiceId} updated.")
          result.content
        }
        .recoverWith { case ex =>
          logger.error(s"Error while updating E-Service ${eServiceId}: ${ex.getMessage}")
          Future.failed[client.model.EService](ex)
        }
        .map(eServiceFromCatalogClient)
    } yield result
  }

  def deprecateDescriptor(bearerToken: BearerToken)(eServiceId: String, descriptorId: String): Future[Unit] = {
    val request: ApiRequest[Unit] = api.deprecateDescriptor(eServiceId, descriptorId)(bearerToken)
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

  def archiveDescriptor(bearerToken: BearerToken)(eServiceId: String, descriptorId: String): Future[Unit] = {
    val request: ApiRequest[Unit] = api.archiveDescriptor(eServiceId, descriptorId)(bearerToken)
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

  def publishDescriptor(bearerToken: BearerToken)(eServiceId: String, descriptorId: String): Future[Unit] = {
    val request: ApiRequest[Unit] = api.publishDescriptor(eServiceId, descriptorId)(bearerToken)
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

  def draftDescriptor(bearerToken: BearerToken)(eServiceId: String, descriptorId: String): Future[Unit] = {
    val request: ApiRequest[Unit] = api.draftDescriptor(eServiceId, descriptorId)(bearerToken)
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
        eService.descriptors.length == 1,
        eService,
        ForbiddenOperation(s"E-service ${eService.id} cannot be updated since it has more than one versions.")
      )
      .toFuture
  }

  override def hasNotDraftDescriptor(eService: EService): Future[Boolean] = {
    Either
      .cond(
        eService.descriptors.filter(_.status == "draft").length < 1,
        true,
        ForbiddenOperation(s"E-service ${eService.id} already has a draft version.")
      )
      .toFuture
  }

  def createDescriptor(
    bearer: BearerToken
  )(eServiceId: String, eServiceDescriptorSeed: EServiceDescriptorSeed): Future[EServiceDescriptor] = {
    for {
      clientSeed <- Future.fromTry(eServiceDescriptorSeedToCatalogClientSeed(eServiceDescriptorSeed).toTry)
      request: ApiRequest[client.model.EServiceDescriptor] = api.createDescriptor(eServiceId, clientSeed)(bearer)
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

  override def deleteEServiceDocument(
    bearer: BearerToken
  )(eServiceId: String, descriptorId: String, documentId: String): Future[Unit] = {
    val request: ApiRequest[Unit] =
      api.deleteEServiceDocument(eServiceId = eServiceId, descriptorId = descriptorId, documentId = documentId)(bearer)
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

  override def updateEServiceDocument(bearer: BearerToken)(
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
      )(bearer)
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
