package it.pagopa.interop.catalogprocess.common.readmodel

import it.pagopa.interop.commons.cqrs.service.ReadModelService
import it.pagopa.interop.tenantmanagement.model.persistence.JsonFormats._
import it.pagopa.interop.tenantmanagement.model.tenant.PersistentTenant
import org.mongodb.scala.model.Filters

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

object ReadModelTenantQueries extends ReadModelQuery {
  def getTenantById(
    tenantId: UUID
  )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[Option[PersistentTenant]] =
    readModel.findOne[PersistentTenant]("tenants", Filters.eq("data.id", tenantId.toString))
}
