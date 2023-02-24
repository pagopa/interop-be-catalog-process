package it.pagopa.interop.catalogprocess.api.impl

import cats.implicits.catsSyntaxOptionId
import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagementDependency}
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagementDependency}
import it.pagopa.interop.catalogmanagement.{model => readmodel}
import it.pagopa.interop.catalogprocess.model._
import io.scalaland.chimney.dsl._

import java.util.UUID

object Converter {

  private final case class AttributeDetails(name: String, description: String)

  def convertToApiEService(eService: CatalogManagementDependency.EService): EService = eService
    .into[EService]
    .withFieldComputed(_.technology, e => convertToApiTechnology(e.technology))
    .withFieldComputed(_.attributes, e => convertToApiAttributes(e.attributes))
    .withFieldComputed(_.descriptors, _.descriptors.map(convertToApiDescriptor))
    .transform

  def convertToApiEService(eService: readmodel.CatalogItem): EService = eService
    .into[EService]
    .withFieldComputed(_.technology, e => convertToApiTechnology(e.technology))
    .withFieldComputed(_.attributes, e => convertToApiAttributes(e.attributes))
    .withFieldComputed(_.descriptors, _.descriptors.map(convertToApiDescriptor))
    .transform

  private def convertToApiAttributes(attributes: CatalogManagementDependency.Attributes): Attributes = Attributes(
    certified = attributes.certified.map(convertToApiAttribute),
    declared = attributes.declared.map(convertToApiAttribute),
    verified = attributes.verified.map(convertToApiAttribute)
  )

  private def convertToApiAttributes(attributes: readmodel.CatalogAttributes): Attributes = Attributes(
    certified = attributes.certified.map(convertToApiAttribute),
    declared = attributes.declared.map(convertToApiAttribute),
    verified = attributes.verified.map(convertToApiAttribute)
  )

  private def convertToApiAttribute(attribute: CatalogManagementDependency.Attribute): Attribute = Attribute(
    single = attribute.single.map(attr => AttributeValue(attr.id, attr.explicitAttributeVerification)),
    group = attribute.group.map(attrs => attrs.map(attr => AttributeValue(attr.id, attr.explicitAttributeVerification)))
  )

  private def convertToApiAttribute(attribute: readmodel.CatalogAttribute): Attribute = attribute match {
    case a: readmodel.SingleAttribute =>
      Attribute(single = AttributeValue(a.id.id, a.id.explicitAttributeVerification).some)
    case a: readmodel.GroupAttribute  =>
      Attribute(group = a.ids.map(attr => AttributeValue(attr.id, attr.explicitAttributeVerification)).some)
  }

  def convertToApiTechnology(technology: readmodel.CatalogItemTechnology): EServiceTechnology = technology match {
    case readmodel.Rest => EServiceTechnology.REST
    case readmodel.Soap => EServiceTechnology.SOAP
  }

  def convertToApiDescriptor(descriptor: readmodel.CatalogDescriptor): EServiceDescriptor =
    descriptor
      .into[EServiceDescriptor]
      .withFieldComputed(_.interface, _.interface.map(convertToApiEServiceDoc))
      .withFieldComputed(_.docs, _.docs.map(convertToApiEServiceDoc))
      .withFieldComputed(_.state, d => convertToApiDescriptorState(d.state))
      .withFieldComputed(
        _.agreementApprovalPolicy,
        d =>
          convertToApiAgreementApprovalPolicy(
            d.agreementApprovalPolicy.getOrElse(readmodel.PersistentAgreementApprovalPolicy.default)
          )
      )
      .transform

  def convertToApiEServiceDoc(document: readmodel.CatalogDocument): EServiceDoc = document.transformInto[EServiceDoc]

  def convertToApiDescriptorState(clientStatus: readmodel.CatalogDescriptorState): EServiceDescriptorState =
    clientStatus match {
      case readmodel.Draft      => EServiceDescriptorState.DRAFT
      case readmodel.Published  => EServiceDescriptorState.PUBLISHED
      case readmodel.Deprecated => EServiceDescriptorState.DEPRECATED
      case readmodel.Suspended  => EServiceDescriptorState.SUSPENDED
      case readmodel.Archived   => EServiceDescriptorState.ARCHIVED
    }

  def convertFromApiDescriptorState(state: EServiceDescriptorState): readmodel.CatalogDescriptorState = state match {
    case EServiceDescriptorState.DRAFT      => readmodel.Draft
    case EServiceDescriptorState.PUBLISHED  => readmodel.Published
    case EServiceDescriptorState.DEPRECATED => readmodel.Deprecated
    case EServiceDescriptorState.SUSPENDED  => readmodel.Suspended
    case EServiceDescriptorState.ARCHIVED   => readmodel.Archived
  }

  def convertToApiAgreementApprovalPolicy(
    policy: readmodel.PersistentAgreementApprovalPolicy
  ): AgreementApprovalPolicy = policy match {
    case readmodel.Automatic => AgreementApprovalPolicy.AUTOMATIC
    case readmodel.Manual    => AgreementApprovalPolicy.MANUAL
  }

  def convertToApiDescriptor(descriptor: CatalogManagementDependency.EServiceDescriptor): EServiceDescriptor =
    descriptor
      .into[EServiceDescriptor]
      .withFieldComputed(_.interface, _.interface.map(convertToApiEserviceDoc))
      .withFieldComputed(_.docs, _.docs.map(convertToApiEserviceDoc))
      .withFieldComputed(_.state, d => convertToApiDescriptorState(d.state))
      .withFieldComputed(_.agreementApprovalPolicy, d => convertToApiAgreementApprovalPolicy(d.agreementApprovalPolicy))
      .transform

