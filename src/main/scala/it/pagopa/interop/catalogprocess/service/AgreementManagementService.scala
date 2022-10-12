package it.pagopa.interop.catalogprocess.service

import it.pagopa.interop.agreementmanagement.client.model.{Agreement, AgreementState}

import scala.concurrent.Future

trait AgreementManagementService {
  def getAgreements(
    consumerId: Option[String],
    producerId: Option[String],
    states: List[AgreementState],
    eServiceId: Option[String]
  )(implicit contexts: Seq[(String, String)]): Future[Seq[Agreement]]

}
