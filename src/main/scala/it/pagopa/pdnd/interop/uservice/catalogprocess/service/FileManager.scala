package it.pagopa.pdnd.interop.uservice.catalogprocess.service

import java.io.ByteArrayOutputStream
import scala.concurrent.Future

trait FileManager {
  def get(filePath: String): Future[ByteArrayOutputStream]
}
