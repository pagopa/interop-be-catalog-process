package it.pagopa.interop.catalogprocess.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.catalogprocess.service.{
  PartyManagementApiKeyValue,
  PartyManagementInvoker,
  PartyManagementService
}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils.getUidFuture
import it.pagopa.interop.selfcare.partymanagement.client.api.PartyApi
import it.pagopa.interop.selfcare.partymanagement.client.model.{BulkInstitutions, BulkPartiesSeed, Institution}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final case class PartyManagementServiceImpl(invoker: PartyManagementInvoker, api: PartyApi)(implicit
  partyManagementApiKeyValue: PartyManagementApiKeyValue
) extends PartyManagementService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getInstitution(
    id: UUID
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Institution] = for {
    uid <- getUidFuture(contexts)
    request = api.getInstitutionById(id)(uid)
    result <- invoker.invoke(request, s"Institution retrieved for id ${id.toString}")
  } yield result

  override def getBulkInstitutions(
    identifiers: BulkPartiesSeed
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[BulkInstitutions] = for {
    uid <- getUidFuture(contexts)
    request = api.bulkInstitutions(identifiers)(uid)
    result <- invoker
      .invoke(request, s"Institutions retrieved for identifiers ${identifiers.partyIdentifiers.mkString(",")}")
  } yield result
}
