package it.pagopa.interop.catalogprocess.api.impl

import it.pagopa.interop.agreementmanagement.model.{agreement => AgreementPersistenceModel}
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagementDependency}
import it.pagopa.interop.catalogmanagement.{model => readmodel}
import it.pagopa.interop.commons.riskanalysis.{model => Commons}
import it.pagopa.interop.catalogprocess.model._
import it.pagopa.interop.catalogprocess.common.readmodel.Consumers
import it.pagopa.interop.tenantmanagement.model.tenant.PersistentTenantKind
import it.pagopa.interop.catalogmanagement.model.{Deliver, Receive}
import it.pagopa.interop.commons.riskanalysis.api.impl.RiskAnalysisValidation
import it.pagopa.interop.catalogprocess.errors.CatalogProcessErrors.RiskAnalysisValidationFailed

import java.util.UUID

object Converter {

  implicit class EServiceSeedWrapper(private val seed: EServiceSeed) extends AnyVal {

    def toDependency(producerId: UUID): CatalogManagementDependency.EServiceSeed =
      CatalogManagementDependency.EServiceSeed(
        producerId = producerId,
        name = seed.name,
        description = seed.description,
        technology = seed.technology.toDependency,
        mode = seed.mode.toDependency
      )
  }

  implicit class AttributesSeedWrapper(private val seed: AttributesSeed) extends AnyVal {
    def toDependency: CatalogManagementDependency.Attributes =
      CatalogManagementDependency.Attributes(
        certified = seed.certified.map(_.map(_.toDependency)),
        declared = seed.declared.map(_.map(_.toDependency)),
        verified = seed.verified.map(_.map(_.toDependency))
      )
  }

  implicit class AttributeSeedWrapper(private val seed: AttributeSeed) extends AnyVal {
    def toDependency: CatalogManagementDependency.Attribute =
      CatalogManagementDependency.Attribute(
        id = seed.id,
        explicitAttributeVerification = seed.explicitAttributeVerification
      )
  }

  implicit class CreateEServiceDescriptorDocumentSeedWrapper(private val seed: CreateEServiceDescriptorDocumentSeed)
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

  implicit class EServiceDocumentKindWrapper(private val policy: EServiceDocumentKind) extends AnyVal {
    def toDependency: CatalogManagementDependency.EServiceDocumentKind =
      policy match {
        case EServiceDocumentKind.INTERFACE => CatalogManagementDependency.EServiceDocumentKind.INTERFACE
        case EServiceDocumentKind.DOCUMENT  => CatalogManagementDependency.EServiceDocumentKind.DOCUMENT
      }
  }

  implicit class EServiceDescriptorSeedWrapper(private val seed: EServiceDescriptorSeed) extends AnyVal {
    def toDependency: CatalogManagementDependency.EServiceDescriptorSeed =
      CatalogManagementDependency.EServiceDescriptorSeed(
        description = seed.description,
        audience = seed.audience,
        voucherLifespan = seed.voucherLifespan,
        dailyCallsPerConsumer = seed.dailyCallsPerConsumer,
        dailyCallsTotal = seed.dailyCallsTotal,
        agreementApprovalPolicy = seed.agreementApprovalPolicy.toDependency,
        attributes = seed.attributes.toDependency
      )
  }

  implicit class UpdateEServiceDescriptorSeedWrapper(private val seed: UpdateEServiceDescriptorSeed) extends AnyVal {
    def toDependency: CatalogManagementDependency.UpdateEServiceDescriptorSeed =
      CatalogManagementDependency.UpdateEServiceDescriptorSeed(
        description = seed.description,
        audience = seed.audience,
        voucherLifespan = seed.voucherLifespan,
        dailyCallsPerConsumer = seed.dailyCallsPerConsumer,
        dailyCallsTotal = seed.dailyCallsTotal,
        state = CatalogManagementDependency.EServiceDescriptorState.DRAFT,
        agreementApprovalPolicy = seed.agreementApprovalPolicy.toDependency,
        attributes = seed.attributes.toDependency
      )
  }

