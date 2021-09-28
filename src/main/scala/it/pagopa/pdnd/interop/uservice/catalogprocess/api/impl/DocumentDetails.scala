package it.pagopa.pdnd.interop.uservice.catalogprocess.api.impl

import akka.http.scaladsl.model.ContentType

import java.io.ByteArrayOutputStream

final case class DocumentDetails(name: String, contentType: ContentType, data: ByteArrayOutputStream)
