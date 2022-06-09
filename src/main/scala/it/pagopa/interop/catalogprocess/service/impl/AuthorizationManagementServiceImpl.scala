package it.pagopa.interop.catalogprocess.service.impl

import it.pagopa.interop.authorizationmanagement.client.api.PurposeApi
import it.pagopa.interop.authorizationmanagement.client.invoker.BearerToken
import it.pagopa.interop.authorizationmanagement.client.model.{ClientComponentState, ClientEServiceDetailsUpdate}
import it.pagopa.interop.catalogprocess.service.{AuthorizationManagementInvoker, AuthorizationManagementService}
import it.pagopa.interop.commons.utils.TypeConversions.EitherOps
import it.pagopa.interop.commons.utils.extractHeaders
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final case class AuthorizationManagementServiceImpl(invoker: AuthorizationManagementInvoker, api: PurposeApi)(implicit
  ec: ExecutionContext
) extends AuthorizationManagementService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def updateStateOnClients(
    eServiceId: UUID,
    descriptorId: UUID,
    state: ClientComponentState,
    audience: Seq[String],
    voucherLifespan: Int
  )(implicit contexts: Seq[(String, String)]): Future[Unit] = {

    val payload: ClientEServiceDetailsUpdate =
      ClientEServiceDetailsUpdate(
        descriptorId = descriptorId,
        state = state,
        audience = audience,
        voucherLifespan = voucherLifespan
      )

    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.updateEServiceState(
        xCorrelationId = correlationId,
        eserviceId = eServiceId,
        clientEServiceDetailsUpdate = payload,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
      result <- invoker
        .invoke(request, s"Update EService state on all clients")
        .recoverWith { case _ =>
          Future.successful(())
        } // Do not fail because this service should not be blocked by this update
    } yield result

  }
}
