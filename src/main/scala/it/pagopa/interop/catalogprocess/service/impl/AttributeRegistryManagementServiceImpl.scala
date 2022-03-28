package it.pagopa.interop.catalogprocess.service.impl

import it.pagopa.interop.attributeregistrymanagement.client.api.AttributeApi
import it.pagopa.interop.attributeregistrymanagement.client.invoker.BearerToken
import it.pagopa.interop.attributeregistrymanagement.client.model.Attribute
import it.pagopa.interop.catalogprocess.service.{AttributeRegistryManagementInvoker, AttributeRegistryManagementService}
import it.pagopa.interop.commons.utils.TypeConversions.EitherOps
import it.pagopa.interop.commons.utils.extractHeaders
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContext, Future}

final case class AttributeRegistryManagementServiceImpl(invoker: AttributeRegistryManagementInvoker, api: AttributeApi)(
  implicit ec: ExecutionContext
) extends AttributeRegistryManagementService {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getAttributesBulk(attributeIds: Seq[String])(contexts: Seq[(String, String)]): Future[Seq[Attribute]] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.getBulkedAttributes(
        xCorrelationId = correlationId,
        xForwardedFor = ip,
        Some(attributeIds.mkString(","))
      )(BearerToken(bearerToken))
      result <- invoker
        .execute(request)
        .map { x =>
          logger.info(s"Retrieving attributes using bulked request ${x.code} > ${x.content}")
          x.content.attributes
        }
        .recoverWith { case ex =>
          logger.error(s"Retrieving attributes using bulked request FAILED: ${ex.getMessage}")
          Future.failed[Seq[Attribute]](ex)
        }
    } yield result
  }

}
