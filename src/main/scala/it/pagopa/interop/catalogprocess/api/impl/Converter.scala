package it.pagopa.interop.catalogprocess.api.impl

import cats.implicits.catsSyntaxOptionId
import it.pagopa.interop.agreementmanagement.model.{agreement => AgreementPersistenceModel}
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagementDependency}
import it.pagopa.interop.catalogmanagement.{model => readmodel}
import it.pagopa.interop.catalogprocess.model._
import it.pagopa.interop.catalogprocess.common.readmodel.Consumers

import java.util.UUID

object Converter {

  implicit class EServiceSeedWrapper(private val seed: EServiceSeed) extends AnyVal {

    def toDependency(producerId: UUID): CatalogManagementDependency.EServiceSeed =
      CatalogManagementDependency.EServiceSeed(
        producerId = producerId,
        name = seed.name,
        description = seed.description,
        technology = seed.technology.toDependency,
        attributes = seed.attributes.toDependency
      )
  }

  implicit class attributesSeedWrapper(private val seed: AttributesSeed) extends AnyVal {
    def toDependency: CatalogManagementDependency.Attributes =
      CatalogManagementDependency.Attributes(
        certified = seed.certified.map(_.toDependency),
        declared = seed.declared.map(_.toDependency),
        verified = seed.verified.map(_.toDependency)
      )
  }

  implicit class attributeSeedWrapper(private val seed: AttributeSeed) extends AnyVal {
    def toDependency: CatalogManagementDependency.Attribute =
      CatalogManagementDependency.Attribute(
        single = seed.single.map(_.toDependency),
        group = seed.group.map(_.map(_.toDependency))
      )
  }

  implicit class attributeValueSeedWrapper(private val seed: AttributeValueSeed) extends AnyVal {
    def toDependency: CatalogManagementDependency.AttributeValue = CatalogManagementDependency.AttributeValue(
      id = seed.id,
      explicitAttributeVerification = seed.explicitAttributeVerification
    )
  }

  implicit class createEServiceDescriptorDocumentSeedWrapper(private val seed: CreateEServiceDescriptorDocumentSeed)
      extends AnyVal {
    def toDependency: CatalogManagementDependency.CreateEServiceDescriptorDocumentSeed =
      CatalogManagementDependency.CreateEServiceDescriptorDocumentSeed(
        documentId = seed.documentId,
        kind = seed.kind.toDependency,
        prettyName = seed.prettyName,
        filePath = seed.filePath,
        fileName = seed.fileName,
        contentType = seed.contentType,
        checksum = seed.checksum,
        serverUrls = seed.serverUrls
      )
  }

  implicit class eServiceDocumentKindWrapper(private val policy: EServiceDocumentKind) extends AnyVal {
    def toDependency: CatalogManagementDependency.EServiceDocumentKind =
      policy match {
        case EServiceDocumentKind.INTERFACE => CatalogManagementDependency.EServiceDocumentKind.INTERFACE
        case EServiceDocumentKind.DOCUMENT  => CatalogManagementDependency.EServiceDocumentKind.DOCUMENT
      }
  }

  implicit class eServiceDescriptorSeedWrapper(private val descriptor: EServiceDescriptorSeed) extends AnyVal {
    def toDependency: CatalogManagementDependency.EServiceDescriptorSeed =
      CatalogManagementDependency.EServiceDescriptorSeed(
        description = descriptor.description,
        audience = descriptor.audience,
        voucherLifespan = descriptor.voucherLifespan,
        dailyCallsPerConsumer = descriptor.dailyCallsPerConsumer,
        dailyCallsTotal = descriptor.dailyCallsTotal,
        agreementApprovalPolicy = descriptor.agreementApprovalPolicy.toDependency
      )
  }

  implicit class updateEServiceDescriptorSeedWrapper(private val seed: UpdateEServiceDescriptorSeed) extends AnyVal {
    def toDependency: CatalogManagementDependency.UpdateEServiceDescriptorSeed =
      CatalogManagementDependency.UpdateEServiceDescriptorSeed(
        description = seed.description,
        audience = seed.audience,
        voucherLifespan = seed.voucherLifespan,
        dailyCallsPerConsumer = seed.dailyCallsPerConsumer,
        dailyCallsTotal = seed.dailyCallsTotal,
        state = CatalogManagementDependency.EServiceDescriptorState.DRAFT,
        agreementApprovalPolicy = seed.agreementApprovalPolicy.toDependency
      )
  }

