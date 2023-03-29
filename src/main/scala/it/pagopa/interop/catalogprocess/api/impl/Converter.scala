package it.pagopa.interop.catalogprocess.api.impl

import cats.implicits.catsSyntaxOptionId
import it.pagopa.interop.agreementmanagement.model.{agreement => AgreementPersistenceModel}
import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagementDependency}
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagementDependency}
import it.pagopa.interop.catalogmanagement.{model => readmodel}
import it.pagopa.interop.catalogprocess.model._

import java.util.UUID

object Converter {

  private final case class AttributeDetails(name: String, description: String)

  def convertToApiEService(eService: CatalogManagementDependency.EService): EService = EService(
    id = eService.id,
    producerId = eService.producerId,
    name = eService.name,
    description = eService.description,
    technology = convertToApiTechnology(eService.technology),
    attributes = convertToApiAttributes(eService.attributes),
    descriptors = eService.descriptors.map(convertToApiDescriptor)
  )

  def convertToApiEService(eService: readmodel.CatalogItem): EService = EService(
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
    prettyName = document.prettyName,
    path = document.path
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
    prettyName = document.prettyName,
    path = document.path
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

  def convertToClientEServiceSeed(
    eServiceSeed: EServiceSeed,
    producerId: UUID
  ): CatalogManagementDependency.EServiceSeed =
    CatalogManagementDependency.EServiceSeed(
      producerId = producerId,
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

  def convertToManagementEServiceDescriptorDocumentSeed(
    seed: CreateEServiceDescriptorDocumentSeed
  ): CatalogManagementDependency.CreateEServiceDescriptorDocumentSeed =
    CatalogManagementDependency.CreateEServiceDescriptorDocumentSeed(
      documentId = seed.documentId,
      kind = convertFromApiEServiceDocumentKind(seed.kind),
      prettyName = seed.prettyName,
      filePath = seed.filePath,
      fileName = seed.fileName,
      contentType = seed.contentType,
      checksum = seed.checksum,
      serverUrls = seed.serverUrls
    )

  def convertFromApiEServiceDocumentKind(
    policy: EServiceDocumentKind
  ): CatalogManagementDependency.EServiceDocumentKind =
    policy match {
      case EServiceDocumentKind.INTERFACE => CatalogManagementDependency.EServiceDocumentKind.INTERFACE
      case EServiceDocumentKind.DOCUMENT  => CatalogManagementDependency.EServiceDocumentKind.DOCUMENT
    }

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

  implicit class ManagementTechnologyWrapper(private val technology: CatalogManagementDependency.EServiceTechnology)
      extends AnyVal {
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
      serverUrls = descriptor.serverUrls
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

  implicit class ReadModelAttributesWrapper(private val attributes: readmodel.CatalogAttributes) extends AnyVal {
    def toApi: Attributes =
      Attributes(
        certified = attributes.certified.map(convertToApiAttribute),
        declared = attributes.declared.map(convertToApiAttribute),
        verified = attributes.verified.map(convertToApiAttribute)
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
      serverUrls = descriptor.serverUrls
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
