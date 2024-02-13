package it.pagopa.interop.catalogprocess

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import it.pagopa.interop.agreementmanagement.model.agreement.{
  Active => AgreementActive,
  Suspended => AgreementSuspended,
  PersistentAgreementState
}
import it.pagopa.interop.authorizationmanagement.client.{model => AuthorizationManagementDependency}
import it.pagopa.interop.catalogmanagement.client.model.AgreementApprovalPolicy.AUTOMATIC
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagementDependency}
import it.pagopa.interop.catalogmanagement.model._
import it.pagopa.interop.catalogprocess.api.impl.Converter._
import it.pagopa.interop.catalogprocess.api.impl._
import it.pagopa.interop.catalogprocess.common.readmodel.{Consumers, PaginatedResult}
import it.pagopa.interop.catalogprocess.errors.CatalogProcessErrors.{
  DescriptorDocumentNotFound,
  EServiceNotFound,
  EServiceRiskAnalysisNotFound,
  AttributeNotFound,
  EServiceWithDescriptorsNotDeletable
}
import it.pagopa.interop.catalogprocess.model._
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import it.pagopa.interop.commons.utils._
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class CatalogProcessSpec extends SpecHelper with AnyWordSpecLike with ScalatestRouteTest {

  "Eservice retrieve" should {
    "succeed when found with role admin and requester is the producer " in {
      val eServiceId  = UUID.randomUUID()
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eServiceId, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem.copy(producerId = requesterId)))

      Get() ~> service.getEServiceById(eServiceId.toString) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    "succeed when found with role api and requester is the producer " in {
      val eServiceId  = UUID.randomUUID()
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "api", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eServiceId, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem.copy(producerId = requesterId)))

      Get() ~> service.getEServiceById(eServiceId.toString) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    "fail with 404 when not found" in {
      val eServiceId  = UUID.randomUUID()
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eServiceId, *, *)
        .once()
        .returns(Future.failed(EServiceNotFound(eServiceId.toString)))

      Get() ~> service.getEServiceById(eServiceId.toString) ~> check {
        status shouldEqual StatusCodes.NotFound
        val problem = responseAs[Problem]
        problem.status shouldBe StatusCodes.NotFound.intValue
        problem.errors.head.code shouldBe "009-0007"
      }
    }
    "fail with 404 when eservice has no descriptor and requester is not the producer" in {
      val eServiceId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eServiceId, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem))

      Get() ~> service.getEServiceById(eServiceId.toString) ~> check {
        status shouldEqual StatusCodes.NotFound
        val problem = responseAs[Problem]
        problem.status shouldBe StatusCodes.NotFound.intValue
        problem.errors.head.code shouldBe "009-0007"
      }
    }
    "fail with 404 when eservice has only a draft descriptor and requester is not the producer" in {
      val eServiceId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eServiceId, *, *)
        .once()
        .returns(
          Future
            .successful(SpecData.catalogItem.copy(descriptors = Seq(SpecData.catalogDescriptor.copy(state = Draft))))
        )

      Get() ~> service.getEServiceById(eServiceId.toString) ~> check {
        status shouldEqual StatusCodes.NotFound
        val problem = responseAs[Problem]
        problem.status shouldBe StatusCodes.NotFound.intValue
        problem.errors.head.code shouldBe "009-0007"
      }
    }
    "succeed when eservice has several descriptors and requester is not the producer" in {
      val eServiceId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eServiceId, *, *)
        .once()
        .returns(
          Future
            .successful(
              SpecData.catalogItem
                .copy(descriptors = Seq(SpecData.catalogDescriptor, SpecData.catalogDescriptor.copy(state = Draft)))
            )
        )

      Get() ~> service.getEServiceById(eServiceId.toString) ~> check {
        status shouldEqual StatusCodes.OK
        val response: EService = responseAs[EService]
        response.descriptors.filterNot(_.state == EServiceDescriptorState.DRAFT).size shouldEqual 1
      }
    }
  }
  "EServices retrieve" should {

    "succeed when Agreement States are empty" in {

      val eServiceId  = UUID.randomUUID()
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val name            = None
      val eServicesIds    = Seq(eServiceId)
      val producersIds    = Seq.empty
      val attributesIds   = Seq.empty
      val agreementStates = Seq.empty
      val states          = Seq.empty
      val mode            = None
      val offset          = 0
      val limit           = 50

      (mockCatalogManagementService
        .getEServices(
          _: UUID,
          _: Option[String],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[CatalogDescriptorState],
          _: Option[CatalogItemMode],
          _: Int,
          _: Int,
          _: Boolean
        )(_: ExecutionContext, _: ReadModelService, _: Seq[(String, String)]))
        .expects(
          requesterId,
          name,
          eServicesIds,
          producersIds,
          attributesIds,
          states,
          mode,
          offset,
          limit,
          false,
          *,
          *,
          context
        )
        .once()
        .returns(Future.successful(PaginatedResult(results = Seq(SpecData.catalogItem), 1)))

      Get() ~> service.getEServices(
        name,
        eServicesIds.mkString(","),
        producersIds.mkString(","),
        attributesIds.mkString(","),
        states.mkString(","),
        agreementStates.mkString(","),
        mode,
        offset,
        limit
      ) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    "succeed when Agreement States are not empty and EServices from agreements are not empty" in {

      val requesterId = UUID.randomUUID()
      val eServiceId  = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val name            = None
      val eServicesIds    = Seq(eServiceId)
      val producersIds    = Seq.empty
      val attributesIds   = Seq.empty
      val agreementStates = Seq("ACTIVE")
      val states          = Seq.empty
      val mode            = None
      val offset          = 0
      val limit           = 50

      (mockAgreementManagementService
        .getAgreements(_: Seq[UUID], _: Seq[UUID], _: Seq[UUID], _: Seq[UUID], _: Seq[PersistentAgreementState])(
          _: ExecutionContext,
          _: ReadModelService
        ))
        .expects(eServicesIds, Seq(requesterId), producersIds, Nil, Seq(AgreementActive), *, *)
        .once()
        .returns(Future.successful(Seq(SpecData.agreement)))

      (mockCatalogManagementService
        .getEServices(
          _: UUID,
          _: Option[String],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[CatalogDescriptorState],
          _: Option[CatalogItemMode],
          _: Int,
          _: Int,
          _: Boolean
        )(_: ExecutionContext, _: ReadModelService, _: Seq[(String, String)]))
        .expects(
          requesterId,
          name,
          Seq(SpecData.agreement.eserviceId),
          producersIds,
          attributesIds,
          states,
          mode,
          offset,
          limit,
          false,
          *,
          *,
          context
        )
        .once()
        .returns(Future.successful(PaginatedResult(results = Seq(SpecData.catalogItem), 1)))

      Get() ~> service.getEServices(
        name,
        eServicesIds.mkString(","),
        producersIds.mkString(","),
        attributesIds.mkString(","),
        states.mkString(","),
        agreementStates.mkString(","),
        mode,
        offset,
        limit
      ) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    "succeed when Agreement States are not empty and EServices from agreements are empty" in {

      val requesterId = UUID.randomUUID()
      val eServiceId  = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val name            = None
      val eServicesIds    = Seq(eServiceId)
      val producersIds    = Seq.empty
      val attributesIds   = Seq.empty
      val agreementStates = Seq("ACTIVE")
      val states          = Seq.empty
      val mode            = None
      val offset          = 0
      val limit           = 50

      (mockAgreementManagementService
        .getAgreements(_: Seq[UUID], _: Seq[UUID], _: Seq[UUID], _: Seq[UUID], _: Seq[PersistentAgreementState])(
          _: ExecutionContext,
          _: ReadModelService
        ))
        .expects(eServicesIds, Seq(requesterId), producersIds, Nil, Seq(AgreementActive), *, *)
        .once()
        .returns(Future.successful(Seq.empty))

      Get() ~> service.getEServices(
        name,
        eServicesIds.mkString(","),
        producersIds.mkString(","),
        attributesIds.mkString(","),
        states.mkString(","),
        agreementStates.mkString(","),
        mode,
        offset,
        limit
      ) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }

  "Consumers retrieve" should {

    "succeed" in {

      val eServiceId  = UUID.randomUUID()
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val offset = 0
      val limit  = 50

      (mockCatalogManagementService
        .getConsumers(_: UUID, _: Int, _: Int)(_: ExecutionContext, _: ReadModelService))
        .expects(eServiceId, offset, limit, *, *)
        .once()
        .returns(
          Future.successful(
            PaginatedResult(
              results = Seq(
                Consumers(
                  descriptorVersion = SpecData.catalogDescriptor.version,
                  descriptorState = SpecData.catalogDescriptor.state,
                  agreementState = SpecData.agreement.state,
                  consumerName = "name",
                  consumerExternalId = "extId"
                )
              ),
              1
            )
          )
        )

      Get() ~> service.getEServiceConsumers(offset, limit, eServiceId.toString) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }

  "EService creation" should {

    "succeed" in {

      val requesterId = UUID.randomUUID()

      val attributeId1: UUID = UUID.randomUUID()
      val attributeId2: UUID = UUID.randomUUID()
      val attributeId3: UUID = UUID.randomUUID()

      val catalogItems: Seq[CatalogItem] = Seq.empty

      implicit val context: Seq[(String, String)] =
        Seq(
          "bearer"                        -> bearerToken,
          USER_ROLES                      -> "admin",
          ORGANIZATION_ID_CLAIM           -> requesterId.toString,
          ORGANIZATION_EXTERNAL_ID_ORIGIN -> "IPA",
          ORGANIZATION_EXTERNAL_ID_VALUE  -> "12345"
        )

      val apiSeed: EServiceSeed =
        EServiceSeed(
          name = "MyService",
          description = "My Service",
          technology = EServiceTechnology.REST,
          mode = EServiceMode.DELIVER
        )

      val seed = CatalogManagementDependency.EServiceSeed(
        producerId = requesterId,
        name = "MyService",
        description = "My Service",
        technology = CatalogManagementDependency.EServiceTechnology.REST,
        mode = CatalogManagementDependency.EServiceMode.DELIVER
      )

      val eservice = CatalogManagementDependency.EService(
        id = UUID.randomUUID(),
        producerId = seed.producerId,
        name = seed.name,
        description = seed.description,
        technology = seed.technology,
        descriptors = List(
          CatalogManagementDependency.EServiceDescriptor(
            id = UUID.randomUUID(),
            version = "1",
            description = None,
            interface = None,
            docs = Nil,
            state = CatalogManagementDependency.EServiceDescriptorState.DRAFT,
            audience = List("aud1"),
            voucherLifespan = 1000,
            dailyCallsPerConsumer = 1000,
            dailyCallsTotal = 0,
            agreementApprovalPolicy = AUTOMATIC,
            serverUrls = Nil,
            attributes = CatalogManagementDependency.Attributes(
              certified =
                List(List(CatalogManagementDependency.Attribute(attributeId1, explicitAttributeVerification = false))),
              declared =
                List(List(CatalogManagementDependency.Attribute(attributeId2, explicitAttributeVerification = false))),
              verified =
                List(List(CatalogManagementDependency.Attribute(attributeId3, explicitAttributeVerification = true)))
            )
          )
        ),
        riskAnalysis = Seq.empty,
        mode = CatalogManagementDependency.EServiceMode.DELIVER
      )

      (mockCatalogManagementService
        .getEServices(
          _: UUID,
          _: Option[String],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[CatalogDescriptorState],
          _: Option[CatalogItemMode],
          _: Int,
          _: Int,
          _: Boolean
        )(_: ExecutionContext, _: ReadModelService, _: Seq[(String, String)]))
        .expects(
          requesterId,
          Some(seed.name),
          Seq.empty,
          Seq(seed.producerId),
          Seq.empty,
          Seq.empty,
          None,
          0,
          1,
          true,
          *,
          *,
          context
        )
        .once()
        .returns(Future.successful(PaginatedResult(results = catalogItems, catalogItems.size)))

      (mockCatalogManagementService
        .createEService(_: CatalogManagementDependency.EServiceSeed)(_: Seq[(String, String)]))
        .expects(seed, *)
        .returning(Future.successful(eservice))
        .once()

      val expected = EService(
        id = eservice.id,
        producerId = eservice.producerId,
        name = seed.name,
        description = seed.description,
        technology = seed.technology.toApi,
        descriptors = eservice.descriptors.map(_.toApi),
        riskAnalysis = Seq.empty,
        mode = seed.mode.toApi
      )

      Post() ~> service.createEService(apiSeed) ~> check {
        status shouldEqual StatusCodes.OK
        val response = responseAs[EService]
        response shouldEqual expected
      }
    }

    "fail with conflict if an EService with the same name exists" in {

      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq(
          "bearer"                        -> bearerToken,
          USER_ROLES                      -> "admin",
          ORGANIZATION_ID_CLAIM           -> requesterId.toString,
          ORGANIZATION_EXTERNAL_ID_ORIGIN -> "IPA",
          ORGANIZATION_EXTERNAL_ID_VALUE  -> "12345"
        )

      val catalogItems: Seq[CatalogItem] = Seq(SpecData.catalogItem)

      val apiSeed: EServiceSeed =
        EServiceSeed(
          name = "MyService",
          description = "My Service",
          technology = EServiceTechnology.REST,
          mode = EServiceMode.DELIVER
        )

      (mockCatalogManagementService
        .getEServices(
          _: UUID,
          _: Option[String],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[CatalogDescriptorState],
          _: Option[CatalogItemMode],
          _: Int,
          _: Int,
          _: Boolean
        )(_: ExecutionContext, _: ReadModelService, _: Seq[(String, String)]))
        .expects(
          requesterId,
          Some(apiSeed.name),
          Seq.empty,
          Seq(requesterId),
          Seq.empty,
          Seq.empty,
          None,
          0,
          1,
          true,
          *,
          *,
          context
        )
        .once()
        .returns(Future.successful(PaginatedResult(results = catalogItems, catalogItems.size)))

      Post() ~> service.createEService(apiSeed) ~> check {
        status shouldEqual StatusCodes.Conflict
      }
    }
    "fail with forbidden requester origin is not IPA" in {

      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq(
          "bearer"                        -> bearerToken,
          USER_ROLES                      -> "admin",
          ORGANIZATION_ID_CLAIM           -> requesterId.toString,
          ORGANIZATION_EXTERNAL_ID_ORIGIN -> "NOT_IPA",
          ORGANIZATION_EXTERNAL_ID_VALUE  -> "12345"
        )

      val apiSeed: EServiceSeed =
        EServiceSeed(
          name = "MyService",
          description = "My Service",
          technology = EServiceTechnology.REST,
          mode = EServiceMode.DELIVER
        )

      Post() ~> service.createEService(apiSeed) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
  }
  "EService update" should {
    "succeed" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor =
        SpecData.eServiceDescriptor.copy(state = CatalogManagementDependency.EServiceDescriptorState.DRAFT)

      val eService = SpecData.eService.copy(descriptors = Seq(descriptor), producerId = requesterId)

      val eServiceSeed =
        UpdateEServiceSeed(
          name = "newName",
          description = "newDescription",
          technology = EServiceTechnology.REST,
          mode = EServiceMode.DELIVER
        )

      val updatedEServiceSeed = CatalogManagementDependency.UpdateEServiceSeed(
        name = "newName",
        description = "newDescription",
        technology = eService.technology,
        mode = CatalogManagementDependency.EServiceMode.DELIVER
      )

      val updatedEService = CatalogManagementDependency.EService(
        id = eService.id,
        producerId = requesterId,
        name = "newName",
        description = "newDescription",
        technology = eService.technology,
        descriptors = Seq(descriptor),
        riskAnalysis = Seq.empty,
        mode = CatalogManagementDependency.EServiceMode.DELIVER
      )

      (mockCatalogManagementService
        .getEServices(
          _: UUID,
          _: Option[String],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[CatalogDescriptorState],
          _: Option[CatalogItemMode],
          _: Int,
          _: Int,
          _: Boolean
        )(_: ExecutionContext, _: ReadModelService, _: Seq[(String, String)]))
        .expects(
          requesterId,
          Some(updatedEServiceSeed.name),
          Seq.empty,
          Seq(requesterId),
          Seq.empty,
          Seq.empty,
          None,
          0,
          1,
          true,
          *,
          *,
          context
        )
        .once()
        .returns(Future.successful(PaginatedResult(results = Seq.empty, 0)))

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eService.id, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem.copy(producerId = requesterId)))

      (mockCatalogManagementService
        .updateEServiceById(_: String, _: CatalogManagementDependency.UpdateEServiceSeed)(_: Seq[(String, String)]))
        .expects(eService.id.toString, updatedEServiceSeed, *)
        .returning(Future.successful(updatedEService))
        .once()

      Put() ~> service.updateEServiceById(eService.id.toString, eServiceSeed) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    "succeed when technology has changed" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val catalogDescriptor =
        SpecData.catalogDescriptor.copy(state = Draft, interface = Some(SpecData.catalogDocumentInterface))

      val descriptor =
        SpecData.eServiceDescriptor.copy(
          state = CatalogManagementDependency.EServiceDescriptorState.DRAFT,
          interface = None
        )

      val eService =
        SpecData.catalogItem.copy(technology = Rest, descriptors = Seq(catalogDescriptor), producerId = requesterId)

      val eServiceSeed =
        UpdateEServiceSeed(
          name = "newName",
          description = "newDescription",
          technology = EServiceTechnology.SOAP,
          mode = EServiceMode.DELIVER
        )

      val updatedEServiceSeed = CatalogManagementDependency.UpdateEServiceSeed(
        name = "newName",
        description = "newDescription",
        technology = CatalogManagementDependency.EServiceTechnology.SOAP,
        mode = CatalogManagementDependency.EServiceMode.DELIVER
      )

      val updatedEService = CatalogManagementDependency.EService(
        id = eService.id,
        producerId = requesterId,
        name = "newName",
        description = "newDescription",
        technology = CatalogManagementDependency.EServiceTechnology.SOAP,
        descriptors = Seq(descriptor),
        riskAnalysis = Seq.empty,
        mode = CatalogManagementDependency.EServiceMode.DELIVER
      )

      (mockCatalogManagementService
        .getEServices(
          _: UUID,
          _: Option[String],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[CatalogDescriptorState],
          _: Option[CatalogItemMode],
          _: Int,
          _: Int,
          _: Boolean
        )(_: ExecutionContext, _: ReadModelService, _: Seq[(String, String)]))
        .expects(
          requesterId,
          Some(updatedEServiceSeed.name),
          Seq.empty,
          Seq(requesterId),
          Seq.empty,
          Seq.empty,
          None,
          0,
          1,
          true,
          *,
          *,
          context
        )
        .once()
        .returns(Future.successful(PaginatedResult(results = Seq.empty, 0)))

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eService.id, *, *)
        .once()
        .returns(Future.successful(eService))

      (mockCatalogManagementService
        .deleteEServiceDocument(_: String, _: String, _: String)(_: Seq[(String, String)]))
        .expects(eService.id.toString, catalogDescriptor.id.toString, SpecData.catalogDocumentInterface.id.toString, *)
        .returning(Future.unit)
        .once()

      (mockCatalogManagementService
        .updateEServiceById(_: String, _: CatalogManagementDependency.UpdateEServiceSeed)(_: Seq[(String, String)]))
        .expects(eService.id.toString, updatedEServiceSeed, *)
        .returning(Future.successful(updatedEService))
        .once()

      Put() ~> service.updateEServiceById(eService.id.toString, eServiceSeed) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    "succeed if use the same name" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor =
        SpecData.eServiceDescriptor.copy(state = CatalogManagementDependency.EServiceDescriptorState.DRAFT)

      val eService = SpecData.eService.copy(descriptors = Seq(descriptor), producerId = requesterId)

      val eServiceSeed =
        UpdateEServiceSeed(
          name = "newName",
          description = "newDescription",
          technology = EServiceTechnology.REST,
          mode = EServiceMode.DELIVER
        )

      val updatedEServiceSeed = CatalogManagementDependency.UpdateEServiceSeed(
        name = "newName",
        description = "newDescription",
        technology = eService.technology,
        mode = CatalogManagementDependency.EServiceMode.DELIVER
      )

      val updatedEService = CatalogManagementDependency.EService(
        id = eService.id,
        producerId = requesterId,
        name = "newName",
        description = "newDescription",
        technology = eService.technology,
        descriptors = Seq(descriptor),
        riskAnalysis = Seq.empty,
        mode = CatalogManagementDependency.EServiceMode.DELIVER
      )

      (mockCatalogManagementService
        .getEServices(
          _: UUID,
          _: Option[String],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[CatalogDescriptorState],
          _: Option[CatalogItemMode],
          _: Int,
          _: Int,
          _: Boolean
        )(_: ExecutionContext, _: ReadModelService, _: Seq[(String, String)]))
        .expects(
          requesterId,
          Some(updatedEServiceSeed.name),
          Seq.empty,
          Seq(requesterId),
          Seq.empty,
          Seq.empty,
          None,
          0,
          1,
          true,
          *,
          *,
          context
        )
        .once()
        .returns(
          Future.successful(
            PaginatedResult(
              results = Seq(
                SpecData.catalogItem.copy(id = eService.id, name = updatedEServiceSeed.name, producerId = requesterId)
              ),
              0
            )
          )
        )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eService.id, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem.copy(producerId = requesterId)))

      (mockCatalogManagementService
        .updateEServiceById(_: String, _: CatalogManagementDependency.UpdateEServiceSeed)(_: Seq[(String, String)]))
        .expects(eService.id.toString, updatedEServiceSeed, *)
        .returning(Future.successful(updatedEService))
        .once()

      Put() ~> service.updateEServiceById(eService.id.toString, eServiceSeed) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    "succeed and delete riskAnalysis when mode move from Receive to Deliver" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor =
        SpecData.eServiceDescriptor.copy(state = CatalogManagementDependency.EServiceDescriptorState.DRAFT)

      val eService = SpecData.eService.copy(descriptors = Seq(descriptor), producerId = requesterId)

      val eServiceSeed =
        UpdateEServiceSeed(
          name = "newName",
          description = "newDescription",
          technology = EServiceTechnology.REST,
          mode = EServiceMode.DELIVER
        )

      val updatedEServiceSeed = CatalogManagementDependency.UpdateEServiceSeed(
        name = "newName",
        description = "newDescription",
        technology = eService.technology,
        mode = CatalogManagementDependency.EServiceMode.DELIVER
      )

      val updatedEService = CatalogManagementDependency.EService(
        id = eService.id,
        producerId = requesterId,
        name = "newName",
        description = "newDescription",
        technology = eService.technology,
        descriptors = Seq(descriptor),
        riskAnalysis = Seq.empty,
        mode = CatalogManagementDependency.EServiceMode.DELIVER
      )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eService.id, *, *)
        .once()
        .returns(
          Future.successful(
            SpecData.catalogItem
              .copy(producerId = requesterId, mode = Receive, riskAnalysis = Seq(SpecData.catalogRiskAnalysisFullValid))
          )
        )

      (mockCatalogManagementService
        .getEServices(
          _: UUID,
          _: Option[String],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[CatalogDescriptorState],
          _: Option[CatalogItemMode],
          _: Int,
          _: Int,
          _: Boolean
        )(_: ExecutionContext, _: ReadModelService, _: Seq[(String, String)]))
        .expects(
          requesterId,
          Some(updatedEServiceSeed.name),
          Seq.empty,
          Seq(requesterId),
          Seq.empty,
          Seq.empty,
          None,
          0,
          1,
          true,
          *,
          *,
          context
        )
        .once()
        .returns(Future.successful(PaginatedResult(results = Seq.empty, 0)))

      (mockCatalogManagementService
        .deleteRiskAnalysis(_: UUID, _: UUID)(_: Seq[(String, String)]))
        .expects(eService.id, SpecData.catalogRiskAnalysisFullValid.id, *)
        .returning(Future.unit)
        .once()

      (mockCatalogManagementService
        .updateEServiceById(_: String, _: CatalogManagementDependency.UpdateEServiceSeed)(_: Seq[(String, String)]))
        .expects(eService.id.toString, updatedEServiceSeed, *)
        .returning(Future.successful(updatedEService))
        .once()

      Put() ~> service.updateEServiceById(eService.id.toString, eServiceSeed) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "succeed if no descriptor is present" in {

      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val eService = SpecData.eService.copy(descriptors = Seq.empty, producerId = requesterId)

      val eServiceSeed =
        UpdateEServiceSeed(
          name = "newName",
          description = "newDescription",
          technology = EServiceTechnology.REST,
          mode = EServiceMode.DELIVER
        )

      val updatedEServiceSeed = CatalogManagementDependency.UpdateEServiceSeed(
        name = "newName",
        description = "newDescription",
        technology = eService.technology,
        mode = eService.mode
      )

      val updatedEService = CatalogManagementDependency.EService(
        id = eService.id,
        producerId = requesterId,
        name = "newName",
        description = "newDescription",
        technology = eService.technology,
        descriptors = Seq.empty,
        riskAnalysis = Seq.empty,
        mode = eService.mode
      )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eService.id, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem.copy(producerId = requesterId)))

      (mockCatalogManagementService
        .getEServices(
          _: UUID,
          _: Option[String],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[CatalogDescriptorState],
          _: Option[CatalogItemMode],
          _: Int,
          _: Int,
          _: Boolean
        )(_: ExecutionContext, _: ReadModelService, _: Seq[(String, String)]))
        .expects(
          requesterId,
          Some(updatedEServiceSeed.name),
          Seq.empty,
          Seq(requesterId),
          Seq.empty,
          Seq.empty,
          None,
          0,
          1,
          true,
          *,
          *,
          context
        )
        .once()
        .returns(Future.successful(PaginatedResult(results = Seq.empty, 0)))

      (mockCatalogManagementService
        .updateEServiceById(_: String, _: CatalogManagementDependency.UpdateEServiceSeed)(_: Seq[(String, String)]))
        .expects(eService.id.toString, updatedEServiceSeed, *)
        .returning(Future.successful(updatedEService))
        .once()

      Put() ~> service.updateEServiceById(eService.id.toString, eServiceSeed) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    "fail if exists another eService with the update name" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor =
        SpecData.eServiceDescriptor.copy(state = CatalogManagementDependency.EServiceDescriptorState.DRAFT)

      val eService = SpecData.eService.copy(descriptors = Seq(descriptor), producerId = requesterId)

      val eServiceSeed =
        UpdateEServiceSeed(
          name = "newName",
          description = "newDescription",
          technology = EServiceTechnology.REST,
          mode = EServiceMode.DELIVER
        )

      val updatedEServiceSeed = CatalogManagementDependency.UpdateEServiceSeed(
        name = "newName",
        description = "newDescription",
        technology = eService.technology,
        mode = CatalogManagementDependency.EServiceMode.DELIVER
      )

      (mockCatalogManagementService
        .getEServices(
          _: UUID,
          _: Option[String],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[CatalogDescriptorState],
          _: Option[CatalogItemMode],
          _: Int,
          _: Int,
          _: Boolean
        )(_: ExecutionContext, _: ReadModelService, _: Seq[(String, String)]))
        .expects(
          requesterId,
          Some(updatedEServiceSeed.name),
          Seq.empty,
          Seq(requesterId),
          Seq.empty,
          Seq.empty,
          None,
          0,
          1,
          true,
          *,
          *,
          context
        )
        .once()
        .returns(
          Future.successful(
            PaginatedResult(
              results = Seq(SpecData.catalogItem.copy(id = UUID.randomUUID(), name = updatedEServiceSeed.name)),
              1
            )
          )
        )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eService.id, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem.copy(producerId = requesterId)))

      Put() ~> service.updateEServiceById(eService.id.toString, eServiceSeed) ~> check {
        status shouldEqual StatusCodes.Conflict
      }
    }

    "fail if descriptor state is not draft" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor =
        SpecData.eServiceDescriptor.copy(state = CatalogManagementDependency.EServiceDescriptorState.PUBLISHED)

      val eService = SpecData.eService.copy(descriptors = Seq(descriptor), producerId = requesterId)

      val eServiceSeed =
        UpdateEServiceSeed(
          name = "newName",
          description = "newDescription",
          technology = EServiceTechnology.REST,
          mode = EServiceMode.DELIVER
        )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eService.id, *, *)
        .once()
        .returns(
          Future.successful(
            SpecData.catalogItem
              .copy(producerId = requesterId, descriptors = Seq(SpecData.catalogDescriptor))
          )
        )

      Put() ~> service.updateEServiceById(eService.id.toString, eServiceSeed) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail if requester is not the Producer" in {

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      val eServiceSeed =
        UpdateEServiceSeed(
          name = "newName",
          description = "newDescription",
          technology = EServiceTechnology.REST,
          mode = EServiceMode.DELIVER
        )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(
          Future.successful(
            SpecData.catalogItem
              .copy(producerId = UUID.randomUUID(), descriptors = Seq(SpecData.catalogDescriptor))
          )
        )

      Put() ~> service.updateEServiceById(SpecData.catalogItem.id.toString, eServiceSeed) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
    "fail if EService does not exist" in {

      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val eServiceSeed =
        UpdateEServiceSeed(
          name = "newName",
          description = "newDescription",
          technology = EServiceTechnology.REST,
          mode = EServiceMode.DELIVER
        )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.failed(EServiceNotFound(SpecData.catalogItem.id.toString)))

      Put() ~> service.updateEServiceById(SpecData.catalogItem.id.toString, eServiceSeed) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail when technology has changed in case of no descriptors" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val eService = SpecData.catalogItem.copy(descriptors = Seq.empty, producerId = requesterId)

      val eServiceSeed =
        UpdateEServiceSeed(
          name = "newName",
          description = "newDescription",
          technology = EServiceTechnology.SOAP,
          mode = EServiceMode.DELIVER
        )

      val updatedEServiceSeed = CatalogManagementDependency.UpdateEServiceSeed(
        name = "newName",
        description = "newDescription",
        technology = CatalogManagementDependency.EServiceTechnology.SOAP,
        mode = CatalogManagementDependency.EServiceMode.DELIVER
      )

      (mockCatalogManagementService
        .getEServices(
          _: UUID,
          _: Option[String],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[CatalogDescriptorState],
          _: Option[CatalogItemMode],
          _: Int,
          _: Int,
          _: Boolean
        )(_: ExecutionContext, _: ReadModelService, _: Seq[(String, String)]))
        .expects(
          requesterId,
          Some(updatedEServiceSeed.name),
          Seq.empty,
          Seq(requesterId),
          Seq.empty,
          Seq.empty,
          None,
          0,
          1,
          true,
          *,
          *,
          context
        )
        .once()
        .returns(Future.successful(PaginatedResult(results = Seq.empty, 0)))

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eService.id, *, *)
        .once()
        .returns(Future.successful(eService))

      Put() ~> service.updateEServiceById(eService.id.toString, eServiceSeed) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
    "fail when technology has changed in case of multiple descriptors" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor1 =
        SpecData.catalogDescriptor.copy(state = Archived)
      val descriptor2 =
        SpecData.catalogDescriptor.copy(state = Draft)

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor1, descriptor2), producerId = requesterId)

      val eServiceSeed =
        UpdateEServiceSeed(
          name = "newName",
          description = "newDescription",
          technology = EServiceTechnology.SOAP,
          mode = EServiceMode.DELIVER
        )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eService.id, *, *)
        .once()
        .returns(Future.successful(eService))

      Put() ~> service.updateEServiceById(eService.id.toString, eServiceSeed) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }

  "EService clone" should {

    "succeed" in {

      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor =
        SpecData.eServiceDescriptor.copy(state = CatalogManagementDependency.EServiceDescriptorState.PUBLISHED)

      val eService = SpecData.eService.copy(descriptors = Seq(descriptor), producerId = requesterId)

      val clonedEService = eService.copy(id = UUID.randomUUID())

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eService.id, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem.copy(producerId = requesterId)))

      (mockCatalogManagementService
        .cloneEService(_: UUID, _: UUID)(_: Seq[(String, String)]))
        .expects(eService.id, descriptor.id, *)
        .returning(Future.successful(clonedEService))
        .once()

      Post() ~> service.cloneEServiceByDescriptor(eService.id.toString, descriptor.id.toString) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    "fail if requester is not the Producer" in {

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem.copy(producerId = UUID.randomUUID())))

      Post() ~> service.cloneEServiceByDescriptor(
        SpecData.catalogItem.id.toString,
        UUID.randomUUID().toString
      ) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
    "fail if EService does not exist" in {

      val requesterId                             = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.failed(EServiceNotFound(SpecData.catalogItem.id.toString)))

      Post() ~> service.cloneEServiceByDescriptor(
        SpecData.catalogItem.id.toString,
        UUID.randomUUID().toString
      ) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
  }
  "EService document deletion" should {
    "succeed only on Draft descriptor" in {
      val requesterId  = UUID.randomUUID()
      val descriptorId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val eServiceDoc = SpecData.eServiceDoc

      val eServiceDescriptor =
        SpecData.eServiceDescriptor.copy(
          id = descriptorId,
          state = CatalogManagementDependency.EServiceDescriptorState.DRAFT,
          docs = Seq(eServiceDoc)
        )

      val eService = SpecData.eService.copy(descriptors = Seq(eServiceDescriptor), producerId = requesterId)

      val descriptor =
        SpecData.catalogDescriptor.copy(
          id = descriptorId,
          docs = Seq(SpecData.catalogDocument.copy(id = eServiceDoc.id)),
          state = Draft
        )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eService.id, *, *)
        .once()
        .returns(
          Future.successful(
            SpecData.catalogItem.copy(id = eService.id, producerId = requesterId, descriptors = Seq(descriptor))
          )
        )

      (mockCatalogManagementService
        .deleteEServiceDocument(_: String, _: String, _: String)(_: Seq[(String, String)]))
        .expects(eService.id.toString, descriptor.id.toString, eServiceDoc.id.toString, *)
        .returning(Future.unit)
        .once()

      Delete() ~> service.deleteEServiceDocumentById(
        eService.id.toString,
        descriptor.id.toString,
        eServiceDoc.id.toString
      ) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }
    "fail if requester is not the Producer" in {
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem.copy(producerId = UUID.randomUUID())))

      Delete() ~> service.deleteEServiceDocumentById(
        SpecData.catalogItem.id.toString,
        UUID.randomUUID().toString,
        UUID.randomUUID().toString
      ) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
    "fail if EService does not exist" in {

      val requesterId                             = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.failed(EServiceNotFound(SpecData.catalogItem.id.toString)))

      Delete() ~> service.deleteEServiceDocumentById(
        SpecData.catalogItem.id.toString,
        UUID.randomUUID().toString,
        UUID.randomUUID().toString
      ) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if descriptor does not exists" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val eService = SpecData.eService.copy(descriptors = Seq.empty, producerId = requesterId)

      val descriptor =
        SpecData.catalogDescriptor.copy(
          id = UUID.randomUUID(),
          docs = Seq(SpecData.catalogDocument.copy(id = SpecData.eServiceDoc.id)),
          state = Draft
        )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eService.id, *, *)
        .once()
        .returns(
          Future.successful(
            SpecData.catalogItem.copy(id = eService.id, producerId = requesterId, descriptors = Seq(descriptor))
          )
        )

      Delete() ~> service.deleteEServiceDocumentById(
        eService.id.toString,
        UUID.randomUUID().toString,
        SpecData.eServiceDoc.id.toString
      ) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if Descriptor is not Draft" in {
      val requesterId  = UUID.randomUUID()
      val descriptorId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val eServiceDoc = SpecData.eServiceDoc

      val eServiceDescriptor =
        SpecData.eServiceDescriptor.copy(
          id = descriptorId,
          state = CatalogManagementDependency.EServiceDescriptorState.PUBLISHED,
          docs = Seq(eServiceDoc)
        )

      val eService = SpecData.eService.copy(descriptors = Seq(eServiceDescriptor), producerId = requesterId)

      val descriptor =
        SpecData.catalogDescriptor.copy(
          id = descriptorId,
          docs = Seq(SpecData.catalogDocument.copy(id = eServiceDoc.id)),
          state = Published
        )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eService.id, *, *)
        .once()
        .returns(
          Future.successful(
            SpecData.catalogItem.copy(id = eService.id, producerId = requesterId, descriptors = Seq(descriptor))
          )
        )

      Delete() ~> service.deleteEServiceDocumentById(
        eService.id.toString,
        descriptor.id.toString,
        eServiceDoc.id.toString
      ) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }
  "Descriptor creation" should {
    "succeed" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val attributeId1: UUID = UUID.randomUUID()
      val attributeId2: UUID = UUID.randomUUID()
      val attributeId3: UUID = UUID.randomUUID()

      val seed = EServiceDescriptorSeed(
        description = None,
        audience = Seq("aud"),
        voucherLifespan = 60,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        agreementApprovalPolicy = AgreementApprovalPolicy.AUTOMATIC,
        attributes = AttributesSeed(
          certified = List(List(AttributeSeed(attributeId1, explicitAttributeVerification = false))),
          declared = List(List(AttributeSeed(attributeId2, explicitAttributeVerification = false))),
          verified = List(List(AttributeSeed(attributeId3, explicitAttributeVerification = true)))
        )
      )

      val eServiceDescriptorSeed = CatalogManagementDependency.EServiceDescriptorSeed(
        description = None,
        audience = Seq("aud"),
        voucherLifespan = 60,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        agreementApprovalPolicy = CatalogManagementDependency.AgreementApprovalPolicy.AUTOMATIC,
        attributes = CatalogManagementDependency.Attributes(
          certified =
            List(List(CatalogManagementDependency.Attribute(attributeId1, explicitAttributeVerification = false))),
          declared =
            List(List(CatalogManagementDependency.Attribute(attributeId2, explicitAttributeVerification = false))),
          verified =
            List(List(CatalogManagementDependency.Attribute(attributeId3, explicitAttributeVerification = true)))
        )
      )

      val eServiceDescriptor = CatalogManagementDependency.EServiceDescriptor(
        id = UUID.randomUUID(),
        version = "1",
        description = None,
        interface = None,
        docs = Nil,
        state = CatalogManagementDependency.EServiceDescriptorState.DRAFT,
        audience = Seq("aud"),
        voucherLifespan = 60,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        agreementApprovalPolicy = AUTOMATIC,
        serverUrls = Nil,
        attributes = CatalogManagementDependency.Attributes(
          certified =
            List(List(CatalogManagementDependency.Attribute(attributeId1, explicitAttributeVerification = false))),
          declared =
            List(List(CatalogManagementDependency.Attribute(attributeId2, explicitAttributeVerification = false))),
          verified =
            List(List(CatalogManagementDependency.Attribute(attributeId3, explicitAttributeVerification = true)))
        )
      )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem.copy(producerId = requesterId)))

      (mockAttributeRegistryManagementService
        .getAttributeById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(attributeId1, *, *)
        .once()
        .returns(Future.successful(SpecData.persistentCertifiedAttribute.copy(id = attributeId1)))

      (mockAttributeRegistryManagementService
        .getAttributeById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(attributeId2, *, *)
        .once()
        .returns(Future.successful(SpecData.persistentDeclaredAttribute.copy(id = attributeId2)))

      (mockAttributeRegistryManagementService
        .getAttributeById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(attributeId3, *, *)
        .once()
        .returns(Future.successful(SpecData.persistentVerifiedAttribute.copy(id = attributeId3)))

      (mockCatalogManagementService
        .createDescriptor(_: String, _: CatalogManagementDependency.EServiceDescriptorSeed)(_: Seq[(String, String)]))
        .expects(SpecData.catalogItem.id.toString, eServiceDescriptorSeed, *)
        .returning(Future.successful(eServiceDescriptor))
        .once()

      Post() ~> service.createDescriptor(SpecData.catalogItem.id.toString, seed) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    "fail if attribute does not exists" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val attributeId1: UUID = UUID.randomUUID()
      val attributeId2: UUID = UUID.randomUUID()
      val attributeId3: UUID = UUID.randomUUID()

      val seed = EServiceDescriptorSeed(
        description = None,
        audience = Seq("aud"),
        voucherLifespan = 60,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        agreementApprovalPolicy = AgreementApprovalPolicy.AUTOMATIC,
        attributes = AttributesSeed(
          certified = List(List(AttributeSeed(attributeId1, explicitAttributeVerification = false))),
          declared = List(List(AttributeSeed(attributeId2, explicitAttributeVerification = false))),
          verified = List(List(AttributeSeed(attributeId3, explicitAttributeVerification = true)))
        )
      )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem.copy(producerId = requesterId)))

      (mockAttributeRegistryManagementService
        .getAttributeById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(attributeId1, *, *)
        .once()
        .returns(Future.failed(AttributeNotFound(attributeId1)))

      Post() ~> service.createDescriptor(SpecData.catalogItem.id.toString, seed) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
    "fail if requester is not the Producer" in {
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem.copy(producerId = UUID.randomUUID())))

      val seed = EServiceDescriptorSeed(
        description = None,
        audience = Seq("aud"),
        voucherLifespan = 60,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        agreementApprovalPolicy = AgreementApprovalPolicy.AUTOMATIC,
        attributes = AttributesSeed(Nil, Nil, Nil)
      )

      Post() ~> service.createDescriptor(SpecData.catalogItem.id.toString, seed) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
    "fail if EService does not exist" in {

      val requesterId                             = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val seed = EServiceDescriptorSeed(
        description = None,
        audience = Seq("aud"),
        voucherLifespan = 60,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        agreementApprovalPolicy = AgreementApprovalPolicy.AUTOMATIC,
        attributes = AttributesSeed(Nil, Nil, Nil)
      )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.failed(EServiceNotFound(SpecData.catalogItem.id.toString)))

      Post() ~> service.createDescriptor(SpecData.catalogItem.id.toString, seed) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
  }
  "Descriptor draft update" should {
    "succeed" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(
          Future.successful(
            SpecData.catalogItem
              .copy(producerId = requesterId, descriptors = Seq(SpecData.catalogDescriptor.copy(state = Draft)))
          )
        )

      val seed = UpdateEServiceDescriptorSeed(
        description = None,
        audience = Seq("aud"),
        voucherLifespan = 60,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        agreementApprovalPolicy = AgreementApprovalPolicy.AUTOMATIC,
        attributes = AttributesSeed(Nil, Nil, Nil)
      )

      val depSeed = CatalogManagementDependency.UpdateEServiceDescriptorSeed(
        description = None,
        audience = Seq("aud"),
        voucherLifespan = 60,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        agreementApprovalPolicy = CatalogManagementDependency.AgreementApprovalPolicy.AUTOMATIC,
        state = CatalogManagementDependency.EServiceDescriptorState.DRAFT,
        attributes = CatalogManagementDependency.Attributes(Nil, Nil, Nil)
      )

      (mockCatalogManagementService
        .updateDescriptor(_: String, _: String, _: CatalogManagementDependency.UpdateEServiceDescriptorSeed)(
          _: Seq[(String, String)]
        ))
        .expects(SpecData.catalogItem.id.toString, SpecData.catalogDescriptor.id.toString, depSeed, *)
        .returning(Future.successful(SpecData.eService))
        .once()

      Put() ~> service.updateDraftDescriptor(
        SpecData.catalogItem.id.toString,
        SpecData.catalogDescriptor.id.toString,
        seed
      ) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    "fail if state is not Draft" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(
          Future.successful(
            SpecData.catalogItem
              .copy(producerId = requesterId, descriptors = Seq(SpecData.catalogDescriptor.copy(state = Published)))
          )
        )

      val seed = UpdateEServiceDescriptorSeed(
        description = None,
        audience = Seq("aud"),
        voucherLifespan = 60,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        agreementApprovalPolicy = AgreementApprovalPolicy.AUTOMATIC,
        attributes = AttributesSeed(Nil, Nil, Nil)
      )

      Put() ~> service.updateDraftDescriptor(
        SpecData.catalogItem.id.toString,
        SpecData.catalogDescriptor.id.toString,
        seed
      ) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
    "fail if EService does not exist" in {

      val requesterId                             = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val seed = UpdateEServiceDescriptorSeed(
        description = None,
        audience = Seq("aud"),
        voucherLifespan = 60,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        agreementApprovalPolicy = AgreementApprovalPolicy.AUTOMATIC,
        attributes = AttributesSeed(Nil, Nil, Nil)
      )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.failed(EServiceNotFound(SpecData.catalogItem.id.toString)))

      Put() ~> service.updateDraftDescriptor(
        SpecData.catalogItem.id.toString,
        UUID.randomUUID.toString,
        seed
      ) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if EService descriptor does not exist" in {

      val requesterId                             = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val seed = UpdateEServiceDescriptorSeed(
        description = None,
        audience = Seq("aud"),
        voucherLifespan = 60,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        agreementApprovalPolicy = AgreementApprovalPolicy.AUTOMATIC,
        attributes = AttributesSeed(Nil, Nil, Nil)
      )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem.copy(producerId = requesterId)))

      Put() ~> service.updateDraftDescriptor(
        SpecData.catalogItem.id.toString,
        UUID.randomUUID.toString,
        seed
      ) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if requester is not the Producer" in {

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem.copy(producerId = UUID.randomUUID())))

      val seed = UpdateEServiceDescriptorSeed(
        description = None,
        audience = Seq("aud"),
        voucherLifespan = 60,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        agreementApprovalPolicy = AgreementApprovalPolicy.AUTOMATIC,
        attributes = AttributesSeed(Nil, Nil, Nil)
      )

      Put() ~> service.updateDraftDescriptor(
        SpecData.catalogItem.id.toString,
        UUID.randomUUID().toString,
        seed
      ) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
  }
  "Descriptor update" should {
    "succeed if published" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(
          Future.successful(
            SpecData.catalogItem
              .copy(producerId = requesterId, descriptors = Seq(SpecData.catalogDescriptor.copy(state = Published)))
          )
        )

      val seed =
        UpdateEServiceDescriptorQuotas(voucherLifespan = 60, dailyCallsPerConsumer = 0, dailyCallsTotal = 0)

      val depSeed = CatalogManagementDependency.UpdateEServiceDescriptorSeed(
        description = None,
        audience = Seq.empty,
        voucherLifespan = 60,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        agreementApprovalPolicy = CatalogManagementDependency.AgreementApprovalPolicy.AUTOMATIC,
        state = CatalogManagementDependency.EServiceDescriptorState.PUBLISHED,
        attributes = CatalogManagementDependency.Attributes(Nil, Nil, Nil)
      )

      (mockCatalogManagementService
        .updateDescriptor(_: String, _: String, _: CatalogManagementDependency.UpdateEServiceDescriptorSeed)(
          _: Seq[(String, String)]
        ))
        .expects(SpecData.catalogItem.id.toString, SpecData.catalogDescriptor.id.toString, depSeed, *)
        .returning(Future.successful(SpecData.eService))
        .once()

      Put() ~> service.updateDescriptor(
        SpecData.catalogItem.id.toString,
        SpecData.catalogDescriptor.id.toString,
        seed
      ) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    "succeed if deprecated" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(
          Future.successful(
            SpecData.catalogItem
              .copy(producerId = requesterId, descriptors = Seq(SpecData.catalogDescriptor.copy(state = Deprecated)))
          )
        )

      val seed =
        UpdateEServiceDescriptorQuotas(voucherLifespan = 60, dailyCallsPerConsumer = 0, dailyCallsTotal = 0)

      val depSeed = CatalogManagementDependency.UpdateEServiceDescriptorSeed(
        description = None,
        audience = Seq.empty,
        voucherLifespan = 60,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        agreementApprovalPolicy = CatalogManagementDependency.AgreementApprovalPolicy.AUTOMATIC,
        state = CatalogManagementDependency.EServiceDescriptorState.DEPRECATED,
        attributes = CatalogManagementDependency.Attributes(Nil, Nil, Nil)
      )

      (mockCatalogManagementService
        .updateDescriptor(_: String, _: String, _: CatalogManagementDependency.UpdateEServiceDescriptorSeed)(
          _: Seq[(String, String)]
        ))
        .expects(SpecData.catalogItem.id.toString, SpecData.catalogDescriptor.id.toString, depSeed, *)
        .returning(Future.successful(SpecData.eService))
        .once()

      Put() ~> service.updateDescriptor(
        SpecData.catalogItem.id.toString,
        SpecData.catalogDescriptor.id.toString,
        seed
      ) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "succeed if suspended" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(
          Future.successful(
            SpecData.catalogItem
              .copy(producerId = requesterId, descriptors = Seq(SpecData.catalogDescriptor.copy(state = Suspended)))
          )
        )

      val seed =
        UpdateEServiceDescriptorQuotas(voucherLifespan = 60, dailyCallsPerConsumer = 0, dailyCallsTotal = 0)

      val depSeed = CatalogManagementDependency.UpdateEServiceDescriptorSeed(
        description = None,
        audience = Seq.empty,
        voucherLifespan = 60,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        agreementApprovalPolicy = CatalogManagementDependency.AgreementApprovalPolicy.AUTOMATIC,
        state = CatalogManagementDependency.EServiceDescriptorState.SUSPENDED,
        attributes = CatalogManagementDependency.Attributes(Nil, Nil, Nil)
      )

      (mockCatalogManagementService
        .updateDescriptor(_: String, _: String, _: CatalogManagementDependency.UpdateEServiceDescriptorSeed)(
          _: Seq[(String, String)]
        ))
        .expects(SpecData.catalogItem.id.toString, SpecData.catalogDescriptor.id.toString, depSeed, *)
        .returning(Future.successful(SpecData.eService))
        .once()

      Put() ~> service.updateDescriptor(
        SpecData.catalogItem.id.toString,
        SpecData.catalogDescriptor.id.toString,
        seed
      ) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    "fail if state is not Published/Deprecated/Suspended" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(
          Future.successful(
            SpecData.catalogItem
              .copy(producerId = requesterId, descriptors = Seq(SpecData.catalogDescriptor.copy(state = Draft)))
          )
        )

      val seed =
        UpdateEServiceDescriptorQuotas(voucherLifespan = 60, dailyCallsPerConsumer = 0, dailyCallsTotal = 0)

      Put() ~> service.updateDescriptor(
        SpecData.catalogItem.id.toString,
        SpecData.catalogDescriptor.id.toString,
        seed
      ) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
    "fail if EService does not exist" in {

      val requesterId                             = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val seed =
        UpdateEServiceDescriptorQuotas(voucherLifespan = 60, dailyCallsPerConsumer = 0, dailyCallsTotal = 0)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.failed(EServiceNotFound(SpecData.catalogItem.id.toString)))

      Put() ~> service.updateDescriptor(SpecData.catalogItem.id.toString, UUID.randomUUID.toString, seed) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if EService descriptor does not exist" in {

      val requesterId                             = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val seed =
        UpdateEServiceDescriptorQuotas(voucherLifespan = 60, dailyCallsPerConsumer = 0, dailyCallsTotal = 0)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem.copy(producerId = requesterId)))

      Put() ~> service.updateDescriptor(SpecData.catalogItem.id.toString, UUID.randomUUID.toString, seed) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if requester is not the Producer" in {

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem.copy(producerId = UUID.randomUUID())))

      val seed =
        UpdateEServiceDescriptorQuotas(voucherLifespan = 60, dailyCallsPerConsumer = 0, dailyCallsTotal = 0)

      Put() ~> service.updateDescriptor(SpecData.catalogItem.id.toString, UUID.randomUUID().toString, seed) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
  }
  "Descriptor publication" should {
    "succeed if mode is Receive" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(
          Future.successful(
            SpecData.catalogItem.copy(
              producerId = requesterId,
              descriptors =
                Seq(SpecData.catalogDescriptor.copy(state = Draft, interface = Option(SpecData.catalogDocument))),
              riskAnalysis = Seq(SpecData.catalogRiskAnalysisFullValid),
              mode = Receive
            )
          )
        )

      (mockTenantManagementService
        .getTenantById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(requesterId, *, *)
        .once()
        .returns(Future.successful(SpecData.persistentTenant.copy(id = requesterId)))

      (mockCatalogManagementService
        .publishDescriptor(_: String, _: String)(_: Seq[(String, String)]))
        .expects(SpecData.catalogItem.id.toString, SpecData.catalogDescriptor.id.toString, *)
        .returning(Future.unit)
        .once()

      (mockAuthorizationManagementService
        .updateStateOnClients(
          _: UUID,
          _: UUID,
          _: AuthorizationManagementDependency.ClientComponentState,
          _: Seq[String],
          _: Int
        )(_: Seq[(String, String)]))
        .expects(
          SpecData.catalogItem.id,
          SpecData.catalogDescriptor.id,
          AuthorizationManagementDependency.ClientComponentState.ACTIVE,
          SpecData.catalogDescriptor.audience,
          SpecData.catalogDescriptor.voucherLifespan,
          *
        )
        .returning(Future.unit)
        .once()

      Post() ~> service.publishDescriptor(
        SpecData.catalogItem.id.toString,
        SpecData.catalogDescriptor.id.toString
      ) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }
    "fail if mode is Receive and Catalog Item has not at least one Risk Analysis" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(
          Future.successful(
            SpecData.catalogItem.copy(
              producerId = requesterId,
              descriptors =
                Seq(SpecData.catalogDescriptor.copy(state = Draft, interface = Option(SpecData.catalogDocument))),
              riskAnalysis = Seq.empty,
              mode = Receive
            )
          )
        )

      Post() ~> service.publishDescriptor(
        SpecData.catalogItem.id.toString,
        SpecData.catalogDescriptor.id.toString
      ) ~> check {
        status shouldEqual StatusCodes.BadRequest
        val problem = responseAs[Problem]
        problem.status shouldBe StatusCodes.BadRequest.intValue
        problem.errors.head.code shouldBe "009-0018"
      }
    }
    "fail if mode is Receive and Risk Analysis did not pass validation" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(
          Future.successful(
            SpecData.catalogItem.copy(
              producerId = requesterId,
              descriptors =
                Seq(SpecData.catalogDescriptor.copy(state = Draft, interface = Option(SpecData.catalogDocument))),
              riskAnalysis = Seq(SpecData.catalogRiskAnalysisSchemaOnly),
              mode = Receive
            )
          )
        )

      (mockTenantManagementService
        .getTenantById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(requesterId, *, *)
        .once()
        .returns(Future.successful(SpecData.persistentTenant.copy(id = requesterId)))

      Post() ~> service.publishDescriptor(
        SpecData.catalogItem.id.toString,
        SpecData.catalogDescriptor.id.toString
      ) ~> check {
        status shouldEqual StatusCodes.BadRequest
        val problem = responseAs[Problem]
        problem.status shouldBe StatusCodes.BadRequest.intValue
        problem.errors.head.code shouldBe "009-0016"
      }
    }
    "succeed if descriptor is Draft" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(
          Future.successful(
            SpecData.catalogItem.copy(
              producerId = requesterId,
              descriptors =
                Seq(SpecData.catalogDescriptor.copy(state = Draft, interface = Option(SpecData.catalogDocument)))
            )
          )
        )

      (mockCatalogManagementService
        .publishDescriptor(_: String, _: String)(_: Seq[(String, String)]))
        .expects(SpecData.catalogItem.id.toString, SpecData.catalogDescriptor.id.toString, *)
        .returning(Future.unit)
        .once()

      (mockAuthorizationManagementService
        .updateStateOnClients(
          _: UUID,
          _: UUID,
          _: AuthorizationManagementDependency.ClientComponentState,
          _: Seq[String],
          _: Int
        )(_: Seq[(String, String)]))
        .expects(
          SpecData.catalogItem.id,
          SpecData.catalogDescriptor.id,
          AuthorizationManagementDependency.ClientComponentState.ACTIVE,
          SpecData.catalogDescriptor.audience,
          SpecData.catalogDescriptor.voucherLifespan,
          *
        )
        .returning(Future.unit)
        .once()

      Post() ~> service.publishDescriptor(
        SpecData.catalogItem.id.toString,
        SpecData.catalogDescriptor.id.toString
      ) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }

    "succeed if descriptor is Draft and archive the previous one" in {
      val requesterId   = UUID.randomUUID()
      val descriptorId1 = UUID.randomUUID()
      val descriptorId2 = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(
          Future.successful(
            SpecData.catalogItem.copy(
              producerId = requesterId,
              descriptors = Seq(
                SpecData.catalogDescriptor
                  .copy(
                    id = descriptorId1,
                    state = Published,
                    interface = Option(SpecData.catalogDocument),
                    version = "2"
                  ),
                SpecData.catalogDescriptor
                  .copy(id = descriptorId2, state = Draft, interface = Option(SpecData.catalogDocument))
              )
            )
          )
        )

      (mockAgreementManagementService
        .getAgreements(_: Seq[UUID], _: Seq[UUID], _: Seq[UUID], _: Seq[UUID], _: Seq[PersistentAgreementState])(
          _: ExecutionContext,
          _: ReadModelService
        ))
        .expects(
          Seq(SpecData.catalogItem.id),
          Nil,
          Nil,
          List(descriptorId1),
          Seq(AgreementActive, AgreementSuspended),
          *,
          *
        )
        .once()
        .returns(Future.successful(Seq.empty))

      (mockCatalogManagementService
        .archiveDescriptor(_: String, _: String)(_: Seq[(String, String)]))
        .expects(SpecData.catalogItem.id.toString, descriptorId1.toString, *)
        .returning(Future.unit)
        .once()

      (mockCatalogManagementService
        .publishDescriptor(_: String, _: String)(_: Seq[(String, String)]))
        .expects(SpecData.catalogItem.id.toString, descriptorId2.toString, *)
        .returning(Future.unit)
        .once()

      (mockAuthorizationManagementService
        .updateStateOnClients(
          _: UUID,
          _: UUID,
          _: AuthorizationManagementDependency.ClientComponentState,
          _: Seq[String],
          _: Int
        )(_: Seq[(String, String)]))
        .expects(
          SpecData.catalogItem.id,
          descriptorId2,
          AuthorizationManagementDependency.ClientComponentState.ACTIVE,
          SpecData.catalogDescriptor.audience,
          SpecData.catalogDescriptor.voucherLifespan,
          *
        )
        .returning(Future.unit)
        .once()

      Post() ~> service.publishDescriptor(SpecData.catalogItem.id.toString, descriptorId2.toString) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }
    "succeed if descriptor is Draft and deprecate the previous one" in {
      val requesterId   = UUID.randomUUID()
      val descriptorId1 = UUID.randomUUID()
      val descriptorId2 = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(
          Future.successful(
            SpecData.catalogItem.copy(
              producerId = requesterId,
              descriptors = Seq(
                SpecData.catalogDescriptor
                  .copy(
                    id = descriptorId1,
                    state = Published,
                    interface = Option(SpecData.catalogDocument),
                    version = "2"
                  ),
                SpecData.catalogDescriptor
                  .copy(id = descriptorId2, state = Draft, interface = Option(SpecData.catalogDocument))
              )
            )
          )
        )

      (mockAgreementManagementService
        .getAgreements(_: Seq[UUID], _: Seq[UUID], _: Seq[UUID], _: Seq[UUID], _: Seq[PersistentAgreementState])(
          _: ExecutionContext,
          _: ReadModelService
        ))
        .expects(
          Seq(SpecData.catalogItem.id),
          Nil,
          Nil,
          List(descriptorId1),
          Seq(AgreementActive, AgreementSuspended),
          *,
          *
        )
        .once()
        .returns(Future.successful(Seq(SpecData.agreement)))

      (mockCatalogManagementService
        .deprecateDescriptor(_: String, _: String)(_: Seq[(String, String)]))
        .expects(SpecData.catalogItem.id.toString, descriptorId1.toString, *)
        .returning(Future.unit)
        .once()

      (mockCatalogManagementService
        .publishDescriptor(_: String, _: String)(_: Seq[(String, String)]))
        .expects(SpecData.catalogItem.id.toString, descriptorId2.toString, *)
        .returning(Future.unit)
        .once()

      (mockAuthorizationManagementService
        .updateStateOnClients(
          _: UUID,
          _: UUID,
          _: AuthorizationManagementDependency.ClientComponentState,
          _: Seq[String],
          _: Int
        )(_: Seq[(String, String)]))
        .expects(
          SpecData.catalogItem.id,
          descriptorId2,
          AuthorizationManagementDependency.ClientComponentState.ACTIVE,
          SpecData.catalogDescriptor.audience,
          SpecData.catalogDescriptor.voucherLifespan,
          *
        )
        .returning(Future.unit)
        .once()

      Post() ~> service.publishDescriptor(SpecData.catalogItem.id.toString, descriptorId2.toString) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }
    "fail if descriptor has not interface" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(
          Future.successful(
            SpecData.catalogItem
              .copy(producerId = requesterId, descriptors = Seq(SpecData.catalogDescriptor.copy(state = Draft)))
          )
        )

      Post() ~> service.publishDescriptor(
        SpecData.catalogItem.id.toString,
        SpecData.catalogDescriptor.id.toString
      ) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
    "fail if descriptor is not Draft" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(
          Future.successful(
            SpecData.catalogItem
              .copy(producerId = requesterId, descriptors = Seq(SpecData.catalogDescriptor.copy(state = Published)))
          )
        )

      Post() ~> service.publishDescriptor(
        SpecData.catalogItem.id.toString,
        SpecData.catalogDescriptor.id.toString
      ) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
    "fail if EService does not exist" in {

      val requesterId                             = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.failed(EServiceNotFound(SpecData.catalogItem.id.toString)))

      Post() ~> service.publishDescriptor(SpecData.catalogItem.id.toString, UUID.randomUUID.toString) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if EService descriptor does not exist" in {

      val requesterId                             = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem.copy(producerId = requesterId)))

      Post() ~> service.publishDescriptor(SpecData.catalogItem.id.toString, UUID.randomUUID.toString) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if requester is not the Producer" in {

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem.copy(producerId = UUID.randomUUID())))

      Post() ~> service.publishDescriptor(SpecData.catalogItem.id.toString, UUID.randomUUID.toString) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
  }
  "Descriptor suspension" should {
    "succeed if descriptor is Published" in {

      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(
          Future.successful(
            SpecData.catalogItem
              .copy(producerId = requesterId, descriptors = Seq(SpecData.catalogDescriptor.copy(state = Published)))
          )
        )

      (mockCatalogManagementService
        .suspendDescriptor(_: String, _: String)(_: Seq[(String, String)]))
        .expects(SpecData.catalogItem.id.toString, SpecData.catalogDescriptor.id.toString, *)
        .returning(Future.unit)
        .once()

      (mockAuthorizationManagementService
        .updateStateOnClients(
          _: UUID,
          _: UUID,
          _: AuthorizationManagementDependency.ClientComponentState,
          _: Seq[String],
          _: Int
        )(_: Seq[(String, String)]))
        .expects(
          SpecData.catalogItem.id,
          SpecData.catalogDescriptor.id,
          AuthorizationManagementDependency.ClientComponentState.INACTIVE,
          SpecData.catalogDescriptor.audience,
          SpecData.catalogDescriptor.voucherLifespan,
          *
        )
        .returning(Future.unit)
        .once()

      Post() ~> service.suspendDescriptor(
        SpecData.catalogItem.id.toString,
        SpecData.catalogDescriptor.id.toString
      ) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }
    "succeed if descriptor is Deprecated" in {

      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(
          Future.successful(
            SpecData.catalogItem
              .copy(producerId = requesterId, descriptors = Seq(SpecData.catalogDescriptor.copy(state = Deprecated)))
          )
        )

      (mockCatalogManagementService
        .suspendDescriptor(_: String, _: String)(_: Seq[(String, String)]))
        .expects(SpecData.catalogItem.id.toString, SpecData.catalogDescriptor.id.toString, *)
        .returning(Future.unit)
        .once()

      (mockAuthorizationManagementService
        .updateStateOnClients(
          _: UUID,
          _: UUID,
          _: AuthorizationManagementDependency.ClientComponentState,
          _: Seq[String],
          _: Int
        )(_: Seq[(String, String)]))
        .expects(
          SpecData.catalogItem.id,
          SpecData.catalogDescriptor.id,
          AuthorizationManagementDependency.ClientComponentState.INACTIVE,
          SpecData.catalogDescriptor.audience,
          SpecData.catalogDescriptor.voucherLifespan,
          *
        )
        .returning(Future.unit)
        .once()

      Post() ~> service.suspendDescriptor(
        SpecData.catalogItem.id.toString,
        SpecData.catalogDescriptor.id.toString
      ) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }
    "fail if descriptor is Draft" in {

      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(
          Future.successful(
            SpecData.catalogItem
              .copy(producerId = requesterId, descriptors = Seq(SpecData.catalogDescriptor.copy(state = Draft)))
          )
        )

      Post() ~> service.suspendDescriptor(
        SpecData.catalogItem.id.toString,
        SpecData.catalogDescriptor.id.toString
      ) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
    "fail if descriptor is Archived" in {

      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(
          Future.successful(
            SpecData.catalogItem
              .copy(producerId = requesterId, descriptors = Seq(SpecData.catalogDescriptor.copy(state = Archived)))
          )
        )

      Post() ~> service.suspendDescriptor(
        SpecData.catalogItem.id.toString,
        SpecData.catalogDescriptor.id.toString
      ) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
    "fail if EService does not exist" in {

      val requesterId                             = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.failed(EServiceNotFound(SpecData.catalogItem.id.toString)))

      Post() ~> service.suspendDescriptor(SpecData.catalogItem.id.toString, UUID.randomUUID().toString) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if EService descriptor does not exist" in {

      val requesterId                             = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem.copy(producerId = requesterId)))

      Post() ~> service.suspendDescriptor(SpecData.catalogItem.id.toString, UUID.randomUUID().toString) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if requester is not the Producer" in {

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem.copy(producerId = UUID.randomUUID())))

      Post() ~> service.suspendDescriptor(SpecData.catalogItem.id.toString, UUID.randomUUID.toString) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
  }
  "Descriptor activation" should {

    "activate Deprecated descriptor - case 1" in {

      val requesterId                             = UUID.randomUUID()
      val descriptorId                            = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor =
        SpecData.catalogDescriptor.copy(id = descriptorId, version = "3", state = Suspended)
      val eService   = SpecData.catalogItem.copy(
        descriptors = Seq(
          SpecData.catalogDescriptor.copy(version = "1", state = Archived),
          SpecData.catalogDescriptor.copy(version = "2", state = Deprecated),
          descriptor,
          SpecData.catalogDescriptor.copy(version = "4", state = Suspended),
          SpecData.catalogDescriptor.copy(version = "5", state = Draft)
        ),
        producerId = requesterId
      )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(eService))

      (mockCatalogManagementService
        .deprecateDescriptor(_: String, _: String)(_: Seq[(String, String)]))
        .expects(eService.id.toString, descriptor.id.toString, *)
        .returning(Future.unit)
        .once()

      (mockAuthorizationManagementService
        .updateStateOnClients(
          _: UUID,
          _: UUID,
          _: AuthorizationManagementDependency.ClientComponentState,
          _: Seq[String],
          _: Int
        )(_: Seq[(String, String)]))
        .expects(
          eService.id,
          descriptor.id,
          AuthorizationManagementDependency.ClientComponentState.ACTIVE,
          descriptor.audience,
          descriptor.voucherLifespan,
          *
        )
        .returning(Future.unit)
        .once()

      Post() ~> service.activateDescriptor(SpecData.catalogItem.id.toString, descriptorId.toString) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }
    "activate Deprecated descriptor - case 2" in {
      val requesterId                             = UUID.randomUUID()
      val descriptorId                            = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor =
        SpecData.catalogDescriptor.copy(id = descriptorId, version = "3", state = Suspended)
      val eService   = SpecData.catalogItem.copy(
        descriptors = Seq(
          SpecData.catalogDescriptor.copy(version = "1", state = Archived),
          SpecData.catalogDescriptor.copy(version = "2", state = Deprecated),
          descriptor,
          SpecData.catalogDescriptor.copy(version = "4", state = Published),
          SpecData.catalogDescriptor.copy(version = "5", state = Draft)
        ),
        producerId = requesterId
      )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(eService))

      (mockCatalogManagementService
        .deprecateDescriptor(_: String, _: String)(_: Seq[(String, String)]))
        .expects(eService.id.toString, descriptor.id.toString, *)
        .returning(Future.unit)
        .once()

      (mockAuthorizationManagementService
        .updateStateOnClients(
          _: UUID,
          _: UUID,
          _: AuthorizationManagementDependency.ClientComponentState,
          _: Seq[String],
          _: Int
        )(_: Seq[(String, String)]))
        .expects(
          eService.id,
          descriptor.id,
          AuthorizationManagementDependency.ClientComponentState.ACTIVE,
          descriptor.audience,
          descriptor.voucherLifespan,
          *
        )
        .returning(Future.unit)
        .once()

      Post() ~> service.activateDescriptor(SpecData.catalogItem.id.toString, descriptorId.toString) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }
    "activate Published descriptor - case 1" in {

      val requesterId                             = UUID.randomUUID()
      val descriptorId                            = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor = SpecData.catalogDescriptor.copy(id = descriptorId, version = "4", state = Suspended)

      val eService = SpecData.catalogItem.copy(
        descriptors = Seq(
          SpecData.catalogDescriptor.copy(version = "1", state = Archived),
          SpecData.catalogDescriptor.copy(version = "2", state = Deprecated),
          SpecData.catalogDescriptor.copy(version = "3", state = Suspended),
          descriptor,
          SpecData.catalogDescriptor.copy(version = "5", state = Draft)
        ),
        producerId = requesterId
      )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(eService))

      (mockCatalogManagementService
        .publishDescriptor(_: String, _: String)(_: Seq[(String, String)]))
        .expects(eService.id.toString, descriptor.id.toString, *)
        .returning(Future.unit)
        .once()

      (mockAuthorizationManagementService
        .updateStateOnClients(
          _: UUID,
          _: UUID,
          _: AuthorizationManagementDependency.ClientComponentState,
          _: Seq[String],
          _: Int
        )(_: Seq[(String, String)]))
        .expects(
          eService.id,
          descriptor.id,
          AuthorizationManagementDependency.ClientComponentState.ACTIVE,
          descriptor.audience,
          descriptor.voucherLifespan,
          *
        )
        .returning(Future.unit)
        .once()

      Post() ~> service.activateDescriptor(SpecData.catalogItem.id.toString, descriptorId.toString) ~> check {
        status shouldEqual StatusCodes.NoContent
      }

    }

    "activate Published descriptor - case 2" in {
      val requesterId                             = UUID.randomUUID()
      val descriptorId                            = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor = SpecData.catalogDescriptor.copy(id = descriptorId, version = "4", state = Suspended)

      val eService = SpecData.catalogItem.copy(
        descriptors = Seq(
          SpecData.catalogDescriptor.copy(version = "1", state = Archived),
          SpecData.catalogDescriptor.copy(version = "2", state = Suspended),
          descriptor
        ),
        producerId = requesterId
      )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(eService))

      (mockCatalogManagementService
        .publishDescriptor(_: String, _: String)(_: Seq[(String, String)]))
        .expects(eService.id.toString, descriptor.id.toString, *)
        .returning(Future.unit)
        .once()

      (mockAuthorizationManagementService
        .updateStateOnClients(
          _: UUID,
          _: UUID,
          _: AuthorizationManagementDependency.ClientComponentState,
          _: Seq[String],
          _: Int
        )(_: Seq[(String, String)]))
        .expects(
          eService.id,
          descriptor.id,
          AuthorizationManagementDependency.ClientComponentState.ACTIVE,
          descriptor.audience,
          descriptor.voucherLifespan,
          *
        )
        .returning(Future.unit)
        .once()

      Post() ~> service.activateDescriptor(SpecData.catalogItem.id.toString, descriptorId.toString) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }
    "fail if descriptor is Draft" in {
      val requesterId                             = UUID.randomUUID()
      val descriptorId                            = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor = SpecData.catalogDescriptor.copy(id = descriptorId, state = Draft)

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), producerId = requesterId)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(eService))

      Post() ~> service.activateDescriptor(SpecData.catalogItem.id.toString, descriptorId.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
    "fail if descriptor is Deprecated" in {
      val requesterId                             = UUID.randomUUID()
      val descriptorId                            = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor = SpecData.catalogDescriptor.copy(id = descriptorId, state = Deprecated)

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), producerId = requesterId)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(eService))

      Post() ~> service.activateDescriptor(SpecData.catalogItem.id.toString, descriptorId.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
    "fail if descriptor is Published" in {
      val requesterId                             = UUID.randomUUID()
      val descriptorId                            = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor = SpecData.catalogDescriptor.copy(id = descriptorId, state = Published)

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), producerId = requesterId)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(eService))

      Post() ~> service.activateDescriptor(SpecData.catalogItem.id.toString, descriptorId.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
    "fail if descriptor is Archived" in {
      val requesterId                             = UUID.randomUUID()
      val descriptorId                            = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor = SpecData.catalogDescriptor.copy(id = descriptorId, state = Archived)

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), producerId = requesterId)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(eService))

      Post() ~> service.activateDescriptor(SpecData.catalogItem.id.toString, descriptorId.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
    "fail if EService does not exist" in {

      val requesterId                             = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.failed(EServiceNotFound(SpecData.catalogItem.id.toString)))

      Post() ~> service.activateDescriptor(SpecData.catalogItem.id.toString, UUID.randomUUID.toString) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if EService descriptor does not exist" in {

      val requesterId                             = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem.copy(producerId = requesterId)))

      Post() ~> service.activateDescriptor(SpecData.catalogItem.id.toString, UUID.randomUUID.toString) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if requester is not the Producer" in {
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem.copy(producerId = UUID.randomUUID())))

      Post() ~> service.activateDescriptor(SpecData.catalogItem.id.toString, UUID.randomUUID.toString) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
  }
  "Descriptor deletion" should {
    "succeed if descriptor is Draft" in {

      val requesterId                             = UUID.randomUUID()
      val descriptorId                            = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor = SpecData.catalogDescriptor.copy(id = descriptorId, state = Draft)

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), producerId = requesterId)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(eService))

      (mockCatalogManagementService
        .deleteDraft(_: String, _: String)(_: Seq[(String, String)]))
        .expects(eService.id.toString, descriptor.id.toString, *)
        .returning(Future.unit)
        .once()

      Delete() ~> service.deleteDraft(SpecData.catalogItem.id.toString, descriptor.id.toString) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }
    "fail if EService does not exist" in {

      val requesterId                             = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.failed(EServiceNotFound(SpecData.catalogItem.id.toString)))

      Delete() ~> service.deleteDraft(SpecData.catalogItem.id.toString, UUID.randomUUID.toString) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if requester is not the Producer" in {
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      val draftDescriptor = SpecData.catalogDescriptor.copy(state = Draft)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(
          Future
            .successful(SpecData.catalogItem.copy(producerId = UUID.randomUUID(), descriptors = Seq(draftDescriptor)))
        )

      Delete() ~> service.deleteDraft(SpecData.catalogItem.id.toString, draftDescriptor.id.toString) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
  }
  "EService deletion" should {
    "succeed" in {

      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val eService = SpecData.catalogItem.copy(descriptors = Seq.empty, producerId = requesterId)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(eService))

      (mockCatalogManagementService
        .deleteEService(_: String)(_: Seq[(String, String)]))
        .expects(eService.id.toString, *)
        .returning(Future.unit)
        .once()

      Delete() ~> service.deleteEService(SpecData.catalogItem.id.toString) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }
    "fail if EService does not exist" in {

      val requesterId                             = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.failed(EServiceNotFound(SpecData.catalogItem.id.toString)))

      Delete() ~> service.deleteEService(SpecData.catalogItem.id.toString) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if EService has descriptors" in {

      val requesterId                             = UUID.randomUUID()
      val descriptorId                            = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor = SpecData.catalogDescriptor.copy(id = descriptorId)

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), producerId = requesterId)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(eService))

      (mockCatalogManagementService
        .deleteEService(_: String)(_: Seq[(String, String)]))
        .expects(eService.id.toString, *)
        .returning(Future.failed(EServiceWithDescriptorsNotDeletable(SpecData.catalogItem.id.toString)))
        .once()

      Delete() ~> service.deleteEService(SpecData.catalogItem.id.toString) ~> check {
        status shouldEqual StatusCodes.Conflict
      }
    }
    "fail if requester is not the Producer" in {
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem.copy(producerId = UUID.randomUUID())))

      Delete() ~> service.deleteEService(SpecData.catalogItem.id.toString) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
  }
  "Document creation" should {
    "succeed" in {
      val requesterId                             = UUID.randomUUID()
      val descriptorId                            = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor = SpecData.catalogDescriptor.copy(id = descriptorId, state = Draft)

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), producerId = requesterId)

      val documentId = UUID.randomUUID()

      val seed = CreateEServiceDescriptorDocumentSeed(
        documentId = documentId,
        kind = EServiceDocumentKind.DOCUMENT,
        prettyName = "prettyName",
        filePath = "filePath",
        fileName = "fileName",
        contentType = "application/pdf",
        checksum = "checksum",
        serverUrls = Seq.empty
      )

      val managementSeed = CatalogManagementDependency.CreateEServiceDescriptorDocumentSeed(
        documentId = documentId,
        kind = CatalogManagementDependency.EServiceDocumentKind.DOCUMENT,
        prettyName = "prettyName",
        filePath = "filePath",
        fileName = "fileName",
        contentType = "application/pdf",
        checksum = "checksum",
        serverUrls = Seq.empty
      )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(eService))

      (mockCatalogManagementService
        .createEServiceDocument(_: UUID, _: UUID, _: CatalogManagementDependency.CreateEServiceDescriptorDocumentSeed)(
          _: Seq[(String, String)]
        ))
        .expects(eService.id, descriptorId, managementSeed, *)
        .returning(Future.successful(SpecData.eService))
        .once()

      Post() ~> service.createEServiceDocument(SpecData.catalogItem.id.toString, descriptorId.toString, seed) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    "fail if EService does not exist" in {

      val requesterId                             = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val seed = CreateEServiceDescriptorDocumentSeed(
        documentId = UUID.randomUUID(),
        kind = EServiceDocumentKind.DOCUMENT,
        prettyName = "prettyName",
        filePath = "filePath",
        fileName = "fileName",
        contentType = "application/pdf",
        checksum = "checksum",
        serverUrls = Seq.empty
      )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.failed(EServiceNotFound(SpecData.catalogItem.id.toString)))

      Post() ~> service.createEServiceDocument(
        SpecData.catalogItem.id.toString,
        UUID.randomUUID().toString,
        seed
      ) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if EService descriptor does not exist" in {

      val requesterId                             = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val seed = CreateEServiceDescriptorDocumentSeed(
        documentId = UUID.randomUUID(),
        kind = EServiceDocumentKind.DOCUMENT,
        prettyName = "prettyName",
        filePath = "filePath",
        fileName = "fileName",
        contentType = "application/pdf",
        checksum = "checksum",
        serverUrls = Seq.empty
      )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem.copy(producerId = requesterId)))

      Post() ~> service.createEServiceDocument(
        SpecData.catalogItem.id.toString,
        UUID.randomUUID().toString,
        seed
      ) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if EService descriptor is not draft" in {

      val requesterId                             = UUID.randomUUID()
      val descriptorId                            = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor = SpecData.catalogDescriptor.copy(id = descriptorId, state = Published)

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), producerId = requesterId)

      val documentId = UUID.randomUUID()

      val seed = CreateEServiceDescriptorDocumentSeed(
        documentId = documentId,
        kind = EServiceDocumentKind.DOCUMENT,
        prettyName = "prettyName",
        filePath = "filePath",
        fileName = "fileName",
        contentType = "application/pdf",
        checksum = "checksum",
        serverUrls = Seq.empty
      )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(eService))

      Post() ~> service.createEServiceDocument(SpecData.catalogItem.id.toString, descriptorId.toString, seed) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
    "fail if requester is not the Producer" in {
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      val seed = CreateEServiceDescriptorDocumentSeed(
        documentId = UUID.randomUUID(),
        kind = EServiceDocumentKind.DOCUMENT,
        prettyName = "prettyName",
        filePath = "filePath",
        fileName = "fileName",
        contentType = "application/pdf",
        checksum = "checksum",
        serverUrls = Seq.empty
      )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem.copy(producerId = UUID.randomUUID())))

      Post() ~> service.createEServiceDocument(
        SpecData.catalogItem.id.toString,
        UUID.randomUUID().toString,
        seed
      ) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
    "fail if document is an interface and it already exists" in {
      val requesterId                             = UUID.randomUUID()
      val descriptorId                            = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor = SpecData.catalogDescriptor.copy(
        id = descriptorId,
        interface = Some(
          CatalogDocument(
            id = UUID.randomUUID(),
            name = "name",
            contentType = "application/yaml",
            prettyName = "pretty",
            path = "path",
            checksum = "checksum",
            uploadDate = OffsetDateTimeSupplier.get().minusDays(10)
          )
        )
      )

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), producerId = requesterId)

      val documentId = UUID.randomUUID()

      val seed = CreateEServiceDescriptorDocumentSeed(
        documentId = documentId,
        kind = EServiceDocumentKind.INTERFACE,
        prettyName = "prettyName",
        filePath = "filePath",
        fileName = "fileName",
        contentType = "application/json",
        checksum = "checksum",
        serverUrls = Seq.empty
      )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(eService))

      Post() ~> service.createEServiceDocument(SpecData.catalogItem.id.toString, descriptorId.toString, seed) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }
  "Document retrieve" should {
    "succeed if role is admin and the requester is the producer" in {

      val requesterId  = UUID.randomUUID()
      val descriptorId = UUID.randomUUID()
      val documentId   = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor = SpecData.catalogDescriptor.copy(
        id = descriptorId,
        interface = Some(SpecData.catalogDocument.copy(id = documentId))
      )

      val eService = SpecData.catalogItem.copy(producerId = requesterId, descriptors = Seq(descriptor))

      (mockCatalogManagementService
        .getEServiceDocument(_: UUID, _: UUID, _: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, descriptorId, documentId, *, *)
        .once()
        .returns(Future.successful((eService, SpecData.catalogDocument.copy(id = documentId))))

      Post() ~> service.getEServiceDocumentById(
        eService.id.toString,
        descriptorId.toString,
        documentId.toString
      ) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    "succeed if role is api and the requester is the producer" in {

      val requesterId  = UUID.randomUUID()
      val descriptorId = UUID.randomUUID()
      val documentId   = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "api", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor = SpecData.catalogDescriptor.copy(
        id = descriptorId,
        interface = Some(SpecData.catalogDocument.copy(id = documentId))
      )

      val eService = SpecData.catalogItem.copy(producerId = requesterId, descriptors = Seq(descriptor))

      (mockCatalogManagementService
        .getEServiceDocument(_: UUID, _: UUID, _: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, descriptorId, documentId, *, *)
        .once()
        .returns(Future.successful((eService, SpecData.catalogDocument.copy(id = documentId))))

      Post() ~> service.getEServiceDocumentById(
        eService.id.toString,
        descriptorId.toString,
        documentId.toString
      ) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    "fail if the requester is not the producer and document belongs to a draft descriptor " in {

      val requesterId  = UUID.randomUUID()
      val descriptorId = UUID.randomUUID()
      val documentId   = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      val descriptor = SpecData.catalogDescriptor.copy(
        id = descriptorId,
        state = Draft,
        interface = Some(SpecData.catalogDocument.copy(id = documentId))
      )

      val eService = SpecData.catalogItem.copy(producerId = requesterId, descriptors = Seq(descriptor))

      (mockCatalogManagementService
        .getEServiceDocument(_: UUID, _: UUID, _: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, descriptorId, documentId, *, *)
        .once()
        .returns(Future.successful((eService, SpecData.catalogDocument.copy(id = documentId))))

      Post() ~> service.getEServiceDocumentById(
        eService.id.toString,
        descriptorId.toString,
        documentId.toString
      ) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if Document does not exist" in {

      val descriptorId = UUID.randomUUID()
      val documentId   = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      val descriptor = SpecData.catalogDescriptor.copy(
        id = descriptorId,
        interface = Some(SpecData.catalogDocument.copy(id = documentId))
      )

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor))

      (mockCatalogManagementService
        .getEServiceDocument(_: UUID, _: UUID, _: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, descriptorId, documentId, *, *)
        .once()
        .returns(
          Future.failed(DescriptorDocumentNotFound(eService.id.toString, descriptorId.toString, documentId.toString))
        )

      Post() ~> service.getEServiceDocumentById(
        eService.id.toString,
        descriptorId.toString,
        documentId.toString
      ) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
  }
  "Document update" should {
    "succeed on draft descriptor" in {
      val requesterId  = UUID.randomUUID()
      val descriptorId = UUID.randomUUID()
      val documentId   = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor =
        SpecData.catalogDescriptor.copy(
          id = descriptorId,
          state = Draft,
          docs = Seq(SpecData.catalogDocument.copy(id = documentId))
        )

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), producerId = requesterId)

      val seed = UpdateEServiceDescriptorDocumentSeed(prettyName = "prettyNameUpdated")

      val managementSeed =
        CatalogManagementDependency.UpdateEServiceDescriptorDocumentSeed(prettyName = "prettyNameUpdated")

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(eService))

      (mockCatalogManagementService
        .updateEServiceDocument(
          _: String,
          _: String,
          _: String,
          _: CatalogManagementDependency.UpdateEServiceDescriptorDocumentSeed
        )(_: Seq[(String, String)]))
        .expects(eService.id.toString, descriptorId.toString, documentId.toString, managementSeed, *)
        .returning(Future.successful(SpecData.eServiceDoc))
        .once()

      Post() ~> service.updateEServiceDocumentById(
        SpecData.catalogItem.id.toString,
        descriptorId.toString,
        documentId.toString,
        seed
      ) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    "fail if descriptor is not draft" in {
      val requesterId  = UUID.randomUUID()
      val descriptorId = UUID.randomUUID()
      val documentId   = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor =
        SpecData.catalogDescriptor.copy(id = descriptorId, docs = Seq(SpecData.catalogDocument.copy(id = documentId)))

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), producerId = requesterId)

      val seed = UpdateEServiceDescriptorDocumentSeed(prettyName = "prettyNameUpdated")

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(eService))

      Post() ~> service.updateEServiceDocumentById(
        SpecData.catalogItem.id.toString,
        descriptorId.toString,
        documentId.toString,
        seed
      ) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
    "fail if EService does not exist" in {

      val requesterId                             = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val seed = UpdateEServiceDescriptorDocumentSeed(prettyName = "prettyNameUpdated")

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.failed(EServiceNotFound(SpecData.catalogItem.id.toString)))

      Post() ~> service.updateEServiceDocumentById(
        SpecData.catalogItem.id.toString,
        UUID.randomUUID().toString,
        UUID.randomUUID().toString,
        seed
      ) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if descriptor does not exist" in {

      val requesterId = UUID.randomUUID()
      val eService    = SpecData.catalogItem.copy(descriptors = Seq.empty, producerId = requesterId)

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val seed = UpdateEServiceDescriptorDocumentSeed(prettyName = "prettyNameUpdated")

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(eService))

      Post() ~> service.updateEServiceDocumentById(
        SpecData.catalogItem.id.toString,
        UUID.randomUUID().toString,
        UUID.randomUUID().toString,
        seed
      ) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if requester is not the Producer" in {
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      val seed = UpdateEServiceDescriptorDocumentSeed(prettyName = "prettyNameUpdated")

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem.copy(producerId = UUID.randomUUID())))

      Post() ~> service.updateEServiceDocumentById(
        SpecData.catalogItem.id.toString,
        UUID.randomUUID().toString,
        UUID.randomUUID().toString,
        seed
      ) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
  }
  "Document deletion" should {
    "succeed only on Draft Descriptor" in {
      val requesterId  = UUID.randomUUID()
      val descriptorId = UUID.randomUUID()
      val documentId   = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor =
        SpecData.catalogDescriptor.copy(
          id = descriptorId,
          docs = Seq(SpecData.catalogDocument.copy(id = documentId)),
          state = Draft
        )

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), producerId = requesterId)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(eService))

      (mockCatalogManagementService
        .deleteEServiceDocument(_: String, _: String, _: String)(_: Seq[(String, String)]))
        .expects(eService.id.toString, descriptorId.toString, documentId.toString, *)
        .returning(Future.unit)
        .once()

      Delete() ~> service.deleteEServiceDocumentById(
        SpecData.catalogItem.id.toString,
        descriptorId.toString,
        documentId.toString
      ) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }
    "fail if EService does not exist" in {

      val requesterId                             = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.failed(EServiceNotFound(SpecData.catalogItem.id.toString)))

      Delete() ~> service.deleteEServiceDocumentById(
        SpecData.catalogItem.id.toString,
        UUID.randomUUID().toString,
        UUID.randomUUID().toString
      ) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if requester is not the Producer" in {
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem.copy(producerId = UUID.randomUUID())))

      Delete() ~> service.deleteEServiceDocumentById(
        SpecData.catalogItem.id.toString,
        UUID.randomUUID().toString,
        UUID.randomUUID().toString
      ) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
    "fail if descriptor does not exists" in {
      val requesterId  = UUID.randomUUID()
      val descriptorId = UUID.randomUUID()
      val documentId   = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor =
        SpecData.catalogDescriptor.copy(
          id = UUID.randomUUID(),
          docs = Seq(SpecData.catalogDocument.copy(id = documentId))
        )

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), producerId = requesterId)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(eService))

      Delete() ~> service.deleteEServiceDocumentById(
        SpecData.catalogItem.id.toString,
        descriptorId.toString,
        documentId.toString
      ) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if descriptor is not Draft" in {
      val requesterId  = UUID.randomUUID()
      val descriptorId = UUID.randomUUID()
      val documentId   = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor =
        SpecData.catalogDescriptor.copy(
          id = descriptorId,
          docs = Seq(SpecData.catalogDocument.copy(id = documentId)),
          state = Published
        )

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), producerId = requesterId)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(eService))

      Delete() ~> service.deleteEServiceDocumentById(
        SpecData.catalogItem.id.toString,
        descriptorId.toString,
        documentId.toString
      ) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }
  "Risk Analysis creation" should {
    "succeed" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor =
        SpecData.catalogDescriptor.copy(state = Draft)

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), producerId = requesterId, mode = Receive)

      val riskAnalysisSeed = EServiceRiskAnalysisSeed(
        name = "newName",
        riskAnalysisForm = EServiceRiskAnalysisFormSeed(
          version = "3.0",
          answers = Map("purpose" -> Seq("INSTITUTIONAL"), "personalDataTypes" -> Seq("OTHER"))
        )
      )

      val dependencyRiskAnalysisSeed =
        CatalogManagementDependency.RiskAnalysisSeed(
          name = "newName",
          riskAnalysisForm = CatalogManagementDependency.RiskAnalysisFormSeed(
            version = "3.0",
            singleAnswers = Seq(
              CatalogManagementDependency.RiskAnalysisSingleAnswerSeed(key = "purpose", value = Some("INSTITUTIONAL"))
            ),
            multiAnswers = Seq(
              CatalogManagementDependency
                .RiskAnalysisMultiAnswerSeed(key = "personalDataTypes", values = Seq("OTHER"))
            )
          )
        )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eService.id, *, *)
        .once()
        .returns(Future.successful(eService))

      (mockTenantManagementService
        .getTenantById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(requesterId, *, *)
        .once()
        .returns(Future.successful(SpecData.persistentTenant.copy(id = requesterId)))

      (mockCatalogManagementService
        .createRiskAnalysis(_: UUID, _: CatalogManagementDependency.RiskAnalysisSeed)(_: Seq[(String, String)]))
        .expects(eService.id, dependencyRiskAnalysisSeed, *)
        .returning(Future.successful(()))
        .once()

      Post() ~> service.createRiskAnalysis(eService.id.toString, riskAnalysisSeed) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }
    "fail if EService mode is not RECEIVE" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor =
        SpecData.catalogDescriptor.copy(state = Draft)

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), producerId = requesterId, mode = Deliver)

      val riskAnalysisSeed = EServiceRiskAnalysisSeed(
        name = "newName",
        riskAnalysisForm = EServiceRiskAnalysisFormSeed(
          version = "3.0",
          answers = Map("purpose" -> Seq("INSTITUTIONAL"), "personalDataTypes" -> Seq("OTHER"))
        )
      )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eService.id, *, *)
        .once()
        .returns(Future.successful(eService))

      Post() ~> service.createRiskAnalysis(eService.id.toString, riskAnalysisSeed) ~> check {
        status shouldEqual StatusCodes.BadRequest
        val problem = responseAs[Problem]
        problem.status shouldBe StatusCodes.BadRequest.intValue
        problem.errors.head.code shouldBe "009-0013"
      }
    }
    "fail if requester is not the Producer" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor =
        SpecData.catalogDescriptor.copy(state = Draft)

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), mode = Receive)

      val riskAnalysisSeed = EServiceRiskAnalysisSeed(
        name = "newName",
        riskAnalysisForm = EServiceRiskAnalysisFormSeed(
          version = "3.0",
          answers = Map("purpose" -> Seq("INSTITUTIONAL"), "personalDataTypes" -> Seq("OTHER"))
        )
      )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eService.id, *, *)
        .once()
        .returns(Future.successful(eService))

      Post() ~> service.createRiskAnalysis(eService.id.toString, riskAnalysisSeed) ~> check {
        status shouldEqual StatusCodes.Forbidden
        val problem = responseAs[Problem]
        problem.status shouldBe StatusCodes.Forbidden.intValue
        problem.errors.head.code shouldBe "009-9989"
      }
    }
    "fail if descriptor is not DRAFT" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor =
        SpecData.catalogDescriptor.copy(state = Published)

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), producerId = requesterId, mode = Receive)

      val riskAnalysisSeed = EServiceRiskAnalysisSeed(
        name = "newName",
        riskAnalysisForm = EServiceRiskAnalysisFormSeed(
          version = "3.0",
          answers = Map("purpose" -> Seq("INSTITUTIONAL"), "personalDataTypes" -> Seq("OTHER"))
        )
      )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eService.id, *, *)
        .once()
        .returns(Future.successful(eService))

      Post() ~> service.createRiskAnalysis(eService.id.toString, riskAnalysisSeed) ~> check {
        status shouldEqual StatusCodes.BadRequest
        val problem = responseAs[Problem]
        problem.status shouldBe StatusCodes.BadRequest.intValue
        problem.errors.head.code shouldBe "009-0012"
      }
    }
    "fail if Risk Analysis did not pass validation" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor =
        SpecData.catalogDescriptor.copy(state = Draft)

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), producerId = requesterId, mode = Receive)

      val riskAnalysisSeed = EServiceRiskAnalysisSeed(
        name = "newName",
        riskAnalysisForm = EServiceRiskAnalysisFormSeed(
          version = "3.0",
          answers = Map("purpose1" -> Seq("INSTITUTIONAL"), "personalDataTypes" -> Seq("OTHER"))
        )
      )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eService.id, *, *)
        .once()
        .returns(Future.successful(eService))

      (mockTenantManagementService
        .getTenantById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(requesterId, *, *)
        .once()
        .returns(Future.successful(SpecData.persistentTenant.copy(id = requesterId)))

      Post() ~> service.createRiskAnalysis(eService.id.toString, riskAnalysisSeed) ~> check {
        status shouldEqual StatusCodes.BadRequest
        val problem = responseAs[Problem]
        problem.status shouldBe StatusCodes.BadRequest.intValue
        problem.errors.head.code shouldBe "009-0019"
      }
    }
  }
  "Risk Analysis update" should {
    "succeed" in {
      val requesterId        = UUID.randomUUID()
      val riskAnalysisId     = UUID.randomUUID()
      val riskAnalysisFormId = UUID.randomUUID()
      val single             = UUID.randomUUID()
      val multi              = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val createdDate = OffsetDateTimeSupplier.get()

      val descriptor =
        SpecData.catalogDescriptor.copy(state = Draft)

      val eService = SpecData.catalogItem.copy(
        descriptors = Seq(descriptor),
        producerId = requesterId,
        riskAnalysis = Seq(
          CatalogRiskAnalysis(
            id = riskAnalysisId,
            name = "OldName",
            riskAnalysisForm = CatalogRiskAnalysisForm(
              id = riskAnalysisFormId,
              version = "3.0",
              singleAnswers =
                Seq(CatalogRiskAnalysisSingleAnswer(id = single, key = "purpose", value = Some("INSTITUTIONAL"))),
              multiAnswers =
                Seq(CatalogRiskAnalysisMultiAnswer(id = multi, key = "personalDataTypes", values = Seq("OTHER")))
            ),
            createdAt = createdDate
          )
        ),
        mode = Receive
      )

      val riskAnalysisSeed = EServiceRiskAnalysisSeed(
        name = "newName",
        riskAnalysisForm = EServiceRiskAnalysisFormSeed(
          version = "3.0",
          answers = Map("purpose" -> Seq("INSTITUTIONAL"), "personalDataTypes" -> Seq("OTHER"))
        )
      )

      val dependencyRiskAnalysisSeed =
        CatalogManagementDependency.RiskAnalysisSeed(
          name = "newName",
          riskAnalysisForm = CatalogManagementDependency.RiskAnalysisFormSeed(
            version = "3.0",
            singleAnswers = Seq(
              CatalogManagementDependency.RiskAnalysisSingleAnswerSeed(key = "purpose", value = Some("INSTITUTIONAL"))
            ),
            multiAnswers = Seq(
              CatalogManagementDependency
                .RiskAnalysisMultiAnswerSeed(key = "personalDataTypes", values = Seq("OTHER"))
            )
          )
        )

      val expected = CatalogManagementDependency.EServiceRiskAnalysis(
        id = riskAnalysisId,
        name = "newName",
        riskAnalysisForm = CatalogManagementDependency.RiskAnalysisForm(
          id = riskAnalysisFormId,
          version = "3.0",
          singleAnswers = Seq(
            CatalogManagementDependency
              .RiskAnalysisSingleAnswer(id = single, key = "purpose", value = Some("INSTITUTIONAL"))
          ),
          multiAnswers = Seq(
            CatalogManagementDependency
              .RiskAnalysisMultiAnswer(id = multi, key = "personalDataTypes", values = Seq("OTHER"))
          )
        ),
        createdAt = createdDate
      )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eService.id, *, *)
        .once()
        .returns(Future.successful(eService))

      (mockTenantManagementService
        .getTenantById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(requesterId, *, *)
        .once()
        .returns(Future.successful(SpecData.persistentTenant.copy(id = requesterId)))

      (mockCatalogManagementService
        .updateRiskAnalysis(_: UUID, _: UUID, _: CatalogManagementDependency.RiskAnalysisSeed)(
          _: Seq[(String, String)]
        ))
        .expects(eService.id, riskAnalysisId, dependencyRiskAnalysisSeed, *)
        .returning(Future.successful(expected))
        .once()

      Post() ~> service.updateRiskAnalysis(eService.id.toString, riskAnalysisId.toString, riskAnalysisSeed) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }

    "fail if Risk Analysis does not exists on EService" in {
      val requesterId        = UUID.randomUUID()
      val riskAnalysisId     = UUID.randomUUID()
      val riskAnalysisFormId = UUID.randomUUID()
      val single             = UUID.randomUUID()
      val multi              = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val createdDate = OffsetDateTimeSupplier.get()

      val descriptor =
        SpecData.catalogDescriptor.copy(state = Draft)

      val eService = SpecData.catalogItem.copy(
        descriptors = Seq(descriptor),
        producerId = requesterId,
        riskAnalysis = Seq(
          CatalogRiskAnalysis(
            id = UUID.randomUUID(),
            name = "OldName",
            riskAnalysisForm = CatalogRiskAnalysisForm(
              id = riskAnalysisFormId,
              version = "3.0",
              singleAnswers =
                Seq(CatalogRiskAnalysisSingleAnswer(id = single, key = "purpose", value = Some("INSTITUTIONAL"))),
              multiAnswers =
                Seq(CatalogRiskAnalysisMultiAnswer(id = multi, key = "personalDataTypes", values = Seq("OTHER")))
            ),
            createdAt = createdDate
          )
        ),
        mode = Receive
      )

      val riskAnalysisSeed = EServiceRiskAnalysisSeed(
        name = "newName",
        riskAnalysisForm = EServiceRiskAnalysisFormSeed(
          version = "3.0",
          answers = Map("purpose" -> Seq("INSTITUTIONAL"), "personalDataTypes" -> Seq("OTHER"))
        )
      )

      val dependencyRiskAnalysisSeed =
        CatalogManagementDependency.RiskAnalysisSeed(
          name = "newName",
          riskAnalysisForm = CatalogManagementDependency.RiskAnalysisFormSeed(
            version = "3.0",
            singleAnswers = Seq(
              CatalogManagementDependency.RiskAnalysisSingleAnswerSeed(key = "purpose", value = Some("INSTITUTIONAL"))
            ),
            multiAnswers = Seq(
              CatalogManagementDependency
                .RiskAnalysisMultiAnswerSeed(key = "personalDataTypes", values = Seq("OTHER"))
            )
          )
        )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eService.id, *, *)
        .once()
        .returns(Future.successful(eService))

      (mockTenantManagementService
        .getTenantById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(requesterId, *, *)
        .once()
        .returns(Future.successful(SpecData.persistentTenant.copy(id = requesterId)))

      (mockCatalogManagementService
        .updateRiskAnalysis(_: UUID, _: UUID, _: CatalogManagementDependency.RiskAnalysisSeed)(
          _: Seq[(String, String)]
        ))
        .expects(eService.id, riskAnalysisId, dependencyRiskAnalysisSeed, *)
        .returning(Future.failed(EServiceRiskAnalysisNotFound(eService.id, riskAnalysisId)))
        .once()

      Post() ~> service.updateRiskAnalysis(eService.id.toString, riskAnalysisId.toString, riskAnalysisSeed) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "fail if EService mode is not RECEIVE" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor =
        SpecData.catalogDescriptor.copy(state = Draft)

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), producerId = requesterId, mode = Deliver)

      val riskAnalysisSeed = EServiceRiskAnalysisSeed(
        name = "newName",
        riskAnalysisForm = EServiceRiskAnalysisFormSeed(
          version = "3.0",
          answers = Map("purpose" -> Seq("INSTITUTIONAL"), "personalDataTypes" -> Seq("OTHER"))
        )
      )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eService.id, *, *)
        .once()
        .returns(Future.successful(eService))

      Post() ~> service.updateRiskAnalysis(
        eService.id.toString,
        UUID.randomUUID().toString,
        riskAnalysisSeed
      ) ~> check {
        status shouldEqual StatusCodes.BadRequest
        val problem = responseAs[Problem]
        problem.status shouldBe StatusCodes.BadRequest.intValue
        problem.errors.head.code shouldBe "009-0013"
      }
    }
    "fail if requester is not the Producer" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor =
        SpecData.catalogDescriptor.copy(state = Draft)

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), mode = Receive)

      val riskAnalysisSeed = EServiceRiskAnalysisSeed(
        name = "newName",
        riskAnalysisForm = EServiceRiskAnalysisFormSeed(
          version = "3.0",
          answers = Map("purpose" -> Seq("INSTITUTIONAL"), "personalDataTypes" -> Seq("OTHER"))
        )
      )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eService.id, *, *)
        .once()
        .returns(Future.successful(eService))

      Post() ~> service.updateRiskAnalysis(
        eService.id.toString,
        UUID.randomUUID().toString,
        riskAnalysisSeed
      ) ~> check {
        status shouldEqual StatusCodes.Forbidden
        val problem = responseAs[Problem]
        problem.status shouldBe StatusCodes.Forbidden.intValue
        problem.errors.head.code shouldBe "009-9989"
      }
    }
    "fail if descriptor is not DRAFT" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor =
        SpecData.catalogDescriptor.copy(state = Published)

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), producerId = requesterId, mode = Receive)

      val riskAnalysisSeed = EServiceRiskAnalysisSeed(
        name = "newName",
        riskAnalysisForm = EServiceRiskAnalysisFormSeed(
          version = "3.0",
          answers = Map("purpose" -> Seq("INSTITUTIONAL"), "personalDataTypes" -> Seq("OTHER"))
        )
      )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eService.id, *, *)
        .once()
        .returns(Future.successful(eService))

      Post() ~> service.updateRiskAnalysis(
        eService.id.toString,
        UUID.randomUUID().toString,
        riskAnalysisSeed
      ) ~> check {
        status shouldEqual StatusCodes.BadRequest
        val problem = responseAs[Problem]
        problem.status shouldBe StatusCodes.BadRequest.intValue
        problem.errors.head.code shouldBe "009-0012"
      }
    }
    "fail if Risk Analysis did not pass validation" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor =
        SpecData.catalogDescriptor.copy(state = Draft)

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), producerId = requesterId, mode = Receive)

      val riskAnalysisSeed = EServiceRiskAnalysisSeed(
        name = "newName",
        riskAnalysisForm = EServiceRiskAnalysisFormSeed(
          version = "3.0",
          answers = Map("purpose1" -> Seq("INSTITUTIONAL"), "personalDataTypes" -> Seq("OTHER"))
        )
      )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eService.id, *, *)
        .once()
        .returns(Future.successful(eService))

      (mockTenantManagementService
        .getTenantById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(requesterId, *, *)
        .once()
        .returns(Future.successful(SpecData.persistentTenant.copy(id = requesterId)))

      Post() ~> service.updateRiskAnalysis(
        eService.id.toString,
        UUID.randomUUID().toString,
        riskAnalysisSeed
      ) ~> check {
        status shouldEqual StatusCodes.BadRequest
        val problem = responseAs[Problem]
        problem.status shouldBe StatusCodes.BadRequest.intValue
        problem.errors.head.code shouldBe "009-0019"
      }
    }
  }
  "Risk Analysis delete" should {
    "succeed" in {
      val requesterId        = UUID.randomUUID()
      val riskAnalysisId     = UUID.randomUUID()
      val riskAnalysisFormId = UUID.randomUUID()
      val single             = UUID.randomUUID()
      val multi              = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val createdDate = OffsetDateTimeSupplier.get()

      val descriptor =
        SpecData.catalogDescriptor.copy(state = Draft)

      val eService = SpecData.catalogItem.copy(
        descriptors = Seq(descriptor),
        producerId = requesterId,
        riskAnalysis = Seq(
          CatalogRiskAnalysis(
            id = riskAnalysisId,
            name = "OldName",
            riskAnalysisForm = CatalogRiskAnalysisForm(
              id = riskAnalysisFormId,
              version = "3.0",
              singleAnswers =
                Seq(CatalogRiskAnalysisSingleAnswer(id = single, key = "purpose", value = Some("INSTITUTIONAL"))),
              multiAnswers =
                Seq(CatalogRiskAnalysisMultiAnswer(id = multi, key = "personalDataTypes", values = Seq("OTHER")))
            ),
            createdAt = createdDate
          )
        ),
        mode = Receive
      )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eService.id, *, *)
        .once()
        .returns(Future.successful(eService))

      (mockCatalogManagementService
        .deleteRiskAnalysis(_: UUID, _: UUID)(_: Seq[(String, String)]))
        .expects(eService.id, riskAnalysisId, *)
        .returning(Future.successful(()))
        .once()

      Post() ~> service.deleteRiskAnalysis(eService.id.toString, riskAnalysisId.toString) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }

    "fail if Risk Analysis does not exists on EService" in {
      val requesterId        = UUID.randomUUID()
      val riskAnalysisId     = UUID.randomUUID()
      val riskAnalysisFormId = UUID.randomUUID()
      val single             = UUID.randomUUID()
      val multi              = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val createdDate = OffsetDateTimeSupplier.get()

      val descriptor =
        SpecData.catalogDescriptor.copy(state = Draft)

      val eService = SpecData.catalogItem.copy(
        descriptors = Seq(descriptor),
        producerId = requesterId,
        riskAnalysis = Seq(
          CatalogRiskAnalysis(
            id = UUID.randomUUID(),
            name = "OldName",
            riskAnalysisForm = CatalogRiskAnalysisForm(
              id = riskAnalysisFormId,
              version = "3.0",
              singleAnswers =
                Seq(CatalogRiskAnalysisSingleAnswer(id = single, key = "purpose", value = Some("INSTITUTIONAL"))),
              multiAnswers =
                Seq(CatalogRiskAnalysisMultiAnswer(id = multi, key = "personalDataTypes", values = Seq("OTHER")))
            ),
            createdAt = createdDate
          )
        ),
        mode = Receive
      )

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eService.id, *, *)
        .once()
        .returns(Future.successful(eService))

      (mockCatalogManagementService
        .deleteRiskAnalysis(_: UUID, _: UUID)(_: Seq[(String, String)]))
        .expects(eService.id, riskAnalysisId, *)
        .returning(Future.failed(EServiceRiskAnalysisNotFound(eService.id, riskAnalysisId)))
        .once()

      Post() ~> service.deleteRiskAnalysis(eService.id.toString, riskAnalysisId.toString) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if descriptor is not DRAFT" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor =
        SpecData.catalogDescriptor.copy(state = Published)

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), producerId = requesterId, mode = Receive)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eService.id, *, *)
        .once()
        .returns(Future.successful(eService))

      Post() ~> service.deleteRiskAnalysis(eService.id.toString, UUID.randomUUID().toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
        val problem = responseAs[Problem]
        problem.status shouldBe StatusCodes.BadRequest.intValue
        problem.errors.head.code shouldBe "009-0012"
      }
    }
    "fail if EService is not Receive" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor =
        SpecData.catalogDescriptor.copy(state = Draft)

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), producerId = requesterId, mode = Deliver)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eService.id, *, *)
        .once()
        .returns(Future.successful(eService))

      Post() ~> service.deleteRiskAnalysis(eService.id.toString, UUID.randomUUID().toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
        val problem = responseAs[Problem]
        problem.status shouldBe StatusCodes.BadRequest.intValue
        problem.errors.head.code shouldBe "009-0013"
      }
    }
  }
}
