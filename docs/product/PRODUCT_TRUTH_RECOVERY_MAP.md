# Product Truth Recovery Map

> Generated: 2026-07-13T19:22:11.174Z
> Branch: `claude/staging-ux-orchestration-remediation-Yypyl`
> Regenerate: `node scripts/product/generate-product-truth-recovery.mjs`

## Purpose

Authoritative Phase 1 discovery map reconciling registry, contracts, BFF, backend controllers, web routes, mobile screens, hooks, events, migrations, doctrine, and infrastructure into one product-truth inventory.

**Exhaustive machine-readable exports:**
- [product-truth-recovery-map.json](../../reports/product/product-truth-recovery-map.json) — 3355 entries
- [product-truth-recovery-map.csv](../../reports/product/product-truth-recovery-map.csv) — same data, CSV
- [product-truth-rollups.md](../../reports/product/product-truth-rollups.md) — summary counts

## Executive summary

| Dimension | Discovered |
|-----------|----------:|
| Total items | 3355 |
| Backend services | 97 |
| APIs/contracts | 120 |
| Frontend routes | 607 |
| Mobile screens | 204 |
| BFF route prefixes | 281 |
| Unknown-needs-review | 258 |

## Canonical capabilities (embedded registry)

| capability | plane | web | mobile | maturity | blocker |
| --- | --- | --- | --- | --- | --- |
| TSHEPO: Trust admin (policies, break-glass, devices, audit) | Trust/TSHEPO | partial | partial | Partial | Device block UX still admin-only |
| VITO: Client search, register, profile, Health ID | Registry/VITO | partial | partial | Partial | Issuance queue / card ops not fully surfaced |
| VARAPI: Provider registry, licenses, privileges, CPD | Registry/VARAPI | partial | partial | Partial | Council import / reconciliation queue thin |
| TUSO: Facility/workspace registry, bookings | Registry/TUSO | partial | partial | Partial | Control-tower / digital readiness dashboards thin |
| Indawo: Public health site registry + geo capture | Registry/Indawo | yes | partial | Live | Mobile site-registry list lacks geo edit |
| BUTANO: SHR summary, timeline, allergies, conditions | Clinical/BUTANO | yes | partial | Live | Mobile conditions/allergies TODO |
| Core Transaction: Transaction composition, journey steppers | Clinical/Core Transaction | partial | partial | Partial | Mobile journey shell shallow |
| Public Health Ops: Surveillance, investigations, campaigns, intelligence, field  | Data & Intelligence/Public Health Ops | yes | partial | Live | Citizen PH awareness thinner than provider web |
| Ndila: Geocode, routes, PH/site ops maps | Integration & Edge/Ndila | yes | partial | Live | Mobile Ndila map parity on field tasks |
| Nhume: Dispatch, delivery, fleet tracking | Enterprise/Nhume | partial | partial | Partial | Dual path: nhume vs dispatch BFF |
| Vashandi: Operational workforce roster, attendance, leave, facility staff | Enterprise/Vashandi | partial | partial | Partial | Web route scaffolds thin; mobile lacks assignment/admin depth |
| Comms Hub: Omnichannel, messaging, notifications | Experience/Comms Hub | partial | partial | Partial | Template/campaign admin depth |
| Telemedicine: Teleconsult sessions, scheduling | Clinical/Telemedicine | partial | partial | Partial | RTC media intentionally blocked |
| Telemedicine analytics: Telemedicine lifecycle SLA aggregates + event ingest | Data/Telemedicine analytics | yes | no | Live | Mobile analytics dashboard |
| Data Pipeline & NDR: Pipeline watermarks, warehouse gold, national dataset catal | Data/Data Pipeline & NDR | yes | no | Live | Mobile data-ops visibility |
| Break-glass (provider request): Emergency access override request from clinical/ | Trust/Break-glass (provider request) | yes | partial | Partial | Mobile provider break-glass still uses legacy mobile BFF stubs |
| Msika / Msika Flow: Catalog, orders, marketplace | Enterprise/Msika / Msika Flow | partial | partial | Partial | Order list routes 501 on some paths |
| MusheX / COSTA: Payments, claims, billing, tariffs | Enterprise/MusheX / COSTA | partial | partial | Partial | No raw /mushex/v1 in browser |
| Fundo: LMS courses, studio, certificates | Experience/Fundo | partial | partial | Partial | Mobile learning shell shallow |
| Social: Timeline, communities, pages | Experience/Social | yes | yes | Live | Moderation admin partial |
| UBOMI: CRVS births/deaths | Registry/UBOMI | partial | no | Partial | Mobile CRVS parity missing |
| ZIBO: Terminology governance | Registry/ZIBO | partial | n/a | Partial | Separate zibo-web app only |
| Nompilo: Guidance, LLM chat, core-transaction assist | Experience/Nompilo | partial | partial | Partial | Route context not always passed |
| Integration Hub: Routes, dead letters, dispatch | Integration/Integration Hub | partial | partial | Partial | Adapter template admin thin |
| Workflow / Dispatch: Workflow definitions, instances, dispatch tasks | Platform/Workflow / Dispatch | partial | partial | Partial | Dispatch detail + offline queue UX |
| Admin / Governance: Users, tenants, roles, audit, feature flags | Platform/Admin / Governance | partial | partial | Partial | Keys/federation blocked |
| MADI: Donor engagement (register, profile, eligibility, feedback) | Clinical/MADI | Live | Live | Live | — |
| MADI: Donation drive scheduling and field capture | Clinical/MADI | Live | Live | Live | — |
| MADI: Blood processing and component labelling | Clinical/MADI | Live | no | Live | — |
| MADI: Blood bank stock and inventory balance | Clinical/MADI | Live | no | Live | — |
| MADI: Clinical blood order (crossmatch, reserve, issue) | Clinical/MADI | Live | Live | Live | — |
| MADI: Transfusion episode and observation capture | Clinical/MADI | Live | Live | Live | — |
| MADI: Haemovigilance (adverse reaction reporting) | Clinical/MADI | Live | Live | Live | — |
| MADI: Central blood bank coordination | Clinical/MADI | Live | no | Live | — |
| Impilo Live: Live events, webinars, broadcasts | Experience/Impilo Live | yes | yes | Live | Host controls on mobile partial |
| Health OS Launcher: Role/facility-aware app launcher + marketplace tiles | Experience/Health OS Launcher | yes | partial | Partial | Mobile launcher parity |
| Wellness / Monitoring: Citizen remote monitoring device pair/list/sync | Experience/Wellness / Monitoring | yes | yes | Partial | Readings timeline depth |
| MADI: MADI dashboards and programme KPIs | Clinical/MADI | Live | no | Live | — |

