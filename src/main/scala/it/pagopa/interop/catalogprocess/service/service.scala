package it.pagopa.interop.catalogprocess

import akka.actor.ActorSystem
import it.pagopa.interop._

import scala.concurrent.ExecutionContextExecutor

package object service {
  type CatalogManagementInvoker           = catalogmanagement.client.invoker.ApiInvoker
  type AttributeRegistryManagementInvoker = attributeregistrymanagement.client.invoker.ApiInvoker
  type AgreementManagementInvoker         = agreementmanagement.client.invoker.ApiInvoker
  type AuthorizationManagementInvoker     = authorizationmanagement.client.invoker.ApiInvoker

  object AgreementManagementInvoker {
    def apply(blockingEc: ExecutionContextExecutor)(implicit actorSystem: ActorSystem): AgreementManagementInvoker =
      agreementmanagement.client.invoker.ApiInvoker(agreementmanagement.client.api.EnumsSerializers.all, blockingEc)
  }

  object AuthorizationManagementInvoker {
    def apply(blockingEc: ExecutionContextExecutor)(implicit actorSystem: ActorSystem): AuthorizationManagementInvoker =
      authorizationmanagement.client.invoker
        .ApiInvoker(authorizationmanagement.client.api.EnumsSerializers.all, blockingEc)
  }

  object CatalogManagementInvoker {
    def apply(blockingEc: ExecutionContextExecutor)(implicit actorSystem: ActorSystem): CatalogManagementInvoker =
      catalogmanagement.client.invoker.ApiInvoker(catalogmanagement.client.api.EnumsSerializers.all, blockingEc)
  }

  object AttributeRegistryManagementInvoker {
    def apply(
      blockingEc: ExecutionContextExecutor
    )(implicit actorSystem: ActorSystem): AttributeRegistryManagementInvoker =
      attributeregistrymanagement.client.invoker
        .ApiInvoker(attributeregistrymanagement.client.api.EnumsSerializers.all, blockingEc)
  }

}
