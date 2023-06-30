package it.pagopa.interop.catalogprocess.service

import it.pagopa.interop.agreementmanagement.model.agreement.{PersistentAgreement, PersistentAgreementState}
import it.pagopa.interop.commons.cqrs.service.ReadModelService

import java.util.UUID
import scala.concurrent.{Future, ExecutionContext}

trait AgreementManagementService {
  def getAgreements(
    eServicesIds: Seq[UUID],
    consumersIds: Seq[UUID],
    producersIds: Seq[UUID],
    states: Seq[PersistentAgreementState]
  )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[Seq[PersistentAgreement]]
}
