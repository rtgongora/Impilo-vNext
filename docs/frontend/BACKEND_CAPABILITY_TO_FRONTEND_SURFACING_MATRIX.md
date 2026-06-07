# Backend Capability to Frontend Surfacing Matrix

> Generated: 2026-06-07. Regenerate: `node scripts/frontend/generate-parity-docs.mjs`

## Summary

| Maturity | Count |
|----------|-------|
| Live | 11 |
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
| Registry | Indawo | Public health site registry | /internal/v1/public-health/site-registry/* | contracts/openapi/indawo.openapi.yaml | /public-health/* | usePublicHealth.ts, useSiteRegistry.ts | partial | partial | no | Partial | MEDIUM | Map layer integration incomplete | Ndila map panel on site registry |
| Clinical | BUTANO | SHR summary, timeline, allergies, conditions | /internal/v1/summary/*, /internal/v1/timeline | contracts/openapi/butano.custom.openapi.yaml | /ehr/[patientId]/* | useSummary.ts, useTimeline.ts | yes | partial | partial | Live | MEDIUM | Mobile conditions/allergies TODO | Wire citizen personal sections to BFF |
| Clinical | Core Transaction | Transaction composition, journey steppers | /internal/v1/core-transactions/* | contracts/core-transaction.ts | /core-transaction | useCoreTransactionExperience.ts | partial | partial | partial | Partial | HIGH | Mobile journey shell shallow | Deepen command/handoff wiring |
| Data & Intelligence | Public Health Ops | Inspections, outbreaks, campaigns, intelligence | /internal/v1/public-health/* | contracts/openapi/surveillance.openapi.yaml | /public-health/* | usePublicHealth.ts, useSurveillance.ts | partial | partial | partial | Partial | HIGH | Field ops mobile thinner than web | Provider field tasks parity |
| Integration & Edge | Ndila | Geocode, routes, intelligence layers | /api/v1/ndila/* | contracts/openapi/ndila.openapi.yaml | NdilaIntelligencePanel | lib/ndila/ndila-client.ts | partial | partial | no | Partial | MEDIUM | Web ops map dashboards incomplete | Reusable map component rollout |
| Enterprise | Nhume | Dispatch, delivery, fleet tracking | /api/v1/nhume/*, /internal/v1/mobile/*/nhume/* | nhume-service controllers | /nhume/*, /operations/dispatch | lib/nhume.ts, useDispatchOps.ts | partial | partial | partial | Partial | HIGH | Dual path: nhume vs dispatch BFF | Unified operator UX + maturity labels |
| Experience | Comms Hub | Omnichannel, messaging, notifications | /internal/v1/omnichannel/*, /internal/v1/communication/* | contracts/openapi/channels.openapi.yaml | /communication, /omnichannel | useOmnichannel.ts, useCommunication.ts | partial | partial | partial | Partial | MEDIUM | Template/campaign admin depth | Comms dashboard actionable tasks |
| Clinical | Telemedicine | Teleconsult sessions, scheduling | /internal/v1/teleconsult/* | experience-bff.openapi.yaml | /telemedicine/* | useTelemedicine.ts | partial | partial | partial | Partial | HIGH | RTC media intentionally blocked | Label Blocked for RTC; live scheduling/records |
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
| Clinical | MADI | Donor engagement (register, profile, eligibility, feedback) | /internal/v1/madi/donors/*, /internal/v1/mobile/citizen/madi/* | contracts/openapi/madi.openapi.yaml | /madi/donor/* | useMadi.ts | Live | Live | partial | Live | HIGH | Nompilo donor assist depth | Expand Nompilo guided pre-screening |
| Clinical | MADI | Donation drive scheduling and field capture | /internal/v1/madi/drives/*, /internal/v1/mobile/provider/madi/drives/* | contracts/openapi/madi.openapi.yaml | /madi/drives/* | useMadi.ts | Live | Live | no | Live | HIGH | Offline drive capture conflict UX | Harden offline sync for drive field capture |
| Clinical | MADI | Blood processing and component labelling | /internal/v1/madi/processing/* | contracts/openapi/madi.openapi.yaml | /madi/processing | useMadi.ts | Live | no | no | Live | MEDIUM | ZIBO terminology deep-link | Surface component catalogue codes in UI |
| Clinical | MADI | Blood bank stock and inventory balance | /internal/v1/madi/blood-banks/* | contracts/openapi/madi.openapi.yaml | /madi/blood-bank/* | useMadi.ts | Live | no | no | Live | HIGH | IoT fridge temperature integration | Wire cold-chain sensor readiness |
| Clinical | MADI | Clinical blood order (crossmatch, reserve, issue) | /internal/v1/madi/orders/*, /internal/v1/mobile/provider/madi/orders/* | contracts/openapi/madi.openapi.yaml | /madi/orders/* | useMadi.ts | Live | Live | partial | Live | HIGH | OROS ServiceRequest deep-link from order detail | Surface OROS order ref in order tracking UI |
| Clinical | MADI | Transfusion episode and observation capture | /internal/v1/madi/transfusions/*, /internal/v1/mobile/provider/madi/transfusions/* | contracts/openapi/madi.openapi.yaml | /madi/transfusion/* | useMadi.ts | Live | Live | partial | Live | HIGH | Biometric patient verification | Wire Impilo ID verification when available |
| Clinical | MADI | Haemovigilance (adverse reaction reporting) | /internal/v1/madi/haemovigilance/*, /internal/v1/mobile/provider/madi/haemovigilance/* | contracts/openapi/madi.openapi.yaml | /madi/haemovigilance | useMadi.ts | Live | Live | no | Live | HIGH | National haemovigilance roll-up | Public-health supervisory dashboard depth |
| Clinical | MADI | Central blood bank coordination | /internal/v1/madi/central-bank/* | contracts/openapi/madi.openapi.yaml | /madi/central-bank | useMadi.ts | Live | no | no | Live | MEDIUM | Emergency redistribution workflow | Add emergency allocation actions |
| Clinical | MADI | MADI dashboards and programme KPIs | /internal/v1/madi/dashboard | contracts/openapi/madi.openapi.yaml | /madi/dashboard | useMadi.ts | Live | no | partial | Live | MEDIUM | 30-day demand forecast depth | Expand forecasting models |

## Trust headers (all BFF paths)

Mandatory where applicable: `X-Tenant-Id`, `X-Correlation-Id`, `X-Device-Fingerprint`, `X-Purpose-Of-Use`, `X-Actor-Id`, `X-Actor-Type`, `X-Facility-Id`, `X-Workspace-Id`, `X-Shift-Id`.

Injected by `ui/one-ui-shell/src/lib/api-client.ts` and `apps/mobile/packages/mobile-trust`.

## Intentional non-BFF exceptions

| Client | Path | Reason |
|--------|------|--------|
| Nhume web | `/api/v1/nhume/*` | Legacy logistics gateway |
| Ndila mobile | `/api/v1/ndila/*` | Geospatial SDK |
| ZIBO admin | `/v1/*` via zibo-web | Sovereign terminology console |
