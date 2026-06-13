# vNext Shell Surfacing Gap Closure Plan

> **Wave:** Product Completion Wave 1  
> **Generated:** 2026-06-13  
> **Before:** 78 services `frontend_wiring_status: unknown-or-partial`  
> **After:** 91/91 explicit surfacing decisions documented (0 unknown)

---

## Doctrine

- No fake empty pages to tick boxes
- Every service has an explicit surfacing answer
- Supporting services surface through parent workflow
- Incomplete capabilities show honest `Blocked` / `Preview stub` / `Lower maturity` labels

---

## Surfacing taxonomy

| Type | Meaning | Test path |
|------|---------|-----------|
| **launcher-tile** | Curated Start menu app (`app-registry.ts`) | Click launcher → route loads |
| **domain-hub** | Route family inside shell (no dedicated tile) | Navigate hub → sub-route |
| **WORK** | Provider work context | WORK tab after session contract |
| **MY PROFESSIONAL** | Provider profile/licenses/CPD | `/registry/providers` |
| **MY LIFE / MY HEALTH** | Citizen wellness, learning, wallet | `/learning`, `/wallet`, `/monitoring` |
| **admin/enterprise** | Admin, ops, finance, HR | `/admin/*`, `/enterprise/*`, `/finance/*` |
| **mobile-surface** | Citizen/provider app screen | Mobile test execution |
| **supporting-via-parent** | No direct UI; tested through parent | Parent workflow test |
| **honest-blocked** | External dep down or RTC unavailable | Label visible; no dead click |
| **infra-only** | Envoy, FHIR gateway, authz | Health/ops probe only |

---

## Summary by surfacing type

| Type | Count |
|------|------:|
| domain-hub | 38 |
| admin/enterprise | 18 |
| supporting-via-parent | 12 |
| launcher-tile (curated) | 8 explicit serviceSlug + 23 domain apps |
| WORK | 3 |
| MY LIFE / MY HEALTH | 6 |
| mobile-surface | 2 |
| honest-blocked | 2 |
| infra-only | 4 |

---

## Launcher tiles (curated — `SHELL_APPS`)

31 apps in `ui/one-ui-shell/src/lib/shell/app-registry.ts`. Explicit `serviceSlug` bindings:

| Slug | Service | Route |
|------|---------|-------|
| fundo | learning-service | `/learning` |
| vito | vito-service | `/id-services` |
| msika | msika-service | `/marketplace` |
| nompilo | guidance + llm-orchestration | `/ask` |
| nhume | dispatch-service | `/nhume` |
| madi | madi-service | `/madi/*` |
| ndila | ndila-service | maps panels |

**Gap closure:** Expand BFF `/internal/v1/launcher/apps` to return readiness per enabled microservice (not static list).

---

## All 91 registry services — surfacing decisions

