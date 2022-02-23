package it.pagopa.interop.catalogprocess.api.impl

import akka.http.scaladsl.model.ContentType

import java.io.ByteArrayOutputStream

final case class DocumentDetails(name: String, contentType: ContentType, data: ByteArrayOutputStream)
