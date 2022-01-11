package it.pagopa.pdnd.interop.uservice.catalogprocess.errors

import it.pagopa.pdnd.interop.commons.utils.errors.ComponentError

object CatalogProcessErrors {

  final case class ContentTypeParsingError(contentType: String, documentPath: String, errors: List[String])
      extends ComponentError("0001", s"Error trying to parse content type $contentType for document ${documentPath}.")

  final case class EServiceDescriptorNotFound(eServiceId: String, descriptorId: String)
      extends ComponentError("0002", s"Descriptor $descriptorId for EService $eServiceId not found")

  final case class EServiceDescriptorWithoutInterface(descriptorId: String)
      extends ComponentError("0003", s"Descriptor $descriptorId does not have an interface")

  final case class NotValidDescriptor(descriptorId: String, descriptorStatus: String)
      extends ComponentError(
        "0004",
        s"Descriptor $descriptorId has a not valid status for this operation $descriptorStatus"
      )

  final case class ForbiddenOperation(message: String) extends ComponentError("0005", s"Operation forbidden: $message")

  final case class EServiceCreationError(message: String)             extends ComponentError("0006", message)
  final case class DraftDescriptorDeletionBadRequest(message: String) extends ComponentError("0007", message)
  final case class DraftDescriptorDeletionNotFound(message: String)   extends ComponentError("0008", message)
  final case class DraftDescriptorDeletionError(message: String)      extends ComponentError("0009", message)

  final case class EServiceRetrievalError(eServiceId: String)
      extends ComponentError("0010", s"Unexpected error while retrieving E-Service: $eServiceId")

  final case class PublishDescriptorBadRequest(descriptorId: String, eServiceId: String)
      extends ComponentError("0011", s"Error while publishing descriptor $descriptorId for E-Service $eServiceId")
  final case class PublishDescriptorNotFound(descriptorId: String, eServiceId: String)
      extends ComponentError("0012", s"Error while publishing descriptor $descriptorId for E-Service $eServiceId")
  final case class PublishDescriptorError(descriptorId: String, eServiceId: String)
      extends ComponentError("0013", s"Error while publishing descriptor $descriptorId for E-Service $eServiceId")

  final case object EServicesRetrievalError
      extends ComponentError("0014", s"Unexpected error while retrieving E-Services")

  final case class CreateDescriptorDocumentBadRequest(descriptorId: String, eServiceId: String)
      extends ComponentError("0015", s"Error while creating document for $descriptorId for E-Service $eServiceId")
  final case class CreateDescriptorDocumentNotFound(descriptorId: String, eServiceId: String)
      extends ComponentError("0016", s"Error while creating document for $descriptorId for E-Service $eServiceId")
  final case class CreateDescriptorDocumentError(descriptorId: String, eServiceId: String)
      extends ComponentError("0017", s"Error while creating document for $descriptorId for E-Service $eServiceId")

  final case object FlattenedEServicesRetrievalError
      extends ComponentError("0018", s"Unexpected error while retrieving flattened E-Services")

  final case class CreateDescriptorError(eServiceId: String)
      extends ComponentError("0019", s"Error while creating Descriptor for e-service identified as $eServiceId")
  final case class UpdateDraftDescriptorError(eServiceId: String)
      extends ComponentError("0020", s"Error while updating draft Descriptor for e-service identified as $eServiceId")
  final case class UpdateEServiceError(eServiceId: String)
      extends ComponentError("0021", s"Error while updating e-service having Id $eServiceId")

  final case class DeleteDescriptorDocumentBadRequest(documentId: String, descriptorId: String, eServiceId: String)
      extends ComponentError(
        "0022",
        s"Error deleting document $documentId for E-Service $eServiceId and descriptor $descriptorId"
      )
  final case class DeleteDescriptorDocumentNotFound(documentId: String, descriptorId: String, eServiceId: String)
      extends ComponentError(
        "0023",
        s"Error deleting document $documentId for E-Service $eServiceId and descriptor $descriptorId"
      )
  final case class DeleteDescriptorDocumentError(documentId: String, descriptorId: String, eServiceId: String)
      extends ComponentError(
        "0024",
        s"Error deleting document $documentId for E-Service $eServiceId and descriptor $descriptorId"
      )

  final case class UpdateDescriptorDocumentBadRequest(documentId: String, descriptorId: String, eServiceId: String)
      extends ComponentError(
        "0025",
        s"Error updating document $documentId for E-Service $eServiceId and descriptor $descriptorId"
      )
  final case class UpdateDescriptorDocumentNotFound(documentId: String, descriptorId: String, eServiceId: String)
      extends ComponentError(
        "0026",
        s"Error updating document $documentId for E-Service $eServiceId and descriptor $descriptorId"
      )
  final case class UpdateDescriptorDocumentError(documentId: String, descriptorId: String, eServiceId: String)
      extends ComponentError(
        "0027",
        s"Error updating document $documentId for E-Service $eServiceId and descriptor $descriptorId"
      )

  final case class CloneDescriptorError(descriptorId: String, eServiceId: String)
      extends ComponentError("0028", s"Error while cloning descriptor $descriptorId for E-service $eServiceId")

  final case class EServiceDeletionError(eServiceId: String)
      extends ComponentError("0029", s"Error while deleting E-Service $eServiceId")

  final case class ActivateDescriptorDocumentBadRequest(descriptorId: String, eServiceId: String)
      extends ComponentError("0030", s"Error while activating descriptor $descriptorId for E-Service $eServiceId")
  final case class ActivateDescriptorDocumentNotFound(descriptorId: String, eServiceId: String)
      extends ComponentError("0031", s"Error while activating descriptor $descriptorId for E-Service $eServiceId")
  final case class ActivateDescriptorDocumentError(descriptorId: String, eServiceId: String)
      extends ComponentError("0032", s"Error while activating descriptor $descriptorId for E-Service $eServiceId")

  final case class SuspendDescriptorDocumentBadRequest(descriptorId: String, eServiceId: String)
      extends ComponentError("0033", s"Error while suspending descriptor $descriptorId for E-Service $eServiceId")
  final case class SuspendDescriptorDocumentNotFound(descriptorId: String, eServiceId: String)
      extends ComponentError("0034", s"Error while suspending descriptor $descriptorId for E-Service $eServiceId")
  final case class SuspendDescriptorDocumentError(descriptorId: String, eServiceId: String)
      extends ComponentError("0035", s"Error while suspending descriptor $descriptorId for E-Service $eServiceId")

  final case class GetDescriptorDocumentBadRequest(documentId: String, descriptorId: String, eServiceId: String)
      extends ComponentError(
        "0036",
        s"Error retrieving document $documentId for E-Service $eServiceId and descriptor $descriptorId"
      )
  final case class GetDescriptorDocumentNotFound(documentId: String, descriptorId: String, eServiceId: String)
      extends ComponentError(
        "0037",
        s"Error retrieving document $documentId for E-Service $eServiceId and descriptor $descriptorId"
      )
  final case class GetDescriptorDocumentError(documentId: String, descriptorId: String, eServiceId: String)
      extends ComponentError(
        "0038",
        s"Error retrieving document $documentId for E-Service $eServiceId and descriptor $descriptorId"
      )
}
