package it.pagopa.interop.catalogprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.Route
import cats.implicits.toTraverseOps
import cats.syntax.all._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagementDependency}
import it.pagopa.interop.authorizationmanagement.client.{model => AuthorizationManagementDependency}
import it.pagopa.interop.catalogmanagement.client
import it.pagopa.interop.catalogmanagement.client.model.{
  EService => ManagementEService,
  EServiceDescriptor => ManagementDescriptor
}
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagementDependency}
import it.pagopa.interop.catalogprocess.api.ProcessApiService
import it.pagopa.interop.catalogprocess.api.impl.Converter._
import it.pagopa.interop.catalogprocess.api.impl.ResponseHandlers._
import it.pagopa.interop.catalogprocess.common.readmodel.ReadModelQueries
import it.pagopa.interop.catalogprocess.errors.CatalogProcessErrors._
import it.pagopa.interop.catalogprocess.model._
import it.pagopa.interop.catalogprocess.service._
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.jwt._
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils.getOrganizationIdFutureUUID
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.{GenericComponentErrors, Problem => CommonProblem}
import it.pagopa.interop.tenantmanagement.client.{model => TenantManagementDependency}
import it.pagopa.interop.catalogprocess.common.readmodel.PaginatedResult
import it.pagopa.interop.catalogmanagement.model.CatalogItem

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

