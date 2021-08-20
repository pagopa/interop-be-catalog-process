package it.pagopa.pdnd.interop.uservice.catalogprocess.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.pdnd.interopuservice.catalogprocess.api.ProcessApiMarshaller
import it.pagopa.pdnd.interopuservice.catalogprocess.model.EService
import spray.json._

class ProcessApiMarshallerImpl extends ProcessApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {
  override implicit def toEntityMarshallerEServicearray: ToEntityMarshaller[Seq[EService]] = ???
}
