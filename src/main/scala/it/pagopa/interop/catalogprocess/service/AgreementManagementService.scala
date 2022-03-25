package it.pagopa.interop.catalogprocess.service

import it.pagopa.interop.agreementmanagement.client.model.{Agreement, AgreementState}

import scala.concurrent.Future

trait AgreementManagementService {
  def getAgreements(
    contexts: Seq[(String, String)],
    consumerId: Option[String],
    producerId: Option[String],
    status: Option[AgreementState]
  ): Future[Seq[Agreement]]

  /** Returns the agreements related to the consumer passed in input.
    * @param bearerToken
    * @param consumerId
    * @param ec
    * @return
    */
  def getAgreementsByConsumerId(contexts: Seq[(String, String)])(consumerId: String): Future[Seq[Agreement]] =
    getAgreements(contexts, Some(consumerId), None, None)

}