final case class ProcessApiServiceImpl(
  catalogManagementService: CatalogManagementService,
  attributeRegistryManagementService: AttributeRegistryManagementService,
  agreementManagementService: AgreementManagementService,
  authorizationManagementService: AuthorizationManagementService,
  tenantManagementService: TenantManagementService,
  readModel: ReadModelService,
  fileManager: FileManager,
  jwtReader: JWTReader
)(implicit ec: ExecutionContext)
    extends ProcessApiService {

  import ProcessApiServiceImpl._

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def createEService(eServiceSeed: EServiceSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerEService: ToEntityMarshaller[EService]
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    val operationLabel: String = s"Creating EService with service name ${eServiceSeed.name}"
    logger.info(operationLabel)

    val result: Future[EService] = for {
      organizationId <- getOrganizationIdFutureUUID(contexts)
      clientSeed = Converter.convertToClientEServiceSeed(eServiceSeed, organizationId)
      maybeEservice <- ReadModelQueries
        .listEServices(
          eServiceSeed.name.some,
          Seq.empty,
          Seq(clientSeed.producerId.toString),
          Seq.empty,
          0,
          1,
          exactMatchOnName = true
        )(readModel)
        .map(_.results.headOption.map(_.name))

      _               <-
        if (maybeEservice.contains(eServiceSeed.name)) Future.failed(DuplicatedEServiceName(eServiceSeed.name))
        else Future.unit
      createdEService <- catalogManagementService.createEService(clientSeed)
      apiEService = Converter.convertToApiEService(createdEService)
    } yield apiEService

    onComplete(result) {
      createEServiceResponse[EService](operationLabel) { res =>
        logger.info(s"E-Service created with id ${res.id}")
        createEService200(res)
      }
    }
  }

  override def deleteDraft(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    val operationLabel: String = s"Deleting draft descriptor $descriptorId for E-Service $eServiceId"
    logger.info(operationLabel)

    val result: Future[Unit] = for {
      organizationId <- getOrganizationIdFutureUUID(contexts)
      eService       <- catalogManagementService.getEService(eServiceId)
      _              <- assertRequesterAllowed(eService.producerId)(organizationId)
      result         <- catalogManagementService.deleteDraft(eServiceId, descriptorId)
    } yield result

    onComplete(result) {
      deleteDraftResponse[Unit](operationLabel)(_ => deleteDraft204)
    }
  }

  override def getEServices(
    name: Option[String],
    eServicesIds: String,
    producersIds: String,
    states: String,
    agreementStates: String,
    offset: Int,
    limit: Int
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerEServices: ToEntityMarshaller[EServices]): Route =
    authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE, M2M_ROLE) {
      val operationLabel =
        s"Getting e-service with name = $name, ids = $eServicesIds, producers = $producersIds, states = $states, agreementStates = $agreementStates, limit = $limit, offset = $offset"
      logger.info(operationLabel)

      def getEservicesInner(
        organizationId: UUID,
        name: Option[String],
        apiEServicesIds: List[String],
        apiProducersIds: List[String],
        apiStates: List[EServiceDescriptorState],
        apiAgreementStates: List[AgreementState],
        offset: Int,
        limit: Int
      ): Future[PaginatedResult[CatalogItem]] = {

        if (apiAgreementStates.isEmpty)
          ReadModelQueries.listEServices(name, apiEServicesIds, apiProducersIds, apiStates, offset, limit)(readModel)
        else
          for {
            agreementEservicesIds <- ReadModelQueries
              .listAgreements(
                eServicesIds = apiEServicesIds,
                producersIds = Nil,
                consumersIds = Seq(organizationId.toString),
                states = apiAgreementStates
              )(readModel)
              .map(_.map(_.eserviceId.toString))
            result                <-
              if (agreementEservicesIds.isEmpty)
                Future.successful(ReadModelQueries.emptyResults[CatalogItem])
              else
                ReadModelQueries.listEServices(name, agreementEservicesIds, apiProducersIds, apiStates, offset, limit)(
                  readModel
                )

          } yield result
      }

      val result: Future[EServices] = for {
        organizationId <- getOrganizationIdFutureUUID(contexts)
        apiStates      <- parseArrayParameters(states).traverse(EServiceDescriptorState.fromValue).toFuture
        apiProducersIds = parseArrayParameters(producersIds)
        apiEServicesIds = parseArrayParameters(eServicesIds)
        apiAgreementStates <- parseArrayParameters(agreementStates)
          .traverse(AgreementState.fromValue)
          .toFuture
        eServices          <-
          getEservicesInner(
            organizationId,
            name,
            apiEServicesIds,
            apiProducersIds,
            apiStates,
            apiAgreementStates,
            offset,
            limit
          )
      } yield EServices(results = eServices.results.map(_.toApi), totalCount = eServices.totalCount)

      onComplete(result) {
        getEServicesResponse(operationLabel)(getEServices200)
      }
    }

  override def publishDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    val operationLabel = s"Publishing descriptor $descriptorId for EService $eServiceId"
    logger.info(operationLabel)

    val result: Future[Unit] = for {
      organizationId  <- getOrganizationIdFutureUUID(contexts)
      currentEService <- catalogManagementService.getEService(eServiceId)
      _               <- assertRequesterAllowed(currentEService.producerId)(organizationId)
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
      publishDescriptorResponse[Unit](operationLabel)(_ => publishDescriptor204)
    }
  }

  override def getEServiceById(eServiceId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerEService: ToEntityMarshaller[EService]
  ): Route = authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE, M2M_ROLE) {
    val operationLabel = s"Retrieving EService $eServiceId"
    logger.info(operationLabel)

    val result: Future[EService] = catalogManagementService.getEService(eServiceId).map(Converter.convertToApiEService)

    onComplete(result) {
      getEServiceByIdResponse[EService](operationLabel)(getEServiceById200)
    }
  }
  override def createEServiceDocument(
    eServiceId: String,
    descriptorId: String,
    documentSeed: CreateEServiceDescriptorDocumentSeed
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerEService: ToEntityMarshaller[EService]
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    val operationLabel =
      s"Creating EService document ${documentSeed.documentId.toString} of kind ${documentSeed.kind}, name ${documentSeed.fileName}, path ${documentSeed.filePath} for EService $eServiceId and descriptor $descriptorId"
    logger.info(operationLabel)

    val managementSeed: CatalogManagementDependency.CreateEServiceDescriptorDocumentSeed =
      Converter.convertToManagementEServiceDescriptorDocumentSeed(documentSeed)

    val result: Future[EService] = for {
      organizationId <- getOrganizationIdFutureUUID(contexts)
      eService       <- catalogManagementService.getEService(eServiceId)
      _              <- assertRequesterAllowed(eService.producerId)(organizationId)
      descriptor     <- eService.descriptors
        .find(_.id.toString == descriptorId)
        .toFuture(EServiceDescriptorNotFound(eServiceId, descriptorId))
      updated        <- catalogManagementService.createEServiceDocument(eService.id, descriptor.id, managementSeed)
    } yield Converter.convertToApiEService(updated)

    onComplete(result) {
      createEServiceDocumentResponse[EService](operationLabel)(createEServiceDocument200)
    }
  }

  override def getEServiceDocumentById(eServiceId: String, descriptorId: String, documentId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerEServiceDoc: ToEntityMarshaller[EServiceDoc],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE, M2M_ROLE) {
    val operationLabel =
      s"Retrieving EService document $documentId for EService $eServiceId and descriptor $descriptorId"
    logger.info(operationLabel)

    val result: Future[EServiceDoc] = catalogManagementService
      .getEServiceDocument(eServiceId, descriptorId, documentId)
      .map(Converter.convertToApiEserviceDoc)

    onComplete(result) {
      getEServiceByIdResponse[EServiceDoc](operationLabel)(getEServiceDocumentById200)
    }
  }

  // TODO To be deleted
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
      statusEnum         <- state.traverse(CatalogManagementDependency.EServiceDescriptorState.fromValue).toFuture
      agreementStates    <- parseArrayParameters(agreementState).distinct.traverse(AgreementState.fromValue).toFuture
      retrievedEservices <- retrieveEservices(producerId, consumerId, statusEnum, agreementStates)
      eservices          <- processEservicesWithLatestFilter(retrievedEservices, latestPublishedOnly)
      eserviceAndTenant  <- Future.traverse(eservices.toList)(getGetEserviceAndTenant)
      agreements         <- Future
        .traverse(eservices.toList)(eService =>
          agreementManagementService.getAgreements(
            consumerId = callerId.some,
            producerId = eService.producerId.toString.some,
            eServiceId = eService.id.toString.some,
            states = agreementStates.map(convertToApiAgreementState)
          )
        )
        .map(_.flatten)

      flattenServices = eserviceAndTenant.flatMap { case (service, tenant) =>
        convertToFlattenEservice(service, agreements, tenant)
      }

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
        val error = CommonProblem(StatusCodes.InternalServerError, FlattenedEServicesRetrievalError, serviceCode, None)
        complete(error.status, error)
    }
  }

  private def getGetEserviceAndTenant(eservice: client.model.EService)(implicit
    contexts: Seq[(String, String)]
  ): Future[(client.model.EService, TenantManagementDependency.Tenant)] = tenantManagementService
    .getTenant(eservice.producerId)
    .tupleLeft(eservice)

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

  override def createDescriptor(eServiceId: String, eServiceDescriptorSeed: EServiceDescriptorSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerEServiceDescriptor: ToEntityMarshaller[EServiceDescriptor],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    val operationLabel = s"Creating descriptor for EService $eServiceId"
    logger.info(operationLabel)

    val result: Future[EServiceDescriptor] = for {
      organizationId  <- getOrganizationIdFutureUUID(contexts)
      currentEService <- catalogManagementService.getEService(eServiceId)
      _               <- assertRequesterAllowed(currentEService.producerId)(organizationId)
      _               <- catalogManagementService.hasNotDraftDescriptor(currentEService)
      clientSeed = Converter.convertToClientEServiceDescriptorSeed(eServiceDescriptorSeed)
      createdEServiceDescriptor <- catalogManagementService.createDescriptor(eServiceId, clientSeed)
    } yield Converter.convertToApiDescriptor(createdEServiceDescriptor)

    onComplete(result) {
      createDescriptorResponse[EServiceDescriptor](operationLabel)(createDescriptor200)
    }
  }

  override def updateDraftDescriptor(
    eServiceId: String,
    descriptorId: String,
    updateEServiceDescriptorSeed: UpdateEServiceDescriptorSeed
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerEService: ToEntityMarshaller[EService]
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    val operationLabel = s"Updating draft descriptor $descriptorId of EService $eServiceId"
    logger.info(operationLabel)

    val result: Future[EService] = for {
      organizationId  <- getOrganizationIdFutureUUID(contexts)
      currentEService <- catalogManagementService.getEService(eServiceId)
      _               <- assertRequesterAllowed(currentEService.producerId)(organizationId)
      descriptor      <- currentEService.descriptors
        .find(_.id.toString == descriptorId)
        .toFuture(EServiceDescriptorNotFound(eServiceId, descriptorId))
      _               <- isDraftDescriptor(descriptor)
      clientSeed = Converter.convertToClientUpdateEServiceDescriptorSeed(updateEServiceDescriptorSeed)
      updatedEService <- catalogManagementService.updateDraftDescriptor(eServiceId, descriptorId, clientSeed)
    } yield Converter.convertToApiEService(updatedEService)

    onComplete(result) {
      updateDraftDescriptorResponse[EService](operationLabel)(updateDraftDescriptor200)
    }
  }

  override def updateEServiceById(eServiceId: String, updateEServiceSeed: UpdateEServiceSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerEService: ToEntityMarshaller[EService]
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    val operationLabel = s"Updating EService by id $eServiceId"
    logger.info(operationLabel)

    val result = for {
      organizationId <- getOrganizationIdFutureUUID(contexts)
      eService       <- catalogManagementService.getEService(eServiceId)
      _              <- assertRequesterAllowed(eService.producerId)(organizationId)
      _              <- CatalogManagementService.eServiceCanBeUpdated(eService)
      clientSeed = Converter.convertToClientUpdateEServiceSeed(updateEServiceSeed)
      updatedEService <- catalogManagementService.updateEServiceById(eServiceId, clientSeed)
      apiEService = Converter.convertToApiEService(updatedEService)
    } yield apiEService

    onComplete(result) {
      updateEServiceByIdResponse(operationLabel)(updateEServiceById200)
    }
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
          agreementStates.distinct.map(convertToApiAgreementState),
          eServiceId = None
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
      logger.error(s"Unable to deprecate descriptor $descriptorId on EService $eServiceId", ex)
      Future.failed(ex)
    }

  private[this] def resetDescriptorToDraft(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = catalogManagementService
    .draftDescriptor(eServiceId = eServiceId, descriptorId = descriptorId)
    .map { result =>
      logger.info(s"Publication cancelled for descriptor $descriptorId in EService $eServiceId")
      result
    }

  private def convertToFlattenEservice(
    eservice: client.model.EService,
    agreements: Seq[AgreementManagementDependency.Agreement],
    tenant: TenantManagementDependency.Tenant
  ): Seq[FlatEService] = {
    val flatEServiceZero: FlatEService = FlatEService(
      id = eservice.id,
      producerId = eservice.producerId,
      name = eservice.name,
      producerName = tenant.name,
      version = None,
      state = None,
      descriptorId = None,
      description = eservice.description,
      agreement = agreements.find(agr => agr.eserviceId == eservice.id).map(toFlatAgreement),
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

  private def toFlatAgreement(agreement: AgreementManagementDependency.Agreement): FlatAgreement =
    FlatAgreement(id = agreement.id, state = convertFromApiAgreementState(agreement.state))

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

  override def deleteEServiceDocumentById(eServiceId: String, descriptorId: String, documentId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    val operationLabel = s"Delete document $documentId of descriptor $descriptorId for EService $eServiceId"
    logger.info(operationLabel)

    val result: Future[Unit] = for {
      organizationId <- getOrganizationIdFutureUUID(contexts)
      eService       <- catalogManagementService.getEService(eServiceId)
      _              <- assertRequesterAllowed(eService.producerId)(organizationId)
      result         <- catalogManagementService.deleteEServiceDocument(eServiceId, descriptorId, documentId)
    } yield result

    onComplete(result) {
      deleteEServiceDocumentByIdResponse[Unit](operationLabel)(_ => deleteEServiceDocumentById204)
    }
  }

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
    val operationLabel = s"Updating EService by id $eServiceId"
    logger.info(operationLabel)

    val clientSeed: CatalogManagementDependency.UpdateEServiceDescriptorDocumentSeed =
      Converter.convertToClientEServiceDescriptorDocumentSeed(updateEServiceDescriptorDocumentSeed)

    val result: Future[EServiceDoc] = for {
      organizationId <- getOrganizationIdFutureUUID(contexts)
      eService       <- catalogManagementService.getEService(eServiceId)
      _              <- assertRequesterAllowed(eService.producerId)(organizationId)
      result         <- catalogManagementService
        .updateEServiceDocument(eServiceId, descriptorId, documentId, clientSeed)
        .map(Converter.convertToApiEserviceDoc)
    } yield result

    onComplete(result) {
      updateEServiceDocumentByIdResponse[EServiceDoc](operationLabel)(updateEServiceDocumentById200)
    }
  }

  override def cloneEServiceByDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerEService: ToEntityMarshaller[EService]
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    val operationLabel = s"Cloning descriptor $descriptorId of EService $eServiceId"
    logger.info(operationLabel)

    val result: Future[EService] = for {
      organizationId <- getOrganizationIdFutureUUID(contexts)
      eService       <- catalogManagementService.getEService(eServiceId)
      _              <- assertRequesterAllowed(eService.producerId)(organizationId)
      eServiceUUID   <- eServiceId.toFutureUUID
      descriptorUUID <- descriptorId.toFutureUUID
      clonedEService <- catalogManagementService.cloneEService(eServiceUUID, descriptorUUID)
      apiEService = Converter.convertToApiEService(clonedEService)
    } yield apiEService

    onComplete(result) {
      cloneEServiceByDescriptorResponse[EService](operationLabel) { res =>
        logger.info(s"EService cloned with id ${res.id}")
        cloneEServiceByDescriptor200(res)
      }
    }
  }

  override def deleteEService(
    eServiceId: String
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
    authorize(ADMIN_ROLE, API_ROLE) {
      val operationLabel = s"Deleting EService $eServiceId"
      logger.info(operationLabel)

      val result: Future[Unit] = for {
        organizationId <- getOrganizationIdFutureUUID(contexts)
        eService       <- catalogManagementService.getEService(eServiceId)
        _              <- assertRequesterAllowed(eService.producerId)(organizationId)
        result         <- catalogManagementService.deleteEService(eServiceId)
      } yield result

      onComplete(result) {
        deleteEServiceResponse[Unit](operationLabel)(_ => deleteEService204)
      }
    }

  override def activateDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    val operationLabel = s"Activating descriptor $descriptorId for EService $eServiceId"
    logger.info(operationLabel)

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

    val result: Future[Unit] = for {
      organizationId <- getOrganizationIdFutureUUID(contexts)
      eService       <- catalogManagementService.getEService(eServiceId)
      _              <- assertRequesterAllowed(eService.producerId)(organizationId)
      descriptor     <- eService.descriptors
        .find(_.id.toString == descriptorId)
        .toFuture(EServiceDescriptorNotFound(eServiceId, descriptorId))
      _              <- descriptorCanBeActivated(descriptor)
      _              <- activateDescriptor(eService, descriptor)
      _              <- authorizationManagementService.updateStateOnClients(
        eService.id,
        descriptor.id,
        AuthorizationManagementDependency.ClientComponentState.ACTIVE,
        descriptor.audience,
        descriptor.voucherLifespan
      )
    } yield ()

    onComplete(result) {
      activateDescriptorResponse[Unit](operationLabel)(_ => activateDescriptor204)
    }
  }

  override def suspendDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    val operationLabel = s"Suspending descriptor $descriptorId of EService $eServiceId"
    logger.info(operationLabel)

    val result = for {
      organizationId <- getOrganizationIdFutureUUID(contexts)
      eService       <- catalogManagementService.getEService(eServiceId)
      _              <- assertRequesterAllowed(eService.producerId)(organizationId)
      descriptor     <- eService.descriptors
        .find(_.id.toString == descriptorId)
        .toFuture(EServiceDescriptorNotFound(eServiceId, descriptorId))
      _              <- descriptorCanBeSuspended(descriptor)
      _              <- catalogManagementService.suspendDescriptor(eServiceId, descriptorId)
      _              <- authorizationManagementService.updateStateOnClients(
        eService.id,
        descriptor.id,
        AuthorizationManagementDependency.ClientComponentState.INACTIVE,
        descriptor.audience,
        descriptor.voucherLifespan
      )
    } yield ()

    onComplete(result) {
      suspendDescriptorResponse[Unit](operationLabel)(_ => suspendDescriptor204)
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

  private def assertRequesterAllowed(resourceId: UUID)(requesterId: UUID)(implicit ec: ExecutionContext): Future[Unit] =
    Future.failed(GenericComponentErrors.OperationForbidden).unlessA(resourceId == requesterId)

}
