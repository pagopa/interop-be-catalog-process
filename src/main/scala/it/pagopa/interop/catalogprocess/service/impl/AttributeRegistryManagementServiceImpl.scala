package it.pagopa.interop.catalogprocess.service.impl

import it.pagopa.interop.attributeregistrymanagement.client.api.AttributeApi
import it.pagopa.interop.attributeregistrymanagement.client.invoker.BearerToken
import it.pagopa.interop.attributeregistrymanagement.client.model.Attribute
import it.pagopa.interop.catalogprocess.service.{AttributeRegistryManagementInvoker, AttributeRegistryManagementService}
import it.pagopa.interop.commons.utils.TypeConversions.EitherOps
import it.pagopa.interop.commons.utils.extractHeaders
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import scala.concurrent.{ExecutionContext, Future}
import java.util.UUID

final case class AttributeRegistryManagementServiceImpl(invoker: AttributeRegistryManagementInvoker, api: AttributeApi)(
  implicit ec: ExecutionContext
) extends AttributeRegistryManagementService {

  val logger: LoggerTakingImplicit[ContextFieldsToLog] = Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getAttributesBulk(
    attributeIds: Seq[UUID]
  )(implicit contexts: Seq[(String, String)]): Future[Seq[Attribute]] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.getBulkedAttributes(
        xCorrelationId = correlationId,
        xForwardedFor = ip,
        Some(attributeIds.map(_.toString).mkString(","))
      )(BearerToken(bearerToken))
      result <- invoker
        .execute(request)
        .map { x =>
          logger.info(s"Retrieving attributes using bulked request ${x.code} > ${x.content}")
          x.content.attributes
        }
        .recoverWith { case ex =>
          logger.error(s"Retrieving attributes using bulked request FAILED", ex)
          Future.failed[Seq[Attribute]](ex)
        }
    } yield result
  }

}
