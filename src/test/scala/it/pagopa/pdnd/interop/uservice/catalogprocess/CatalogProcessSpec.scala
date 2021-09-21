package it.pagopa.pdnd.interop.uservice.catalogprocess

import akka.http.scaladsl.model.{HttpMethods, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import it.pagopa.pdnd.interop.uservice.catalogprocess.api.impl._
import it.pagopa.pdnd.interop.uservice.catalogprocess.service.{AgreementManagementService, CatalogManagementService}
import it.pagopa.pdnd.interop.uservice.catalogprocess.api.{HealthApi, ProcessApi, ProcessApiMarshaller}
import it.pagopa.pdnd.interop.uservice.catalogprocess.model._
import it.pagopa.pdnd.interop.uservice.catalogprocess.server.Controller
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json._

import java.util.UUID
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.NonUnitStatements",
    "org.wartremover.warts.Null",
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
        ProcessApiServiceImpl(catalogManagementService, agreementManagementService),
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

  "Processing a request payload" must {

    "create an e-service" in {

      val seed = EServiceSeed(
        producerId = UUID.fromString("c54aebcc-f469-4c5a-b232-8b7003824300"),
        name = "MyService",
        description = "My Service",
        audience = List("aud1"),
        technology = "REST",
        voucherLifespan = 1000,
        attributes = Attributes(
          certified = List(
            Attribute(single = Some(AttributeValue("0001", false)), group = Some(List(AttributeValue("0002", false))))
          ),
          declared = List(
            Attribute(single = Some(AttributeValue("0001", false)), group = Some(List(AttributeValue("0002", false))))
          ),
          verified = List(
            Attribute(single = Some(AttributeValue("0001", false)), group = Some(List(AttributeValue("0002", false))))
          )
        )
      )

      val expected = EService(
        id = UUID.fromString("c54aebcc-f469-4c5a-b232-8b7003824301"),
        producerId = seed.producerId,
        name = seed.name,
        description = seed.description,
        technology = seed.technology,
        attributes = seed.attributes,
        descriptors = List(
          EServiceDescriptor(
            id = UUID.fromString("c54aebcc-f469-4c5a-b232-8b7003824302"),
            version = "1",
            description = None,
            interface = None,
            docs = Nil,
            status = "draft",
            audience = seed.audience,
            voucherLifespan = seed.voucherLifespan
          )
        )
      )

      (catalogManagementService
        .createEService(_: String)(_: EServiceSeed))
        .expects(bearerToken, seed)
        .returning(Future.successful(expected))
        .once()

      val requestData = seed.toJson.toString
      val response    = request("eservices", HttpMethods.POST, Some(requestData))

      response.status shouldBe StatusCodes.OK

      val body = Await.result(Unmarshal(response.entity).to[EService], Duration.Inf)

      body shouldBe expected

    }
  }
}

@SuppressWarnings(Array("org.wartremover.warts.Null", "org.wartremover.warts.ImplicitParameter"))
object CatalogProcessSpec extends MockFactory {
  val mockHealthApi: HealthApi                               = mock[HealthApi]
  val catalogManagementService: CatalogManagementService     = mock[CatalogManagementService]
  val agreementManagementService: AgreementManagementService = mock[AgreementManagementService]
}
