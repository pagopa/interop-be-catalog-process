package it.pagopa.interop.catalogprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.{ContentType, HttpEntity, MessageEntity, StatusCodes}
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FileInfo
import cats.implicits.toTraverseOps
import cats.syntax.all._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagementDependency}
import it.pagopa.interop.authorizationmanagement.client.{model => AuthorizationManagementDependency}
import it.pagopa.interop.catalogmanagement.client
import it.pagopa.interop.catalogmanagement.client.invoker.ApiError
import it.pagopa.interop.catalogmanagement.client.model.{
  EService => ManagementEService,
  EServiceDescriptor => ManagementDescriptor
}
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagementDependency}
import it.pagopa.interop.catalogprocess.api.ProcessApiService
import it.pagopa.interop.catalogprocess.api.impl.Converter.{convertToApiAgreementState, convertToApiDescriptorState}
import it.pagopa.interop.catalogprocess.common.system.ApplicationConfiguration
import it.pagopa.interop.catalogprocess.errors.CatalogProcessErrors._
import it.pagopa.interop.catalogprocess.model._
import it.pagopa.interop.catalogprocess.service._
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.jwt._
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.interop.commons.utils.TypeConversions.{EitherOps, OptionOps}
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.OperationForbidden
import it.pagopa.interop.selfcare.partymanagement.client.model.{BulkInstitutions, BulkPartiesSeed}

import java.io.{File, FileOutputStream}
import java.nio.file.{Files, Path}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

