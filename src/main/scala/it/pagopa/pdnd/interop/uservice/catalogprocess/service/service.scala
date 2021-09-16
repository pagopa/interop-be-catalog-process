package it.pagopa.pdnd.interop.uservice.catalogprocess

import akka.actor.ActorSystem
import it.pagopa.pdnd.interop.uservice.catalogmanagement
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement

package object service {
  type CatalogManagementInvoker           = catalogmanagement.client.invoker.ApiInvoker
  type AttributeRegistryManagementInvoker = attributeregistrymanagement.client.invoker.ApiInvoker

  @SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
  object CatalogManagementInvoker {
    def apply()(implicit actorSystem: ActorSystem): CatalogManagementInvoker =
      catalogmanagement.client.invoker.ApiInvoker(catalogmanagement.client.api.EnumsSerializers.all)
  }

}
