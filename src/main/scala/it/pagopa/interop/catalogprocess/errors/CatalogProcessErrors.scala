package it.pagopa.interop.catalogprocess.errors

import cats.data.NonEmptyChain
import it.pagopa.interop.commons.utils.errors.ComponentError
import it.pagopa.interop.commons.riskanalysis.error
import java.util.UUID

object CatalogProcessErrors {

  final case class ContentTypeParsingError(contentType: String, documentPath: String, errors: List[String])
      extends ComponentError(
        "0001",
        s"Error parsing content type $contentType for document $documentPath. Reasons: ${errors.mkString(",")}"
      )

  final case class EServiceDescriptorNotFound(eServiceId: String, descriptorId: String)
      extends ComponentError("0002", s"Descriptor $descriptorId for EService $eServiceId not found")

  final case class EServiceDescriptorWithoutInterface(descriptorId: String)
      extends ComponentError("0003", s"Descriptor $descriptorId does not have an interface")

  final case class NotValidDescriptor(descriptorId: String, descriptorStatus: String)
      extends ComponentError(
        "0004",
        s"Descriptor $descriptorId has a not valid status for this operation $descriptorStatus"
      )

  final case class DescriptorDocumentNotFound(eServiceId: String, descriptorId: String, documentId: String)
      extends ComponentError(
        "0006",
        s"Error retrieving document $documentId for E-Service $eServiceId and descriptor $descriptorId"
      )

  final case class EServiceNotFound(eServiceId: String)
      extends ComponentError("0007", s"EService $eServiceId not found")

  final case class DraftDescriptorAlreadyExists(eServiceId: String)
      extends ComponentError("0008", s"EService $eServiceId already contains a draft descriptor")

  final case class EServiceCannotBeUpdated(eServiceId: String)
      extends ComponentError("0009", s"EService $eServiceId contains valid descriptors and cannot be updated")

  final case class DuplicatedEServiceName(name: String)
      extends ComponentError("0010", s"EService with name: $name already in use")

  final case class OriginIsNotCompliant(origin: String)
      extends ComponentError("0011", s"Requester has not origin: $origin")

  final case class EServiceNotInDraftState(eServiceId: UUID)
      extends ComponentError("0012", s"EService $eServiceId is not draft")

  final case class EServiceNotInReceiveMode(eServiceId: UUID)
      extends ComponentError("0013", s"EService $eServiceId is not in receive mode")

  final case class TenantNotFound(tenantId: UUID)
      extends ComponentError("0014", s"Tenant ${tenantId.toString} not found")

  final case class TenantKindNotFound(tenantId: UUID)
      extends ComponentError("0015", s"Tenant kind for tenant ${tenantId.toString} not found")

  final case object RiskAnalysisNotValid extends ComponentError("0016", s"Risk Analysis did not pass validation")

  final case class EServiceRiskAnalysisNotFound(eServiceId: UUID, riskAnalysisId: UUID)
      extends ComponentError("0017", s"Risk Analysis $riskAnalysisId not found for EService $eServiceId")

  final case class EServiceRiskAnalysisIsRequired(eServiceId: UUID)
      extends ComponentError("0018", s"At least one Risk Analysis is required for EService $eServiceId")

  final case class RiskAnalysisValidationFailed(reason: String)
      extends ComponentError("0019", s"Risk analysis validation failed. Reasons: $reason")

  final object RiskAnalysisValidationFailed {
    def apply(failures: NonEmptyChain[error.RiskAnalysisValidationError]): RiskAnalysisValidationFailed =
      RiskAnalysisValidationFailed(failures.map(_.message).distinct.iterator.mkString("[", ", ", "]"))
  }

}
