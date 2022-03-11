package it.pagopa.interop.catalogprocess

import akka.http.scaladsl.model.{HttpMethods, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.nimbusds.jwt.JWTClaimsSet
import it.pagopa.interop.agreementmanagement.client.model.{Agreement, AgreementState}
import it.pagopa.interop.authorizationmanagement.client.{model => AuthorizationManagementDependency}
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagementDependency}
import it.pagopa.interop.catalogprocess.api.impl._
import it.pagopa.interop.catalogprocess.api.{HealthApi, ProcessApi}
import it.pagopa.interop.catalogprocess.model.EServiceDescriptorState._
import it.pagopa.interop.catalogprocess.model._
import it.pagopa.interop.catalogprocess.server.Controller
import it.pagopa.interop.catalogprocess.service._
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.partymanagement.client.{model => PartyManagementDependency}
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json._

import java.time.{Instant, OffsetDateTime}
import java.util.UUID
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Success

class CatalogProcessSpec extends SpecHelper with AnyWordSpecLike with BeforeAndAfterAll with MockFactory {

  import CatalogProcessSpec._

  var controller: Option[Controller] = None

  override def beforeAll(): Unit = {

    val processApi =
      new ProcessApi(
        ProcessApiServiceImpl(
          catalogManagementService = catalogManagementService,
          partyManagementService = partyManagementService,
          attributeRegistryManagementService = attributeRegistryManagementService,
          agreementManagementService = agreementManagementService,
          authorizationManagementService = authorizationManagementService,
          fileManager = fileManager,
          jwtReader = jwtReader
        ),
        ProcessApiMarshallerImpl,
        wrappingDirective
      )

    controller = Some(new Controller(mockHealthApi, processApi))

    controller.foreach(startServer)
  }

  override def afterAll(): Unit = {
    shutDownServer()
    super.afterAll()
  }

  "flatten e-service filter" must {
    "filter properly" in {
      val flattenServices = Seq(
        FlatEService(
          id = UUID.randomUUID(),
          producerId = UUID.randomUUID(),
          producerName = "test",
          name = "test",
          version = None,
          state = Some(PUBLISHED),
          descriptorId = Some("1d"),
          callerSubscribed = None,
          certifiedAttributes = Seq.empty
        ),
        FlatEService(
          id = UUID.randomUUID(),
          producerId = UUID.randomUUID(),
          producerName = "test2",
          name = "test2",
          version = None,
          state = Some(SUSPENDED),
          descriptorId = Some("2d"),
          callerSubscribed = None,
          certifiedAttributes = Seq.empty
        ),
        FlatEService(
          id = UUID.randomUUID(),
          producerId = UUID.randomUUID(),
          producerName = "test3",
          name = "test3",
          version = None,
          state = Some(SUSPENDED),
          descriptorId = Some("3d"),
          callerSubscribed = None,
          certifiedAttributes = Seq.empty
        )
      )

      val published = EServiceDescriptorState.fromValue("PUBLISHED").toOption
      flattenServices.filter(item => published.forall(item.state.contains)) should have size 1

      val draft = EServiceDescriptorState.fromValue("DRAFT").toOption
      flattenServices.filter(item => draft.forall(item.state.contains)) should have size 0

      val suspended = EServiceDescriptorState.fromValue("SUSPENDED").toOption
      flattenServices.filter(item => suspended.forall(item.state.contains)) should have size 2
    }
  }

