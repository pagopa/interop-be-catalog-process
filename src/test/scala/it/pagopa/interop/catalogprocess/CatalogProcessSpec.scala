package it.pagopa.interop.catalogprocess

import akka.http.scaladsl.model.{HttpMethods, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.nimbusds.jwt.JWTClaimsSet
import it.pagopa.interop.agreementmanagement.client.model.{Agreement, AgreementState}
import it.pagopa.interop.attributeregistrymanagement.client.model.{
  Attribute => AttributeRegistryManagementApiAttribute,
  AttributeKind => AttributeRegistryManagementApiAttributeKind
}
import cats.syntax.all._
import it.pagopa.interop.authorizationmanagement.client.{model => AuthorizationManagementDependency}
import it.pagopa.interop.catalogmanagement.client.model.AgreementApprovalPolicy.AUTOMATIC
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagementDependency}
import it.pagopa.interop.catalogprocess.api.impl.Converter.convertToApiTechnology
import it.pagopa.interop.catalogprocess.api.impl._
import it.pagopa.interop.catalogprocess.api.{HealthApi, ProcessApi}
import it.pagopa.interop.catalogprocess.model.EServiceDescriptorState._
import it.pagopa.interop.catalogprocess.model._
import it.pagopa.interop.catalogprocess.server.Controller
import it.pagopa.interop.catalogprocess.service._
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.utils.SprayCommonFormats.uuidFormat
import it.pagopa.interop.selfcare.partymanagement.client.model.{Attribute => PartyManagementApiAttribute}
import it.pagopa.interop.selfcare.partymanagement.client.{model => PartyManagementDependency}
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json._

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Success
import it.pagopa.interop.tenantmanagement.client.model.Tenant
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier

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
          tenantManagementService = tenantManagementService,
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
          description = "",
          agreement = None,
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
          description = "",
          agreement = None,
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
          description = "",
          agreement = None,
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
      val selfcareId1      = UUID.randomUUID().toString()

      val activeAgreement = Agreement(
        id = UUID.randomUUID(),
        eserviceId = eserviceId1,
        descriptorId = descriptorId1,
        producerId = UUID.randomUUID(),
        consumerId = consumerId,
        state = AgreementState.ACTIVE,
        verifiedAttributes = List.empty,
        certifiedAttributes = List.empty,
        declaredAttributes = List.empty,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        suspendedByPlatform = None,
        consumerDocuments = List.empty,
        createdAt = OffsetDateTime.now(),
        updatedAt = None,
        consumerNotes = None
      )

      (agreementManagementService
        .getAgreements(_: Option[String], _: Option[String], _: List[AgreementState], _: Option[String])(
          _: Seq[(String, String)]
        ))
        .expects(Some(consumerId.toString), None, List(AgreementState.ACTIVE), None, *)
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
        .getEService(_: String)(_: Seq[(String, String)]))
        .expects(eserviceId1.toString, *)
        .returning(Future.successful(eservice1))
        .once()

      val tenant = Tenant(
        id = producerId1,
        selfcareId = selfcareId1.some,
        externalId = null,
        features = Nil,
        attributes = Nil,
        createdAt = OffsetDateTimeSupplier.get(),
        updatedAt = None
      )

      val org1 =
        PartyManagementDependency.Institution(
          UUID.fromString(selfcareId1),
          "",
          "",
          "description1",
          "",
          "",
          "",
          "",
          "",
          institutionType = "",
          Seq.empty
        )

      (tenantManagementService
        .getTenant(_: UUID)(_: Seq[(String, String)]))
        .expects(producerId1, *)
        .once()
        .returning(Future.successful(tenant))

      (partyManagementService
        .getInstitution(_: String)(_: Seq[(String, String)], _: ExecutionContext))
        .expects(selfcareId1, *, *)
        .returning(Future.successful(org1))
        .once()

      (attributeRegistryManagementService
        .getAttributesBulk(_: Seq[UUID])(_: Seq[(String, String)]))
        .expects(Seq.empty, *)
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

      val attributeId1: UUID = UUID.randomUUID()
      val attributeId2: UUID = UUID.randomUUID()
      val attributeId3: UUID = UUID.randomUUID()

      val seed = CatalogManagementDependency.EServiceSeed(
        producerId = UUID.randomUUID(),
        name = "MyService",
        description = "My Service",
        technology = CatalogManagementDependency.EServiceTechnology.REST,
        attributes = CatalogManagementDependency.Attributes(
          certified = List(
            CatalogManagementDependency
              .Attribute(
                single =
                  Some(CatalogManagementDependency.AttributeValue(attributeId1, explicitAttributeVerification = false)),
                group = None
              )
          ),
          declared = List(
            CatalogManagementDependency
              .Attribute(
                single = None,
                group = Some(
                  List(CatalogManagementDependency.AttributeValue(attributeId2, explicitAttributeVerification = false))
                )
              )
          ),
          verified = List(
            CatalogManagementDependency
              .Attribute(
                single =
                  Some(CatalogManagementDependency.AttributeValue(attributeId3, explicitAttributeVerification = true)),
                group = None
              )
          )
        )
      )

      val eservice = CatalogManagementDependency.EService(
        id = UUID.randomUUID(),
        producerId = seed.producerId,
        name = seed.name,
        description = seed.description,
        technology = seed.technology,
        attributes = seed.attributes,
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
            agreementApprovalPolicy = AUTOMATIC
          )
        )
      )

      val tenant = Tenant(
        id = seed.producerId,
        selfcareId = UUID.randomUUID.toString().some,
        externalId = null,
        features = Nil,
        attributes = Nil,
        createdAt = OffsetDateTimeSupplier.get(),
        updatedAt = None
      )

      val institution = PartyManagementDependency.Institution(
        id = seed.producerId,
        externalId = "institutionId",
        originId = "",
        description = "organization description",
        digitalAddress = "digitalAddress",
        address = "address",
        zipCode = "zipCode",
        taxCode = "code",
        origin = "",
        institutionType = "",
        attributes = Seq.empty[PartyManagementApiAttribute]
      )

      val attribute1 = AttributeRegistryManagementApiAttribute(
        id = attributeId1,
        code = None,
        kind = AttributeRegistryManagementApiAttributeKind.CERTIFIED,
        description = s"$attributeId1-description",
        origin = None,
        name = s"$attributeId1-name",
        creationTime = OffsetDateTime.now()
      )
      val attribute2 = AttributeRegistryManagementApiAttribute(
        id = attributeId2,
        code = None,
        kind = AttributeRegistryManagementApiAttributeKind.CERTIFIED,
        description = s"$attributeId2-description",
        origin = None,
        name = s"$attributeId2-name",
        creationTime = OffsetDateTime.now()
      )
      val attribute3 = AttributeRegistryManagementApiAttribute(
        id = attributeId3,
        code = None,
        kind = AttributeRegistryManagementApiAttributeKind.CERTIFIED,
        description = s"$attributeId3-description",
        origin = None,
        name = s"$attributeId3-name",
        creationTime = OffsetDateTime.now()
      )

      (catalogManagementService
        .createEService(_: CatalogManagementDependency.EServiceSeed)(_: Seq[(String, String)]))
        .expects(seed, *)
        .returning(Future.successful(eservice))
        .once()

      (tenantManagementService
        .getTenant(_: UUID)(_: Seq[(String, String)]))
        .expects(seed.producerId, *)
        .once()
        .returning(Future.successful(tenant))

      (partyManagementService
        .getInstitution(_: String)(_: Seq[(String, String)], _: ExecutionContext))
        .expects(tenant.selfcareId.get, *, *)
        .returning(Future.successful(institution))
        .once()

      (attributeRegistryManagementService
        .getAttributesBulk(_: Seq[UUID])(_: Seq[(String, String)]))
        .expects(Seq(attributeId1, attributeId2, attributeId3), *)
        .returning(Future.successful(Seq(attribute1, attribute2, attribute3)))
        .once()

      implicit val seedFormat: JsonFormat[CatalogManagementDependency.EServiceTechnology] =
        new JsonFormat[CatalogManagementDependency.EServiceTechnology] {
          override def write(obj: CatalogManagementDependency.EServiceTechnology): JsValue =
            obj match {
              case CatalogManagementDependency.EServiceTechnology.REST => JsString("REST")
              case CatalogManagementDependency.EServiceTechnology.SOAP => JsString("SOAP")
            }

          override def read(json: JsValue): CatalogManagementDependency.EServiceTechnology =
            json match {
              case JsString("REST") => CatalogManagementDependency.EServiceTechnology.REST
              case JsString("SOAP") => CatalogManagementDependency.EServiceTechnology.SOAP
              case unrecognized     =>
                deserializationError(s"EServiceTechnology serialization error ${unrecognized.toString}")
            }
        }

      implicit val attributeValueFormat: RootJsonFormat[CatalogManagementDependency.AttributeValue] =
        jsonFormat2(CatalogManagementDependency.AttributeValue)

      implicit val attributeFormat: RootJsonFormat[CatalogManagementDependency.Attribute] =
        jsonFormat2(CatalogManagementDependency.Attribute)

      implicit val attributesFormat: RootJsonFormat[CatalogManagementDependency.Attributes] =
        jsonFormat3(CatalogManagementDependency.Attributes)

      implicit val eServiceSeedFormat: RootJsonFormat[CatalogManagementDependency.EServiceSeed] =
        jsonFormat5(CatalogManagementDependency.EServiceSeed)

      val requestData = seed.toJson.toString

      val response = request("eservices", HttpMethods.POST, Some(requestData))

      val expected = EService(
        id = eservice.id,
        producer = Organization(id = institution.id, name = institution.description),
        name = seed.name,
        description = seed.description,
        technology = convertToApiTechnology(seed.technology),
        attributes = Attributes(
          certified = Seq(
            Attribute(
              single = Some(
                AttributeValue(
                  id = attributeId1,
                  name = s"$attributeId1-name",
                  description = s"$attributeId1-description",
                  explicitAttributeVerification = false
                )
              ),
              group = None
            )
          ),
          declared = Seq(
            Attribute(
              single = None,
              group = Some(
                Seq(
                  AttributeValue(
                    id = attributeId2,
                    name = s"$attributeId2-name",
                    description = s"$attributeId2-description",
                    explicitAttributeVerification = false
                  )
                )
              )
            )
          ),
          verified = Seq(
            Attribute(
              single = Some(
                AttributeValue(
                  id = attributeId3,
                  name = s"$attributeId3-name",
                  description = s"$attributeId3-description",
                  explicitAttributeVerification = true
                )
              ),
              group = None
            )
          )
        ),
        descriptors = eservice.descriptors.map(Converter.convertToApiDescriptor)
      )

      response.status shouldBe StatusCodes.OK

      val body = Await.result(Unmarshal(response.entity).to[EService], Duration.Inf)

      body shouldBe expected

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
        .getEService(_: String)(_: Seq[(String, String)]))
        .expects(eServiceId, *)
        .returning(Future.successful(eService))
        .once()

      (catalogManagementService
        .suspendDescriptor(_: String, _: String)(_: Seq[(String, String)]))
        .expects(eServiceId, descriptorId, *)
        .returning(Future.successful(()))
        .once()

      (
        authorizationManagementService
          .updateStateOnClients(
            _: UUID,
            _: UUID,
            _: AuthorizationManagementDependency.ClientComponentState,
            _: Seq[String],
            _: Int
          )(_: Seq[(String, String)])
        )
        .expects(
          eServiceUuid,
          descriptor.id,
          AuthorizationManagementDependency.ClientComponentState.INACTIVE,
          descriptor.audience,
          descriptor.voucherLifespan,
          *
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
        .getEService(_: String)(_: Seq[(String, String)]))
        .expects(eServiceId, *)
        .returning(Future.successful(eService))
        .once()

      (catalogManagementService
        .suspendDescriptor(_: String, _: String)(_: Seq[(String, String)]))
        .expects(eServiceId, descriptorId, *)
        .returning(Future.successful(()))
        .once()

      (
        authorizationManagementService
          .updateStateOnClients(
            _: UUID,
            _: UUID,
            _: AuthorizationManagementDependency.ClientComponentState,
            _: Seq[String],
            _: Int
          )(_: Seq[(String, String)])
        )
        .expects(
          eServiceUuid,
          descriptor.id,
          AuthorizationManagementDependency.ClientComponentState.INACTIVE,
          descriptor.audience,
          descriptor.voucherLifespan,
          *
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
        .getEService(_: String)(_: Seq[(String, String)]))
        .expects(eServiceId, *)
        .returning(Future.successful(eService))
        .once()

      (catalogManagementService
        .suspendDescriptor(_: String, _: String)(_: Seq[(String, String)]))
        .expects(eServiceId, descriptorId, *)
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
        .getEService(_: String)(_: Seq[(String, String)]))
        .expects(eServiceId, *)
        .returning(Future.successful(eService))
        .once()

      (catalogManagementService
        .suspendDescriptor(_: String, _: String)(_: Seq[(String, String)]))
        .expects(eServiceId, descriptorId, *)
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
        .getEService(_: String)(_: Seq[(String, String)]))
        .expects(eServiceId, *)
        .returning(Future.successful(eService))
        .once()

      (catalogManagementService
        .deprecateDescriptor(_: String, _: String)(_: Seq[(String, String)]))
        .expects(eServiceId, descriptorId, *)
        .returning(Future.successful(()))
        .once()

      (
        authorizationManagementService
          .updateStateOnClients(
            _: UUID,
            _: UUID,
            _: AuthorizationManagementDependency.ClientComponentState,
            _: Seq[String],
            _: Int
          )(_: Seq[(String, String)])
        )
        .expects(
          eServiceUuid,
          descriptor.id,
          AuthorizationManagementDependency.ClientComponentState.ACTIVE,
          descriptor.audience,
          descriptor.voucherLifespan,
          *
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
        .getEService(_: String)(_: Seq[(String, String)]))
        .expects(eServiceId, *)
        .returning(Future.successful(eService))
        .once()

      (catalogManagementService
        .deprecateDescriptor(_: String, _: String)(_: Seq[(String, String)]))
        .expects(eServiceId, descriptorId, *)
        .returning(Future.successful(()))
        .once()

      (
        authorizationManagementService
          .updateStateOnClients(
            _: UUID,
            _: UUID,
            _: AuthorizationManagementDependency.ClientComponentState,
            _: Seq[String],
            _: Int
          )(_: Seq[(String, String)])
        )
        .expects(
          eServiceUuid,
          descriptor.id,
          AuthorizationManagementDependency.ClientComponentState.ACTIVE,
          descriptor.audience,
          descriptor.voucherLifespan,
          *
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
        .getEService(_: String)(_: Seq[(String, String)]))
        .expects(eServiceId, *)
        .returning(Future.successful(eService))
        .once()

      (catalogManagementService
        .publishDescriptor(_: String, _: String)(_: Seq[(String, String)]))
        .expects(eServiceId, descriptorId, *)
        .returning(Future.successful(()))
        .once()

      (
        authorizationManagementService
          .updateStateOnClients(
            _: UUID,
            _: UUID,
            _: AuthorizationManagementDependency.ClientComponentState,
            _: Seq[String],
            _: Int
          )(_: Seq[(String, String)])
        )
        .expects(
          eServiceUuid,
          descriptor.id,
          AuthorizationManagementDependency.ClientComponentState.ACTIVE,
          descriptor.audience,
          descriptor.voucherLifespan,
          *
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
        .getEService(_: String)(_: Seq[(String, String)]))
        .expects(eServiceId, *)
        .returning(Future.successful(eService))
        .once()

      (catalogManagementService
        .publishDescriptor(_: String, _: String)(_: Seq[(String, String)]))
        .expects(eServiceId, descriptorId, *)
        .returning(Future.successful(()))
        .once()

      (
        authorizationManagementService
          .updateStateOnClients(
            _: UUID,
            _: UUID,
            _: AuthorizationManagementDependency.ClientComponentState,
            _: Seq[String],
            _: Int
          )(_: Seq[(String, String)])
        )
        .expects(
          eServiceUuid,
          descriptor.id,
          AuthorizationManagementDependency.ClientComponentState.ACTIVE,
          descriptor.audience,
          descriptor.voucherLifespan,
          *
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
        .getEService(_: String)(_: Seq[(String, String)]))
        .expects(eServiceId, *)
        .returning(Future.successful(eService))
        .once()

      (catalogManagementService
        .suspendDescriptor(_: String, _: String)(_: Seq[(String, String)]))
        .expects(eServiceId, descriptorId, *)
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
        .getEService(_: String)(_: Seq[(String, String)]))
        .expects(eServiceId, *)
        .returning(Future.successful(eService))
        .once()

      (catalogManagementService
        .suspendDescriptor(_: String, _: String)(_: Seq[(String, String)]))
        .expects(eServiceId, descriptorId, *)
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
        .getEService(_: String)(_: Seq[(String, String)]))
        .expects(eServiceId, *)
        .returning(Future.successful(eService))
        .once()

      (catalogManagementService
        .suspendDescriptor(_: String, _: String)(_: Seq[(String, String)]))
        .expects(eServiceId, descriptorId, *)
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
        .getEService(_: String)(_: Seq[(String, String)]))
        .expects(eServiceId, *)
        .returning(Future.successful(eService))
        .once()

      (catalogManagementService
        .suspendDescriptor(_: String, _: String)(_: Seq[(String, String)]))
        .expects(eServiceId, descriptorId, *)
        .returning(Future.successful(()))
        .once()

      val response = request(s"eservices/$eServiceId/descriptors/$descriptorId/activate", HttpMethods.POST)

      response.status shouldBe StatusCodes.BadRequest
    }
  }

  "Descriptor deletion" must {

    "keep EService if other Descriptors exist" in {

      val draftDescriptor = CatalogManagementDependency.EServiceDescriptor(
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
        agreementApprovalPolicy = AUTOMATIC
      )

      val otherDescriptor = CatalogManagementDependency.EServiceDescriptor(
        id = UUID.randomUUID(),
        version = "1",
        description = None,
        interface = None,
        docs = Nil,
        state = CatalogManagementDependency.EServiceDescriptorState.PUBLISHED,
        audience = List("aud1"),
        voucherLifespan = 1000,
        dailyCallsPerConsumer = 1000,
        dailyCallsTotal = 0,
        agreementApprovalPolicy = AUTOMATIC
      )

      val eservice = CatalogManagementDependency.EService(
        id = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        name = "Name",
        description = "Description",
        technology = CatalogManagementDependency.EServiceTechnology.REST,
        attributes = CatalogManagementDependency.Attributes(certified = Nil, verified = Nil, declared = Nil),
        descriptors = List(draftDescriptor, otherDescriptor)
      )

      (catalogManagementService
        .getEService(_: String)(_: Seq[(String, String)]))
        .expects(eservice.id.toString, *)
        .returning(Future.successful(eservice))
        .once()

      (catalogManagementService
        .deleteDraft(_: String, _: String)(_: Seq[(String, String)]))
        .expects(eservice.id.toString, draftDescriptor.id.toString, *)
        .returning(Future.successful(()))
        .once()

      val response = request(s"eservices/${eservice.id}/descriptors/${draftDescriptor.id}", HttpMethods.DELETE)

      response.status shouldBe StatusCodes.NoContent
    }

    "delete EService if there are no other Descriptors" in {

      val draftDescriptor = CatalogManagementDependency.EServiceDescriptor(
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
        agreementApprovalPolicy = AUTOMATIC
      )

      val eservice = CatalogManagementDependency.EService(
        id = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        name = "Name",
        description = "Description",
        technology = CatalogManagementDependency.EServiceTechnology.REST,
        attributes = CatalogManagementDependency.Attributes(certified = Nil, verified = Nil, declared = Nil),
        descriptors = List(draftDescriptor)
      )

      (catalogManagementService
        .getEService(_: String)(_: Seq[(String, String)]))
        .expects(eservice.id.toString, *)
        .returning(Future.successful(eservice))
        .once()

      (catalogManagementService
        .deleteDraft(_: String, _: String)(_: Seq[(String, String)]))
        .expects(eservice.id.toString, draftDescriptor.id.toString, *)
        .returning(Future.successful(()))
        .once()

      (catalogManagementService
        .deleteEService(_: String)(_: Seq[(String, String)]))
        .expects(eservice.id.toString, *)
        .returning(Future.successful(()))
        .once()

      val response = request(s"eservices/${eservice.id}/descriptors/${draftDescriptor.id}", HttpMethods.DELETE)

      response.status shouldBe StatusCodes.NoContent
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
  val tenantManagementService: TenantManagementService                       = mock[TenantManagementService]
  val fileManager: FileManager                                               = mock[FileManager]
  val jwtReader: JWTReader                                                   = mock[JWTReader]
  def mockSubject(uuid: String): Success[JWTClaimsSet] = Success(new JWTClaimsSet.Builder().subject(uuid).build())
}
