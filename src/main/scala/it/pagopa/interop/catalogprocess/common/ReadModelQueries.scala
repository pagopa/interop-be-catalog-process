package it.pagopa.interop.catalogprocess.common

import cats.implicits._
import it.pagopa.interop.catalogmanagement.model.CatalogItem
import it.pagopa.interop.catalogmanagement.model.persistence.JsonFormats._
import it.pagopa.interop.catalogprocess.api.impl.Converter.convertFromApiDescriptorState
import it.pagopa.interop.catalogprocess.model.EServiceDescriptorState
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import org.mongodb.scala.Document
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Aggregates._
import org.mongodb.scala.model.Filters
import org.mongodb.scala.model.Projections._
import org.mongodb.scala.model.Sorts._

import scala.concurrent.{ExecutionContext, Future}

object ReadModelQueries {

  def listEServices(
    name: Option[String],
    producersId: List[String],
    states: List[EServiceDescriptorState],
    offset: Int,
    limit: Int
  )(readModel: ReadModelService)(implicit ec: ExecutionContext): Future[Seq[CatalogItem]] = {
    val query = listEServicesFilters(name, producersId, states)

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
    } yield eServices
  }

  def listEServicesFilters(
    name: Option[String],
    producersId: List[String],
    states: List[EServiceDescriptorState]
  ): Bson = {
    val statesPartialFilter = states
      .map(convertFromApiDescriptorState)
      .map(_.toString)
      .map(Filters.eq("data.descriptors.state", _))

    val statesFilter      = mapToVarArgs(statesPartialFilter)(Filters.or)
    val producersIdFilter = mapToVarArgs(producersId.map(Filters.eq("data.producerId", _)))(Filters.or)
    val nameFilter        = name.map(Filters.regex("data.name", _, "i"))

    mapToVarArgs(producersIdFilter.toList ++ statesFilter.toList ++ nameFilter.toList)(Filters.and)
      .getOrElse(Filters.empty())
  }

  def mapToVarArgs[A, B](l: Seq[A])(f: Seq[A] => B): Option[B] = if (l.isEmpty) None else f(l).some

}