  def convertToApiEserviceDoc(document: CatalogManagementDependency.EServiceDoc): EServiceDoc =
    document.transformInto[EServiceDoc]

  def convertToApiAgreementState(state: AgreementState): AgreementManagementDependency.AgreementState =
    state.transformInto[AgreementManagementDependency.AgreementState]

  def convertFromApiAgreementState(state: AgreementManagementDependency.AgreementState): AgreementState =
    state.transformInto[AgreementState]

  def convertToClientEServiceSeed(
    eServiceSeed: EServiceSeed,
    producerId: UUID
  ): CatalogManagementDependency.EServiceSeed = eServiceSeed
    .into[CatalogManagementDependency.EServiceSeed]
    .withFieldConst(_.producerId, producerId)
    .withFieldComputed(_.technology, e => convertFromApiTechnology(e.technology))
    .withFieldComputed(_.attributes, e => convertToCatalogClientAttributes(e.attributes))
    .transform

  def convertToClientEServiceDescriptorSeed(
    descriptor: EServiceDescriptorSeed
  ): CatalogManagementDependency.EServiceDescriptorSeed = descriptor
    .into[CatalogManagementDependency.EServiceDescriptorSeed]
    .withFieldComputed(_.agreementApprovalPolicy, d => convertFromApiAgreementApprovalPolicy(d.agreementApprovalPolicy))
    .transform

  def convertToClientUpdateEServiceSeed(
    eServiceSeed: UpdateEServiceSeed
  ): CatalogManagementDependency.UpdateEServiceSeed = eServiceSeed
    .into[CatalogManagementDependency.UpdateEServiceSeed]
    .withFieldComputed(_.technology, s => convertFromApiTechnology(s.technology))
    .withFieldComputed(_.attributes, s => convertToCatalogClientAttributes(s.attributes))
    .transform

  def convertToManagementEServiceDescriptorDocumentSeed(
    seed: CreateEServiceDescriptorDocumentSeed
  ): CatalogManagementDependency.CreateEServiceDescriptorDocumentSeed = seed
    .into[CatalogManagementDependency.CreateEServiceDescriptorDocumentSeed]
    .withFieldComputed(_.kind, s => convertFromApiEServiceDocumentKind(s.kind))
    .transform

  def convertFromApiEServiceDocumentKind(
    policy: EServiceDocumentKind
  ): CatalogManagementDependency.EServiceDocumentKind =
    policy.transformInto[CatalogManagementDependency.EServiceDocumentKind]

  def convertToClientEServiceDescriptorDocumentSeed(
    seed: UpdateEServiceDescriptorDocumentSeed
  ): CatalogManagementDependency.UpdateEServiceDescriptorDocumentSeed =
    seed.transformInto[CatalogManagementDependency.UpdateEServiceDescriptorDocumentSeed]

  def convertToClientUpdateEServiceDescriptorSeed(
    seed: UpdateEServiceDescriptorSeed
  ): CatalogManagementDependency.UpdateEServiceDescriptorSeed = seed
    .into[CatalogManagementDependency.UpdateEServiceDescriptorSeed]
    .withFieldConst(_.state, CatalogManagementDependency.EServiceDescriptorState.DRAFT)
    .withFieldComputed(_.agreementApprovalPolicy, s => convertFromApiAgreementApprovalPolicy(s.agreementApprovalPolicy))
    .transform

  def convertToApiDescriptorState(
    clientStatus: CatalogManagementDependency.EServiceDescriptorState
  ): EServiceDescriptorState = clientStatus.transformInto[EServiceDescriptorState]

  def convertToApiAgreementApprovalPolicy(
    policy: CatalogManagementDependency.AgreementApprovalPolicy
  ): AgreementApprovalPolicy = policy.transformInto[AgreementApprovalPolicy]

  def convertFromApiAgreementApprovalPolicy(
    policy: AgreementApprovalPolicy
  ): CatalogManagementDependency.AgreementApprovalPolicy =
    policy.transformInto[CatalogManagementDependency.AgreementApprovalPolicy]

  def convertToApiTechnology(technology: CatalogManagementDependency.EServiceTechnology): EServiceTechnology =
    technology.transformInto[EServiceTechnology]

  def convertFromApiTechnology(technology: EServiceTechnology): CatalogManagementDependency.EServiceTechnology =
    technology.transformInto[CatalogManagementDependency.EServiceTechnology]

  private def convertToCatalogClientAttributes(seed: AttributesSeed): CatalogManagementDependency.Attributes = seed
    .into[CatalogManagementDependency.Attributes]
    .withFieldComputed(_.certified, _.certified.map(convertToCatalogClientAttribute))
    .withFieldComputed(_.declared, _.declared.map(convertToCatalogClientAttribute))
    .withFieldComputed(_.verified, _.verified.map(convertToCatalogClientAttribute))
    .transform

  private def convertToCatalogClientAttribute(seed: AttributeSeed): CatalogManagementDependency.Attribute = seed
    .into[CatalogManagementDependency.Attribute]
    .withFieldComputed(_.single, _.single.map(convertToCatalogClientAttributeValue))
    .withFieldComputed(_.group, _.group.map(_.map(convertToCatalogClientAttributeValue)))
    .transform

  private def convertToCatalogClientAttributeValue(
    seed: AttributeValueSeed
  ): CatalogManagementDependency.AttributeValue = seed.transformInto[CatalogManagementDependency.AttributeValue]
}
