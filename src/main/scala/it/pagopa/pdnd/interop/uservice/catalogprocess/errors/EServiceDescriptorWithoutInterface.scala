package it.pagopa.pdnd.interop.uservice.catalogprocess.errors

final case class EServiceDescriptorWithoutInterface(descriptorId: String)
    extends Throwable(s"Descriptor $descriptorId does not have an interface")
