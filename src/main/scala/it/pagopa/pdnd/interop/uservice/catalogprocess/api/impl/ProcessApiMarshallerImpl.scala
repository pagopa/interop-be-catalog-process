package it.pagopa.pdnd.interop.uservice.catalogprocess.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.pdnd.interop.uservice.catalogprocess.api.ProcessApiMarshaller
import it.pagopa.pdnd.interop.uservice.catalogprocess.model.{
  EService,
  EServiceDescriptor,
  EServiceDescriptorSeed,
  EServiceDoc,
  EServiceSeed,
  FlatEService,
  Problem,
  UpdateEServiceDescriptorDocumentSeed,
  UpdateEServiceDescriptorSeed,
  UpdateEServiceSeed
}
import spray.json._

import java.io.File
import java.nio.charset.StandardCharsets
import scala.io.{BufferedSource, Codec}

final case class ProcessApiMarshallerImpl()
    extends ProcessApiMarshaller
    with SprayJsonSupport
    with DefaultJsonProtocol {

  override implicit def fromEntityUnmarshallerEServiceSeed: FromEntityUnmarshaller[EServiceSeed] =
    sprayJsonUnmarshaller[EServiceSeed]

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]

  override implicit def toEntityMarshallerEService: ToEntityMarshaller[EService] = sprayJsonMarshaller[EService]

  override implicit def toEntityMarshallerEServicearray: ToEntityMarshaller[Seq[EService]] =
    sprayJsonMarshaller[Seq[EService]]

  override implicit def toEntityMarshallerFlatEServicearray: ToEntityMarshaller[Seq[FlatEService]] =
    sprayJsonMarshaller[Seq[FlatEService]]

  override implicit def toEntityMarshallerFile: ToEntityMarshaller[File] =
    Marshaller.withFixedContentType(ContentTypes.`application/octet-stream`) { f =>
      val source: BufferedSource = scala.io.Source.fromFile(f.getPath)(Codec(StandardCharsets.UTF_8.name))
      val out: String            = source.mkString
      source.close()
      out.getBytes(StandardCharsets.UTF_8.name)
    }

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
}
