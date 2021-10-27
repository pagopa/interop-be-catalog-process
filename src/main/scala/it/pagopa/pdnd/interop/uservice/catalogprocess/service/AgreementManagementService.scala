package it.pagopa.pdnd.interop.uservice.catalogprocess.service

import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.Agreement

import scala.concurrent.{ExecutionContext, Future}

trait AgreementManagementService {
  def getAgreements(
    bearerToken: String,
    consumerId: Option[String],
    producerId: Option[String],
    status: Option[String]
  )(implicit ec: ExecutionContext): Future[Seq[Agreement]]

  /** Returns the agreements related to the consumer passed in input.
    * @param bearerToken
    * @param consumerId
    * @param ec
    * @return
    */
  def getAgreementsByConsumerId(
    bearerToken: String
  )(consumerId: String)(implicit ec: ExecutionContext): Future[Seq[Agreement]] = {
    for {
      agreements <- getAgreements(bearerToken, Some(consumerId), None, None)
    } yield agreements
  }
}
