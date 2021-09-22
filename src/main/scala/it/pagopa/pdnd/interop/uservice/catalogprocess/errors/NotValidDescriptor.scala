package it.pagopa.pdnd.interop.uservice.catalogprocess.errors

/** ADT modeling a not valid descriptor error
  * @param message
  */
final case class NotValidDescriptor(message: String) extends Throwable(s"Descriptor not valid: $message")
