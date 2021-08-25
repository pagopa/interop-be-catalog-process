package it.pagopa.pdnd.interop.uservice.catalogprocess.model
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.EServiceDescriptorEnums.Status

final case class UpdateDescriptorSeed(description: Option[String], status: Option[Status])
