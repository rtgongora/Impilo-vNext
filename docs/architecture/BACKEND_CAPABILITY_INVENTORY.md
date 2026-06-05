# Backend capability inventory

> Generated: 2026-06-05. Regenerate: `node scripts/architecture/generate-parity-inventories.mjs`

Canonical matrix: [BACKEND_CAPABILITY_TO_FRONTEND_SURFACING_MATRIX.md](../frontend/BACKEND_CAPABILITY_TO_FRONTEND_SURFACING_MATRIX.md)

| service | plane | path | purpose | apis | userFacing | webRoute | mobileExpectation | frontendStatus |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TSHEPO | Trust | /internal/v1/admin/trust/* | Trust admin (policies, break-glass, devices, audit) | /internal/v1/admin/trust/* | yes | /admin/trust, /settings/security | yes | partially surfaced |
| VITO | Registry | /internal/v1/identity/*, /internal/v1/registry/* | Client search, register, profile, Health ID | /internal/v1/identity/*, /internal/v1/registry/* | yes | /id-services, /registry/* | yes | partially surfaced |
| VARAPI | Registry | /internal/v1/registry/* | Provider registry, licenses, privileges, CPD | /internal/v1/registry/* | yes | /registry/providers/* | yes | partially surfaced |
| TUSO | Registry | /internal/v1/facilities, /internal/v1/registry/* | Facility/workspace registry, bookings | /internal/v1/facilities, /internal/v1/registry/* | yes | /facility/*, /registry/facilities/* | yes | partially surfaced |
| Indawo | Registry | /internal/v1/public-health/site-registry/* | Public health site registry | /internal/v1/public-health/site-registry/* | yes | /public-health/* | yes | partially surfaced |
| BUTANO | Clinical | /internal/v1/summary/*, /internal/v1/timeline | SHR summary, timeline, allergies, conditions | /internal/v1/summary/*, /internal/v1/timeline | yes | /ehr/[patientId]/* | yes | fully surfaced |
| Core Transaction | Clinical | /internal/v1/core-transactions/* | Transaction composition, journey steppers | /internal/v1/core-transactions/* | yes | /core-transaction | yes | partially surfaced |
| Public Health Ops | Data & Intelligence | /internal/v1/public-health/* | Inspections, outbreaks, campaigns, intelligence | /internal/v1/public-health/* | yes | /public-health/* | yes | partially surfaced |
| Ndila | Integration & Edge | /api/v1/ndila/* | Geocode, routes, intelligence layers | /api/v1/ndila/* | yes | NdilaIntelligencePanel | yes | partially surfaced |
| Nhume | Enterprise | /api/v1/nhume/*, /internal/v1/mobile/*/nhume/* | Dispatch, delivery, fleet tracking | /api/v1/nhume/*, /internal/v1/mobile/*/nhume/* | yes | /nhume/*, /operations/dispatch | yes | partially surfaced |
| Comms Hub | Experience | /internal/v1/omnichannel/*, /internal/v1/communication/* | Omnichannel, messaging, notifications | /internal/v1/omnichannel/*, /internal/v1/communication/* | yes | /communication, /omnichannel | yes | partially surfaced |
| Telemedicine | Clinical | /internal/v1/teleconsult/* | Teleconsult sessions, scheduling | /internal/v1/teleconsult/* | yes | /telemedicine/* | yes | partially surfaced |
| Msika / Msika Flow | Enterprise | /internal/v1/marketplace/*, /internal/v1/commerce/* | Catalog, orders, marketplace | /internal/v1/marketplace/*, /internal/v1/commerce/* | yes | /marketplace/* | yes | partially surfaced |
| MusheX / COSTA | Enterprise | /internal/v1/finance/*, /internal/v1/wallet/* | Payments, claims, billing, tariffs | /internal/v1/finance/*, /internal/v1/wallet/* | yes | /finance/*, /wallet | yes | partially surfaced |
| Fundo | Experience | /internal/v1/learning/v11/* | LMS courses, studio, certificates | /internal/v1/learning/v11/* | yes | /learning/* | yes | partially surfaced |
| Social | Experience | /internal/v1/social/* | Timeline, communities, pages | /internal/v1/social/* | yes | /social, /communities, /pages | yes | fully surfaced |
| UBOMI | Registry | ubomi-service /v1/births | CRVS births/deaths | ubomi-service /v1/births | yes | /ubomi | yes | partially surfaced |
| ZIBO | Registry | /v1/artifacts, /v1/packs | Terminology governance | /v1/artifacts, /v1/packs | yes | ui/zibo-web | no | partially surfaced |
| Nompilo | Experience | /internal/v1/guidance/*, /internal/v1/llm/* | Guidance, LLM chat, core-transaction assist | /internal/v1/guidance/*, /internal/v1/llm/* | yes | /ask, global command bar | yes | partially surfaced |
| Integration Hub | Integration | /internal/v1/integration-hub/* | Routes, dead letters, dispatch | /internal/v1/integration-hub/* | yes | /admin/integration-status, /settings/integrations | yes | partially surfaced |
| Workflow / Dispatch | Platform | /internal/v1/workflows/*, /internal/v1/dispatch/* | Workflow definitions, instances, dispatch tasks | /internal/v1/workflows/*, /internal/v1/dispatch/* | yes | /operations/workflows, /operations/dispatch | yes | partially surfaced |
| Admin / Governance | Platform | /internal/v1/admin/* | Users, tenants, roles, audit, feature flags | /internal/v1/admin/* | yes | /admin/*, /organization-admin/* | partial | partially surfaced |

## Internal / non-user-facing

Platform infra (Kafka, Postgres, Redis, observability) is not listed here — see `docs/registry/services-registry.yaml`.
