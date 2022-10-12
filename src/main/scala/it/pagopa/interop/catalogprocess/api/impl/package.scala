package it.pagopa.interop.catalogprocess.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCode
import it.pagopa.interop.commons.utils.SprayCommonFormats.uuidFormat
import it.pagopa.interop.commons.utils.errors.ComponentError
import it.pagopa.interop.catalogprocess.model._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val eServiceAttributeValueFormat: RootJsonFormat[AttributeValue]         = jsonFormat4(AttributeValue)
  implicit val eServiceAttributeFormat: RootJsonFormat[Attribute]                   = jsonFormat2(Attribute)
  implicit val eServiceAttributesFormat: RootJsonFormat[Attributes]                 = jsonFormat3(Attributes)
  implicit val organizationFormat: RootJsonFormat[Organization]                     = jsonFormat2(Organization)
  implicit val eServiceDocFormat: RootJsonFormat[EServiceDoc]                       = jsonFormat4(EServiceDoc)
  implicit val eServiceDescriptorFormat: RootJsonFormat[EServiceDescriptor]         = jsonFormat11(EServiceDescriptor)
  implicit val eServiceDescriptorSeedFormat: RootJsonFormat[EServiceDescriptorSeed] =
    jsonFormat6(EServiceDescriptorSeed)
  implicit val eServiceFormat: RootJsonFormat[EService]                             = jsonFormat7(EService)
  implicit val eServiceAttributeValueSeedFormat: RootJsonFormat[AttributeValueSeed] = jsonFormat2(AttributeValueSeed)
  implicit val eServiceAttributeSeedFormat: RootJsonFormat[AttributeSeed]           = jsonFormat2(AttributeSeed)
  implicit val eServiceAttributesSeedFormat: RootJsonFormat[AttributesSeed]         = jsonFormat3(AttributesSeed)
  implicit val eServiceSeedFormat: RootJsonFormat[EServiceSeed]                     = jsonFormat5(EServiceSeed)
  implicit val updateEServiceSeedFormat: RootJsonFormat[UpdateEServiceSeed]         = jsonFormat4(UpdateEServiceSeed)
  implicit val updateEServiceDescriptorSeedFormat: RootJsonFormat[UpdateEServiceDescriptorSeed]                 =
    jsonFormat6(UpdateEServiceDescriptorSeed)
  implicit val updateEServiceDescriptorDocumentSeedFormat: RootJsonFormat[UpdateEServiceDescriptorDocumentSeed] =
    jsonFormat1(UpdateEServiceDescriptorDocumentSeed)

  implicit val flatAgreementFormat: RootJsonFormat[FlatAgreement]           = jsonFormat2(FlatAgreement)
  implicit val flatAttributeValueFormat: RootJsonFormat[FlatAttributeValue] = jsonFormat1(FlatAttributeValue)
  implicit val flatAttributeFormat: RootJsonFormat[FlatAttribute]           = jsonFormat2(FlatAttribute)
  implicit val flatEServiceFormat: RootJsonFormat[FlatEService]             = jsonFormat10(FlatEService)

  implicit val problemErrorFormat: RootJsonFormat[ProblemError] = jsonFormat2(ProblemError)
  implicit val problemFormat: RootJsonFormat[Problem]           = jsonFormat5(Problem)

  final val entityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]

  final val serviceErrorCodePrefix: String = "009"
  final val defaultProblemType: String     = "about:blank"
  final val defaultErrorMessage: String    = "Unknown error"

  def problemOf(httpError: StatusCode, error: ComponentError): Problem =
    Problem(
      `type` = defaultProblemType,
      status = httpError.intValue,
      title = httpError.defaultMessage,
      errors = Seq(
        ProblemError(
          code = s"$serviceErrorCodePrefix-${error.code}",
          detail = Option(error.getMessage).getOrElse(defaultErrorMessage)
        )
      )
    )

  def problemOf(httpError: StatusCode, errors: List[ComponentError]): Problem =
    Problem(
      `type` = defaultProblemType,
      status = httpError.intValue,
      title = httpError.defaultMessage,
      errors = errors.map(error =>
        ProblemError(
          code = s"$serviceErrorCodePrefix-${error.code}",
          detail = Option(error.getMessage).getOrElse(defaultErrorMessage)
        )
      )
    )
}
