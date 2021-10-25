package it.pagopa.pdnd.interop.uservice.catalogprocess

import akka.http.scaladsl.model.{HttpMethods, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.{
  EServiceDescriptorEnums => ManagementDescriptorEnums
}
import it.pagopa.pdnd.interop.uservice.catalogprocess.api.impl._
import it.pagopa.pdnd.interop.uservice.catalogprocess.api.{HealthApi, ProcessApi, ProcessApiMarshaller}
import it.pagopa.pdnd.interop.uservice.catalogprocess.model._
import it.pagopa.pdnd.interop.uservice.catalogprocess.server.Controller
import it.pagopa.pdnd.interop.uservice.catalogprocess.service._
import it.pagopa.pdnd.interop.uservice.{attributeregistrymanagement, catalogmanagement, partymanagement}
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json._

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.NonUnitStatements",
    "org.wartremover.warts.Null",
    "org.wartremover.warts.ToString",
    "org.wartremover.warts.Nothing",
    "org.wartremover.warts.StringPlusAny",
    "org.wartremover.warts.Any",
    "org.wartremover.warts.OptionPartial",
    "org.wartremover.warts.Var"
  )
)
class CatalogProcessSpec extends SpecHelper with AnyWordSpecLike with BeforeAndAfterAll with MockFactory {

  import CatalogProcessSpec._

  val processApiMarshaller: ProcessApiMarshaller = new ProcessApiMarshallerImpl

  var controller: Option[Controller] = None

  override def beforeAll(): Unit = {

    val processApi =
      new ProcessApi(
        ProcessApiServiceImpl(
          catalogManagementService = catalogManagementService,
          partyManagementService = partyManagementService,
          attributeRegistryManagementService = attributeRegistryManagementService,
          agreementManagementService = agreementManagementService,
          fileManager = fileManager
        ),
        processApiMarshaller,
        wrappingDirective
      )

    controller = Some(new Controller(mockHealthApi, processApi))

    controller.foreach(startServer)
  }

  override def afterAll(): Unit = {
    shutDownServer()
    super.afterAll()
  }

