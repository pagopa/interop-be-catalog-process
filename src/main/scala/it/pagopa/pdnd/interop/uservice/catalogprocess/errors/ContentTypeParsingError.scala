package it.pagopa.pdnd.interop.uservice.catalogprocess.errors

import akka.http.scaladsl.model.ErrorInfo
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.EServiceDoc

final case class ContentTypeParsingError(document: EServiceDoc, errors: List[ErrorInfo]) extends Throwable {
  override def getMessage: String = {
    val errorTxt: String = errors.map(_.formatPretty).mkString("\n")
    s"Error trying to parse content type ${document.contentType} for document ${document.path},reason:\n$errorTxt"
  }
}