  implicit class UpdateEServiceSeedWrapper(private val seed: UpdateEServiceSeed) extends AnyVal {
    def toDependency: CatalogManagementDependency.UpdateEServiceSeed = CatalogManagementDependency.UpdateEServiceSeed(
      name = seed.name,
      description = seed.description,
      technology = seed.technology.toDependency,
      mode = seed.mode.toDependency
    )
  }

  implicit class EServiceRiskAnalysisSeedWrapper(private val seed: EServiceRiskAnalysisSeed) extends AnyVal {
    def toDependency(schemaOnlyValidation: Boolean)(
      kind: PersistentTenantKind
    ): Either[RiskAnalysisValidationFailed, CatalogManagementDependency.RiskAnalysisSeed] = for {
      riskAnalysisFormSeed <-
        RiskAnalysisValidation
          .validate(seed.riskAnalysisForm.toTemplate, schemaOnlyValidation)(kind.toTemplate)
          .leftMap(RiskAnalysisValidationFailed(_))
          .toEither
          .map(_.toDependency)
    } yield CatalogManagementDependency.RiskAnalysisSeed(name = seed.name, riskAnalysisForm = riskAnalysisFormSeed)
  }

  implicit class TemplateRiskAnalysisFormSeedWrapper(private val riskAnalysis: Commons.RiskAnalysisFormSeed)
      extends AnyVal {
    def toDependency: CatalogManagementDependency.RiskAnalysisFormSeed =
      CatalogManagementDependency.RiskAnalysisFormSeed(
        version = riskAnalysis.version,
        singleAnswers = riskAnalysis.singleAnswers.map(_.toDependency),
        multiAnswers = riskAnalysis.multiAnswers.map(_.toDependency)
      )
  }

  implicit class RiskAnalysisSingleAnswerValidatedWrapper(
    private val singleAnswers: Commons.RiskAnalysisSingleAnswerValidated
  ) extends AnyVal {
    def toDependency: CatalogManagementDependency.RiskAnalysisSingleAnswerSeed =
      CatalogManagementDependency.RiskAnalysisSingleAnswerSeed(key = singleAnswers.key, value = singleAnswers.value)
  }

  implicit class RiskAnalysisMultiAnswerValidatedWrapper(
    private val multiAnswers: Commons.RiskAnalysisMultiAnswerValidated
  ) extends AnyVal {
    def toDependency: CatalogManagementDependency.RiskAnalysisMultiAnswerSeed =
      CatalogManagementDependency.RiskAnalysisMultiAnswerSeed(key = multiAnswers.key, values = multiAnswers.values)
  }

  implicit class EServiceRiskAnalysisFormSeedWrapper(private val seed: EServiceRiskAnalysisFormSeed) extends AnyVal {
    def toTemplate: Commons.RiskAnalysisForm = Commons.RiskAnalysisForm(version = seed.version, answers = seed.answers)
  }

  implicit class PersistentTenantKindrapper(private val kind: PersistentTenantKind) extends AnyVal {
    def toTemplate: Commons.RiskAnalysisTenantKind = kind match {
      case PersistentTenantKind.PA      => Commons.RiskAnalysisTenantKind.PA
      case PersistentTenantKind.GSP     => Commons.RiskAnalysisTenantKind.GSP
      case PersistentTenantKind.PRIVATE => Commons.RiskAnalysisTenantKind.PRIVATE
    }
  }

  implicit class EServiceDescriptorDocumentSeedWrapper(private val seed: UpdateEServiceDescriptorDocumentSeed)
      extends AnyVal {
    def toDependency: CatalogManagementDependency.UpdateEServiceDescriptorDocumentSeed =
      CatalogManagementDependency.UpdateEServiceDescriptorDocumentSeed(prettyName = seed.prettyName)
  }

