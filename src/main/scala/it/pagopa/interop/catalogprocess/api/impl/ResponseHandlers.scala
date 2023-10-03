package it.pagopa.interop.catalogprocess.api.impl

import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LoggerTakingImplicit
import it.pagopa.interop.catalogprocess.errors.CatalogProcessErrors._
import it.pagopa.interop.commons.logging.ContextFieldsToLog
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.OperationForbidden
import it.pagopa.interop.commons.utils.errors.{AkkaResponses, ServiceCode}

import scala.util.{Failure, Success, Try}

object ResponseHandlers extends AkkaResponses {

  implicit val serviceCode: ServiceCode = ServiceCode("009")

  def getEServiceByIdResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                    => success(s)
      case Failure(ex: EServiceNotFound) => notFound(ex, logMessage)
      case Failure(ex)                   => internalServerError(ex, logMessage)
    }

  def getEServiceConsumersResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)  => success(s)
      case Failure(ex) => internalServerError(ex, logMessage)
    }

  def getEServicesResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)  => success(s)
      case Failure(ex) => internalServerError(ex, logMessage)
    }

  def createEServiceResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                          => success(s)
      case Failure(ex: OriginIsNotCompliant)   => forbidden(ex, logMessage)
      case Failure(ex: DuplicatedEServiceName) => conflict(ex, logMessage)
      case Failure(ex)                         => internalServerError(ex, logMessage)
    }

  def createRiskAnalysisResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                                => success(s)
      case Failure(ex: OperationForbidden.type)      => forbidden(ex, logMessage)
      case Failure(ex: EServiceNotFound)             => notFound(ex, logMessage)
      case Failure(ex: EServiceNotInDraftState)      => badRequest(ex, logMessage)
      case Failure(ex: EServiceNotInReceiveMode)     => badRequest(ex, logMessage)
      case Failure(ex: RiskAnalysisValidationFailed) => badRequest(ex, logMessage)
      case Failure(ex)                               => internalServerError(ex, logMessage)
    }

  def updateRiskAnalysisResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                                => success(s)
      case Failure(ex: OperationForbidden.type)      => forbidden(ex, logMessage)
      case Failure(ex: EServiceNotFound)             => notFound(ex, logMessage)
      case Failure(ex: EServiceRiskAnalysisNotFound) => notFound(ex, logMessage)
      case Failure(ex: EServiceNotInDraftState)      => badRequest(ex, logMessage)
      case Failure(ex: EServiceNotInReceiveMode)     => badRequest(ex, logMessage)
      case Failure(ex: RiskAnalysisValidationFailed) => badRequest(ex, logMessage)
      case Failure(ex)                               => internalServerError(ex, logMessage)
    }

  def deleteRiskAnalysisResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                                => success(s)
      case Failure(ex: EServiceNotFound)             => notFound(ex, logMessage)
      case Failure(ex: EServiceRiskAnalysisNotFound) => notFound(ex, logMessage)
      case Failure(ex: EServiceNotInDraftState)      => badRequest(ex, logMessage)
      case Failure(ex: EServiceNotInReceiveMode)     => badRequest(ex, logMessage)
      case Failure(ex)                               => internalServerError(ex, logMessage)
    }

  def updateEServiceByIdResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                           => success(s)
      case Failure(ex: OperationForbidden.type) => forbidden(ex, logMessage)
      case Failure(ex: EServiceNotFound)        => notFound(ex, logMessage)
      case Failure(ex: EServiceCannotBeUpdated) => badRequest(ex, logMessage)
      case Failure(ex)                          => internalServerError(ex, logMessage)
    }

  def deleteEServiceResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                           => success(s)
      case Failure(ex: OperationForbidden.type) => forbidden(ex, logMessage)
      case Failure(ex: EServiceNotFound)        => notFound(ex, logMessage)
      case Failure(ex)                          => internalServerError(ex, logMessage)
    }

  def activateDescriptorResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                              => success(s)
      case Failure(ex: OperationForbidden.type)    => forbidden(ex, logMessage)
      case Failure(ex: EServiceNotFound)           => notFound(ex, logMessage)
      case Failure(ex: EServiceDescriptorNotFound) => notFound(ex, logMessage)
      case Failure(ex: NotValidDescriptor)         => badRequest(ex, logMessage)
      case Failure(ex)                             => internalServerError(ex, logMessage)
    }

  def suspendDescriptorResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                              => success(s)
      case Failure(ex: OperationForbidden.type)    => forbidden(ex, logMessage)
      case Failure(ex: EServiceNotFound)           => notFound(ex, logMessage)
      case Failure(ex: EServiceDescriptorNotFound) => notFound(ex, logMessage)
      case Failure(ex: NotValidDescriptor)         => badRequest(ex, logMessage)
      case Failure(ex)                             => internalServerError(ex, logMessage)
    }

  def cloneEServiceByDescriptorResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                              => success(s)
      case Failure(ex: OperationForbidden.type)    => forbidden(ex, logMessage)
      case Failure(ex: EServiceNotFound)           => notFound(ex, logMessage)
      case Failure(ex: EServiceDescriptorNotFound) => notFound(ex, logMessage)
      case Failure(ex)                             => internalServerError(ex, logMessage)
    }

  def createDescriptorResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                                => success(s)
      case Failure(ex: OperationForbidden.type)      => forbidden(ex, logMessage)
      case Failure(ex: EServiceNotFound)             => notFound(ex, logMessage)
      case Failure(ex: DraftDescriptorAlreadyExists) => badRequest(ex, logMessage)
      case Failure(ex)                               => internalServerError(ex, logMessage)
    }

  def updateDraftDescriptorResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                              => success(s)
      case Failure(ex: OperationForbidden.type)    => forbidden(ex, logMessage)
      case Failure(ex: EServiceNotFound)           => notFound(ex, logMessage)
      case Failure(ex: EServiceDescriptorNotFound) => notFound(ex, logMessage)
      case Failure(ex: NotValidDescriptor)         => badRequest(ex, logMessage)
      case Failure(ex)                             => internalServerError(ex, logMessage)
    }

  def deleteDraftResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                              => success(s)
      case Failure(ex: OperationForbidden.type)    => forbidden(ex, logMessage)
      case Failure(ex: EServiceNotFound)           => notFound(ex, logMessage)
      case Failure(ex: EServiceDescriptorNotFound) => notFound(ex, logMessage)
      case Failure(ex)                             => internalServerError(ex, logMessage)
    }

  def publishDescriptorResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                                      => success(s)
      case Failure(ex: OperationForbidden.type)            => forbidden(ex, logMessage)
      case Failure(ex: EServiceNotFound)                   => notFound(ex, logMessage)
      case Failure(ex: EServiceDescriptorNotFound)         => notFound(ex, logMessage)
      case Failure(ex: EServiceDescriptorWithoutInterface) => badRequest(ex, logMessage)
      case Failure(ex: EServiceRiskAnalysisIsRequired)     => badRequest(ex, logMessage)
      case Failure(ex: RiskAnalysisNotValid.type)          => badRequest(ex, logMessage)
      case Failure(ex)                                     => internalServerError(ex, logMessage)
    }

  def createEServiceDocumentResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                              => success(s)
      case Failure(ex: OperationForbidden.type)    => forbidden(ex, logMessage)
      case Failure(ex: EServiceNotFound)           => notFound(ex, logMessage)
      case Failure(ex: EServiceDescriptorNotFound) => notFound(ex, logMessage)
      case Failure(ex)                             => internalServerError(ex, logMessage)
    }

  def deleteEServiceDocumentByIdResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                              => success(s)
      case Failure(ex: OperationForbidden.type)    => forbidden(ex, logMessage)
      case Failure(ex: EServiceNotFound)           => notFound(ex, logMessage)
      case Failure(ex: DescriptorDocumentNotFound) => notFound(ex, logMessage)
      case Failure(ex)                             => internalServerError(ex, logMessage)
    }

  def updateEServiceDocumentByIdResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                              => success(s)
      case Failure(ex: OperationForbidden.type)    => forbidden(ex, logMessage)
      case Failure(ex: EServiceNotFound)           => notFound(ex, logMessage)
      case Failure(ex: DescriptorDocumentNotFound) => notFound(ex, logMessage)
      case Failure(ex)                             => internalServerError(ex, logMessage)
    }

  def getEServiceDocumentByIdResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                              => success(s)
      case Failure(ex: DescriptorDocumentNotFound) => notFound(ex, logMessage)
      case Failure(ex: ContentTypeParsingError)    => badRequest(ex, logMessage)
      case Failure(ex)                             => internalServerError(ex, logMessage)
    }

}
