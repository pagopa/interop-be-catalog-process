package it.pagopa.pdnd.interop.uservice.catalogprocess.service.impl

import it.pagopa.pdnd.interop.uservice.catalogmanagement.client
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.api.EServiceApi
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.invoker.{ApiRequest, BearerToken}
import it.pagopa.pdnd.interop.uservice.catalogprocess.service.{CatalogManagementInvoker, CatalogManagementService}
import it.pagopa.pdnd.interopuservice.catalogprocess.model.{EService, EServiceSeed}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContext, Future}

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
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
        .map(eServiceSeedFromCatalogClientSeed)
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
}
