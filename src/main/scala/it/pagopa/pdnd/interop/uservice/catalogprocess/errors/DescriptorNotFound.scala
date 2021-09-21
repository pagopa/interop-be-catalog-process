package it.pagopa.pdnd.interop.uservice.catalogprocess.errors

/** ADT modeling a missing descriptor error
  * @param message
  */
final case class DescriptorNotFound(message: String) extends Throwable(s"Descriptor not found: $message")
