# Impilo vNext — One Health Operating System Architecture

> **Status:** Architecture and product-truth consolidation pass  
> **Generated:** 2026-06-13  
> **Preview truth:** `impilo-full-preview` — 98/98 deployments ready; 89/89 K8s microservices enabled (waves 0–8)  
> **Commit:** `0ff0a6eb`  
> **Related:** [`VNEXT_SERVICE_ACCOUNTABILITY_MATRIX.md`](VNEXT_SERVICE_ACCOUNTABILITY_MATRIX.md), [`VNEXT_KEYCLOAK_TRUST_ALIGNMENT_REPORT.md`](VNEXT_KEYCLOAK_TRUST_ALIGNMENT_REPORT.md), [`VNEXT_BFF_ORCHESTRATION_MODEL.md`](VNEXT_BFF_ORCHESTRATION_MODEL.md)

---

## A. Executive architecture statement

Impilo vNext is **one integrated national Health Operating System (HOS)** — not a loose collection of optional apps attached to a shell. It is a governed execution environment in which sovereign service domains (identity, registry, clinical record, workflow, enterprise, public health, intelligence, integration) operate under a single trust plane, a single experience shell, and a single orchestration layer.

Services are **modular for engineering** (independent build, deploy, scale, resilience, governance) but **coherent for product** (one estate, one session/context model, one testing doctrine, one preview truth). Deployment waves exist only for **operational safety** (pod ceilings, image import, rollout sequencing) — never for product optionality.

**All of vNext is vNext.** Every service, workflow, mobile surface, registry, administrative surface, AI surface, and enterprise surface in the repository is part of the product estate and must be built, deployed, running in preview, reachable, surfaced where design expects, testable, and represented in the testing workbook — or explicitly recorded as a **blocker** with resolution path.

---

## B. Architecture principles

| Principle | Meaning |
|-----------|---------|
| **All of vNext is vNext** | No orphan capabilities; no “outside preview” product slices |
| **Modular ≠ disconnected** | Independent deployability does not imply separate products |
| **First-party internal services** | Internal adapters (eLMIS, PACS, DHIS2, LiveKit, LLM) are vNext — not third-party apps |
| **Waves ≠ optionality** | Wave labels are sequencing; accountability is full-estate |
| **Every service runs in preview** | 89/89 microservices + infra = current target state |
| **Every service is testable** | User story, DoD, Given/When/Then, evidence — or blocker reason |
| **Identity/context flows everywhere** | Health ID, Provider ID, facility, workspace, purpose-of-use |
| **BFF is orchestration** | Not a random proxy; composes sovereign truths |
| **Shell is the OS environment** | Launcher, WORK, MY PROFESSIONAL, MY LIFE, MY HEALTH |
| **Adapters represent externals** | External system may be unavailable; internal surface must be honest |
| **No silent fallbacks** | Stub/blocked states must be labelled and auditable |
| **Preview bypass is explicit** | Documented, preview-only, never production-default |

### Correct language (product accountability)

| Do not say | Say instead |
|------------|-------------|
| optional | lower walkthrough priority; running but lower test priority |
| future/runtime pending | defect: not deployed; blocker: runtime unavailable |
| contract/library only | supporting component for service X (if truly non-runtime) |
| mobile-only | mobile surface requiring mobile test execution |
| external integration | vNext adapter running; external dependency available/unavailable |
| not part of preview | blocker: preview gap requiring resolution |

---

## C. Platform planes

Impilo uses **seven canonical planes** (source: `docs/architecture/planes/00-production-plane-doctrine.md`). Each plane contributes sovereign truth; experience composes — it does not duplicate SoR.

