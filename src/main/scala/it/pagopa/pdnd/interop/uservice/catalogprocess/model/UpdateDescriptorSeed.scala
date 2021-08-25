package it.pagopa.pdnd.interop.uservice.catalogprocess.model
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.EServiceDescriptorSeedEnums.Status
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.EServiceDescriptorSeed

final case class UpdateDescriptorSeed(description: Option[String], status: Option[Status]) {
  def toApi(): EServiceDescriptorSeed = EServiceDescriptorSeed(description = description, status = status)
}