  implicit class updateEServiceSeedWrapper(private val seed: UpdateEServiceSeed) extends AnyVal {
    def toDependency: CatalogManagementDependency.UpdateEServiceSeed = CatalogManagementDependency.UpdateEServiceSeed(
      name = seed.name,
      description = seed.description,
      technology = seed.technology.toDependency,
      attributes = seed.attributes.toDependency
    )
  }

  implicit class eServiceDescriptorDocumentSeedWrapper(private val seed: UpdateEServiceDescriptorDocumentSeed)
      extends AnyVal {
    def toDependency: CatalogManagementDependency.UpdateEServiceDescriptorDocumentSeed =
      CatalogManagementDependency.UpdateEServiceDescriptorDocumentSeed(prettyName = seed.prettyName)
  }

  implicit class agreementApprovalPolicyWrapper(private val policy: AgreementApprovalPolicy) extends AnyVal {
    def toDependency: CatalogManagementDependency.AgreementApprovalPolicy =
      policy match {
        case AgreementApprovalPolicy.AUTOMATIC => CatalogManagementDependency.AgreementApprovalPolicy.AUTOMATIC
        case AgreementApprovalPolicy.MANUAL    => CatalogManagementDependency.AgreementApprovalPolicy.MANUAL
      }
  }

  implicit class AgreementStateWrapper(private val as: AgreementState) extends AnyVal {
    def toPersistence: AgreementPersistenceModel.PersistentAgreementState = as match {
      case AgreementState.DRAFT                        => AgreementPersistenceModel.Draft
      case AgreementState.PENDING                      => AgreementPersistenceModel.Pending
      case AgreementState.ACTIVE                       => AgreementPersistenceModel.Active
      case AgreementState.SUSPENDED                    => AgreementPersistenceModel.Suspended
      case AgreementState.ARCHIVED                     => AgreementPersistenceModel.Archived
      case AgreementState.MISSING_CERTIFIED_ATTRIBUTES => AgreementPersistenceModel.MissingCertifiedAttributes
      case AgreementState.REJECTED                     => AgreementPersistenceModel.Rejected
    }
  }

  implicit class ReadModelAgreementStateWrapper(private val as: AgreementPersistenceModel.PersistentAgreementState)
      extends AnyVal {
    def toApi: AgreementState = as match {
      case AgreementPersistenceModel.Draft                      => AgreementState.DRAFT
      case AgreementPersistenceModel.Pending                    => AgreementState.PENDING
      case AgreementPersistenceModel.Active                     => AgreementState.ACTIVE
      case AgreementPersistenceModel.Suspended                  => AgreementState.SUSPENDED
      case AgreementPersistenceModel.Archived                   => AgreementState.ARCHIVED
      case AgreementPersistenceModel.MissingCertifiedAttributes => AgreementState.MISSING_CERTIFIED_ATTRIBUTES
      case AgreementPersistenceModel.Rejected                   => AgreementState.REJECTED
    }
  }

  implicit class ManagementEServiceWrapper(private val eService: CatalogManagementDependency.EService) extends AnyVal {
    def toApi: EService = EService(
      id = eService.id,
      producerId = eService.producerId,
      name = eService.name,
      description = eService.description,
      technology = eService.technology.toApi,
      attributes = eService.attributes.toApi,
      descriptors = eService.descriptors.map(_.toApi)
    )
  }

  implicit class EServiceTechnologyWrapper(private val technology: EServiceTechnology) extends AnyVal {
    def toDependency: CatalogManagementDependency.EServiceTechnology = technology match {
      case EServiceTechnology.REST => CatalogManagementDependency.EServiceTechnology.REST
      case EServiceTechnology.SOAP => CatalogManagementDependency.EServiceTechnology.SOAP
    }
  }

  implicit class DependencyEServiceTechnologyWrapper(
    private val technology: CatalogManagementDependency.EServiceTechnology
  ) extends AnyVal {
    def toApi: EServiceTechnology = technology match {
      case CatalogManagementDependency.EServiceTechnology.REST => EServiceTechnology.REST
      case CatalogManagementDependency.EServiceTechnology.SOAP => EServiceTechnology.SOAP
    }
  }

