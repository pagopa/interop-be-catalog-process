package it.pagopa.pdnd.interop.uservice.catalogprocess.errors

final case class NotValidDescriptor(descriptorId: String, descriptorStatus: String)
    extends Throwable(s"Descriptor $descriptorId has a not valid status for this operation $descriptorStatus")
