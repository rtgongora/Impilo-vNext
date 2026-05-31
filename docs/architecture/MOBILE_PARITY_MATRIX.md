# Mobile parity matrix

> Generated: 2026-05-31. Regenerate: `node scripts/architecture/generate-parity-inventories.mjs`

Apps: **citizen-app**, **provider-app** (Expo, pnpm workspace). See [MOBILE_APP_INVENTORY.md](./MOBILE_APP_INVENTORY.md).

Tier matrices: `docs/mobile/full-mobile-parity-matrix.md` (from `node tools/parity/generate-mobile-parity-matrix.mjs`).

| capability | domain | backend | webRoute | android | ios | mobileClient | realData | parity | gate | remediation |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Trust admin (policies, break-glass, devices, audit) | TSHEPO | /internal/v1/admin/trust/* | /admin/trust, /settings/security | partial screens | planned (Expo/EAS) | apps/mobile/packages/mobile-* | partial | partial | advisory | Expand trust context panel on settings + admin |
| Client search, register, profile, Health ID | VITO | /internal/v1/identity/*, /internal/v1/registry/* | /id-services, /registry/* | partial screens | planned (Expo/EAS) | apps/mobile/packages/mobile-* | partial | partial | advisory | Registry hub depth + mobile health-id parity |
| Provider registry, licenses, privileges, CPD | VARAPI | /internal/v1/registry/* | /registry/providers/* | partial screens | planned (Expo/EAS) | apps/mobile/packages/mobile-* | partial | partial | advisory | Verification workflow screens |
| Facility/workspace registry, bookings | TUSO | /internal/v1/facilities, /internal/v1/registry/* | /facility/*, /registry/facilities/* | partial screens | planned (Expo/EAS) | apps/mobile/packages/mobile-* | partial | partial | advisory | Facility operating model detail pages |
| Public health site registry | Indawo | /internal/v1/public-health/site-registry/* | /public-health/* | partial screens | planned (Expo/EAS) | apps/mobile/packages/mobile-* | partial | partial | advisory | Ndila map panel on site registry |
| SHR summary, timeline, allergies, conditions | BUTANO | /internal/v1/summary/*, /internal/v1/timeline | /ehr/[patientId]/* | partial screens | planned (Expo/EAS) | apps/mobile/packages/mobile-* | partial | partial | advisory | Wire citizen personal sections to BFF |
| Transaction composition, journey steppers | Core Transaction | /internal/v1/core-transactions/* | /core-transaction | partial screens | planned (Expo/EAS) | apps/mobile/packages/mobile-* | partial | partial | advisory | Deepen command/handoff wiring |
| Inspections, outbreaks, campaigns, intelligence | Public Health Ops | /internal/v1/public-health/* | /public-health/* | partial screens | planned (Expo/EAS) | apps/mobile/packages/mobile-* | partial | partial | advisory | Provider field tasks parity |
| Geocode, routes, intelligence layers | Ndila | /api/v1/ndila/* | NdilaIntelligencePanel | partial screens | planned (Expo/EAS) | apps/mobile/packages/mobile-* | partial | partial | advisory | Reusable map component rollout |
| Dispatch, delivery, fleet tracking | Nhume | /api/v1/nhume/*, /internal/v1/mobile/*/nhume/* | /nhume/*, /operations/dispatch | partial screens | planned (Expo/EAS) | apps/mobile/packages/mobile-* | partial | partial | advisory | Unified operator UX + maturity labels |
| Omnichannel, messaging, notifications | Comms Hub | /internal/v1/omnichannel/*, /internal/v1/communication/* | /communication, /omnichannel | partial screens | planned (Expo/EAS) | apps/mobile/packages/mobile-* | partial | partial | advisory | Comms dashboard actionable tasks |
| Teleconsult sessions, scheduling | Telemedicine | /internal/v1/teleconsult/* | /telemedicine/* | partial screens | planned (Expo/EAS) | apps/mobile/packages/mobile-* | partial | partial | advisory | Label Blocked for RTC; live scheduling/records |
| Catalog, orders, marketplace | Msika / Msika Flow | /internal/v1/marketplace/*, /internal/v1/commerce/* | /marketplace/* | partial screens | planned (Expo/EAS) | apps/mobile/packages/mobile-* | partial | partial | advisory | Honest blocked states on list routes |
| Payments, claims, billing, tariffs | MusheX / COSTA | /internal/v1/finance/*, /internal/v1/wallet/* | /finance/*, /wallet | partial screens | planned (Expo/EAS) | apps/mobile/packages/mobile-* | partial | partial | advisory | Finance journey mobile parity |
| LMS courses, studio, certificates | Fundo | /internal/v1/learning/v11/* | /learning/* | partial screens | planned (Expo/EAS) | apps/mobile/packages/mobile-* | partial | partial | advisory | Fundo mobile module depth |
| Timeline, communities, pages | Social | /internal/v1/social/* | /social, /communities, /pages | screens (citizen/provider) | planned (Expo/EAS) | apps/mobile/packages/mobile-* | yes | complete | advisory | Moderation workflow surfacing |
| CRVS births/deaths | UBOMI | ubomi-service /v1/births | /ubomi | missing | planned (Expo/EAS) | apps/mobile/packages/mobile-* | partial | intentionally deferred | advisory | BFF bridge + honest Not wired until live |
| Terminology governance | ZIBO | /v1/artifacts, /v1/packs | ui/zibo-web | missing | not supported by platform | apps/mobile/packages/mobile-* | partial | intentionally deferred | advisory | Shell link + maturity on terminology nav |
| Guidance, LLM chat, core-transaction assist | Nompilo | /internal/v1/guidance/*, /internal/v1/llm/* | /ask, global command bar | partial screens | planned (Expo/EAS) | apps/mobile/packages/mobile-* | partial | partial | advisory | Context query params + fallback label |
| Routes, dead letters, dispatch | Integration Hub | /internal/v1/integration-hub/* | /admin/integration-status, /settings/integrations | partial screens | planned (Expo/EAS) | apps/mobile/packages/mobile-* | partial | partial | advisory | Integration admin depth |
| Workflow definitions, instances, dispatch tasks | Workflow / Dispatch | /internal/v1/workflows/*, /internal/v1/dispatch/* | /operations/workflows, /operations/dispatch | partial screens | planned (Expo/EAS) | apps/mobile/packages/mobile-* | partial | partial | advisory | Guided workflow/dispatch detail |
| Users, tenants, roles, audit, feature flags | Admin / Governance | /internal/v1/admin/* | /admin/*, /organization-admin/* | partial screens | planned (Expo/EAS) | apps/mobile/packages/mobile-* | partial | partial | advisory | Document Blocked surfaces explicitly |

## Required domains (classification)

Vito, Varapi, Tuso, Tshepo (user-facing), Butano, Zibo (admin), Fundo, Nompilo, Ndila, Nhume, MusheX, clinical, enterprise, data/intelligence, telemedicine, notifications — all rows above.
