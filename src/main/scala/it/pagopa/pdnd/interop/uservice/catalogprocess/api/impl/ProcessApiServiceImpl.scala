package it.pagopa.pdnd.interop.uservice.catalogprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.{ContentType, HttpEntity, MessageEntity, StatusCodes}
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FileInfo
import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.Logger
import it.pagopa.pdnd.interop.commons.files.service.FileManager
import it.pagopa.pdnd.interop.commons.jwt.service.JWTReader
import it.pagopa.pdnd.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.pdnd.interop.commons.utils.TypeConversions.{EitherOps, OptionOps}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.Agreement
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.invoker.ApiError
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.{
  EService => ManagementEService,
  EServiceDescriptor => ManagementDescriptor
}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.{model => CatalogManagementDependency}
import it.pagopa.pdnd.interop.uservice.catalogprocess.api.ProcessApiService
import it.pagopa.pdnd.interop.uservice.catalogprocess.api.impl.Converter.convertToApiDescriptorState
import it.pagopa.pdnd.interop.uservice.catalogprocess.common.system.{ApplicationConfiguration, validateBearer}
import it.pagopa.pdnd.interop.uservice.catalogprocess.errors.CatalogProcessErrors._
import it.pagopa.pdnd.interop.uservice.catalogprocess.model._
import it.pagopa.pdnd.interop.uservice.catalogprocess.service._
import it.pagopa.pdnd.interop.uservice.partymanagement.client.model.{BulkOrganizations, BulkPartiesSeed}
import org.slf4j.LoggerFactory

import java.io.{File, FileOutputStream}
import java.nio.file.{Files, Path}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