  "Eservice listing" must {
    "return eservices related to an active agreement" in {

      val consumerId: UUID = UUID.randomUUID()
      val eserviceId1      = UUID.randomUUID()
      val descriptorId1    = UUID.randomUUID()
      val producerId1      = UUID.randomUUID()

      (jwtReader
        .getClaims(_: String))
        .expects(bearerToken)
        .returning(mockSubject(UUID.randomUUID().toString))
        .once()

      val activeAgreement = Agreement(
        UUID.randomUUID(),
        eserviceId1,
        descriptorId1,
        UUID.randomUUID(),
        consumerId,
        AgreementState.ACTIVE,
        Seq.empty,
        None,
        None,
        OffsetDateTime.now()
      )

      (agreementManagementService
        .getAgreements(_: String, _: Option[String], _: Option[String], _: Option[AgreementState]))
        .expects(bearerToken, Some(consumerId.toString), None, Some(AgreementState.ACTIVE))
        .returning(Future.successful(Seq(activeAgreement)))
        .once()

      val eservice1 = CatalogManagementDependency.EService(
        eserviceId1,
        producerId1,
        "eservice1",
        "eservice1",
        CatalogManagementDependency.EServiceTechnology.REST,
        CatalogManagementDependency.Attributes(Seq.empty, Seq.empty, Seq.empty),
        Seq.empty
      )

      (catalogManagementService
        .getEService(_: String)(_: String))
        .expects(bearerToken, eserviceId1.toString)
        .returning(Future.successful(eservice1))
        .once()

      val org1 =
        PartyManagementDependency.Organization(producerId1, "", "description1", "", "", "", "", Seq.empty)

      (partyManagementService
        .getOrganization(_: UUID)(_: String))
        .expects(producerId1, bearerToken)
        .returning(Future.successful(org1))
        .once()

      (attributeRegistryManagementService
        .getAttributesBulk(_: Seq[String])(_: String))
        .expects(Seq.empty, bearerToken)
        .returning(Future.successful(Seq.empty))
        .once()

      val response = request(s"eservices?consumerId=$consumerId&agreementStates=ACTIVE", HttpMethods.GET)

      response.status shouldBe StatusCodes.OK
      val body = Await.result(Unmarshal(response.entity).to[Seq[EService]], Duration.Inf)
      body shouldBe Seq(
        EService(
          eserviceId1,
          Organization(producerId1, "description1"),
          "eservice1",
          "eservice1",
          EServiceTechnology.REST,
          Attributes(Seq.empty, Seq.empty, Seq.empty),
          Seq.empty
        )
      )
    }
  }