## Backend services (registry)

| service | plane | status | contract | frontend | blocker |
| --- | --- | --- | --- | --- | --- |
| ai-model-registry-service | data/intelligence | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/ai-model-registry.openapi.yaml;  | partial |  |
| analytics-pipeline-service | integration/platform-ops | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/analytics-pipeline.openapi.yaml; | partial |  |
| asset-registry-service | integration/platform-ops | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/asset-registry.openapi.yaml; sta | partial |  |
| audit-ledger-service | integration/platform-ops | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/audit-ledger.openapi.yaml; statu | partial |  |
| booking-service | experience/workflow-orchestration | production=baseline-assessed; impl=implemented-or-partial; frontend=wired | contract: contracts/openapi/booking.openapi.yaml; status=par | yes |  |
| butano-fhir | clinical/care-delivery | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/butano-fhir.openapi.yaml; status | partial | Mobile conditions/allergies TODO |
| butano-service | clinical/care-delivery | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | no-matched-openapi; status=partial | partial | Mobile conditions/allergies TODO |
| campaigns-service | data/public-health-campaigns | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/campaigns.openapi.yaml; status=p | partial |  |
| card-print-agent | integration/interoperability | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | no-matched-openapi; status=partial | partial |  |
| channels-service | integration/interoperability | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/channels.openapi.yaml; status=pa | partial |  |
| clinical-knowledge-platform-service | clinical/clinical-knowledge | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/clinical-knowledge-platform.open | partial |  |
| community-service | experience/workflow-orchestration | production=baseline-assessed; impl=implemented-or-partial; frontend=wired | contract: contracts/openapi/community.openapi.yaml; status=p | yes |  |
| connector-fhir-adapter | integration/interoperability | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | no-matched-openapi; status=partial | partial |  |
| costing-engine-service | enterprise/finance | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | no-matched-openapi; status=partial | partial |  |
| coverage-service | enterprise/finance | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/coverage.openapi.yaml; status=pa | partial |  |
| daidzai-service | experience/workflow-orchestration | production=baseline-assessed; impl=implemented-or-partial; frontend=wired | contract: contracts/openapi/daidzai.openapi.yaml; status=par | yes |  |
| credential-verification-service | enterprise/finance | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/credential-verification.openapi. | partial |  |
| data-access-governance-service | data/intelligence | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/data-access-governance.openapi.y | partial |  |
| data-governance-service | data/intelligence | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/data-governance.openapi.yaml; st | partial |  |
| data-ingestion-service | data/intelligence | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/data-ingestion.openapi.yaml; sta | partial |  |
| data-pipeline-service | data/intelligence | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/data-pipeline.openapi.yaml; stat | partial |  |
| data-warehouse-service | data/intelligence | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/data-warehouse.openapi.yaml; sta | partial |  |
| developer-portal-service | integration/platform-ops | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/developer-portal.openapi.yaml; s | partial |  |
| dispatch-service | integration/platform-ops | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/dispatch.openapi.yaml; status=pa | partial |  |
| document-service | clinical/care-delivery | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | no-matched-openapi; status=partial | partial |  |
| experience-bff | experience/workflow-orchestration | production=baseline-assessed; impl=implemented-or-partial; frontend=wired | contract: contracts/openapi/experience-bff.openapi.yaml; sta | yes |  |
| fhir-gateway-service | clinical/care-delivery | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/fhir-gateway.openapi.yaml; statu | partial |  |
| forms-service | clinical/clinical-knowledge | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/forms.openapi.yaml; status=parti | partial |  |
| general-ledger-service | enterprise/enterprise-resource | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/general-ledger.openapi.yaml; sta | partial |  |
| guidance-service | clinical/clinical-knowledge | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/guidance.openapi.yaml; status=pa | partial |  |
| hr-payroll-service | enterprise/enterprise-resource | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/hr-payroll.openapi.yaml; status= | partial |  |
| identity-assurance-service | trust/identity-governance | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/identity-assurance.openapi.yaml; | partial |  |
| indawo-service | registry/registry-spine | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/indawo.openapi.yaml; status=part | partial | Mobile site-registry list lacks geo edit |
| inpatient-service | clinical/care-delivery | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/inpatient.openapi.yaml; status=p | partial |  |
| integration-hub | integration/interoperability | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/integration-hub.openapi.yaml; st | partial |  |
| inventory-elmis-adapter | clinical/care-delivery | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | no-matched-openapi; status=partial | partial |  |
| inventory-service | clinical/care-delivery | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/inventory.openapi.yaml; status=p | partial |  |
| iot-ingestion-service | integration/platform-ops | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/iot-ingestion.openapi.yaml; stat | partial |  |
| jobs-service | integration/interoperability | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/jobs.openapi.yaml; status=partia | partial |  |
| landela-adapter-service | integration/interoperability | production=baseline-assessed; impl=implemented-or-partial; frontend=unknown-or-p | contract: contracts/openapi/landela-adapter.openapi.yaml; st | partial |  |

