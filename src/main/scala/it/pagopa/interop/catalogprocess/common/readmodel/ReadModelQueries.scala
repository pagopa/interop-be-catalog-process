package it.pagopa.interop.catalogprocess.common.readmodel

import it.pagopa.interop.agreementmanagement.model.agreement.PersistentAgreement
import it.pagopa.interop.agreementmanagement.model.persistence.JsonFormats._
import it.pagopa.interop.catalogprocess.api.impl.Converter._
import it.pagopa.interop.catalogprocess.model.{EServiceDescriptorState, AgreementState}
import it.pagopa.interop.catalogmanagement.model.{CatalogItem, Suspended, Deprecated, Published}
import it.pagopa.interop.agreementmanagement.model.{agreement => ManagementAgreementState}
import it.pagopa.interop.catalogmanagement.model.persistence.JsonFormats._
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import org.mongodb.scala.Document
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Aggregates.{`match`, count, project, sort, lookup, unwind, addFields}
import org.mongodb.scala.model.{Filters, Field}
import org.mongodb.scala.model.Projections.{computed, fields, include, excludeId}
import org.mongodb.scala.model.Sorts.ascending

import scala.concurrent.{ExecutionContext, Future}

object ReadModelQueries {

  private def listConsumersFilter(eServiceId: String): Bson = {

    val idFilter = Filters.eq("data.id", eServiceId)

    val descriptorsStateFilter =
      Filters.in("data.descriptors.state", Published.toString, Deprecated.toString, Suspended.toString)

    mapToVarArgs(Seq(idFilter) ++ Seq(descriptorsStateFilter))(Filters.and).getOrElse(Filters.empty())

  }
  def listConsumers(eServiceId: String, offset: Int, limit: Int)(
    readModel: ReadModelService
  )(implicit ec: ExecutionContext): Future[PaginatedResult[Consumers]] = {
    val query: Bson = listConsumersFilter(eServiceId)

    val filterPipeline: Seq[Bson] = Seq(
      `match`(query),
      lookup(from = "agreements", localField = "data.id", foreignField = "data.eserviceId", as = "agreements"),
      unwind("$agreements"),
      lookup(from = "tenants", localField = "agreements.data.consumerId", foreignField = "data.id", as = "tenants"),
      unwind("$tenants"),
      `match`(
        Filters.in(
          "agreements.data.state",
          ManagementAgreementState.Active.toString,
          ManagementAgreementState.Suspended.toString
        )
      ),
      addFields(
        Field(
          "validDescriptor",
          Document("""{ $filter: {
              input: "$data.descriptors",
              as: "fd",         
              cond: {  $eq: ["$$fd.id" , "$agreements.data.descriptorId"]}}} }""")
        )
      ),
      unwind("$validDescriptor"),
      `match`(Filters.exists("validDescriptor", true))
    )

    val projection: Bson = project(
      fields(
        computed("descriptorVersion", "$validDescriptor.version"),
        computed("descriptorState", "$validDescriptor.state"),
        computed("agreementState", "$agreements.data.state"),
        computed("consumer", "$tenants.data.name"),
        computed("consumerExternalId", "$tenants.data.externalId.value"),
        excludeId()
      )
    )

    for {
      // Using aggregate to perform case insensitive sorting
      //   N.B.: Required because DocumentDB does not support collation
      consumers <- readModel.aggregateRaw[Consumers](
        "eservices",
        filterPipeline ++
          Seq(projection, sort(ascending("descriptorVersion", "consumer"))),
        offset = offset,
        limit = limit
      )
      // Note: This could be obtained using $facet function (avoiding to execute the query twice),
      //   but it is not supported by DocumentDB
      count     <- readModel.aggregate[TotalCountResult](
        "eservices",
        filterPipeline ++
          Seq(count("totalCount"), project(computed("data", Document("""{ "totalCount" : "$totalCount" }""")))),
        offset = 0,
        limit = Int.MaxValue
      )
    } yield PaginatedResult(results = consumers, totalCount = count.headOption.map(_.totalCount).getOrElse(0))
  }

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

    val query = listEServicesFilters(name, eServicesIds, producersIds, states, exactMatchOnName)

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
    exactMatchOnName: Boolean
  ): Bson = {
    val statesPartialFilter = states
      .map(_.toPersistent)
      .map(_.toString)
      .map(Filters.eq("data.descriptors.state", _))

    val statesFilter       = mapToVarArgs(statesPartialFilter)(Filters.or)
    val eServicesIdsFilter = mapToVarArgs(eServicesIds.map(Filters.eq("data.id", _)))(Filters.or)
    val producersIdsFilter = mapToVarArgs(producersIds.map(Filters.eq("data.producerId", _)))(Filters.or)
    val nameFilter         =
      if (exactMatchOnName) name.map(n => Filters.regex("data.name", s"^$n$$", "i"))
      else name.map(Filters.regex("data.name", _, "i"))
    mapToVarArgs(eServicesIdsFilter.toList ++ producersIdsFilter.toList ++ statesFilter.toList ++ nameFilter.toList)(
      Filters.and
    )
      .getOrElse(Filters.empty())
  }

  private def mapToVarArgs[A, B](l: Seq[A])(f: Seq[A] => B): Option[B] = Option.when(l.nonEmpty)(f(l))
}
