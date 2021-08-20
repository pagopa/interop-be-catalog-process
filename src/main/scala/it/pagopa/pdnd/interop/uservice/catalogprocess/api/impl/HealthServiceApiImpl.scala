package it.pagopa.pdnd.interop.uservice.catalogprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Route
import it.pagopa.pdnd.interopuservice.catalogprocess.api.HealthApiService
import it.pagopa.pdnd.interopuservice.catalogprocess.model.Problem

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
class HealthServiceApiImpl extends HealthApiService {

  /** Code: 200, Message: successful operation, DataType: Problem
    */
  override def getStatus()(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = getStatus200(Problem(None, 200, "OK"))

}
