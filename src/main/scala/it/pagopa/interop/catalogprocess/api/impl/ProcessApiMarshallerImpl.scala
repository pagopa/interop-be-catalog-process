package it.pagopa.interop.catalogprocess.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.interop.catalogprocess.api.ProcessApiMarshaller
import it.pagopa.interop.catalogprocess.model._
import spray.json._

object ProcessApiMarshallerImpl extends ProcessApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  override implicit def fromEntityUnmarshallerEServiceSeed: FromEntityUnmarshaller[EServiceSeed] =
    sprayJsonUnmarshaller[EServiceSeed]

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = entityMarshallerProblem

  override implicit def toEntityMarshallerEService: ToEntityMarshaller[EService] = sprayJsonMarshaller[EService]

  override implicit def toEntityMarshallerEServices: ToEntityMarshaller[EServices] = sprayJsonMarshaller[EServices]

  override implicit def fromEntityUnmarshallerUpdateEServiceSeed: FromEntityUnmarshaller[UpdateEServiceSeed] =
    sprayJsonUnmarshaller[UpdateEServiceSeed]

  override implicit def fromEntityUnmarshallerEServiceDescriptorSeed: FromEntityUnmarshaller[EServiceDescriptorSeed] =
    sprayJsonUnmarshaller[EServiceDescriptorSeed]

  override implicit def fromEntityUnmarshallerUpdateEServiceDescriptorSeed
    : FromEntityUnmarshaller[UpdateEServiceDescriptorSeed] = sprayJsonUnmarshaller[UpdateEServiceDescriptorSeed]

  override implicit def toEntityMarshallerEServiceDescriptor: ToEntityMarshaller[EServiceDescriptor] =
    sprayJsonMarshaller[EServiceDescriptor]

  override implicit def fromEntityUnmarshallerUpdateEServiceDescriptorDocumentSeed
    : FromEntityUnmarshaller[UpdateEServiceDescriptorDocumentSeed] =
    sprayJsonUnmarshaller[UpdateEServiceDescriptorDocumentSeed]

  override implicit def toEntityMarshallerEServiceDoc: ToEntityMarshaller[EServiceDoc] =
    sprayJsonMarshaller[EServiceDoc]

  override def fromEntityUnmarshallerCreateEServiceDescriptorDocumentSeed
    : FromEntityUnmarshaller[CreateEServiceDescriptorDocumentSeed] =
    sprayJsonUnmarshaller[CreateEServiceDescriptorDocumentSeed]

  override implicit def toEntityMarshallerEServiceConsumers: ToEntityMarshaller[EServiceConsumers] =
    sprayJsonMarshaller[EServiceConsumers]

  override implicit def fromEntityUnmarshallerEServiceRiskAnalysisSeed
    : FromEntityUnmarshaller[EServiceRiskAnalysisSeed] = sprayJsonUnmarshaller[EServiceRiskAnalysisSeed]
}
