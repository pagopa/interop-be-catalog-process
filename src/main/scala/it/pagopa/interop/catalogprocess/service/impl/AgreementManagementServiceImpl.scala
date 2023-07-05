package it.pagopa.interop.catalogprocess.service.impl

import it.pagopa.interop.catalogprocess.service.AgreementManagementService
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import it.pagopa.interop.catalogprocess.common.readmodel.ReadModelAgreementQueries
import it.pagopa.interop.agreementmanagement.model.agreement.{PersistentAgreement, PersistentAgreementState}

import java.util.UUID
import scala.concurrent.{Future, ExecutionContext}

object AgreementManagementServiceImpl extends AgreementManagementService {

  override def getAgreements(
    eServicesIds: Seq[UUID],
    consumersIds: Seq[UUID],
    producersIds: Seq[UUID],
    states: Seq[PersistentAgreementState]
  )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[Seq[PersistentAgreement]] =
    getAllAgreements(eServicesIds, consumersIds, producersIds, states)

  private def getAgreements(
    eServicesIds: Seq[UUID],
    consumersIds: Seq[UUID],
    producersIds: Seq[UUID],
    states: Seq[PersistentAgreementState],
    offset: Int,
    limit: Int
  )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[Seq[PersistentAgreement]] =
    ReadModelAgreementQueries.getAgreements(eServicesIds, consumersIds, producersIds, states, offset, limit)

  private def getAllAgreements(
    eServicesIds: Seq[UUID],
    consumersIds: Seq[UUID],
    producersIds: Seq[UUID],
    states: Seq[PersistentAgreementState]
  )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[Seq[PersistentAgreement]] = {

    def getAgreementsFrom(offset: Int): Future[Seq[PersistentAgreement]] =
      getAgreements(
        eServicesIds = eServicesIds,
        consumersIds = consumersIds,
        producersIds = producersIds,
        states = states,
        offset = offset,
        limit = 50
      )

    def go(start: Int)(as: Seq[PersistentAgreement]): Future[Seq[PersistentAgreement]] =
      getAgreementsFrom(start).flatMap(agrs =>
        if (agrs.size < 50) Future.successful(as ++ agrs) else go(start + 50)(as ++ agrs)
      )

    go(0)(Nil)
  }
}
