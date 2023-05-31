package it.pagopa.interop.catalogprocess.common.readmodel

import it.pagopa.interop.agreementmanagement.model.agreement.PersistentAgreementState
import it.pagopa.interop.agreementmanagement.model.persistence.JsonFormats._
import it.pagopa.interop.catalogmanagement.model.persistence.JsonFormats._
import it.pagopa.interop.catalogmanagement.model.CatalogDescriptorState
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

final case class Consumers(
  descriptorVersion: String,
  descriptorState: CatalogDescriptorState,
  agreementState: PersistentAgreementState,
  consumerName: String,
  consumerExternalId: String
)

object Consumers {
  implicit val format: RootJsonFormat[Consumers] = jsonFormat5(Consumers.apply)
}
