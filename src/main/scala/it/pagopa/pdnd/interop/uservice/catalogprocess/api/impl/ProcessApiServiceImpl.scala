package it.pagopa.pdnd.interop.uservice.catalogprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Route
import it.pagopa.pdnd.interop.uservice.catalogprocess.service.CatalogManagementService
import it.pagopa.pdnd.interopuservice.catalogprocess.api.ProcessApiService
import it.pagopa.pdnd.interopuservice.catalogprocess.model.EService

@SuppressWarnings(
  Array(
    "org.wartremover.warts.ImplicitParameter",
    "org.wartremover.warts.Any",
    "org.wartremover.warts.Nothing",
    "org.wartremover.warts.Recursion"
  )
)
final case class ProcessApiServiceImpl(catalogManagementService: CatalogManagementService) extends ProcessApiService {

  /** Code: 200, Message: List of EServices, DataType: Seq[EService]
    */
  override def listEServices()(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerEServicearray: ToEntityMarshaller[Seq[EService]]
  ): Route = listEServices200(Seq(EService(Some("1234567890"), "MyService")))
}
