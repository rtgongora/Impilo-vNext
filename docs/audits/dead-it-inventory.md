# Dead *IT inventory — b646e0efc (2026-07-19)

No maven-failsafe anywhere at HEAD: every git-tracked *IT.java is DEAD (never run by mvn test/verify).
Strategy (user ruling 2026-07-19): rename → *Test per service, GREEN-ONLY commits; docker-needing tests
gated with @EnabledIfEnvironmentVariable. This session: msika + 1-2 dependency-free services; rest resumable here.

## By service (count)
     18 learning-service
      6 pct-service
      5 inpatient-service
      4 tshepo-authz-service
      4 simba-service
      3 vito-service
      3 tuso-service
      3 experience-bff
      2 tshepo-keys-service
      2 search-service
      2 reporting-service
      2 mvumo-service
      2 identity-assurance-service
      2 data-access-governance-service
      2 coverage-service
      2 channels-service
      1 zibo-service
      1 workflow-service
      1 vashandi-workforce-service
      1 varapi-service
      1 ubomi-service
      1 tshepo-service
      1 tshepo-offline-service
      1 tshepo-identity-service
      1 tshepo-consent-service
      1 tshepo-audit-service
      1 surveillance-service
      1 support-service
      1 share-slip-service
      1 security-hardening-service
      1 schema-registry-service
      1 scheduling-service
      1 rules-service
      1 product-registry-service
      1 pharmacy-service
      1 pharmacy-elmis-adapter
      1 pacs-adapter-service
      1 oros-service
      1 offline-sync-service
      1 offline-edge-service
      1 observability-service
      1 notification-service
      1 ndr-service
      1 national-data-repository-service
      1 mushex-service
      1 msika-service
      1 msika-flow-service
      1 madi-service
      1 live-service
      1 landela-adapter-service
      1 jobs-service
      1 iot-ingestion-service
      1 inventory-service
      1 inventory-elmis-adapter
      1 integration-hub
      1 indawo-service
      1 forms-service
      1 fhir-gateway-service
      1 document-service
      1 dispatch-service
      1 developer-portal-service
      1 data-warehouse-service
      1 data-pipeline-service
      1 data-ingestion-service
      1 data-governance-service
      1 credential-verification-service
      1 costing-engine-service
      1 connector-fhir-adapter
      1 clinical-knowledge-platform-service
      1 card-print-agent
      1 campaigns-service
      1 butano-service
      1 butano-fhir
      1 audit-ledger-service
      1 asset-registry-service

## Docker/Testcontainers-dependent (must be env-gated when revived)
services/clinical-knowledge-platform-service/src/test/java/zw/gov/mohcc/impilo/clinical/api/ClinicalKnowledgePathwayApiIT.java
services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/GoldenContractIT.java
services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/ImagingExperienceWireMockIT.java
services/search-service/src/test/java/zw/gov/mohcc/impilo/search/SearchPgvectorAnnIT.java
services/tshepo-authz-service/src/test/java/zw/gov/mohcc/impilo/tshepo/authz/service/StepUpVerificationIT.java

## Spring-context/bean-heavy (likely need running deps; classify at revival)
118
files carry @SpringBootTest (list omitted — regen: git ls-files '*IT.java' | xargs grep -l @SpringBootTest)