| Plane | Role in the cohesive OS |
|-------|-------------------------|
| **Experience** | `one-ui-shell`, `experience-bff` — operating environment and orchestration |
| **Trust & Identity** | TSHEPO family, Keycloak, identity assurance, consent, audit, keys, offline trust |
| **Registry** | VITO, VARAPI, TUSO, UBOMI, ZIBO, Indawo, product/asset registries |
| **Clinical** | BUTANO/SHR, PCT, inpatient, pharmacy, OROS, MADI, PACS, scheduling, forms, rules |
| **Public Health / Data** | Surveillance, campaigns, NDR, pipelines, warehouse, reporting, search |
| **Enterprise / Resource** | Msika, MusheX, Costa, Simba, HR/payroll, procurement, workforce governance |
| **Integration / Interop** | FHIR gateway, connector adapters, integration hub, IoT, offline sync |
| **Intelligence / Nompilo** | Guidance, LLM orchestration, AI model registry, clinical knowledge platform |
| **Operations / Observability** | Observability, security hardening, jobs, analytics pipeline, audit ledger |

Planes interact through: **Envoy → TSHEPO ext_authz → service**, **BFF composition**, **Kafka/outbox events**, **shared registries**, and **unified shell navigation**.

---

## D. Service cohesion model

### Service identity
- Stable ID in `docs/registry/services-registry.yaml`
- Maven module, plane, domain, SoR responsibilities, forbidden responsibilities
- Classification in `config/full-boot-service-classification.yml` (146 entries)

### Discovery
| Layer | Mechanism |
|-------|-----------|
| Service registry | `services-registry.yaml` — 91 services, 12 libraries |
| Runtime deployment | `values-full-preview-runtime.generated.yaml` — 89 microservices |
| Route discovery | `docs/architecture/FRONTEND_ROUTE_INVENTORY.md`, `one-ui-shell` routes |
| API discovery | `contracts/openapi/`, BFF `/internal/v1/*` |
| Event discovery | Per-service `event_outbox`; AsyncAPI where published |
| Launcher | BFF `/internal/v1/launcher/apps` + `app-registry.ts` (31 curated apps) |

### Context requirements (every meaningful action)
Health ID, active role, Provider/Staff ID, facility/workspace, purpose-of-use, consent/legal basis, assurance level, workflow state — propagated via `CompanionHeaders` / trust headers.

### UI surfacing rules
- **Canonical web entry:** `one-ui-shell` only (GAP-010 convergence; retired sidecars absorbed)
- Domain routes inside shell (`/clinical`, `/madi`, `/finance`, `/registry`, etc.)
- `serviceSlug` in launcher for sovereign branding (8 explicit bindings today)
- No dead clicks; honest `Blocked` / `Preview stub` labels where backend unavailable

### Mobile surfacing
- Citizen and provider apps (`apps/mobile/`) — parity matrix required
- BFF mobile routes: `/internal/v1/mobile/citizen/*`, `/internal/v1/mobile/provider/*`

### Testing accountability
Every runtime service → workbook row with user story, DoD, navigation, evidence. See [`VNEXT_FULL_PRODUCT_TESTING_WORKBOOK_UPGRADE_REQUIREMENTS.md`](../product/VNEXT_FULL_PRODUCT_TESTING_WORKBOOK_UPGRADE_REQUIREMENTS.md).

---

## E. Keycloak / Trust Plane architecture

### Internal first-party services
All 89 K8s microservices are **first-party HOS components**. They authenticate via:
- **Envoy ingress** → TSHEPO ext_authz policy
- **Bearer tokens** validated per service SecurityConfig
- **Service accounts** where machine-to-machine (BFF → downstream)

They must **not** be modelled as unrelated external OAuth applications.

### True external dependencies (doctrine_only contract references)
`dhis2`, `external-elmis`, `external-pacs-network`, `banking-rails`, `mosip`, `lims`, `sms-whatsapp-gateway`, `external-idp`, `civil-registry-system` — external systems represented by **internal adapters** that must run in preview.

### Browser clients (Keycloak realm `impilo-preview`)
| Client | Role |
|--------|------|
| `impilo-ui` | Primary web (`one-ui-shell`) |
| `impilo-mobile-citizen` / `impilo-mobile-provider` | Mobile apps |
| `impilo-bff` | BFF service account |
| `impilo-backend` | Backend registration/service account |
| Legacy continuity | `impilo-ops-console`, `impilo-ehr`, `impilo-portal` — wiring only, not parallel UX |

