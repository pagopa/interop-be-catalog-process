package it.pagopa.pdnd.interop.uservice.catalogprocess

import akka.actor.ActorSystem
import it.pagopa.pdnd.interop.uservice.agreementmanagement
import it.pagopa.pdnd.interop.uservice.catalogmanagement

package object service {
  type CatalogManagementInvoker   = catalogmanagement.client.invoker.ApiInvoker
  type AgreementManagementInvoker = agreementmanagement.client.invoker.ApiInvoker

  @SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
  object AgreementManagementInvoker {
    def apply()(implicit actorSystem: ActorSystem): AgreementManagementInvoker =
      agreementmanagement.client.invoker.ApiInvoker(agreementmanagement.client.api.EnumsSerializers.all)
  }

  @SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
  object CatalogManagementInvoker {
    def apply()(implicit actorSystem: ActorSystem): CatalogManagementInvoker =
      catalogmanagement.client.invoker.ApiInvoker(catalogmanagement.client.api.EnumsSerializers.all)
  }

}
