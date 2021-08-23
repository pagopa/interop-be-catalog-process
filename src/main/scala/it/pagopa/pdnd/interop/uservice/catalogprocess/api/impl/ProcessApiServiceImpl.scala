package it.pagopa.pdnd.interop.uservice.catalogprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.invoker.BearerToken
import it.pagopa.pdnd.interop.uservice.catalogprocess.service.CatalogManagementService
import it.pagopa.pdnd.interopuservice.catalogprocess.api.ProcessApiService
import it.pagopa.pdnd.interopuservice.catalogprocess.model.{EService, EServiceSeed, Problem}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.ImplicitParameter",
    "org.wartremover.warts.Any",
    "org.wartremover.warts.Equals",
    "org.wartremover.warts.Nothing",
    "org.wartremover.warts.Recursion"
  )
)
final case class ProcessApiServiceImpl(catalogManagementService: CatalogManagementService)(implicit
  ec: ExecutionContext
) extends ProcessApiService {

//  /** Code: 200, Message: List of EServices, DataType: Seq[EService]
//    */
//  override def listEServices()(implicit
//    contexts: Seq[(String, String)],
//    toEntityMarshallerEServicearray: ToEntityMarshaller[Seq[EService]]
//  ): Route = listEServices200(Seq(EService(Some("1234567890"), "MyService")))

  /** Code: 200, Message: EService created, DataType: EService
    * Code: 400, Message: Invalid input, DataType: Problem
    */
  override def createEService(eServiceSeed: EServiceSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerEService: ToEntityMarshaller[EService]
  ): Route = {
    val bearerToken = Future.fromTry(
      contexts
        .find(_._1 == "bearer")
        .map(header => BearerToken(header._2))
        .toRight(new RuntimeException("Bearer Token not provided"))
        .toTry
    )
    val result =
      for {
        bearer          <- bearerToken
        createdEService <- catalogManagementService.createEService(bearer, eServiceSeed)
      } yield createdEService

    onComplete(result) {
      case Success(res) => createEService200(res)
      case Failure(ex) =>
        val errorResponse: Problem = Problem(Option(ex.getMessage), 400, "Error while creating E-Service")
        createEService400(errorResponse)
    }
  }
}
