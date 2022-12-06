package it.pagopa.interop.catalogprocess.errors

import akka.http.scaladsl.server.StandardRoute
import com.typesafe.scalalogging.LoggerTakingImplicit
import it.pagopa.interop.commons.logging.ContextFieldsToLog
import it.pagopa.interop.commons.utils.errors.{AkkaResponses, ServiceCode}
import it.pagopa.interop.catalogprocess.errors.CatalogProcessErrors.{
  ContentTypeParsingError,
  DescriptorDocumentNotFound,
  DraftDescriptorAlreadyExists,
  EServiceCannotBeUpdated,
  EServiceDescriptorNotFound,
  EServiceDescriptorWithoutInterface,
  EServiceNotFound,
  NotValidDescriptor
}

import scala.util.{Failure, Try}

object Handlers extends AkkaResponses {

  implicit val serviceCode: ServiceCode = ServiceCode("009")

  def handleRetrieveError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: EServiceNotFound) => notFound(ex, logMessage)
    case Failure(ex)                   => internalServerError(ex, logMessage)
  }

  def handleListingError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = { case Failure(ex) => internalServerError(ex, logMessage) }

  def handleEServiceCreationError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = { case Failure(ex) => internalServerError(ex, logMessage) }

  def handleEServiceUpdateError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: EServiceNotFound)        => notFound(ex, logMessage)
    case Failure(ex: EServiceCannotBeUpdated) => badRequest(ex, logMessage)
    case Failure(ex)                          => internalServerError(ex, logMessage)
  }

  def handleEServiceDeletionError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: EServiceNotFound) => notFound(ex, logMessage)
    case Failure(ex)                   => internalServerError(ex, logMessage)
  }

  def handleEServiceActivationError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: EServiceNotFound)           => notFound(ex, logMessage)
    case Failure(ex: EServiceDescriptorNotFound) => notFound(ex, logMessage)
    case Failure(ex: NotValidDescriptor)         => badRequest(ex, logMessage)
    case Failure(ex)                             => internalServerError(ex, logMessage)
  }

  def handleEServiceSuspensionError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: EServiceNotFound)           => notFound(ex, logMessage)
    case Failure(ex: EServiceDescriptorNotFound) => notFound(ex, logMessage)
    case Failure(ex: NotValidDescriptor)         => badRequest(ex, logMessage)
    case Failure(ex)                             => internalServerError(ex, logMessage)
  }

  def handleEServiceCloneError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: EServiceDescriptorNotFound) => notFound(ex, logMessage)
    case Failure(ex)                             => internalServerError(ex, logMessage)
  }

  def handleDescriptorCreationError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: EServiceNotFound)             => notFound(ex, logMessage)
    case Failure(ex: DraftDescriptorAlreadyExists) => badRequest(ex, logMessage)
    case Failure(ex)                               => internalServerError(ex, logMessage)
  }

  def handleDescriptorUpdateError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: EServiceNotFound)           => notFound(ex, logMessage)
    case Failure(ex: EServiceDescriptorNotFound) => notFound(ex, logMessage)
    case Failure(ex: NotValidDescriptor)         => badRequest(ex, logMessage)
    case Failure(ex)                             => internalServerError(ex, logMessage)
  }

  def handleDraftDeletionError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: EServiceNotFound)           => notFound(ex, logMessage)
    case Failure(ex: EServiceDescriptorNotFound) => notFound(ex, logMessage)
    case Failure(ex)                             => internalServerError(ex, logMessage)
  }

  def handlePublishError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: EServiceNotFound)                   => notFound(ex, logMessage)
    case Failure(ex: EServiceDescriptorNotFound)         => notFound(ex, logMessage)
    case Failure(ex: EServiceDescriptorWithoutInterface) => badRequest(ex, logMessage)
    case Failure(ex)                                     => internalServerError(ex, logMessage)
  }

  def handleDocumentCreationError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: EServiceDescriptorNotFound) => notFound(ex, logMessage)
    case Failure(ex)                             => internalServerError(ex, logMessage)
  }

  def handleDocumentDeletionError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: DescriptorDocumentNotFound) => notFound(ex, logMessage)
    case Failure(ex)                             => internalServerError(ex, logMessage)
  }

  def handleDocumentUpdateError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: DescriptorDocumentNotFound) => notFound(ex, logMessage)
    case Failure(ex)                             => internalServerError(ex, logMessage)
  }

  def handleDocumentRetrieveError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: DescriptorDocumentNotFound) => notFound(ex, logMessage)
    case Failure(ex: ContentTypeParsingError)    => badRequest(ex, logMessage)
    case Failure(ex)                             => internalServerError(ex, logMessage)
  }

}
