package it.pagopa.interop.catalogprocess.service.impl

import it.pagopa.interop.agreementmanagement.client.api.AgreementApi
import it.pagopa.interop.agreementmanagement.client.invoker.BearerToken
import it.pagopa.interop.agreementmanagement.client.model.{Agreement, AgreementState}
import it.pagopa.interop.catalogprocess.service.{AgreementManagementInvoker, AgreementManagementService}
import it.pagopa.interop.commons.utils.TypeConversions.EitherOps
import it.pagopa.interop.commons.utils.extractHeaders
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import scala.concurrent.{ExecutionContext, Future}

final case class AgreementManagementServiceImpl(invoker: AgreementManagementInvoker, api: AgreementApi)(implicit
  ec: ExecutionContext
) extends AgreementManagementService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getAgreements(consumerId: Option[String], producerId: Option[String], states: List[AgreementState])(
    implicit contexts: Seq[(String, String)]
  ): Future[Seq[Agreement]] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = api.getAgreements(
      xCorrelationId = correlationId,
      xForwardedFor = ip,
      consumerId = consumerId,
      producerId = producerId,
      states = states
    )(BearerToken(bearerToken))
    result <- invoker.invoke(request, s"Agreements retrieval for consumer ${consumerId.getOrElse("Unknown")}")
  } yield result

}
