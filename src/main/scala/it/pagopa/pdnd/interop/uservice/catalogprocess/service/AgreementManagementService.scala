package it.pagopa.pdnd.interop.uservice.catalogprocess.service

import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.Agreement

import scala.concurrent.{ExecutionContext, Future}
trait AgreementManagementService {
  @SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
  def getAgreements(
    bearerToken: String,
    consumerId: Option[String],
    producerId: Option[String],
    status: Option[String]
  )(implicit ec: ExecutionContext): Future[Seq[Agreement]]
}
