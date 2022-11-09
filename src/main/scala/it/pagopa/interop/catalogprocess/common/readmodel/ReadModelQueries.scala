package it.pagopa.interop.catalogprocess.common.readmodel

import it.pagopa.interop.catalogmanagement.model.CatalogItem
import it.pagopa.interop.catalogmanagement.model.persistence.JsonFormats._
import it.pagopa.interop.catalogprocess.api.impl.Converter.convertFromApiDescriptorState
import it.pagopa.interop.catalogprocess.model.EServiceDescriptorState
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import org.mongodb.scala.Document
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Aggregates.{`match`, count, project, sort}
import org.mongodb.scala.model.Filters
import org.mongodb.scala.model.Projections.{computed, fields, include}
import org.mongodb.scala.model.Sorts.ascending

import scala.concurrent.{ExecutionContext, Future}

object ReadModelQueries {

  def listEServices(
    name: Option[String],
    producersIds: List[String],
    states: List[EServiceDescriptorState],
    offset: Int,
    limit: Int
  )(readModel: ReadModelService)(implicit ec: ExecutionContext): Future[PaginatedResult[CatalogItem]] = {
    val query = listEServicesFilters(name, producersIds, states)

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

  def listEServicesFilters(
    name: Option[String],
    producersIds: List[String],
    states: List[EServiceDescriptorState]
  ): Bson = {
    val statesPartialFilter = states
      .map(convertFromApiDescriptorState)
      .map(_.toString)
      .map(Filters.eq("data.descriptors.state", _))

    val statesFilter       = mapToVarArgs(statesPartialFilter)(Filters.or)
    val producersIdsFilter = mapToVarArgs(producersIds.map(Filters.eq("data.producerId", _)))(Filters.or)
    val nameFilter         = name.map(Filters.regex("data.name", _, "i"))

    mapToVarArgs(producersIdsFilter.toList ++ statesFilter.toList ++ nameFilter.toList)(Filters.and)
      .getOrElse(Filters.empty())
  }

  def mapToVarArgs[A, B](l: Seq[A])(f: Seq[A] => B): Option[B] = Option.when(l.nonEmpty)(f(l))
}
