package it.pagopa.interop.catalogprocess.service

import it.pagopa.interop.authorizationmanagement.client.model.ClientComponentState

import java.util.UUID
import scala.concurrent.Future

trait AuthorizationManagementService {

  def updateStateOnClients(
    bearerToken: String
  )(eServiceId: UUID, state: ClientComponentState, audience: Seq[String], voucherLifespan: Int): Future[Unit]

}
