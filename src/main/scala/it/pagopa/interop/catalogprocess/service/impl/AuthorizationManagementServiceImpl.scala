package it.pagopa.interop.catalogprocess.service.impl

import it.pagopa.interop.authorizationmanagement.client.api.PurposeApi
import it.pagopa.interop.authorizationmanagement.client.invoker.{ApiRequest, BearerToken}
import it.pagopa.interop.authorizationmanagement.client.model.{ClientComponentState, ClientEServiceDetailsUpdate}
import it.pagopa.interop.catalogprocess.service.{AuthorizationManagementInvoker, AuthorizationManagementService}
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final case class AuthorizationManagementServiceImpl(invoker: AuthorizationManagementInvoker, api: PurposeApi)(implicit
  ec: ExecutionContext
) extends AuthorizationManagementService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def updateStateOnClients(
    bearerToken: String
  )(eServiceId: UUID, state: ClientComponentState, audience: Seq[String], voucherLifespan: Int): Future[Unit] = {
    val payload: ClientEServiceDetailsUpdate =
      ClientEServiceDetailsUpdate(state = state, audience = audience, voucherLifespan = voucherLifespan)
    val request: ApiRequest[Unit] =
      api.updateEServiceState(eserviceId = eServiceId, clientEServiceDetailsUpdate = payload)(BearerToken(bearerToken))
    invoker
      .invoke(request, s"Update EService state on all clients")
      .recoverWith { case _ =>
        Future.successful(())
      } // Do not fail because this service should not be blocked by this update
  }
}
