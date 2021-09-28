package it.pagopa.pdnd.interop.uservice.catalogprocess.service

import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.Agreement

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
trait AgreementManagementService {
  def getAgreements(
    bearerToken: String,
    consumerId: Option[String],
    producerId: Option[String],
    status: Option[String]
  )(implicit ec: ExecutionContext): Future[Seq[Agreement]]

  /** Returns the e-service identifiers of the agreements related to the consumer passed in input.
    * @param bearerToken
    * @param consumerId
    * @param ec
    * @return the e-service identifiers
    */
  def getEServiceIdentifiersOfAgreements(
    bearerToken: String
  )(consumerId: String)(implicit ec: ExecutionContext): Future[Seq[UUID]] = {
    for {
      agreements <- getAgreements(bearerToken, Some(consumerId), None, None)
    } yield agreements.map(_.eserviceId)
  }
}
