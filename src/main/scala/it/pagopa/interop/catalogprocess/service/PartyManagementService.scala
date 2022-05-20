package it.pagopa.interop.catalogprocess.service

import it.pagopa.interop.selfcare.partymanagement.client.model.{BulkInstitutions, BulkPartiesSeed, Institution}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait PartyManagementService {
  def getInstitution(id: UUID)(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Institution]
  def getBulkInstitutions(
    identifiers: BulkPartiesSeed
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[BulkInstitutions]
}
