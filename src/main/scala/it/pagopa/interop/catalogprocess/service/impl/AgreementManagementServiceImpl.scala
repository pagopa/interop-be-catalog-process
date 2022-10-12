package it.pagopa.interop.catalogprocess.service.impl

import it.pagopa.interop.agreementmanagement.client.api.AgreementApi
import it.pagopa.interop.agreementmanagement.client.invoker.BearerToken
import it.pagopa.interop.agreementmanagement.client.model.{Agreement, AgreementState}
import it.pagopa.interop.catalogprocess.service.{AgreementManagementInvoker, AgreementManagementService}
import it.pagopa.interop.commons.utils._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import scala.concurrent.Future

final case class AgreementManagementServiceImpl(invoker: AgreementManagementInvoker, api: AgreementApi)
    extends AgreementManagementService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getAgreements(
    consumerId: Option[String],
    producerId: Option[String],
    states: List[AgreementState],
    eServiceId: Option[String]
  )(implicit contexts: Seq[(String, String)]): Future[Seq[Agreement]] = withHeaders {
    (bearerToken, correlationId, ip) =>
      val request = api.getAgreements(
        xCorrelationId = correlationId,
        xForwardedFor = ip,
        consumerId = consumerId,
        producerId = producerId,
        states = states,
        eserviceId = eServiceId
      )(BearerToken(bearerToken))
      invoker.invoke(request, s"Agreements retrieval for consumer ${consumerId.getOrElse("Unknown")}")
  }

}
