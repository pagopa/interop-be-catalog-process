package it.pagopa.interop.catalogprocess.common.readmodel

import it.pagopa.interop.catalogmanagement.model.{CatalogItem, Deprecated, Published, Suspended}
import it.pagopa.interop.agreementmanagement.model.{agreement => PersistentAgreement}
import it.pagopa.interop.catalogmanagement.model.persistence.JsonFormats._
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import org.mongodb.scala.Document
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Aggregates.{`match`, addFields, count, lookup, project, sort, unwind}
import org.mongodb.scala.model.{Field, Filters}
import org.mongodb.scala.model.Projections.{computed, excludeId, fields, include}
import org.mongodb.scala.model.Sorts.ascending

import scala.concurrent.{ExecutionContext, Future}
import java.util.UUID
import it.pagopa.interop.catalogmanagement.model.{CatalogItemMode, CatalogDescriptorState}
import org.mongodb.scala.bson.BsonDocument
import it.pagopa.interop.catalogmanagement.model.Draft

object ReadModelCatalogQueries extends ReadModelQuery {

  private def listConsumersFilter(eServiceId: UUID): Bson = {

    val idFilter = Filters.eq("data.id", eServiceId.toString)

    val descriptorsStateFilter =
      Filters.in("data.descriptors.state", Published.toString, Deprecated.toString, Suspended.toString)

    mapToVarArgs(Seq(idFilter) ++ Seq(descriptorsStateFilter))(Filters.and).getOrElse(Filters.empty())
  }

  def getConsumers(eServiceId: UUID, offset: Int, limit: Int)(implicit
    ec: ExecutionContext,
    readModel: ReadModelService
  ): Future[PaginatedResult[Consumers]] = {
    val query: Bson = listConsumersFilter(eServiceId)

    val filterPipeline: Seq[Bson] = Seq(
      `match`(query),
      lookup(from = "agreements", localField = "data.id", foreignField = "data.eserviceId", as = "agreements"),
      unwind("$agreements"),
      lookup(from = "tenants", localField = "agreements.data.consumerId", foreignField = "data.id", as = "tenants"),
      unwind("$tenants"),
      `match`(
        Filters.in("agreements.data.state", PersistentAgreement.Active.toString, PersistentAgreement.Suspended.toString)
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
        computed("consumerName", "$tenants.data.name"),
        computed("consumerExternalId", "$tenants.data.externalId.value"),
        computed("lowerName", Document("""{ "$toLower" : "$tenants.data.name" }""")),
        excludeId()
      )
    )

    for {
      // Using aggregate to perform case insensitive sorting
      //   N.B.: Required because DocumentDB does not support collation
      consumers <- readModel.aggregateRaw[Consumers](
        "eservices",
        filterPipeline ++
          Seq(projection, sort(ascending("lowerName"))),
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

  def getEServiceDocument(eServiceId: UUID, descriptorId: UUID, documentId: UUID)(implicit
    ec: ExecutionContext,
    readModel: ReadModelService
  ): Future[Option[CatalogItem]] = {
    val filters = Filters.and(
      Filters.eq("data.id", eServiceId.toString),
      Filters.eq("data.descriptors.id", descriptorId.toString),
      Filters.or(
        Filters.eq("data.descriptors.docs.id", documentId.toString),
        Filters.eq("data.descriptors.interface.id", documentId.toString)
      )
    )
    readModel.findOne[CatalogItem]("eservices", filters)
  }

  def getEServiceById(
    eServiceId: UUID
  )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[Option[CatalogItem]] = {
    readModel.findOne[CatalogItem]("eservices", Filters.eq("data.id", eServiceId.toString))
  }

  def getEServices(
    requesterId: UUID,
    name: Option[String],
    eServicesIds: Seq[UUID],
    producersIds: Seq[UUID],
    attributesIds: Seq[UUID],
    states: Seq[CatalogDescriptorState],
    mode: Option[CatalogItemMode],
    offset: Int,
    limit: Int,
    exactMatchOnName: Boolean = false,
    isSuperVisor: Boolean
  )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[PaginatedResult[CatalogItem]] = {

    val query =
      listEServicesFilters(
        requesterId,
        name,
        eServicesIds,
        producersIds,
        attributesIds,
        states,
        mode,
        exactMatchOnName,
        isSuperVisor
      )

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
    requesterId: UUID,
    name: Option[String],
    eServicesIds: Seq[UUID],
    producersIds: Seq[UUID],
    attributesIds: Seq[UUID],
    states: Seq[CatalogDescriptorState],
    mode: Option[CatalogItemMode],
    exactMatchOnName: Boolean,
    isSuperVisor: Boolean
  ): Bson = {
    val statesPartialFilter = states
      .map(_.toString)
      .map(Filters.eq("data.descriptors.state", _))

    val statesFilter        = mapToVarArgs(statesPartialFilter)(Filters.or)
    val eServicesIdsFilter  = mapToVarArgs(eServicesIds.map(e => Filters.eq("data.id", e.toString)))(Filters.or)
    val producersIdsFilter  = mapToVarArgs(producersIds.map(p => Filters.eq("data.producerId", p.toString)))(Filters.or)
    val attributesIdsFilter = attributesIds match {
      case Seq()      => None
      case attributes =>
        val attributesInCondition: String = attributes.map(u => s"'${u.toString}'").mkString(",")
        val certifiedFilter: BsonDocument = BsonDocument(
          s"""{ 'data.descriptors.attributes.certified' : { $$elemMatch : {$$elemMatch : {'id' : { $$in : [$attributesInCondition]}}}}}"""
        )
        val declaredFilter: BsonDocument  = BsonDocument(
          s"""{ 'data.descriptors.attributes.declared' : { $$elemMatch : {$$elemMatch : {'id' : { $$in : [$attributesInCondition]}}}}}"""
        )
        val verifiedFilter: BsonDocument  = BsonDocument(
          s"""{ 'data.descriptors.attributes.verified' : { $$elemMatch : {$$elemMatch : {'id' : { $$in : [$attributesInCondition]}}}}}"""
        )
        Some(Filters.or(certifiedFilter, declaredFilter, verifiedFilter))
    }
    val nameFilter          =
      if (exactMatchOnName) name.map(n => Filters.regex("data.name", s"^${escape(n)}$$", "i"))
      else name.map(n => Filters.regex("data.name", escape(n), "i"))

    val modeFilter = mode.map(_.toString).map(Filters.eq("data.mode", _))

    /**
      * Allow only the producer with right permissions to see eservices in draft, that is:
      * - eservices without descriptors
      * - eservices with only one descriptor that is in Draft state
      */
    val isSupervisorFilter =
      if (isSuperVisor)
        Some(
          Filters.nor(
            Filters.and(Filters.ne("data.producerId", requesterId.toString), Filters.size("data.descriptors", 0)),
            Filters.and(
              Filters.ne("data.producerId", requesterId.toString),
              Filters.size("data.descriptors", 1),
              Filters.eq("data.descriptors.state", Draft.toString())
            )
          )
        )
      else
        Some(
          Filters.nor(
            Filters.size("data.descriptors", 0),
            Filters.and(Filters.size("data.descriptors", 1), Filters.eq("data.descriptors.state", Draft.toString()))
          )
        )

    mapToVarArgs(
      eServicesIdsFilter.toList ++ producersIdsFilter.toList ++ attributesIdsFilter.toList ++ statesFilter.toList ++ nameFilter.toList ++ modeFilter.toList ++ isSupervisorFilter.toList
    )(Filters.and)
      .getOrElse(Filters.empty())
  }
}
