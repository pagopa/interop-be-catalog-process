package it.pagopa.pdnd.interop.uservice.catalogprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FileInfo
import cats.implicits.toTraverseOps
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.invoker.{ApiError, BearerToken}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.EServiceDescriptorSeedEnums.Status
import it.pagopa.pdnd.interop.uservice.catalogprocess.model.UpdateDescriptorSeed
import it.pagopa.pdnd.interop.uservice.catalogprocess.service.CatalogManagementService
import it.pagopa.pdnd.interopuservice.catalogprocess.api.ProcessApiService
import it.pagopa.pdnd.interopuservice.catalogprocess.model.{EService, EServiceSeed, FlatEService, Problem}
import org.slf4j.{Logger, LoggerFactory}

import java.io.File
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

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  /** Code: 200, Message: EService created, DataType: EService
    * Code: 400, Message: Invalid input, DataType: Problem
    */
  override def createEService(eServiceSeed: EServiceSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerEService: ToEntityMarshaller[EService]
  ): Route = {
    val result =
      for {
        bearer          <- tokenFromContext(contexts)
        createdEService <- catalogManagementService.createEService(bearer, eServiceSeed)
      } yield createdEService

    onComplete(result) {
      case Success(res) => createEService200(res)
      case Failure(ex) =>
        val errorResponse: Problem = Problem(
          Option(ex.getMessage),
          400,
          s"Error while creating E-Service for producer Id ${eServiceSeed.producerId.toString}"
        )
        createEService400(errorResponse)
    }
  }

  /** Code: 204, Message: E-Service draft Descriptor deleted
    * Code: 400, Message: Invalid input, DataType: Problem
    * Code: 404, Message: Not found, DataType: Problem
    */
  override def deleteDraft(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result =
      for {
        bearer <- tokenFromContext(contexts)
        _      <- catalogManagementService.deleteDraft(bearer, eServiceId, descriptorId)
      } yield ()

    onComplete(result) {
      case Success(_) => deleteDraft204
      case Failure(ex: ApiError[_]) if ex.code == 400 =>
        deleteDraft400(
          Problem(
            Option(ex.getMessage),
            400,
            s"Error while deleting draft descriptor $descriptorId for E-Service $eServiceId"
          )
        )
      case Failure(ex: ApiError[_]) if ex.code == 404 =>
        deleteDraft404(
          Problem(
            Option(ex.getMessage),
            404,
            s"Error while deleting draft descriptor $descriptorId for E-Service $eServiceId"
          )
        )
      case Failure(ex) =>
        deleteDraft500(
          Problem(
            Option(ex.getMessage),
            500,
            s"Unexpected error while deleting draft descriptor $descriptorId for E-Service $eServiceId"
          )
        )
    }
  }

  /** Code: 200, Message: A list of E-Service, DataType: Seq[EService]
    * Code: 500, Message: Internal Server Error, DataType: Problem
    */
  override def getEServices(producerId: Option[String], consumerId: Option[String], status: Option[String])(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerEServicearray: ToEntityMarshaller[Seq[EService]],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result =
      for {
        bearer   <- tokenFromContext(contexts)
        response <- catalogManagementService.listEServices(bearer, producerId, consumerId, status)
      } yield response

    onComplete(result) {
      case Success(response) => getEServices200(response)
      case Failure(ex) =>
        getEServices500(Problem(Option(ex.getMessage), 500, s"Unexpected error while retrieving E-Services"))
    }
  }

  /** Code: 200, Message: E-Service Descriptor published, DataType: EService
    * Code: 400, Message: Invalid input, DataType: Problem
    * Code: 404, Message: Not found, DataType: Problem
    */
  override def publishDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerEService: ToEntityMarshaller[EService]
  ): Route = {
    val result =
      for {
        bearer          <- tokenFromContext(contexts)
        currentEService <- catalogManagementService.getEService(bearer, eServiceId)
        // TODO Status should be an enum
        currentActiveDescriptor = currentEService.descriptors.find(_.status == "published") // Must be at most one
        descriptorToPublishSeed = UpdateDescriptorSeed(description = None, status = Some(Status.Published))
        updatedEService <- catalogManagementService.updateDescriptor(
          bearer,
          eServiceId,
          descriptorId,
          descriptorToPublishSeed
        )
        _ <- currentActiveDescriptor
          .map(oldDescriptor =>
            deprecateDescriptorOrCancelPublication(
              bearer = bearer,
              eServiceId = eServiceId,
              descriptorIdToDeprecate = oldDescriptor.id.toString,
              descriptorIdToCancel = descriptorId
            )
          )
          .sequence
      } yield updatedEService

    onComplete(result) {
      case Success(response) => publishDescriptor200(response)
      case Failure(ex: ApiError[_]) if ex.code == 400 =>
        publishDescriptor400(
          Problem(
            Option(ex.getMessage),
            400,
            s"Error while publishing descriptor $descriptorId for E-Service $eServiceId"
          )
        )
      case Failure(ex: ApiError[_]) if ex.code == 404 =>
        publishDescriptor404(
          Problem(
            Option(ex.getMessage),
            404,
            s"Error while publishing descriptor $descriptorId for E-Service $eServiceId"
          )
        )
      case Failure(ex) =>
        publishDescriptor500(
          Problem(
            Option(ex.getMessage),
            500,
            s"Unexpected error while publishing descriptor $descriptorId for E-Service $eServiceId"
          )
        )
    }
  }

  /** Code: 200, Message: E-Service retrieved, DataType: EService
    * Code: 404, Message: E-Service not found, DataType: Problem
    * Code: 400, Message: Bad request, DataType: Problem
    * Code: 500, Message: Internal Server Error, DataType: Problem
    */
  override def getEServiceById(eServiceId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerEService: ToEntityMarshaller[EService]
  ): Route = {
    val result =
      for {
        bearer   <- tokenFromContext(contexts)
        response <- catalogManagementService.getEService(bearer, eServiceId)
      } yield response

    onComplete(result) {
      case Success(response) => getEServiceById200(response)
      case Failure(ex) =>
        getEServiceById500(Problem(Option(ex.getMessage), 500, s"Unexpected error retrieving E-Service $eServiceId"))
    }
  }

  /** Code: 200, Message: EService Document created, DataType: EService
    * Code: 400, Message: Invalid input, DataType: Problem
    * Code: 404, Message: Not found, DataType: Problem
    */
  override def createEServiceDocument(
    eServiceId: String,
    descriptorId: String,
    kind: String,
    description: String,
    doc: (FileInfo, File)
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerEService: ToEntityMarshaller[EService]
  ): Route = {
    val result =
      for {
        bearer <- tokenFromContext(contexts)
        response <- catalogManagementService.createEServiceDocument(
          bearer,
          eServiceId,
          descriptorId,
          kind,
          description,
          doc
        )
      } yield response

    onComplete(result) {
      case Success(response) => createEServiceDocument200(response)
      case Failure(ex: ApiError[_]) if ex.code == 400 =>
        createEServiceDocument400(
          Problem(
            Option(ex.getMessage),
            400,
            s"Error creating document for E-Service $eServiceId and descriptor $descriptorId"
          )
        )
      case Failure(ex: ApiError[_]) if ex.code == 404 =>
        createEServiceDocument404(
          Problem(
            Option(ex.getMessage),
            404,
            s"Error creating document for E-Service $eServiceId and descriptor $descriptorId"
          )
        )
      case Failure(ex) =>
        createEServiceDocument500(
          Problem(
            Option(ex.getMessage),
            500,
            s"Error creating document for E-Service $eServiceId and descriptor $descriptorId"
          )
        )
    }
  }

  /** Code: 200, Message: EService document retrieved, DataType: File
    * Code: 404, Message: EService not found, DataType: Problem
    * Code: 400, Message: Bad request, DataType: Problem
    * Code: 500, Message: Internal Server Error, DataType: Problem
    */
  override def getEServiceDocumentById(eServiceId: String, descriptorId: String, documentId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerFile: ToEntityMarshaller[File]
  ): Route = {
    val result =
      for {
        bearer   <- tokenFromContext(contexts)
        response <- catalogManagementService.getEServiceDocument(bearer, eServiceId, descriptorId, documentId)
      } yield response

    onComplete(result) {
      case Success(response) => getEServiceDocumentById200(response)
      case Failure(ex: ApiError[_]) if ex.code == 400 =>
        getEServiceDocumentById400(
          Problem(
            Option(ex.getMessage),
            400,
            s"Error retrieving document $documentId for E-Service $eServiceId and descriptor $descriptorId"
          )
        )
      case Failure(ex: ApiError[_]) if ex.code == 404 =>
        getEServiceDocumentById404(
          Problem(
            Option(ex.getMessage),
            404,
            s"Error retrieving document $documentId for E-Service $eServiceId and descriptor $descriptorId"
          )
        )
      case Failure(ex) =>
        getEServiceDocumentById500(
          Problem(
            Option(ex.getMessage),
            500,
            s"Error retrieving document $documentId for E-Service $eServiceId and descriptor $descriptorId"
          )
        )
    }
  }

  /** Code: 200, Message: A list of flattened E-Services, DataType: Seq[FlatEService]
    * Code: 500, Message: Internal Server Error, DataType: Problem
    */
  override def getFlatEServices(producerId: Option[String], consumerId: Option[String], status: Option[String])(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerFlatEServicearray: ToEntityMarshaller[Seq[FlatEService]],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {

    val result: Future[Seq[EService]] =
      for {
        bearer   <- tokenFromContext(contexts)
        response <- catalogManagementService.listEServices(bearer, producerId, consumerId, status)
      } yield response

    onComplete(result) {
      case Success(response) => getFlatEServices200(response.flatMap(convertToFlattenEservice))
      case Failure(ex) =>
        getFlatEServices500(
          Problem(Option(ex.getMessage), 500, s"Unexpected error while retrieving flatted E-Services")
        )
    }

  }

  private[this] def deprecateDescriptorOrCancelPublication(
    bearer: BearerToken,
    eServiceId: String,
    descriptorIdToDeprecate: String,
    descriptorIdToCancel: String
  ): Future[EService] = {
    deprecateDescriptor(descriptorIdToDeprecate, eServiceId, bearer)
      .recoverWith(error =>
        resetDescriptorToDraft(eServiceId, descriptorIdToCancel, bearer)
          .flatMap(_ => Future.failed(error))
      )
  }

  private[this] def deprecateDescriptor(
    descriptorId: String,
    eServiceId: String,
    bearerToken: BearerToken
  ): Future[EService] = {
    val descriptorSeed =
      UpdateDescriptorSeed(description = None, status = Some(Status.Deprecated)) // TODO It should be in a library
    catalogManagementService
      .updateDescriptor(
        bearerToken = bearerToken,
        eServiceId = eServiceId,
        descriptorId = descriptorId,
        seed = descriptorSeed
      )
      .recoverWith { case ex =>
        logger.error(s"Unable to deprecate descriptor $descriptorId on E-Service $eServiceId. Reason: ${ex.getMessage}")
        Future.failed(ex)
      }
  }

  private[this] def resetDescriptorToDraft(
    eServiceId: String,
    descriptorId: String,
    bearerToken: BearerToken
  ): Future[EService] = {
    val descriptorSeed =
      UpdateDescriptorSeed(description = None, status = Some(Status.Draft)) // TODO It should be in a library
    catalogManagementService
      .updateDescriptor(
        bearerToken = bearerToken,
        eServiceId = eServiceId,
        descriptorId = descriptorId,
        seed = descriptorSeed
      )
      .map { result =>
        logger.info(s"Publication cancelled for descriptor $descriptorId in E-Service $eServiceId")
        result
      }
  }

  private[this] def tokenFromContext(context: Seq[(String, String)]): Future[BearerToken] =
    Future.fromTry(
      context
        .find(_._1 == "bearer")
        .map(header => BearerToken(header._2))
        .toRight(new RuntimeException("Bearer Token not provided"))
        .toTry
    )

  private def convertToFlattenEservice(eservice: EService): Seq[FlatEService] = {
    eservice.descriptors.map(descriptor =>
      FlatEService(
        id = eservice.id,
        name = eservice.name,
        version = descriptor.version,
        status = descriptor.status,
        descriptorId = descriptor.id.toString
      )
    )
  }
}
