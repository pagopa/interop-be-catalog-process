package it.pagopa.pdnd.interop.uservice.catalogprocess.service.impl

import akka.http.scaladsl.server.directives.FileInfo
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.api.EServiceApi
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.invoker.{ApiRequest, BearerToken}
import it.pagopa.pdnd.interop.uservice.catalogprocess.model.UpdateDescriptorSeed
import it.pagopa.pdnd.interop.uservice.catalogprocess.service.{CatalogManagementInvoker, CatalogManagementService}
import it.pagopa.pdnd.interopuservice.catalogprocess.model.{EService, EServiceSeed}
import org.slf4j.{Logger, LoggerFactory}

import java.io.File
import scala.concurrent.{ExecutionContext, Future}

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter", "org.wartremover.warts.StringPlusAny"))
final case class CatalogManagementServiceImpl(invoker: CatalogManagementInvoker, api: EServiceApi)(implicit
  ec: ExecutionContext
) extends CatalogManagementService {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def createEService(bearerToken: String, eServiceSeed: EServiceSeed): Future[EService] = {
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

  override def deleteDraft(bearerToken: String, eServiceId: String, descriptorId: String): Future[Unit] = {
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
    bearerToken: String,
    producerId: Option[String],
    status: Option[String]
  ): Future[Seq[EService]] = {
    val request: ApiRequest[Seq[client.model.EService]] =
      api.getEServices(producerId = producerId, status = status)(BearerToken(bearerToken))
    invoker
      .execute[Seq[client.model.EService]](request)
      .map { result =>
        logger.info(s"E-Services list retrieved for filters: producerId = $producerId, status = $status")
        result.content
      }
      .recoverWith { case ex =>
        logger.error(s"Error while retrieving E-Services for filters: producerId = $producerId, status = $status")
        Future.failed[Seq[client.model.EService]](ex)
      }
      .map(_.map(eServiceFromCatalogClient))
  }

  override def getEService(bearerToken: String, eServiceId: String): Future[EService] = {
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

  override def updateDescriptor(
    bearerToken: String,
    eServiceId: String,
    descriptorId: String,
    seed: UpdateDescriptorSeed
  ): Future[EService] = {
    val request: ApiRequest[client.model.EService] =
      api.updateDescriptor(eServiceId, descriptorId, seed.toApi())(BearerToken(bearerToken))
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
    bearerToken: String,
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
    bearerToken: String,
    eServiceId: String,
    descriptorId: String,
    documentId: String
  ): Future[File] = {
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
}
