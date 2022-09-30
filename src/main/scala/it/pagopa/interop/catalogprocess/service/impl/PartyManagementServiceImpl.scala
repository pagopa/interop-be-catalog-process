package it.pagopa.interop.catalogprocess.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.catalogprocess.service.{
  PartyManagementApiKeyValue,
  PartyManagementInvoker,
  PartyManagementService
}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.selfcare.partymanagement.client.api.PartyApi
import it.pagopa.interop.commons.utils.withUid
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.selfcare.partymanagement.client.model.{BulkInstitutions, BulkPartiesSeed, Institution}

import scala.concurrent.{ExecutionContext, Future}
import java.util.UUID

final case class PartyManagementServiceImpl(invoker: PartyManagementInvoker, api: PartyApi)(implicit
  partyManagementApiKeyValue: PartyManagementApiKeyValue
) extends PartyManagementService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getInstitution(
    id: String
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Institution] = withUid(uid =>
    id.toFutureUUID.flatMap { idAsUUID =>
      val request = api.getInstitutionById(idAsUUID)(uid)
      invoker.invoke(request, s"Institution retrieved for id ${id.toString}")
    }
  )

  override def getBulkInstitutions(
    identifiers: List[String]
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[BulkInstitutions] = withUid { uid =>
    val ids     = BulkPartiesSeed(identifiers.map(UUID.fromString))
    val request = api.bulkInstitutions(ids)(uid)
    invoker.invoke(request, s"Institutions retrieved for identifiers ${ids.partyIdentifiers.mkString(",")}")
  }
}
