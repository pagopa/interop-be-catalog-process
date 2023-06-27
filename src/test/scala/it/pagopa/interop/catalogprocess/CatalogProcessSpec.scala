package it.pagopa.interop.catalogprocess

import akka.http.scaladsl.model.{HttpMethods, HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.nimbusds.jwt.JWTClaimsSet
import it.pagopa.interop.authorizationmanagement.client.{model => AuthorizationManagementDependency}
import it.pagopa.interop.catalogmanagement.client.model.AgreementApprovalPolicy.AUTOMATIC
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagementDependency}
import it.pagopa.interop.catalogmanagement.model.CatalogItem
import it.pagopa.interop.catalogprocess.api.impl.Converter.convertToApiTechnology
import it.pagopa.interop.catalogprocess.api.impl._
import it.pagopa.interop.catalogprocess.api.{HealthApi, ProcessApi}
import it.pagopa.interop.catalogprocess.common.readmodel.TotalCountResult
import it.pagopa.interop.catalogprocess.model.EServiceDescriptorState._
import it.pagopa.interop.catalogprocess.model._
import it.pagopa.interop.catalogprocess.server.Controller
import it.pagopa.interop.catalogprocess.service._
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.jwt.service.JWTReader
import org.mongodb.scala.bson.conversions.Bson
import org.scalamock.scalatest.MockFactory
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{Assertion, BeforeAndAfterAll}
import spray.json._

import java.util.UUID
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Success

class CatalogProcessSpec extends SpecHelper with AnyWordSpecLike with BeforeAndAfterAll with MockFactory {

  import CatalogProcessSpec._

  var controller: Option[Controller] = None