final case class ProcessApiServiceImpl(
  catalogManagementService: CatalogManagementService,
  partyManagementService: PartyManagementService,
  attributeRegistryManagementService: AttributeRegistryManagementService,
  agreementManagementService: AgreementManagementService,
  fileManager: FileManager,
  jwtReader: JWTReader
)(implicit ec: ExecutionContext)
    extends ProcessApiService {

  import ProcessApiServiceImpl._

  val logger = Logger.takingImplicit[ContextFieldsToLog](LoggerFactory.getLogger(this.getClass))

  /** Code: 200, Message: EService created, DataType: EService
    * Code: 400, Message: Invalid input, DataType: Problem
    */
  override def createEService(eServiceSeed: EServiceSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerEService: ToEntityMarshaller[EService]
  ): Route = {
    logger.info("Creating e-service for producer {} with service name {}", eServiceSeed.producerId, eServiceSeed.name)
    val result =
      for {
        bearer <- validateBearer(contexts, jwtReader)
        clientSeed = Converter.convertToClientEServiceSeed(eServiceSeed)
        createdEService <- catalogManagementService.createEService(bearer)(clientSeed)
        apiEservice     <- convertToApiEservice(bearer, createdEService)
      } yield apiEservice

    onComplete(result) {
      case Success(res) => createEService200(res)
      case Failure(ex) =>
        logger.error(
          "Error while creating e-service for producer {} with service name {}",
          eServiceSeed.producerId,
          eServiceSeed.name,
          ex
        )
        val errorResponse: Problem =
          problemOf(
            StatusCodes.BadRequest,
            EServiceCreationError(s"Error while creating E-Service for producer Id ${eServiceSeed.producerId.toString}")
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
    logger.info("Deleting draft descriptor {} for e-service {}", eServiceId, descriptorId)
    val result =
      for {
        bearer <- validateBearer(contexts, jwtReader)
        _      <- catalogManagementService.deleteDraft(bearer)(eServiceId, descriptorId)
      } yield ()

    onComplete(result) {
      case Success(_) => deleteDraft204
      case Failure(ex: ApiError[_]) if ex.code == 400 =>
        logger.error("Error while deleting draft descriptor {} for e-service {}", eServiceId, descriptorId, ex)
        deleteDraft400(
          problemOf(
            StatusCodes.BadRequest,
            DraftDescriptorDeletionBadRequest(
              s"Error while deleting draft descriptor $descriptorId for E-Service $eServiceId"
            )
          )
        )
      case Failure(ex: ApiError[_]) if ex.code == 404 =>
        logger.error("Error while deleting draft descriptor {} for e-service {}", eServiceId, descriptorId, ex)
        deleteDraft404(
          problemOf(
            StatusCodes.NotFound,
            DraftDescriptorDeletionNotFound(
              s"Error while deleting draft descriptor $descriptorId for E-Service $eServiceId"
            )
          )
        )
      case Failure(ex) =>
        logger.error("Error while deleting draft descriptor {} for e-service {}", eServiceId, descriptorId, ex)
        val error = problemOf(
          StatusCodes.InternalServerError,
          DraftDescriptorDeletionError(
            s"Unexpected error while deleting draft descriptor $descriptorId for E-Service $eServiceId"
          )
        )
        complete(error.status, error)
    }
  }

  /** Code: 200, Message: A list of E-Service, DataType: Seq[EService]
    * Code: 500, Message: Internal Server Error, DataType: Problem
    */
  override def getEServices(producerId: Option[String], consumerId: Option[String], status: Option[String])(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerEServicearray: ToEntityMarshaller[Seq[EService]]
  ): Route = {
    logger.info("Getting e-service with producer = {}, consumer = {} and state = {}", producerId, consumerId, status)
    val result =
      for {
        bearer       <- validateBearer(contexts, jwtReader)
        statusEnum   <- status.traverse(CatalogManagementDependency.EServiceDescriptorState.fromValue).toFuture
        eservices    <- retrieveEservices(bearer, producerId, consumerId, statusEnum)
        apiEservices <- eservices.traverse(service => convertToApiEservice(bearer, service))
      } yield apiEservices

    onComplete(result) {
      case Success(response) => getEServices200(response)
      case Failure(ex) =>
        logger.error(
          "Error while getting e-service with producer = {}, consumer = {} and state = {}",
          producerId,
          consumerId,
          status,
          ex
        )
        val error =
          problemOf(StatusCodes.InternalServerError, EServicesRetrievalError)
        complete(error.status, error)
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
    logger.info("Publishing descriptor {} for eservice {}", descriptorId, eServiceId)
    val result =
      for {
        bearer          <- validateBearer(contexts, jwtReader)
        currentEService <- catalogManagementService.getEService(bearer)(eServiceId)
        descriptor <- currentEService.descriptors
          .find(_.id.toString == descriptorId)
          .toFuture(EServiceDescriptorNotFound(eServiceId, descriptorId))
        _ <- verifyPublicationEligibility(descriptor)
        currentActiveDescriptor = currentEService.descriptors.find(d =>
          d.state == CatalogManagementDependency.EServiceDescriptorState.PUBLISHED
        ) // Must be at most one
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
        logger.error("Error while publishing descriptor {} for eservice {}", descriptorId, eServiceId, ex)
        publishDescriptor400(problemOf(StatusCodes.BadRequest, PublishDescriptorBadRequest(descriptorId, eServiceId)))
      case Failure(ex: ApiError[_]) if ex.code == 404 =>
        logger.error("Error while publishing descriptor {} for eservice {}", descriptorId, eServiceId, ex)
        publishDescriptor404(problemOf(StatusCodes.NotFound, PublishDescriptorNotFound(descriptorId, eServiceId)))
      case Failure(ex) =>
        logger.error("Error while publishing descriptor {} for eservice {}", descriptorId, eServiceId, ex)
        val error =
          problemOf(StatusCodes.InternalServerError, PublishDescriptorError(descriptorId, eServiceId))
        complete(error.status, error)
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
    logger.info("Getting e-service {}", eServiceId)
    val result =
      for {
        bearer      <- validateBearer(contexts, jwtReader)
        eservice    <- catalogManagementService.getEService(bearer)(eServiceId)
        apiEservice <- convertToApiEservice(bearer, eservice)
      } yield apiEservice

    onComplete(result) {
      case Success(response) => getEServiceById200(response)
      case Failure(ex) =>
        logger.error("Error while getting e-service {}", eServiceId, ex)
        val error =
          problemOf(StatusCodes.InternalServerError, EServiceRetrievalError(eServiceId))
        complete(error.status, error)
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
    logger.info(
      "Creating e-service document of kind {} for e-service {} and descriptor {}",
      kind,
      eServiceId,
      descriptorId
    )
    val result =
      for {
        bearer <- validateBearer(contexts, jwtReader)
        eservice <- catalogManagementService.createEServiceDocument(bearer)(
          eServiceId,
          descriptorId,
          kind,
          description,
          doc
        )
        apiEservice <- convertToApiEservice(bearer, eservice)
      } yield apiEservice

    onComplete(result) {
      case Success(response) => createEServiceDocument200(response)
      case Failure(ex: ApiError[_]) if ex.code == 400 =>
        logger.error(
          "Failure in creation of e-service document of kind {} for e-service {} and descriptor {}",
          kind,
          eServiceId,
          descriptorId,
          ex
        )
        createEServiceDocument400(
          problemOf(StatusCodes.BadRequest, CreateDescriptorDocumentBadRequest(descriptorId, eServiceId))
        )
      case Failure(ex: ApiError[_]) if ex.code == 404 =>
        logger.error(
          "Failure in creation of e-service document of kind {} for e-service {} and descriptor {}",
          kind,
          eServiceId,
          descriptorId,
          ex
        )
        createEServiceDocument404(
          problemOf(StatusCodes.NotFound, CreateDescriptorDocumentNotFound(descriptorId, eServiceId))
        )
      case Failure(ex) =>
        logger.error(
          "Failure in creation of e-service document of kind {} for e-service {} and descriptor {}",
          kind,
          eServiceId,
          descriptorId,
          ex
        )
        val error =
          problemOf(StatusCodes.InternalServerError, CreateDescriptorDocumentError(descriptorId, eServiceId))
        complete(error.status, error)
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
    logger.info(
      "Getting e-service document {} for e-service {} and descriptor {}",
      documentId,
      eServiceId,
      descriptorId
    )
    val result: Future[DocumentDetails] =
      for {
        bearer      <- validateBearer(contexts, jwtReader)
        document    <- catalogManagementService.getEServiceDocument(bearer)(eServiceId, descriptorId, documentId)
        contentType <- getDocumentContentType(document)
        response    <- fileManager.get(ApplicationConfiguration.storageContainer)(document.path)
      } yield DocumentDetails(document.name, contentType, response)

    onComplete(result) {
      case Success(documentDetails) =>
        val output: MessageEntity = convertToMessageEntity(documentDetails)
        complete(output)
      case Failure(ex: ApiError[_]) if ex.code == 400 =>
        logger.error(
          "Error while getting e-service document {} for e-service {} and descriptor {}",
          documentId,
          eServiceId,
          descriptorId,
          ex
        )
        getEServiceDocumentById400(
          problemOf(StatusCodes.BadRequest, GetDescriptorDocumentBadRequest(documentId, descriptorId, eServiceId))
        )
      case Failure(ex: ApiError[_]) if ex.code == 404 =>
        logger.error(
          "Error while getting e-service document {} for e-service {} and descriptor {}",
          documentId,
          eServiceId,
          descriptorId,
          ex
        )
        getEServiceDocumentById404(
          problemOf(StatusCodes.NotFound, GetDescriptorDocumentNotFound(documentId, descriptorId, eServiceId))
        )
      case Failure(ex: ContentTypeParsingError) =>
        logger.error(
          "Error while parsing document {} content type for e-service {} and descriptor {} - Content type: {}, errors - {}",
          documentId,
          eServiceId,
          descriptorId,
          ex.contentType,
          ex.errors.mkString(", "),
          ex
        )
        val error = problemOf(StatusCodes.InternalServerError, ex)
        complete(error.status, error)
      case Failure(ex) =>
        logger.error(
          "Error while getting e-service document {} for e-service {} and descriptor {}",
          documentId,
          eServiceId,
          descriptorId,
          ex
        )
        val error =
          problemOf(StatusCodes.InternalServerError, GetDescriptorDocumentError(documentId, descriptorId, eServiceId))
        complete(error.status, error)
    }
  }

  def convertToMessageEntity(documentDetails: DocumentDetails): MessageEntity = {
    val randomPath: Path               = Files.createTempDirectory(s"document")
    val temporaryFilePath: String      = s"${randomPath.toString}/${documentDetails.name}"
    val file: File                     = new File(temporaryFilePath)
    val outputStream: FileOutputStream = new FileOutputStream(file)
    documentDetails.data.writeTo(outputStream)
    HttpEntity.fromFile(documentDetails.contentType, file)
  }

  private def getDocumentContentType(document: CatalogManagementDependency.EServiceDoc): Future[ContentType] = {
    ContentType
      .parse(document.contentType)
      .fold(
        ex => Future.failed(ContentTypeParsingError(document.contentType, document.path, ex.map(_.formatPretty))),
        Future.successful
      )
  }

  /** Code: 200, Message: A list of flattened E-Services, DataType: Seq[FlatEService]
    * Code: 500, Message: Internal Server Error, DataType: Problem
    */
  override def getFlatEServices(
    callerId: String,
    producerId: Option[String],
    consumerId: Option[String],
    status: Option[String],
    latestPublishedOnly: Option[Boolean]
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerFlatEServicearray: ToEntityMarshaller[Seq[FlatEService]]
  ): Route = {
    logger.info(
      "Getting flatten e-services list for caller {} where producer = {}, consumer = {}, state = {} and latest published only = {}",
      callerId,
      producerId,
      consumerId,
      status,
      latestPublishedOnly
    )
    val result =
      for {
        bearer                    <- validateBearer(contexts, jwtReader)
        statusEnum                <- status.traverse(CatalogManagementDependency.EServiceDescriptorState.fromValue).toFuture
        callerSubscribedEservices <- agreementManagementService.getAgreementsByConsumerId(bearer)(callerId)
        retrievedEservices        <- retrieveEservices(bearer, producerId, consumerId, statusEnum)
        eservices                 <- processEservicesWithLatestFilter(retrievedEservices, latestPublishedOnly)
        organizationsDetails <- partyManagementService.getBulkOrganizations(
          BulkPartiesSeed(partyIdentifiers = eservices.map(_.producerId))
        )(bearer)
        flattenServices = eservices.flatMap(service =>
          convertToFlattenEservice(service, callerSubscribedEservices, organizationsDetails)
        )
        filteredDescriptors = flattenServices.filter(item => status.forall(item.state.contains))
      } yield filteredDescriptors

    onComplete(result) {
      case Success(response) => getFlatEServices200(response)
      case Failure(ex) =>
        logger.error(
          "Error while getting flatten e-services list for caller {} where producer = {}, consumer = {}, state = {} and latest published only = {}",
          callerId,
          producerId,
          consumerId,
          status,
          latestPublishedOnly,
          ex
        )
        val error =
          problemOf(StatusCodes.InternalServerError, FlattenedEServicesRetrievalError)
        complete(error.status, error)
    }
  }

  private def processEservicesWithLatestFilter(
    eservices: Seq[CatalogManagementDependency.EService],
    latestOnly: Option[Boolean]
  ): Future[Seq[CatalogManagementDependency.EService]] = {
    latestOnly match {
      case Some(true) =>
        Future.successful(eservices.map(eservice => {
          val latestDescriptor =
            eservice.descriptors
              .filter(d =>
                d.state == CatalogManagementDependency.EServiceDescriptorState.PUBLISHED || d.state == CatalogManagementDependency.EServiceDescriptorState.SUSPENDED
              )
              .sortWith((ver1, ver2) => Ordering[Option[Long]].gt(ver1.version.toLongOption, ver2.version.toLongOption))
              .headOption

          eservice.copy(descriptors =
            latestDescriptor.fold(Seq.empty[CatalogManagementDependency.EServiceDescriptor])(latest => Seq(latest))
          )
        }))
      case _ => Future.successful(eservices)
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
    logger.info("Creating descriptor for e-service {}", eServiceId)
    val result =
      for {
        bearer                    <- validateBearer(contexts, jwtReader)
        currentEService           <- catalogManagementService.getEService(bearer)(eServiceId)
        _                         <- catalogManagementService.hasNotDraftDescriptor(currentEService)
        clientSeed                <- Converter.convertToClientEServiceDescriptorSeed(eServiceDescriptorSeed)
        createdEServiceDescriptor <- catalogManagementService.createDescriptor(bearer)(eServiceId, clientSeed)
      } yield Converter.convertToApiDescriptor(createdEServiceDescriptor)

    onComplete(result) {
      case Success(res) => createDescriptor200(res)
      case Failure(ex) =>
        logger.error("Error while creating descriptor for e-service {}", eServiceId, ex)
        val errorResponse: Problem =
          problemOf(StatusCodes.BadRequest, CreateDescriptorError(eServiceId))
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
    logger.info("Updating draft descriptor {} of e-service {}", descriptorId, eServiceId)
    val result: Future[EService] =
      for {
        bearer          <- validateBearer(contexts, jwtReader)
        currentEService <- catalogManagementService.getEService(bearer)(eServiceId)
        descriptor <- currentEService.descriptors
          .find(_.id.toString == descriptorId)
          .toFuture(EServiceDescriptorNotFound(eServiceId, descriptorId))
        _               <- isDraftDescriptor(descriptor)
        clientSeed      <- Converter.convertToClientUpdateEServiceDescriptorSeed(updateEServiceDescriptorSeed)
        updatedEservice <- catalogManagementService.updateDraftDescriptor(bearer)(eServiceId, descriptorId, clientSeed)
        apiEservice     <- convertToApiEservice(bearer, updatedEservice)
      } yield apiEservice

    onComplete(result) {
      case Success(res) => updateDraftDescriptor200(res)
      case Failure(ex) =>
        logger.error("Error while updating draft descriptor {} of e-service {}", descriptorId, eServiceId, ex)
        val errorResponse: Problem =
          problemOf(StatusCodes.BadRequest, UpdateDraftDescriptorError(eServiceId))
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
    logger.info("Updating e-service by id {}", eServiceId)
    val result =
      for {
        bearer <- validateBearer(contexts, jwtReader)
        clientSeed = Converter.convertToClientUpdateEServiceSeed(updateEServiceSeed)
        updatedEservice <- catalogManagementService.updateEservice(bearer)(eServiceId, clientSeed)
        apiEservice     <- convertToApiEservice(bearer, updatedEservice)
      } yield apiEservice

    onComplete(result) {
      case Success(res) => updateEServiceById200(res)
      case Failure(ex) =>
        logger.error("Error while updating e-service by id {}", eServiceId, ex)
        val errorResponse: Problem =
          problemOf(StatusCodes.BadRequest, UpdateEServiceError(eServiceId))
        createDescriptor400(errorResponse)
    }
  }

  private def convertToApiEservice(bearer: String, eservice: CatalogManagementDependency.EService): Future[EService] = {
    for {
      organization <- partyManagementService.getOrganization(eservice.producerId)(bearer)
      attributes <- attributeRegistryManagementService.getAttributesBulk(extractIdsFromAttributes(eservice.attributes))(
        bearer
      )
    } yield Converter.convertToApiEservice(eservice, organization, attributes)
  }

  private def extractIdsFromAttributes(attributes: CatalogManagementDependency.Attributes): Seq[String] = {
    attributes.certified.flatMap(extractIdsFromAttribute) ++
      attributes.declared.flatMap(extractIdsFromAttribute) ++
      attributes.verified.flatMap(extractIdsFromAttribute)
  }

  private def extractIdsFromAttribute(attribute: CatalogManagementDependency.Attribute): Seq[String] = {
    val fromSingle: Seq[String] = attribute.single.toSeq.map(_.id)
    val fromGroup: Seq[String]  = attribute.group.toSeq.flatMap(_.map(_.id))

    fromSingle ++ fromGroup
  }

  private def retrieveEservices(
    bearer: String,
    producerId: Option[String],
    consumerId: Option[String],
    status: Option[CatalogManagementDependency.EServiceDescriptorState]
  ): Future[Seq[CatalogManagementDependency.EService]] = {
    if (consumerId.isEmpty) catalogManagementService.listEServices(bearer)(producerId, status)
    else
      for {
        agreements <- agreementManagementService.getAgreements(bearer, consumerId, producerId, None)
        eservices <- agreements.traverse(agreement =>
          catalogManagementService
            .getEService(bearer)(eServiceId = agreement.eserviceId.toString)
        )
      } yield eservices.filter(eService =>
        producerId.forall(_ == eService.producerId.toString) &&
          status.forall(s => eService.descriptors.exists(_.state == s))
      )
  }

  private[this] def deprecateDescriptorOrCancelPublication(
    bearer: String,
    eServiceId: String,
    descriptorIdToDeprecate: String,
    descriptorIdToCancel: String
  )(implicit contexts: Seq[(String, String)]): Future[Unit] = {
    deprecateDescriptor(descriptorIdToDeprecate, eServiceId, bearer)
      .recoverWith(error =>
        resetDescriptorToDraft(eServiceId, descriptorIdToCancel, bearer)
          .flatMap(_ => Future.failed(error))
      )
  }

  private[this] def deprecateDescriptor(descriptorId: String, eServiceId: String, bearerToken: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = {
    catalogManagementService
      .deprecateDescriptor(bearerToken)(eServiceId = eServiceId, descriptorId = descriptorId)
      .recoverWith { case ex =>
        logger.error(s"Unable to deprecate descriptor $descriptorId on E-Service $eServiceId.", ex)
        Future.failed(ex)
      }
  }

  private[this] def resetDescriptorToDraft(eServiceId: String, descriptorId: String, bearerToken: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = {

    catalogManagementService
      .draftDescriptor(bearerToken)(eServiceId = eServiceId, descriptorId = descriptorId)
      .map { result =>
        logger.info(s"Publication cancelled for descriptor $descriptorId in E-Service $eServiceId")
        result
      }
  }

  private def convertToFlattenEservice(
    eservice: client.model.EService,
    agreementSubscribedEservices: Seq[Agreement],
    organizationDetails: BulkOrganizations
  ): Seq[FlatEService] = {

    val flatEServiceZero: FlatEService = FlatEService(
      id = eservice.id,
      producerId = eservice.producerId,
      name = eservice.name,
      //TODO "Unknown" is a temporary flag
      producerName = organizationDetails.found
        .find(_.id == eservice.producerId)
        .map(_.description)
        .getOrElse("Unknown"),
      version = None,
      state = None,
      descriptorId = None,
      callerSubscribed = agreementSubscribedEservices.find(agr => agr.eserviceId == eservice.id).map(_.id),
      certifiedAttributes = eservice.attributes.certified.map(toFlatAttribute)
    )

    val flatEServices: Seq[FlatEService] = eservice.descriptors.map { descriptor =>
      flatEServiceZero.copy(
        version = Some(descriptor.version),
        state = Some(convertToApiDescriptorState(descriptor.state)),
        descriptorId = Some(descriptor.id.toString)
      )

    }

    Option(flatEServices).filter(_.nonEmpty).getOrElse(Seq(flatEServiceZero))

  }

  private def toFlatAttribute(attribute: client.model.Attribute): FlatAttribute = {
    FlatAttribute(
      single = attribute.single.map(a => FlatAttributeValue(a.id)),
      group = attribute.group.map(a => a.map(attr => FlatAttributeValue(attr.id)))
    )
  }

  private def descriptorCanBeSuspended(
    descriptor: CatalogManagementDependency.EServiceDescriptor
  ): Future[CatalogManagementDependency.EServiceDescriptor] =
    descriptor.state match {
      case CatalogManagementDependency.EServiceDescriptorState.DEPRECATED => Future.successful(descriptor)
      case CatalogManagementDependency.EServiceDescriptorState.PUBLISHED  => Future.successful(descriptor)
      case _ =>
        Future.failed(NotValidDescriptor(descriptor.id.toString, descriptor.state.toString))
    }

  private def descriptorCanBeActivated(
    descriptor: CatalogManagementDependency.EServiceDescriptor
  ): Future[CatalogManagementDependency.EServiceDescriptor] =
    descriptor.state match {
      case CatalogManagementDependency.EServiceDescriptorState.SUSPENDED => Future.successful(descriptor)
      case _ =>
        Future.failed(NotValidDescriptor(descriptor.id.toString, descriptor.state.toString))
    }

  /** Code: 204, Message: Document deleted.
    * Code: 404, Message: E-Service descriptor document not found, DataType: Problem
    * Code: 400, Message: Bad request, DataType: Problem
    */
  override def deleteEServiceDocumentById(eServiceId: String, descriptorId: String, documentId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info("Delete document {} of descriptor {} for e-service {}", documentId, descriptorId, eServiceId)
    val result =
      for {
        bearer <- validateBearer(contexts, jwtReader)
        _      <- catalogManagementService.deleteEServiceDocument(bearer)(eServiceId, descriptorId, documentId)
      } yield ()

    onComplete(result) {
      case Success(_) => deleteEServiceDocumentById204
      case Failure(ex: ApiError[_]) if ex.code == 400 =>
        logger.error(
          "Error while deleting document {} of descriptor {} for e-service {}",
          documentId,
          descriptorId,
          eServiceId,
          ex
        )
        deleteEServiceDocumentById400(
          problemOf(StatusCodes.BadRequest, DeleteDescriptorDocumentBadRequest(documentId, descriptorId, eServiceId))
        )
      case Failure(ex: ApiError[_]) if ex.code == 404 =>
        logger.error(
          "Error while deleting document {} of descriptor {} for e-service {}",
          documentId,
          descriptorId,
          eServiceId,
          ex
        )
        deleteEServiceDocumentById404(
          problemOf(StatusCodes.NotFound, DeleteDescriptorDocumentNotFound(documentId, descriptorId, eServiceId))
        )
      case Failure(ex) =>
        logger.error(
          "Error while deleting document {} of descriptor {} for e-service {}",
          documentId,
          descriptorId,
          eServiceId,
          ex
        )
        val error = problemOf(
          StatusCodes.InternalServerError,
          DeleteDescriptorDocumentError(documentId, descriptorId, eServiceId)
        )
        complete(error.status, error)
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
    logger.info("Updating e-service by id {}", eServiceId)
    val result =
      for {
        bearer <- validateBearer(contexts, jwtReader)
        clientSeed <-
          Converter.convertToClientEServiceDescriptorDocumentSeed(updateEServiceDescriptorDocumentSeed)
        updatedDocument <- catalogManagementService.updateEServiceDocument(bearer)(
          eServiceId,
          descriptorId,
          documentId,
          clientSeed
        )
      } yield Converter.convertToApiEserviceDoc(updatedDocument)

    onComplete(result) {
      case Success(updatedDocument) => updateEServiceDocumentById200(updatedDocument)
      case Failure(ex: ApiError[_]) if ex.code == 400 =>
        logger.error("Error while updating e-service by id {}", eServiceId, ex)
        updateEServiceDocumentById400(
          problemOf(StatusCodes.BadRequest, UpdateDescriptorDocumentBadRequest(documentId, descriptorId, eServiceId))
        )
      case Failure(ex: ApiError[_]) if ex.code == 404 =>
        logger.error("Error while updating e-service by id {}", eServiceId, ex)
        updateEServiceDocumentById404(
          problemOf(StatusCodes.NotFound, UpdateDescriptorDocumentNotFound(documentId, descriptorId, eServiceId))
        )
      case Failure(ex) =>
        logger.error("Error while updating e-service by id {}", eServiceId, ex)
        val error = problemOf(
          StatusCodes.InternalServerError,
          UpdateDescriptorDocumentError(documentId, descriptorId, eServiceId)
        )
        complete(error.status, error)
    }
  }

  /** Code: 200, Message: Cloned EService with a new draft descriptor updated., DataType: EService
    * Code: 400, Message: Invalid input, DataType: Problem
    * Code: 404, Message: Not found, DataType: Problem
    * Code: 500, Message: Internal Server Error, DataType: Problem
    */
  override def cloneEServiceByDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerEService: ToEntityMarshaller[EService]
  ): Route = {
    logger.info("Cloning descriptor {} of e-service {}", descriptorId, eServiceId)
    val result =
      for {
        bearer         <- validateBearer(contexts, jwtReader)
        clonedEService <- catalogManagementService.cloneEservice(bearer)(eServiceId, descriptorId)
        apiEservice    <- convertToApiEservice(bearer, clonedEService)
      } yield apiEservice

    onComplete(result) {
      case Success(res) => cloneEServiceByDescriptor200(res)
      case Failure(ex) =>
        logger.error("Error while cloning descriptor {} of e-service {}", descriptorId, eServiceId, ex)
        val errorResponse: Problem =
          problemOf(StatusCodes.BadRequest, CloneDescriptorError(descriptorId, eServiceId))
        cloneEServiceByDescriptor400(errorResponse)
    }
  }

  /** Code: 204, Message: EService deleted
    * Code: 400, Message: Invalid input, DataType: Problem
    * Code: 404, Message: Not found, DataType: Problem
    */
  override def deleteEService(
    eServiceId: String
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route = {
    logger.info("Deleting e-service {}", eServiceId)
    val result =
      for {
        bearer <- validateBearer(contexts, jwtReader)
        _      <- catalogManagementService.deleteEService(bearer)(eServiceId)
      } yield ()

    onComplete(result) {
      case Success(_) => deleteEService204
      case Failure(ex) =>
        logger.error("Error while deleting e-service {}", eServiceId, ex)
        val error =
          problemOf(StatusCodes.InternalServerError, EServiceDeletionError(eServiceId))
        complete(error.status, error)
    }
  }

  /** Code: 204, Message: E-Service Descriptor activated
    * Code: 400, Message: Invalid input, DataType: Problem
    * Code: 404, Message: Not found, DataType: Problem
    */
  override def activateDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info("Activating descriptor {} for e-service {}", descriptorId, eServiceId)
    def activateDescriptor(
      bearer: String
    )(eService: ManagementEService, descriptor: ManagementDescriptor): Future[Unit] = {
      val validState =
        Seq(
          CatalogManagementDependency.EServiceDescriptorState.SUSPENDED,
          CatalogManagementDependency.EServiceDescriptorState.DEPRECATED,
          CatalogManagementDependency.EServiceDescriptorState.PUBLISHED
        )
      val mostRecentValidVersion =
        eService.descriptors
          .filter(d => validState.contains(d.state))
          .map(_.version.toInt)
          .sorted(Ordering.Int.reverse)
          .headOption

      mostRecentValidVersion match {
        case Some(version) if version == descriptor.version.toInt =>
          catalogManagementService.publishDescriptor(bearer)(eServiceId, descriptorId)
        case _ =>
          catalogManagementService.deprecateDescriptor(bearer)(eServiceId, descriptorId)
      }
    }

    val result =
      for {
        bearer   <- validateBearer(contexts, jwtReader)
        eService <- catalogManagementService.getEService(bearer)(eServiceId)
        descriptor <- eService.descriptors
          .find(_.id.toString == descriptorId)
          .toFuture(EServiceDescriptorNotFound(eServiceId, descriptorId))
        _ <- descriptorCanBeActivated(descriptor)
        _ <- activateDescriptor(bearer)(eService, descriptor)
      } yield ()

    onComplete(result) {
      case Success(_) => activateDescriptor204
      case Failure(ex: NotValidDescriptor) =>
        logger.error("Activating descriptor {} for e-service {}", descriptorId, eServiceId, ex)
        activateDescriptor400(problemOf(StatusCodes.BadRequest, ex))
      case Failure(ex: ApiError[_]) if ex.code == 400 =>
        logger.error("Activating descriptor {} for e-service {}", descriptorId, eServiceId, ex)
        activateDescriptor400(
          problemOf(StatusCodes.BadRequest, ActivateDescriptorDocumentBadRequest(descriptorId, eServiceId))
        )
      case Failure(ex: ApiError[_]) if ex.code == 404 =>
        logger.error("Activating descriptor {} for e-service {}", descriptorId, eServiceId, ex)
        activateDescriptor404(
          problemOf(StatusCodes.NotFound, ActivateDescriptorDocumentNotFound(descriptorId, eServiceId))
        )
      case Failure(ex) =>
        logger.error("Activating descriptor {} for e-service {}", descriptorId, eServiceId, ex)
        val error =
          problemOf(StatusCodes.InternalServerError, ActivateDescriptorDocumentError(descriptorId, eServiceId))
        complete(error.status, error)
    }
  }

  /** Code: 204, Message: E-Service Descriptor suspended
    * Code: 400, Message: Invalid input, DataType: Problem
    * Code: 404, Message: Not found, DataType: Problem
    */
  override def suspendDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info("Suspending descriptor {} of e-service {}", descriptorId, eServiceId)
    val result =
      for {
        bearer   <- validateBearer(contexts, jwtReader)
        eService <- catalogManagementService.getEService(bearer)(eServiceId)
        descriptor <- eService.descriptors
          .find(_.id.toString == descriptorId)
          .toFuture(EServiceDescriptorNotFound(eServiceId, descriptorId))
        _ <- descriptorCanBeSuspended(descriptor)
        _ <- catalogManagementService.suspendDescriptor(bearer)(eServiceId, descriptorId)
      } yield ()

    onComplete(result) {
      case Success(_) => suspendDescriptor204
      case Failure(ex: NotValidDescriptor) =>
        logger.error("Error during suspension of descriptor {} of e-service {}", descriptorId, eServiceId, ex)
        suspendDescriptor400(problemOf(StatusCodes.BadRequest, ex))
      case Failure(ex: ApiError[_]) if ex.code == 400 =>
        logger.error("Error during suspension of descriptor {} of e-service {}", descriptorId, eServiceId, ex)
        suspendDescriptor400(
          problemOf(StatusCodes.BadRequest, SuspendDescriptorDocumentBadRequest(descriptorId, eServiceId))
        )
      case Failure(ex: ApiError[_]) if ex.code == 404 =>
        logger.error("Error during suspension of descriptor {} of e-service {}", descriptorId, eServiceId, ex)
        suspendDescriptor404(
          problemOf(StatusCodes.NotFound, SuspendDescriptorDocumentNotFound(descriptorId, eServiceId))
        )
      case Failure(ex) =>
        logger.error("Error during suspension of descriptor {} of e-service {}", descriptorId, eServiceId, ex)
        val error = problemOf(StatusCodes.InternalServerError, SuspendDescriptorDocumentError(descriptorId, eServiceId))
        complete(error.status, error)
    }
  }
}

object ProcessApiServiceImpl {
  def verifyPublicationEligibility(
    descriptor: CatalogManagementDependency.EServiceDescriptor
  )(implicit ec: ExecutionContext): Future[Unit] = {
    for {
      _ <- isDraftDescriptor(descriptor)
      _ <- descriptor.interface.toFuture(EServiceDescriptorWithoutInterface(descriptor.id.toString))
    } yield ()
  }

  def isDraftDescriptor(
    descriptor: CatalogManagementDependency.EServiceDescriptor
  ): Future[CatalogManagementDependency.EServiceDescriptor] =
    descriptor.state match {
      case CatalogManagementDependency.EServiceDescriptorState.DRAFT => Future.successful(descriptor)
      case _ =>
        Future.failed(NotValidDescriptor(descriptor.id.toString, descriptor.state.toString))
    }
}