### Preview-only bypass policy
- `IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true` injected for `global.environment == full-preview` (`deploy/helm/impilo-vnext/templates/_helpers.tpl`)
- **Must not** leak to production
- **Must not** hide broken trust modelling — documented in trust alignment report
- Per-service bypass also on VARAPI, workforce-governance (preview OAuth disable)

### Production posture
Full OAuth, TSHEPO RBAC/ABAC, audit chain, no test bypass flags, audience/scope enforcement per client.

**Doctrine:** *All internal vNext services are first-party components of the Impilo Health Operating System.*

---

## F. Context propagation architecture

Standard context dimensions (10-dimension access model):

| Context | Header / source | Consumers |
|---------|-----------------|-----------|
| Person (Health ID) | `X-Actor-ID`, session | All clinical/registry flows |
| Provider ID | `X-Provider-ID`, VARAPI resolution | WORK, prescribing, claims |
| Facility | `X-Facility-ID`, TUSO | Queue, inpatient, dispensing |
| Workspace | `X-Workspace-ID` | Role-specific workspaces |
| Purpose of use | `X-Purpose-Of-Use` | TSHEPO policy |
| Tenant/jurisdiction | `X-Tenant-ID` | Multi-tenant preview |
| Subject in context | `X-Subject-ID` | EHR, PCT |
| Session | BFF session contract | Shell tabs, WORK visibility |
| Preview/test | `global.environment`, seeded data | Walkthrough only |

Flow: **Shell** injects via `api-client.ts` → **BFF** mediates + enriches → **TSHEPO** authorizes → **domain service** executes.

---

## G. BFF orchestration architecture

`experience-bff` is the **experience orchestration layer**:

| Responsibility | Implementation |
|----------------|----------------|
| Launcher aggregation | `/internal/v1/launcher/apps` |
| Facility/provider hubs | Provider hubs mode + failure policy |
| Service discovery | Downstream env from registry generator |
| Downstream orchestration | 72+ `*_BASE_URL` env vars (cluster DNS) |
| Context propagation | Trust headers forwarded on outbound calls |
| Fallback policy | `stub_fallback` for citizen longtail; honest errors |
| Notification aggregation | Notification service proxy |
| Marketplace tiles | Msika/Msika Flow composition |
| Error normalization | Consistent API error shapes |
| Health aggregation | `/health/version`, downstream probes |
| **No localhost in preview** | `generate-full-preview-bff-downstream-env.mjs` |

**Anti-pattern:** `application.yml` localhost defaults (70+) — overridden in cluster by generated helm env; must never be sole preview config.

See [`VNEXT_BFF_ORCHESTRATION_MODEL.md`](VNEXT_BFF_ORCHESTRATION_MODEL.md).

---

## H. Shell and user experience architecture

`one-ui-shell` is the **operating environment**:

| Surface | Route families | Status |
|---------|----------------|--------|
| Login/landing | `/`, auth flows | Live |
| WORK | Provider work assignments, session contract | Requires VARAPI + workforce-governance truth |
| MY PROFESSIONAL | Provider profile, CPD, licenses | Partial parity |
| MY LIFE / MY HEALTH | Citizen wellness, monitoring | Partial; wellness migrated to Simba |
| Launcher | Start menu, Production Command Centre | 31 apps; BFF launcher contract partial |
| Search / Nompilo | `/ask`, command bar | Guidance + LLM orchestration |
| Notifications | Comms hub | Partial |
| Context switcher | Facility/role | Requires seeded registry |
| Service cards | Domain hubs | Role-gated |
| Taskbar | Minimal surfaces | Live |

**Retired sidecars** (wave 6): `portal`, `pct-web`, `oros-web`, etc. — absorbed into shell routes per `sidecar-retirement-ledger.ts`.

---

## I. Workflow orchestration architecture