_…and 57 more rows in JSON/CSV._


## Unregistered frontend pages (needs review)

| route | path | blocker |
| --- | --- | --- |
| /access/governance | ui/one-ui-shell/src/app/access/governance/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /budgets/[budgetId] | ui/one-ui-shell/src/app/budgets/[budgetId]/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /budgets | ui/one-ui-shell/src/app/budgets/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /clinical/emergency/[visitId] | ui/one-ui-shell/src/app/clinical/emergency/[visitId]/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /clinical/inpatient/admissions/new | ui/one-ui-shell/src/app/clinical/inpatient/admissions/new/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /developer/event-catalogue | ui/one-ui-shell/src/app/developer/event-catalogue/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /ehr/[patientId]/charts/[chartId] | ui/one-ui-shell/src/app/ehr/[patientId]/charts/[chartId]/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /ehr/[patientId]/emergency | ui/one-ui-shell/src/app/ehr/[patientId]/emergency/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /ehr/[patientId]/encounter | ui/one-ui-shell/src/app/ehr/[patientId]/encounter/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /ehr/[patientId]/procedures/[episodeId] | ui/one-ui-shell/src/app/ehr/[patientId]/procedures/[episodeId]/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /ehr/[patientId]/workspace/[specialty] | ui/one-ui-shell/src/app/ehr/[patientId]/workspace/[specialty]/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /facility/[id]/cockpit | ui/one-ui-shell/src/app/facility/[id]/cockpit/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /facility/[id]/control-tower | ui/one-ui-shell/src/app/facility/[id]/control-tower/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /facility/[id]/departments | ui/one-ui-shell/src/app/facility/[id]/departments/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /facility/[id]/regulators | ui/one-ui-shell/src/app/facility/[id]/regulators/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /facility/[id]/setup | ui/one-ui-shell/src/app/facility/[id]/setup/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /groups/[id] | ui/one-ui-shell/src/app/groups/[id]/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /groups | ui/one-ui-shell/src/app/groups/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /indawo/field-teams | ui/one-ui-shell/src/app/indawo/field-teams/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /indawo/outbreaks | ui/one-ui-shell/src/app/indawo/outbreaks/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /indawo | ui/one-ui-shell/src/app/indawo/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /indawo/surveillance | ui/one-ui-shell/src/app/indawo/surveillance/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /inventory/items | ui/one-ui-shell/src/app/inventory/items/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /inventory/reconciliation | ui/one-ui-shell/src/app/inventory/reconciliation/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /inventory/stock | ui/one-ui-shell/src/app/inventory/stock/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /landela | ui/one-ui-shell/src/app/landela/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /learning/admin/academic | ui/one-ui-shell/src/app/learning/admin/academic/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /learning/admin/accreditation | ui/one-ui-shell/src/app/learning/admin/accreditation/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /learning/admin/moderation | ui/one-ui-shell/src/app/learning/admin/moderation/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /learning/admin/providers | ui/one-ui-shell/src/app/learning/admin/providers/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /learning/admissions | ui/one-ui-shell/src/app/learning/admissions/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /learning/reports/dashboards | ui/one-ui-shell/src/app/learning/reports/dashboards/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /learning/sessions/[sessionId]/checkin | ui/one-ui-shell/src/app/learning/sessions/[sessionId]/checkin/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /learning/sessions/[sessionId]/classroom | ui/one-ui-shell/src/app/learning/sessions/[sessionId]/classroom/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /learning/sessions/[sessionId] | ui/one-ui-shell/src/app/learning/sessions/[sessionId]/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /learning/sessions | ui/one-ui-shell/src/app/learning/sessions/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /learning/spaces/[spaceId] | ui/one-ui-shell/src/app/learning/spaces/[spaceId]/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /learning/students/[studentId] | ui/one-ui-shell/src/app/learning/students/[studentId]/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /learning/studio/delivery | ui/one-ui-shell/src/app/learning/studio/delivery/page.tsx | not in routes.ts — guard/sidebar coverage gap |
| /learning/teach/marking | ui/one-ui-shell/src/app/learning/teach/marking/page.tsx | not in routes.ts — guard/sidebar coverage gap |

_…and 126 more rows in JSON/CSV._


## Entry schema

Each entry in JSON/CSV contains:

`canonicalName`, `aliases`, `sourcePath`, `componentType`, `serviceDomainPlane`, `capabilityDescription`, `backendApiContractStatus`, `frontendSurfaceExpected`, `currentFrontendRouteComponent`, `mobileSurfaceExpected`, `currentMobileScreenComponent`, `apiClientHook`, `workflowEventDependency`, `databaseMigrationDependency`, `trustSecurityDependency`, `relatedServices`, `currentCompletenessStatus`, `currentBlocker`, `recommendedClassification`

Classification vocabulary: `backend-capability`, `api-contract`, `frontend-route`, `mobile-screen`, `bff-api-facade`, `worker-job`, `integration-adapter`, `registry-service`, `trust-security-service`, `clinical-service`, `fhir-shared-record-service`, `finance-transaction-service`, `document-imaging-service`, `data-analytics-ai-service`, `offline-federation-service`, `infrastructure-dependency`, `shared-internal-library`, `generated-client`, `doctrine-only`, `deprecated-retired`, `unknown-needs-review`.
