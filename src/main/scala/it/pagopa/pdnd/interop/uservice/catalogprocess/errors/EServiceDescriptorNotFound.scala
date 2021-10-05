package it.pagopa.pdnd.interop.uservice.catalogprocess.errors

final case class EServiceDescriptorNotFound(eServiceId: String, descriptorId: String)
    extends Throwable(s"Descriptor $descriptorId for EService $eServiceId not found")
