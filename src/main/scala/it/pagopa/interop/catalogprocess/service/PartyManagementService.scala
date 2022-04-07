package it.pagopa.interop.catalogprocess.service

import it.pagopa.interop.partymanagement.client.model.{BulkInstitutions, BulkPartiesSeed, Institution}

import java.util.UUID
import scala.concurrent.Future

trait PartyManagementService {
  def getInstitution(id: UUID)(bearerToken: String): Future[Institution]
  def getBulkInstitutions(identifiers: BulkPartiesSeed)(bearerToken: String): Future[BulkInstitutions]
}