  "EService creation" must {

    "succeed" in {

      val (attribute1, attribute2, attribute3)       = EserviceCreationTestData.getAttributes
      val (attributeId1, attributeId2, attributeId3) = (attribute1.id, attribute2.id, attribute3.id)

      val attributeValue1 = (attributeId1, false)
      val attributeValue2 = (attributeId2, false)
      val attributeValue3 = (attributeId3, true)
      val seed            = EserviceCreationTestData.getSeed(attributeValue1, attributeValue2, attributeValue3)
      val eserviceId      = UUID.randomUUID()
      val descriptorId    = UUID.randomUUID()
      val eservice        = EserviceCreationTestData.getEservice(eserviceId, descriptorId, seed)
      val organization    = EserviceCreationTestData.getOrganization(seed)

      (jwtReader
        .getClaims(_: String))
        .expects(bearerToken)
        .returning(mockSubject(UUID.randomUUID().toString))
        .once()

      (agreementManagementService
        .getAgreements(_: String, _: Option[String], _: Option[String], _: Option[AgreementState]))
        .expects(bearerToken, None, None, None)
        .returning(Future.successful(Seq.empty))
        .once()

      (catalogManagementService
        .createEService(_: String)(_: CatalogManagementDependency.EServiceSeed))
        .expects(bearerToken, seed)
        .returning(Future.successful(eservice))
        .once()

      (partyManagementService
        .getOrganization(_: UUID)(_: String))
        .expects(seed.producerId, bearerToken)
        .returning(Future.successful(organization))
        .once()

      (attributeRegistryManagementService
        .getAttributesBulk(_: Seq[String])(_: String))
        .expects(Seq(attributeId1, attributeId2, attributeId3), bearerToken)
        .returning(Future.successful(Seq(attribute1, attribute2, attribute3)))
        .once()

      val requestData = seed.toJson.toString

      val response = request("eservices", HttpMethods.POST, Some(requestData))

      val expected = EserviceCreationTestData.getEserviceExpected(
        eserviceId,
        descriptorId,
        seed,
        organization,
        attributeValue1,
        attributeValue2,
        attributeValue3
      )

      response.status shouldBe StatusCodes.OK

      val body = Await.result(Unmarshal(response.entity).to[EService], Duration.Inf)

      body shouldBe expected

    }

    "succeed for implicit attribute verification when attribute is already verified and valid" in {

      val (attribute1, attribute2, attribute3)       = EserviceCreationTestData.getAttributes
      val (attributeId1, attributeId2, attributeId3) = (attribute1.id, attribute2.id, attribute3.id)

      val attributeValue1 = (attributeId1, false)
      val attributeValue2 = (attributeId2, false)
      val attributeValue3 = (attributeId3, false)
      val seed            = EserviceCreationTestData.getSeed(attributeValue1, attributeValue2, attributeValue3)
      val eserviceId      = UUID.randomUUID()
      val descriptorId    = UUID.randomUUID()
      val eservice        = EserviceCreationTestData.getEservice(eserviceId, descriptorId, seed)
      val organization    = EserviceCreationTestData.getOrganization(seed)
      val agreement = EserviceCreationTestData.getAgreement(
        attributeId3,
        Some(true),
        Some(Instant.now().plusSeconds(60).getEpochSecond)
      )

      (jwtReader
        .getClaims(_: String))
        .expects(bearerToken)
        .returning(mockSubject(UUID.randomUUID().toString))
        .once()

      (agreementManagementService
        .getAgreements(_: String, _: Option[String], _: Option[String], _: Option[AgreementState]))
        .expects(bearerToken, None, None, None)
        .returning(Future.successful(Seq(agreement)))
        .once()

      (catalogManagementService
        .createEService(_: String)(_: CatalogManagementDependency.EServiceSeed))
        .expects(bearerToken, seed)
        .returning(Future.successful(eservice))
        .once()

      (partyManagementService
        .getOrganization(_: UUID)(_: String))
        .expects(seed.producerId, bearerToken)
        .returning(Future.successful(organization))
        .once()

      (attributeRegistryManagementService
        .getAttributesBulk(_: Seq[String])(_: String))
        .expects(Seq(attributeId1, attributeId2, attributeId3), bearerToken)
        .returning(Future.successful(Seq(attribute1, attribute2, attribute3)))
        .once()

      val requestData = seed.toJson.toString

      val response = request("eservices", HttpMethods.POST, Some(requestData))
      val expected = EserviceCreationTestData.getEserviceExpected(
        eserviceId,
        descriptorId,
        seed,
        organization,
        attributeValue1,
        attributeValue2,
        attributeValue3
      )

      response.status shouldBe StatusCodes.OK

      val body = Await.result(Unmarshal(response.entity).to[EService], Duration.Inf)

      body shouldBe expected

    }

    "fail for implicit attribute verification when attribute is not already verified" in {

      val (attribute1, attribute2, attribute3)       = EserviceCreationTestData.getAttributes
      val (attributeId1, attributeId2, attributeId3) = (attribute1.id, attribute2.id, attribute3.id)

      val seed         = EserviceCreationTestData.getSeed((attributeId1, false), (attributeId2, false), (attributeId3, false))
      val eserviceId   = UUID.randomUUID()
      val descriptorId = UUID.randomUUID()
      val eservice     = EserviceCreationTestData.getEservice(eserviceId, descriptorId, seed)
      val organization = EserviceCreationTestData.getOrganization(seed)

      val agreement = EserviceCreationTestData.getAgreement(attributeId3, None, None)

      (jwtReader
        .getClaims(_: String))
        .expects(bearerToken)
        .returning(mockSubject(UUID.randomUUID().toString))
        .once()

      (agreementManagementService
        .getAgreements(_: String, _: Option[String], _: Option[String], _: Option[AgreementState]))
        .expects(bearerToken, None, None, None)
        .returning(Future.successful(Seq(agreement)))
        .once()

      (catalogManagementService
        .createEService(_: String)(_: CatalogManagementDependency.EServiceSeed))
        .expects(bearerToken, seed)
        .returning(Future.successful(eservice))
        .once()

      (partyManagementService
        .getOrganization(_: UUID)(_: String))
        .expects(seed.producerId, bearerToken)
        .returning(Future.successful(organization))
        .once()

      (attributeRegistryManagementService
        .getAttributesBulk(_: Seq[String])(_: String))
        .expects(Seq(attributeId1, attributeId2, attributeId3), bearerToken)
        .returning(Future.successful(Seq(attribute1, attribute2, attribute3)))
        .once()

      val requestData = seed.toJson.toString

      val response = request("eservices", HttpMethods.POST, Some(requestData))

      response.status shouldBe StatusCodes.BadRequest

    }

    "fail for implicit attribute verification when a verified attribute is set false" in {

      val (attribute1, attribute2, attribute3)       = EserviceCreationTestData.getAttributes
      val (attributeId1, attributeId2, attributeId3) = (attribute1.id, attribute2.id, attribute3.id)

      val seed         = EserviceCreationTestData.getSeed((attributeId1, false), (attributeId2, false), (attributeId3, false))
      val eserviceId   = UUID.randomUUID()
      val descriptorId = UUID.randomUUID()
      val eservice     = EserviceCreationTestData.getEservice(eserviceId, descriptorId, seed)
      val organization = EserviceCreationTestData.getOrganization(seed)
      val agreement    = EserviceCreationTestData.getAgreement(attributeId3, Some(false), None)

      (jwtReader
        .getClaims(_: String))
        .expects(bearerToken)
        .returning(mockSubject(UUID.randomUUID().toString))
        .once()

      (agreementManagementService
        .getAgreements(_: String, _: Option[String], _: Option[String], _: Option[AgreementState]))
        .expects(bearerToken, None, None, None)
        .returning(Future.successful(Seq(agreement)))
        .once()

      (catalogManagementService
        .createEService(_: String)(_: CatalogManagementDependency.EServiceSeed))
        .expects(bearerToken, seed)
        .returning(Future.successful(eservice))
        .once()

      (partyManagementService
        .getOrganization(_: UUID)(_: String))
        .expects(seed.producerId, bearerToken)
        .returning(Future.successful(organization))
        .once()

      (attributeRegistryManagementService
        .getAttributesBulk(_: Seq[String])(_: String))
        .expects(Seq(attributeId1, attributeId2, attributeId3), bearerToken)
        .returning(Future.successful(Seq(attribute1, attribute2, attribute3)))
        .once()

      val requestData = seed.toJson.toString

      val response = request("eservices", HttpMethods.POST, Some(requestData))

      response.status shouldBe StatusCodes.BadRequest

    }

    "fail for implicit attribute verification when verified attribute is no more valid" in {

      val (attribute1, attribute2, attribute3)       = EserviceCreationTestData.getAttributes
      val (attributeId1, attributeId2, attributeId3) = (attribute1.id, attribute2.id, attribute3.id)

      val seed         = EserviceCreationTestData.getSeed((attributeId1, false), (attributeId2, false), (attributeId3, false))
      val eserviceId   = UUID.randomUUID()
      val descriptorId = UUID.randomUUID()
      val eservice     = EserviceCreationTestData.getEservice(eserviceId, descriptorId, seed)
      val organization = EserviceCreationTestData.getOrganization(seed)

      val agreement = EserviceCreationTestData.getAgreement(
        attributeId3,
        Some(true),
        Some(Instant.now().minusSeconds(60).getEpochSecond)
      )

      (jwtReader
        .getClaims(_: String))
        .expects(bearerToken)
        .returning(mockSubject(UUID.randomUUID().toString))
        .once()

      (agreementManagementService
        .getAgreements(_: String, _: Option[String], _: Option[String], _: Option[AgreementState]))
        .expects(bearerToken, None, None, None)
        .returning(Future.successful(Seq(agreement)))
        .once()

      (catalogManagementService
        .createEService(_: String)(_: CatalogManagementDependency.EServiceSeed))
        .expects(bearerToken, seed)
        .returning(Future.successful(eservice))
        .once()

      (partyManagementService
        .getOrganization(_: UUID)(_: String))
        .expects(seed.producerId, bearerToken)
        .returning(Future.successful(organization))
        .once()

      (attributeRegistryManagementService
        .getAttributesBulk(_: Seq[String])(_: String))
        .expects(Seq(attributeId1, attributeId2, attributeId3), bearerToken)
        .returning(Future.successful(Seq(attribute1, attribute2, attribute3)))
        .once()

      val requestData = seed.toJson.toString

      val response = request("eservices", HttpMethods.POST, Some(requestData))

      response.status shouldBe StatusCodes.BadRequest

    }
  }

