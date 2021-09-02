package it.pagopa.pdnd.interop.uservice.catalogprocess.service

import it.pagopa.pdnd.interop.uservice.catalogmanagement.client
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.EServiceSeedEnums
import it.pagopa.pdnd.interopuservice.catalogprocess.model.{
  Attribute,
  AttributeValue,
  Attributes,
  EService,
  EServiceDescriptor,
  EServiceDoc,
  EServiceSeed
}

import scala.util.Try

package object impl {

  def attributesFromCatalogClientAttributes(clientAttributes: client.model.Attributes): Attributes =
    Attributes(
      certified = clientAttributes.certified.map(attributeFromCatalogClientAttribute),
      declared = clientAttributes.declared.map(attributeFromCatalogClientAttribute),
      verified = clientAttributes.verified.map(attributeFromCatalogClientAttribute)
    )

  def attributeFromCatalogClientAttribute(clientAttribute: client.model.Attribute): Attribute =
    Attribute(
      single = clientAttribute.single.map(attributeValueFromCatalogClientAttributeValue),
      group = clientAttribute.group.map(_.map(attributeValueFromCatalogClientAttributeValue))
    )

  def attributeValueFromCatalogClientAttributeValue(clientAttributeValue: client.model.AttributeValue): AttributeValue =
    AttributeValue(
      id = clientAttributeValue.id,
      explicitAttributeVerification = clientAttributeValue.explicitAttributeVerification
    )

  def docFromCatalogClientDoc(clientDoc: client.model.EServiceDoc): EServiceDoc =
    EServiceDoc(
      id = clientDoc.id,
      name = clientDoc.name,
      contentType = clientDoc.contentType,
      description = clientDoc.description
    )

  @SuppressWarnings(Array("org.wartremover.warts.ToString"))
  def descriptorFromCatalogClientDescriptor(clientDescriptor: client.model.EServiceDescriptor): EServiceDescriptor =
    EServiceDescriptor(
      id = clientDescriptor.id,
      version = clientDescriptor.version,
      description = clientDescriptor.description,
      interface = clientDescriptor.interface.map(docFromCatalogClientDoc),
      docs = clientDescriptor.docs.map(docFromCatalogClientDoc),
      status = clientDescriptor.status.toString
    )

  def eServiceFromCatalogClient(clientEService: client.model.EService): EService =
    EService(
      id = clientEService.id,
      producerId = clientEService.producerId,
      name = clientEService.name,
      description = clientEService.description,
      audience = clientEService.audience,
      technology = clientEService.technology,
      voucherLifespan = clientEService.voucherLifespan,
      attributes = attributesFromCatalogClientAttributes(clientEService.attributes),
      descriptors = clientEService.descriptors.map(descriptorFromCatalogClientDescriptor)
    )

  def attributesToCatalogClientAttributes(attributes: Attributes): client.model.Attributes =
    client.model.Attributes(
      certified = attributes.certified.map(attributeToCatalogClientAttribute),
      declared = attributes.declared.map(attributeToCatalogClientAttribute),
      verified = attributes.verified.map(attributeToCatalogClientAttribute)
    )

  def attributeToCatalogClientAttribute(attribute: Attribute): client.model.Attribute =
    client.model.Attribute(
      single = attribute.single.map(attributeValueToCatalogClientAttributeValue),
      group = attribute.group.map(_.map(attributeValueToCatalogClientAttributeValue))
    )

  def attributeValueToCatalogClientAttributeValue(attributeValue: AttributeValue): client.model.AttributeValue =
    client.model.AttributeValue(
      id = attributeValue.id,
      explicitAttributeVerification = attributeValue.explicitAttributeVerification
    )

  @SuppressWarnings(Array("org.wartremover.warts.ToString"))
  def eServiceSeedToCatalogClientSeed(eServiceSeed: EServiceSeed): Either[Throwable, client.model.EServiceSeed] = {
    for {
      seedTechnology <- Try(EServiceSeedEnums.Technology.withName(eServiceSeed.technology)).toEither.left.map(_ =>
        new RuntimeException(
          s"Unknown Technology ${eServiceSeed.technology}. Allowed values: [${EServiceSeedEnums.Technology.values.map(_.toString).mkString(",")}]"
        )
      )
    } yield client.model.EServiceSeed(
      producerId = eServiceSeed.producerId,
      name = eServiceSeed.name,
      description = eServiceSeed.description,
      audience = eServiceSeed.audience,
      technology = seedTechnology,
      voucherLifespan = eServiceSeed.voucherLifespan,
      attributes = attributesToCatalogClientAttributes(eServiceSeed.attributes)
    )
  }
}
