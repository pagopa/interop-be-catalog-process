package it.pagopa.interop.catalogprocess.service

import it.pagopa.interop.selfcare.partymanagement.client.model.{BulkInstitutions, BulkPartiesSeed, Institution}

import java.util.UUID
import scala.concurrent.Future

trait PartyManagementService {
  def getInstitution(id: UUID)(bearerToken: String)(implicit contexts: Seq[(String, String)]): Future[Institution]
  def getBulkInstitutions(identifiers: BulkPartiesSeed)(bearerToken: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[BulkInstitutions]
}
