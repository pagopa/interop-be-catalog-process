package it.pagopa.pdnd.interop.uservice.catalogprocess.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import it.pagopa.pdnd.interop.uservice.catalogprocess.model._
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat, deserializationError}

import java.util.UUID
import scala.util.{Failure, Success, Try}

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val uuidFormat: JsonFormat[UUID] =
    new JsonFormat[UUID] {
      override def write(obj: UUID): JsValue = JsString(obj.toString)

      override def read(json: JsValue): UUID = json match {
        case JsString(s) =>
          Try(UUID.fromString(s)) match {
            case Success(result) => result
            case Failure(exception) =>
              deserializationError(s"could not parse $s as UUID", exception)
          }
        case notAJsString =>
          deserializationError(s"expected a String but got a ${notAJsString.compactPrint}")
      }
    }

  implicit val eServiceAttributeValueFormat: RootJsonFormat[AttributeValue] = jsonFormat4(AttributeValue)
  implicit val eServiceAttributeFormat: RootJsonFormat[Attribute]           = jsonFormat2(Attribute)
  implicit val eServiceAttributesFormat: RootJsonFormat[Attributes]         = jsonFormat3(Attributes)
  implicit val organizationFormat: RootJsonFormat[Organization]             = jsonFormat2(Organization)
  implicit val eServiceDocFormat: RootJsonFormat[EServiceDoc]               = jsonFormat4(EServiceDoc)
  implicit val eServiceDescriptorFormat: RootJsonFormat[EServiceDescriptor] = jsonFormat8(EServiceDescriptor)
  implicit val eServiceDescriptorSeedFormat: RootJsonFormat[EServiceDescriptorSeed] = jsonFormat3(
    EServiceDescriptorSeed
  )
  implicit val eServiceFormat: RootJsonFormat[EService]                             = jsonFormat7(EService)
  implicit val eServiceAttributeValueSeedFormat: RootJsonFormat[AttributeValueSeed] = jsonFormat2(AttributeValueSeed)
  implicit val eServiceAttributeSeedFormat: RootJsonFormat[AttributeSeed]           = jsonFormat2(AttributeSeed)
  implicit val eServiceAttributesSeedFormat: RootJsonFormat[AttributesSeed]         = jsonFormat3(AttributesSeed)
  implicit val eServiceSeedFormat: RootJsonFormat[EServiceSeed]                     = jsonFormat5(EServiceSeed)
  implicit val updateEServiceSeedFormat: RootJsonFormat[UpdateEServiceSeed]         = jsonFormat4(UpdateEServiceSeed)
  implicit val updateEServiceDescriptorSeedFormat: RootJsonFormat[UpdateEServiceDescriptorSeed] = jsonFormat3(
    UpdateEServiceDescriptorSeed
  )
  implicit val updateEServiceDescriptorDocumentSeedFormat: RootJsonFormat[UpdateEServiceDescriptorDocumentSeed] =
    jsonFormat1(UpdateEServiceDescriptorDocumentSeed)

  implicit val flatAttributeValueFormat: RootJsonFormat[FlatAttributeValue] = jsonFormat1(FlatAttributeValue)
  implicit val flatAttributeFormat: RootJsonFormat[FlatAttribute]           = jsonFormat2(FlatAttribute)
  implicit val flatEServiceFormat: RootJsonFormat[FlatEService]             = jsonFormat9(FlatEService)

  implicit val problemFormat: RootJsonFormat[Problem] = jsonFormat3(Problem)

}
