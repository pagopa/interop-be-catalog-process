package it.pagopa.interop.catalogprocess.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import it.pagopa.interop.catalogprocess.model._
import it.pagopa.interop.commons.utils.SprayCommonFormats.{offsetDateTimeFormat, uuidFormat}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val eServiceAttribute2Format: RootJsonFormat[Attribute]                  = jsonFormat2(Attribute)
  implicit val eServiceAttributes2Format: RootJsonFormat[Attributes]                = jsonFormat3(Attributes)
  implicit val organizationFormat: RootJsonFormat[Organization]                     = jsonFormat2(Organization)
  implicit val eServiceDocFormat: RootJsonFormat[EServiceDoc]                       = jsonFormat5(EServiceDoc)
  implicit val eServiceDescriptorFormat: RootJsonFormat[EServiceDescriptor]         = jsonFormat17(EServiceDescriptor)
  implicit val eServiceFormat: RootJsonFormat[EService]                             = jsonFormat6(EService)
  implicit val eServicesFormat: RootJsonFormat[EServices]                           = jsonFormat2(EServices)
  implicit val eServiceAttributeSeedFormat: RootJsonFormat[AttributeSeed]           = jsonFormat2(AttributeSeed)
  implicit val eServiceAttributesSeedFormat: RootJsonFormat[AttributesSeed]         = jsonFormat3(AttributesSeed)
  implicit val eServiceSeedFormat: RootJsonFormat[EServiceSeed]                     = jsonFormat3(EServiceSeed)
  implicit val updateEServiceSeedFormat: RootJsonFormat[UpdateEServiceSeed]         = jsonFormat3(UpdateEServiceSeed)
  implicit val eServiceDescriptorSeedFormat: RootJsonFormat[EServiceDescriptorSeed] =
    jsonFormat7(EServiceDescriptorSeed)
  implicit val updateEServiceDescriptorSeedFormat: RootJsonFormat[UpdateEServiceDescriptorSeed]                 =
    jsonFormat7(UpdateEServiceDescriptorSeed)
  implicit val updateEServiceDescriptorDocumentSeedFormat: RootJsonFormat[UpdateEServiceDescriptorDocumentSeed] =
    jsonFormat1(UpdateEServiceDescriptorDocumentSeed)
  implicit val createEServiceDescriptorDocumentSeedFormat: RootJsonFormat[CreateEServiceDescriptorDocumentSeed] =
    jsonFormat8(CreateEServiceDescriptorDocumentSeed)

  implicit val flatAgreementFormat: RootJsonFormat[FlatAgreement]   = jsonFormat2(FlatAgreement)
  implicit val flatAttributeFormat: RootJsonFormat[FlatAttribute]   = jsonFormat1(FlatAttribute)
  implicit val flatAttributesFormat: RootJsonFormat[FlatAttributes] = jsonFormat1(FlatAttributes)
  implicit val flatEServiceFormat: RootJsonFormat[FlatEService]     = jsonFormat10(FlatEService)

  implicit val eServiceConsumerFormat: RootJsonFormat[EServiceConsumer]   = jsonFormat5(EServiceConsumer)
  implicit val eServiceConsumersFormat: RootJsonFormat[EServiceConsumers] = jsonFormat2(EServiceConsumers)

  implicit val problemErrorFormat: RootJsonFormat[ProblemError] = jsonFormat2(ProblemError)
  implicit val problemFormat: RootJsonFormat[Problem]           = jsonFormat6(Problem)

  final val entityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]

}
