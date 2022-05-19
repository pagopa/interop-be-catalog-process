package it.pagopa.interop.catalogprocess.api.impl

import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagementDependency}
import it.pagopa.interop.selfcare.partymanagement.client.{model => PartyManagementDependency}
import it.pagopa.interop.attributeregistrymanagement.client.{model => AttributeManagementDependency}
import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagementDependency}
import it.pagopa.interop.catalogprocess.model._

import scala.concurrent.Future

object Converter {

  private final case class AttributeDetails(name: String, description: String)

  def convertToApiEservice(
    eservice: CatalogManagementDependency.EService,
    institution: PartyManagementDependency.Institution,
    attributes: Seq[AttributeManagementDependency.Attribute]
  ): EService = EService(
    id = eservice.id,
    producer = Organization(id = eservice.producerId, name = institution.description),
    name = eservice.name,
    description = eservice.description,
    technology = convertToApiTechnology(eservice.technology),
    attributes = convertToApiAttributes(eservice.attributes, attributes),
    descriptors = eservice.descriptors.map(convertToApiDescriptor)
  )

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
      dailyCallsTotal = descriptor.dailyCallsTotal
    )

  def convertToApiEserviceDoc(document: CatalogManagementDependency.EServiceDoc): EServiceDoc = EServiceDoc(
    id = document.id,
    name = document.name,
    contentType = document.contentType,
    prettyName = document.prettyName
  )

  def convertToApiAgreementState(state: AgreementState): AgreementManagementDependency.AgreementState =
    state match {
      case AgreementState.ACTIVE    => AgreementManagementDependency.AgreementState.ACTIVE
      case AgreementState.INACTIVE  => AgreementManagementDependency.AgreementState.INACTIVE
      case AgreementState.PENDING   => AgreementManagementDependency.AgreementState.PENDING
      case AgreementState.SUSPENDED => AgreementManagementDependency.AgreementState.SUSPENDED
    }

  private def convertToApiAttributes(
    currentAttributes: CatalogManagementDependency.Attributes,
    attributes: Seq[AttributeManagementDependency.Attribute]
  ): Attributes = {
    val attributeNames: Map[String, AttributeDetails] =
      attributes.map(attr => attr.id -> AttributeDetails(attr.name, attr.description)).toMap

    Attributes(
      certified = currentAttributes.certified.map(convertToApiAttribute(attributeNames)),
      declared = currentAttributes.declared.map(convertToApiAttribute(attributeNames)),
      verified = currentAttributes.verified.map(convertToApiAttribute(attributeNames))
    )
  }

  private def convertToApiAttribute(
    attributeNames: Map[String, AttributeDetails]
  )(attribute: CatalogManagementDependency.Attribute): Attribute = Attribute(
    single = attribute.single.map(convertToApiAttributeValue(attributeNames)),
    group = attribute.group.map(values => values.map(convertToApiAttributeValue(attributeNames)))
  )

  private def convertToApiAttributeValue(
    attributeNames: Map[String, AttributeDetails]
  )(value: CatalogManagementDependency.AttributeValue) = AttributeValue(
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
  ): Future[CatalogManagementDependency.EServiceDescriptorSeed] = Future.successful(
    CatalogManagementDependency.EServiceDescriptorSeed(
      description = descriptor.description,
      audience = descriptor.audience,
      voucherLifespan = descriptor.voucherLifespan,
      dailyCallsPerConsumer = descriptor.dailyCallsPerConsumer,
      dailyCallsTotal = descriptor.dailyCallsTotal
    )
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
  ): Future[CatalogManagementDependency.UpdateEServiceDescriptorDocumentSeed] =
    Future.successful(CatalogManagementDependency.UpdateEServiceDescriptorDocumentSeed(prettyName = seed.prettyName))

  def convertToClientUpdateEServiceDescriptorSeed(
    seed: UpdateEServiceDescriptorSeed
  ): Future[CatalogManagementDependency.UpdateEServiceDescriptorSeed] = Future.successful(
    CatalogManagementDependency.UpdateEServiceDescriptorSeed(
      description = seed.description,
      audience = seed.audience,
      voucherLifespan = seed.voucherLifespan,
      dailyCallsPerConsumer = seed.dailyCallsPerConsumer,
      dailyCallsTotal = seed.dailyCallsTotal,
      state = CatalogManagementDependency.EServiceDescriptorState.DRAFT
    )
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
