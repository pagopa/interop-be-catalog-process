package it.pagopa.interop.catalogprocess.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import it.pagopa.interop.catalogprocess.api.HealthApiMarshaller
import it.pagopa.interop.catalogprocess.model.Problem
import spray.json.DefaultJsonProtocol

object HealthApiMarshallerImpl extends HealthApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = entityMarshallerProblem
}