| Workflow | Initiating role | Services | Context | Evidence |
|----------|-----------------|----------|---------|----------|
| Client registration | Clerk/citizen | VITO, BFF, Keycloak | Facility | Health ID issued |
| Client self-registration | Citizen | VITO, BFF, Keycloak | — | Account + Health ID |
| Provider ID request | Provider | VARAPI, workforce-governance | Provider | PROV-* ID |
| Facility context | Provider | TUSO, session | Facility ID | WORK tab active |
| Patient care/PCT | Clinician | PCT, BUTANO, BFF | Patient CPID | Chart data |
| Telemedicine | Provider/citizen | live-service, rtc-gateway, scheduling | Session | Session or honest blocked |
| Appointments | Citizen/provider | booking-service, scheduling | Facility | Booking confirmed |
| Inpatient | Nurse/clinician | inpatient-service | Ward | Bed status |
| Orders/OROS | Clinician | oros-service | Patient | Order placed |
| Imaging/PACS | Radiologist | pacs-adapter-service | Study | Worklist item |
| Blood/Madi | Bank/clinician | madi-service | Facility | Stock/order/transfusion |
| Inventory/Simba | Pharmacy/supply | simba-service, inventory-service | Facility | Stock movement |
| Payments/Mushex | Finance | mushex-service, mushe-wallet | Payer | Payment state |
| Costing/Costa | Finance | costing-engine-service | Encounter | Tariff applied |
| Coverage | Clerk | coverage-service | Member | Eligibility result |
| Public health/Indawo | PH officer | indawo-service, surveillance | Site | Site registered |
| Learning/Fundo | Any | learning-service | Role | Course progress |
| Enterprise governance | Admin | workforce-governance, general-ledger | Org | Assignment/posting |
| Live events | Host | live-service | Event | Broadcast state |
| Nompilo support | Any | guidance-service, llm-orchestration | Context | Guidance response |

Failure behaviour: explicit error surfaces; `Blocked` labels for RTC/external deps; no silent no-ops.

---

## J. Event and data orchestration architecture

| Pattern | Rule |
|---------|------|
| Synchronous APIs | OpenAPI contracts; BFF composition for UX |
| Async events | `event_outbox` table per service → Kafka |
| Audit | TSHEPO audit service; serialized audit chain |
| FHIR/SHR | BUTANO (CPID only, no PII); FHIR gateway |
| Reporting | data-pipeline → warehouse → reporting/NDR |
| Idempotency | BFF idempotency keys on mutations |
| Consistency | Eventual via outbox; UI shows pending states |

---

## K. External integration doctrine

| External | Internal vNext adapter | Preview requirement |
|----------|------------------------|---------------------|
| eLMIS/NatPharm | inventory-elmis-adapter, pharmacy-elmis-adapter | Adapter running; honest if external down |
| DHIS2 | analytics-pipeline / public health surfaces | Adapter + fallback state |
| PACS/DICOM | pacs-adapter-service | Worklist in shell; orthanc in preview |
| LiveKit | live-service, rtc-gateway-service | Honest blocked if media infra unavailable |
| SMS/Email | notification-service, channels-service | Sandbox mode documented |
| Payment rails | mushex-service, mushe-wallet-service | Test harness / sandbox |
| AI/LLM providers | llm-orchestration-service, ai-model-registry | Offline fallback in BFF |

---

## L. Preview deployment doctrine

| Rule | Current state (2026-06-13) |
|------|----------------------------|
| Every microservice runs | 89/89 enabled, 98/98 deployments ready |
| Waves = sequencing only | Phased promote waves 0–8 completed |
| Single public stack | `impilo-full-preview` owns `http://41.57.127.235` |
| Fallback slice | `impilo-preview` scaled to 0 (rollback namespace preserved) |
| Pod ceiling | k3s 110 pods/node — requires phased rollout + fallback scale-down |
| Image truth | Local registry `127.0.0.1:5000`; `imagePullPolicy: Always` |
| Health | `/health/version` commit alignment required |
| Auth | Preview OAuth bypass global — documented risk |

---

## M. Testing doctrine