  override def beforeAll(): Unit = {

    val processApi = new ProcessApi(
      ProcessApiServiceImpl(
        catalogManagementService = catalogManagementService,
        attributeRegistryManagementService = attributeRegistryManagementService,
        agreementManagementService = agreementManagementService,
        authorizationManagementService = authorizationManagementService,
        tenantManagementService = tenantManagementService,
        fileManager = fileManager,
        readModel = readModel,
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

  "EService creation" must {

    "succeed" in {

      val attributeId1: UUID = UUID.randomUUID()
      val attributeId2: UUID = UUID.randomUUID()
      val attributeId3: UUID = UUID.randomUUID()

      val catalogItems: Seq[CatalogItem] = Seq.empty

      val apiSeed: EServiceSeed =
        EServiceSeed(name = "MyService", description = "My Service", technology = EServiceTechnology.REST)

      val seed = CatalogManagementDependency.EServiceSeed(
        producerId = AdminMockAuthenticator.requesterId,
        name = "MyService",
        description = "My Service",
        technology = CatalogManagementDependency.EServiceTechnology.REST
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
              certified = List(
                CatalogManagementDependency.Attribute(
                  single = Some(
                    CatalogManagementDependency.AttributeValue(attributeId1, explicitAttributeVerification = false)
                  ),
                  group = None
                )
              ),
              declared = List(
                CatalogManagementDependency.Attribute(
                  single = None,
                  group = Some(
                    List(
                      CatalogManagementDependency.AttributeValue(attributeId2, explicitAttributeVerification = false)
                    )
                  )
                )
              ),
              verified = List(
                CatalogManagementDependency.Attribute(
                  single = Some(
                    CatalogManagementDependency.AttributeValue(attributeId3, explicitAttributeVerification = true)
                  ),
                  group = None
                )
              )
            )
          )
        )
      )

      // Data retrieve
      (readModel
        .aggregate(_: String, _: Seq[Bson], _: Int, _: Int)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, 0, 1, *, *)
        .once()
        .returns(Future.successful(catalogItems))

      // Total count
      (readModel
        .aggregate(_: String, _: Seq[Bson], _: Int, _: Int)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, 0, Int.MaxValue, *, *)
        .once()
        .returns(Future.successful(Seq(TotalCountResult(0))))

      (catalogManagementService
        .createEService(_: CatalogManagementDependency.EServiceSeed)(_: Seq[(String, String)]))
        .expects(seed, *)
        .returning(Future.successful(eservice))
        .once()

      val requestData = apiSeed.toJson.toString

      val response = request("eservices", HttpMethods.POST, Some(requestData))

      val expected = EService(
        id = eservice.id,
        producerId = eservice.producerId,
        name = seed.name,
        description = seed.description,
        technology = convertToApiTechnology(seed.technology),
        descriptors = eservice.descriptors.map(Converter.convertToApiDescriptor)
      )

      response.status shouldBe StatusCodes.OK

      val body = Await.result(Unmarshal(response.entity).to[EService], Duration.Inf)

      body shouldBe expected

    }

    "fail with conflict if an EService with the same name exists" in {

      val attributeId1: UUID = UUID.randomUUID()
      val attributeId2: UUID = UUID.randomUUID()
      val attributeId3: UUID = UUID.randomUUID()

      val catalogItems: Seq[CatalogItem] = Seq(SpecData.catalogItem)

      val apiSeed: EServiceSeed =
        EServiceSeed(name = "MyService", description = "My Service", technology = EServiceTechnology.REST)

      val seed = CatalogManagementDependency.EServiceSeed(
        producerId = AdminMockAuthenticator.requesterId,
        name = "MyService",
        description = "My Service",
        technology = CatalogManagementDependency.EServiceTechnology.REST
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
              certified = List(
                CatalogManagementDependency
                  .Attribute(
                    single = Some(
                      CatalogManagementDependency.AttributeValue(attributeId1, explicitAttributeVerification = false)
                    ),
                    group = None
                  )
              ),
              declared = List(
                CatalogManagementDependency
                  .Attribute(
                    single = None,
                    group = Some(
                      List(
                        CatalogManagementDependency.AttributeValue(attributeId2, explicitAttributeVerification = false)
                      )
                    )
                  )
              ),
              verified = List(
                CatalogManagementDependency
                  .Attribute(
                    single = Some(
                      CatalogManagementDependency.AttributeValue(attributeId3, explicitAttributeVerification = true)
                    ),
                    group = None
                  )
              )
            )
          )
        )
      )

      // Data retrieve
      (readModel
        .aggregate(_: String, _: Seq[Bson], _: Int, _: Int)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, 0, 1, *, *)
        .once()
        .returns(Future.successful(catalogItems))

      // Total count
      (readModel
        .aggregate(_: String, _: Seq[Bson], _: Int, _: Int)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, 0, Int.MaxValue, *, *)
        .once()
        .returns(Future.successful(Seq(TotalCountResult(1))))

      (catalogManagementService
        .createEService(_: CatalogManagementDependency.EServiceSeed)(_: Seq[(String, String)]))
        .expects(seed, *)
        .returning(Future.successful(eservice))
        .once()

      val requestData = apiSeed.toJson.toString

      val response = request("eservices", HttpMethods.POST, Some(requestData))

      response.status shouldBe StatusCodes.Conflict

    }

  }

  "EService update" must {
    "succeed" in {
      val descriptor = descriptorStub.copy(state = CatalogManagementDependency.EServiceDescriptorState.DRAFT)

      val eService = eServiceStub.copy(descriptors = Seq(descriptor), producerId = AdminMockAuthenticator.requesterId)
      val eServiceUuid = eService.id
      val eServiceId   = eServiceUuid.toString

      val eServiceSeed =
        UpdateEServiceSeed(name = "newName", description = "newDescription", technology = EServiceTechnology.REST)

      val updatedEServiceSeed = CatalogManagementDependency.UpdateEServiceSeed(
        name = "newName",
        description = "newDescription",
        technology = eService.technology
      )

      val updatedEService = CatalogManagementDependency.EService(
        id = eServiceUuid,
        producerId = AdminMockAuthenticator.requesterId,
        name = "newName",
        description = "newDescription",
        technology = eService.technology,
        descriptors = Seq(descriptor)
      )

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
        .updateEServiceById(_: String, _: CatalogManagementDependency.UpdateEServiceSeed)(_: Seq[(String, String)]))
        .expects(eServiceId, updatedEServiceSeed, *)
        .returning(Future.successful(updatedEService))
        .once()

      val response = request(s"eservices/$eServiceUuid", HttpMethods.PUT, Some(eServiceSeed.toJson.toString))
      response.status shouldBe StatusCodes.OK
    }

    "succeed if no descriptor is present" in {

      val eService     = eServiceStub.copy(descriptors = Seq.empty, producerId = AdminMockAuthenticator.requesterId)
      val eServiceUuid = eService.id
      val eServiceId   = eServiceUuid.toString

      val eServiceSeed =
        UpdateEServiceSeed(name = "newName", description = "newDescription", technology = EServiceTechnology.REST)

      val updatedEServiceSeed = CatalogManagementDependency.UpdateEServiceSeed(
        name = "newName",
        description = "newDescription",
        technology = eService.technology
      )

      val updatedEService = CatalogManagementDependency.EService(
        id = eServiceUuid,
        producerId = AdminMockAuthenticator.requesterId,
        name = "newName",
        description = "newDescription",
        technology = eService.technology,
        descriptors = Seq.empty
      )

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
        .updateEServiceById(_: String, _: CatalogManagementDependency.UpdateEServiceSeed)(_: Seq[(String, String)]))
        .expects(eServiceId, updatedEServiceSeed, *)
        .returning(Future.successful(updatedEService))
        .once()

      val response = request(s"eservices/$eServiceUuid", HttpMethods.PUT, Some(eServiceSeed.toJson.toString))
      response.status shouldBe StatusCodes.OK
    }

    "fail if descriptor state is not draft" in {
      val descriptor = descriptorStub.copy(state = CatalogManagementDependency.EServiceDescriptorState.PUBLISHED)

      val eService = eServiceStub.copy(descriptors = Seq(descriptor), producerId = AdminMockAuthenticator.requesterId)
      val eServiceUuid = eService.id
      val eServiceId   = eServiceUuid.toString

      val eServiceSeed =
        UpdateEServiceSeed(name = "newName", description = "newDescription", technology = EServiceTechnology.REST)

      val updatedEServiceSeed = CatalogManagementDependency.UpdateEServiceSeed(
        name = "newName",
        description = "newDescription",
        technology = eService.technology
      )

      val updatedEService = CatalogManagementDependency.EService(
        id = eServiceUuid,
        producerId = AdminMockAuthenticator.requesterId,
        name = "newName",
        description = "newDescription",
        technology = eService.technology,
        descriptors = Seq(descriptor)
      )

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
        .updateEServiceById(_: String, _: CatalogManagementDependency.UpdateEServiceSeed)(_: Seq[(String, String)]))
        .expects(eServiceId, updatedEServiceSeed, *)
        .returning(Future.successful(updatedEService))
        .once()

      val response = request(s"eservices/$eServiceUuid", HttpMethods.PUT, Some(eServiceSeed.toJson.toString))
      response.status shouldBe StatusCodes.BadRequest
    }

    "fail if requester is not the Producer" in {
      val seed =
        UpdateEServiceSeed(name = "newName", description = "newDescription", technology = EServiceTechnology.REST)

      failOnRequesterNotProducer(id => request(s"eservices/$id", HttpMethods.PUT, Some(seed.toJson.toString)))
    }
  }

  "EService clone" must {

    "succeed" in {

      val descriptor     = descriptorStub.copy(state = CatalogManagementDependency.EServiceDescriptorState.PUBLISHED)
      val descriptorUuid = descriptor.id
      val descriptorId   = descriptorUuid.toString

      val eService = eServiceStub.copy(descriptors = Seq(descriptor), producerId = AdminMockAuthenticator.requesterId)
      val eServiceUuid = eService.id
      val eServiceId   = eServiceUuid.toString

      val clonedEService = eService.copy()

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
        .cloneEService(_: UUID, _: UUID)(_: Seq[(String, String)]))
        .expects(eServiceUuid, descriptorUuid, *)
        .returning(Future.successful(clonedEService))
        .once()

      val response = request(s"eservices/$eServiceId/descriptors/$descriptorId/clone", HttpMethods.POST)
      response.status shouldBe StatusCodes.OK
    }

    "fail if requester is not the Producer" in {
      failOnRequesterNotProducer(id =>
        request(s"eservices/$id/descriptors/${UUID.randomUUID()}/clone", HttpMethods.POST)
      )
    }
  }

  "EService deletion" must {

    "fail if requester is not the Producer" in {
      failOnRequesterNotProducer(id => request(s"eservices/$id", HttpMethods.DELETE))
    }
  }

  "Descriptor creation" must {

    "fail if requester is not the Producer" in {
      val seed = EServiceDescriptorSeed(
        description = None,
        audience = Seq("aud"),
        voucherLifespan = 60,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        agreementApprovalPolicy = AgreementApprovalPolicy.AUTOMATIC,
        attributes = AttributesSeed(Nil, Nil, Nil)
      )

      failOnRequesterNotProducer(id =>
        request(s"eservices/$id/descriptors", HttpMethods.POST, Some(seed.toJson.toString))
      )
    }
  }

  "Descriptor draft update" must {

    "fail if requester is not the Producer" in {

      val seed = UpdateEServiceDescriptorSeed(
        description = None,
        audience = Seq("aud"),
        voucherLifespan = 60,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        agreementApprovalPolicy = AgreementApprovalPolicy.AUTOMATIC,
        attributes = AttributesSeed(Nil, Nil, Nil)
      )

      failOnRequesterNotProducer(id =>
        request(s"eservices/$id/descriptors/${UUID.randomUUID()}", HttpMethods.PUT, Some(seed.toJson.toString))
      )
    }
  }

  "Descriptor publication" must {

    "fail if requester is not the Producer" in {
      failOnRequesterNotProducer(id =>
        request(s"eservices/$id/descriptors/${UUID.randomUUID()}/publish", HttpMethods.POST)
      )
    }
  }

  "Descriptor suspension" must {
    "succeed if descriptor is Published" in {
      val descriptor = descriptorStub.copy(state = CatalogManagementDependency.EServiceDescriptorState.PUBLISHED)
      val eService   = eServiceStub.copy(descriptors = Seq(descriptor), producerId = AdminMockAuthenticator.requesterId)
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
      val descriptor = descriptorStub.copy(state = CatalogManagementDependency.EServiceDescriptorState.DEPRECATED)
      val eService   = eServiceStub.copy(descriptors = Seq(descriptor), producerId = AdminMockAuthenticator.requesterId)
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
      val descriptor = descriptorStub.copy(state = CatalogManagementDependency.EServiceDescriptorState.DRAFT)
      val eService   = eServiceStub.copy(descriptors = Seq(descriptor), producerId = AdminMockAuthenticator.requesterId)
      val eServiceId = eService.id.toString
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
      val descriptor = descriptorStub.copy(state = CatalogManagementDependency.EServiceDescriptorState.ARCHIVED)
      val eService   = eServiceStub.copy(descriptors = Seq(descriptor), producerId = AdminMockAuthenticator.requesterId)
      val eServiceId = eService.id.toString
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

    "fail if requester is not the Producer" in {
      failOnRequesterNotProducer(id =>
        request(s"eservices/$id/descriptors/${UUID.randomUUID()}/suspend", HttpMethods.POST)
      )
    }

  }

  "Descriptor activation" must {
    val eService1 = eServiceStub.copy(
      descriptors = Seq(
        descriptorStub.copy(version = "1", state = CatalogManagementDependency.EServiceDescriptorState.ARCHIVED),
        descriptorStub.copy(version = "2", state = CatalogManagementDependency.EServiceDescriptorState.DEPRECATED),
        descriptorStub.copy(version = "3", state = CatalogManagementDependency.EServiceDescriptorState.SUSPENDED),
        descriptorStub.copy(version = "4", state = CatalogManagementDependency.EServiceDescriptorState.SUSPENDED),
        descriptorStub.copy(version = "5", state = CatalogManagementDependency.EServiceDescriptorState.DRAFT)
      ),
      producerId = AdminMockAuthenticator.requesterId
    )
    val eService2 = eServiceStub.copy(
      descriptors = Seq(
        descriptorStub.copy(version = "1", state = CatalogManagementDependency.EServiceDescriptorState.ARCHIVED),
        descriptorStub.copy(version = "2", state = CatalogManagementDependency.EServiceDescriptorState.DEPRECATED),
        descriptorStub.copy(version = "3", state = CatalogManagementDependency.EServiceDescriptorState.SUSPENDED),
        descriptorStub.copy(version = "4", state = CatalogManagementDependency.EServiceDescriptorState.PUBLISHED),
        descriptorStub.copy(version = "5", state = CatalogManagementDependency.EServiceDescriptorState.DRAFT)
      ),
      producerId = AdminMockAuthenticator.requesterId
    )
    val eService3 = eServiceStub.copy(
      descriptors = Seq(
        descriptorStub.copy(version = "1", state = CatalogManagementDependency.EServiceDescriptorState.ARCHIVED),
        descriptorStub.copy(version = "2", state = CatalogManagementDependency.EServiceDescriptorState.SUSPENDED),
        descriptorStub.copy(version = "3", state = CatalogManagementDependency.EServiceDescriptorState.SUSPENDED)
      ),
      producerId = AdminMockAuthenticator.requesterId
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
      val descriptor = descriptorStub.copy(state = CatalogManagementDependency.EServiceDescriptorState.DRAFT)
      val eService   = eServiceStub.copy(descriptors = Seq(descriptor), producerId = AdminMockAuthenticator.requesterId)
      val eServiceId = eService.id.toString
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
      val descriptor = descriptorStub.copy(state = CatalogManagementDependency.EServiceDescriptorState.DEPRECATED)
      val eService   = eServiceStub.copy(descriptors = Seq(descriptor), producerId = AdminMockAuthenticator.requesterId)
      val eServiceId = eService.id.toString
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
      val descriptor = descriptorStub.copy(state = CatalogManagementDependency.EServiceDescriptorState.PUBLISHED)
      val eService   = eServiceStub.copy(descriptors = Seq(descriptor), producerId = AdminMockAuthenticator.requesterId)
      val eServiceId = eService.id.toString
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
      val descriptor = descriptorStub.copy(state = CatalogManagementDependency.EServiceDescriptorState.ARCHIVED)
      val eService   = eServiceStub.copy(descriptors = Seq(descriptor), producerId = AdminMockAuthenticator.requesterId)
      val eServiceId = eService.id.toString
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

    "fail if requester is not the Producer" in {
      failOnRequesterNotProducer(id =>
        request(s"eservices/$id/descriptors/${UUID.randomUUID()}/activate", HttpMethods.POST)
      )
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
        agreementApprovalPolicy = AUTOMATIC,
        serverUrls = Nil,
        attributes = CatalogManagementDependency.Attributes(Nil, Nil, Nil)
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
        agreementApprovalPolicy = AUTOMATIC,
        serverUrls = Nil,
        attributes = CatalogManagementDependency.Attributes(Nil, Nil, Nil)
      )

      val eservice = CatalogManagementDependency.EService(
        id = UUID.randomUUID(),
        producerId = AdminMockAuthenticator.requesterId,
        name = "Name",
        description = "Description",
        technology = CatalogManagementDependency.EServiceTechnology.REST,
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
        agreementApprovalPolicy = AUTOMATIC,
        serverUrls = Nil,
        attributes = CatalogManagementDependency.Attributes(Nil, Nil, Nil)
      )

      val eservice = CatalogManagementDependency.EService(
        id = UUID.randomUUID(),
        producerId = AdminMockAuthenticator.requesterId,
        name = "Name",
        description = "Description",
        technology = CatalogManagementDependency.EServiceTechnology.REST,
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

    "fail if requester is not the Producer" in {
      failOnRequesterNotProducer(id => request(s"eservices/$id/descriptors/${UUID.randomUUID()}", HttpMethods.DELETE))
    }
  }

  "Document creation" must {

    "fail if requester is not the Producer" in {
      val seed = CreateEServiceDescriptorDocumentSeed(
        documentId = UUID.randomUUID(),
        kind = EServiceDocumentKind.INTERFACE,
        prettyName = "newPrettyName",
        filePath = "fake",
        fileName = "fake",
        contentType = "fake",
        checksum = "fake",
        serverUrls = List()
      )
      failOnRequesterNotProducer(id =>
        request(
          s"eservices/$id/descriptors/${UUID.randomUUID()}/documents",
          HttpMethods.POST,
          Some(seed.toJson.toString)
        )
      )

    }
  }

  "Document update" must {

    "fail if requester is not the Producer" in {
      val seed = UpdateEServiceDescriptorDocumentSeed(prettyName = "newPrettyName")

      failOnRequesterNotProducer(id =>
        request(
          s"eservices/$id/descriptors/${UUID.randomUUID()}/documents/${UUID.randomUUID()}/update",
          HttpMethods.POST,
          Some(seed.toJson.toString)
        )
      )
    }
  }

  "Document deletion" must {

    "fail if requester is not the Producer" in {
      failOnRequesterNotProducer(id =>
        request(s"eservices/$id/descriptors/${UUID.randomUUID()}/documents/${UUID.randomUUID()}", HttpMethods.DELETE)
      )
    }
  }

  def failOnRequesterNotProducer(response: UUID => HttpResponse): Assertion = {

    val eServiceId: UUID = UUID.randomUUID()

    val eservice = CatalogManagementDependency.EService(
      id = eServiceId,
      producerId = UUID.randomUUID(),
      name = "name",
      description = "description",
      technology = CatalogManagementDependency.EServiceTechnology.REST,
      descriptors = Nil
    )

    (catalogManagementService
      .getEService(_: String)(_: Seq[(String, String)]))
      .expects(eservice.id.toString, *)
      .returning(Future.successful(eservice))
      .once()

    response(eServiceId).status shouldBe StatusCodes.Forbidden

  }
}

object CatalogProcessSpec extends MockFactory {
  val mockHealthApi: HealthApi                                               = mock[HealthApi]
  val catalogManagementService: CatalogManagementService                     = mock[CatalogManagementService]
  val agreementManagementService: AgreementManagementService                 = mock[AgreementManagementService]
  val authorizationManagementService: AuthorizationManagementService         = mock[AuthorizationManagementService]
  val attributeRegistryManagementService: AttributeRegistryManagementService = mock[AttributeRegistryManagementService]
  val tenantManagementService: TenantManagementService                       = mock[TenantManagementService]
  val fileManager: FileManager                                               = mock[FileManager]
  val readModel: ReadModelService                                            = mock[ReadModelService]
  val jwtReader: JWTReader                                                   = mock[JWTReader]
  def mockSubject(uuid: String): Success[JWTClaimsSet] = Success(new JWTClaimsSet.Builder().subject(uuid).build())
}
