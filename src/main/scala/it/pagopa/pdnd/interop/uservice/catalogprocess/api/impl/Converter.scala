package it.pagopa.pdnd.interop.uservice.catalogprocess.api.impl

import it.pagopa.pdnd.interop.uservice.catalogmanagement.client
import it.pagopa.pdnd.interop.uservice.catalogprocess.model._
import it.pagopa.pdnd.interop.uservice.{attributeregistrymanagement, catalogmanagement, partymanagement}

import scala.concurrent.Future

@SuppressWarnings(Array("org.wartremover.warts.ToString"))
object Converter {

  private final case class AttributeDetails(name: String, description: String)

  def convertToApiEservice(
    eservice: catalogmanagement.client.model.EService,
    organization: partymanagement.client.model.Organization,
    attributes: Seq[attributeregistrymanagement.client.model.Attribute]
  ): EService =
    EService(
      id = eservice.id,
      producer = Organization(id = eservice.producerId, name = organization.description),
      name = eservice.name,
      description = eservice.description,
      technology = convertToApiTechnology(eservice.technology),
      attributes = convertToApiAttributes(eservice.attributes, attributes),
      descriptors = eservice.descriptors.map(convertToApiDescriptor)
    )

  def convertToApiDescriptor(descriptor: catalogmanagement.client.model.EServiceDescriptor): EServiceDescriptor = {
    EServiceDescriptor(
      id = descriptor.id,
      version = descriptor.version,
      description = descriptor.description,
      interface = descriptor.interface.map(convertToApiEserviceDoc),
      docs = descriptor.docs.map(convertToApiEserviceDoc),
      status = convertToApiDescriptorStatus(descriptor.status),
      audience = descriptor.audience,
      voucherLifespan = descriptor.voucherLifespan
    )
  }

  def convertToApiEserviceDoc(document: catalogmanagement.client.model.EServiceDoc): EServiceDoc = {
    EServiceDoc(
      id = document.id,
      name = document.name,
      contentType = document.contentType,
      description = document.description
    )
  }

  private def convertToApiAttributes(
    currentAttributes: catalogmanagement.client.model.Attributes,
    attributes: Seq[attributeregistrymanagement.client.model.Attribute]
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
  )(attribute: catalogmanagement.client.model.Attribute): Attribute = {
    Attribute(
      single = attribute.single.map(convertToApiAttributeValue(attributeNames)),
      group = attribute.group.map(values => values.map(convertToApiAttributeValue(attributeNames)))
    )
  }

  private def convertToApiAttributeValue(
    attributeNames: Map[String, AttributeDetails]
  )(value: client.model.AttributeValue) =
    AttributeValue(
      id = value.id,
      // TODO how to manage this case? Raise an error/Default/Flat option values
      // TODO for now default value "Unknown"
      name = attributeNames.get(value.id).map(_.name).getOrElse("Unknown"),
      // TODO same here
      description = attributeNames.get(value.id).map(_.description).getOrElse("Unknown"),
      explicitAttributeVerification = value.explicitAttributeVerification
    )

  def convertToClientEServiceSeed(eServiceSeed: EServiceSeed): client.model.EServiceSeed =
    client.model.EServiceSeed(
      producerId = eServiceSeed.producerId,
      name = eServiceSeed.name,
      description = eServiceSeed.description,
      technology = convertFromApiTechnology(eServiceSeed.technology),
      attributes = convertToCatalogClientAttributes(eServiceSeed.attributes)
    )

  def convertToClientEServiceDescriptorSeed(
    descriptor: EServiceDescriptorSeed
  ): Future[client.model.EServiceDescriptorSeed] = {
    Future.successful(
      client.model.EServiceDescriptorSeed(
        description = descriptor.description,
        audience = descriptor.audience,
        voucherLifespan = descriptor.voucherLifespan
      )
    )
  }

  def convertToClientUpdateEServiceSeed(eServiceSeed: UpdateEServiceSeed): client.model.UpdateEServiceSeed =
    client.model.UpdateEServiceSeed(
      name = eServiceSeed.name,
      description = eServiceSeed.description,
      technology = convertFromApiTechnology(eServiceSeed.technology),
      attributes = convertToCatalogClientAttributes(eServiceSeed.attributes)
    )

  def convertToClientEServiceDescriptorDocumentSeed(
    seed: UpdateEServiceDescriptorDocumentSeed
  ): Future[client.model.UpdateEServiceDescriptorDocumentSeed] = {
    Future.successful(client.model.UpdateEServiceDescriptorDocumentSeed(description = seed.description))
  }

  def convertToClientUpdateEServiceDescriptorSeed(
    seed: UpdateEServiceDescriptorSeed
  ): Future[client.model.UpdateEServiceDescriptorSeed] = {
    Future.successful(
      client.model.UpdateEServiceDescriptorSeed(
        description = seed.description,
        audience = seed.audience,
        voucherLifespan = seed.voucherLifespan,
        status = catalogmanagement.client.model.DRAFT
      )
    )
  }

  def convertToApiDescriptorStatus(
    clientStatus: catalogmanagement.client.model.EServiceDescriptorStatusEnum
  ): EServiceDescriptorStatusEnum =
    clientStatus match {
      case catalogmanagement.client.model.DRAFT      => DRAFT
      case catalogmanagement.client.model.PUBLISHED  => PUBLISHED
      case catalogmanagement.client.model.DEPRECATED => DEPRECATED
      case catalogmanagement.client.model.SUSPENDED  => SUSPENDED
      case catalogmanagement.client.model.ARCHIVED   => ARCHIVED
    }

  def convertToApiTechnology(
    technology: catalogmanagement.client.model.EServiceTechnologyEnum
  ): EServiceTechnologyEnum =
    technology match {
      case catalogmanagement.client.model.REST => REST
      case catalogmanagement.client.model.SOAP => SOAP
    }

  def convertFromApiTechnology(
    technology: EServiceTechnologyEnum
  ): catalogmanagement.client.model.EServiceTechnologyEnum =
    technology match {
      case REST => catalogmanagement.client.model.REST
      case SOAP => catalogmanagement.client.model.SOAP
    }

  private def convertToCatalogClientAttributes(seed: AttributesSeed): client.model.Attributes =
    client.model.Attributes(
      certified = seed.certified.map(convertToCatalogClientAttribute),
      declared = seed.declared.map(convertToCatalogClientAttribute),
      verified = seed.verified.map(convertToCatalogClientAttribute)
    )

  private def convertToCatalogClientAttribute(seed: AttributeSeed): client.model.Attribute =
    client.model.Attribute(
      single = seed.single.map(convertToCatalogClientAttributeValue),
      group = seed.group.map(_.map(convertToCatalogClientAttributeValue))
    )

  private def convertToCatalogClientAttributeValue(seed: AttributeValueSeed): client.model.AttributeValue =
    client.model.AttributeValue(id = seed.id, explicitAttributeVerification = seed.explicitAttributeVerification)
}
