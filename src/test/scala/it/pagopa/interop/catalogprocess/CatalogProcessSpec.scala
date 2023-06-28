package it.pagopa.interop.catalogprocess

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import it.pagopa.interop.authorizationmanagement.client.{model => AuthorizationManagementDependency}
import it.pagopa.interop.catalogmanagement.client.model.AgreementApprovalPolicy.AUTOMATIC
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagementDependency}
import it.pagopa.interop.catalogmanagement.{model => readmodel}
import it.pagopa.interop.catalogprocess.api.impl.Converter._
import it.pagopa.interop.catalogprocess.api.impl._
import it.pagopa.interop.catalogprocess.common.readmodel.TotalCountResult
import it.pagopa.interop.commons.utils.{ORGANIZATION_ID_CLAIM, USER_ROLES}
import it.pagopa.interop.catalogprocess.model._
import org.mongodb.scala.bson.conversions.Bson
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.matchers.should.Matchers._
import spray.json._

import java.util.UUID
import scala.concurrent.{Future, ExecutionContext}

class CatalogProcessSpec extends SpecHelper with AnyWordSpecLike with ScalatestRouteTest {

  "EService creation" should {

    "succeed" in {

      val requesterId = UUID.randomUUID()

      val attributeId1: UUID = UUID.randomUUID()
      val attributeId2: UUID = UUID.randomUUID()
      val attributeId3: UUID = UUID.randomUUID()

      val catalogItems: Seq[readmodel.CatalogItem] = Seq.empty

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val apiSeed: EServiceSeed = EServiceSeed(
        name = "MyService",
        description = "My Service",
        technology = EServiceTechnology.REST,
        attributes = AttributesSeed(
          certified = List(
            AttributeSeed(
              single = Some(AttributeValueSeed(attributeId1, explicitAttributeVerification = false)),
              group = None
            )
          ),
          declared = List(
            AttributeSeed(
              single = None,
              group = Some(List(AttributeValueSeed(attributeId2, explicitAttributeVerification = false)))
            )
          ),
          verified = List(
            AttributeSeed(
              single = Some(AttributeValueSeed(attributeId3, explicitAttributeVerification = true)),
              group = None
            )
          )
        )
      )

      val seed = CatalogManagementDependency.EServiceSeed(
        producerId = requesterId,
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
      (mockReadModel
        .aggregate(_: String, _: Seq[Bson], _: Int, _: Int)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, 0, 1, *, *)
        .once()
        .returns(Future.successful(catalogItems))

      // Total count
      (mockReadModel
        .aggregate(_: String, _: Seq[Bson], _: Int, _: Int)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, 0, Int.MaxValue, *, *)
        .once()
        .returns(Future.successful(Seq(TotalCountResult(0))))

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
        attributes = Attributes(
          certified = Seq(
            Attribute(
              single = Some(AttributeValue(id = attributeId1, explicitAttributeVerification = false)),
              group = None
            )
          ),
          declared = Seq(
            Attribute(
              single = None,
              group = Some(Seq(AttributeValue(id = attributeId2, explicitAttributeVerification = false)))
            )
          ),
          verified = Seq(
            Attribute(
              single = Some(AttributeValue(id = attributeId3, explicitAttributeVerification = true)),
              group = None
            )
          )
        ),
        descriptors = eservice.descriptors.map(_.toApi)
      )

      Post() ~> service.createEService(apiSeed) ~> check {
        status shouldEqual StatusCodes.OK
        val response = responseAs[EService]
        response shouldEqual expected
      }
    }

    "fail with conflict if an EService with the same name exists" in {

      val requesterId = UUID.randomUUID()

      val attributeId1: UUID = UUID.randomUUID()
      val attributeId2: UUID = UUID.randomUUID()
      val attributeId3: UUID = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val catalogItems: Seq[readmodel.CatalogItem] = Seq(SpecData.catalogItem)

      val apiSeed: EServiceSeed = EServiceSeed(
        name = "MyService",
        description = "My Service",
        technology = EServiceTechnology.REST,
        attributes = AttributesSeed(
          certified = List(
            AttributeSeed(
              single = Some(AttributeValueSeed(attributeId1, explicitAttributeVerification = false)),
              group = None
            )
          ),
          declared = List(
            AttributeSeed(
              single = None,
              group = Some(List(AttributeValueSeed(attributeId2, explicitAttributeVerification = false)))
            )
          ),
          verified = List(
            AttributeSeed(
              single = Some(AttributeValueSeed(attributeId3, explicitAttributeVerification = true)),
              group = None
            )
          )
        )
      )

      // Data retrieve
      (mockReadModel
        .aggregate(_: String, _: Seq[Bson], _: Int, _: Int)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, 0, 1, *, *)
        .once()
        .returns(Future.successful(catalogItems))

      // Total count
      (mockReadModel
        .aggregate(_: String, _: Seq[Bson], _: Int, _: Int)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, 0, Int.MaxValue, *, *)
        .once()
        .returns(Future.successful(Seq(TotalCountResult(1))))

      Post() ~> service.createEService(apiSeed) ~> check {
        status shouldEqual StatusCodes.Conflict
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

      val eServiceSeed = UpdateEServiceSeed(
        name = "newName",
        description = "newDescription",
        technology = EServiceTechnology.REST,
        attributes = AttributesSeed(Nil, Nil, Nil)
      )

      val updatedEServiceSeed = CatalogManagementDependency.UpdateEServiceSeed(
        name = "newName",
        description = "newDescription",
        technology = eService.technology
      )

      val updatedEService = CatalogManagementDependency.EService(
        id = eService.id,
        producerId = requesterId,
        name = "newName",
        description = "newDescription",
        technology = eService.technology,
        descriptors = Seq(descriptor)
      )

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(SpecData.catalogItem.copy(producerId = requesterId))))

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

      val eServiceSeed = UpdateEServiceSeed(
        name = "newName",
        description = "newDescription",
        technology = EServiceTechnology.REST,
        attributes = AttributesSeed(Nil, Nil, Nil)
      )

      val updatedEServiceSeed = CatalogManagementDependency.UpdateEServiceSeed(
        name = "newName",
        description = "newDescription",
        technology = eService.technology
      )

      val updatedEService = CatalogManagementDependency.EService(
        id = eService.id,
        producerId = requesterId,
        name = "newName",
        description = "newDescription",
        technology = eService.technology,
        descriptors = Seq.empty
      )

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(SpecData.catalogItem.copy(producerId = requesterId))))

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

      val eServiceSeed = UpdateEServiceSeed(
        name = "newName",
        description = "newDescription",
        technology = EServiceTechnology.REST,
        attributes = AttributesSeed(Nil, Nil, Nil)
      )

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(
          Future.successful(
            Some(
              SpecData.catalogItem
                .copy(producerId = requesterId, descriptors = Seq(SpecData.catalogDescriptor))
            )
          )
        )

      Put() ~> service.updateEServiceById(eService.id.toString, eServiceSeed) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail if requester is not the Producer" in {

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      val eServiceSeed = UpdateEServiceSeed(
        name = "newName",
        description = "newDescription",
        technology = EServiceTechnology.REST,
        attributes = AttributesSeed(Nil, Nil, Nil)
      )

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(
          Future.successful(
            Some(
              SpecData.catalogItem
                .copy(producerId = UUID.randomUUID(), descriptors = Seq(SpecData.catalogDescriptor))
            )
          )
        )

      Put() ~> service.updateEServiceById(UUID.randomUUID().toString, eServiceSeed) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
    "fail if EService does not exist" in {

      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val eServiceSeed = UpdateEServiceSeed(
        name = "newName",
        description = "newDescription",
        technology = EServiceTechnology.REST,
        attributes = AttributesSeed(Nil, Nil, Nil)
      )

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(None))

      Put() ~> service.updateEServiceById(UUID.randomUUID().toString, eServiceSeed) ~> check {
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

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(SpecData.catalogItem.copy(producerId = requesterId))))

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

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(SpecData.catalogItem.copy(producerId = UUID.randomUUID()))))

      Post() ~> service.cloneEServiceByDescriptor(UUID.randomUUID().toString, UUID.randomUUID().toString) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
    "fail if EService does not exist" in {

      val requesterId                             = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(None))

      Post() ~> service.cloneEServiceByDescriptor(UUID.randomUUID().toString, UUID.randomUUID().toString) ~> check {
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

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(SpecData.catalogItem.copy(id = eService.id, producerId = requesterId))))

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

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(SpecData.catalogItem.copy(producerId = UUID.randomUUID()))))

      Delete() ~> service.deleteEServiceDocumentById(
        UUID.randomUUID().toString,
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

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(None))

      Delete() ~> service.deleteEServiceDocumentById(
        UUID.randomUUID().toString,
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

      val seed = EServiceDescriptorSeed(
        description = None,
        audience = Seq("aud"),
        voucherLifespan = 60,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        agreementApprovalPolicy = AgreementApprovalPolicy.AUTOMATIC
      )

      val eServiceDescriptorSeed = CatalogManagementDependency.EServiceDescriptorSeed(
        description = None,
        audience = Seq("aud"),
        voucherLifespan = 60,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        agreementApprovalPolicy = CatalogManagementDependency.AgreementApprovalPolicy.AUTOMATIC
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
        serverUrls = Nil
      )

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(SpecData.catalogItem.copy(producerId = requesterId))))

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

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(SpecData.catalogItem.copy(producerId = UUID.randomUUID()))))

      val seed = EServiceDescriptorSeed(
        description = None,
        audience = Seq("aud"),
        voucherLifespan = 60,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        agreementApprovalPolicy = AgreementApprovalPolicy.AUTOMATIC
      )

      Post() ~> service.createDescriptor(UUID.randomUUID().toString, seed) ~> check {
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
        agreementApprovalPolicy = AgreementApprovalPolicy.AUTOMATIC
      )

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(None))

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

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(
          Future.successful(
            Some(
              SpecData.catalogItem.copy(
                producerId = requesterId,
                descriptors = Seq(SpecData.catalogDescriptor.copy(state = readmodel.Draft))
              )
            )
          )
        )

      val seed = UpdateEServiceDescriptorSeed(
        description = None,
        audience = Seq("aud"),
        voucherLifespan = 60,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        agreementApprovalPolicy = AgreementApprovalPolicy.AUTOMATIC
      )

      val depSeed = CatalogManagementDependency.UpdateEServiceDescriptorSeed(
        description = None,
        audience = Seq("aud"),
        voucherLifespan = 60,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        agreementApprovalPolicy = CatalogManagementDependency.AgreementApprovalPolicy.AUTOMATIC,
        state = CatalogManagementDependency.EServiceDescriptorState.DRAFT
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

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(
          Future.successful(
            Some(
              SpecData.catalogItem.copy(
                producerId = requesterId,
                descriptors = Seq(SpecData.catalogDescriptor.copy(state = readmodel.Published))
              )
            )
          )
        )

      val seed = UpdateEServiceDescriptorSeed(
        description = None,
        audience = Seq("aud"),
        voucherLifespan = 60,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        agreementApprovalPolicy = AgreementApprovalPolicy.AUTOMATIC
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
        agreementApprovalPolicy = AgreementApprovalPolicy.AUTOMATIC
      )

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(None))

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
        agreementApprovalPolicy = AgreementApprovalPolicy.AUTOMATIC
      )

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(SpecData.catalogItem.copy(producerId = requesterId))))

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

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(SpecData.catalogItem.copy(producerId = UUID.randomUUID()))))

      val seed = UpdateEServiceDescriptorSeed(
        description = None,
        audience = Seq("aud"),
        voucherLifespan = 60,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        agreementApprovalPolicy = AgreementApprovalPolicy.AUTOMATIC
      )

      Put() ~> service.updateDraftDescriptor(UUID.randomUUID().toString, UUID.randomUUID().toString, seed) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
  }
  "Descriptor publication" should {
    "succeed if descriptor is Draft" in {
      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(
          Future.successful(
            Some(
              SpecData.catalogItem.copy(
                producerId = requesterId,
                descriptors = Seq(
                  SpecData.catalogDescriptor.copy(state = readmodel.Draft, interface = Option(SpecData.catalogDocument))
                )
              )
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

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(
          Future.successful(
            Some(
              SpecData.catalogItem.copy(
                producerId = requesterId,
                descriptors = Seq(SpecData.catalogDescriptor.copy(state = readmodel.Draft))
              )
            )
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

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(
          Future.successful(
            Some(
              SpecData.catalogItem.copy(
                producerId = requesterId,
                descriptors = Seq(SpecData.catalogDescriptor.copy(state = readmodel.Published))
              )
            )
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

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(None))

      Post() ~> service.publishDescriptor(SpecData.catalogItem.id.toString, UUID.randomUUID.toString) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if EService descriptor does not exist" in {

      val requesterId                             = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(SpecData.catalogItem.copy(producerId = requesterId))))

      Post() ~> service.publishDescriptor(SpecData.catalogItem.id.toString, UUID.randomUUID.toString) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if requester is not the Producer" in {

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(SpecData.catalogItem.copy(producerId = UUID.randomUUID()))))

      Post() ~> service.publishDescriptor(UUID.randomUUID().toString, UUID.randomUUID.toString) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
  }
  "Descriptor suspension" should {
    "succeed if descriptor is Published" in {

      val requesterId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(
          Future.successful(
            Some(
              SpecData.catalogItem.copy(
                producerId = requesterId,
                descriptors = Seq(SpecData.catalogDescriptor.copy(state = readmodel.Published))
              )
            )
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

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(
          Future.successful(
            Some(
              SpecData.catalogItem.copy(
                producerId = requesterId,
                descriptors = Seq(SpecData.catalogDescriptor.copy(state = readmodel.Deprecated))
              )
            )
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

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(
          Future.successful(
            Some(
              SpecData.catalogItem.copy(
                producerId = requesterId,
                descriptors = Seq(SpecData.catalogDescriptor.copy(state = readmodel.Draft))
              )
            )
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

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(
          Future.successful(
            Some(
              SpecData.catalogItem.copy(
                producerId = requesterId,
                descriptors = Seq(SpecData.catalogDescriptor.copy(state = readmodel.Archived))
              )
            )
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

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(None))

      Post() ~> service.suspendDescriptor(UUID.randomUUID().toString, UUID.randomUUID().toString) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if EService descriptor does not exist" in {

      val requesterId                             = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(SpecData.catalogItem.copy(producerId = requesterId))))

      Post() ~> service.suspendDescriptor(SpecData.catalogItem.id.toString, UUID.randomUUID().toString) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if requester is not the Producer" in {

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(SpecData.catalogItem.copy(producerId = UUID.randomUUID()))))

      Post() ~> service.suspendDescriptor(UUID.randomUUID().toString, UUID.randomUUID.toString) ~> check {
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
        SpecData.catalogDescriptor.copy(id = descriptorId, version = "3", state = readmodel.Suspended)
      val eService   = SpecData.catalogItem.copy(
        descriptors = Seq(
          SpecData.catalogDescriptor.copy(version = "1", state = readmodel.Archived),
          SpecData.catalogDescriptor.copy(version = "2", state = readmodel.Deprecated),
          descriptor,
          SpecData.catalogDescriptor.copy(version = "4", state = readmodel.Suspended),
          SpecData.catalogDescriptor.copy(version = "5", state = readmodel.Draft)
        ),
        producerId = requesterId
      )

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(eService)))

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
        SpecData.catalogDescriptor.copy(id = descriptorId, version = "3", state = readmodel.Suspended)
      val eService   = SpecData.catalogItem.copy(
        descriptors = Seq(
          SpecData.catalogDescriptor.copy(version = "1", state = readmodel.Archived),
          SpecData.catalogDescriptor.copy(version = "2", state = readmodel.Deprecated),
          descriptor,
          SpecData.catalogDescriptor.copy(version = "4", state = readmodel.Published),
          SpecData.catalogDescriptor.copy(version = "5", state = readmodel.Draft)
        ),
        producerId = requesterId
      )

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(eService)))

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

      val descriptor = SpecData.catalogDescriptor.copy(id = descriptorId, version = "4", state = readmodel.Suspended)

      val eService = SpecData.catalogItem.copy(
        descriptors = Seq(
          SpecData.catalogDescriptor.copy(version = "1", state = readmodel.Archived),
          SpecData.catalogDescriptor.copy(version = "2", state = readmodel.Deprecated),
          SpecData.catalogDescriptor.copy(version = "3", state = readmodel.Suspended),
          descriptor,
          SpecData.catalogDescriptor.copy(version = "5", state = readmodel.Draft)
        ),
        producerId = requesterId
      )

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(eService)))

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

      val descriptor = SpecData.catalogDescriptor.copy(id = descriptorId, version = "4", state = readmodel.Suspended)

      val eService = SpecData.catalogItem.copy(
        descriptors = Seq(
          SpecData.catalogDescriptor.copy(version = "1", state = readmodel.Archived),
          SpecData.catalogDescriptor.copy(version = "2", state = readmodel.Suspended),
          descriptor
        ),
        producerId = requesterId
      )

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(eService)))

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

      val descriptor = SpecData.catalogDescriptor.copy(id = descriptorId, state = readmodel.Draft)

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), producerId = requesterId)

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(eService)))

      Post() ~> service.activateDescriptor(SpecData.catalogItem.id.toString, descriptorId.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
    "fail if descriptor is Deprecated" in {
      val requesterId                             = UUID.randomUUID()
      val descriptorId                            = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor = SpecData.catalogDescriptor.copy(id = descriptorId, state = readmodel.Deprecated)

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), producerId = requesterId)

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(eService)))

      Post() ~> service.activateDescriptor(SpecData.catalogItem.id.toString, descriptorId.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
    "fail if descriptor is Published" in {
      val requesterId                             = UUID.randomUUID()
      val descriptorId                            = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor = SpecData.catalogDescriptor.copy(id = descriptorId, state = readmodel.Published)

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), producerId = requesterId)

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(eService)))

      Post() ~> service.activateDescriptor(SpecData.catalogItem.id.toString, descriptorId.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
    "fail if descriptor is Archived" in {
      val requesterId                             = UUID.randomUUID()
      val descriptorId                            = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      val descriptor = SpecData.catalogDescriptor.copy(id = descriptorId, state = readmodel.Archived)

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), producerId = requesterId)

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(eService)))

      Post() ~> service.activateDescriptor(SpecData.catalogItem.id.toString, descriptorId.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
    "fail if EService does not exist" in {

      val requesterId                             = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(None))

      Post() ~> service.activateDescriptor(UUID.randomUUID.toString, UUID.randomUUID.toString) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if EService descriptor does not exist" in {

      val requesterId                             = UUID.randomUUID()
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterId.toString)

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(SpecData.catalogItem.copy(producerId = requesterId))))

      Post() ~> service.activateDescriptor(SpecData.catalogItem.id.toString, UUID.randomUUID.toString) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if requester is not the Producer" in {
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(SpecData.catalogItem.copy(producerId = UUID.randomUUID()))))

      Post() ~> service.activateDescriptor(UUID.randomUUID().toString, UUID.randomUUID.toString) ~> check {
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

      val descriptor = SpecData.catalogDescriptor.copy(id = descriptorId, state = readmodel.Draft)

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor), producerId = requesterId)

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(eService)))

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

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(None))

      Delete() ~> service.deleteDraft(UUID.randomUUID.toString, UUID.randomUUID.toString) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if requester is not the Producer" in {
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(SpecData.catalogItem.copy(producerId = UUID.randomUUID()))))

      Delete() ~> service.deleteDraft(UUID.randomUUID().toString, UUID.randomUUID.toString) ~> check {
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

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(eService)))

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

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(None))

      Delete() ~> service.deleteEService(UUID.randomUUID().toString) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if requester is not the Producer" in {
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(SpecData.catalogItem.copy(producerId = UUID.randomUUID()))))

      Delete() ~> service.deleteEService(UUID.randomUUID().toString) ~> check {
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

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(eService)))

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

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(None))

      Post() ~> service.createEServiceDocument(UUID.randomUUID().toString, UUID.randomUUID().toString, seed) ~> check {
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

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(SpecData.catalogItem.copy(producerId = requesterId))))

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

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(SpecData.catalogItem.copy(producerId = UUID.randomUUID()))))

      Post() ~> service.createEServiceDocument(UUID.randomUUID().toString, UUID.randomUUID().toString, seed) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
  }
  "Document retrieve" should {

    "succeed if document is an interface" in {

      val descriptorId = UUID.randomUUID()
      val documentId   = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      val descriptor = SpecData.catalogDescriptor.copy(
        id = descriptorId,
        interface = Some(SpecData.catalogDocument.copy(id = documentId))
      )

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor))

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(eService)))

      Post() ~> service.getEServiceDocumentById(
        SpecData.catalogItem.id.toString,
        descriptorId.toString,
        documentId.toString
      ) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "succeed if document is not an interface" in {

      val descriptorId = UUID.randomUUID()
      val documentId   = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      val descriptor =
        SpecData.catalogDescriptor.copy(id = descriptorId, docs = Seq(SpecData.catalogDocument.copy(id = documentId)))

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor))

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(eService)))

      Post() ~> service.getEServiceDocumentById(
        SpecData.catalogItem.id.toString,
        descriptorId.toString,
        documentId.toString
      ) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    "fail if EService does not exist" in {

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(None))

      Post() ~> service.getEServiceDocumentById(
        UUID.randomUUID.toString,
        UUID.randomUUID.toString,
        UUID.randomUUID.toString
      ) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if EService Descriptor does not exist" in {

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(SpecData.catalogItem)))

      Post() ~> service.getEServiceDocumentById(
        SpecData.catalogItem.id.toString,
        UUID.randomUUID.toString,
        UUID.randomUUID.toString
      ) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if Descriptor Document does not exist (No documents at all)" in {

      val descriptorId = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      val descriptor =
        SpecData.catalogDescriptor.copy(id = descriptorId)

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor))

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(eService)))

      Post() ~> service.getEServiceDocumentById(
        SpecData.catalogItem.id.toString,
        descriptorId.toString,
        UUID.randomUUID.toString
      ) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if Descriptor Document does not exist (No document id found)" in {

      val descriptorId = UUID.randomUUID()
      val documentId   = UUID.randomUUID()

      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      val descriptor =
        SpecData.catalogDescriptor.copy(id = descriptorId, docs = Seq(SpecData.catalogDocument.copy(id = documentId)))

      val eService = SpecData.catalogItem.copy(descriptors = Seq(descriptor))

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(eService)))

      Post() ~> service.getEServiceDocumentById(
        SpecData.catalogItem.id.toString,
        descriptorId.toString,
        UUID.randomUUID.toString
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

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(eService)))

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

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(None))

      Post() ~> service.updateEServiceDocumentById(
        UUID.randomUUID().toString,
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

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(SpecData.catalogItem.copy(producerId = UUID.randomUUID()))))

      Post() ~> service.updateEServiceDocumentById(
        UUID.randomUUID().toString,
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

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(eService)))

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

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(None))

      Delete() ~> service.deleteEServiceDocumentById(
        UUID.randomUUID().toString,
        UUID.randomUUID().toString,
        UUID.randomUUID().toString
      ) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "fail if requester is not the Producer" in {
      implicit val context: Seq[(String, String)] =
        Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID().toString)

      (mockReadModel
        .findOne(_: String, _: Bson)(_: JsonReader[_], _: ExecutionContext))
        .expects("eservices", *, *, *)
        .once()
        .returns(Future.successful(Some(SpecData.catalogItem.copy(producerId = UUID.randomUUID()))))

      Delete() ~> service.deleteEServiceDocumentById(
        UUID.randomUUID().toString,
        UUID.randomUUID().toString,
        UUID.randomUUID().toString
      ) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
  }
}
