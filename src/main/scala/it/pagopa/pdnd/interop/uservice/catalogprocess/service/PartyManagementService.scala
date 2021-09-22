package it.pagopa.pdnd.interop.uservice.catalogprocess.service

import it.pagopa.pdnd.interop.uservice.partymanagement.client.model.Organization

import java.util.UUID
import scala.concurrent.Future

trait PartyManagementService {
  @SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
  def getOrganization(id: UUID): Future[Organization]
}