  implicit class AgreementApprovalPolicyWrapper(private val policy: AgreementApprovalPolicy) extends AnyVal {
    def toDependency: CatalogManagementDependency.AgreementApprovalPolicy =
      policy match {
        case AgreementApprovalPolicy.AUTOMATIC => CatalogManagementDependency.AgreementApprovalPolicy.AUTOMATIC
        case AgreementApprovalPolicy.MANUAL    => CatalogManagementDependency.AgreementApprovalPolicy.MANUAL
      }
  }

  implicit class AgreementStateWrapper(private val as: AgreementState) extends AnyVal {
    def toPersistent: AgreementPersistenceModel.PersistentAgreementState = as match {
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
      descriptors = eService.descriptors.map(_.toApi),
      riskAnalysis = eService.riskAnalysis.map(_.toApi),
      mode = eService.mode.toApi
    )
  }

  implicit class ManagementRiskAnalysisObjectWrapper(private val p: CatalogManagementDependency.EServiceRiskAnalysis)
      extends AnyVal {
    def toApi: EServiceRiskAnalysis =
      EServiceRiskAnalysis(
        id = p.id,
        name = p.name,
        riskAnalysisForm = p.riskAnalysisForm.toApi,
        createdAt = p.createdAt
      )
  }

  implicit class ManagementRiskAnalysisFormObjectWrapper(private val p: CatalogManagementDependency.RiskAnalysisForm)
      extends AnyVal {
    def toApi: EServiceRiskAnalysisForm = EServiceRiskAnalysisForm(
      id = p.id,
      version = p.version,
      singleAnswers = p.singleAnswers.map(_.toApi),
      multiAnswers = p.multiAnswers.map(_.toApi)
    )
  }

  implicit class ManagementRiskAnalysisSingleAnswerObjectWrapper(
    private val p: CatalogManagementDependency.RiskAnalysisSingleAnswer
  ) extends AnyVal {
    def toApi: EServiceRiskAnalysisSingleAnswer =
      EServiceRiskAnalysisSingleAnswer(id = p.id, key = p.key, value = p.value)
  }

  implicit class ManagementRiskAnalysisMultiAnswerObjectWrapper(
    private val p: CatalogManagementDependency.RiskAnalysisMultiAnswer
  ) extends AnyVal {
    def toApi: EServiceRiskAnalysisMultiAnswer =
      EServiceRiskAnalysisMultiAnswer(id = p.id, key = p.key, values = p.values)
  }

  implicit class ManagementItemModeObjectWrapper(private val p: CatalogManagementDependency.EServiceMode)
      extends AnyVal {
    def toApi: EServiceMode = p match {
      case CatalogManagementDependency.EServiceMode.RECEIVE => EServiceMode.RECEIVE
      case CatalogManagementDependency.EServiceMode.DELIVER => EServiceMode.DELIVER
    }
  }

  implicit class EServiceModeObjectWrapper(private val p: EServiceMode) extends AnyVal {
    def toDependency: CatalogManagementDependency.EServiceMode = p match {
      case EServiceMode.RECEIVE => CatalogManagementDependency.EServiceMode.RECEIVE
      case EServiceMode.DELIVER => CatalogManagementDependency.EServiceMode.DELIVER
    }
  }

  implicit class EServiceRiskAnalysisObjectWrapper(private val p: EServiceRiskAnalysis) extends AnyVal {
    def toDependency: CatalogManagementDependency.EServiceRiskAnalysis =
      CatalogManagementDependency.EServiceRiskAnalysis(
        id = p.id,
        name = p.name,
        riskAnalysisForm = p.riskAnalysisForm.toDependency,
        createdAt = p.createdAt
      )
  }

  implicit class EServiceRiskAnalysisFormObjectWrapper(private val p: EServiceRiskAnalysisForm) extends AnyVal {
    def toDependency: CatalogManagementDependency.RiskAnalysisForm = CatalogManagementDependency.RiskAnalysisForm(
      id = p.id,
      version = p.version,
      singleAnswers = p.singleAnswers.map(_.toDependency),
      multiAnswers = p.multiAnswers.map(_.toDependency)
    )
  }

