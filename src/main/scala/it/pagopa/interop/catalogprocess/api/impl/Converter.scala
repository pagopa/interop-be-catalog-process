package it.pagopa.interop.catalogprocess.api.impl

import cats.implicits.catsSyntaxOptionId
import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagementDependency}
import it.pagopa.interop.attributeregistrymanagement.client.{model => AttributeManagementDependency}
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagementDependency}
import it.pagopa.interop.catalogmanagement.{model => readmodel}
import it.pagopa.interop.catalogprocess.model._
import it.pagopa.interop.tenantmanagement.client.{model => TenantManagementDependency}

import java.util.UUID

object Converter {

  private final case class AttributeDetails(name: String, description: String)

  def convertToApiOldEservice(
    eservice: CatalogManagementDependency.EService,
    tenant: TenantManagementDependency.Tenant,
    attributes: Seq[AttributeManagementDependency.Attribute]
  ): OldEService = OldEService(
    id = eservice.id,
    producer = Organization(id = eservice.producerId, name = tenant.name),
    name = eservice.name,
    description = eservice.description,
    technology = convertToApiTechnology(eservice.technology),
    attributes = convertToApiOldAttributes(eservice.attributes, attributes),
    descriptors = eservice.descriptors.map(convertToApiDescriptor)
  )

  def convertToApiEService(eService: CatalogManagementDependency.EService): EService = EService(
    id = eService.id,
    producerId = eService.producerId,
    name = eService.name,
    description = eService.description,
    technology = convertToApiTechnology(eService.technology),
    attributes = convertToApiAttributes(eService.attributes),
    descriptors = eService.descriptors.map(convertToApiDescriptor)
  )
  def convertToApiEService(eService: readmodel.CatalogItem): EService                = EService(
    id = eService.id,
    producerId = eService.producerId,
    name = eService.name,
    description = eService.description,
    technology = convertToApiTechnology(eService.technology),
    attributes = convertToApiAttributes(eService.attributes),
    descriptors = eService.descriptors.map(convertToApiDescriptor)
  )

  private def convertToApiAttributes(attributes: CatalogManagementDependency.Attributes): Attributes =
    Attributes(
      certified = attributes.certified.map(convertToApiAttribute),
      declared = attributes.declared.map(convertToApiAttribute),
      verified = attributes.verified.map(convertToApiAttribute)
    )

  private def convertToApiAttributes(attributes: readmodel.CatalogAttributes): Attributes =
    Attributes(
      certified = attributes.certified.map(convertToApiAttribute),
      declared = attributes.declared.map(convertToApiAttribute),
      verified = attributes.verified.map(convertToApiAttribute)
    )

  private def convertToApiAttribute(attribute: CatalogManagementDependency.Attribute): Attribute = {
    Attribute(
      single = attribute.single.map(attr => AttributeValue(attr.id, attr.explicitAttributeVerification)),
      group =
        attribute.group.map(attrs => attrs.map(attr => AttributeValue(attr.id, attr.explicitAttributeVerification)))
    )

  }

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
    EServiceDescriptor(
      id = descriptor.id,
      version = descriptor.version,
      description = descriptor.description,
      interface = descriptor.interface.map(convertToApiEServiceDoc),
      docs = descriptor.docs.map(convertToApiEServiceDoc),
      state = convertToApiDescriptorState(descriptor.state),
      audience = descriptor.audience,
      voucherLifespan = descriptor.voucherLifespan,
      dailyCallsPerConsumer = descriptor.dailyCallsPerConsumer,
      dailyCallsTotal = descriptor.dailyCallsTotal,
      agreementApprovalPolicy = convertToApiAgreementApprovalPolicy(
        descriptor.agreementApprovalPolicy.getOrElse(readmodel.PersistentAgreementApprovalPolicy.default)
      ),
      serverUrls = descriptor.serverUrls
    )

  def convertToApiEServiceDoc(document: readmodel.CatalogDocument): EServiceDoc = EServiceDoc(
    id = document.id,
    name = document.name,
    contentType = document.contentType,
    prettyName = document.prettyName
  )

  def convertToApiDescriptorState(clientStatus: readmodel.CatalogDescriptorState): EServiceDescriptorState =
    clientStatus match {
      case readmodel.Draft      => EServiceDescriptorState.DRAFT
      case readmodel.Published  => EServiceDescriptorState.PUBLISHED
      case readmodel.Deprecated => EServiceDescriptorState.DEPRECATED
      case readmodel.Suspended  => EServiceDescriptorState.SUSPENDED
      case readmodel.Archived   => EServiceDescriptorState.ARCHIVED
    }

