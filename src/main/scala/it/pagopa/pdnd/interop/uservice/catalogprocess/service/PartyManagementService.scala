package it.pagopa.pdnd.interop.uservice.catalogprocess.service

import it.pagopa.pdnd.interop.uservice.partymanagement.client.model.{BulkOrganizations, BulkPartiesSeed, Organization}

import java.util.UUID
import scala.concurrent.Future

trait PartyManagementService {
  def getOrganization(id: UUID)(bearerToken: String): Future[Organization]
  def getBulkOrganizations(identifiers: BulkPartiesSeed)(bearerToken: String): Future[BulkOrganizations]
}
