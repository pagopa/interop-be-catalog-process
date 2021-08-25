package it.pagopa.pdnd.interop.uservice.catalogprocess.service.impl

import it.pagopa.pdnd.interop.uservice.catalogmanagement.client
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.api.EServiceApi
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.invoker.{ApiRequest, BearerToken}
import it.pagopa.pdnd.interop.uservice.catalogprocess.model.UpdateDescriptorSeed
import it.pagopa.pdnd.interop.uservice.catalogprocess.service.{CatalogManagementInvoker, CatalogManagementService}
import it.pagopa.pdnd.interopuservice.catalogprocess.model.{EService, EServiceSeed}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContext, Future}

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter", "org.wartremover.warts.StringPlusAny"))
final case class CatalogManagementServiceImpl(invoker: CatalogManagementInvoker, api: EServiceApi)(implicit
  ec: ExecutionContext
) extends CatalogManagementService {
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

  override def updateDescriptor(
    bearerToken: BearerToken,
    eServiceId: String,
    descriptorId: String,
    seed: UpdateDescriptorSeed
  ): Future[EService] = {
    val request: ApiRequest[client.model.EService] =
      api.updateDescriptor(eServiceId, descriptorId, seed.toApi())(bearerToken)
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
}
