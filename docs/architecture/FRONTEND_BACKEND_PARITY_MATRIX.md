# Frontend ↔ backend parity matrix

> Generated: 2026-06-05. Regenerate: `node scripts/architecture/generate-parity-inventories.mjs`

| capability | endpoint | webRoute | webClient | realData | mockRisk | parity | priority | remediation | gate |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TSHEPO: Trust admin (policies, break-glass, devices, audit) | /internal/v1/admin/trust/* | /admin/trust, /settings/security | useTrustAdmin.ts | partial | no | partial | HIGH | Trust governance strip on settings; deepen device admin | advisory |
| VITO: Client search, register, profile, Health ID | /internal/v1/identity/*, /internal/v1/registry/* | /id-services, /registry/* | useIdentity.ts, useClientRegistry.ts | partial | no | partial | HIGH | Registry hub depth + mobile health-id parity | advisory |
| VARAPI: Provider registry, licenses, privileges, CPD | /internal/v1/registry/* | /registry/providers/* | useRegistry.ts, useLicenses.ts, useCpd.ts | partial | no | partial | HIGH | Verification workflow screens | advisory |
| TUSO: Facility/workspace registry, bookings | /internal/v1/facilities, /internal/v1/registry/* | /facility/*, /registry/facilities/* | useFacilities.ts, useTusoRegistry.ts | partial | no | partial | HIGH | Facility operating model detail pages | advisory |
| Indawo: Public health site registry | /internal/v1/public-health/site-registry/* | /public-health/* | usePublicHealth.ts, useSiteRegistry.ts | partial | no | partial | MEDIUM | Ndila map panel on site registry | advisory |
| BUTANO: SHR summary, timeline, allergies, conditions | /internal/v1/summary/*, /internal/v1/timeline | /ehr/[patientId]/* | useSummary.ts, useTimeline.ts | yes | no | complete | MEDIUM | Wire citizen personal sections to BFF | existing |
| Core Transaction: Transaction composition, journey steppers | /internal/v1/core-transactions/* | /core-transaction | useCoreTransactionExperience.ts | partial | no | partial | HIGH | Deepen command/handoff wiring | advisory |
| Public Health Ops: Inspections, outbreaks, campaigns, intelligence | /internal/v1/public-health/* | /public-health/* | usePublicHealth.ts, useSurveillance.ts | partial | no | partial | HIGH | Provider field tasks parity | advisory |
| Ndila: Geocode, routes, intelligence layers | /api/v1/ndila/* | NdilaIntelligencePanel | lib/ndila/ndila-client.ts | partial | no | partial | MEDIUM | Reusable map component rollout | advisory |
| Nhume: Dispatch, delivery, fleet tracking | /api/v1/nhume/*, /internal/v1/mobile/*/nhume/* | /nhume/*, /operations/dispatch | lib/nhume.ts, useDispatchOps.ts | partial | no | partial | HIGH | Unified operator UX + maturity labels | advisory |
| Comms Hub: Omnichannel, messaging, notifications | /internal/v1/omnichannel/*, /internal/v1/communication/* | /communication, /omnichannel | useOmnichannel.ts, useCommunication.ts | partial | no | partial | MEDIUM | Comms dashboard actionable tasks | advisory |
| Telemedicine: Teleconsult sessions, scheduling | /internal/v1/teleconsult/* | /telemedicine/* | useTelemedicine.ts | partial | no | partial | HIGH | Label Blocked for RTC; live scheduling/records | advisory |
| Msika / Msika Flow: Catalog, orders, marketplace | /internal/v1/marketplace/*, /internal/v1/commerce/* | /marketplace/* | useMarketplace.ts, useCommerceFlow.ts | partial | no | partial | MEDIUM | Honest blocked states on list routes | advisory |
| MusheX / COSTA: Payments, claims, billing, tariffs | /internal/v1/finance/*, /internal/v1/wallet/* | /finance/*, /wallet | useMusheWallet.ts, useFinanceBillingWorkspace.ts | partial | no | partial | HIGH | Finance journey mobile parity | advisory |
| Fundo: LMS courses, studio, certificates | /internal/v1/learning/v11/* | /learning/* | useFundoLms.ts, useFundoStudio.ts | partial | no | partial | MEDIUM | Fundo mobile module depth | advisory |
| Social: Timeline, communities, pages | /internal/v1/social/* | /social, /communities, /pages | useSocial.ts | yes | no | complete | LOW | Moderation workflow surfacing | existing |
| UBOMI: CRVS births/deaths | ubomi-service /v1/births | /ubomi | useUbomiRegistry.ts (new) | partial | no | partial | HIGH | UBOMI births/deaths/verify live when service up; mobile parity | advisory |
| ZIBO: Terminology governance | /v1/artifacts, /v1/packs | ui/zibo-web | ziboApi.ts | partial | no | partial | LOW | Shell link + maturity on terminology nav | advisory |
| Nompilo: Guidance, LLM chat, core-transaction assist | /internal/v1/guidance/*, /internal/v1/llm/* | /ask, global command bar | useGuidance.ts, NompiloGlobalCommandBar | partial | no | partial | HIGH | Context query params + fallback label | advisory |
| Integration Hub: Routes, dead letters, dispatch | /internal/v1/integration-hub/* | /admin/integration-status, /settings/integrations | useIntegrationHub.ts | partial | no | partial | MEDIUM | Integration admin depth | advisory |
| Workflow / Dispatch: Workflow definitions, instances, dispatch tasks | /internal/v1/workflows/*, /internal/v1/dispatch/* | /operations/workflows, /operations/dispatch | useDispatchOps.ts | partial | no | partial | HIGH | Workflow instance table + dispatch guided detail | advisory |
| Admin / Governance: Users, tenants, roles, audit, feature flags | /internal/v1/admin/* | /admin/*, /organization-admin/* | useAdminUsers.ts, useTrustAdmin.ts | partial | no | partial | MEDIUM | Document Blocked surfaces explicitly | advisory |