  implicit class ManagementAttributesWrapper(private val attributes: CatalogManagementDependency.Attributes)
      extends AnyVal {
    def toApi: Attributes = Attributes(
      certified = attributes.certified.map(_.toApi),
      declared = attributes.declared.map(_.toApi),
      verified = attributes.verified.map(_.toApi)
    )
  }

  implicit class ManagementDescriptorStateWrapper(
    private val clientStatus: CatalogManagementDependency.EServiceDescriptorState
  ) extends AnyVal {
    def toApi: EServiceDescriptorState = clientStatus match {
      case CatalogManagementDependency.EServiceDescriptorState.DRAFT      => EServiceDescriptorState.DRAFT
      case CatalogManagementDependency.EServiceDescriptorState.PUBLISHED  => EServiceDescriptorState.PUBLISHED
      case CatalogManagementDependency.EServiceDescriptorState.DEPRECATED => EServiceDescriptorState.DEPRECATED
      case CatalogManagementDependency.EServiceDescriptorState.SUSPENDED  => EServiceDescriptorState.SUSPENDED
      case CatalogManagementDependency.EServiceDescriptorState.ARCHIVED   => EServiceDescriptorState.ARCHIVED
    }
  }

  implicit class ManagementAttributeWrapper(private val attribute: CatalogManagementDependency.Attribute)
      extends AnyVal {
    def toApi: Attribute = Attribute(
      single = attribute.single.map(attr => AttributeValue(attr.id, attr.explicitAttributeVerification)),
      group =
        attribute.group.map(attrs => attrs.map(attr => AttributeValue(attr.id, attr.explicitAttributeVerification)))
    )
  }

  implicit class ManagementAgreementApprovalPolicyWrapper(
    private val policy: CatalogManagementDependency.AgreementApprovalPolicy
  ) extends AnyVal {
    def toApi: AgreementApprovalPolicy = policy match {
      case CatalogManagementDependency.AgreementApprovalPolicy.AUTOMATIC => AgreementApprovalPolicy.AUTOMATIC
      case CatalogManagementDependency.AgreementApprovalPolicy.MANUAL    => AgreementApprovalPolicy.MANUAL
    }
  }

  implicit class ManagementEserviceDoc(private val document: CatalogManagementDependency.EServiceDoc) extends AnyVal {
    def toApi: EServiceDoc = EServiceDoc(
      id = document.id,
      name = document.name,
      contentType = document.contentType,
      prettyName = document.prettyName,
      path = document.path
    )
  }

  implicit class ManagementDescriptorWrapper(private val descriptor: CatalogManagementDependency.EServiceDescriptor)
      extends AnyVal {
    def toApi: EServiceDescriptor = EServiceDescriptor(
      id = descriptor.id,
      version = descriptor.version,
      description = descriptor.description,
      interface = descriptor.interface.map(_.toApi),
      docs = descriptor.docs.map(_.toApi),
      state = descriptor.state.toApi,
      audience = descriptor.audience,
      voucherLifespan = descriptor.voucherLifespan,
      dailyCallsPerConsumer = descriptor.dailyCallsPerConsumer,
      dailyCallsTotal = descriptor.dailyCallsTotal,
      agreementApprovalPolicy = descriptor.agreementApprovalPolicy.toApi,
      serverUrls = descriptor.serverUrls,
      publishedAt = descriptor.publishedAt,
      suspendedAt = descriptor.suspendedAt,
      deprecatedAt = descriptor.deprecatedAt,
      archivedAt = descriptor.archivedAt
    )
  }

  implicit class ReadModelAgreementApprovalPolicyWrapper(
    private val policy: readmodel.PersistentAgreementApprovalPolicy
  ) extends AnyVal {
    def toApi: AgreementApprovalPolicy = policy match {
      case readmodel.Automatic => AgreementApprovalPolicy.AUTOMATIC
      case readmodel.Manual    => AgreementApprovalPolicy.MANUAL
    }
  }

  implicit class ReadModelCatalogItemWrapper(private val item: readmodel.CatalogItem) extends AnyVal {
    def toApi: EService = EService(
      id = item.id,
      producerId = item.producerId,
      name = item.name,
      description = item.description,
      technology = item.technology.toApi,
      attributes = item.attributes.toApi,
      descriptors = item.descriptors.map(_.toApi)
    )
  }

