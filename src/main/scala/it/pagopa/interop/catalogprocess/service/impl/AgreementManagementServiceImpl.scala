package it.pagopa.interop.catalogprocess.service.impl

import it.pagopa.interop.agreementmanagement.client.api.AgreementApi
import it.pagopa.interop.agreementmanagement.client.invoker.BearerToken
import it.pagopa.interop.agreementmanagement.client.model.{Agreement, AgreementState}
import it.pagopa.interop.catalogprocess.service.{AgreementManagementInvoker, AgreementManagementService}
import it.pagopa.interop.commons.utils.TypeConversions.EitherOps
import it.pagopa.interop.commons.utils.extractHeaders
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContext, Future}

final case class AgreementManagementServiceImpl(invoker: AgreementManagementInvoker, api: AgreementApi)(implicit
  ec: ExecutionContext
) extends AgreementManagementService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getAgreements(
    contexts: Seq[(String, String)],
    consumerId: Option[String],
    producerId: Option[String],
    state: Option[AgreementState]
  ): Future[Seq[Agreement]] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.getAgreements(
        xCorrelationId = correlationId,
        xForwardedFor = ip,
        consumerId = consumerId,
        producerId = producerId,
        state = state
      )(BearerToken(bearerToken))
      result <- invoker.invoke(request, s"Agreements retrieval for consumer ${consumerId.getOrElse("Unknown")}")
    } yield result
  }
}
