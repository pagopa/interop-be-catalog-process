package it.pagopa.interop.catalogprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import cats.syntax.all._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementmanagement.model.agreement.PersistentAgreementState
import it.pagopa.interop.authorizationmanagement.client.{model => AuthorizationManagementDependency}
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagementDependency}
import it.pagopa.interop.catalogmanagement.model.{
  CatalogDescriptor,
  CatalogDescriptorState,
  CatalogItem,
  Deliver,
  Deprecated,
  Draft,
  Published,
  Receive,
  Suspended
}
import it.pagopa.interop.catalogprocess.api.ProcessApiService
import it.pagopa.interop.catalogprocess.api.impl.Converter._
import it.pagopa.interop.catalogprocess.api.impl.ResponseHandlers._
import it.pagopa.interop.catalogprocess.common.readmodel.{PaginatedResult, ReadModelCatalogQueries}
import it.pagopa.interop.catalogprocess.errors.CatalogProcessErrors._
import it.pagopa.interop.catalogprocess.model._
import it.pagopa.interop.catalogprocess.service._
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import it.pagopa.interop.commons.jwt._
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.riskanalysis.api.impl.RiskAnalysisValidation
import it.pagopa.interop.commons.riskanalysis.{model => Commons}
import it.pagopa.interop.commons.utils.AkkaUtils._
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.{ComponentError, GenericComponentErrors}
import it.pagopa.interop.catalogmanagement.model.CatalogItemMode
import it.pagopa.interop.agreementmanagement.model.agreement.{
  Active => AgreementActive,
  Suspended => AgreementSuspended
}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final case class ProcessApiServiceImpl(
  catalogManagementService: CatalogManagementService,
  agreementManagementService: AgreementManagementService,
  authorizationManagementService: AuthorizationManagementService,
  attributeRegistryManagementService: AttributeRegistryManagementService,
  tenantManagementService: TenantManagementService
)(implicit ec: ExecutionContext, readModel: ReadModelService)
    extends ProcessApiService {

  import ProcessApiServiceImpl._

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  val IPA = "IPA"

  override def createEService(eServiceSeed: EServiceSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerEService: ToEntityMarshaller[EService]
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    val operationLabel: String = s"Creating EService with service name ${eServiceSeed.name}"
    logger.info(operationLabel)

    val result: Future[EService] = for {
      organizationId <- getOrganizationIdFutureUUID(contexts)
      origin         <- getExternalIdOriginFuture(contexts)
      _              <- if (origin == IPA) Future.unit else Future.failed(OriginIsNotCompliant(IPA))
      clientSeed = eServiceSeed.toDependency(organizationId)
      _               <- checkDuplicateName(organizationId, None, eServiceSeed.name, clientSeed.producerId)
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
      catalogItem    <- catalogManagementService.getEServiceById(eServiceUuid)
      _              <- descriptorDeletable(catalogItem, descriptorId).toFuture
      _              <- assertRequesterAllowed(catalogItem.producerId)(organizationId)
      result         <- catalogManagementService.deleteDraft(eServiceId, descriptorId)
    } yield result

    onComplete(result) {
      deleteDraftResponse[Unit](operationLabel)(_ => deleteDraft204)
    }
  }

  private def descriptorDeletable(catalogItem: CatalogItem, descriptorId: String): Either[ComponentError, Unit] =
    getDescriptor(catalogItem, descriptorId).flatMap(descriptor =>
      Left(NotValidDescriptor(descriptorId, descriptor.state.toString))
        .withRight[Unit]
        .unlessA(descriptor.state == Draft)
    )

  private def getDescriptor(
    eService: CatalogItem,
    descriptorId: String
  ): Either[EServiceDescriptorNotFound, CatalogDescriptor] =
    eService.descriptors
      .find(_.id.toString == descriptorId)
      .toRight(EServiceDescriptorNotFound(eService.id.toString, descriptorId))

  override def getEServices(
    name: Option[String],
    eServicesIds: String,
    producersIds: String,
    attributesIds: String,
    states: String,
    agreementStates: String,
    mode: Option[String],
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
        eServicesIds: List[UUID],
        producersIds: List[UUID],
        attributesIds: List[UUID],
        states: Seq[CatalogDescriptorState],
        agreementStates: Seq[PersistentAgreementState],
        mode: Option[CatalogItemMode],
        offset: Int,
        limit: Int
      ): Future[PaginatedResult[CatalogItem]] = {

        if (agreementStates.isEmpty)
          catalogManagementService.getEServices(
            requesterId = organizationId,
            name = name,
            eServicesIds = eServicesIds,
            producersIds = producersIds,
            attributesIds = attributesIds,
            states = states,
            mode = mode,
            offset = offset,
            limit = limit
          )
        else
          for {
            agreementEservicesIds <- agreementManagementService
              .getAgreements(
                eServicesIds = eServicesIds,
                producersIds = Nil,
                consumersIds = Seq(organizationId),
                descriptorsIds = Nil,
                states = agreementStates
              )
              .map(_.map(_.eserviceId))
            result                <-
              if (agreementEservicesIds.isEmpty)
                Future.successful(ReadModelCatalogQueries.emptyResults[CatalogItem])
              else
                catalogManagementService.getEServices(
                  requesterId = organizationId,
                  name = name,
                  eServicesIds = agreementEservicesIds,
                  producersIds = producersIds,
                  attributesIds = attributesIds,
                  states = states,
                  mode = mode,
                  offset = offset,
                  limit = limit
                )
          } yield result
      }

      val result: Future[EServices] = for {
        organizationId  <- getOrganizationIdFutureUUID(contexts)
        states          <- parseArrayParameters(states).traverse(EServiceDescriptorState.fromValue).toFuture
        mode            <- mode.traverse(EServiceMode.fromValue).toFuture
        producersUuids  <- parseArrayParameters(producersIds).traverse(_.toFutureUUID)
        eServicesUuids  <- parseArrayParameters(eServicesIds).traverse(_.toFutureUUID)
        attributesUuids <- parseArrayParameters(attributesIds).traverse(_.toFutureUUID)
        agreementStates <- parseArrayParameters(agreementStates)
          .traverse(AgreementState.fromValue)
          .toFuture
        eServices       <-
          getEservicesInner(
            organizationId,
            name,
            eServicesUuids,
            producersUuids,
            attributesUuids,
            states.map(_.toPersistent),
            agreementStates.map(_.toPersistent),
            mode.map(_.toPersistent),
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

    def changeStateOfOldDescriptorOrCancelPublication(
      eServiceId: UUID,
      oldDescriptorId: UUID,
      descriptorId: UUID,
      validStates: Seq[PersistentAgreementState]
    ): Future[Unit] = for {
      validAgreements <- agreementManagementService.getAgreements(
        List(eServiceId),
        Nil,
        Nil,
        List(oldDescriptorId),
        validStates
      )
      _               <- validAgreements.headOption match {
        case Some(_) =>
          deprecateDescriptor(oldDescriptorId.toString, eServiceId.toString).recoverWith(error =>
            resetDescriptorToDraft(eServiceId.toString, descriptorId.toString).flatMap(_ => Future.failed(error))
          )
        case None    =>
          catalogManagementService.archiveDescriptor(eServiceId.toString, oldDescriptorId.toString) recoverWith (
            error =>
              resetDescriptorToDraft(eServiceId.toString, descriptorId.toString).flatMap(_ => Future.failed(error))
          )
      }
    } yield ()

    def verifyRiskAnalysisForPublication(catalogItem: CatalogItem): Future[Unit] = catalogItem.mode match {
      case Deliver => Future.unit
      case Receive =>
        for {
          _          <-
            if (catalogItem.riskAnalysis.isEmpty) Future.failed(EServiceRiskAnalysisIsRequired(catalogItem.id))
            else Future.unit
          tenant     <- tenantManagementService.getTenantById(catalogItem.producerId)
          tenantKind <- tenant.kind.toFuture(TenantKindNotFound(tenant.id))
          _          <- catalogItem.riskAnalysis.traverse(risk =>
            isRiskAnalysisFormValid(riskAnalysisForm = risk.riskAnalysisForm.toTemplate, schemaOnlyValidation = false)(
              tenantKind.toTemplate
            )
          )
        } yield ()
    }

    val result: Future[Unit] = for {
      organizationId <- getOrganizationIdFutureUUID(contexts)
      eServiceUuid   <- eServiceId.toFutureUUID
      descriptorUuid <- descriptorId.toFutureUUID
      catalogItem    <- catalogManagementService.getEServiceById(eServiceUuid)
      _              <- assertRequesterAllowed(catalogItem.producerId)(organizationId)
      descriptor     <- assertDescriptorExists(catalogItem, descriptorUuid)
      _              <- verifyPublicationEligibility(descriptor)
      _              <- verifyRiskAnalysisForPublication(catalogItem)
      currentActiveDescriptor = catalogItem.descriptors.find(d => d.state == Published) // Must be at most one
      _ <- catalogManagementService.publishDescriptor(eServiceId, descriptorId)
      _ <- currentActiveDescriptor
        .map(oldDescriptor =>
          changeStateOfOldDescriptorOrCancelPublication(
            eServiceId = eServiceUuid,
            oldDescriptorId = oldDescriptor.id,
            descriptorId = descriptorUuid,
            validStates = List(AgreementActive, AgreementSuspended)
          )
        )
        .sequence
      _ <- authorizationManagementService.updateStateOnClients(
        catalogItem.id,
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

  private def applyVisibilityToEService(
    catalogItem: CatalogItem,
    organizationId: UUID,
    role: String
  ): Future[CatalogItem] = {
    if (
      Seq(ADMIN_ROLE, API_ROLE)
        .intersect(role.split(",").toList.map(_.trim()))
        .nonEmpty && catalogItem.producerId == organizationId
    )
      Future.successful(catalogItem)
    else if (catalogItem.descriptors.forall(_.state == Draft))
      Future.failed(EServiceNotFound(catalogItem.id.toString))
    else
      Future.successful(catalogItem.copy(descriptors = catalogItem.descriptors.filterNot(_.state == Draft)))
  }

  override def getEServiceById(eServiceId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerEService: ToEntityMarshaller[EService]
  ): Route = authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE, M2M_ROLE, SUPPORT_ROLE) {
    val operationLabel = s"Retrieving EService $eServiceId"
    logger.info(operationLabel)

    val result: Future[EService] = for {
      organizationId <- getOrganizationIdFutureUUID(contexts)
      eServiceUuid   <- eServiceId.toFutureUUID
      role           <- getUserRolesFuture(contexts)
      eService       <- catalogManagementService.getEServiceById(eServiceUuid)
      catalogItem    <- applyVisibilityToEService(eService, organizationId, role)
    } yield catalogItem.toApi

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
      descriptorUuid <- descriptorId.toFutureUUID
      catalogItem    <- catalogManagementService.getEServiceById(eServiceUuid)
      _              <- assertRequesterAllowed(catalogItem.producerId)(organizationId)
      descriptor     <- assertDescriptorExists(catalogItem, descriptorUuid)
      _              <- isDraftDescriptor(descriptor)
      _              <-
        if (documentSeed.kind == EServiceDocumentKind.INTERFACE) assertInterfaceDoesNotExists(descriptor)
        else Future.unit
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

    val result: Future[EServiceDoc] = for {
      organizationId                 <- getOrganizationIdFutureUUID(contexts)
      eServiceUuid                   <- eServiceId.toFutureUUID
      descriptorUuid                 <- descriptorId.toFutureUUID
      documentIdUuid                 <- documentId.toFutureUUID
      role                           <- getUserRolesFuture(contexts)
      (catalogItem, catalogDocument) <- catalogManagementService.getEServiceDocument(
        eServiceUuid,
        descriptorUuid,
        documentIdUuid
      )
      eService                       <- applyVisibilityToEService(catalogItem, organizationId, role)
      _                              <-
        if (eService.descriptors.map(_.id).contains(descriptorUuid)) Future.successful(())
        else Future.failed(DescriptorDocumentNotFound(eServiceId, descriptorId, documentId))
    } yield catalogDocument.toApi

    onComplete(result) {
      getEServiceDocumentByIdResponse[EServiceDoc](operationLabel)(getEServiceDocumentById200)
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
      catalogItem               <- catalogManagementService.getEServiceById(eServiceUuid)
      _                         <- assertRequesterAllowed(catalogItem.producerId)(organizationId)
      _                         <- hasNotDraftDescriptor(catalogItem).toFuture
      _                         <- eServiceDescriptorSeed.attributes.certified.traverse(
        _.traverse(attr => attributeRegistryManagementService.getAttributeById(attr.id))
      )
      _                         <- eServiceDescriptorSeed.attributes.declared.traverse(
        _.traverse(attr => attributeRegistryManagementService.getAttributeById(attr.id))
      )
      _                         <- eServiceDescriptorSeed.attributes.verified.traverse(
        _.traverse(attr => attributeRegistryManagementService.getAttributeById(attr.id))
      )
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
      descriptorUuid  <- descriptorId.toFutureUUID
      catalogItem     <- catalogManagementService.getEServiceById(eServiceUuid)
      _               <- assertRequesterAllowed(catalogItem.producerId)(organizationId)
      descriptor      <- assertDescriptorExists(catalogItem, descriptorUuid)
      _               <- isDraftDescriptor(descriptor)
      updatedEService <- catalogManagementService.updateDescriptor(
        eServiceId,
        descriptorId,
        updateEServiceDescriptorSeed.toDependency
      )
    } yield updatedEService.toApi

    onComplete(result) {
      updateDraftDescriptorResponse[EService](operationLabel)(updateDraftDescriptor200)
    }
  }

  override def updatePublishedDescriptor(
    eServiceId: String,
    descriptorId: String,
    seed: UpdateEServicePublishedDescriptorSeed
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerEService: ToEntityMarshaller[EService]
  ): Route =
    authorize(ADMIN_ROLE, API_ROLE) {
      val operationLabel = s"Update Published Descriptor $descriptorId for EService $eServiceId"
      logger.info(operationLabel)

      val result: Future[EService] = for {
        organizationId  <- getOrganizationIdFutureUUID(contexts)
        eServiceUuid    <- eServiceId.toFutureUUID
        descriptorUuid  <- descriptorId.toFutureUUID
        catalogItem     <- catalogManagementService.getEServiceById(eServiceUuid)
        _               <- assertRequesterAllowed(catalogItem.producerId)(organizationId)
        descriptor      <- assertDescriptorExists(catalogItem, descriptorUuid)
        _               <- descriptorCanBeUpdated(descriptor)
        updatedEService <- catalogManagementService.updateDescriptor(
          eServiceId,
          descriptorId,
          seed.toDependency(descriptor)
        )
      } yield updatedEService.toApi

      onComplete(result) {
        updatePublishedDescriptorResponse[EService](operationLabel)(updatePublishedDescriptor200)
      }
    }

  private def checkDuplicateName(requesterId: UUID, eServiceId: Option[UUID], name: String, producerId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = for {
    result <- catalogManagementService
      .getEServices(
        requesterId,
        name.some,
        Seq.empty,
        Seq(producerId),
        Seq.empty,
        Seq.empty,
        None,
        0,
        1,
        exactMatchOnName = true
      )
    eservice = eServiceId.fold(result.results)(id => result.results.filterNot(_.id == id))
    _ <- eservice.headOption.map(_.name).fold(Future.unit)(_ => Future.failed(DuplicatedEServiceName(name)))
  } yield ()

  override def updateEServiceById(eServiceId: String, updateEServiceSeed: UpdateEServiceSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerEService: ToEntityMarshaller[EService]
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    val operationLabel = s"Updating EService by id $eServiceId"
    logger.info(operationLabel)

    val result = for {
      organizationId <- getOrganizationIdFutureUUID(contexts)
      eServiceUuid   <- eServiceId.toFutureUUID
      catalogItem    <- catalogManagementService.getEServiceById(eServiceUuid)
      _              <- assertRequesterAllowed(catalogItem.producerId)(organizationId)
      _              <- eServiceCanBeUpdated(catalogItem).toFuture
      _ <- checkDuplicateName(organizationId, Some(eServiceUuid), updateEServiceSeed.name, catalogItem.producerId)
      _ <- deleteRiskAnalysisOnModeUpdate(updateEServiceSeed.mode, catalogItem)
      updatedEService <- catalogManagementService.updateEServiceById(eServiceId, updateEServiceSeed.toDependency)
    } yield updatedEService.toApi

    onComplete(result) {
      updateEServiceByIdResponse(operationLabel)(updateEServiceById200)
    }
  }

  private def deleteRiskAnalysisOnModeUpdate(newMode: EServiceMode, catalogItem: CatalogItem)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] =
    if (newMode == EServiceMode.DELIVER)
      Future
        .traverse(catalogItem.riskAnalysis)(risk =>
          catalogManagementService.deleteRiskAnalysis(catalogItem.id, risk.id)
        )
        .map(_ => ())
    else Future.unit

  private def hasNotDraftDescriptor(eService: CatalogItem): Either[Throwable, Boolean] =
    Either
      .cond(eService.descriptors.count(_.state == Draft) < 1, true, DraftDescriptorAlreadyExists(eService.id.toString))

  private def eServiceCanBeUpdated(eService: CatalogItem): Either[Throwable, Unit] = Either
    .cond(
      eService.descriptors.isEmpty ||
        (eService.descriptors.length == 1 && eService.descriptors.exists(_.state == Draft)),
      (),
      EServiceCannotBeUpdated(eService.id.toString)
    )

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

  private def descriptorCanBeSuspended(descriptor: CatalogDescriptor): Future[CatalogDescriptor] =
    descriptor.state match {
      case Deprecated => Future.successful(descriptor)
      case Published  => Future.successful(descriptor)
      case _          => Future.failed(NotValidDescriptor(descriptor.id.toString, descriptor.state.toString))
    }

  private def descriptorCanBeUpdated(descriptor: CatalogDescriptor): Future[CatalogDescriptor] =
    descriptor.state match {
      case Deprecated => Future.successful(descriptor)
      case Suspended  => Future.successful(descriptor)
      case Published  => Future.successful(descriptor)
      case _          => Future.failed(NotValidDescriptor(descriptor.id.toString, descriptor.state.toString))
    }

  private def descriptorCanBeActivated(descriptor: CatalogDescriptor): Future[CatalogDescriptor] =
    descriptor.state match {
      case Suspended => Future.successful(descriptor)
      case _         => Future.failed(NotValidDescriptor(descriptor.id.toString, descriptor.state.toString))
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
      catalogItem    <- catalogManagementService.getEServiceById(eServiceUuid)
      _              <- assertRequesterAllowed(catalogItem.producerId)(organizationId)
      descriptor     <- assertDescriptorExists(catalogItem, descriptorUuid)
      _              <- isDraftDescriptor(descriptor)
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
      catalogItem    <- catalogManagementService.getEServiceById(eServiceUuid)
      _              <- assertRequesterAllowed(catalogItem.producerId)(organizationId)
      descriptor     <- assertDescriptorExists(catalogItem, descriptorUuid)
      _              <- isDraftDescriptor(descriptor)
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
      catalogItem    <- catalogManagementService.getEServiceById(eServiceUuid)
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
        catalogItem    <- catalogManagementService.getEServiceById(eServiceUuid)
        _              <- assertRequesterAllowed(catalogItem.producerId)(organizationId)
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

    def activateDescriptor(eService: CatalogItem, descriptor: CatalogDescriptor): Future[Unit] = {
      val validState             = Seq(Suspended, Deprecated, Published)
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
      catalogItem    <- catalogManagementService.getEServiceById(eServiceUuid)
      _              <- assertRequesterAllowed(catalogItem.producerId)(organizationId)
      descriptor     <- assertDescriptorExists(catalogItem, descriptorUuid)
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
      catalogItem    <- catalogManagementService.getEServiceById(eServiceUuid)
      _              <- assertRequesterAllowed(catalogItem.producerId)(organizationId)
      descriptor     <- assertDescriptorExists(catalogItem, descriptorUuid)
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
      eServiceUuid <- eServiceId.toFutureUUID
      result       <- catalogManagementService.getConsumers(eServiceUuid, offset, limit)
    } yield EServiceConsumers(results = result.results.map(_.toApi), totalCount = result.totalCount)

    onComplete(result) {
      getEServiceConsumersResponse[EServiceConsumers](operationLabel)(getEServiceConsumers200)
    }
  }

  override def archiveDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(INTERNAL_ROLE) {
    val operationLabel = s"Archiving descriptor $descriptorId of EService $eServiceId"
    logger.info(operationLabel)

    val result = for {
      eServiceUuid   <- eServiceId.toFutureUUID
      descriptorUuid <- descriptorId.toFutureUUID
      catalogItem    <- catalogManagementService.getEServiceById(eServiceUuid)
      _              <- assertDescriptorExists(catalogItem, descriptorUuid)
      _              <- catalogManagementService.archiveDescriptor(eServiceId, descriptorId)
    } yield ()

    onComplete(result) {
      suspendDescriptorResponse[Unit](operationLabel)(_ => archiveDescriptor204)
    }
  }

  override def createRiskAnalysis(eServiceId: String, seed: EServiceRiskAnalysisSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    val operationLabel = s"Create Risk Analysis for EService $eServiceId"
    logger.info(operationLabel)

    val result = for {
      organizationId <- getOrganizationIdFutureUUID(contexts)
      eServiceUuid   <- eServiceId.toFutureUUID
      catalogItem    <- catalogManagementService.getEServiceById(eServiceUuid)
      _              <- isDraftEService(catalogItem)
      _              <- isReceiveEService(catalogItem)
      _              <- assertRequesterAllowed(catalogItem.producerId)(organizationId)
      tenant         <- tenantManagementService.getTenantById(organizationId)
      tenantKind     <- tenant.kind.toFuture(TenantKindNotFound(tenant.id))
      depSeed        <- seed.toDependency(schemaOnlyValidation = true)(tenantKind).toFuture
      _              <- catalogManagementService.createRiskAnalysis(eServiceUuid, depSeed)
    } yield ()

    onComplete(result) {
      createRiskAnalysisResponse[Unit](operationLabel)(_ => createRiskAnalysis204)
    }
  }

  override def updateRiskAnalysis(eServiceId: String, riskAnalysisId: String, seed: EServiceRiskAnalysisSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    val operationLabel = s"Update a Risk Analysis for EService $eServiceId"
    logger.info(operationLabel)

    val result = for {
      organizationId   <- getOrganizationIdFutureUUID(contexts)
      eServiceUuid     <- eServiceId.toFutureUUID
      riskAnalysisUuid <- riskAnalysisId.toFutureUUID
      catalogItem      <- catalogManagementService.getEServiceById(eServiceUuid)
      _                <- isDraftEService(catalogItem)
      _                <- isReceiveEService(catalogItem)
      _                <- assertRequesterAllowed(catalogItem.producerId)(organizationId)
      tenant           <- tenantManagementService.getTenantById(organizationId)
      tenantKind       <- tenant.kind.toFuture(TenantKindNotFound(tenant.id))
      depSeed          <- seed.toDependency(schemaOnlyValidation = true)(tenantKind).toFuture
      _                <- catalogManagementService.updateRiskAnalysis(eServiceUuid, riskAnalysisUuid, depSeed)
    } yield ()

    onComplete(result) {
      updateRiskAnalysisResponse[Unit](operationLabel)(_ => updateRiskAnalysis204)
    }
  }

  override def deleteRiskAnalysis(eServiceId: String, riskAnalysisId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    val operationLabel = s"Delete a Risk Analysis $riskAnalysisId for EService $eServiceId"
    logger.info(operationLabel)

    val result = for {
      eServiceUuid     <- eServiceId.toFutureUUID
      riskAnalysisUuid <- riskAnalysisId.toFutureUUID
      catalogItem      <- catalogManagementService.getEServiceById(eServiceUuid)
      _                <- isDraftEService(catalogItem)
      _                <- isReceiveEService(catalogItem)
      _                <- catalogManagementService.deleteRiskAnalysis(eServiceUuid, riskAnalysisUuid)
    } yield ()

    onComplete(result) {
      deleteRiskAnalysisResponse[Unit](operationLabel)(_ => deleteRiskAnalysis204)
    }
  }
}

object ProcessApiServiceImpl {
  def verifyPublicationEligibility(descriptor: CatalogDescriptor)(implicit ec: ExecutionContext): Future[Unit] =
    isDraftDescriptor(descriptor) >> descriptor.interface
      .toFuture(EServiceDescriptorWithoutInterface(descriptor.id.toString))
      .void

  def isDraftDescriptor(descriptor: CatalogDescriptor): Future[CatalogDescriptor] =
    descriptor.state match {
      case Draft => Future.successful(descriptor)
      case _     => Future.failed(NotValidDescriptor(descriptor.id.toString, descriptor.state.toString))
    }

  def isRiskAnalysisFormValid(riskAnalysisForm: Commons.RiskAnalysisForm, schemaOnlyValidation: Boolean)(
    kind: Commons.RiskAnalysisTenantKind
  ): Future[Unit] =
    if (
      RiskAnalysisValidation
        .validate(riskAnalysisForm, schemaOnlyValidation)(kind)
        .isValid
    ) Future.unit
    else Future.failed(RiskAnalysisNotValid)

  def isReceiveEService(eService: CatalogItem): Future[Unit] =
    if (eService.mode == Receive) Future.unit else Future.failed(EServiceNotInReceiveMode(eService.id))

  def isDraftEService(eService: CatalogItem): Future[Unit] =
    if (eService.descriptors.isEmpty || eService.descriptors.map(_.state) == Seq(Draft)) Future.unit
    else Future.failed(EServiceNotInDraftState(eService.id))

  def assertRequesterAllowed(resourceId: UUID)(requesterId: UUID)(implicit ec: ExecutionContext): Future[Unit] =
    Future.failed(GenericComponentErrors.OperationForbidden).unlessA(resourceId == requesterId)

  def assertDescriptorExists(eService: CatalogItem, descriptorId: UUID): Future[CatalogDescriptor] =
    eService.descriptors
      .find(_.id == descriptorId)
      .toFuture(EServiceDescriptorNotFound(eService.id.toString, descriptorId.toString))

  def assertInterfaceDoesNotExists(descriptor: CatalogDescriptor): Future[Unit] =
    descriptor.interface match {
      case Some(_) => Future.failed(InterfaceAlreadyExists(descriptor.id))
      case None    => Future.unit
    }
}
