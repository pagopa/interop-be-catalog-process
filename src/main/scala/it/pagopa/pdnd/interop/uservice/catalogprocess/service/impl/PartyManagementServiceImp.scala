package it.pagopa.pdnd.interop.uservice.catalogprocess.service.impl

import it.pagopa.pdnd.interop.uservice.catalogprocess.service.{PartyManagementInvoker, PartyManagementService}
import it.pagopa.pdnd.interop.uservice.partymanagement.client.model.Organization
import it.pagopa.pdnd.interop.uservice.partymanagement.client.api.PartyApi
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final case class PartyManagementServiceImp(invoker: PartyManagementInvoker, api: PartyApi)
    extends PartyManagementService {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getOrganization(id: UUID)(implicit ec: ExecutionContext): Future[Organization] = {
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
}
