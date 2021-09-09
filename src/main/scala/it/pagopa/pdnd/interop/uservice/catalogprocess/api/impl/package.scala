package it.pagopa.pdnd.interop.uservice.catalogprocess.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import it.pagopa.pdnd.interopuservice.catalogprocess.model.{
  Attribute,
  AttributeValue,
  Attributes,
  EService,
  EServiceDescriptor,
  EServiceDoc,
  EServiceFlatten,
  EServiceSeed,
  Problem
}
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

  implicit val eServiceAttributeValueFormat: RootJsonFormat[AttributeValue] = jsonFormat2(AttributeValue)
  implicit val eServiceAttributeFormat: RootJsonFormat[Attribute]           = jsonFormat2(Attribute)
  implicit val eServiceAttributesFormat: RootJsonFormat[Attributes]         = jsonFormat3(Attributes)
  implicit val eServiceDocFormat: RootJsonFormat[EServiceDoc]               = jsonFormat4(EServiceDoc)
  implicit val eServiceDescriptorFormat: RootJsonFormat[EServiceDescriptor] = jsonFormat6(EServiceDescriptor)
  implicit val eServiceFormat: RootJsonFormat[EService]                     = jsonFormat9(EService)
  implicit val eServiceSeedFormat: RootJsonFormat[EServiceSeed]             = jsonFormat7(EServiceSeed)
  implicit val eServiceFlattenFormat: RootJsonFormat[EServiceFlatten]       = jsonFormat5(EServiceFlatten)
  implicit val problemFormat: RootJsonFormat[Problem]                       = jsonFormat3(Problem)

}
