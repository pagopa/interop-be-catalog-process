package it.pagopa.interop.catalogprocess.common.readmodel

import it.pagopa.interop.agreementmanagement.model.agreement.{PersistentAgreement, PersistentAgreementState}
import it.pagopa.interop.agreementmanagement.model.persistence.JsonFormats._
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Aggregates.{`match`, project, sort}
import org.mongodb.scala.model.Filters
import org.mongodb.scala.model.Projections.{fields, include}
import org.mongodb.scala.model.Sorts.ascending

import scala.concurrent.{ExecutionContext, Future}
import java.util.UUID

object ReadModelAgreementQueries extends ReadModelQuery {
  def getAgreements(
    eServicesIds: Seq[UUID],
    consumersIds: Seq[UUID],
    producersIds: Seq[UUID],
    states: Seq[PersistentAgreementState],
    offset: Int,
    limit: Int
  )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[Seq[PersistentAgreement]] = {

    val query: Bson =
      getAgreementsFilters(eServicesIds, consumersIds, producersIds, states)

    for {
      agreements <- readModel.aggregate[PersistentAgreement](
        "agreements",
        Seq(`match`(query), project(fields(include("data"))), sort(ascending("data.id"))),
        offset = offset,
        limit = limit
      )
    } yield agreements

  }

  private def getAgreementsFilters(
    eServicesIds: Seq[UUID],
    consumersIds: Seq[UUID],
    producersIds: Seq[UUID],
    states: Seq[PersistentAgreementState]
  ): Bson = {

    val statesFilter = listStatesFilter(states)

    val eServicesIdsFilter =
      mapToVarArgs(eServicesIds.map(id => Filters.eq("data.eserviceId", id.toString)))(Filters.or)
    val consumersIdsFilter =
      mapToVarArgs(consumersIds.map(id => Filters.eq("data.consumerId", id.toString)))(Filters.or)
    val producersIdsFilter =
      mapToVarArgs(producersIds.map(id => Filters.eq("data.producerId", id.toString)))(Filters.or)

    mapToVarArgs(
      eServicesIdsFilter.toList ++ consumersIdsFilter.toList ++ producersIdsFilter.toList ++ statesFilter.toList
    )(Filters.and).getOrElse(Filters.empty())
  }

  private def listStatesFilter(states: Seq[PersistentAgreementState]): Option[Bson] =
    mapToVarArgs(
      states
        .map(_.toString)
        .map(Filters.eq("data.state", _))
    )(Filters.or)
}