## Full file list
services/asset-registry-service/src/test/java/zw/gov/mohcc/impilo/assetregistry/AssetRegistryGoldenContractIT.java
services/audit-ledger-service/src/test/java/zw/gov/mohcc/impilo/auditledger/AuditLedgerGoldenContractIT.java
services/butano-fhir/src/test/java/zw/gov/mohcc/impilo/butanofhir/v11/ButanoFhirGoldenContractIT.java
services/butano-service/src/test/java/zw/gov/mohcc/impilo/butano/v11/ButanoGoldenContractIT.java
services/campaigns-service/src/test/java/zw/gov/mohcc/impilo/campaigns/v11/CampaignsGoldenContractIT.java
services/card-print-agent/src/test/java/zw/gov/mohcc/impilo/cardprint/v11/CardPrintGoldenContractIT.java
services/channels-service/src/test/java/zw/gov/mohcc/impilo/channels/ChannelsEndpointIT.java
services/channels-service/src/test/java/zw/gov/mohcc/impilo/channels/ChannelsGoldenContractIT.java
services/clinical-knowledge-platform-service/src/test/java/zw/gov/mohcc/impilo/clinical/api/ClinicalKnowledgePathwayApiIT.java
services/connector-fhir-adapter/src/test/java/zw/gov/mohcc/impilo/connectorfhir/ConnectorFhirGoldenContractIT.java
services/costing-engine-service/src/test/java/zw/gov/mohcc/impilo/costa/v11/CostaGoldenContractIT.java
services/coverage-service/src/test/java/zw/gov/mohcc/impilo/coverage/CoverageGoldenContractIT.java
services/coverage-service/src/test/java/zw/gov/mohcc/impilo/coverage/MemberEnrollmentIT.java
services/credential-verification-service/src/test/java/zw/gov/mohcc/impilo/credential/v11/CredentialGoldenContractIT.java
services/data-access-governance-service/src/test/java/zw/gov/mohcc/impilo/dags/core/PermitEnforcementRuntimeProofIT.java
services/data-access-governance-service/src/test/java/zw/gov/mohcc/impilo/dags/v11/DagsGoldenContractIT.java
services/data-governance-service/src/test/java/zw/gov/mohcc/impilo/datagovernance/DataGovernanceGoldenContractIT.java
services/data-ingestion-service/src/test/java/zw/gov/mohcc/impilo/dataingestion/DataIngestionGoldenContractIT.java
services/data-pipeline-service/src/test/java/zw/gov/mohcc/impilo/pipeline/v11/DataPipelineGoldenContractIT.java
services/data-warehouse-service/src/test/java/zw/gov/mohcc/impilo/datawarehouse/DataWarehouseGoldenContractIT.java
services/developer-portal-service/src/test/java/zw/gov/mohcc/impilo/devportal/DeveloperPortalGoldenContractIT.java
services/dispatch-service/src/test/java/zw/gov/mohcc/impilo/dispatch/DispatchGoldenContractIT.java
services/document-service/src/test/java/zw/gov/mohcc/impilo/docs/v11/DocsGoldenContractIT.java
services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/GoldenContractIT.java
services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/ImagingExperienceWireMockIT.java
services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/MobileProviderTier2ResponseShapeIT.java
services/fhir-gateway-service/src/test/java/zw/gov/mohcc/impilo/fhirgateway/v11/FhirGatewayGoldenContractIT.java
services/forms-service/src/test/java/zw/gov/mohcc/impilo/forms/FormsGoldenContractIT.java
services/identity-assurance-service/src/test/java/zw/gov/mohcc/impilo/ia/core/AssuranceWorkflowRuntimeProofIT.java
services/identity-assurance-service/src/test/java/zw/gov/mohcc/impilo/ia/v11/IaGoldenContractIT.java
services/indawo-service/src/test/java/zw/gov/mohcc/impilo/indawo/IndawoGoldenContractIT.java
services/inpatient-service/src/test/java/zw/gov/mohcc/impilo/inpatient/InpatientClinicalDepthIT.java
services/inpatient-service/src/test/java/zw/gov/mohcc/impilo/inpatient/InpatientTenantIsolationIT.java
services/inpatient-service/src/test/java/zw/gov/mohcc/impilo/inpatient/ProcedureEpisodeIT.java
services/inpatient-service/src/test/java/zw/gov/mohcc/impilo/inpatient/WardRoundIT.java
services/inpatient-service/src/test/java/zw/gov/mohcc/impilo/inpatient/v11/GoldenContractIT.java
services/integration-hub/src/test/java/zw/gov/mohcc/impilo/integration/IntegrationHubGoldenContractIT.java
services/inventory-elmis-adapter/src/test/java/zw/gov/mohcc/impilo/inventory/elmis/v11/GoldenContractIT.java
services/inventory-service/src/test/java/zw/gov/mohcc/impilo/inventory/v11/InventoryGoldenContractIT.java
services/iot-ingestion-service/src/test/java/zw/gov/mohcc/impilo/iotingestion/IoTIngestionGoldenContractIT.java
services/jobs-service/src/test/java/zw/gov/mohcc/impilo/jobs/v11/GoldenContractIT.java
services/landela-adapter-service/src/test/java/zw/gov/mohcc/impilo/landela/v11/LandelaGoldenContractIT.java
services/learning-service/src/test/java/zw/gov/mohcc/impilo/learning/fundo/FundoAcademicIT.java
services/learning-service/src/test/java/zw/gov/mohcc/impilo/learning/fundo/FundoAccreditationIT.java
services/learning-service/src/test/java/zw/gov/mohcc/impilo/learning/fundo/FundoAssignmentIT.java
services/learning-service/src/test/java/zw/gov/mohcc/impilo/learning/fundo/FundoAttendanceIT.java
services/learning-service/src/test/java/zw/gov/mohcc/impilo/learning/fundo/FundoAuthoringIT.java
services/learning-service/src/test/java/zw/gov/mohcc/impilo/learning/fundo/FundoBulkEnrolIT.java
services/learning-service/src/test/java/zw/gov/mohcc/impilo/learning/fundo/FundoCohortReportIT.java
services/learning-service/src/test/java/zw/gov/mohcc/impilo/learning/fundo/FundoDashboardReportIT.java
services/learning-service/src/test/java/zw/gov/mohcc/impilo/learning/fundo/FundoDeliveryIT.java
services/learning-service/src/test/java/zw/gov/mohcc/impilo/learning/fundo/FundoDirectoryIT.java
services/learning-service/src/test/java/zw/gov/mohcc/impilo/learning/fundo/FundoLearningProviderIT.java
services/learning-service/src/test/java/zw/gov/mohcc/impilo/learning/fundo/FundoNativeLmsIT.java
services/learning-service/src/test/java/zw/gov/mohcc/impilo/learning/fundo/FundoRegistrationIT.java
services/learning-service/src/test/java/zw/gov/mohcc/impilo/learning/fundo/FundoSpaceAdminIT.java
services/learning-service/src/test/java/zw/gov/mohcc/impilo/learning/fundo/FundoStudentIT.java
services/learning-service/src/test/java/zw/gov/mohcc/impilo/learning/persistence/CohortSessionEntityAdoptionIT.java
services/learning-service/src/test/java/zw/gov/mohcc/impilo/learning/persistence/LearningSpaceScopeIT.java
services/learning-service/src/test/java/zw/gov/mohcc/impilo/learning/v11/LearningGoldenContractIT.java
services/live-service/src/test/java/zw/gov/mohcc/impilo/live/v11/LiveGoldenContractIT.java
services/madi-service/src/test/java/zw/gov/mohcc/impilo/madi/v11/MadiGoldenContractIT.java
services/msika-flow-service/src/test/java/zw/gov/mohcc/impilo/msikaflow/v11/MsikaFlowGoldenContractIT.java
services/msika-service/src/test/java/zw/gov/mohcc/impilo/msika/v11/MsikaGoldenContractIT.java
services/mushex-service/src/test/java/zw/gov/mohcc/impilo/mushex/v11/MushexGoldenContractIT.java
services/mvumo-service/src/test/java/zw/gov/mohcc/impilo/mvumo/integration/MvumoCrossServiceFlowIT.java
services/mvumo-service/src/test/java/zw/gov/mohcc/impilo/mvumo/integration/TshepoConsentDevInstanceIT.java
services/national-data-repository-service/src/test/java/zw/gov/mohcc/impilo/ndr/v11/NdrGoldenContractIT.java
services/ndr-service/src/test/java/zw/gov/mohcc/impilo/ndr/NdrGoldenContractIT.java
services/notification-service/src/test/java/zw/gov/mohcc/impilo/notification/NotificationGoldenContractIT.java
services/observability-service/src/test/java/zw/gov/mohcc/impilo/obs/v11/ObsGoldenContractIT.java
services/offline-edge-service/src/test/java/zw/gov/mohcc/impilo/offlineedge/OfflineEdgeGoldenContractIT.java
services/offline-sync-service/src/test/java/zw/gov/mohcc/impilo/offlinesync/v11/GoldenContractIT.java
services/oros-service/src/test/java/zw/gov/mohcc/impilo/oros/v11/OrosGoldenContractIT.java
services/pacs-adapter-service/src/test/java/zw/gov/mohcc/impilo/pacs/v11/GoldenContractIT.java
services/pct-service/src/test/java/zw/gov/mohcc/impilo/pct/EdVisitIT.java
services/pct-service/src/test/java/zw/gov/mohcc/impilo/pct/PctQueueEncounterIT.java
services/pct-service/src/test/java/zw/gov/mohcc/impilo/pct/TelemedicinePoolQueueIT.java
services/pct-service/src/test/java/zw/gov/mohcc/impilo/pct/core/forms/FormExtractionIT.java
services/pct-service/src/test/java/zw/gov/mohcc/impilo/pct/core/forms/FormResponseLifecycleIT.java
services/pct-service/src/test/java/zw/gov/mohcc/impilo/pct/v11/PctGoldenContractIT.java
services/pharmacy-elmis-adapter/src/test/java/zw/gov/mohcc/impilo/pharmacy/elmis/v11/GoldenContractIT.java
services/pharmacy-service/src/test/java/zw/gov/mohcc/impilo/pharmacy/v11/PharmacyGoldenContractIT.java
services/product-registry-service/src/test/java/zw/gov/mohcc/impilo/productregistry/v11/GoldenContractIT.java
services/reporting-service/src/test/java/zw/gov/mohcc/impilo/reporting/api/ReportControllerIT.java
services/reporting-service/src/test/java/zw/gov/mohcc/impilo/reporting/v11/ReportingGoldenContractIT.java
services/rules-service/src/test/java/zw/gov/mohcc/impilo/rules/RulesGoldenContractIT.java
services/scheduling-service/src/test/java/zw/gov/mohcc/impilo/scheduling/SchedulingSlotServiceIT.java
services/schema-registry-service/src/test/java/zw/gov/mohcc/impilo/schemaregistry/SchemaRegistryGoldenContractIT.java
services/search-service/src/test/java/zw/gov/mohcc/impilo/search/SearchGoldenContractIT.java
services/search-service/src/test/java/zw/gov/mohcc/impilo/search/SearchPgvectorAnnIT.java
services/security-hardening-service/src/test/java/zw/gov/mohcc/impilo/secharden/v11/SecHardenGoldenContractIT.java
services/share-slip-service/src/test/java/zw/gov/mohcc/impilo/shareslip/v11/ShareSlipGoldenContractIT.java
services/simba-service/src/test/java/zw/gov/mohcc/impilo/simba/SimbaWellnessJourneyIT.java
services/simba-service/src/test/java/zw/gov/mohcc/impilo/simba/WellnessAssessmentJourneyIT.java
services/simba-service/src/test/java/zw/gov/mohcc/impilo/simba/WellnessDepthJourneyIT.java
services/simba-service/src/test/java/zw/gov/mohcc/impilo/simba/WellnessSocialJourneyIT.java
services/support-service/src/test/java/zw/gov/mohcc/impilo/support/SupportGoldenContractIT.java
services/surveillance-service/src/test/java/zw/gov/mohcc/impilo/surv/v11/SurvGoldenContractIT.java
services/tshepo-audit-service/src/test/java/zw/gov/mohcc/impilo/tshepo/audit/v11/TshepoAuditGoldenContractIT.java
services/tshepo-authz-service/src/test/java/zw/gov/mohcc/impilo/tshepo/authz/service/GdhcnReadinessRuntimeProofIT.java
services/tshepo-authz-service/src/test/java/zw/gov/mohcc/impilo/tshepo/authz/service/StepUpVerificationIT.java
services/tshepo-authz-service/src/test/java/zw/gov/mohcc/impilo/tshepo/authz/service/TrustAuthorityRegistryRuntimeProofIT.java
services/tshepo-authz-service/src/test/java/zw/gov/mohcc/impilo/tshepo/authz/v11/TshepoAuthzGoldenContractIT.java
services/tshepo-consent-service/src/test/java/zw/gov/mohcc/impilo/tshepo/consent/v11/TshepoConsentGoldenContractIT.java
services/tshepo-identity-service/src/test/java/zw/gov/mohcc/impilo/tshepo/identity/v11/TshepoIdentityGoldenContractIT.java
services/tshepo-keys-service/src/test/java/zw/gov/mohcc/impilo/tshepo/keys/api/SigningRuntimeProofIT.java
services/tshepo-keys-service/src/test/java/zw/gov/mohcc/impilo/tshepo/keys/v11/TshepoKeysGoldenContractIT.java
services/tshepo-offline-service/src/test/java/zw/gov/mohcc/impilo/tshepo/offline/v11/TshepoOfflineGoldenContractIT.java
services/tshepo-service/src/test/java/zw/gov/mohcc/impilo/tshepo/v11/TshepoGoldenContractIT.java
services/tuso-service/src/test/java/zw/gov/mohcc/impilo/tuso/config/SecurityConfigDefaultAuthRequiredIT.java
services/tuso-service/src/test/java/zw/gov/mohcc/impilo/tuso/config/SecurityConfigDisableOauthBypassIT.java
services/tuso-service/src/test/java/zw/gov/mohcc/impilo/tuso/v11/TusoGoldenContractIT.java
services/ubomi-service/src/test/java/zw/gov/mohcc/impilo/ubomi/v11/UbomiGoldenContractIT.java
services/varapi-service/src/test/java/zw/gov/mohcc/impilo/varapi/v11/VarapiGoldenContractIT.java
services/vashandi-workforce-service/src/test/java/zw/gov/mohcc/impilo/vashandi/VashandiWorkforceIT.java
services/vito-service/src/test/java/zw/gov/mohcc/impilo/vito/migration/v11/runtime/DualEmitModeIntegrationIT.java
services/vito-service/src/test/java/zw/gov/mohcc/impilo/vito/migration/v11/runtime/V11MergeFlowIT.java
services/vito-service/src/test/java/zw/gov/mohcc/impilo/vito/v11/VitoGoldenContractIT.java
services/workflow-service/src/test/java/zw/gov/mohcc/impilo/workflow/WorkflowGoldenContractIT.java
services/zibo-service/src/test/java/zw/gov/mohcc/impilo/zibo/v11/ZiboGoldenContractIT.java
