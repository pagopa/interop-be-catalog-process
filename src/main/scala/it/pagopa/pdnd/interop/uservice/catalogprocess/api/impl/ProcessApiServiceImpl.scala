package it.pagopa.pdnd.interop.uservice.catalogprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.{ContentType, HttpEntity, MessageEntity}
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FileInfo
import cats.implicits.toTraverseOps
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.Agreement
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.invoker.ApiError
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.{
  EService => ManagementEService,
  EServiceDescriptor => ManagementDescriptor
}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.{model => CatalogManagementDependency}
import it.pagopa.pdnd.interop.uservice.catalogprocess.api.ProcessApiService
import it.pagopa.pdnd.interop.uservice.catalogprocess.api.impl.Converter.convertToApiDescriptorStatus
import it.pagopa.pdnd.interop.uservice.catalogprocess.common.system.{EitherOps, OptionOps}
import it.pagopa.pdnd.interop.uservice.catalogprocess.errors.{
  ContentTypeParsingError,
  DescriptorNotFound,
  EServiceDescriptorNotFound,
  NotValidDescriptor
}
import it.pagopa.pdnd.interop.uservice.catalogprocess.model._
import it.pagopa.pdnd.interop.uservice.catalogprocess.service._
import it.pagopa.pdnd.interop.uservice.partymanagement.client.model.{BulkOrganizations, BulkPartiesSeed}
import org.slf4j.{Logger, LoggerFactory}

