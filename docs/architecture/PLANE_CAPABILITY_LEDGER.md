# Plane Capability Ledger

> Generated: 2026-06-05. Regenerate: `node scripts/architecture/generate-plane-capability-ledger.mjs`

Maps each registry service to contract, BFF client, UI hook/route, and surfacing maturity.
Operator and user-facing workflows are equally in scope per plane surfacing doctrine.

## Summary

| Plane | Services |
|-------|----------|
| trust | 9 |
| registry | 7 |
| clinical | 17 |
| data | 12 |
| integration | 24 |
| experience | 3 |
| enterprise | 14 |
| **Total** | **98** |

## Capability registry cross-reference

| plane | domain | capability | maturity | webRoute | action |
| --- | --- | --- | --- | --- | --- |
| Trust | TSHEPO | Trust admin (policies, break-glass, devices, audit) | Partial | /admin/trust, /settings/security | Trust governance strip on settings; deepen device admin |
| Registry | VITO | Client search, register, profile, Health ID | Partial | /id-services, /registry/* | Registry hub depth + mobile health-id parity |
| Registry | VARAPI | Provider registry, licenses, privileges, CPD | Partial | /registry/providers/* | Verification workflow screens |
| Registry | TUSO | Facility/workspace registry, bookings | Partial | /facility/*, /registry/facilities/* | Facility operating model detail pages |
| Registry | Indawo | Public health site registry | Partial | /public-health/* | Ndila map panel on site registry |
| Clinical | BUTANO | SHR summary, timeline, allergies, conditions | Live | /ehr/[patientId]/* | Wire citizen personal sections to BFF |
| Clinical | Core Transaction | Transaction composition, journey steppers | Partial | /core-transaction | Deepen command/handoff wiring |
| Data & Intelligence | Public Health Ops | Inspections, outbreaks, campaigns, intelligence | Partial | /public-health/* | Provider field tasks parity |
| Integration & Edge | Ndila | Geocode, routes, intelligence layers | Partial | NdilaIntelligencePanel | Reusable map component rollout |
| Enterprise | Nhume | Dispatch, delivery, fleet tracking | Partial | /nhume/*, /operations/dispatch | Unified operator UX + maturity labels |
| Experience | Comms Hub | Omnichannel, messaging, notifications | Partial | /communication, /omnichannel | Comms dashboard actionable tasks |
| Clinical | Telemedicine | Teleconsult sessions, scheduling | Partial | /telemedicine/* | Label Blocked for RTC; live scheduling/records |
| Enterprise | Msika / Msika Flow | Catalog, orders, marketplace | Partial | /marketplace/* | Honest blocked states on list routes |
| Enterprise | MusheX / COSTA | Payments, claims, billing, tariffs | Partial | /finance/*, /wallet | Finance journey mobile parity |
| Experience | Fundo | LMS courses, studio, certificates | Partial | /learning/* | Fundo mobile module depth |
| Experience | Social | Timeline, communities, pages | Live | /social, /communities, /pages | Moderation workflow surfacing |
| Registry | UBOMI | CRVS births/deaths | Partial | /ubomi | UBOMI births/deaths/verify live when service up; mobile parity |
| Registry | ZIBO | Terminology governance | Partial | ui/zibo-web | Shell link + maturity on terminology nav |
| Experience | Nompilo | Guidance, LLM chat, core-transaction assist | Partial | /ask, global command bar | Context query params + fallback label |
| Integration | Integration Hub | Routes, dead letters, dispatch | Partial | /admin/integration-status, /settings/integrations | Integration admin depth |
| Platform | Workflow / Dispatch | Workflow definitions, instances, dispatch tasks | Partial | /operations/workflows, /operations/dispatch | Workflow instance table + dispatch guided detail |
| Platform | Admin / Governance | Users, tenants, roles, audit, feature flags | Partial | /admin/*, /organization-admin/* | Document Blocked surfaces explicitly |

## UI hook inventory (157 domain hooks)

- `ui/one-ui-shell/src/hooks/queries/useAdminObservability.ts`
- `ui/one-ui-shell/src/hooks/queries/useAdminReportJobs.ts`
- `ui/one-ui-shell/src/hooks/queries/useAdminUsers.ts`
- `ui/one-ui-shell/src/hooks/queries/useAiGovernance.ts`
- `ui/one-ui-shell/src/hooks/queries/useAllergies.ts`
- `ui/one-ui-shell/src/hooks/queries/useApgar.ts`
- `ui/one-ui-shell/src/hooks/queries/useAssets.ts`
- `ui/one-ui-shell/src/hooks/queries/useAssistantNotifications.ts`
- `ui/one-ui-shell/src/hooks/queries/useAudit.ts`
- `ui/one-ui-shell/src/hooks/queries/useAuth.ts`
- `ui/one-ui-shell/src/hooks/queries/useBeds.ts`
- `ui/one-ui-shell/src/hooks/queries/useBiometricPolicy.ts`
- `ui/one-ui-shell/src/hooks/queries/useCDSAlerts.ts`
- `ui/one-ui-shell/src/hooks/queries/useCampaigns.ts`
- `ui/one-ui-shell/src/hooks/queries/useCareContinuity.ts`
- `ui/one-ui-shell/src/hooks/queries/useCaregiverLinkage.ts`
- `ui/one-ui-shell/src/hooks/queries/useCitizenHealthSummary.ts`
- `ui/one-ui-shell/src/hooks/queries/useCitizenMonitoring.ts`
- `ui/one-ui-shell/src/hooks/queries/useCitizenWellness.ts`
- `ui/one-ui-shell/src/hooks/queries/useClientRegistry.ts`
- `ui/one-ui-shell/src/hooks/queries/useClinicalCuration.ts`
- `ui/one-ui-shell/src/hooks/queries/useClinicalDocuments.ts`
- `ui/one-ui-shell/src/hooks/queries/useClinicalExtensions.ts`
- `ui/one-ui-shell/src/hooks/queries/useClinicalNotes.ts`
- `ui/one-ui-shell/src/hooks/queries/useClinicalWorklist.ts`
- `ui/one-ui-shell/src/hooks/queries/useCommerceFlow.ts`
- `ui/one-ui-shell/src/hooks/queries/useCommercePickup.ts`
- `ui/one-ui-shell/src/hooks/queries/useCommerceSubstitutions.ts`
- `ui/one-ui-shell/src/hooks/queries/useCommerceVendor.ts`
- `ui/one-ui-shell/src/hooks/queries/useCommunication.ts`
- `ui/one-ui-shell/src/hooks/queries/useCommunity.ts`
- `ui/one-ui-shell/src/hooks/queries/useCommunityService.ts`
- `ui/one-ui-shell/src/hooks/queries/useConditions.ts`
- `ui/one-ui-shell/src/hooks/queries/useConsent.ts`
- `ui/one-ui-shell/src/hooks/queries/useCoreTransactionExperience.ts`
- `ui/one-ui-shell/src/hooks/queries/useCostaIntel.ts`
- `ui/one-ui-shell/src/hooks/queries/useCoverage.ts`
- `ui/one-ui-shell/src/hooks/queries/useCpd.ts`
- `ui/one-ui-shell/src/hooks/queries/useDataAccessGovernance.ts`
- `ui/one-ui-shell/src/hooks/queries/useDataGovernance.ts`

… and 117 more under `ui/one-ui-shell/src/hooks/queries/`.

---

## Trust plane

| Metric | Count |
|--------|-------|
| Services | 9 |
| Live | 0 |
| Partial | 9 |
| Not Wired | 0 |

| serviceId | domain | contract | bffClient | uiHook | webRoute | maturity | apiContract | nextAction |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| identity-assurance-service | identity-governance | contracts/openapi/identity-assurance.openapi.yaml | — | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| mvumo-service | identity-governance | contracts/openapi/mvumo.openapi.yaml | services/experience-bff/.../client/MvumoServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| tshepo-audit-service | identity-governance | contracts/openapi/tshepo-audit.openapi.yaml | services/experience-bff/.../client/TshepoAuditServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| tshepo-authz-service | identity-governance | contracts/openapi/tshepo-authz.openapi.yaml | services/experience-bff/.../client/TshepoAuthzServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| tshepo-consent-service | identity-governance | contracts/openapi/tshepo-consent.openapi.yaml | services/experience-bff/.../client/TshepoConsentServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| tshepo-identity-service | identity-governance | contracts/openapi/tshepo-identity.openapi.yaml | services/experience-bff/.../client/TshepoIdentityServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| tshepo-keys-service | identity-governance | contracts/openapi/tshepo-keys.openapi.yaml | services/experience-bff/.../client/TshepoKeysServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| tshepo-offline-service | identity-governance | contracts/openapi/tshepo-offline.openapi.yaml | services/experience-bff/.../client/TshepoOfflineServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| tshepo-service | identity-governance | contracts/openapi/tshepo.openapi.yaml | services/experience-bff/.../client/TshepoAuditServiceClient.java | useTrustAdmin.ts | /admin/trust, /settings/security | Partial | partial | Trust governance strip on settings; deepen device admin |


## Registry plane

| Metric | Count |
|--------|-------|
| Services | 7 |
| Live | 0 |
| Partial | 7 |
| Not Wired | 0 |

| serviceId | domain | contract | bffClient | uiHook | webRoute | maturity | apiContract | nextAction |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| indawo-service | registry-spine | contracts/openapi/indawo.openapi.yaml | services/experience-bff/.../client/IndawoServiceClient.java | usePublicHealth.ts, useSiteRegistry.ts | /public-health/* | Partial | partial | Ndila map panel on site registry |
| product-registry-service | registry-spine | contracts/openapi/product-registry.openapi.yaml | — | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| tuso-service | registry-spine | contracts/openapi/tuso.openapi.yaml | services/experience-bff/.../client/TusoServiceClient.java | useFacilities.ts, useTusoRegistry.ts | /facility/*, /registry/facilities/* | Partial | partial | Facility operating model detail pages |
| ubomi-service | registry-spine | contracts/openapi/ubomi.openapi.yaml | services/experience-bff/.../client/UbomiServiceClient.java | useUbomiRegistry.ts (new) | /ubomi | Partial | partial | UBOMI births/deaths/verify live when service up; mobile parity |
| varapi-service | registry-spine | contracts/openapi/varapi.openapi.yaml | services/experience-bff/.../client/VarapiServiceClient.java | useRegistry.ts, useLicenses.ts, useCpd.ts | /registry/providers/* | Partial | partial | Verification workflow screens |
| vito-service | registry-spine | contracts/openapi/vito.openapi.yaml | services/experience-bff/.../client/VitoServiceClient.java | useIdentity.ts, useClientRegistry.ts | /id-services, /registry/* | Partial | partial | Registry hub depth + mobile health-id parity |
| zibo-service | terminology | contracts/openapi/zibo.openapi.yaml | services/experience-bff/.../client/ZiboServiceClient.java | ziboApi.ts | ui/zibo-web | Partial | partial | Shell link + maturity on terminology nav |


## Clinical plane

| Metric | Count |
|--------|-------|
| Services | 17 |
| Live | 1 |
| Partial | 16 |
| Not Wired | 0 |

| serviceId | domain | contract | bffClient | uiHook | webRoute | maturity | apiContract | nextAction |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| butano-fhir | care-delivery | contracts/openapi/butano-fhir.openapi.yaml | services/experience-bff/.../client/ButanoServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| butano-service | care-delivery | contracts/openapi/butano.custom.openapi.yaml | services/experience-bff/.../client/ButanoServiceClient.java | useSummary.ts, useTimeline.ts | /ehr/[patientId]/* | Live | partial | Wire citizen personal sections to BFF |
| clinical-knowledge-platform-service | clinical-knowledge | contracts/openapi/clinical-knowledge-platform.openapi.yaml | — | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| document-service | care-delivery | — | services/experience-bff/.../client/DocumentServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| fhir-gateway-service | care-delivery | contracts/openapi/fhir-gateway.openapi.yaml | services/experience-bff/.../client/FhirGatewayServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| forms-service | clinical-knowledge | contracts/openapi/forms.openapi.yaml | services/experience-bff/.../client/FormsServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| guidance-service | clinical-knowledge | contracts/openapi/guidance.openapi.yaml | services/experience-bff/.../client/GuidanceServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| inpatient-service | care-delivery | contracts/openapi/inpatient.openapi.yaml | services/experience-bff/.../client/InpatientServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| inventory-elmis-adapter | care-delivery | — | services/experience-bff/.../client/InventoryServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| inventory-service | care-delivery | contracts/openapi/inventory.openapi.yaml | services/experience-bff/.../client/InventoryServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| oros-service | care-delivery | contracts/openapi/oros.openapi.yaml | services/experience-bff/.../client/OrosServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| pacs-adapter-service | care-delivery | contracts/openapi/pacs-adapter.openapi.yaml | services/experience-bff/.../client/PacsServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| pct-service | care-delivery | contracts/openapi/pct.openapi.yaml | services/experience-bff/.../client/PctServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| pharmacy-elmis-adapter | care-delivery | — | services/experience-bff/.../client/PharmacyServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| pharmacy-service | care-delivery | contracts/openapi/pharmacy.openapi.yaml | services/experience-bff/.../client/PharmacyServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| rules-service | clinical-knowledge | contracts/openapi/rules.openapi.yaml | services/experience-bff/.../client/RulesServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| scheduling-service | care-delivery | contracts/openapi/scheduling.openapi.yaml | services/experience-bff/.../client/SchedulingServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |


## Data plane

| Metric | Count |
|--------|-------|
| Services | 12 |
| Live | 0 |
| Partial | 12 |
| Not Wired | 0 |

| serviceId | domain | contract | bffClient | uiHook | webRoute | maturity | apiContract | nextAction |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ai-model-registry-service | intelligence | contracts/openapi/ai-model-registry.openapi.yaml | services/experience-bff/.../client/AiModelRegistryServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| campaigns-service | public-health-campaigns | contracts/openapi/campaigns.openapi.yaml | services/experience-bff/.../client/CampaignsServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| data-access-governance-service | intelligence | contracts/openapi/data-access-governance.openapi.yaml | — | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| data-governance-service | intelligence | contracts/openapi/data-governance.openapi.yaml | services/experience-bff/.../client/DataGovernanceServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| data-ingestion-service | intelligence | contracts/openapi/data-ingestion.openapi.yaml | services/experience-bff/.../client/DataIngestionServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| data-pipeline-service | intelligence | contracts/openapi/data-pipeline.openapi.yaml | services/experience-bff/.../client/DataPipelineServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| data-warehouse-service | intelligence | contracts/openapi/data-warehouse.openapi.yaml | services/experience-bff/.../client/DataWarehouseServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| national-data-repository-service | intelligence | contracts/openapi/national-data-repository.openapi.yaml | — | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| ndr-service | intelligence | contracts/openapi/ndr.openapi.yaml | services/experience-bff/.../client/NdrQueryServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| reporting-service | intelligence | contracts/openapi/reporting.openapi.yaml | services/experience-bff/.../client/ReportingServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| search-service | intelligence | contracts/openapi/search.openapi.yaml | services/experience-bff/.../client/SearchServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| surveillance-service | public-health-surveillance | contracts/openapi/surveillance.openapi.yaml | services/experience-bff/.../client/SurveillanceServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |


## Integration plane

| Metric | Count |
|--------|-------|
| Services | 24 |
| Live | 0 |
| Partial | 24 |
| Not Wired | 0 |

| serviceId | domain | contract | bffClient | uiHook | webRoute | maturity | apiContract | nextAction |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| analytics-pipeline-service | platform-ops | contracts/openapi/analytics-pipeline.openapi.yaml | — | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| asset-registry-service | platform-ops | contracts/openapi/asset-registry.openapi.yaml | services/experience-bff/.../client/AssetRegistryServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| audit-ledger-service | platform-ops | contracts/openapi/audit-ledger.openapi.yaml | — | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| card-print-agent | interoperability | — | — | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| channels-service | interoperability | contracts/openapi/channels.openapi.yaml | services/experience-bff/.../client/ChannelsServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| connector-fhir-adapter | interoperability | — | — | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| developer-portal-service | platform-ops | contracts/openapi/developer-portal.openapi.yaml | — | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| dispatch-service | platform-ops | contracts/openapi/dispatch.openapi.yaml | services/experience-bff/.../client/DispatchServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| integration-hub | interoperability | contracts/openapi/integration-hub.openapi.yaml | services/experience-bff/.../client/IntegrationHubServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| iot-ingestion-service | platform-ops | contracts/openapi/iot-ingestion.openapi.yaml | — | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| jobs-service | interoperability | contracts/openapi/jobs.openapi.yaml | — | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| landela-adapter-service | interoperability | contracts/openapi/landela-adapter.openapi.yaml | services/experience-bff/.../client/LandelaServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| llm-orchestration-service | platform-ops | — | — | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| ndila-service | interoperability | contracts/openapi/ndila.openapi.yaml | services/experience-bff/.../client/NdilaServiceClient.java | lib/ndila/ndila-client.ts | NdilaIntelligencePanel | Partial | partial | Reusable map component rollout |
| nhume-service | interoperability | — | services/experience-bff/.../client/NhumeServiceClient.java | lib/nhume.ts, useDispatchOps.ts | /nhume/*, /operations/dispatch | Partial | partial | Unified operator UX + maturity labels |
| notification-service | interoperability | contracts/openapi/notification.openapi.yaml | services/experience-bff/.../client/NotificationServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| observability-service | platform-ops | contracts/openapi/observability.openapi.yaml | — | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| offline-edge-service | platform-ops | contracts/openapi/offline-edge.openapi.yaml | — | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| offline-sync-service | interoperability | contracts/openapi/offline-sync.openapi.yaml | — | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| referral-service | platform-ops | contracts/openapi/referral.openapi.yaml | — | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| schema-registry-service | platform-ops | contracts/openapi/schema-registry.openapi.yaml | — | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| security-hardening-service | platform-ops | contracts/openapi/security-hardening.openapi.yaml | — | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| support-service | platform-ops | contracts/openapi/support.openapi.yaml | services/experience-bff/.../client/SupportServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| workflow-service | interoperability | contracts/openapi/workflow.openapi.yaml | services/experience-bff/.../client/WorkflowServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |


## Experience plane

| Metric | Count |
|--------|-------|
| Services | 3 |
| Live | 3 |
| Partial | 0 |
| Not Wired | 0 |

| serviceId | domain | contract | bffClient | uiHook | webRoute | maturity | apiContract | nextAction |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| community-service | workflow-orchestration | contracts/openapi/community.openapi.yaml | services/experience-bff/.../client/CommunityServiceClient.java | — | — | Live | partial | Trace BFF + extend UI surfacing |
| experience-bff | workflow-orchestration | contracts/openapi/experience-bff.openapi.yaml | — | — | — | Live | partial | Trace BFF + extend UI surfacing |
| learning-service | workflow-orchestration | contracts/openapi/learning.openapi.yaml | services/experience-bff/.../client/LearningServiceClient.java | — | — | Live | partial | Trace BFF + extend UI surfacing |


## Enterprise plane

| Metric | Count |
|--------|-------|
| Services | 14 |
| Live | 0 |
| Partial | 14 |
| Not Wired | 0 |

| serviceId | domain | contract | bffClient | uiHook | webRoute | maturity | apiContract | nextAction |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| costing-engine-service | finance | — | — | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| coverage-service | finance | contracts/openapi/coverage.openapi.yaml | services/experience-bff/.../client/CoverageServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| credential-verification-service | finance | contracts/openapi/credential-verification.openapi.yaml | services/experience-bff/.../client/CredentialServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| general-ledger-service | enterprise-resource | contracts/openapi/general-ledger.openapi.yaml | services/experience-bff/.../client/GeneralLedgerServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| hr-payroll-service | enterprise-resource | contracts/openapi/hr-payroll.openapi.yaml | services/experience-bff/.../client/HrPayrollServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| msika-flow-service | marketplace | contracts/openapi/msika-flow.openapi.yaml | services/experience-bff/.../client/MsikaFlowServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| msika-service | marketplace | contracts/openapi/msika-core.openapi.yaml | services/experience-bff/.../client/MsikaFlowServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| mushe-wallet-service | finance | contracts/openapi/mushe-wallet.openapi.yaml | services/experience-bff/.../client/MusheWalletServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| mushex-service | finance | contracts/openapi/mushex.openapi.yaml | services/experience-bff/.../client/MushexServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| procurement-service | enterprise-resource | contracts/openapi/procurement.openapi.yaml | services/experience-bff/.../client/ProcurementServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| share-slip-service | finance | contracts/openapi/share-slip.openapi.yaml | — | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| simba-service | wellness-personal-health-data | contracts/openapi/simba.openapi.yaml | services/experience-bff/.../client/SimbaServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| wellness-service | wellness-compatibility-alias | contracts/openapi/wellness.openapi.yaml | services/experience-bff/.../client/WellnessServiceClient.java | — | — | Partial | partial | Trace BFF + extend UI surfacing |
| workforce-governance-service | workforce-operations | contracts/openapi/workforce-governance.openapi.yaml | — | — | — | Partial | partial | Trace BFF + extend UI surfacing |


## Golden path (proof per capability)

`route/screen → hook → BFF → sovereign service → contract`

Trust headers: `ui/one-ui-shell/src/lib/api-client.ts` → TSHEPO ext_authz → service.

## Regenerate related docs

```bash
node scripts/architecture/generate-plane-capability-ledger.mjs
node scripts/frontend/generate-parity-docs.mjs
node scripts/architecture/generate-parity-inventories.mjs
```