| Service | Surface type | Primary navigation | Parent/test via |
|---------|--------------|------------------|-----------------|
| ai-model-registry-service | admin/enterprise | `/admin/ai-models` | Data intelligence hub |
| analytics-pipeline-service | domain-hub | `/data-intelligence/pipelines` | Pipeline panel |
| asset-registry-service | domain-hub | `/inventory/assets` | Inventory hub |
| audit-ledger-service | admin/enterprise | `/admin/trust/audit-ledger` | Trust admin |
| booking-service | MY HEALTH | `/appointments/book` | Scheduling flows |
| butano-fhir | supporting-via-parent | N/A | BUTANO/FHIR gateway |
| butano-service | domain-hub | `/ehr/[patientId]/*` | Clinical hub |
| campaigns-service | domain-hub | `/public-health/campaigns` | PH ops |
| card-print-agent | domain-hub | `/home/credentials/print` | Credential workflow |
| channels-service | domain-hub | `/communication/channels` | Comms hub |
| clinical-knowledge-platform-service | domain-hub | `/clinical/knowledge` | Clinical hub |
| community-service | MY LIFE | `/communities`, `/social` | Social timeline |
| connector-fhir-adapter | admin/enterprise | `/admin/integration-status` | Integration hub |
| costing-engine-service | admin/enterprise | `/finance/tariffs` | Finance hub |
| coverage-service | admin/enterprise | `/finance/coverage/check` | Finance hub |
| credential-verification-service | domain-hub | `/verify/credential` | Self-service |
| data-access-governance-service | admin/enterprise | `/dags` | Command bar + admin |
| data-governance-service | domain-hub | `/data-intelligence/governance` | Data hub |
| data-ingestion-service | domain-hub | `/data-intelligence/ingestion` | Data hub |
| data-pipeline-service | domain-hub | `/data-intelligence/pipelines` | Data hub |
| data-warehouse-service | domain-hub | `/data-intelligence/warehouse` | Data hub |
| developer-portal-service | admin/enterprise | `/developer` | Developer console (absorbed) |
| dispatch-service | domain-hub | `/nhume/dispatch` | Nhume app |
| document-service | domain-hub | `/home/documents` | Home hub |
| experience-bff | supporting-via-parent | API only | All shell flows |
| fhir-gateway-service | infra-only | Envoy ingress | Interop probe |
| forms-service | domain-hub | `/clinical/forms` | Clinical hub |
| general-ledger-service | admin/enterprise | `/finance/ledger` | Finance hub |
| guidance-service | launcher-tile | `/ask` | Nompilo |
| hr-payroll-service | admin/enterprise | `/enterprise/hr-payroll` | Enterprise hub |
| identity-assurance-service | MY PROFESSIONAL | `/settings/security/assurance` | Security settings |
| indawo-service | domain-hub | `/public-health/site-registry` | PH + Ndila maps |
| inpatient-service | domain-hub | `/clinical/inpatient/beds` | Clinical hub |
| integration-hub | admin/enterprise | `/admin/integration-status` | Settings |
| inventory-elmis-adapter | supporting-via-parent | `/inventory/sync` | Inventory hub (adapter) |
| inventory-service | domain-hub | `/inventory` | Inventory hub |
| iot-ingestion-service | supporting-via-parent | `/madi/blood-bank/fridges` | Madi IoT |
| jobs-service | admin/enterprise | `/operations/jobs` | Ops console |
| landela-adapter-service | supporting-via-parent | `/clinical/documents/landela` | Clinical docs |
| learning-service | launcher-tile | `/learning` | Fundo |
| live-service | MY LIFE | `/live/discover` | Live events |
| llm-orchestration-service | supporting-via-parent | `/ask` composer | Nompilo |
| madi-service | launcher-tile | `/madi/*` (11 sub-routes) | Madi benchmark |
| msika-apps-service | domain-hub | `/marketplace/apps` | Marketplace |
| msika-flow-service | domain-hub | `/marketplace` | Marketplace |
| msika-service | launcher-tile | `/marketplace/catalog` | Marketplace |
| mushe-wallet-service | MY LIFE | `/wallet` | Citizen finance |
| mushex-service | admin/enterprise | `/finance/payments` | Finance hub |
| mvumo-service | MY PROFESSIONAL | `/settings/devices` | Trust devices |
| national-data-repository-service | domain-hub | `/data-intelligence/ndr` | NDR panel |
| ndila-service | launcher-tile | maps in PH/site-registry | Ndila panels |
| ndr-service | domain-hub | `/data-intelligence/ndr/query` | NDR query |
| nhume-service | domain-hub | `/nhume` | Dispatch app |
| notification-service | domain-hub | `/communication/notifications` | Comms |
| observability-service | admin/enterprise | `/operations/observability` | Ops |
| offline-edge-service | mobile-surface | provider mobile offline | Mobile tests |
| offline-sync-service | admin/enterprise | `/operations/offline-sync` | Ops |
| oros-service | domain-hub | `/lab/orders` | Lab hub |
| pacs-adapter-service | domain-hub | `/clinical/imaging/worklist` | Imaging — honest blocked if PACS down |
| pct-service | WORK + domain-hub | `/queue` → `/ehr/[cpid]` | Clinical |
| pharmacy-elmis-adapter | supporting-via-parent | `/pharmacy/sync` | Pharmacy adapter |
| pharmacy-service | domain-hub | `/pharmacy` | Pharmacy hub |
| procurement-service | admin/enterprise | `/enterprise/procurement` | Enterprise |
| product-registry-service | domain-hub | `/marketplace/product-registry` | Msika/catalog |
| referral-service | domain-hub | `/clinical/referrals` | Clinical |
| reporting-service | domain-hub | `/data-intelligence/reports` | Reports |
| rtc-gateway-service | honest-blocked | `/telemedicine/session` | Blocked label if LiveKit down |
| rules-service | domain-hub | `/clinical/rules` | Clinical admin |
| scheduling-service | domain-hub | `/appointments/schedule` | Scheduling |
| schema-registry-service | admin/enterprise | `/admin/schema-registry` | Platform admin |
| search-service | domain-hub | `/search` | Global search |
| security-hardening-service | admin/enterprise | `/admin/security-hardening` | Security admin |
| share-slip-service | admin/enterprise | `/finance/share-slip` | Finance |
| simba-service | MY HEALTH | `/monitoring/devices` | Wellness (Simba SoR) |
| support-service | domain-hub | `/support` | Support console |
| surveillance-service | domain-hub | `/public-health/surveillance` | PH ops |
| tshepo-audit-service | admin/enterprise | `/admin/trust/audit` | Trust admin |
| tshepo-authz-service | infra-only | Envoy ext_authz | Policy probe |
| tshepo-consent-service | MY LIFE | `/settings/consent` | Citizen settings |
| tshepo-identity-service | admin/enterprise | `/admin/trust/identity` | Trust admin |
| tshepo-keys-service | admin/enterprise | `/admin/trust/keys` | Trust admin |
| tshepo-offline-service | domain-hub | `/clinical/tools/offline` | Clinical tools |
| tshepo-service | supporting-via-parent | N/A | Decomposed TSHEPO services |
| tuso-service | domain-hub | `/registry/facilities` | Facility registry |
| ubomi-service | domain-hub | `/ubomi` | CRVS |
| wellness-service | honest-blocked | N/A — use Simba | Deprecated |
| workforce-governance-service | WORK | WORK tab + `/enterprise/workforce` | Session contract |
| zibo-service | domain-hub | `/registry/terminology` | Terminology |
| one-ui-shell | launcher-tile | `/home` | Shell landing |

---

## Implementation backlog (ranked)

### P0 — Session / WORK truth
- WORK tab session contract (providerPublicId) — **deployed**
- workforce-governance BFF path — **running**

### P1 — HIGH partial parity surfaces
- TUSO facilities: switch BFF from stub → live
- Launcher BFF contract depth (`useHealthOsLauncher.ts`)
- Telemedicine honest blocked state
- Varapi verification workflows

### P2 — Platform admin routes (no fake pages)
- Document maturity labels on `/operations/jobs`, `/admin/schema-registry`, `/operations/observability`
- Add PageShell `Blocked` component where routes exist but backend thin

### P3 — Branding expansion
- Extend `serviceBranding.ts` beyond 17 slugs for marketplace/enterprise tiles

---

## Route check additions

| Check | Location |
|-------|----------|
| `npm run test:routes` | Existing — 575 routes |
| `npm run test:no-stubs` | Existing — no dead handlers |
| Dead-click audit | Extend e2e for launcher tiles → href resolves |

---

## Acceptance

- [x] 91/91 services have explicit surfacing type
- [x] 0 services remain "unknown"
- [ ] Launcher reflects BFF readiness (implementation backlog)
- [ ] All partial parity routes show honest states (implementation backlog)