Preview is **complete for architecture review** only when:
1. Whole estate built and deployed (runtime) ✓ (89/89)
2. Every service has workbook accountability row (gap: ~44 services without UAT module)
3. Logged-in click-flow tests exist (180 scenarios; partial depth)
4. API-backed rendering verified (parity: 17 complete, 20 partial)
5. Mobile parity executed (15 mobile scenarios; 29 partial rows)
6. Blockers explicitly listed ✓ (accountability matrix)

**Madi benchmark:** exact navigation paths required per service (see workbook upgrade doc).

---

## N. Observability and operations

| Signal | Source |
|--------|--------|
| Deployment truth | `scripts/operator/report-preview-generation.sh` |
| Service health | `/actuator/health` per microservice |
| Public version | `http://41.57.127.235/health/version` |
| BFF downstream | Generated env + integration tests |
| Audit correlation | `X-Correlation-ID`, `X-Request-ID` |
| Full-boot reports | `reports/full-boot/` |

---

## O. Risks and anti-patterns

| Anti-pattern | Current evidence | Severity |
|--------------|------------------|----------|
| localhost defaults in BFF pods | `application.yml` — mitigated by generator | HIGH if generator skipped |
| `optional_full_boot` label | 87+ classification entries — **misleading language** | MEDIUM (rename to wave sequencing) |
| Global OAuth disable in preview | All preview pods | HIGH for trust validation |
| BFF stub modes | facilities, citizen longtail | MEDIUM — must be honest in UI |
| Route-only success | 20 partial parity rows | HIGH |
| Pod ceiling mass rollout | Wave 8 hook timeout; 110 pod cap | OPERATIONAL |
| Helm/kubectl drift | Rev 38 failed (hook); cluster healthy | MEDIUM |
| Dead clicks | Guard: `test:no-stubs`, `test:routes` | HIGH if regress |
| UI without backend | Partial wiring on 78 services | HIGH |
| Backend without UI | Platform services (audit-ledger, jobs) | MEDIUM |

---

## P. Implementation roadmap

### Immediate preview correction
- Reconcile Helm revision metadata (failed hook vs healthy cluster)
- Document pod-ceiling playbook in phased promote script
- Verify `/health/version` matches intended commit after next deploy

### Service registry and metadata correction
- Replace `optional_full_boot` with `wave_sequenced_full_boot` in classification
- Regenerate accountability matrix on each preview generation

### Keycloak/trust alignment
- Per-service preview auth posture (not global disable only)
- Service account grants audit (registration, downstream callers)
- See trust alignment report

### BFF orchestration hardening
- Add 15 missing downstream URLs (audit-ledger, product-registry, share-slip, etc.)
- Reduce stub modes where sovereign data exists (facilities → TUSO live)

### Shell and launcher hardening
- Expand BFF launcher contract coverage beyond 31 curated apps
- WORK tab session contract — providerPublicId mapping (fixed in `997a10d0`)

### Workflow orchestration
- Close 20 partial parity rows (HIGH priority first)

### External adapters
- PACS, eLMIS, LiveKit honest blocked states in UI

### Mobile parity
- Execute 15 mobile UAT scenarios; close 29 partial rows

### Testing workbook update
- Expand from 47 modules → 91 service modules
- Madi-level navigation specificity for all services

### Production hardening
- Remove preview bypass flags
- Per-service OAuth + TSHEPO enforcement
- No stub fallbacks in production paths

---

## References

| Artifact | Path |
|----------|------|
| Service registry | `docs/registry/services-registry.yaml` |
| Classification | `config/full-boot-service-classification.yml` |
| Preview generation | `reports/full-boot/preview-generation.json` |
| Accountability matrix | `docs/architecture/VNEXT_SERVICE_ACCOUNTABILITY_MATRIX.md` |
| Parity matrix | `docs/architecture/FRONTEND_BACKEND_PARITY_MATRIX.md` |
| UAT pack | `docs/product/UAT_FULL_PREVIEW_VALIDATION_PACK_4917def8.md` |
| Phased promote | `scripts/operator/phased-wave-preview-promote.sh` |
