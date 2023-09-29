package it.pagopa.interop.catalogprocess.service.impl

import it.pagopa.interop.catalogprocess.service.TenantManagementService
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import it.pagopa.interop.catalogprocess.common.readmodel.ReadModelTenantQueries
import it.pagopa.interop.tenantmanagement.model.tenant.PersistentTenant
import it.pagopa.interop.catalogprocess.errors.CatalogProcessErrors.TenantNotFound
import it.pagopa.interop.commons.utils.TypeConversions._

import java.util.UUID
import scala.concurrent.{Future, ExecutionContext}

object TenantManagementServiceImpl extends TenantManagementService {

  override def getTenantById(
    tenantId: UUID
  )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[PersistentTenant] =
    ReadModelTenantQueries.getTenantById(tenantId).flatMap(_.toFuture(TenantNotFound(tenantId)))

}
