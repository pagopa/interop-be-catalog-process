package it.pagopa.pdnd.interop.uservice.catalogprocess.service.impl

import it.pagopa.pdnd.interop.uservice.catalogprocess.common.system._
import it.pagopa.pdnd.interop.uservice.catalogprocess.service.{PartyManagementInvoker, PartyManagementService}
import it.pagopa.pdnd.interop.uservice.partymanagement.client.api.PartyApi
import it.pagopa.pdnd.interop.uservice.partymanagement.client.model.{BulkOrganizations, BulkPartiesSeed, Organization}
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.Future

final case class PartyManagementServiceImpl(invoker: PartyManagementInvoker, api: PartyApi)
    extends PartyManagementService {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  override def getOrganization(id: UUID): Future[Organization] = {
    val request = api.getPartyOrganizationByUUID(id)
    invoker
      .execute(request)
      .map { result =>
        logger.info(s"Organization retrieved for id ${id.toString}")
        result.content
      }
      .recoverWith { case ex =>
        logger.error(s"Error trying to get organization for id ${id.toString}. Error: ${ex.getMessage}")
        Future.failed[Organization](ex)
      }
  }

  override def getBulkOrganizations(identifiers: BulkPartiesSeed): Future[BulkOrganizations] = {
    val request = api.bulkOrganizations(identifiers)
    invoker
      .execute(request)
      .map { result =>
        logger.info(s"Organizations retrieved for identifiers ${identifiers.partyIdentifiers.mkString(",")}")
        result.content
      }
      .recoverWith { case ex =>
        logger.error(
          s"Error trying to retriever organizations for id ${identifiers.partyIdentifiers.mkString(",")}. Error: ${ex.getMessage}"
        )
        Future.failed[BulkOrganizations](ex)
      }
  }
}
