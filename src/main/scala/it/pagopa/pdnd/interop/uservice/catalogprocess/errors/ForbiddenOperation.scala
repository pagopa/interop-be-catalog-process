package it.pagopa.pdnd.interop.uservice.catalogprocess.errors

/** ADT modeling a forbidden operation error
  * @param message
  */
final case class ForbiddenOperation(message: String) extends Throwable(s"Operation forbidden :: $message")

@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
object ForbiddenOperation {
  def apply(messages: List[String]): ForbiddenOperation = ForbiddenOperation(messages.mkString("[", ",", "]"))
}
