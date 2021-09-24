package it.pagopa.pdnd.interop.uservice.catalogprocess.api.impl

import it.pagopa.pdnd.interop.uservice.catalogmanagement.client
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.{
  EServiceSeedEnums,
  UpdateEServiceDescriptorSeedEnums,
  UpdateEServiceSeedEnums
}
import it.pagopa.pdnd.interop.uservice.catalogprocess.model._
import it.pagopa.pdnd.interop.uservice.{attributeregistrymanagement, catalogmanagement, partymanagement}

import scala.concurrent.Future
import scala.util.{Failure, Try}

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
      technology = eservice.technology,
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
      status = descriptor.status.toString,
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

  def convertToClientEServiceSeed(eServiceSeed: EServiceSeed): Future[client.model.EServiceSeed] = {
    val converted: Try[EServiceSeedEnums.Technology.Value] = Try(
      EServiceSeedEnums.Technology.withName(eServiceSeed.technology)
    )
    Future.fromTry {
      converted
        .map(seedTechnology =>
          client.model.EServiceSeed(
            producerId = eServiceSeed.producerId,
            name = eServiceSeed.name,
            description = eServiceSeed.description,
            technology = seedTechnology,
            attributes = convertToCatalogClientAttributes(eServiceSeed.attributes)
          )
        )
        .recoverWith { _ =>
          Failure[client.model.EServiceSeed](
            new RuntimeException(
              s"Unknown Technology ${eServiceSeed.technology}. Allowed values: [${UpdateEServiceSeedEnums.Technology.values.map(_.toString).mkString(",")}]"
            )
          )
        }
    }

  }

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

  def convertToClientUpdateEServiceSeed(eServiceSeed: UpdateEServiceSeed): Future[client.model.UpdateEServiceSeed] = {

    val converted: Try[UpdateEServiceSeedEnums.Technology.Value] = Try(
      UpdateEServiceSeedEnums.Technology.withName(eServiceSeed.technology)
    )

    Future.fromTry {
      converted
        .map(seedTechnology =>
          client.model.UpdateEServiceSeed(
            name = eServiceSeed.name,
            description = eServiceSeed.description,
            technology = seedTechnology,
            attributes = convertToCatalogClientAttributes(eServiceSeed.attributes)
          )
        )
        .recoverWith { _ =>
          Failure[client.model.UpdateEServiceSeed](
            new RuntimeException(
              s"Unknown Technology ${eServiceSeed.technology}. Allowed values: [${UpdateEServiceSeedEnums.Technology.values.map(_.toString).mkString(",")}]"
            )
          )
        }
    }

  }

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
        status = UpdateEServiceDescriptorSeedEnums.Status.Draft
      )
    )
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
