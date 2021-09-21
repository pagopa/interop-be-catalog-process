package it.pagopa.pdnd.interop.uservice.catalogprocess.service

import it.pagopa.pdnd.interop.uservice.partymanagement.client.model.Organization

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait PartyManagementService {
  def getOrganization(id: UUID)(implicit ec: ExecutionContext): Future[Organization]
}
