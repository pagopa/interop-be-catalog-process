package it.pagopa.interop.catalogprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import cats.syntax.all._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.authorizationmanagement.client.{model => AuthorizationManagementDependency}
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagementDependency}
import it.pagopa.interop.catalogmanagement.{model => readmodel}
import it.pagopa.interop.catalogprocess.api.ProcessApiService
import it.pagopa.interop.catalogprocess.api.impl.Converter._
import it.pagopa.interop.catalogprocess.api.impl.ResponseHandlers._
import it.pagopa.interop.catalogprocess.common.readmodel.{PaginatedResult, ReadModelQueries}
import it.pagopa.interop.catalogprocess.errors.CatalogProcessErrors._
import it.pagopa.interop.catalogprocess.model._
import it.pagopa.interop.catalogprocess.service._
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.jwt._
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils.getOrganizationIdFutureUUID
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final case class ProcessApiServiceImpl(
  catalogManagementService: CatalogManagementService,
  authorizationManagementService: AuthorizationManagementService,
  readModel: ReadModelService,
  fileManager: FileManager
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
      clientSeed = eServiceSeed.toDependency(organizationId)
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

      _               <- maybeEservice.fold(Future.unit)(_ => Future.failed(DuplicatedEServiceName(eServiceSeed.name)))
      createdEService <- catalogManagementService.createEService(clientSeed)
    } yield createdEService.toApi

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
      eServiceUuid   <- eServiceId.toFutureUUID
      catalogItem    <- getEService(eServiceUuid)
      _              <- assertRequesterAllowed(catalogItem.producerId)(organizationId)
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
    authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE, M2M_ROLE, SUPPORT_ROLE) {
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
      ): Future[PaginatedResult[readmodel.CatalogItem]] = {

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
                Future.successful(ReadModelQueries.emptyResults[readmodel.CatalogItem])
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
      organizationId <- getOrganizationIdFutureUUID(contexts)
      eServiceUuid   <- eServiceId.toFutureUUID
      eService       <- getEService(eServiceUuid)
      _              <- assertRequesterAllowed(eService.producerId)(organizationId)
      descriptor     <- eService.descriptors
        .find(_.id.toString == descriptorId)
        .toFuture(EServiceDescriptorNotFound(eServiceId, descriptorId))
      _              <- verifyPublicationEligibility(descriptor)
      currentActiveDescriptor = eService.descriptors.find(d => d.state == readmodel.Published) // Must be at most one
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
        eService.id,
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
  ): Route = authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE, M2M_ROLE, SUPPORT_ROLE) {
    val operationLabel = s"Retrieving EService $eServiceId"
    logger.info(operationLabel)

    val result: Future[EService] = for {
      eServiceUuid <- eServiceId.toFutureUUID
      eService     <- getEService(eServiceUuid)
    } yield eService.toApi

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
      documentSeed.toDependency

    val result: Future[EService] = for {
      organizationId <- getOrganizationIdFutureUUID(contexts)
      eServiceUuid   <- eServiceId.toFutureUUID
      catalogItem    <- getEService(eServiceUuid)
      _              <- assertRequesterAllowed(catalogItem.producerId)(organizationId)
      descriptor     <- catalogItem.descriptors
        .find(_.id.toString == descriptorId)
        .toFuture(EServiceDescriptorNotFound(eServiceId, descriptorId))
      updated        <- catalogManagementService.createEServiceDocument(catalogItem.id, descriptor.id, managementSeed)
    } yield updated.toApi

    onComplete(result) {
      createEServiceDocumentResponse[EService](operationLabel)(createEServiceDocument200)
    }
  }

  override def getEServiceDocumentById(eServiceId: String, descriptorId: String, documentId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerEServiceDoc: ToEntityMarshaller[EServiceDoc],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE, M2M_ROLE, SUPPORT_ROLE) {
    val operationLabel =
      s"Retrieving EService document $documentId for EService $eServiceId and descriptor $descriptorId"
    logger.info(operationLabel)

    def getDocument(
      catalogItem: Option[readmodel.CatalogItem],
      descriptorId: String,
      documentId: String
    ): Option[readmodel.CatalogDocument] = {

      def lookup(catalogDescriptor: readmodel.CatalogDescriptor): Option[readmodel.CatalogDocument]                 = {
        val interface = catalogDescriptor.interface.fold(Seq.empty[readmodel.CatalogDocument])(doc => Seq(doc))
        (interface ++: catalogDescriptor.docs).find(_.id.toString == documentId)
      }
      def getDescriptor(eService: readmodel.CatalogItem, descriptorId: String): Option[readmodel.CatalogDescriptor] =
        eService.descriptors.find(_.id.toString == descriptorId)

      for {
        eService   <- catalogItem
        descriptor <- getDescriptor(eService, descriptorId)
        document   <- lookup(descriptor)
      } yield document
    }

    val result: Future[EServiceDoc] = for {
      eServiceUuid   <- eServiceId.toFutureUUID
      descriptorUuid <- descriptorId.toFutureUUID
      documentIdUuid <- documentId.toFutureUUID
      catalogItem    <- ReadModelQueries.getEServiceDocument(eServiceUuid, descriptorUuid, documentIdUuid)(readModel)
      eServiceDoc    <- getDocument(catalogItem, descriptorId, documentId).toFuture(
        DescriptorDocumentNotFound(eServiceId, descriptorId, documentId)
      )
    } yield eServiceDoc.toApi

    onComplete(result) {
      getEServiceByIdResponse[EServiceDoc](operationLabel)(getEServiceDocumentById200)
    }
  }

  override def createDescriptor(eServiceId: String, eServiceDescriptorSeed: EServiceDescriptorSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerEServiceDescriptor: ToEntityMarshaller[EServiceDescriptor],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    val operationLabel = s"Creating descriptor for EService $eServiceId"
    logger.info(operationLabel)

    val result: Future[EServiceDescriptor] = for {
      organizationId            <- getOrganizationIdFutureUUID(contexts)
      eServiceUuid              <- eServiceId.toFutureUUID
      catalogItem               <- getEService(eServiceUuid)
      _                         <- assertRequesterAllowed(catalogItem.producerId)(organizationId)
      _                         <- hasNotDraftDescriptor(catalogItem).toFuture
      createdEServiceDescriptor <- catalogManagementService.createDescriptor(
        eServiceId,
        eServiceDescriptorSeed.toDependency
      )
    } yield createdEServiceDescriptor.toApi

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
      eServiceUuid    <- eServiceId.toFutureUUID
      catalogItem     <- getEService(eServiceUuid)
      _               <- assertRequesterAllowed(catalogItem.producerId)(organizationId)
      descriptor      <- catalogItem.descriptors
        .find(_.id.toString == descriptorId)
        .toFuture(EServiceDescriptorNotFound(eServiceId, descriptorId))
      _               <- isDraftDescriptor(descriptor)
      updatedEService <- catalogManagementService.updateDraftDescriptor(
        eServiceId,
        descriptorId,
        updateEServiceDescriptorSeed.toDependency
      )
    } yield updatedEService.toApi

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
      organizationId  <- getOrganizationIdFutureUUID(contexts)
      eServiceUuid    <- eServiceId.toFutureUUID
      catalogItem     <- getEService(eServiceUuid)
      _               <- assertRequesterAllowed(catalogItem.producerId)(organizationId)
      _               <- eServiceCanBeUpdated(catalogItem).toFuture
      updatedEService <- catalogManagementService.updateEServiceById(eServiceId, updateEServiceSeed.toDependency)
    } yield updatedEService.toApi

    onComplete(result) {
      updateEServiceByIdResponse(operationLabel)(updateEServiceById200)
    }
  }

  private def hasNotDraftDescriptor(eService: readmodel.CatalogItem): Either[Throwable, Boolean] =
    Either
      .cond(
        eService.descriptors.count(_.state == readmodel.Draft) < 1,
        true,
        DraftDescriptorAlreadyExists(eService.id.toString)
      )

  private def eServiceCanBeUpdated(eService: readmodel.CatalogItem): Either[Throwable, Unit] = Either
    .cond(
      eService.descriptors.isEmpty ||
        (eService.descriptors.length == 1 && eService.descriptors.exists(_.state == readmodel.Draft)),
      (),
      EServiceCannotBeUpdated(eService.id.toString)
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

  private def descriptorCanBeSuspended(descriptor: readmodel.CatalogDescriptor): Future[readmodel.CatalogDescriptor] =
    descriptor.state match {
      case readmodel.Deprecated => Future.successful(descriptor)
      case readmodel.Published  => Future.successful(descriptor)
      case _                    => Future.failed(NotValidDescriptor(descriptor.id.toString, descriptor.state.toString))
    }

  private def descriptorCanBeActivated(descriptor: readmodel.CatalogDescriptor): Future[readmodel.CatalogDescriptor] =
    descriptor.state match {
      case readmodel.Suspended => Future.successful(descriptor)
      case _                   => Future.failed(NotValidDescriptor(descriptor.id.toString, descriptor.state.toString))
    }

  override def deleteEServiceDocumentById(eServiceId: String, descriptorId: String, documentId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    val operationLabel = s"Delete document $documentId of descriptor $descriptorId for EService $eServiceId"
    logger.info(operationLabel)

    val result: Future[Unit] = for {
      organizationId <- getOrganizationIdFutureUUID(contexts)
      eServiceUuid   <- eServiceId.toFutureUUID
      descriptorUuid <- descriptorId.toFutureUUID
      documentUuid   <- documentId.toFutureUUID
      catalogItem    <- getEService(eServiceUuid)
      _              <- assertRequesterAllowed(catalogItem.producerId)(organizationId)
      result         <- catalogManagementService.deleteEServiceDocument(
        eServiceUuid.toString,
        descriptorUuid.toString,
        documentUuid.toString
      )
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
      updateEServiceDescriptorDocumentSeed.toDependency

    val result: Future[EServiceDoc] = for {
      organizationId <- getOrganizationIdFutureUUID(contexts)
      eServiceUuid   <- eServiceId.toFutureUUID
      descriptorUuid <- descriptorId.toFutureUUID
      documentUuid   <- documentId.toFutureUUID
      catalogItem    <- getEService(eServiceUuid)
      _              <- assertRequesterAllowed(catalogItem.producerId)(organizationId)
      result         <- catalogManagementService
        .updateEServiceDocument(eServiceId, descriptorUuid.toString, documentUuid.toString, clientSeed)
        .map(_.toApi)
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
      eServiceUuid   <- eServiceId.toFutureUUID
      descriptorUuid <- descriptorId.toFutureUUID
      catalogItem    <- getEService(eServiceUuid)
      _              <- assertRequesterAllowed(catalogItem.producerId)(organizationId)
      clonedEService <- catalogManagementService.cloneEService(eServiceUuid, descriptorUuid)
    } yield clonedEService.toApi

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
        eServiceUuid   <- eServiceId.toFutureUUID
        catalogItem    <- ReadModelQueries.getEService(eServiceUuid)(readModel)
        eService       <- catalogItem.toFuture(EServiceNotFound(eServiceId))
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

    def activateDescriptor(eService: readmodel.CatalogItem, descriptor: readmodel.CatalogDescriptor): Future[Unit] = {
      val validState             = Seq(readmodel.Suspended, readmodel.Deprecated, readmodel.Published)
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
      eServiceUuid   <- eServiceId.toFutureUUID
      descriptorUuid <- descriptorId.toFutureUUID
      catalogItem    <- getEService(eServiceUuid)
      _              <- assertRequesterAllowed(catalogItem.producerId)(organizationId)
      descriptor     <- catalogItem.descriptors
        .find(_.id == descriptorUuid)
        .toFuture(EServiceDescriptorNotFound(eServiceId, descriptorId))
      _              <- descriptorCanBeActivated(descriptor)
      _              <- activateDescriptor(catalogItem, descriptor)
      _              <- authorizationManagementService.updateStateOnClients(
        catalogItem.id,
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
      eServiceUuid   <- eServiceId.toFutureUUID
      descriptorUuid <- descriptorId.toFutureUUID
      catalogItem    <- getEService(eServiceUuid)
      _              <- assertRequesterAllowed(catalogItem.producerId)(organizationId)
      descriptor     <- catalogItem.descriptors
        .find(_.id == descriptorUuid)
        .toFuture(EServiceDescriptorNotFound(eServiceId, descriptorId))
      _              <- descriptorCanBeSuspended(descriptor)
      _              <- catalogManagementService.suspendDescriptor(eServiceId, descriptorId)
      _              <- authorizationManagementService.updateStateOnClients(
        catalogItem.id,
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

  override def getEServiceConsumers(offset: Int, limit: Int, eServiceId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerEServiceConsumers: ToEntityMarshaller[EServiceConsumers],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE, M2M_ROLE, SUPPORT_ROLE) {
    val operationLabel =
      s"Retrieving consumers for EService $eServiceId"
    logger.info(operationLabel)

    val result: Future[EServiceConsumers] = for {
      result <- ReadModelQueries.listConsumers(eServiceId, offset, limit)(readModel)
      apiResults = result.results.map(_.toApi)
    } yield EServiceConsumers(results = apiResults, totalCount = result.totalCount)

    onComplete(result) {
      getEServiceConsumersResponse[EServiceConsumers](operationLabel)(getEServiceConsumers200)
    }
  }

  private def getEService(eServiceId: UUID): Future[readmodel.CatalogItem] = for {
    catalogItem <- ReadModelQueries.getEService(eServiceId)(readModel)
    eService    <- catalogItem.toFuture(EServiceNotFound(eServiceId.toString))
  } yield eService
}

object ProcessApiServiceImpl {
  def verifyPublicationEligibility(
    descriptor: readmodel.CatalogDescriptor
  )(implicit ec: ExecutionContext): Future[Unit] =
    isDraftDescriptor(descriptor) >> descriptor.interface
      .toFuture(EServiceDescriptorWithoutInterface(descriptor.id.toString))
      .void

  def isDraftDescriptor(descriptor: readmodel.CatalogDescriptor): Future[readmodel.CatalogDescriptor] =
    descriptor.state match {
      case readmodel.Draft => Future.successful(descriptor)
      case _               => Future.failed(NotValidDescriptor(descriptor.id.toString, descriptor.state.toString))
    }

  private def assertRequesterAllowed(resourceId: UUID)(requesterId: UUID)(implicit ec: ExecutionContext): Future[Unit] =
    Future.failed(GenericComponentErrors.OperationForbidden).unlessA(resourceId == requesterId)

}