  def convertFromApiDescriptorState(state: EServiceDescriptorState): readmodel.CatalogDescriptorState =
    state match {
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
    EServiceDescriptor(
      id = descriptor.id,
      version = descriptor.version,
      description = descriptor.description,
      interface = descriptor.interface.map(convertToApiEserviceDoc),
      docs = descriptor.docs.map(convertToApiEserviceDoc),
      state = convertToApiDescriptorState(descriptor.state),
      audience = descriptor.audience,
      voucherLifespan = descriptor.voucherLifespan,
      dailyCallsPerConsumer = descriptor.dailyCallsPerConsumer,
      dailyCallsTotal = descriptor.dailyCallsTotal,
      agreementApprovalPolicy = convertToApiAgreementApprovalPolicy(descriptor.agreementApprovalPolicy),
      serverUrls = descriptor.serverUrls
    )

  def convertToApiEserviceDoc(document: CatalogManagementDependency.EServiceDoc): EServiceDoc = EServiceDoc(
    id = document.id,
    name = document.name,
    contentType = document.contentType,
    prettyName = document.prettyName
  )

  def convertToApiAgreementState(state: AgreementState): AgreementManagementDependency.AgreementState = state match {
    case AgreementState.DRAFT                        => AgreementManagementDependency.AgreementState.DRAFT
    case AgreementState.PENDING                      => AgreementManagementDependency.AgreementState.PENDING
    case AgreementState.ACTIVE                       => AgreementManagementDependency.AgreementState.ACTIVE
    case AgreementState.SUSPENDED                    => AgreementManagementDependency.AgreementState.SUSPENDED
    case AgreementState.ARCHIVED                     => AgreementManagementDependency.AgreementState.ARCHIVED
    case AgreementState.MISSING_CERTIFIED_ATTRIBUTES =>
      AgreementManagementDependency.AgreementState.MISSING_CERTIFIED_ATTRIBUTES
    case AgreementState.REJECTED                     => AgreementManagementDependency.AgreementState.REJECTED
  }

  def convertFromApiAgreementState(state: AgreementManagementDependency.AgreementState): AgreementState = state match {
    case AgreementManagementDependency.AgreementState.DRAFT                        => AgreementState.DRAFT
    case AgreementManagementDependency.AgreementState.PENDING                      => AgreementState.PENDING
    case AgreementManagementDependency.AgreementState.ACTIVE                       => AgreementState.ACTIVE
    case AgreementManagementDependency.AgreementState.SUSPENDED                    => AgreementState.SUSPENDED
    case AgreementManagementDependency.AgreementState.ARCHIVED                     => AgreementState.ARCHIVED
    case AgreementManagementDependency.AgreementState.MISSING_CERTIFIED_ATTRIBUTES =>
      AgreementState.MISSING_CERTIFIED_ATTRIBUTES
    case AgreementManagementDependency.AgreementState.REJECTED                     => AgreementState.REJECTED

  }

  private def convertToApiOldAttributes(
    currentAttributes: CatalogManagementDependency.Attributes,
    attributes: Seq[AttributeManagementDependency.Attribute]
  ): OldAttributes = {
    val attributeNames: Map[UUID, AttributeDetails] =
      attributes.map(attr => attr.id -> AttributeDetails(attr.name, attr.description)).toMap

    OldAttributes(
      certified = currentAttributes.certified.map(convertToApiOldAttribute(attributeNames)),
      declared = currentAttributes.declared.map(convertToApiOldAttribute(attributeNames)),
      verified = currentAttributes.verified.map(convertToApiOldAttribute(attributeNames))
    )
  }

  private def convertToApiOldAttribute(
    attributeNames: Map[UUID, AttributeDetails]
  )(attribute: CatalogManagementDependency.Attribute): OldAttribute = OldAttribute(
    single = attribute.single.map(convertToApiOldAttributeValue(attributeNames)),
    group = attribute.group.map(values => values.map(convertToApiOldAttributeValue(attributeNames)))
  )

  private def convertToApiOldAttributeValue(
    attributeNames: Map[UUID, AttributeDetails]
  )(value: CatalogManagementDependency.AttributeValue) = OldAttributeValue(
    id = value.id,
    // TODO how to manage this case? Raise an error/Default/Flat option values
    // TODO for now default value "Unknown"
    name = attributeNames.get(value.id).map(_.name).getOrElse("Unknown"),
    // TODO same here
    description = attributeNames.get(value.id).map(_.description).getOrElse("Unknown"),
    explicitAttributeVerification = value.explicitAttributeVerification
  )

  def convertToClientEServiceSeed(eServiceSeed: EServiceSeed): CatalogManagementDependency.EServiceSeed =
    CatalogManagementDependency.EServiceSeed(
      producerId = eServiceSeed.producerId,
      name = eServiceSeed.name,
      description = eServiceSeed.description,
      technology = convertFromApiTechnology(eServiceSeed.technology),
      attributes = convertToCatalogClientAttributes(eServiceSeed.attributes)
    )

  def convertToClientEServiceDescriptorSeed(
    descriptor: EServiceDescriptorSeed
  ): CatalogManagementDependency.EServiceDescriptorSeed =
    CatalogManagementDependency.EServiceDescriptorSeed(
      description = descriptor.description,
      audience = descriptor.audience,
      voucherLifespan = descriptor.voucherLifespan,
      dailyCallsPerConsumer = descriptor.dailyCallsPerConsumer,
      dailyCallsTotal = descriptor.dailyCallsTotal,
      agreementApprovalPolicy = convertFromApiAgreementApprovalPolicy(descriptor.agreementApprovalPolicy)
    )

  def convertToClientUpdateEServiceSeed(
    eServiceSeed: UpdateEServiceSeed
  ): CatalogManagementDependency.UpdateEServiceSeed = CatalogManagementDependency.UpdateEServiceSeed(
    name = eServiceSeed.name,
    description = eServiceSeed.description,
    technology = convertFromApiTechnology(eServiceSeed.technology),
    attributes = convertToCatalogClientAttributes(eServiceSeed.attributes)
  )

  def convertToClientEServiceDescriptorDocumentSeed(
    seed: UpdateEServiceDescriptorDocumentSeed
  ): CatalogManagementDependency.UpdateEServiceDescriptorDocumentSeed =
    CatalogManagementDependency.UpdateEServiceDescriptorDocumentSeed(prettyName = seed.prettyName)

  def convertToClientUpdateEServiceDescriptorSeed(
    seed: UpdateEServiceDescriptorSeed
  ): CatalogManagementDependency.UpdateEServiceDescriptorSeed =
    CatalogManagementDependency.UpdateEServiceDescriptorSeed(
      description = seed.description,
      audience = seed.audience,
      voucherLifespan = seed.voucherLifespan,
      dailyCallsPerConsumer = seed.dailyCallsPerConsumer,
      dailyCallsTotal = seed.dailyCallsTotal,
      state = CatalogManagementDependency.EServiceDescriptorState.DRAFT,
      agreementApprovalPolicy = convertFromApiAgreementApprovalPolicy(seed.agreementApprovalPolicy)
    )

  def convertToApiDescriptorState(
    clientStatus: CatalogManagementDependency.EServiceDescriptorState
  ): EServiceDescriptorState = clientStatus match {
    case CatalogManagementDependency.EServiceDescriptorState.DRAFT      => EServiceDescriptorState.DRAFT
    case CatalogManagementDependency.EServiceDescriptorState.PUBLISHED  => EServiceDescriptorState.PUBLISHED
    case CatalogManagementDependency.EServiceDescriptorState.DEPRECATED => EServiceDescriptorState.DEPRECATED
    case CatalogManagementDependency.EServiceDescriptorState.SUSPENDED  => EServiceDescriptorState.SUSPENDED
    case CatalogManagementDependency.EServiceDescriptorState.ARCHIVED   => EServiceDescriptorState.ARCHIVED
  }

  def convertToApiAgreementApprovalPolicy(
    policy: CatalogManagementDependency.AgreementApprovalPolicy
  ): AgreementApprovalPolicy = policy match {
    case CatalogManagementDependency.AgreementApprovalPolicy.AUTOMATIC => AgreementApprovalPolicy.AUTOMATIC
    case CatalogManagementDependency.AgreementApprovalPolicy.MANUAL    => AgreementApprovalPolicy.MANUAL
  }

  def convertFromApiAgreementApprovalPolicy(
    policy: AgreementApprovalPolicy
  ): CatalogManagementDependency.AgreementApprovalPolicy =
    policy match {
      case AgreementApprovalPolicy.AUTOMATIC => CatalogManagementDependency.AgreementApprovalPolicy.AUTOMATIC
      case AgreementApprovalPolicy.MANUAL    => CatalogManagementDependency.AgreementApprovalPolicy.MANUAL
    }

  def convertToApiTechnology(technology: CatalogManagementDependency.EServiceTechnology): EServiceTechnology =
    technology match {
      case CatalogManagementDependency.EServiceTechnology.REST => EServiceTechnology.REST
      case CatalogManagementDependency.EServiceTechnology.SOAP => EServiceTechnology.SOAP
    }

  def convertFromApiTechnology(technology: EServiceTechnology): CatalogManagementDependency.EServiceTechnology =
    technology match {
      case EServiceTechnology.REST => CatalogManagementDependency.EServiceTechnology.REST
      case EServiceTechnology.SOAP => CatalogManagementDependency.EServiceTechnology.SOAP
    }

  private def convertToCatalogClientAttributes(seed: AttributesSeed): CatalogManagementDependency.Attributes =
    CatalogManagementDependency.Attributes(
      certified = seed.certified.map(convertToCatalogClientAttribute),
      declared = seed.declared.map(convertToCatalogClientAttribute),
      verified = seed.verified.map(convertToCatalogClientAttribute)
    )

  private def convertToCatalogClientAttribute(seed: AttributeSeed): CatalogManagementDependency.Attribute =
    CatalogManagementDependency.Attribute(
      single = seed.single.map(convertToCatalogClientAttributeValue),
      group = seed.group.map(_.map(convertToCatalogClientAttributeValue))
    )

  private def convertToCatalogClientAttributeValue(
    seed: AttributeValueSeed
  ): CatalogManagementDependency.AttributeValue = CatalogManagementDependency.AttributeValue(
    id = seed.id,
    explicitAttributeVerification = seed.explicitAttributeVerification
  )
}
