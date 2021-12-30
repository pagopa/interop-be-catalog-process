package it.pagopa.pdnd.interop.uservice.catalogprocess.service.impl

import it.pagopa.pdnd.interop.uservice.catalogprocess.service.{PartyManagementInvoker, PartyManagementService}
import it.pagopa.pdnd.interop.uservice.partymanagement.client.api.PartyApi
import it.pagopa.pdnd.interop.uservice.partymanagement.client.invoker.BearerToken
import it.pagopa.pdnd.interop.uservice.partymanagement.client.model.{BulkOrganizations, BulkPartiesSeed, Organization}
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.Future

final case class PartyManagementServiceImpl(invoker: PartyManagementInvoker, api: PartyApi)
    extends PartyManagementService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)
  override def getOrganization(id: UUID)(bearerToken: String): Future[Organization] = {
    val request = api.getOrganizationById(id)(BearerToken(bearerToken))
    invoker.invoke(request, s"Organization retrieved for id ${id.toString}")
  }

  override def getBulkOrganizations(identifiers: BulkPartiesSeed)(bearerToken: String): Future[BulkOrganizations] = {
    val request = api.bulkOrganizations(identifiers)(BearerToken(bearerToken))
    invoker
      .invoke(request, s"Organizations retrieved for identifiers ${identifiers.partyIdentifiers.mkString(",")}")
  }
}
