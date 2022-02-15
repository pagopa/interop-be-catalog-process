package it.pagopa.pdnd.interop.uservice.catalogprocess.service

import it.pagopa.pdnd.interop.uservice.keymanagement.client.model.ClientComponentState

import java.util.UUID
import scala.concurrent.Future

trait AuthorizationManagementService {

  def updateStateOnClients(
    bearerToken: String
  )(eServiceId: UUID, state: ClientComponentState, audience: Seq[String], voucherLifespan: Int): Future[Unit]

}