final case class ProcessApiServiceImpl(
  catalogManagementService: CatalogManagementService,
  partyManagementService: PartyManagementService,
  attributeRegistryManagementService: AttributeRegistryManagementService,
  agreementManagementService: AgreementManagementService,
  authorizationManagementService: AuthorizationManagementService,
  fileManager: FileManager,
  jwtReader: JWTReader
)(implicit ec: ExecutionContext)
    extends ProcessApiService {

  import ProcessApiServiceImpl._

  val logger: LoggerTakingImplicit[ContextFieldsToLog] = Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  private[this] def authorize(roles: String*)(
    route: => Route
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
    authorizeInterop(hasPermissions(roles: _*), problemOf(StatusCodes.Forbidden, OperationForbidden)) {
      route
    }

  /** Code: 200, Message: EService created, DataType: EService
    * Code: 400, Message: Invalid input, DataType: Problem
    */
  override def createEService(eServiceSeed: EServiceSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerEService: ToEntityMarshaller[EService]
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    logger.info("Creating e-service for producer {} with service name {}", eServiceSeed.producerId, eServiceSeed.name)
    val clientSeed: CatalogManagementDependency.EServiceSeed = Converter.convertToClientEServiceSeed(eServiceSeed)
    val result                                               = for {
      createdEService <- catalogManagementService.createEService(clientSeed)
      apiEservice     <- convertToApiEservice(createdEService)
    } yield apiEservice

    onComplete(result) {
      case Success(res) => createEService200(res)
      case Failure(ex)  =>
        logger.error(
          s"Error while creating e-service for producer ${eServiceSeed.producerId} with service name ${eServiceSeed.name}",
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
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    logger.info("Deleting draft descriptor {} for e-service {}", eServiceId, descriptorId)

    def deleteEServiceIfEmpty(eService: CatalogManagementDependency.EService): Future[Unit] =
      if (eService.descriptors.exists(_.id.toString != descriptorId))
        Future.unit
      else
        catalogManagementService.deleteEService(eServiceId)

    val result = for {
      eService <- catalogManagementService.getEService(eServiceId)
      result   <- catalogManagementService.deleteDraft(eServiceId, descriptorId)
      _        <- deleteEServiceIfEmpty(eService)
    } yield result

    onComplete(result) {
      case Success(_)                                 => deleteDraft204
      case Failure(ex: ApiError[_]) if ex.code == 400 =>
        logger.error(s"Error while deleting draft descriptor $descriptorId for e-service $eServiceId", ex)
        deleteDraft400(
          problemOf(
            StatusCodes.BadRequest,
            DraftDescriptorDeletionBadRequest(
              s"Error while deleting draft descriptor $descriptorId for E-Service $eServiceId"
            )
          )
        )
      case Failure(ex: ApiError[_]) if ex.code == 404 =>
        logger.error(s"Error while deleting draft descriptor $descriptorId for e-service $eServiceId", ex)
        deleteDraft404(
          problemOf(
            StatusCodes.NotFound,
            DraftDescriptorDeletionNotFound(
              s"Error while deleting draft descriptor $descriptorId for E-Service $eServiceId"
            )
          )
        )
      case Failure(ex)                                =>
        logger.error(s"Error while deleting draft descriptor $descriptorId for e-service $eServiceId", ex)
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
  override def getEServices(
    producerId: Option[String],
    consumerId: Option[String],
    agreementState: String,
    status: Option[String]
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerEServicearray: ToEntityMarshaller[Seq[EService]]
  ): Route = authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE, M2M_ROLE) {
    logger.info("Getting e-service with producer = {}, consumer = {} and state = {}", producerId, consumerId, status)
    val result = for {
      statusEnum      <- status.traverse(CatalogManagementDependency.EServiceDescriptorState.fromValue).toFuture
      agreementStates <- parseArrayParameters(agreementState).traverse(AgreementState.fromValue).toFuture
      eservices       <- retrieveEservices(producerId, consumerId, statusEnum, agreementStates)
      apiEservices    <- Future.traverse(eservices)(service => convertToApiEservice(service))
    } yield apiEservices

    onComplete(result) {
      case Success(response) => getEServices200(response)
      case Failure(ex)       =>
        logger.error(
          s"Error while getting e-service with producer = $producerId, consumer = $consumerId and state = $status",
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
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    logger.info("Publishing descriptor {} for eservice {}", descriptorId, eServiceId)
    val result = for {
      currentEService <- catalogManagementService.getEService(eServiceId)
      descriptor      <- currentEService.descriptors
        .find(_.id.toString == descriptorId)
        .toFuture(EServiceDescriptorNotFound(eServiceId, descriptorId))
      _               <- verifyPublicationEligibility(descriptor)
      currentActiveDescriptor = currentEService.descriptors.find(d =>
        d.state == CatalogManagementDependency.EServiceDescriptorState.PUBLISHED
      ) // Must be at most one
      _ <- catalogManagementService.publishDescriptor(eServiceId, descriptorId)
      _ <- currentActiveDescriptor
        .map(oldDescriptor =>
          deprecateDescriptorOrCancelPublication(
            eServiceId = eServiceId,
            descriptorIdToDeprecate = oldDescriptor.id.toString,
            descriptorIdToCancel = descriptorId
          )
        )
        .sequence
      _ <- authorizationManagementService.updateStateOnClients(
        currentEService.id,
        descriptor.id,
        AuthorizationManagementDependency.ClientComponentState.ACTIVE,
        descriptor.audience,
        descriptor.voucherLifespan
      )
    } yield ()

    onComplete(result) {
      case Success(_)                                      => publishDescriptor204
      case Failure(ex: ApiError[_]) if ex.code == 400      =>
        logger.error(s"Error while publishing descriptor $descriptorId for eservice $eServiceId", ex)
        publishDescriptor400(problemOf(StatusCodes.BadRequest, PublishDescriptorBadRequest(descriptorId, eServiceId)))
      case Failure(ex: ApiError[_]) if ex.code == 404      =>
        logger.error(s"Error while publishing descriptor $descriptorId for eservice $eServiceId", ex)

        publishDescriptor404(problemOf(StatusCodes.NotFound, PublishDescriptorNotFound(descriptorId, eServiceId)))
      case Failure(ex: EServiceDescriptorWithoutInterface) =>
        logger.error(s"Error while publishing descriptor $descriptorId for eservice $eServiceId", ex)

        publishDescriptor400(problemOf(StatusCodes.BadRequest, ex))
      case Failure(ex)                                     =>
        logger.error(s"Error while publishing descriptor $descriptorId for eservice $eServiceId", ex)
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
  ): Route = authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE, M2M_ROLE) {
    logger.info("Getting e-service {}", eServiceId)
    val result = for {
      eservice    <- catalogManagementService.getEService(eServiceId)
      apiEservice <- convertToApiEservice(eservice)
    } yield apiEservice

    onComplete(result) {
      case Success(response) => getEServiceById200(response)
      case Failure(ex)       =>
        logger.error(s"Error while getting e-service $eServiceId", ex)
        val error =
          problemOf(StatusCodes.InternalServerError, EServiceRetrievalError(eServiceId))
        complete(error.status, error)
    }
  }

  /**
    * Code: 200, Message: EService Document created, DataType: EService
    * Code: 400, Message: Invalid input, DataType: Problem
    * Code: 404, Message: Not found, DataType: Problem
    */
  override def createEServiceDocument(
    kind: String,
    prettyName: String,
    doc: (FileInfo, File),
    eServiceId: String,
    descriptorId: String
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerEService: ToEntityMarshaller[EService]
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    logger.info(
      "Creating e-service document of kind {} for e-service {} and descriptor {}",
      kind,
      eServiceId,
      descriptorId
    )
    val result = for {
      eservice    <- catalogManagementService.createEServiceDocument(eServiceId, descriptorId, kind, prettyName, doc)
      apiEservice <- convertToApiEservice(eservice)
    } yield apiEservice

    onComplete(result) {
      case Success(response)                          => createEServiceDocument200(response)
      case Failure(ex: ApiError[_]) if ex.code == 400 =>
        logger.error(
          s"Failure in creation of e-service document of kind $kind for e-service $eServiceId and descriptor $descriptorId",
          ex
        )
        createEServiceDocument400(
          problemOf(StatusCodes.BadRequest, CreateDescriptorDocumentBadRequest(descriptorId, eServiceId))
        )
      case Failure(ex: ApiError[_]) if ex.code == 404 =>
        logger.error(
          s"Failure in creation of e-service document of kind $kind for e-service $eServiceId and descriptor $descriptorId",
          ex
        )
        createEServiceDocument404(
          problemOf(StatusCodes.NotFound, CreateDescriptorDocumentNotFound(descriptorId, eServiceId))
        )
      case Failure(ex)                                =>
        logger.error(
          s"Failure in creation of e-service document of kind $kind for e-service $eServiceId and descriptor $descriptorId",
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
  ): Route = authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE, M2M_ROLE) {
    logger.info(
      "Getting e-service document {} for e-service {} and descriptor {}",
      documentId,
      eServiceId,
      descriptorId
    )
    val result: Future[DocumentDetails] = for {
      document    <- catalogManagementService.getEServiceDocument(eServiceId, descriptorId, documentId)
      contentType <- getDocumentContentType(document)
      response    <- fileManager.get(ApplicationConfiguration.storageContainer)(document.path)
    } yield DocumentDetails(document.name, contentType, response)

    onComplete(result) {
      case Success(documentDetails)                   =>
        val output: MessageEntity = convertToMessageEntity(documentDetails)
        complete(output)
      case Failure(ex: ApiError[_]) if ex.code == 400 =>
        logger.error(
          s"Error while getting e-service document $documentId for e-service $eServiceId and descriptor $descriptorId",
          ex
        )
        getEServiceDocumentById400(
          problemOf(StatusCodes.BadRequest, GetDescriptorDocumentBadRequest(documentId, descriptorId, eServiceId))
        )
      case Failure(ex: ApiError[_]) if ex.code == 404 =>
        logger.error(
          s"Error while getting e-service document $documentId for e-service $eServiceId and descriptor $descriptorId",
          ex
        )
        getEServiceDocumentById404(
          problemOf(StatusCodes.NotFound, GetDescriptorDocumentNotFound(documentId, descriptorId, eServiceId))
        )
      case Failure(ex: ContentTypeParsingError)       =>
        logger.error(
          s"Error while parsing document $documentId content type for e-service $eServiceId and descriptor $descriptorId - Content type: ${ex.contentType}, errors - ${ex.errors
              .mkString(", ")}",
          ex
        )
        val error = problemOf(StatusCodes.InternalServerError, ex)
        complete(error.status, error)
      case Failure(ex)                                =>
        logger.error(
          s"Error while getting e-service document $documentId for e-service $eServiceId and descriptor $descriptorId",
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

  private def getDocumentContentType(document: CatalogManagementDependency.EServiceDoc): Future[ContentType] =
    ContentType
      .parse(document.contentType)
      .fold(
        ex => Future.failed(ContentTypeParsingError(document.contentType, document.path, ex.map(_.formatPretty))),
        Future.successful
      )

  /** Code: 200, Message: A list of flattened E-Services, DataType: Seq[FlatEService]
    * Code: 500, Message: Internal Server Error, DataType: Problem
    */
  override def getFlatEServices(
    callerId: String,
    producerId: Option[String],
    consumerId: Option[String],
    agreementState: String,
    state: Option[String],
    latestPublishedOnly: Option[Boolean]
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerFlatEServicearray: ToEntityMarshaller[Seq[FlatEService]]
  ): Route = authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE, M2M_ROLE) {
    logger.info(
      "Getting flatten e-services list for caller {} where producer = {}, consumer = {}, state = {} and latest published only = {}",
      callerId,
      producerId,
      consumerId,
      state,
      latestPublishedOnly
    )
    val result = for {
      statusEnum <- state.traverse(CatalogManagementDependency.EServiceDescriptorState.fromValue).toFuture
      callerSubscribedEservices <- agreementManagementService.getAgreementsByConsumerId(callerId)
      agreementStates           <- parseArrayParameters(agreementState).traverse(AgreementState.fromValue).toFuture
      retrievedEservices        <- retrieveEservices(producerId, consumerId, statusEnum, agreementStates)
      eservices                 <- processEservicesWithLatestFilter(retrievedEservices, latestPublishedOnly)
      organizationsDetails      <- partyManagementService.getBulkInstitutions(
        BulkPartiesSeed(partyIdentifiers = eservices.map(_.producerId))
      )
      flattenServices = eservices.flatMap(service =>
        convertToFlattenEservice(service, callerSubscribedEservices, organizationsDetails)
      )
      stateProcessEnum <- state.traverse(EServiceDescriptorState.fromValue).toFuture
      filteredDescriptors = flattenServices.filter(item => stateProcessEnum.forall(item.state.contains))
    } yield filteredDescriptors

    onComplete(result) {
      case Success(response) => getFlatEServices200(response)
      case Failure(ex)       =>
        logger.error(
          s"Error while getting flatten e-services list for caller $callerId where producer = $producerId, consumer = $consumerId, state = $state and latest published only = $latestPublishedOnly",
          ex
        )
        val error = problemOf(StatusCodes.InternalServerError, FlattenedEServicesRetrievalError)
        complete(error.status, error)
    }
  }

  private def processEservicesWithLatestFilter(
    eservices: Seq[CatalogManagementDependency.EService],
    latestOnly: Option[Boolean]
  ): Future[Seq[CatalogManagementDependency.EService]] = latestOnly match {
    case Some(true) =>
      Future.successful(eservices.map(eservice => {
        val latestDescriptor = eservice.descriptors
          .filter(d =>
            d.state == CatalogManagementDependency.EServiceDescriptorState.PUBLISHED || d.state == CatalogManagementDependency.EServiceDescriptorState.SUSPENDED
          )
          .sortWith((ver1, ver2) => Ordering[Option[Long]].gt(ver1.version.toLongOption, ver2.version.toLongOption))
          .headOption

        eservice.copy(descriptors =
          latestDescriptor.fold(Seq.empty[CatalogManagementDependency.EServiceDescriptor])(latest => Seq(latest))
        )
      }))
    case _          => Future.successful(eservices)
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
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    logger.info("Creating descriptor for e-service {}", eServiceId)
    val result = for {
      currentEService           <- catalogManagementService.getEService(eServiceId)
      _                         <- catalogManagementService.hasNotDraftDescriptor(currentEService)
      clientSeed                <- Converter.convertToClientEServiceDescriptorSeed(eServiceDescriptorSeed)
      createdEServiceDescriptor <- catalogManagementService.createDescriptor(eServiceId, clientSeed)
    } yield Converter.convertToApiDescriptor(createdEServiceDescriptor)

    onComplete(result) {
      case Success(res) => createDescriptor200(res)
      case Failure(ex)  =>
        logger.error(s"Error while creating descriptor for e-service $eServiceId", ex)
        val errorResponse: Problem = problemOf(StatusCodes.BadRequest, CreateDescriptorError(eServiceId))
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
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    logger.info("Updating draft descriptor {} of e-service {}", descriptorId, eServiceId)
    val result: Future[EService] = for {
      currentEService <- catalogManagementService.getEService(eServiceId)
      descriptor      <- currentEService.descriptors
        .find(_.id.toString == descriptorId)
        .toFuture(EServiceDescriptorNotFound(eServiceId, descriptorId))
      _               <- isDraftDescriptor(descriptor)
      clientSeed      <- Converter.convertToClientUpdateEServiceDescriptorSeed(updateEServiceDescriptorSeed)
      updatedEservice <- catalogManagementService.updateDraftDescriptor(eServiceId, descriptorId, clientSeed)
      apiEservice     <- convertToApiEservice(updatedEservice)
    } yield apiEservice

    onComplete(result) {
      case Success(res) => updateDraftDescriptor200(res)
      case Failure(ex)  =>
        logger.error(s"Error while updating draft descriptor $descriptorId of e-service $eServiceId", ex)
        val errorResponse: Problem = problemOf(StatusCodes.BadRequest, UpdateDraftDescriptorError(eServiceId))
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
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    logger.info("Updating e-service by id {}", eServiceId)
    val clientSeed: CatalogManagementDependency.UpdateEServiceSeed =
      Converter.convertToClientUpdateEServiceSeed(updateEServiceSeed)

    val result = for {
      updatedEservice <- catalogManagementService.updateEservice(eServiceId, clientSeed)
      apiEservice     <- convertToApiEservice(updatedEservice)
    } yield apiEservice

    onComplete(result) {
      case Success(res) => updateEServiceById200(res)
      case Failure(ex)  =>
        logger.error(s"Error while updating e-service by id $eServiceId", ex)
        val errorResponse: Problem = problemOf(StatusCodes.BadRequest, UpdateEServiceError(eServiceId))
        createDescriptor400(errorResponse)
    }
  }

  private def convertToApiEservice(
    eservice: CatalogManagementDependency.EService
  )(implicit contexts: Seq[(String, String)]): Future[EService] = for {
    organization <- partyManagementService.getInstitution(eservice.producerId)
    attributes   <- attributeRegistryManagementService.getAttributesBulk(extractIdsFromAttributes(eservice.attributes))(
      contexts
    )
  } yield Converter.convertToApiEservice(eservice, organization, attributes)

  private def extractIdsFromAttributes(attributes: CatalogManagementDependency.Attributes): Seq[UUID] =
    attributes.certified.flatMap(extractIdsFromAttribute) ++
      attributes.declared.flatMap(extractIdsFromAttribute) ++
      attributes.verified.flatMap(extractIdsFromAttribute)

  private def extractIdsFromAttribute(attribute: CatalogManagementDependency.Attribute): Seq[UUID] = {
    val fromSingle: Seq[UUID] = attribute.single.toSeq.map(_.id)
    val fromGroup: Seq[UUID]  = attribute.group.toSeq.flatMap(_.map(_.id))

    fromSingle ++ fromGroup
  }

  private def retrieveEservices(
    producerId: Option[String],
    consumerId: Option[String],
    status: Option[CatalogManagementDependency.EServiceDescriptorState],
    agreementStates: List[AgreementState]
  )(implicit contexts: Seq[(String, String)]): Future[Seq[CatalogManagementDependency.EService]] =
    if (agreementStates.isEmpty && consumerId.isEmpty)
      catalogManagementService.listEServices(producerId, status)
    else
      for {
        agreements <- agreementManagementService.getAgreements(
          consumerId,
          producerId,
          agreementStates.distinct.map(convertToApiAgreementState)
        )
        ids = agreements.map(_.eserviceId.toString).distinct
        eservices <- Future.traverse(ids)(catalogManagementService.getEService(_))
      } yield eservices.filter(eService =>
        producerId.forall(_ == eService.producerId.toString) &&
          status.forall(s => eService.descriptors.exists(_.state == s))
      )

  private[this] def deprecateDescriptorOrCancelPublication(
    eServiceId: String,
    descriptorIdToDeprecate: String,
    descriptorIdToCancel: String
  )(implicit contexts: Seq[(String, String)]): Future[Unit] = deprecateDescriptor(descriptorIdToDeprecate, eServiceId)
    .recoverWith(error => resetDescriptorToDraft(eServiceId, descriptorIdToCancel).flatMap(_ => Future.failed(error)))

  private[this] def deprecateDescriptor(descriptorId: String, eServiceId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = catalogManagementService
    .deprecateDescriptor(eServiceId = eServiceId, descriptorId = descriptorId)
    .recoverWith { case ex =>
      logger.error(s"Unable to deprecate descriptor $descriptorId on E-Service $eServiceId", ex)
      Future.failed(ex)
    }

  private[this] def resetDescriptorToDraft(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = catalogManagementService
    .draftDescriptor(eServiceId = eServiceId, descriptorId = descriptorId)
    .map { result =>
      logger.info(s"Publication cancelled for descriptor $descriptorId in E-Service $eServiceId")
      result
    }

  private def convertToFlattenEservice(
    eservice: client.model.EService,
    agreementSubscribedEservices: Seq[AgreementManagementDependency.Agreement],
    institutionsDetails: BulkInstitutions
  ): Seq[FlatEService] = {
    val flatEServiceZero: FlatEService = FlatEService(
      id = eservice.id,
      producerId = eservice.producerId,
      name = eservice.name,
      // TODO "Unknown" is a temporary flag
      producerName = institutionsDetails.found
        .find(_.id == eservice.producerId)
        .map(_.description)
        .getOrElse("Unknown"),
      version = None,
      state = None,
      descriptorId = None,
      description = eservice.description,
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

  private def toFlatAttribute(attribute: client.model.Attribute): FlatAttribute = FlatAttribute(
    single = attribute.single.map(a => FlatAttributeValue(a.id)),
    group = attribute.group.map(a => a.map(attr => FlatAttributeValue(attr.id)))
  )

  private def descriptorCanBeSuspended(
    descriptor: CatalogManagementDependency.EServiceDescriptor
  ): Future[CatalogManagementDependency.EServiceDescriptor] = descriptor.state match {
    case CatalogManagementDependency.EServiceDescriptorState.DEPRECATED => Future.successful(descriptor)
    case CatalogManagementDependency.EServiceDescriptorState.PUBLISHED  => Future.successful(descriptor)
    case _ => Future.failed(NotValidDescriptor(descriptor.id.toString, descriptor.state.toString))
  }

  private def descriptorCanBeActivated(
    descriptor: CatalogManagementDependency.EServiceDescriptor
  ): Future[CatalogManagementDependency.EServiceDescriptor] = descriptor.state match {
    case CatalogManagementDependency.EServiceDescriptorState.SUSPENDED => Future.successful(descriptor)
    case _ => Future.failed(NotValidDescriptor(descriptor.id.toString, descriptor.state.toString))
  }

  /** Code: 204, Message: Document deleted.
    * Code: 404, Message: E-Service descriptor document not found, DataType: Problem
    * Code: 400, Message: Bad request, DataType: Problem
    */
  override def deleteEServiceDocumentById(eServiceId: String, descriptorId: String, documentId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    logger.info("Delete document {} of descriptor {} for e-service {}", documentId, descriptorId, eServiceId)
    val result = catalogManagementService.deleteEServiceDocument(eServiceId, descriptorId, documentId)

    onComplete(result) {
      case Success(_)                                 => deleteEServiceDocumentById204
      case Failure(ex: ApiError[_]) if ex.code == 400 =>
        logger.error(
          s"Error while deleting document $documentId of descriptor $descriptorId for e-service $eServiceId",
          ex
        )
        deleteEServiceDocumentById400(
          problemOf(StatusCodes.BadRequest, DeleteDescriptorDocumentBadRequest(documentId, descriptorId, eServiceId))
        )
      case Failure(ex: ApiError[_]) if ex.code == 404 =>
        logger.error(
          s"Error while deleting document $documentId of descriptor $descriptorId for e-service $eServiceId",
          ex
        )
        deleteEServiceDocumentById404(
          problemOf(StatusCodes.NotFound, DeleteDescriptorDocumentNotFound(documentId, descriptorId, eServiceId))
        )
      case Failure(ex)                                =>
        logger.error(
          s"Error while deleting document $documentId of descriptor $descriptorId for e-service $eServiceId",
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
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    logger.info("Updating e-service by id {}", eServiceId)
    val result = for {
      clientSeed      <-
        Converter.convertToClientEServiceDescriptorDocumentSeed(updateEServiceDescriptorDocumentSeed)
      updatedDocument <- catalogManagementService.updateEServiceDocument(
        eServiceId,
        descriptorId,
        documentId,
        clientSeed
      )
    } yield Converter.convertToApiEserviceDoc(updatedDocument)

    onComplete(result) {
      case Success(updatedDocument)                   => updateEServiceDocumentById200(updatedDocument)
      case Failure(ex: ApiError[_]) if ex.code == 400 =>
        logger.error(s"Error while updating e-service by id $eServiceId", ex)
        updateEServiceDocumentById400(
          problemOf(StatusCodes.BadRequest, UpdateDescriptorDocumentBadRequest(documentId, descriptorId, eServiceId))
        )
      case Failure(ex: ApiError[_]) if ex.code == 404 =>
        logger.error(s"Error while updating e-service by id $eServiceId", ex)
        updateEServiceDocumentById404(
          problemOf(StatusCodes.NotFound, UpdateDescriptorDocumentNotFound(documentId, descriptorId, eServiceId))
        )
      case Failure(ex)                                =>
        logger.error(s"Error while updating e-service by id $eServiceId", ex)
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
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    logger.info("Cloning descriptor {} of e-service {}", descriptorId, eServiceId)
    val result = for {
      clonedEService <- catalogManagementService.cloneEservice(eServiceId, descriptorId)
      apiEservice    <- convertToApiEservice(clonedEService)
    } yield apiEservice

    onComplete(result) {
      case Success(res) => cloneEServiceByDescriptor200(res)
      case Failure(ex)  =>
        logger.error(s"Error while cloning descriptor $descriptorId of e-service $eServiceId", ex)
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
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
    authorize(ADMIN_ROLE, API_ROLE) {
      logger.info("Deleting e-service {}", eServiceId)
      val result = catalogManagementService.deleteEService(eServiceId)

      onComplete(result) {
        case Success(_)  => deleteEService204
        case Failure(ex) =>
          logger.error(s"Error while deleting e-service $eServiceId", ex)
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
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    logger.info("Activating descriptor {} for e-service {}", descriptorId, eServiceId)
    def activateDescriptor(eService: ManagementEService, descriptor: ManagementDescriptor): Future[Unit] = {
      val validState             = Seq(
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
          catalogManagementService.publishDescriptor(eServiceId, descriptorId)
        case _ => catalogManagementService.deprecateDescriptor(eServiceId, descriptorId)
      }
    }

    val result = for {
      eService   <- catalogManagementService.getEService(eServiceId)
      descriptor <- eService.descriptors
        .find(_.id.toString == descriptorId)
        .toFuture(EServiceDescriptorNotFound(eServiceId, descriptorId))
      _          <- descriptorCanBeActivated(descriptor)
      _          <- activateDescriptor(eService, descriptor)
      _          <- authorizationManagementService.updateStateOnClients(
        eService.id,
        descriptor.id,
        AuthorizationManagementDependency.ClientComponentState.ACTIVE,
        descriptor.audience,
        descriptor.voucherLifespan
      )
    } yield ()

    onComplete(result) {
      case Success(_)                                 => activateDescriptor204
      case Failure(ex: NotValidDescriptor)            =>
        logger.error(s"Activating descriptor $descriptorId for e-service $eServiceId", ex)
        activateDescriptor400(problemOf(StatusCodes.BadRequest, ex))
      case Failure(ex: ApiError[_]) if ex.code == 400 =>
        logger.error(s"Activating descriptor $descriptorId for e-service $eServiceId", ex)
        activateDescriptor400(
          problemOf(StatusCodes.BadRequest, ActivateDescriptorDocumentBadRequest(descriptorId, eServiceId))
        )
      case Failure(ex: ApiError[_]) if ex.code == 404 =>
        logger.error(s"Activating descriptor $descriptorId for e-service $eServiceId", ex)
        activateDescriptor404(
          problemOf(StatusCodes.NotFound, ActivateDescriptorDocumentNotFound(descriptorId, eServiceId))
        )
      case Failure(ex)                                =>
        logger.error(s"Activating descriptor $descriptorId for e-service $eServiceId", ex)
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
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    logger.info("Suspending descriptor {} of e-service {}", descriptorId, eServiceId)
    val result = for {
      eService   <- catalogManagementService.getEService(eServiceId)
      descriptor <- eService.descriptors
        .find(_.id.toString == descriptorId)
        .toFuture(EServiceDescriptorNotFound(eServiceId, descriptorId))
      _          <- descriptorCanBeSuspended(descriptor)
      _          <- catalogManagementService.suspendDescriptor(eServiceId, descriptorId)
      _          <- authorizationManagementService.updateStateOnClients(
        eService.id,
        descriptor.id,
        AuthorizationManagementDependency.ClientComponentState.INACTIVE,
        descriptor.audience,
        descriptor.voucherLifespan
      )
    } yield ()

    onComplete(result) {
      case Success(_)                                 => suspendDescriptor204
      case Failure(ex: NotValidDescriptor)            =>
        logger.error(s"Error during suspension of descriptor $descriptorId of e-service $eServiceId", ex)
        suspendDescriptor400(problemOf(StatusCodes.BadRequest, ex))
      case Failure(ex: ApiError[_]) if ex.code == 400 =>
        logger.error(s"Error during suspension of descriptor $descriptorId of e-service $eServiceId", ex)
        suspendDescriptor400(
          problemOf(StatusCodes.BadRequest, SuspendDescriptorDocumentBadRequest(descriptorId, eServiceId))
        )
      case Failure(ex: ApiError[_]) if ex.code == 404 =>
        logger.error(s"Error during suspension of descriptor $descriptorId of e-service $eServiceId", ex)
        suspendDescriptor404(
          problemOf(StatusCodes.NotFound, SuspendDescriptorDocumentNotFound(descriptorId, eServiceId))
        )
      case Failure(ex)                                =>
        logger.error(s"Error during suspension of descriptor $descriptorId of e-service $eServiceId", ex)
        val error =
          problemOf(StatusCodes.InternalServerError, SuspendDescriptorDocumentError(descriptorId, eServiceId))
        complete(error.status, error)
    }
  }
}

object ProcessApiServiceImpl {
  def verifyPublicationEligibility(
    descriptor: CatalogManagementDependency.EServiceDescriptor
  )(implicit ec: ExecutionContext): Future[Unit] = isDraftDescriptor(descriptor) >> descriptor.interface
    .toFuture(EServiceDescriptorWithoutInterface(descriptor.id.toString))
    .void

  def isDraftDescriptor(
    descriptor: CatalogManagementDependency.EServiceDescriptor
  ): Future[CatalogManagementDependency.EServiceDescriptor] = descriptor.state match {
    case CatalogManagementDependency.EServiceDescriptorState.DRAFT => Future.successful(descriptor)
    case _ => Future.failed(NotValidDescriptor(descriptor.id.toString, descriptor.state.toString))
  }

}