  implicit class EServiceRiskAnalysisSingleAnswerObjectWrapper(private val p: EServiceRiskAnalysisSingleAnswer)
      extends AnyVal {
    def toDependency: CatalogManagementDependency.RiskAnalysisSingleAnswer =
      CatalogManagementDependency.RiskAnalysisSingleAnswer(id = p.id, key = p.key, value = p.value)
  }

  implicit class EServiceRiskAnalysisMultiAnswerObjectWrapper(private val p: EServiceRiskAnalysisMultiAnswer)
      extends AnyVal {
    def toDependency: CatalogManagementDependency.RiskAnalysisMultiAnswer =
      CatalogManagementDependency.RiskAnalysisMultiAnswer(id = p.id, key = p.key, values = p.values)
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
      certified = attributes.certified.map(_.map(_.toApi)),
      declared = attributes.declared.map(_.map(_.toApi)),
      verified = attributes.verified.map(_.map(_.toApi))
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
    def toApi: Attribute = Attribute(attribute.id, attribute.explicitAttributeVerification)
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
      attributes = descriptor.attributes.toApi,
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
      descriptors = item.descriptors.map(_.toApi),
      mode = item.mode.toApi,
      riskAnalysis = item.riskAnalysis.map(_.toApi)
    )
  }

  implicit class CatalogRiskAnalysisObjectWrapper(private val p: readmodel.CatalogRiskAnalysis) extends AnyVal {
    def toApi: EServiceRiskAnalysis =
      EServiceRiskAnalysis(
        id = p.id,
        name = p.name,
        riskAnalysisForm = p.riskAnalysisForm.toApi,
        createdAt = p.createdAt
      )
  }

  implicit class CatalogRiskAnalysisFormObjectWrapper(private val p: readmodel.CatalogRiskAnalysisForm) extends AnyVal {
    def toApi: EServiceRiskAnalysisForm = EServiceRiskAnalysisForm(
      id = p.id,
      version = p.version,
      singleAnswers = p.singleAnswers.map(_.toApi),
      multiAnswers = p.multiAnswers.map(_.toApi)
    )

    def toTemplate: Commons.RiskAnalysisForm = Commons.RiskAnalysisForm(
      version = p.version,
      answers = (p.singleAnswers.map(_.toTemplate).flatten ++ p.multiAnswers.map(_.toTemplate).flatten).toMap
    )
  }

  implicit class CatalogRiskAnalysisSingleAnswerObjectWrapper(private val p: readmodel.CatalogRiskAnalysisSingleAnswer)
      extends AnyVal {
    def toApi: EServiceRiskAnalysisSingleAnswer =
      EServiceRiskAnalysisSingleAnswer(id = p.id, key = p.key, value = p.value)
    def toTemplate: Map[String, Seq[String]]    = Map(p.key -> p.value.toSeq)
  }

  implicit class CatalogRiskAnalysisMultiAnswerObjectWrapper(private val p: readmodel.CatalogRiskAnalysisMultiAnswer)
      extends AnyVal {
    def toApi: EServiceRiskAnalysisMultiAnswer =
      EServiceRiskAnalysisMultiAnswer(id = p.id, key = p.key, values = p.values)
    def toTemplate: Map[String, Seq[String]]   = Map(p.key -> p.values)
  }

  implicit class ReadModelModeWrapper(private val mode: readmodel.CatalogItemMode) extends AnyVal {
    def toApi: EServiceMode = mode match {
      case Deliver => EServiceMode.DELIVER
      case Receive => EServiceMode.RECEIVE
    }
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
        certified = attributes.certified.map(_.map(_.toApi)),
        declared = attributes.declared.map(_.map(_.toApi)),
        verified = attributes.verified.map(_.map(_.toApi))
      )
  }

  implicit class ReadModelAttributeWrapper(private val attribute: readmodel.CatalogAttribute) extends AnyVal {
    def toApi: Attribute = Attribute(attribute.id, attribute.explicitAttributeVerification)
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
      archivedAt = descriptor.archivedAt,
      attributes = descriptor.attributes.toApi
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
