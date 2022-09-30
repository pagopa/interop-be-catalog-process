package it.pagopa.interop.catalogprocess.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.catalogprocess.service.TenantManagementService
import it.pagopa.interop.tenantmanagement.client.invoker.ApiInvoker
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.tenantmanagement.client.api.{TenantApi, EnumsSerializers}
import it.pagopa.interop.tenantmanagement.client.invoker.BearerToken
import it.pagopa.interop.tenantmanagement.client.model.Tenant

import java.util.UUID
import scala.concurrent.{Future, ExecutionContextExecutor}
import it.pagopa.interop.tenantmanagement.client.invoker.ApiRequest
import akka.actor.typed.ActorSystem
import it.pagopa.interop.commons.utils.withHeaders

class TenantManagementServiceImpl(tenantManagementUrl: String, blockingEc: ExecutionContextExecutor)(implicit
  system: ActorSystem[_]
) extends TenantManagementService {

  val invoker: ApiInvoker = ApiInvoker(EnumsSerializers.all, blockingEc)(system.classicSystem)
  val api: TenantApi      = TenantApi(tenantManagementUrl)

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getTenant(tenantId: UUID)(implicit contexts: Seq[(String, String)]): Future[Tenant] =
    withHeaders[Tenant] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Tenant] =
        api.getTenant(xCorrelationId = correlationId, tenantId = tenantId, xForwardedFor = ip)(BearerToken(bearerToken))
      invoker.invoke(request, s"Retrieving Tenant $tenantId")
    }

}
