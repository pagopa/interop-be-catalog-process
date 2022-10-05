package it.pagopa.interop.catalogprocess.service

import it.pagopa.interop.selfcare.partymanagement.client.model.{BulkInstitutions, Institution}

import scala.concurrent.{ExecutionContext, Future}

trait PartyManagementService {
  def getInstitution(id: String)(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Institution]
  def getBulkInstitutions(
    selfcareIds: List[String]
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[BulkInstitutions]
}