  "EService creation" must {

    "succeed" in {

      val seed = catalogmanagement.client.model.EServiceSeed(
        producerId = UUID.fromString("c54aebcc-f469-4c5a-b232-8b7003824300"),
        name = "MyService",
        description = "My Service",
        technology = catalogmanagement.client.model.EServiceSeedEnums.Technology.REST,
        attributes = catalogmanagement.client.model.Attributes(
          certified = List(
            catalogmanagement.client.model
              .Attribute(
                single =
                  Some(catalogmanagement.client.model.AttributeValue("0001", explicitAttributeVerification = false)),
                group = None
              )
          ),
          declared = List(
            catalogmanagement.client.model
              .Attribute(
                single = None,
                group = Some(
                  List(catalogmanagement.client.model.AttributeValue("0002", explicitAttributeVerification = false))
                )
              )
          ),
          verified = List(
            catalogmanagement.client.model
              .Attribute(
                single =
                  Some(catalogmanagement.client.model.AttributeValue("0003", explicitAttributeVerification = true)),
                group = None
              )
          )
        )
      )

      val eservice = catalogmanagement.client.model.EService(
        id = UUID.fromString("c54aebcc-f469-4c5a-b232-8b7003824301"),
        producerId = seed.producerId,
        name = seed.name,
        description = seed.description,
        technology = seed.technology.toString,
        attributes = seed.attributes,
        descriptors = List(
          catalogmanagement.client.model.EServiceDescriptor(
            id = UUID.fromString("c54aebcc-f469-4c5a-b232-8b7003824302"),
            version = "1",
            description = None,
            interface = None,
            docs = Nil,
            status = catalogmanagement.client.model.EServiceDescriptorEnums.Status.Draft,
            audience = List("aud1"),
            voucherLifespan = 1000
          )
        )
      )

      val organization = partymanagement.client.model.Organization(
        institutionId = "institutionId",
        description = "organization description",
        digitalAddress = "digitalAddress",
        id = seed.producerId,
        attributes = Seq.empty[String]
      )

      val attributeId1: String = "0001"
      val attributeId2: String = "0002"
      val attributeId3: String = "0003"

      val attribute1 = attributeregistrymanagement.client.model.Attribute(
        id = attributeId1,
        code = None,
        certified = true,
        description = s"$attributeId1-description",
        origin = None,
        name = s"$attributeId1-name",
        creationTime = OffsetDateTime.now()
      )
      val attribute2 = attributeregistrymanagement.client.model.Attribute(
        id = attributeId2,
        code = None,
        certified = false,
        description = s"$attributeId2-description",
        origin = None,
        name = s"$attributeId2-name",
        creationTime = OffsetDateTime.now()
      )
      val attribute3 = attributeregistrymanagement.client.model.Attribute(
        id = attributeId3,
        code = None,
        certified = false,
        description = s"$attributeId3-description",
        origin = None,
        name = s"$attributeId3-name",
        creationTime = OffsetDateTime.now()
      )

      (catalogManagementService
        .createEService(_: String)(_: catalogmanagement.client.model.EServiceSeed))
        .expects(bearerToken, seed)
        .returning(Future.successful(eservice))
        .once()

      (partyManagementService.getOrganization _)
        .expects(seed.producerId)
        .returning(Future.successful(organization))
        .once()

      (attributeRegistryManagementService.getAttributesBulk _)
        .expects(Seq(attributeId1, attributeId2, attributeId3))
        .returning(Future.successful(Seq(attribute1, attribute2, attribute3)))
        .once()

      implicit val seedFormat: JsonFormat[catalogmanagement.client.model.EServiceSeedEnums.Technology] =
        new JsonFormat[catalogmanagement.client.model.EServiceSeedEnums.Technology] {
          override def write(obj: catalogmanagement.client.model.EServiceSeedEnums.Technology): JsValue =
            JsString(obj.toString)

          override def read(json: JsValue): catalogmanagement.client.model.EServiceSeedEnums.Technology = json match {
            case JsString(s) =>
              s match {
                case "REST" => catalogmanagement.client.model.EServiceSeedEnums.Technology.REST
                case "SOAP" => catalogmanagement.client.model.EServiceSeedEnums.Technology.SOAP
                case _ =>
                  deserializationError(s"could not parse $s as EServiceSeedEnums.Technology")
              }
            case notAJsString =>
              deserializationError(s"expected a String but got a ${notAJsString.compactPrint}")
          }
        }

      implicit val attributeValueFormat: RootJsonFormat[client.model.AttributeValue] =
        jsonFormat2(catalogmanagement.client.model.AttributeValue)

      implicit val attributeFormat: RootJsonFormat[client.model.Attribute] =
        jsonFormat2(catalogmanagement.client.model.Attribute)

      implicit val attributesFormat: RootJsonFormat[client.model.Attributes] =
        jsonFormat3(catalogmanagement.client.model.Attributes)

      implicit val eServiceSeedFormat: RootJsonFormat[client.model.EServiceSeed] =
        jsonFormat5(catalogmanagement.client.model.EServiceSeed)

      val requestData = seed.toJson.toString

      val response = request("eservices", HttpMethods.POST, Some(requestData))

      val expected = EService(
        id = UUID.fromString("c54aebcc-f469-4c5a-b232-8b7003824301"),
        producer = Organization(id = organization.id, name = organization.description),
        name = seed.name,
        description = seed.description,
        technology = seed.technology.toString,
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
      val descriptor   = descriptorStub.copy(status = ManagementDescriptorEnums.Status.Published)
      val eService     = eServiceStub.copy(descriptors = Seq(descriptor))
      val eServiceId   = eService.id.toString
      val descriptorId = descriptor.id.toString

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

      response.status shouldBe StatusCodes.NoContent
    }

    "succeed if descriptor is Deprecated" in {
      val descriptor   = descriptorStub.copy(status = ManagementDescriptorEnums.Status.Deprecated)
      val eService     = eServiceStub.copy(descriptors = Seq(descriptor))
      val eServiceId   = eService.id.toString
      val descriptorId = descriptor.id.toString

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

      response.status shouldBe StatusCodes.NoContent
    }

    "fail if descriptor is Draft" in {
      val descriptor   = descriptorStub.copy(status = ManagementDescriptorEnums.Status.Draft)
      val eService     = eServiceStub.copy(descriptors = Seq(descriptor))
      val eServiceId   = eService.id.toString
      val descriptorId = descriptor.id.toString

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
      val descriptor   = descriptorStub.copy(status = ManagementDescriptorEnums.Status.Archived)
      val eService     = eServiceStub.copy(descriptors = Seq(descriptor))
      val eServiceId   = eService.id.toString
      val descriptorId = descriptor.id.toString

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
        descriptorStub.copy(version = "1", status = ManagementDescriptorEnums.Status.Archived),
        descriptorStub.copy(version = "2", status = ManagementDescriptorEnums.Status.Deprecated),
        descriptorStub.copy(version = "3", status = ManagementDescriptorEnums.Status.Suspended),
        descriptorStub.copy(version = "4", status = ManagementDescriptorEnums.Status.Suspended),
        descriptorStub.copy(version = "5", status = ManagementDescriptorEnums.Status.Draft)
      )
    )
    val eService2 = eServiceStub.copy(descriptors =
      Seq(
        descriptorStub.copy(version = "1", status = ManagementDescriptorEnums.Status.Archived),
        descriptorStub.copy(version = "2", status = ManagementDescriptorEnums.Status.Deprecated),
        descriptorStub.copy(version = "3", status = ManagementDescriptorEnums.Status.Suspended),
        descriptorStub.copy(version = "4", status = ManagementDescriptorEnums.Status.Published),
        descriptorStub.copy(version = "5", status = ManagementDescriptorEnums.Status.Draft)
      )
    )
    val eService3 = eServiceStub.copy(descriptors =
      Seq(
        descriptorStub.copy(version = "1", status = ManagementDescriptorEnums.Status.Archived),
        descriptorStub.copy(version = "2", status = ManagementDescriptorEnums.Status.Suspended),
        descriptorStub.copy(version = "3", status = ManagementDescriptorEnums.Status.Suspended)
      )
    )

    "activate Deprecated descriptor - case 1" in {
      val eService     = eService1
      val eServiceId   = eService.id.toString
      val descriptorId = eService.descriptors.find(_.version == "3").get.id.toString

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

      val response = request(s"eservices/$eServiceId/descriptors/$descriptorId/activate", HttpMethods.POST)

      response.status shouldBe StatusCodes.NoContent
    }

    "activate Deprecated descriptor - case 2" in {
      val eService     = eService2
      val eServiceId   = eService.id.toString
      val descriptorId = eService.descriptors.find(_.version == "3").get.id.toString

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

      val response = request(s"eservices/$eServiceId/descriptors/$descriptorId/activate", HttpMethods.POST)

      response.status shouldBe StatusCodes.NoContent
    }

    "activate Published descriptor - case 1" in {
      val eService     = eService1
      val eServiceId   = eService.id.toString
      val descriptorId = eService.descriptors.find(_.version == "4").get.id.toString

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

      val response = request(s"eservices/$eServiceId/descriptors/$descriptorId/activate", HttpMethods.POST)

      response.status shouldBe StatusCodes.NoContent
    }

    "activate Published descriptor - case 2" in {
      val eService     = eService3
      val eServiceId   = eService.id.toString
      val descriptorId = eService.descriptors.find(_.version == "3").get.id.toString

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

      val response = request(s"eservices/$eServiceId/descriptors/$descriptorId/activate", HttpMethods.POST)

      response.status shouldBe StatusCodes.NoContent
    }

    "fail if descriptor is Draft" in {
      val descriptor   = descriptorStub.copy(status = ManagementDescriptorEnums.Status.Draft)
      val eService     = eServiceStub.copy(descriptors = Seq(descriptor))
      val eServiceId   = eService.id.toString
      val descriptorId = descriptor.id.toString

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
      val descriptor   = descriptorStub.copy(status = ManagementDescriptorEnums.Status.Deprecated)
      val eService     = eServiceStub.copy(descriptors = Seq(descriptor))
      val eServiceId   = eService.id.toString
      val descriptorId = descriptor.id.toString

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
      val descriptor   = descriptorStub.copy(status = ManagementDescriptorEnums.Status.Published)
      val eService     = eServiceStub.copy(descriptors = Seq(descriptor))
      val eServiceId   = eService.id.toString
      val descriptorId = descriptor.id.toString

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
      val descriptor   = descriptorStub.copy(status = ManagementDescriptorEnums.Status.Archived)
      val eService     = eServiceStub.copy(descriptors = Seq(descriptor))
      val eServiceId   = eService.id.toString
      val descriptorId = descriptor.id.toString

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

@SuppressWarnings(Array("org.wartremover.warts.Null", "org.wartremover.warts.ImplicitParameter"))
object CatalogProcessSpec extends MockFactory {
  val mockHealthApi: HealthApi                                               = mock[HealthApi]
  val catalogManagementService: CatalogManagementService                     = mock[CatalogManagementService]
  val agreementManagementService: AgreementManagementService                 = mock[AgreementManagementService]
  val attributeRegistryManagementService: AttributeRegistryManagementService = mock[AttributeRegistryManagementService]
  val partyManagementService: PartyManagementService                         = mock[PartyManagementService]
  val fileManager: FileManager                                               = mock[FileManager]
}
