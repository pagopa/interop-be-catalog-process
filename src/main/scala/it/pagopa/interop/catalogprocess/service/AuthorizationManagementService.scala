package it.pagopa.interop.catalogprocess.service

import it.pagopa.interop.authorizationmanagement.client.model.ClientComponentState

import java.util.UUID
import scala.concurrent.Future

trait AuthorizationManagementService {

  def updateStateOnClients(
    eServiceId: UUID,
    descriptorId: UUID,
    state: ClientComponentState,
    audience: Seq[String],
    voucherLifespan: Int
  )(implicit contexts: Seq[(String, String)]): Future[Unit]

}
