package it.pagopa.pdnd.interop.uservice.catalogprocess.service.impl

import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.api.AgreementApi
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.invoker.{ApiRequest, BearerToken}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.Agreement
import it.pagopa.pdnd.interop.uservice.catalogprocess.service.{AgreementManagementInvoker, AgreementManagementService}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContext, Future}

final case class AgreementManagementServiceImpl(invoker: AgreementManagementInvoker, api: AgreementApi)
    extends AgreementManagementService {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  @SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
  override def getAgreements(
    bearerToken: String,
    consumerId: Option[String],
    producerId: Option[String],
    status: Option[String]
  )(implicit ec: ExecutionContext): Future[Seq[Agreement]] = {
    val request: ApiRequest[Seq[Agreement]] =
      api.getAgreements(consumerId = consumerId, producerId = producerId, status = status)(BearerToken(bearerToken))
    invoker
      .execute(request)
      .map { result =>
        logger.info(s"Agreements retrieved for consumer ${consumerId.getOrElse("Unknown")}")
        result.content
      }
      .recoverWith { case ex =>
        logger.error(s"Error trying to get agreements for consumer ${consumerId.getOrElse("Unknown")}. Error: ${ex.getMessage}")
        Future.failed[Seq[Agreement]](ex)
      }

  }
}
