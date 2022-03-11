package it.pagopa.interop.catalogprocess

import it.pagopa.interop.agreementmanagement.client.model.Agreement
import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagementDependency}
import it.pagopa.interop.attributeregistrymanagement.client.model.{
  Attribute => AttributeRegistryManagementApiAttribute,
  AttributeKind => AttributeRegistryManagementApiAttributeKind
}
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagementDependency}
import it.pagopa.interop.catalogprocess.api.impl.Converter.convertToApiTechnology
import it.pagopa.interop.catalogprocess.model._
import it.pagopa.interop.partymanagement.client.model.{
  Attribute => PartyManagementApiAttribute,
  Organization => PartyManagementApiOrganization
}

import java.time.OffsetDateTime
import java.util.UUID

object EserviceCreationTestData {

  def getSeed(
    attributeValue1: (String, Boolean),
    attributeValue2: (String, Boolean),
    attributeValue3: (String, Boolean)
  ): CatalogManagementDependency.EServiceSeed = {
    CatalogManagementDependency.EServiceSeed(
      producerId = UUID.fromString("c54aebcc-f469-4c5a-b232-8b7003824300"),
      name = "MyService",
      description = "My Service",
      technology = CatalogManagementDependency.EServiceTechnology.REST,
      attributes = CatalogManagementDependency.Attributes(
        certified = List(
          CatalogManagementDependency
            .Attribute(
              single = Some(
                CatalogManagementDependency
                  .AttributeValue(id = attributeValue1._1, explicitAttributeVerification = attributeValue1._2)
              ),
              group = None
            )
        ),
        declared = List(
          CatalogManagementDependency
            .Attribute(
              single = None,
              group = Some(
                List(
                  CatalogManagementDependency
                    .AttributeValue(id = attributeValue2._1, explicitAttributeVerification = attributeValue2._2)
                )
              )
            )
        ),
        verified = List(
          CatalogManagementDependency
            .Attribute(
              single = Some(
                CatalogManagementDependency
                  .AttributeValue(id = attributeValue3._1, explicitAttributeVerification = attributeValue3._2)
              ),
              group = None
            )
        )
      )
    )
  }

  def getEservice(
    id: UUID,
    descriptorId: UUID,
    seed: CatalogManagementDependency.EServiceSeed
  ): CatalogManagementDependency.EService = CatalogManagementDependency.EService(
    id = id,
    producerId = seed.producerId,
    name = seed.name,
    description = seed.description,
    technology = seed.technology,
    attributes = seed.attributes,
    descriptors = List(
      CatalogManagementDependency.EServiceDescriptor(
        id = descriptorId,
        version = "1",
        description = None,
        interface = None,
        docs = Nil,
        state = CatalogManagementDependency.EServiceDescriptorState.DRAFT,
        audience = List("aud1"),
        voucherLifespan = 1000,
        dailyCallsMaxNumber = 1000
      )
    )
  )

  def getEserviceExpected(
    id: UUID,
    descriptorId: UUID,
    seed: CatalogManagementDependency.EServiceSeed,
    organization: PartyManagementApiOrganization,
    attributeValue1: (String, Boolean),
    attributeValue2: (String, Boolean),
    attributeValue3: (String, Boolean)
  ): EService = {
    EService(
      id = id,
      producer = Organization(id = organization.id, name = organization.description),
      name = seed.name,
      description = seed.description,
      technology = convertToApiTechnology(seed.technology),
      attributes = Attributes(
        certified = Seq(
          Attribute(
            single = Some(
              AttributeValue(
                id = attributeValue1._1,
                name = s"${attributeValue1._1}-name",
                description = s"${attributeValue1._1}-description",
                explicitAttributeVerification = attributeValue1._2
              )
            ),
            group = None
          )
        ),
        declared = Seq(
          Attribute(
            single = None,
            group = Some(
              Seq(
                AttributeValue(
                  id = attributeValue2._1,
                  name = s"${attributeValue2._1}-name",
                  description = s"${attributeValue2._1}-description",
                  explicitAttributeVerification = attributeValue2._2
                )
              )
            )
          )
        ),
        verified = Seq(
          Attribute(
            single = Some(
              AttributeValue(
                id = attributeValue3._1,
                name = s"${attributeValue3._1}-name",
                description = s"${attributeValue3._1}-description",
                explicitAttributeVerification = attributeValue3._2
              )
            ),
            group = None
          )
        )
      ),
      descriptors = Seq(
        EServiceDescriptor(
          id = descriptorId,
          version = "1",
          description = None,
          interface = None,
          docs = Nil,
          state = EServiceDescriptorState.DRAFT,
          audience = List("aud1"),
          voucherLifespan = 1000,
          dailyCallsMaxNumber = 1000
        )
      )
    )
  }

  def getOrganization(seed: CatalogManagementDependency.EServiceSeed): PartyManagementApiOrganization =
    PartyManagementApiOrganization(
      id = seed.producerId,
      institutionId = "institutionId",
      description = "organization description",
      digitalAddress = "digitalAddress",
      attributes = Seq.empty[PartyManagementApiAttribute],
      taxCode = "code",
      address = "address",
      zipCode = "zipCode"
    )

  def getAttributes: (
    AttributeRegistryManagementApiAttribute,
    AttributeRegistryManagementApiAttribute,
    AttributeRegistryManagementApiAttribute
  ) = {
    val attributeId1: String = UUID.randomUUID().toString
    val attributeId2: String = UUID.randomUUID().toString
    val attributeId3: String = UUID.randomUUID().toString

    val attribute1 = AttributeRegistryManagementApiAttribute(
      id = attributeId1,
      code = None,
      kind = AttributeRegistryManagementApiAttributeKind.CERTIFIED,
      description = s"$attributeId1-description",
      origin = None,
      name = s"$attributeId1-name",
      creationTime = OffsetDateTime.now()
    )
    val attribute2 = AttributeRegistryManagementApiAttribute(
      id = attributeId2,
      code = None,
      kind = AttributeRegistryManagementApiAttributeKind.DECLARED,
      description = s"$attributeId2-description",
      origin = None,
      name = s"$attributeId2-name",
      creationTime = OffsetDateTime.now()
    )
    val attribute3 = AttributeRegistryManagementApiAttribute(
      id = attributeId3,
      code = None,
      kind = AttributeRegistryManagementApiAttributeKind.VERIFIED,
      description = s"$attributeId3-description",
      origin = None,
      name = s"$attributeId3-name",
      creationTime = OffsetDateTime.now()
    )

    (attribute1, attribute2, attribute3)
  }

  def getAgreement(id: String, verified: Option[Boolean], validityTimespan: Option[Long]): Agreement =
    AgreementManagementDependency.Agreement(
      id = UUID.randomUUID(),
      eserviceId = UUID.randomUUID(),
      descriptorId = UUID.randomUUID(),
      producerId = UUID.randomUUID(),
      consumerId = UUID.randomUUID(),
      state = AgreementManagementDependency.AgreementState.ACTIVE,
      verifiedAttributes = Seq(
        AgreementManagementDependency
          .VerifiedAttribute(
            id = UUID.fromString(id),
            verified = verified,
            verificationDate = None,
            validityTimespan = validityTimespan
          )
      ),
      suspendedByConsumer = None,
      suspendedByProducer = None,
      createdAt = OffsetDateTime.now(),
      updatedAt = None
    )

}
