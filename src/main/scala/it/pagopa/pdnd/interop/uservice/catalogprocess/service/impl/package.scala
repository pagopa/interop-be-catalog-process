package it.pagopa.pdnd.interop.uservice.catalogprocess.service

import it.pagopa.pdnd.interop.uservice.catalogmanagement.client
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.{EServiceSeedEnums, UpdateEServiceSeedEnums}
import it.pagopa.pdnd.interopuservice.catalogprocess.model.{
  Attribute,
  AttributeValue,
  Attributes,
  EService,
  EServiceDescriptor,
  EServiceDescriptorSeed,
  EServiceDoc,
  EServiceSeed,
  UpdateEServiceDescriptorDocumentSeed,
  UpdateEServiceSeed
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
      status = clientDescriptor.status.toString,
      audience = clientDescriptor.audience,
      voucherLifespan = clientDescriptor.voucherLifespan
    )

  def eServiceFromCatalogClient(clientEService: client.model.EService): EService =
    EService(
      id = clientEService.id,
      producerId = clientEService.producerId,
      name = clientEService.name,
      description = clientEService.description,
      technology = clientEService.technology,
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
      technology = seedTechnology,
      attributes = attributesToCatalogClientAttributes(eServiceSeed.attributes)
    )
  }

  @SuppressWarnings(Array("org.wartremover.warts.ToString"))
  def eServiceDescriptorSeedToCatalogClientSeed(
    descriptor: EServiceDescriptorSeed
  ): Either[Throwable, client.model.EServiceDescriptorSeed] = {
    Right[Throwable, client.model.EServiceDescriptorSeed](
      client.model.EServiceDescriptorSeed(
        description = descriptor.description,
        audience = descriptor.audience,
        voucherLifespan = descriptor.voucherLifespan
      )
    )
  }

  @SuppressWarnings(Array("org.wartremover.warts.ToString"))
  def updateEServiceSeedToCatalogClientSeed(
    eServiceSeed: UpdateEServiceSeed
  ): Either[Throwable, client.model.UpdateEServiceSeed] = {
    for {
      seedTechnology <- Try(UpdateEServiceSeedEnums.Technology.withName(eServiceSeed.technology)).toEither.left.map(_ =>
        new RuntimeException(
          s"Unknown Technology ${eServiceSeed.technology}. Allowed values: [${UpdateEServiceSeedEnums.Technology.values.map(_.toString).mkString(",")}]"
        )
      )
    } yield client.model.UpdateEServiceSeed(
      name = eServiceSeed.name,
      description = eServiceSeed.description,
      technology = seedTechnology,
      attributes = attributesToCatalogClientAttributes(eServiceSeed.attributes)
    )
  }

  @SuppressWarnings(Array("org.wartremover.warts.ToString"))
  def updateEServiceDescriptorDocumentSeedToCatalogClientSeed(
    seed: UpdateEServiceDescriptorDocumentSeed
  ): client.model.UpdateEServiceDescriptorDocumentSeed = {
    client.model.UpdateEServiceDescriptorDocumentSeed(description = seed.description)
  }
}