  "Descriptor suspension" must {
    "succeed if descriptor is Published" in {
      val descriptor   = descriptorStub.copy(state = CatalogManagementDependency.EServiceDescriptorState.PUBLISHED)
      val eService     = eServiceStub.copy(descriptors = Seq(descriptor))
      val eServiceUuid = eService.id
      val eServiceId   = eServiceUuid.toString
      val descriptorId = descriptor.id.toString

      (jwtReader
        .getClaims(_: String))
        .expects(bearerToken)
        .returning(mockSubject(UUID.randomUUID().toString))
        .once()

      (catalogManagementService
        .getEService(_: String)(_: String))
        .expects(bearerToken, eServiceId)
        .returning(Future.successful(eService))
        .once()

      (catalogManagementService
        .suspendDescriptor(_: String)(_: String, _: String))
        .expects(bearerToken, eServiceId, descriptorId)
        .returning(Future.successful(()))
        .once()

      (authorizationManagementService
        .updateStateOnClients(_: String)(
          _: UUID,
          _: AuthorizationManagementDependency.ClientComponentState,
          _: Seq[String],
          _: Int
        ))
        .expects(
          bearerToken,
          eServiceUuid,
          AuthorizationManagementDependency.ClientComponentState.INACTIVE,
          descriptor.audience,
          descriptor.voucherLifespan
        )
        .returning(Future.successful(()))
        .once()

      val response = request(s"eservices/$eServiceId/descriptors/$descriptorId/suspend", HttpMethods.POST)

      response.status shouldBe StatusCodes.NoContent
    }

    "succeed if descriptor is Deprecated" in {
      val descriptor   = descriptorStub.copy(state = CatalogManagementDependency.EServiceDescriptorState.DEPRECATED)
      val eService     = eServiceStub.copy(descriptors = Seq(descriptor))
      val eServiceUuid = eService.id
      val eServiceId   = eServiceUuid.toString
      val descriptorId = descriptor.id.toString

      (jwtReader
        .getClaims(_: String))
        .expects(bearerToken)
        .returning(mockSubject(UUID.randomUUID().toString))
        .once()

      (catalogManagementService
        .getEService(_: String)(_: String))
        .expects(bearerToken, eServiceId)
        .returning(Future.successful(eService))
        .once()

      (catalogManagementService
        .suspendDescriptor(_: String)(_: String, _: String))
        .expects(bearerToken, eServiceId, descriptorId)
        .returning(Future.successful(()))
        .once()

      (authorizationManagementService
        .updateStateOnClients(_: String)(
          _: UUID,
          _: AuthorizationManagementDependency.ClientComponentState,
          _: Seq[String],
          _: Int
        ))
        .expects(
          bearerToken,
          eServiceUuid,
          AuthorizationManagementDependency.ClientComponentState.INACTIVE,
          descriptor.audience,
          descriptor.voucherLifespan
        )
        .returning(Future.successful(()))
        .once()

      val response = request(s"eservices/$eServiceId/descriptors/$descriptorId/suspend", HttpMethods.POST)

      response.status shouldBe StatusCodes.NoContent
    }

    "fail if descriptor is Draft" in {
      val descriptor   = descriptorStub.copy(state = CatalogManagementDependency.EServiceDescriptorState.DRAFT)
      val eService     = eServiceStub.copy(descriptors = Seq(descriptor))
      val eServiceId   = eService.id.toString
      val descriptorId = descriptor.id.toString

      (jwtReader
        .getClaims(_: String))
        .expects(bearerToken)
        .returning(mockSubject(UUID.randomUUID().toString))
        .once()

      (catalogManagementService
        .getEService(_: String)(_: String))
        .expects(bearerToken, eServiceId)
        .returning(Future.successful(eService))
        .once()

      (catalogManagementService
        .suspendDescriptor(_: String)(_: String, _: String))
        .expects(bearerToken, eServiceId, descriptorId)
        .returning(Future.successful(()))
        .once()

      val response = request(s"eservices/$eServiceId/descriptors/$descriptorId/suspend", HttpMethods.POST)

      response.status shouldBe StatusCodes.BadRequest
    }

    "fail if descriptor is Archived" in {
      val descriptor   = descriptorStub.copy(state = CatalogManagementDependency.EServiceDescriptorState.ARCHIVED)
      val eService     = eServiceStub.copy(descriptors = Seq(descriptor))
      val eServiceId   = eService.id.toString
      val descriptorId = descriptor.id.toString

      (jwtReader
        .getClaims(_: String))
        .expects(bearerToken)
        .returning(mockSubject(UUID.randomUUID().toString))
        .once()

      (catalogManagementService
        .getEService(_: String)(_: String))
        .expects(bearerToken, eServiceId)
        .returning(Future.successful(eService))
        .once()

      (catalogManagementService
        .suspendDescriptor(_: String)(_: String, _: String))
        .expects(bearerToken, eServiceId, descriptorId)
        .returning(Future.successful(()))
        .once()

      val response = request(s"eservices/$eServiceId/descriptors/$descriptorId/suspend", HttpMethods.POST)

      response.status shouldBe StatusCodes.BadRequest
    }
  }

