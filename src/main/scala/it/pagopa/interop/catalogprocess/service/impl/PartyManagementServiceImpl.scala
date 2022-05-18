package it.pagopa.interop.catalogprocess.service.impl

import it.pagopa.interop.catalogprocess.service.{PartyManagementInvoker, PartyManagementService}
import it.pagopa.interop.partymanagement.client.api.PartyApi
import it.pagopa.interop.partymanagement.client.invoker.BearerToken
import it.pagopa.interop.partymanagement.client.model.{BulkInstitutions, BulkPartiesSeed, Institution}
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import java.util.UUID
import scala.concurrent.Future

final case class PartyManagementServiceImpl(invoker: PartyManagementInvoker, api: PartyApi)
    extends PartyManagementService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getInstitution(
    id: UUID
  )(bearerToken: String)(implicit contexts: Seq[(String, String)]): Future[Institution] = {
    val request = api.getInstitutionById(id)(BearerToken(bearerToken))
    invoker.invoke(request, s"Institution retrieved for id ${id.toString}")
  }

  override def getBulkInstitutions(
    identifiers: BulkPartiesSeed
  )(bearerToken: String)(implicit contexts: Seq[(String, String)]): Future[BulkInstitutions] = {
    val request = api.bulkInstitutions(identifiers)(BearerToken(bearerToken))
    invoker
      .invoke(request, s"Institutions retrieved for identifiers ${identifiers.partyIdentifiers.mkString(",")}")
  }
}
