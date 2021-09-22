package it.pagopa.pdnd.interop.uservice.catalogprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FileInfo
import cats.implicits.toTraverseOps
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.invoker.ApiError
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement
import it.pagopa.pdnd.interop.uservice.catalogmanagement
import it.pagopa.pdnd.interop.uservice.catalogprocess.errors.{DescriptorNotFound, NotValidDescriptor}
import it.pagopa.pdnd.interop.uservice.catalogprocess.service.{
  AgreementManagementService,
  AttributeManagementService,
  CatalogManagementService,
  PartyManagementService
}
import it.pagopa.pdnd.interop.uservice.catalogprocess.api.ProcessApiService
import it.pagopa.pdnd.interop.uservice.catalogprocess.model._
import org.slf4j.{Logger, LoggerFactory}

import java.io.File
import java.util.UUID
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
final case class ProcessApiServiceImpl(
  catalogManagementService: CatalogManagementService,
  partyManagementService: PartyManagementService,
  attributeManagementService: AttributeManagementService,
  agreementManagementService: AgreementManagementService
)(implicit ec: ExecutionContext)
    extends ProcessApiService {

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
        createdEService <- catalogManagementService.createEService(bearer)(eServiceSeed)
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

  def getApiEservice(eservice: catalogmanagement.client.model.EService): Future[EService] = {
    for {
      organization <- partyManagementService.getOrganization(eservice.producerId)
      attributes   <- attributeManagementService.getAttributesBulk(extractIdsFromAttributes(eservice.attributes))
    } yield EService(
      id = eservice.id,
      producer = Organization(id = eservice.producerId, name = organization.description),
      name = eservice.name,
      description = eservice.description,
      technology = eservice.technology,
      attributes = eservice.attributes,
      descriptors = eservice.descriptors
    )
  }

  private def extractIdsFromAttributes(attributes: catalogmanagement.client.model.Attributes): Seq[String] = {
    attributes.certified.flatMap(extractIdsFromAttribute) ++
      attributes.declared.flatMap(extractIdsFromAttribute) ++
      attributes.verified.flatMap(extractIdsFromAttribute)
  }

  private def extractIdsFromAttribute(attribute: catalogmanagement.client.model.Attribute): Seq[String] = {
    val fromSingle: Seq[String] = attribute.single.toSeq.map(_.id)
    val fromGroup: Seq[String]  = attribute.group.toSeq.flatMap(_.map(_.id))

    fromSingle ++ fromGroup
  }

  def getApiAttributes(
    currentAttributes: Attributes,
    attributes: Seq[attributeregistrymanagement.client.model.Attribute]
  ) = {

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
        _      <- catalogManagementService.deleteDraft(bearer)(eServiceId, descriptorId)
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
        bearer    <- tokenFromContext(contexts)
        eservices <- retrieveEservices(bearer, producerId, consumerId, status)
      } yield eservices

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
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {

    val result =
      for {
        bearer          <- tokenFromContext(contexts)
        currentEService <- catalogManagementService.getEService(bearer)(eServiceId)
        _               <- isDraftDescriptor(currentEService.descriptors.find(_.id.toString == descriptorId))
        // TODO Status should be an enum
        currentActiveDescriptor = currentEService.descriptors.find(d => d.status == "published") // Must be at most one
        _ <- catalogManagementService.publishDescriptor(bearer)(eServiceId, descriptorId)
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
      } yield ()

    onComplete(result) {
      case Success(_) => publishDescriptor204
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
        response <- catalogManagementService.getEService(bearer)(eServiceId)
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
        response <- catalogManagementService.createEServiceDocument(bearer)(
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
        response <- catalogManagementService.getEServiceDocument(bearer)(eServiceId, descriptorId, documentId)
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
        response <- retrieveEservices(bearer, producerId, consumerId, status)
      } yield response

    onComplete(result) {
      case Success(response) => getFlatEServices200(response.flatMap(convertToFlattenEservice))
      case Failure(ex) =>
        getFlatEServices500(
          Problem(Option(ex.getMessage), 500, s"Unexpected error while retrieving flatted E-Services")
        )
    }
  }

  /** Code: 200, Message: EService Descriptor created., DataType: EServiceDescriptor
    * Code: 400, Message: Invalid input, DataType: Problem
    * Code: 404, Message: Not found, DataType: Problem
    * Code: 500, Message: Not found, DataType: Problem
    */
  override def createDescriptor(eServiceId: String, eServiceDescriptorSeed: EServiceDescriptorSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerEServiceDescriptor: ToEntityMarshaller[EServiceDescriptor],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result =
      for {
        bearer          <- tokenFromContext(contexts)
        currentEService <- catalogManagementService.getEService(bearer)(eServiceId)
        _               <- catalogManagementService.hasNotDraftDescriptor(currentEService)
        createdEServiceDescriptor <- catalogManagementService.createDescriptor(bearer)(
          eServiceId,
          eServiceDescriptorSeed
        )
      } yield createdEServiceDescriptor

    onComplete(result) {
      case Success(res) => createDescriptor200(res)
      case Failure(ex) =>
        val errorResponse: Problem =
          Problem(Option(ex.getMessage), 400, s"Error while creating Descriptor for e-service Id $eServiceId")
        createDescriptor400(errorResponse)
    }
  }

  /** Code: 200, Message: EService Descriptor published, DataType: EService
    * Code: 400, Message: Invalid input, DataType: Problem
    * Code: 404, Message: Not found, DataType: Problem
    * Code: 500, Message: Not found, DataType: Problem
    */
  override def updateDraftDescriptor(
    eServiceId: String,
    descriptorId: String,
    updateEServiceDescriptorSeed: UpdateEServiceDescriptorSeed
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerEService: ToEntityMarshaller[EService]
  ): Route = {
    val result =
      for {
        bearer          <- tokenFromContext(contexts)
        currentEService <- catalogManagementService.getEService(bearer)(eServiceId)
        _               <- isDraftDescriptor(currentEService.descriptors.find(_.id.toString == descriptorId))
        updatedDescriptor <- catalogManagementService.updateDraftDescriptor(bearer)(
          eServiceId,
          descriptorId,
          updateEServiceDescriptorSeed
        )
      } yield updatedDescriptor

    onComplete(result) {
      case Success(res) => updateDraftDescriptor200(res)
      case Failure(ex) =>
        val errorResponse: Problem =
          Problem(Option(ex.getMessage), 400, s"Error while updating draft Descriptor for e-service Id $eServiceId")
        updateDraftDescriptor400(errorResponse)
    }
  }

  /** Code: 200, Message: E-Service updated, DataType: EService
    * Code: 404, Message: E-Service not found, DataType: Problem
    * Code: 400, Message: Bad request, DataType: Problem
    */
  override def updateEServiceById(eServiceId: String, updateEServiceSeed: UpdateEServiceSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerEService: ToEntityMarshaller[EService]
  ): Route = {
    val result =
      for {
        bearer          <- tokenFromContext(contexts)
        updatedEservice <- catalogManagementService.updateEservice(bearer)(eServiceId, updateEServiceSeed)
      } yield updatedEservice

    onComplete(result) {
      case Success(res) => updateEServiceById200(res)
      case Failure(ex) =>
        val errorResponse: Problem =
          Problem(Option(ex.getMessage), 400, s"Error while creating Descriptor for e-service Id $eServiceId")
        createDescriptor400(errorResponse)
    }
  }

  private def retrieveEservices(
    bearer: String,
    producerId: Option[String],
    consumerId: Option[String],
    status: Option[String]
  ): Future[Seq[EService]] = {
    if (consumerId.isEmpty) catalogManagementService.listEServices(bearer)(producerId, status)
    else
      for {
        agreements <- agreementManagementService.getAgreements(bearer, consumerId, producerId, None)
        eservices <- agreements.flatTraverse(agreement =>
          catalogManagementService
            .listEServices(bearer)(producerId = Some(agreement.producerId.toString), status = status)
        )
      } yield eservices
  }

  private[this] def deprecateDescriptorOrCancelPublication(
    bearer: String,
    eServiceId: String,
    descriptorIdToDeprecate: String,
    descriptorIdToCancel: String
  ): Future[Unit] = {
    deprecateDescriptor(descriptorIdToDeprecate, eServiceId, bearer)
      .recoverWith(error =>
        resetDescriptorToDraft(eServiceId, descriptorIdToCancel, bearer)
          .flatMap(_ => Future.failed(error))
      )
  }

  private[this] def deprecateDescriptor(descriptorId: String, eServiceId: String, bearerToken: String): Future[Unit] = {
    catalogManagementService
      .deprecateDescriptor(bearerToken)(eServiceId = eServiceId, descriptorId = descriptorId)
      .recoverWith { case ex =>
        logger.error(s"Unable to deprecate descriptor $descriptorId on E-Service $eServiceId. Reason: ${ex.getMessage}")
        Future.failed(ex)
      }
  }

  private[this] def resetDescriptorToDraft(
    eServiceId: String,
    descriptorId: String,
    bearerToken: String
  ): Future[Unit] = {

    catalogManagementService
      .draftDescriptor(bearerToken)(eServiceId = eServiceId, descriptorId = descriptorId)
      .map { result =>
        logger.info(s"Publication cancelled for descriptor $descriptorId in E-Service $eServiceId")
        result
      }
  }

  private[this] def tokenFromContext(context: Seq[(String, String)]): Future[String] =
    Future.fromTry(
      context
        .find(_._1 == "bearer")
        .map(header => header._2)
        .toRight(new RuntimeException("Bearer Token not provided"))
        .toTry
    )

  private def convertToFlattenEservice(eservice: EService): Seq[FlatEService] = {
    eservice.descriptors.map(descriptor =>
      FlatEService(
        id = eservice.id,
        producerId = eservice.producerId,
        name = eservice.name,
        version = descriptor.version,
        status = descriptor.status,
        descriptorId = descriptor.id.toString
      )
    )
  }

  private def isDraftDescriptor(optDescriptor: Option[EServiceDescriptor]): Future[EServiceDescriptor] = {
    optDescriptor.fold(Future.failed[EServiceDescriptor](DescriptorNotFound(""))) { descriptor =>
      descriptor.status match {
        case "draft" => Future.successful(descriptor)
        case _ =>
          Future.failed(NotValidDescriptor(s"Descriptor ${descriptor.id.toString} has status ${descriptor.status}"))
      }
    }
  }

  /** Code: 204, Message: Document deleted.
    * Code: 404, Message: E-Service descriptor document not found, DataType: Problem
    * Code: 400, Message: Bad request, DataType: Problem
    */
  override def deleteEServiceDocumentById(eServiceId: String, descriptorId: String, documentId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result =
      for {
        bearer <- tokenFromContext(contexts)
        _      <- catalogManagementService.deleteEServiceDocument(bearer)(eServiceId, descriptorId, documentId)
      } yield ()

    onComplete(result) {
      case Success(_) => deleteEServiceDocumentById204
      case Failure(ex: ApiError[_]) if ex.code == 400 =>
        deleteEServiceDocumentById400(
          Problem(
            Option(ex.getMessage),
            400,
            s"Error deleting document $documentId for E-Service $eServiceId and descriptor $descriptorId"
          )
        )
      case Failure(ex: ApiError[_]) if ex.code == 404 =>
        deleteEServiceDocumentById404(
          Problem(
            Option(ex.getMessage),
            404,
            s"Error deleting document $documentId for E-Service $eServiceId and descriptor $descriptorId"
          )
        )
      case Failure(ex) =>
        complete(
          Problem(
            Option(ex.getMessage),
            500,
            s"Error deleting document $documentId for E-Service $eServiceId and descriptor $descriptorId"
          )
        )
    }
  }

  /** Code: 200, Message: EService Descriptor updated., DataType: EServiceDoc
    * Code: 404, Message: EService not found, DataType: Problem
    * Code: 400, Message: Bad request, DataType: Problem
    */
  override def updateEServiceDocumentById(
    eServiceId: String,
    descriptorId: String,
    documentId: String,
    updateEServiceDescriptorDocumentSeed: UpdateEServiceDescriptorDocumentSeed
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerEServiceDoc: ToEntityMarshaller[EServiceDoc],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result =
      for {
        bearer <- tokenFromContext(contexts)
        updatedDocument <- catalogManagementService.updateEServiceDocument(bearer)(
          eServiceId,
          descriptorId,
          documentId,
          updateEServiceDescriptorDocumentSeed
        )
      } yield updatedDocument

    onComplete(result) {
      case Success(updatedDocument) => updateEServiceDocumentById200(updatedDocument)
      case Failure(ex: ApiError[_]) if ex.code == 400 =>
        updateEServiceDocumentById400(
          Problem(
            Option(ex.getMessage),
            400,
            s"Error updating document $documentId for E-Service $eServiceId and descriptor $descriptorId"
          )
        )
      case Failure(ex: ApiError[_]) if ex.code == 404 =>
        updateEServiceDocumentById404(
          Problem(
            Option(ex.getMessage),
            404,
            s"Error updating document $documentId for E-Service $eServiceId and descriptor $descriptorId"
          )
        )
      case Failure(ex) =>
        complete(
          Problem(
            Option(ex.getMessage),
            500,
            s"Error updating document $documentId for E-Service $eServiceId and descriptor $descriptorId"
          )
        )
    }
  }
}