  "Descriptor activation" must {
    val eService1 = eServiceStub.copy(descriptors =
      Seq(
        descriptorStub.copy(version = "1", state = CatalogManagementDependency.EServiceDescriptorState.ARCHIVED),
        descriptorStub.copy(version = "2", state = CatalogManagementDependency.EServiceDescriptorState.DEPRECATED),
        descriptorStub.copy(version = "3", state = CatalogManagementDependency.EServiceDescriptorState.SUSPENDED),
        descriptorStub.copy(version = "4", state = CatalogManagementDependency.EServiceDescriptorState.SUSPENDED),
        descriptorStub.copy(version = "5", state = CatalogManagementDependency.EServiceDescriptorState.DRAFT)
      )
    )
    val eService2 = eServiceStub.copy(descriptors =
      Seq(
        descriptorStub.copy(version = "1", state = CatalogManagementDependency.EServiceDescriptorState.ARCHIVED),
        descriptorStub.copy(version = "2", state = CatalogManagementDependency.EServiceDescriptorState.DEPRECATED),
        descriptorStub.copy(version = "3", state = CatalogManagementDependency.EServiceDescriptorState.SUSPENDED),
        descriptorStub.copy(version = "4", state = CatalogManagementDependency.EServiceDescriptorState.PUBLISHED),
        descriptorStub.copy(version = "5", state = CatalogManagementDependency.EServiceDescriptorState.DRAFT)
      )
    )
    val eService3 = eServiceStub.copy(descriptors =
      Seq(
        descriptorStub.copy(version = "1", state = CatalogManagementDependency.EServiceDescriptorState.ARCHIVED),
        descriptorStub.copy(version = "2", state = CatalogManagementDependency.EServiceDescriptorState.SUSPENDED),
        descriptorStub.copy(version = "3", state = CatalogManagementDependency.EServiceDescriptorState.SUSPENDED)
      )
    )

    "activate Deprecated descriptor - case 1" in {
      val eService     = eService1
      val eServiceUuid = eService.id
      val eServiceId   = eServiceUuid.toString
      val descriptor   = eService.descriptors.find(_.version == "3").get
      val descriptorId = descriptor.id.toString

      (jwtReader
        .getClaims(_: String))
        .expects(bearerToken)
        .returning(mockSubject(UUID.randomUUID().toString))
        .once()

      (catalogManagementService
        .getEService(_: String)(_: String))
        .expects(bearerToken, eServiceId)
        .returning(Future.successful(eService))
        .once()

      (catalogManagementService
        .deprecateDescriptor(_: String)(_: String, _: String))
        .expects(bearerToken, eServiceId, descriptorId)
        .returning(Future.successful(()))
        .once()

      (authorizationManagementService
        .updateStateOnClients(_: String)(
          _: UUID,
          _: AuthorizationManagementDependency.ClientComponentState,
          _: Seq[String],
          _: Int
        ))
        .expects(
          bearerToken,
          eServiceUuid,
          AuthorizationManagementDependency.ClientComponentState.ACTIVE,
          descriptor.audience,
          descriptor.voucherLifespan
        )
        .returning(Future.successful(()))
        .once()

      val response = request(s"eservices/$eServiceId/descriptors/$descriptorId/activate", HttpMethods.POST)

      response.status shouldBe StatusCodes.NoContent
    }

    "activate Deprecated descriptor - case 2" in {
      val eService     = eService2
      val eServiceUuid = eService.id
      val eServiceId   = eServiceUuid.toString
      val descriptor   = eService.descriptors.find(_.version == "3").get
      val descriptorId = descriptor.id.toString

      (jwtReader
        .getClaims(_: String))
        .expects(bearerToken)
        .returning(mockSubject(UUID.randomUUID().toString))
        .once()

      (catalogManagementService
        .getEService(_: String)(_: String))
        .expects(bearerToken, eServiceId)
        .returning(Future.successful(eService))
        .once()

      (catalogManagementService
        .deprecateDescriptor(_: String)(_: String, _: String))
        .expects(bearerToken, eServiceId, descriptorId)
        .returning(Future.successful(()))
        .once()

      (authorizationManagementService
        .updateStateOnClients(_: String)(
          _: UUID,
          _: AuthorizationManagementDependency.ClientComponentState,
          _: Seq[String],
          _: Int
        ))
        .expects(
          bearerToken,
          eServiceUuid,
          AuthorizationManagementDependency.ClientComponentState.ACTIVE,
          descriptor.audience,
          descriptor.voucherLifespan
        )
        .returning(Future.successful(()))
        .once()

      val response = request(s"eservices/$eServiceId/descriptors/$descriptorId/activate", HttpMethods.POST)

      response.status shouldBe StatusCodes.NoContent
    }

    "activate Published descriptor - case 1" in {
      val eService     = eService1
      val eServiceUuid = eService.id
      val eServiceId   = eServiceUuid.toString
      val descriptor   = eService.descriptors.find(_.version == "4").get
      val descriptorId = descriptor.id.toString

      (jwtReader
        .getClaims(_: String))
        .expects(bearerToken)
        .returning(mockSubject(UUID.randomUUID().toString))
        .once()

      (catalogManagementService
        .getEService(_: String)(_: String))
        .expects(bearerToken, eServiceId)
        .returning(Future.successful(eService))
        .once()

      (catalogManagementService
        .publishDescriptor(_: String)(_: String, _: String))
        .expects(bearerToken, eServiceId, descriptorId)
        .returning(Future.successful(()))
        .once()

      (authorizationManagementService
        .updateStateOnClients(_: String)(
          _: UUID,
          _: AuthorizationManagementDependency.ClientComponentState,
          _: Seq[String],
          _: Int
        ))
        .expects(
          bearerToken,
          eServiceUuid,
          AuthorizationManagementDependency.ClientComponentState.ACTIVE,
          descriptor.audience,
          descriptor.voucherLifespan
        )
        .returning(Future.successful(()))
        .once()

      val response = request(s"eservices/$eServiceId/descriptors/$descriptorId/activate", HttpMethods.POST)

      response.status shouldBe StatusCodes.NoContent
    }

    "activate Published descriptor - case 2" in {
      val eService     = eService3
      val eServiceUuid = eService.id
      val eServiceId   = eServiceUuid.toString
      val descriptor   = eService.descriptors.find(_.version == "3").get
      val descriptorId = descriptor.id.toString

      (jwtReader
        .getClaims(_: String))
        .expects(bearerToken)
        .returning(mockSubject(UUID.randomUUID().toString))
        .once()

      (catalogManagementService
        .getEService(_: String)(_: String))
        .expects(bearerToken, eServiceId)
        .returning(Future.successful(eService))
        .once()

      (catalogManagementService
        .publishDescriptor(_: String)(_: String, _: String))
        .expects(bearerToken, eServiceId, descriptorId)
        .returning(Future.successful(()))
        .once()

      (authorizationManagementService
        .updateStateOnClients(_: String)(
          _: UUID,
          _: AuthorizationManagementDependency.ClientComponentState,
          _: Seq[String],
          _: Int
        ))
        .expects(
          bearerToken,
          eServiceUuid,
          AuthorizationManagementDependency.ClientComponentState.ACTIVE,
          descriptor.audience,
          descriptor.voucherLifespan
        )
        .returning(Future.successful(()))
        .once()

      val response = request(s"eservices/$eServiceId/descriptors/$descriptorId/activate", HttpMethods.POST)

      response.status shouldBe StatusCodes.NoContent
    }

    "fail if descriptor is Draft" in {
      val descriptor   = descriptorStub.copy(state = CatalogManagementDependency.EServiceDescriptorState.DRAFT)
      val eService     = eServiceStub.copy(descriptors = Seq(descriptor))
      val eServiceId   = eService.id.toString
      val descriptorId = descriptor.id.toString

      (jwtReader
        .getClaims(_: String))
        .expects(bearerToken)
        .returning(mockSubject(UUID.randomUUID().toString))
        .once()

      (catalogManagementService
        .getEService(_: String)(_: String))
        .expects(bearerToken, eServiceId)
        .returning(Future.successful(eService))
        .once()

      (catalogManagementService
        .suspendDescriptor(_: String)(_: String, _: String))
        .expects(bearerToken, eServiceId, descriptorId)
        .returning(Future.successful(()))
        .once()

      val response = request(s"eservices/$eServiceId/descriptors/$descriptorId/activate", HttpMethods.POST)

      response.status shouldBe StatusCodes.BadRequest
    }

    "fail if descriptor is Deprecated" in {
      val descriptor   = descriptorStub.copy(state = CatalogManagementDependency.EServiceDescriptorState.DEPRECATED)
      val eService     = eServiceStub.copy(descriptors = Seq(descriptor))
      val eServiceId   = eService.id.toString
      val descriptorId = descriptor.id.toString

      (jwtReader
        .getClaims(_: String))
        .expects(bearerToken)
        .returning(mockSubject(UUID.randomUUID().toString))
        .once()

      (catalogManagementService
        .getEService(_: String)(_: String))
        .expects(bearerToken, eServiceId)
        .returning(Future.successful(eService))
        .once()

      (catalogManagementService
        .suspendDescriptor(_: String)(_: String, _: String))
        .expects(bearerToken, eServiceId, descriptorId)
        .returning(Future.successful(()))
        .once()

      val response = request(s"eservices/$eServiceId/descriptors/$descriptorId/activate", HttpMethods.POST)

      response.status shouldBe StatusCodes.BadRequest
    }

    "fail if descriptor is Published" in {
      val descriptor   = descriptorStub.copy(state = CatalogManagementDependency.EServiceDescriptorState.PUBLISHED)
      val eService     = eServiceStub.copy(descriptors = Seq(descriptor))
      val eServiceId   = eService.id.toString
      val descriptorId = descriptor.id.toString

      (jwtReader
        .getClaims(_: String))
        .expects(bearerToken)
        .returning(mockSubject(UUID.randomUUID().toString))
        .once()

      (catalogManagementService
        .getEService(_: String)(_: String))
        .expects(bearerToken, eServiceId)
        .returning(Future.successful(eService))
        .once()

      (catalogManagementService
        .suspendDescriptor(_: String)(_: String, _: String))
        .expects(bearerToken, eServiceId, descriptorId)
        .returning(Future.successful(()))
        .once()

      val response = request(s"eservices/$eServiceId/descriptors/$descriptorId/activate", HttpMethods.POST)

      response.status shouldBe StatusCodes.BadRequest
    }

    "fail if descriptor is Archived" in {
      val descriptor   = descriptorStub.copy(state = CatalogManagementDependency.EServiceDescriptorState.ARCHIVED)
      val eService     = eServiceStub.copy(descriptors = Seq(descriptor))
      val eServiceId   = eService.id.toString
      val descriptorId = descriptor.id.toString

      (jwtReader
        .getClaims(_: String))
        .expects(bearerToken)
        .returning(mockSubject(UUID.randomUUID().toString))
        .once()

      (catalogManagementService
        .getEService(_: String)(_: String))
        .expects(bearerToken, eServiceId)
        .returning(Future.successful(eService))
        .once()

      (catalogManagementService
        .suspendDescriptor(_: String)(_: String, _: String))
        .expects(bearerToken, eServiceId, descriptorId)
        .returning(Future.successful(()))
        .once()

      val response = request(s"eservices/$eServiceId/descriptors/$descriptorId/activate", HttpMethods.POST)

      response.status shouldBe StatusCodes.BadRequest
    }
  }

}

object CatalogProcessSpec extends MockFactory {
  val mockHealthApi: HealthApi                                               = mock[HealthApi]
  val catalogManagementService: CatalogManagementService                     = mock[CatalogManagementService]
  val agreementManagementService: AgreementManagementService                 = mock[AgreementManagementService]
  val authorizationManagementService: AuthorizationManagementService         = mock[AuthorizationManagementService]
  val attributeRegistryManagementService: AttributeRegistryManagementService = mock[AttributeRegistryManagementService]
  val partyManagementService: PartyManagementService                         = mock[PartyManagementService]
  val fileManager: FileManager                                               = mock[FileManager]
  val jwtReader: JWTReader                                                   = mock[JWTReader]
  def mockSubject(uuid: String): Success[JWTClaimsSet]                       = Success(new JWTClaimsSet.Builder().subject(uuid).build())
}
