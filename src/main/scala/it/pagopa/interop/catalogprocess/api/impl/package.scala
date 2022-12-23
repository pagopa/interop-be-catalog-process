package it.pagopa.interop.catalogprocess.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import it.pagopa.interop.catalogprocess.model._
import it.pagopa.interop.commons.utils.SprayCommonFormats.uuidFormat
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val eServiceAttributeValueFormat: RootJsonFormat[OldAttributeValue]      = jsonFormat4(OldAttributeValue)
  implicit val eServiceAttributeFormat: RootJsonFormat[OldAttribute]                = jsonFormat2(OldAttribute)
  implicit val eServiceAttributesFormat: RootJsonFormat[OldAttributes]              = jsonFormat3(OldAttributes)
  implicit val eServiceAttribute2ValueFormat: RootJsonFormat[AttributeValue]        = jsonFormat2(AttributeValue)
  implicit val eServiceAttribute2Format: RootJsonFormat[Attribute]                  = jsonFormat2(Attribute)
  implicit val eServiceAttributes2Format: RootJsonFormat[Attributes]                = jsonFormat3(Attributes)
  implicit val organizationFormat: RootJsonFormat[Organization]                     = jsonFormat2(Organization)
  implicit val eServiceDocFormat: RootJsonFormat[EServiceDoc]                       = jsonFormat4(EServiceDoc)
  implicit val eServiceDescriptorFormat: RootJsonFormat[EServiceDescriptor]         = jsonFormat12(EServiceDescriptor)
  implicit val eServiceDescriptorSeedFormat: RootJsonFormat[EServiceDescriptorSeed] =
    jsonFormat6(EServiceDescriptorSeed)
  implicit val oldEServiceFormat: RootJsonFormat[OldEService]                       = jsonFormat7(OldEService)
  implicit val eServiceFormat: RootJsonFormat[EService]                             = jsonFormat7(EService)
  implicit val eServicesFormat: RootJsonFormat[EServices]                           = jsonFormat2(EServices)
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

}
