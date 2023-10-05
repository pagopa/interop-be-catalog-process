package it.pagopa.interop.catalogprocess

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import it.pagopa.interop.agreementmanagement.model.agreement.{Active, PersistentAgreementState}
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
  EServiceRiskAnalysisNotFound
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
    "succeed when found" in {
      val eServiceId  = UUID.randomUUID()
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eServiceId, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem))

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
      val offset          = 0
      val limit           = 50

      (mockCatalogManagementService
        .getEServices(
          _: Option[String],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[CatalogDescriptorState],
          _: Int,
          _: Int,
          _: Boolean
        )(_: ExecutionContext, _: ReadModelService))
        .expects(name, eServicesIds, producersIds, attributesIds, states, offset, limit, false, *, *)
        .once()
        .returns(Future.successful(PaginatedResult(results = Seq(SpecData.catalogItem), 1)))

      Get() ~> service.getEServices(
        name,
        eServicesIds.mkString(","),
        producersIds.mkString(","),
        attributesIds.mkString(","),
        states.mkString(","),
        agreementStates.mkString(","),
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
      val offset          = 0
      val limit           = 50

      (mockAgreementManagementService
        .getAgreements(_: Seq[UUID], _: Seq[UUID], _: Seq[UUID], _: Seq[PersistentAgreementState])(
          _: ExecutionContext,
          _: ReadModelService
        ))
        .expects(eServicesIds, Seq(requesterId), producersIds, Seq(Active), *, *)
        .once()
        .returns(Future.successful(Seq(SpecData.agreement)))

      (mockCatalogManagementService
        .getEServices(
          _: Option[String],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[CatalogDescriptorState],
          _: Int,
          _: Int,
          _: Boolean
        )(_: ExecutionContext, _: ReadModelService))
        .expects(
          name,
          Seq(SpecData.agreement.eserviceId),
          producersIds,
          attributesIds,
          states,
          offset,
          limit,
          false,
          *,
          *
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
      val offset          = 0
      val limit           = 50

      (mockAgreementManagementService
        .getAgreements(_: Seq[UUID], _: Seq[UUID], _: Seq[UUID], _: Seq[PersistentAgreementState])(
          _: ExecutionContext,
          _: ReadModelService
        ))
        .expects(eServicesIds, Seq(requesterId), producersIds, Seq(Active), *, *)
        .once()
        .returns(Future.successful(Seq.empty))

      Get() ~> service.getEServices(
        name,
        eServicesIds.mkString(","),
        producersIds.mkString(","),
        attributesIds.mkString(","),
        states.mkString(","),
        agreementStates.mkString(","),
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
          _: Option[String],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[CatalogDescriptorState],
          _: Int,
          _: Int,
          _: Boolean
        )(_: ExecutionContext, _: ReadModelService))
        .expects(Some(seed.name), Seq.empty, Seq(seed.producerId), Seq.empty, Seq.empty, 0, 1, true, *, *)
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
          _: Option[String],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[UUID],
          _: Seq[CatalogDescriptorState],
          _: Int,
          _: Int,
          _: Boolean
        )(_: ExecutionContext, _: ReadModelService))
        .expects(Some(apiSeed.name), Seq.empty, Seq(requesterId), Seq.empty, Seq.empty, 0, 1, true, *, *)
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
        .updateEServiceById(_: String, _: CatalogManagementDependency.UpdateEServiceSeed)(_: Seq[(String, String)]))
        .expects(eService.id.toString, updatedEServiceSeed, *)
        .returning(Future.successful(updatedEService))
        .once()

      Put() ~> service.updateEServiceById(eService.id.toString, eServiceSeed) ~> check {
        status shouldEqual StatusCodes.OK
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
    "succeed" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val eServiceDoc = SpecData.eServiceDoc

      val descriptor =
        SpecData.eServiceDescriptor.copy(
          state = CatalogManagementDependency.EServiceDescriptorState.PUBLISHED,
          docs = Seq(eServiceDoc)
        )

      val eService = SpecData.eService.copy(descriptors = Seq(descriptor), producerId = requesterId)

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(eService.id, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem.copy(id = eService.id, producerId = requesterId)))

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

      (mockCatalogManagementService
        .createDescriptor(_: String, _: CatalogManagementDependency.EServiceDescriptorSeed)(_: Seq[(String, String)]))
        .expects(SpecData.catalogItem.id.toString, eServiceDescriptorSeed, *)
        .returning(Future.successful(eServiceDescriptor))
        .once()

      Post() ~> service.createDescriptor(SpecData.catalogItem.id.toString, seed) ~> check {
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
        .updateDraftDescriptor(_: String, _: String, _: CatalogManagementDependency.UpdateEServiceDescriptorSeed)(
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
        status shouldEqual StatusCodes.InternalServerError
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

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
        .expects(SpecData.catalogItem.id, *, *)
        .once()
        .returns(Future.successful(SpecData.catalogItem.copy(producerId = UUID.randomUUID())))

      Delete() ~> service.deleteDraft(SpecData.catalogItem.id.toString, UUID.randomUUID.toString) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
  }
  "EService deletion" should {
    "succeed" in {

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

      val descriptor = SpecData.catalogDescriptor.copy(id = descriptorId)

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
  }
  "Document retrieve" should {
    "succeed" in {

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
        .returns(Future.successful(SpecData.catalogDocument.copy(id = documentId)))

      Post() ~> service.getEServiceDocumentById(
        eService.id.toString,
        descriptorId.toString,
        documentId.toString
      ) ~> check {
        status shouldEqual StatusCodes.OK
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
    "succeed" in {
      val requesterId  = UUID.randomUUID()
      val descriptorId = UUID.randomUUID()
      val documentId   = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor =
        SpecData.catalogDescriptor.copy(id = descriptorId, docs = Seq(SpecData.catalogDocument.copy(id = documentId)))

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
    "succeed" in {
      val requesterId  = UUID.randomUUID()
      val descriptorId = UUID.randomUUID()
      val documentId   = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor =
        SpecData.catalogDescriptor.copy(id = descriptorId, docs = Seq(SpecData.catalogDocument.copy(id = documentId)))

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
