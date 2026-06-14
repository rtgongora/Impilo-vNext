# Backend Capability to Frontend Surfacing Matrix

> Generated: 2026-06-14. Regenerate: `node scripts/frontend/generate-parity-docs.mjs`

## Summary

| Maturity | Count |
|----------|-------|
| Live | 17 |
| Partial | 20 |
| Fixture | 0 |
| Not Wired | 0 |
| Blocked | 0 |

## Matrix

| plane | domain | capability | backend | contract | webRoute | webClient | web | mobile | nompilo | maturity | priority | gap | action |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Trust | TSHEPO | Trust admin (policies, break-glass, devices, audit) | /internal/v1/admin/trust/* | contracts/openapi/tshepo-authz.openapi.yaml | /admin/trust, /settings/security | useTrustAdmin.ts | partial | partial | partial | Partial | HIGH | Device block UX still admin-only | Trust governance strip on settings; deepen device admin |
| Registry | VITO | Client search, register, profile, Health ID | /internal/v1/identity/*, /internal/v1/registry/* | contracts/openapi/vito.openapi.yaml | /id-services, /registry/* | useIdentity.ts, useClientRegistry.ts | partial | partial | partial | Partial | HIGH | Issuance queue / card ops not fully surfaced | Registry hub depth + mobile health-id parity |
| Registry | VARAPI | Provider registry, licenses, privileges, CPD | /internal/v1/registry/* | contracts/openapi/varapi.openapi.yaml | /registry/providers/* | useRegistry.ts, useLicenses.ts, useCpd.ts | partial | partial | no | Partial | HIGH | Council import / reconciliation queue thin | Verification workflow screens |
| Registry | TUSO | Facility/workspace registry, bookings | /internal/v1/facilities, /internal/v1/registry/* | contracts/openapi/tuso.openapi.yaml | /facility/*, /registry/facilities/* | useFacilities.ts, useTusoRegistry.ts | partial | partial | partial | Partial | HIGH | Control-tower / digital readiness dashboards thin | Facility operating model detail pages |
| Registry | Indawo | Public health site registry + geo capture | /internal/v1/public-health/site-registry/* | contracts/openapi/indawo.openapi.yaml | /public-health/site-registry/* | useSiteRegistry.ts, SiteRegistryGeoMapPanel, NdilaLocationPicker | yes | partial | no | Live | MEDIUM | Mobile site-registry list lacks geo edit | Provider mobile site location capture |
| Clinical | BUTANO | SHR summary, timeline, allergies, conditions | /internal/v1/summary/*, /internal/v1/timeline | contracts/openapi/butano.custom.openapi.yaml | /ehr/[patientId]/* | useSummary.ts, useTimeline.ts | yes | partial | partial | Live | MEDIUM | Mobile conditions/allergies TODO | Wire citizen personal sections to BFF |
| Clinical | Core Transaction | Transaction composition, journey steppers | /internal/v1/core-transactions/* | contracts/core-transaction.ts | /core-transaction | useCoreTransactionExperience.ts | partial | partial | partial | Partial | HIGH | Mobile journey shell shallow | Deepen command/handoff wiring |
| Data & Intelligence | Public Health Ops | Surveillance, investigations, campaigns, intelligence, field ops | /internal/v1/public-health/*, /internal/v1/mobile/provider/public-health/* | contracts/openapi/surveillance.openapi.yaml | /public-health/* | usePublicHealth.ts, useSurveillance.ts, useCampaigns.ts | yes | partial | partial | Live | HIGH | Citizen PH awareness thinner than provider web | Citizen outbreak/alert depth on mobile |
| Integration & Edge | Ndila | Geocode, routes, PH/site ops maps | /api/v1/ndila/* | contracts/openapi/ndila.openapi.yaml | NdilaPublicHealthRiskMap, SiteRegistryGeoMapPanel | lib/ndila/ndila-client.ts, NdilaMapLibre | yes | partial | no | Live | MEDIUM | Mobile Ndila map parity on field tasks | Provider field map overlay |
| Enterprise | Nhume | Dispatch, delivery, fleet tracking | /api/v1/nhume/*, /internal/v1/mobile/*/nhume/* | nhume-service controllers | /nhume/*, /operations/dispatch | lib/nhume.ts, useDispatchOps.ts | partial | partial | partial | Partial | HIGH | Dual path: nhume vs dispatch BFF | Unified operator UX + maturity labels |
| Experience | Comms Hub | Omnichannel, messaging, notifications | /internal/v1/omnichannel/*, /internal/v1/communication/* | contracts/openapi/channels.openapi.yaml | /communication, /omnichannel | useOmnichannel.ts, useCommunication.ts | partial | partial | partial | Partial | MEDIUM | Template/campaign admin depth | Comms dashboard actionable tasks |
| Clinical | Telemedicine | Teleconsult sessions, scheduling | /internal/v1/teleconsult/* | experience-bff.openapi.yaml | /telemedicine/* | useTelemedicine.ts | partial | partial | partial | Partial | HIGH | RTC media intentionally blocked | Label Blocked for RTC; live scheduling/records |
| Data | Telemedicine analytics | Telemedicine lifecycle SLA aggregates + event ingest | /internal/v1/telemedicine/sla, /internal/v1/telemedicine/events | analytics-pipeline-service (internal) | /telemedicine/analytics | useTelemedicineAnalytics.ts | yes | no | no | Live | MEDIUM | Mobile analytics dashboard | Provider telemedicine SLA strip |
| Data | Data Pipeline & NDR | Pipeline watermarks, warehouse gold, national dataset catalog | /internal/v1/pipeline/*, /internal/v1/warehouse/*, /internal/v1/ndr-catalog/* | data-pipeline-service, national-data-repository-service | /data-intelligence/pipelines | useDataPipelineWatermarks.ts, useWarehouse.ts, NdrWarehouseQueryPanel | yes | no | partial | Live | HIGH | Mobile data-ops visibility | Provider governance summary strip |
| Trust | Break-glass (provider request) | Emergency access override request from clinical/emergency shells | POST /internal/v1/trust/break-glass | tshepo-authz /v1/break-glass | /clinical/emergency, /ehr/[patientId]/emergency | useTrustBreakGlass.ts, BreakGlassRequestPanel.tsx | yes | partial | no | Partial | HIGH | Mobile provider break-glass still uses legacy mobile BFF stubs | Provider break-glass panel on ED + EHR emergency views |
| Enterprise | Msika / Msika Flow | Catalog, orders, marketplace | /internal/v1/marketplace/*, /internal/v1/commerce/* | contracts/openapi/msika-flow.openapi.yaml | /marketplace/* | useMarketplace.ts, useCommerceFlow.ts | partial | partial | no | Partial | MEDIUM | Order list routes 501 on some paths | Honest blocked states on list routes |
| Enterprise | MusheX / COSTA | Payments, claims, billing, tariffs | /internal/v1/finance/*, /internal/v1/wallet/* | contracts/openapi/mushex.openapi.yaml, costa.openapi.yaml | /finance/*, /wallet | useMusheWallet.ts, useFinanceBillingWorkspace.ts | partial | partial | no | Partial | HIGH | No raw /mushex/v1 in browser | Finance journey mobile parity |
| Experience | Fundo | LMS courses, studio, certificates | /internal/v1/learning/v11/* | contracts/openapi/learning.openapi.yaml | /learning/* | useFundoLms.ts, useFundoStudio.ts | partial | partial | partial | Partial | MEDIUM | Mobile learning shell shallow | Fundo mobile module depth |
| Experience | Social | Timeline, communities, pages | /internal/v1/social/* | contracts/openapi/social.openapi.yaml | /social, /communities, /pages | useSocial.ts | yes | yes | no | Live | LOW | Moderation admin partial | Moderation workflow surfacing |
| Registry | UBOMI | CRVS births/deaths | ubomi-service /v1/births | contracts/openapi/ubomi.openapi.yaml | /ubomi | useUbomiRegistry.ts (new) | partial | no | no | Partial | HIGH | Mobile CRVS parity missing | UBOMI births/deaths/verify live when service up; mobile parity |
| Registry | ZIBO | Terminology governance | /v1/artifacts, /v1/packs | contracts/openapi/zibo.openapi.yaml | ui/zibo-web | ziboApi.ts | partial | n/a | no | Partial | LOW | Separate zibo-web app only | Shell link + maturity on terminology nav |
| Experience | Nompilo | Guidance, LLM chat, core-transaction assist | /internal/v1/guidance/*, /internal/v1/llm/* | experience-bff.openapi.yaml | /ask, global command bar | useGuidance.ts, NompiloGlobalCommandBar | partial | partial | partial | Partial | HIGH | Route context not always passed | Context query params + fallback label |
| Integration | Integration Hub | Routes, dead letters, dispatch | /internal/v1/integration-hub/* | contracts/openapi/integration-hub.openapi.yaml | /admin/integration-status, /settings/integrations | useIntegrationHub.ts | partial | partial | no | Partial | MEDIUM | Adapter template admin thin | Integration admin depth |
| Platform | Workflow / Dispatch | Workflow definitions, instances, dispatch tasks | /internal/v1/workflows/*, /internal/v1/dispatch/* | contracts/openapi/workflow.openapi.yaml | /operations/workflows, /operations/dispatch | useDispatchOps.ts | partial | partial | partial | Partial | HIGH | Dispatch detail + offline queue UX | Workflow instance table + dispatch guided detail |
| Platform | Admin / Governance | Users, tenants, roles, audit, feature flags | /internal/v1/admin/* | experience-bff.openapi.yaml | /admin/*, /organization-admin/* | useAdminUsers.ts, useTrustAdmin.ts | partial | partial | no | Partial | MEDIUM | Keys/federation blocked | Document Blocked surfaces explicitly |
| Clinical | MADI | Donor engagement (register, profile, eligibility, feedback) | /internal/v1/madi/donors/*, /internal/v1/mobile/citizen/madi/* | contracts/openapi/madi.openapi.yaml | /madi/donor/* | useMadi.ts | Live | Live | Live | Live | HIGH | — | Guided pre-screening + Nompilo assist on web and citizen mobile |
| Clinical | MADI | Donation drive scheduling and field capture | /internal/v1/madi/drives/*, /internal/v1/mobile/provider/madi/drives/* | contracts/openapi/madi.openapi.yaml | /madi/drives/* | useMadi.ts | Live | Live | no | Live | HIGH | — | Offline queue + sync-conflict resolution on provider mobile |
| Clinical | MADI | Blood processing and component labelling | /internal/v1/madi/processing/* | contracts/openapi/madi.openapi.yaml | /madi/processing | useMadi.ts | Live | no | no | Live | MEDIUM | — | ZIBO SNOMED deep-links on /madi/processing component selector |
| Clinical | MADI | Blood bank stock and inventory balance | /internal/v1/madi/blood-banks/* | contracts/openapi/madi.openapi.yaml | /madi/blood-bank/* | useMadi.ts | Live | no | no | Live | HIGH | — | IoT fridge monitoring at /madi/blood-bank/fridges |
| Clinical | MADI | Clinical blood order (crossmatch, reserve, issue) | /internal/v1/madi/orders/*, /internal/v1/mobile/provider/madi/orders/* | contracts/openapi/madi.openapi.yaml | /madi/orders/* | useMadi.ts | Live | Live | partial | Live | HIGH | — | OROS lab worklist deep-link on order detail |
| Clinical | MADI | Transfusion episode and observation capture | /internal/v1/madi/transfusions/*, /internal/v1/mobile/provider/madi/transfusions/* | contracts/openapi/madi.openapi.yaml | /madi/transfusion/* | useMadi.ts | Live | Live | partial | Live | HIGH | — | VITO biometric + barcode bedside verify on web and mobile |
| Clinical | MADI | Haemovigilance (adverse reaction reporting) | /internal/v1/madi/haemovigilance/*, /internal/v1/mobile/provider/madi/haemovigilance/* | contracts/openapi/madi.openapi.yaml | /madi/haemovigilance | useMadi.ts | Live | Live | no | Live | HIGH | — | National roll-up at /madi/haemovigilance/national |
| Clinical | MADI | Central blood bank coordination | /internal/v1/madi/central-bank/* | contracts/openapi/madi.openapi.yaml | /madi/central-bank | useMadi.ts | Live | no | no | Live | MEDIUM | — | Emergency redistribution request + approve on /madi/central-bank |
| Experience | Impilo Live | Live events, webinars, broadcasts | /internal/v1/live/* | contracts/openapi/impilo-live.openapi.yaml | /live, /live/discover, /live/event/[eventId] | useLive.ts | yes | yes | yes | Live | MEDIUM | Host controls on mobile partial | Deepen provider host/moderator mobile controls |
| Experience | Health OS Launcher | Role/facility-aware app launcher + marketplace tiles | /internal/v1/launcher/apps, /internal/v1/launcher/apps/{appCode}/state | contracts/openapi/experience-bff.openapi.yaml | ShellStartMenu (Start menu) | useHealthOsLauncher.ts | yes | partial | no | Partial | HIGH | Mobile launcher parity | Shell Start menu uses BFF launcher/apps contract surface |
| Experience | Wellness / Monitoring | Citizen remote monitoring device pair/list/sync | /internal/v1/mobile/citizen/monitoring/devices | contracts/openapi/experience-bff.openapi.yaml | /monitoring/devices | useCitizenMonitoring.ts, citizen-monitoring-api.ts | yes | yes | no | Partial | MEDIUM | Readings timeline depth | BFF explicit proxy to wellness-service; web + citizen mobile wired |
| Clinical | MADI | MADI dashboards and programme KPIs | /internal/v1/madi/dashboard | contracts/openapi/madi.openapi.yaml | /madi/dashboard | useMadi.ts | Live | no | partial | Live | MEDIUM | — | 30-day forecast table on /madi/dashboard from order + stock signals |

## Trust headers (all BFF paths)

Mandatory where applicable: `X-Tenant-Id`, `X-Correlation-Id`, `X-Device-Fingerprint`, `X-Purpose-Of-Use`, `X-Actor-Id`, `X-Actor-Type`, `X-Facility-Id`, `X-Workspace-Id`, `X-Shift-Id`.

Injected by `ui/one-ui-shell/src/lib/api-client.ts` and `apps/mobile/packages/mobile-trust`.

## Intentional non-BFF exceptions

| Client | Path | Reason |
|--------|------|--------|
| Nhume web | `/api/v1/nhume/*` | Legacy logistics gateway |
| Ndila mobile | `/api/v1/ndila/*` | Geospatial SDK |
| ZIBO admin | `/v1/*` via zibo-web | Sovereign terminology console |
