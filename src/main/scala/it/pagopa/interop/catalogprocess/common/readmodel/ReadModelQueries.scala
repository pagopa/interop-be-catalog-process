package it.pagopa.interop.catalogprocess.common.readmodel

import it.pagopa.interop.agreementmanagement.model.agreement.PersistentAgreement
import it.pagopa.interop.agreementmanagement.model.persistence.JsonFormats._
import it.pagopa.interop.catalogprocess.api.impl.Converter._
import it.pagopa.interop.catalogprocess.model.{EServiceDescriptorState, AgreementState}
import it.pagopa.interop.catalogmanagement.model.CatalogItem
import it.pagopa.interop.catalogmanagement.model.persistence.JsonFormats._
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import org.mongodb.scala.Document
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Aggregates.{`match`, count, project, sort}
import org.mongodb.scala.model.Filters
import org.mongodb.scala.model.Projections.{computed, fields, include}
import org.mongodb.scala.model.Sorts.ascending

import scala.concurrent.{ExecutionContext, Future}

object ReadModelQueries {

  def emptyResults[T] = PaginatedResult[T](results = Nil, totalCount = 0)

  def listAgreements(
    eServicesIds: Seq[String],
    consumersIds: Seq[String],
    producersIds: Seq[String],
    states: Seq[AgreementState]
  )(readModel: ReadModelService)(implicit ec: ExecutionContext): Future[Seq[PersistentAgreement]] = {

    val query: Bson =
      listAgreementsFilters(eServicesIds, consumersIds, producersIds, states)

    for {
      agreements <- readModel.aggregate[PersistentAgreement](
        "agreements",
        Seq(`match`(query), project(fields(include("data"))), sort(ascending("data.id"))),
        offset = 0,
        limit = Int.MaxValue
      )
    } yield agreements

  }

  private def listAgreementsFilters(
    eServicesIds: Seq[String],
    consumersIds: Seq[String],
    producersIds: Seq[String],
    states: Seq[AgreementState]
  ): Bson = {

    val statesFilter = listStatesFilter(states)

    val eServicesIdsFilter = mapToVarArgs(eServicesIds.map(Filters.eq("data.eserviceId", _)))(Filters.or)
    val consumersIdsFilter = mapToVarArgs(consumersIds.map(Filters.eq("data.consumerId", _)))(Filters.or)
    val producersIdsFilter = mapToVarArgs(producersIds.map(Filters.eq("data.producerId", _)))(Filters.or)

    mapToVarArgs(
      eServicesIdsFilter.toList ++ consumersIdsFilter.toList ++ producersIdsFilter.toList ++ statesFilter.toList
    )(Filters.and).getOrElse(Filters.empty())
  }

  private def listStatesFilter(states: Seq[AgreementState]): Option[Bson] =
    mapToVarArgs(
      states
        .map(_.toPersistence)
        .map(_.toString)
        .map(Filters.eq("data.state", _))
    )(Filters.or)

  def listEServices(
    name: Option[String],
    eServicesIds: Seq[String],
    producersIds: Seq[String],
    states: Seq[EServiceDescriptorState],
    offset: Int,
    limit: Int,
    exactMatchOnName: Boolean = false
  )(readModel: ReadModelService)(implicit ec: ExecutionContext): Future[PaginatedResult[CatalogItem]] = {

    val query =
      if (exactMatchOnName) listEServicesFilters(name, eServicesIds, producersIds, states, exactMatchOnName = true)
      else listEServicesFilters(name, eServicesIds, producersIds, states)

    for {
      // Using aggregate to perform case insensitive sorting
      //   N.B.: Required because DocumentDB does not support collation
      eServices <- readModel.aggregate[CatalogItem](
        "eservices",
        Seq(
          `match`(query),
          project(fields(include("data"), computed("lowerName", Document("""{ "$toLower" : "$data.name" }""")))),
          sort(ascending("lowerName"))
        ),
        offset = offset,
        limit = limit
      )

      // Note: This could be obtained using $facet function (avoiding to execute the query twice),
      //   but it is not supported by DocumentDB
      count     <- readModel.aggregate[TotalCountResult](
        "eservices",
        Seq(
          `match`(query),
          count("totalCount"),
          project(computed("data", Document("""{ "totalCount" : "$totalCount" }""")))
        ),
        offset = 0,
        limit = Int.MaxValue
      )
    } yield PaginatedResult(results = eServices, totalCount = count.headOption.map(_.totalCount).getOrElse(0))
  }

  private def listEServicesFilters(
    name: Option[String],
    eServicesIds: Seq[String],
    producersIds: Seq[String],
    states: Seq[EServiceDescriptorState],
    exactMatchOnName: Boolean = false
  ): Bson = {
    val statesPartialFilter = states
      .map(_.toPersistent)
      .map(_.toString)
      .map(Filters.eq("data.descriptors.state", _))

    val statesFilter       = mapToVarArgs(statesPartialFilter)(Filters.or)
    val eServicesIdsFilter = mapToVarArgs(eServicesIds.map(Filters.eq("data.id", _)))(Filters.or)
    val producersIdsFilter = mapToVarArgs(producersIds.map(Filters.eq("data.producerId", _)))(Filters.or)
    val nameFilter         =
      if (exactMatchOnName) name.map(Filters.eq("data.name", _)) else name.map(Filters.regex("data.name", _, "i"))

    mapToVarArgs(eServicesIdsFilter.toList ++ producersIdsFilter.toList ++ statesFilter.toList ++ nameFilter.toList)(
      Filters.and
    )
      .getOrElse(Filters.empty())
  }

  private def mapToVarArgs[A, B](l: Seq[A])(f: Seq[A] => B): Option[B] = Option.when(l.nonEmpty)(f(l))
}
