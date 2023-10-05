package it.pagopa.interop.catalogprocess

import it.pagopa.interop.agreementmanagement.model.agreement.{Active, PersistentAgreement, PersistentStamps}
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagementDependency}
import it.pagopa.interop.catalogmanagement.{model => CatalogManagement}
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier
import it.pagopa.interop.tenantmanagement.model.tenant.{PersistentExternalId, PersistentTenant, PersistentTenantKind}

import java.util.UUID

object SpecData {
  val eServiceId   = UUID.randomUUID()
  val docId        = UUID.randomUUID()
  val descriptorId = UUID.randomUUID()

  val agreement: PersistentAgreement = PersistentAgreement(
    id = UUID.randomUUID(),
    eserviceId = UUID.randomUUID(),
    descriptorId = UUID.randomUUID(),
    producerId = UUID.randomUUID(),
    consumerId = UUID.randomUUID(),
    state = Active,
    verifiedAttributes = Seq.empty,
    certifiedAttributes = Seq.empty,
    declaredAttributes = Seq.empty,
    suspendedByConsumer = None,
    suspendedByProducer = None,
    suspendedByPlatform = None,
    consumerDocuments = Seq.empty,
    createdAt = OffsetDateTimeSupplier.get(),
    updatedAt = None,
    consumerNotes = None,
    contract = None,
    stamps = PersistentStamps(),
    rejectionReason = None,
    suspendedAt = None
  )

  val catalogItem: CatalogManagement.CatalogItem = CatalogManagement.CatalogItem(
    id = eServiceId,
    producerId = UUID.randomUUID(),
    name = "CatalogItemName",
    description = "CatalogItemDescription",
    technology = CatalogManagement.Rest,
    attributes = None,
    descriptors = Seq.empty,
    createdAt = OffsetDateTimeSupplier.get(),
    riskAnalysis = Seq.empty,
    mode = CatalogManagement.Deliver
  )

  val catalogDocument: CatalogManagement.CatalogDocument = CatalogManagement.CatalogDocument(
    id = docId,
    name = "name",
    contentType = "application/pdf",
    prettyName = "prettyName",
    path = "path",
    checksum = "checksum",
    uploadDate = OffsetDateTimeSupplier.get().minusDays(30)
  )

  val catalogRiskAnalysisSchemaOnly = CatalogManagement.CatalogRiskAnalysis(
    id = UUID.randomUUID(),
    name = "name",
    riskAnalysisForm = CatalogManagement.CatalogRiskAnalysisForm(
      id = UUID.randomUUID(),
      version = "3.0",
      singleAnswers = Seq(
        CatalogManagement
          .CatalogRiskAnalysisSingleAnswer(id = UUID.randomUUID(), key = "purpose", value = Some("INSTITUTIONAL"))
      ),
      multiAnswers = Seq(
        CatalogManagement
          .CatalogRiskAnalysisMultiAnswer(id = UUID.randomUUID(), key = "personalDataTypes", values = Seq("OTHER"))
      )
    ),
    createdAt = OffsetDateTimeSupplier.get()
  )