import java.io.{File, FileOutputStream}
import java.nio.file.{Files, Path}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.ImplicitParameter",
    "org.wartremover.warts.Any",
    "org.wartremover.warts.Equals",
    "org.wartremover.warts.Nothing",
    "org.wartremover.warts.ToString",
    "org.wartremover.warts.Option2Iterable",
    "org.wartremover.warts.Recursion"
  )
)
final case class ProcessApiServiceImpl(
  catalogManagementService: CatalogManagementService,
  partyManagementService: PartyManagementService,
  attributeRegistryManagementService: AttributeRegistryManagementService,
  agreementManagementService: AgreementManagementService,
  fileManager: FileManager
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
        bearer <- tokenFromContext(contexts)
        clientSeed = Converter.convertToClientEServiceSeed(eServiceSeed)
        createdEService <- catalogManagementService.createEService(bearer)(clientSeed)
        apiEservice     <- convertToApiEservice(createdEService)
      } yield apiEservice

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
        bearer       <- tokenFromContext(contexts)
        statusEnum   <- status.traverse(CatalogManagementDependency.EServiceDescriptorStatusEnum.fromValue).toFuture
        eservices    <- retrieveEservices(bearer, producerId, consumerId, statusEnum)
        apiEservices <- eservices.traverse(convertToApiEservice)
      } yield apiEservices

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
        currentActiveDescriptor = currentEService.descriptors.find(d =>
          d.status == CatalogManagementDependency.PUBLISHED
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
        bearer      <- tokenFromContext(contexts)
        eservice    <- catalogManagementService.getEService(bearer)(eServiceId)
        apiEservice <- convertToApiEservice(eservice)
      } yield apiEservice

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
        eservice <- catalogManagementService.createEServiceDocument(bearer)(
          eServiceId,
          descriptorId,
          kind,
          description,
          doc
        )
        apiEservice <- convertToApiEservice(eservice)
      } yield apiEservice

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
    val result: Future[DocumentDetails] =
      for {
        bearer      <- tokenFromContext(contexts)
        document    <- catalogManagementService.getEServiceDocument(bearer)(eServiceId, descriptorId, documentId)
        contentType <- extractFile(document)
        response    <- fileManager.get(document.path)
      } yield DocumentDetails(document.name, contentType, response)

    onComplete(result) {
      case Success(documentDetails) =>
        val output: MessageEntity = convertToMessageEntity(documentDetails)
        complete(output)
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

  def convertToMessageEntity(documentDetails: DocumentDetails): MessageEntity = {
    val randomPath: Path               = Files.createTempDirectory(s"document")
    val temporaryFilePath: String      = s"${randomPath.toString}/${documentDetails.name}"
    val file: File                     = new File(temporaryFilePath)
    val outputStream: FileOutputStream = new FileOutputStream(file)
    documentDetails.data.writeTo(outputStream)
    HttpEntity.fromFile(documentDetails.contentType, file)
  }

  private def extractFile(document: CatalogManagementDependency.EServiceDoc): Future[ContentType] = {
    ContentType
      .parse(document.contentType)
      .fold(ex => Future.failed(ContentTypeParsingError(document, ex)), Future.successful)
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
    toEntityMarshallerFlatEServicearray: ToEntityMarshaller[Seq[FlatEService]],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {

    val result =
      for {
        bearer                    <- tokenFromContext(contexts)
        statusEnum                <- status.traverse(CatalogManagementDependency.EServiceDescriptorStatusEnum.fromValue).toFuture
        callerSubscribedEservices <- agreementManagementService.getAgreementsByConsumerId(bearer)(callerId)
        retrievedEservices        <- retrieveEservices(bearer, producerId, consumerId, statusEnum)
        eservices                 <- processEservicesWithLatestFilter(retrievedEservices, latestPublishedOnly)
        organizationsDetails <- partyManagementService.getBulkOrganizations(
          BulkPartiesSeed(partyIdentifiers = eservices.map(_.producerId))
        )
        flattenServices = eservices.flatMap(service =>
          convertToFlattenEservice(service, callerSubscribedEservices, organizationsDetails)
        )
        filteredDescriptors = flattenServices.filter(item => status.forall(item.status.contains))
      } yield filteredDescriptors

    onComplete(result) {
      case Success(response) => getFlatEServices200(response)
      case Failure(ex) =>
        getFlatEServices500(
          Problem(Option(ex.getMessage), 500, s"Unexpected error while retrieving flatted E-Services")
        )
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
                d.status == CatalogManagementDependency.PUBLISHED || d.status == CatalogManagementDependency.SUSPENDED
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
    val result =
      for {
        bearer                    <- tokenFromContext(contexts)
        currentEService           <- catalogManagementService.getEService(bearer)(eServiceId)
        _                         <- catalogManagementService.hasNotDraftDescriptor(currentEService)
        clientSeed                <- Converter.convertToClientEServiceDescriptorSeed(eServiceDescriptorSeed)
        createdEServiceDescriptor <- catalogManagementService.createDescriptor(bearer)(eServiceId, clientSeed)
      } yield Converter.convertToApiDescriptor(createdEServiceDescriptor)

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

    val result: Future[EService] =
      for {
        bearer          <- tokenFromContext(contexts)
        currentEService <- catalogManagementService.getEService(bearer)(eServiceId)
        _               <- isDraftDescriptor(currentEService.descriptors.find(_.id.toString == descriptorId))
        clientSeed      <- Converter.convertToClientUpdateEServiceDescriptorSeed(updateEServiceDescriptorSeed)
        updatedEservice <- catalogManagementService.updateDraftDescriptor(bearer)(eServiceId, descriptorId, clientSeed)
        apiEservice     <- convertToApiEservice(updatedEservice)
      } yield apiEservice

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
        bearer <- tokenFromContext(contexts)
        clientSeed = Converter.convertToClientUpdateEServiceSeed(updateEServiceSeed)
        updatedEservice <- catalogManagementService.updateEservice(bearer)(eServiceId, clientSeed)
        apiEservice     <- convertToApiEservice(updatedEservice)
      } yield apiEservice

    onComplete(result) {
      case Success(res) => updateEServiceById200(res)
      case Failure(ex) =>
        val errorResponse: Problem =
          Problem(Option(ex.getMessage), 400, s"Error while creating Descriptor for e-service Id $eServiceId")
        createDescriptor400(errorResponse)
    }
  }

  private def convertToApiEservice(eservice: CatalogManagementDependency.EService): Future[EService] = {
    for {
      organization <- partyManagementService.getOrganization(eservice.producerId)
      attributes   <- attributeRegistryManagementService.getAttributesBulk(extractIdsFromAttributes(eservice.attributes))
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
    status: Option[CatalogManagementDependency.EServiceDescriptorStatusEnum]
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
          status.forall(s => eService.descriptors.exists(_.status == s))
      )
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
      status = None,
      descriptorId = None,
      callerSubscribed = agreementSubscribedEservices.find(agr => agr.eserviceId == eservice.id).map(_.id),
      certifiedAttributes = eservice.attributes.certified.map(toFlatAttribute)
    )

    val flatEServices: Seq[FlatEService] = eservice.descriptors.map { descriptor =>
      flatEServiceZero.copy(
        version = Some(descriptor.version),
        status = Some(convertToApiDescriptorStatus(descriptor.status)),
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

  private def isDraftDescriptor(
    optDescriptor: Option[CatalogManagementDependency.EServiceDescriptor]
  ): Future[CatalogManagementDependency.EServiceDescriptor] = {
    optDescriptor.fold(Future.failed[CatalogManagementDependency.EServiceDescriptor](DescriptorNotFound(""))) {
      descriptor =>
        descriptor.status match {
          case CatalogManagementDependency.DRAFT => Future.successful(descriptor)
          case _ =>
            Future.failed(NotValidDescriptor(descriptor.id.toString, descriptor.status.toString))
        }
    }
  }

  private def descriptorCanBeSuspended(
    descriptor: CatalogManagementDependency.EServiceDescriptor
  ): Future[CatalogManagementDependency.EServiceDescriptor] =
    descriptor.status match {
      case CatalogManagementDependency.DEPRECATED => Future.successful(descriptor)
      case CatalogManagementDependency.PUBLISHED  => Future.successful(descriptor)
      case _ =>
        Future.failed(NotValidDescriptor(descriptor.id.toString, descriptor.status.toString))
    }

  private def descriptorCanBeActivated(
    descriptor: CatalogManagementDependency.EServiceDescriptor
  ): Future[CatalogManagementDependency.EServiceDescriptor] =
    descriptor.status match {
      case CatalogManagementDependency.SUSPENDED => Future.successful(descriptor)
      case _ =>
        Future.failed(NotValidDescriptor(descriptor.id.toString, descriptor.status.toString))
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
    val result =
      for {
        bearer         <- tokenFromContext(contexts)
        clonedEService <- catalogManagementService.cloneEservice(bearer)(eServiceId, descriptorId)
        apiEservice    <- convertToApiEservice(clonedEService)
      } yield apiEservice

    onComplete(result) {
      case Success(res) => cloneEServiceByDescriptor200(res)
      case Failure(ex) =>
        val errorResponse: Problem =
          Problem(Option(ex.getMessage), 400, s"Error while cloning descriptor $descriptorId for E-service $eServiceId")
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
    val result =
      for {
        bearer <- tokenFromContext(contexts)
        _      <- catalogManagementService.deleteEService(bearer)(eServiceId)
      } yield ()

    onComplete(result) {
      case Success(_) => deleteEService204
      case Failure(ex) =>
        complete(500, Problem(Option(ex.getMessage), 500, s"Error while deleting E-service $eServiceId"))
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
    def activateDescriptor(
      bearer: String
    )(eService: ManagementEService, descriptor: ManagementDescriptor): Future[Unit] = {
      val validStatus =
        Seq(
          CatalogManagementDependency.SUSPENDED,
          CatalogManagementDependency.DEPRECATED,
          CatalogManagementDependency.PUBLISHED
        )
      val mostRecentValidVersion =
        eService.descriptors
          .filter(d => validStatus.contains(d.status))
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
        bearer   <- tokenFromContext(contexts)
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
        activateDescriptor400(
          Problem(
            Option(ex.getMessage),
            400,
            s"Error while activating descriptor $descriptorId for E-Service $eServiceId"
          )
        )
      case Failure(ex: ApiError[_]) if ex.code == 400 =>
        activateDescriptor400(
          Problem(
            Option(ex.getMessage),
            400,
            s"Error while activating descriptor $descriptorId for E-Service $eServiceId"
          )
        )
      case Failure(ex: ApiError[_]) if ex.code == 404 =>
        activateDescriptor404(
          Problem(
            Option(ex.getMessage),
            404,
            s"Error while activating descriptor $descriptorId for E-Service $eServiceId"
          )
        )
      case Failure(ex) =>
        complete(
          (
            500,
            Problem(
              Option(ex.getMessage),
              500,
              s"Unexpected error while activating descriptor $descriptorId for E-Service $eServiceId"
            )
          )
        )
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
    val result =
      for {
        bearer   <- tokenFromContext(contexts)
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
        suspendDescriptor400(
          Problem(
            Option(ex.getMessage),
            400,
            s"Error while suspending descriptor $descriptorId for E-Service $eServiceId"
          )
        )
      case Failure(ex: ApiError[_]) if ex.code == 400 =>
        suspendDescriptor400(
          Problem(
            Option(ex.getMessage),
            400,
            s"Error while suspending descriptor $descriptorId for E-Service $eServiceId"
          )
        )
      case Failure(ex: ApiError[_]) if ex.code == 404 =>
        suspendDescriptor404(
          Problem(
            Option(ex.getMessage),
            404,
            s"Error while suspending descriptor $descriptorId for E-Service $eServiceId"
          )
        )
      case Failure(ex) =>
        complete(
          (
            500,
            Problem(
              Option(ex.getMessage),
              500,
              s"Unexpected error while suspending descriptor $descriptorId for E-Service $eServiceId"
            )
          )
        )
    }
  }
}