  implicit class ReadModelConsumersWrapper(private val item: Consumers) extends AnyVal {
    def toApi: EServiceConsumer = EServiceConsumer(
      descriptorVersion = item.descriptorVersion.toInt,
      descriptorState = item.descriptorState.toApi,
      agreementState = item.agreementState.toApi,
      consumerName = item.consumerName,
      consumerExternalId = item.consumerExternalId
    )
  }

  implicit class ReadModelAttributesWrapper(private val attributes: readmodel.CatalogAttributes) extends AnyVal {
    def toApi: Attributes =
      Attributes(
        certified = attributes.certified.map(_.toApi),
        declared = attributes.declared.map(_.toApi),
        verified = attributes.verified.map(_.toApi)
      )
  }

  implicit class ReadModelAttributeWrapper(private val attribute: readmodel.CatalogAttribute) extends AnyVal {
    def toApi: Attribute = attribute match {
      case a: readmodel.SingleAttribute =>
        Attribute(single = AttributeValue(a.id.id, a.id.explicitAttributeVerification).some)
      case a: readmodel.GroupAttribute  =>
        Attribute(group = a.ids.map(attr => AttributeValue(attr.id, attr.explicitAttributeVerification)).some)
    }
  }

  implicit class ReadModelTechnologyWrapper(private val technology: readmodel.CatalogItemTechnology) extends AnyVal {
    def toApi: EServiceTechnology = technology match {
      case readmodel.Rest => EServiceTechnology.REST
      case readmodel.Soap => EServiceTechnology.SOAP
    }
  }

  implicit class ReadModelDescriptorWrapper(private val descriptor: readmodel.CatalogDescriptor) extends AnyVal {
    def toApi: EServiceDescriptor = EServiceDescriptor(
      id = descriptor.id,
      version = descriptor.version,
      description = descriptor.description,
      interface = descriptor.interface.map(_.toApi),
      docs = descriptor.docs.map(_.toApi),
      state = descriptor.state.toApi,
      audience = descriptor.audience,
      voucherLifespan = descriptor.voucherLifespan,
      dailyCallsPerConsumer = descriptor.dailyCallsPerConsumer,
      dailyCallsTotal = descriptor.dailyCallsTotal,
      agreementApprovalPolicy =
        descriptor.agreementApprovalPolicy.getOrElse(readmodel.PersistentAgreementApprovalPolicy.default).toApi,
      serverUrls = descriptor.serverUrls,
      publishedAt = descriptor.publishedAt,
      suspendedAt = descriptor.suspendedAt,
      deprecatedAt = descriptor.deprecatedAt,
      archivedAt = descriptor.archivedAt
    )
  }

  implicit class ManagementEServiceDescriptorState(private val state: EServiceDescriptorState) extends AnyVal {
    def toPersistent: readmodel.CatalogDescriptorState = state match {
      case EServiceDescriptorState.DRAFT      => readmodel.Draft
      case EServiceDescriptorState.PUBLISHED  => readmodel.Published
      case EServiceDescriptorState.DEPRECATED => readmodel.Deprecated
      case EServiceDescriptorState.SUSPENDED  => readmodel.Suspended
      case EServiceDescriptorState.ARCHIVED   => readmodel.Archived
    }
  }

  implicit class ReadModelDescriptorStateWrapper(private val clientStatus: readmodel.CatalogDescriptorState)
      extends AnyVal {
    def toApi: EServiceDescriptorState =
      clientStatus match {
        case readmodel.Draft      => EServiceDescriptorState.DRAFT
        case readmodel.Published  => EServiceDescriptorState.PUBLISHED
        case readmodel.Deprecated => EServiceDescriptorState.DEPRECATED
        case readmodel.Suspended  => EServiceDescriptorState.SUSPENDED
        case readmodel.Archived   => EServiceDescriptorState.ARCHIVED
      }
  }

  implicit class ReadModelEServiceDocWrapper(private val document: readmodel.CatalogDocument) extends AnyVal {
    def toApi: EServiceDoc = EServiceDoc(
      id = document.id,
      name = document.name,
      contentType = document.contentType,
      prettyName = document.prettyName,
      path = document.path
    )
  }
}