  val catalogRiskAnalysisFullValid = CatalogManagement.CatalogRiskAnalysis(
    id = UUID.randomUUID(),
    name = "name",
    riskAnalysisForm = CatalogManagement.CatalogRiskAnalysisForm(
      id = UUID.randomUUID(),
      version = "3.0",
      singleAnswers = Seq(
        CatalogManagement
          .CatalogRiskAnalysisSingleAnswer(id = UUID.randomUUID(), key = "purpose", value = Some("INSTITUTIONAL")),
        CatalogManagement.CatalogRiskAnalysisSingleAnswer(
          id = UUID.randomUUID(),
          key = "legalObligationReference",
          value = Some("YES")
        ),
        CatalogManagement
          .CatalogRiskAnalysisSingleAnswer(id = UUID.randomUUID(), key = "dataDownload", value = Some("YES")),
        CatalogManagement.CatalogRiskAnalysisSingleAnswer(
          id = UUID.randomUUID(),
          key = "checkedExistenceMereCorrectnessInteropCatalogue",
          value = Some("true")
        ),
        CatalogManagement
          .CatalogRiskAnalysisSingleAnswer(id = UUID.randomUUID(), key = "deliveryMethod", value = Some("CLEARTEXT")),
        CatalogManagement.CatalogRiskAnalysisSingleAnswer(
          id = UUID.randomUUID(),
          key = "legalBasisPublicInterest",
          value = Some("RULE_OF_LAW")
        ),
        CatalogManagement.CatalogRiskAnalysisSingleAnswer(
          id = UUID.randomUUID(),
          key = "confirmPricipleIntegrityAndDiscretion",
          value = Some("true")
        ),
        CatalogManagement
          .CatalogRiskAnalysisSingleAnswer(id = UUID.randomUUID(), key = "ruleOfLawText", value = Some("TheLaw")),
        CatalogManagement
          .CatalogRiskAnalysisSingleAnswer(
            id = UUID.randomUUID(),
            key = "confirmDataRetentionPeriod",
            value = Some("true")
          ),
        CatalogManagement
          .CatalogRiskAnalysisSingleAnswer(id = UUID.randomUUID(), key = "usesThirdPartyData", value = Some("YES")),
        CatalogManagement.CatalogRiskAnalysisSingleAnswer(
          id = UUID.randomUUID(),
          key = "otherPersonalDataTypes",
          value = Some("MyThirdPartyData")
        ),
        CatalogManagement
          .CatalogRiskAnalysisSingleAnswer(id = UUID.randomUUID(), key = "doesUseThirdPartyData", value = Some("YES")),
        CatalogManagement
          .CatalogRiskAnalysisSingleAnswer(id = UUID.randomUUID(), key = "knowsDataQuantity", value = Some("NO")),
        CatalogManagement.CatalogRiskAnalysisSingleAnswer(
          id = UUID.randomUUID(),
          key = "institutionalPurpose",
          value = Some("MyPurpose")
        ),
        CatalogManagement
          .CatalogRiskAnalysisSingleAnswer(id = UUID.randomUUID(), key = "policyProvided", value = Some("NO")),
        CatalogManagement.CatalogRiskAnalysisSingleAnswer(
          id = UUID.randomUUID(),
          key = "reasonPolicyNotProvided",
          value = Some("Because")
        ),
        CatalogManagement.CatalogRiskAnalysisSingleAnswer(id = UUID.randomUUID(), key = "doneDpia", value = Some("NO")),
        CatalogManagement.CatalogRiskAnalysisSingleAnswer(
          id = UUID.randomUUID(),
          key = "declarationConfirmGDPR",
          value = Some("true")
        ),
        CatalogManagement.CatalogRiskAnalysisSingleAnswer(
          id = UUID.randomUUID(),
          key = "purposePursuit",
          value = Some("MERE_CORRECTNESS")
        )
      ),
      multiAnswers = Seq(
        CatalogManagement
          .CatalogRiskAnalysisMultiAnswer(id = UUID.randomUUID(), key = "personalDataTypes", values = Seq("OTHER")),
        CatalogManagement.CatalogRiskAnalysisMultiAnswer(
          id = UUID.randomUUID(),
          key = "legalBasis",
          values = Seq("LEGAL_OBLIGATION", "PUBLIC_INTEREST")
        )
      )
    ),
    createdAt = OffsetDateTimeSupplier.get()
  )

  val catalogDescriptor: CatalogManagement.CatalogDescriptor = CatalogManagement.CatalogDescriptor(
    id = descriptorId,
    version = "1",
    description = None,
    audience = Seq.empty,
    voucherLifespan = 0,
    dailyCallsPerConsumer = 0,
    dailyCallsTotal = 0,
    interface = None,
    docs = Seq.empty,
    state = CatalogManagement.Published,
    agreementApprovalPolicy = Some(CatalogManagement.Automatic),
    serverUrls = Nil,
    createdAt = OffsetDateTimeSupplier.get(),
    publishedAt = Some(OffsetDateTimeSupplier.get()),
    suspendedAt = None,
    deprecatedAt = None,
    archivedAt = None,
    attributes = CatalogManagement.CatalogAttributes.empty
  )

  val eServiceDescriptor: CatalogManagementDependency.EServiceDescriptor =
    CatalogManagementDependency.EServiceDescriptor(
      id = descriptorId,
      version = "1",
      description = None,
      audience = Seq.empty,
      voucherLifespan = 0,
      dailyCallsPerConsumer = 0,
      dailyCallsTotal = 0,
      interface = None,
      docs = Seq.empty,
      state = CatalogManagementDependency.EServiceDescriptorState.PUBLISHED,
      agreementApprovalPolicy = CatalogManagementDependency.AgreementApprovalPolicy.AUTOMATIC,
      serverUrls = Nil,
      attributes = CatalogManagementDependency.Attributes(Nil, Nil, Nil)
    )

  val eServiceDoc: CatalogManagementDependency.EServiceDoc =
    CatalogManagementDependency.EServiceDoc(
      id = docId,
      name = "name",
      contentType = "application/pdf",
      prettyName = "pretty",
      path = "path",
      checksum = "checksum",
      uploadDate = OffsetDateTimeSupplier.get().minusDays(10)
    )

  val eService: CatalogManagementDependency.EService = CatalogManagementDependency.EService(
    id = eServiceId,
    producerId = UUID.randomUUID(),
    name = "EService1",
    description = "description",
    technology = CatalogManagementDependency.EServiceTechnology.REST,
    descriptors = Seq.empty,
    riskAnalysis = Seq.empty,
    mode = CatalogManagementDependency.EServiceMode.DELIVER
  )

  val persistentTenant: PersistentTenant = PersistentTenant(
    id = UUID.randomUUID(),
    kind = Some(PersistentTenantKind.PA),
    selfcareId = None,
    externalId = PersistentExternalId("IPA", "value"),
    features = Nil,
    attributes = Nil,
    createdAt = OffsetDateTimeSupplier.get(),
    updatedAt = Some(OffsetDateTimeSupplier.get().plusDays(10)),
    mails = Nil,
    name = "name"
  )
}
