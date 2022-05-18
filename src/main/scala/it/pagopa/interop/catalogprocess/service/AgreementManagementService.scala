package it.pagopa.interop.catalogprocess.service

import it.pagopa.interop.agreementmanagement.client.model.{Agreement, AgreementState}

import scala.concurrent.Future

trait AgreementManagementService {
  def getAgreements(consumerId: Option[String], producerId: Option[String], status: Option[AgreementState])(implicit
    contexts: Seq[(String, String)]
  ): Future[Seq[Agreement]]

  /** Returns the agreements related to the consumer passed in input.
    * @param bearerToken
    * @param consumerId
    * @param ec
    * @return
    */
  def getAgreementsByConsumerId(consumerId: String)(implicit contexts: Seq[(String, String)]): Future[Seq[Agreement]] =
    getAgreements(Some(consumerId), None, None)

}
