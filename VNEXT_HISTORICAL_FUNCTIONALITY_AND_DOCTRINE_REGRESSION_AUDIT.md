# Impilo vNext — Historical Functionality and Doctrine Regression Audit

**Audit type:** Investigation, classification, and restoration planning only (no production code changes).  
**Branch reviewed:** `claude/staging-ux-orchestration-remediation-Yypyl`  
**Impilo-vNext HEAD:** `b340703937d1dfaa0c0a272c089def5aec898956`  
**Audit date:** 2026-05-29  
**Auditor role:** Historical functionality and doctrine regression analysis across AI-assisted implementation waves.  
**Full-history supplement:** [`VNEXT_FULL_HISTORY_ARCHAEOLOGICAL_SUPPLEMENT.md`](VNEXT_FULL_HISTORY_ARCHAEOLOGICAL_SUPPLEMENT.md) (`484c899b` → `b3407039`, 1,078 commits)

---

## 17.1 Executive Summary

### What is preserved

- **Seven-plane architecture** remains canonical in `docs/architecture/planes/`, `README.md`, `AGENTS.md`, and `docs/architecture/SERVICE_ARCHITECTURE_REGISTER.md`.
- **Core Transaction Doctrine** is filed and intact under `docs/doctrine/CORE_TRANSACTION_DOCTRINE.md`, `CORE_TRANSACTION_STATE_MACHINE.md`, `THREE_CORE_JOURNEYS.md`, journey maps, and `contracts/core-transaction.ts`.
- **88 Maven service modules** plus `shared-core` are included in `services/pom.xml`; **no `pom.xml` deletions** appear in git history.
- **Experience BFF** exposes 11 core-transaction operations on both `/internal/v1/core-transactions/*` and `/experience/core-transactions/*`.
- **Dual-emit to `core.transaction.events`** is implemented (default on) in pct, costing-engine, oros, pharmacy, msika-flow, and mushex outbox publishers.
- **GAP-010 convergence** (commit `1f26349e`) intentionally retired `ui/experience/` (~989 files) after lifting capabilities into `ui/one-ui-shell`; CI, Playwright, and scripts were repointed. This is an **equivalent replacement**, not silent loss.
- **Web route registry** enforces `EXPECTED_ROUTE_COUNT = 417` with `routes.test.ts` and `route-parity-check.mjs`.
- **CI guardrails** include `test:routes`, `test:no-stubs`, `verify-demo-journeys.mjs`, sovereign smoke overlay, and `deprecated-surface-guard.yml` blocking new `ui/experience` files.
- **Mobile provider** has a live-BFF `CoreTransactionJourneyShellScreen` calling `listCoreTransactions` / `applyCoreTransactionAction`.
- **Social timeline** and **BUTANO SHR** are the only capabilities marked **Live** in the generated surfacing matrix (2026-05-29).

### What is improved

- Unified web orchestration in `one-ui-shell` (no parallel `ui/experience` fork).
- Wave 20 sovereign compose overlay packages ndila, nhume, dispatch, pharmacy, mushex, costing, surveillance for demo hardening (`9a52cd34`).
- No-stub UI policy enforced in CI (`d8cfe5e2`).
- Telemedicine, PACS, and document-management remediation snapshots documented in architecture register (May 2026).
- Registry maturity generation and parity doc automation (`generate-parity-docs.mjs`).

### What is weakened

- **Dual-emit payloads** republish raw domain JSON; they do **not** populate the canonical `CoreTransactionEventEnvelope` from AsyncAPI.
- **`workflow-service`** (BFF composition SoR for transactions) does **not** dual-emit to `core.transaction.events`.
- **BFF stubs** for Nompilo command, handoff, and feedback return `ACCEPTED` without downstream calls.
- **~95% of services** (`83/87`) remain `frontend_wiring_status: unknown-or-partial` in `docs/registry/services-registry.yaml`.
- **CI uses `-DskipTests`** on most Maven package paths; no full-reactor `mvn verify` on every PR.
- **Nhume** actuator remains unstable in runtime smoke (15/16 pass per `PRODUCTION_READINESS_AUDIT.md`).
- **`AIDiagnosticAssistant.tsx`** falls back to in-page `MOCK_*` constants on API failure (fixture-presented-as-live risk in clinical tools).
- **Security posture gap:** `dispatch-service` and `nhume-service` lack `SecurityConfig` after mass `SecurityConfig` removal (`2b82d766`).

### What is missing or orphaned

- **UBOMI CRVS** — backend exists; web/mobile marked **Not Wired** (placeholder only).
- **`msika-apps-service`** and **`rtc-gateway-service`** — in Maven build, **not** in `docs/registry/services-registry.yaml`.
- **`surveillance-service` Flyway `V003__outbox_companion_columns.sql`** deleted in Wave 20 (`9a52cd34`) without replacement visible in audit scope.
- **Core transaction web route** not registered in `app-registry.ts` (orphaned from production command centre link only).
- **Historical audit docs** under `docs/audits/` still reference `ui/experience` paths (documentation orphan).

### What is duplicated

- **Nhume vs dispatch BFF paths** — dual logistics operator UX (`/nhume/*` direct API vs `/operations/dispatch` BFF composition); intentional but confusing.
- **Two service registry files** — `docs/registry/services-registry.yaml` (production baseline) vs `docs/architecture/services-registry.yaml` (architecture register); paths and counts differ.
- **Route count documentation** — multiple stale values (370, 398, 416) vs code constant **417**.

### Documentation stale vs code

| Document | Claims | Code reality |
|----------|--------|--------------|
| `FRONTEND_ARCHITECTURE.md`, `ROUTE_MAP.md`, `FRONTEND_IMPLEMENTATION_STATUS.md` | `EXPECTED_ROUTE_COUNT = 370` | **417** in `routes.ts` |
| `docs/acceptance/experience-platform-acceptance-pack.md` | expected count = 98 | obsolete |
| `docs/doctrine/doctrine-gap-matrix.md` (2026-04-11) | `ui/experience` 219 routes | fork removed |
| `docs/audits/BACKEND_NOT_SURFACED_REGISTER.md` | BNS-001 fixture-only | BFF live; hook missing |
| `SERVICE_ARCHITECTURE_REGISTER.md` | links `docs/architecture/services-registry.yaml` for ownership | production seed uses `docs/registry/` |
| Nhume port in architecture register | 8120 | `application.yml` uses **8210** |

### Urgent restoration priorities

1. **Align dual-emit** with canonical envelope or document/enforce adapter consumers.
3. **Add `workflow-service` core-transaction event emission** for state-machine SoR alignment.
4. **Wire UBOMI** through BFF or mark surfaces honestly until live.
5. **Register `msika-apps-service` and `rtc-gateway-service`** in production registry.
6. **Reconcile documentation** to route count 417 and post-GAP-010 shell paths.
7. **Stabilize Nhume** runtime (schema/entity, compose health).
8. **Remove or label MOCK fallbacks** in `AIDiagnosticAssistant.tsx`.

### Requires human review

- Whether `ui/experience` retirement lost any unmigrated routes (convergence inventory claims full lift; spot-check recommended).
- Whether surveillance `V003` deletion was intentional schema consolidation.
- Whether direct `/api/v1/nhume/*` and `/api/v1/ndila/*` browser paths should remain exceptions to BFF-only doctrine.
- ERP/fleet/contracts depth vs acceptable partial maturity for staging demos.

---

## 17.2 Methodology

### Audit passes

| Pass | Scope | Status |
|------|-------|--------|
| **Pass 1** (initial) | HEAD-anchored on `b3407039`; recent ~30 commits; selective pickaxe; canonical docs + current code | Complete |
| **Pass 2** (full history) | **First commit → latest:** `484c899b` (2026-02-07) → `b3407039` (2026-05-29); **1,078 commits**; all 28 deletion commits; 40+ pickaxe terms; era snapshots of `services/pom.xml` and `EXPECTED_ROUTE_COUNT` | Complete — see supplement |

### Branches and commit ranges reviewed

| Scope | Range |
|-------|-------|
| Outer wrapper repo | `d1ea5f0` → `f85986a` (5 submodule pointer commits) |
| Impilo-vNext (full) | **`484c899b` → `b3407039`** (1,078 commits, 2026-02-07 → 2026-05-29) |
| Era anchors | `484c899b`, `683821bc`, `0501e103`, `319b2d3e`, `b6776009`, `8c561460`, `2b82d766`, `04b2a0cb`, `47de393f`, `1f26349e`, `9a52cd34`, `b3407039` |

### Git commands run (equivalents)

**Pass 1 + Pass 2 combined:**

```bash
git rev-list --count HEAD
git log --reverse --oneline | head -1
git branch --show-current && git rev-parse HEAD
git log --oneline -30
git log --diff-filter=D --summary --all --format="%h %ad %s" --date=short
git log --diff-filter=D --oneline --all
git log --diff-filter=D --summary --all -- '**/pom.xml'
git log --all --oneline -- services/pom.xml
git log --all --oneline -- ui/one-ui-shell/src/lib/routes.ts
git log --all --oneline -- contracts/core-transaction.ts
git log --all --oneline -- docs/doctrine
git log -S "EXPECTED_ROUTE_COUNT" --oneline --all
git log -S "core.transaction.events" --oneline --all
git log -S "skipTests" --oneline --all
git log -S "ui/experience" --oneline --all
git log -S "SecurityConfig" --oneline --all
# 40+ additional pickaxe sweeps (Tshepo, Vito, FHIR, kafka, pharmacy, etc.)
git show <era>:services/pom.xml  # module count at era boundaries
git show <era>:ui/one-ui-shell/src/lib/routes.ts  # EXPECTED_ROUTE_COUNT evolution
git show 8c561460 --stat && git show 1f26349e --stat
git shortlog -sn --all
```

### Documents read

Root and `docs/`: `README.md`, `AGENTS.md`, `CLAUDE.md`, `INTEGRATED_OPERATING_MODEL.md`, `FRONTEND_ARCHITECTURE.md`, `FRONTEND_BACKEND_PARITY_AUDIT.md`, `FRONTEND_PARITY_BACKLOG.md`, `PRODUCTION_READINESS_AUDIT.md`, `ROUTE_MAP.md`, `SERVICE_WIRING_MATRIX.md`, `TESTING_AND_SMOKE_CHECKS.md`, `WEB_MOBILE_PARITY_MATRIX.md`, `KNOWN_LIMITATIONS.md`, `DOCTRINE_ALIGNMENT_CHECKLIST.md`, `DOCTRINE_COMPLIANCE_MATRIX.md`.

Doctrine: `docs/doctrine/CORE_TRANSACTION_DOCTRINE.md`, `CORE_TRANSACTION_STATE_MACHINE.md`, `THREE_CORE_JOURNEYS.md`, `PERSON_JOURNEY.md`, `PROVIDER_JOURNEY.md`, `PLATFORM_BACK_OF_HOUSE_JOURNEY.md`, `NOMPILO_INTELLIGENT_JOURNEY_COMPANION.md`, `health-os-doctrine.md`.

Architecture: `SERVICE_ARCHITECTURE_REGISTER.md`, `core-transaction-event-model.md`, `core-transaction-plane-map.md`, PACS/telemedicine/document pipeline docs.

Frontend: `docs/frontend/FRONTEND_IMPLEMENTATION_STATUS.md`, `BACKEND_CAPABILITY_TO_FRONTEND_SURFACING_MATRIX.md`, `GAP_CLOSURE_RULES.md`.

Audits: all 12 files under `docs/audits/`.

Contracts: `contracts/core-transaction.ts`, `contracts/asyncapi/core-transaction-events.asyncapi.yaml`, OpenAPI slices referenced in surfacing matrix.

### Code and config inspected

- `services/pom.xml`, `services/experience-bff/`, dual-emit outbox publishers (6 services)
- `ui/one-ui-shell/src/lib/routes.ts`, `maturity.ts`, `app-registry.ts`, core-transaction and dispatch pages
- `apps/mobile/citizen-app`, `apps/mobile/provider-app` navigation and core-transaction screen
- `docker-compose.yml`, `compose/experience/docker-compose.yml`, `docker-compose.sovereign.yml`
- `.github/workflows/ci.yml`

### Assumptions and limitations

- Audit is **read-only**; no runtime stack was started in this pass.
- Outer git repo is a thin wrapper; history lives in **Impilo-vNext** submodule.
- **Pass 2** covered all 1,078 commits via deletion inventory, pickaxe sweeps, and era snapshots — but **not** per-file blame on every path.
- Pickaxe (`-S`) counts commits where a string was added/removed, not line-level churn.
- "Wired" status inferred from registry YAML, hook/client presence, BFF controller inventory, and parity matrix — not live HTTP probes.
- Submodule working tree may contain uncommitted changes; HEAD commit `b3407039` is the baseline.
- For ~78 days (2026-03-11 → 2026-05-28) **two web UX codebases** coexisted (`ui/experience` + `one-ui-shell`); pre-GAP-010 history requires fork-aware analysis (documented in supplement §7).

---

## 17.3 Canonical Doctrine Baseline

| Element | Canonical source | Current status |
|---------|------------------|----------------|
| Health Operating System | `docs/doctrine/health-os-doctrine.md` | **Preserved** — one shell, person anchor, governed runtime |
| Seven planes | `docs/architecture/planes/00-production-plane-doctrine.md` | **Preserved** — trust, registry, clinical, data, integration, experience, enterprise |
| Core Transaction formula | `CORE_TRANSACTION_DOCTRINE.md` | **Preserved** — Person + Need + Context + … = Trusted Health Transaction |
| Three synchronized journeys | `THREE_CORE_JOURNEYS.md`, journey maps | **Preserved** in docs; **Partial** in UI depth |
| State machine | `CORE_TRANSACTION_STATE_MACHINE.md`, `contracts/core-transaction.ts` | **Preserved** in contract; **Partial** in runtime enforcement |
| Event doctrine | `core-transaction-event-model.md`, AsyncAPI | **Partial** — dual-emit exists; envelope not enforced |
| Nompilo doctrine | `NOMPILO_INTELLIGENT_JOURNEY_COMPANION.md` | **Partial** — global launcher present; BFF command path stubbed |
| Anti-duplication rule | Doctrine + `AGENTS.md` | **At risk** — MOCK clinical fallbacks; dual logistics paths |
| Source-of-truth boundaries | Doctrine + `INTEGRATED_OPERATING_MODEL.md` | **Mostly preserved** — BFF composes; no evidence BFF owns clinical SoR |
| Service Architecture Register | `SERVICE_ARCHITECTURE_REGISTER.md` | **Preserved** — ~155 classified entries; 2 Maven modules missing from production registry seed |

---

## 17.4 Major Functionality Timeline

> **Full-history version** (1,078 commits, `484c899b` → `b3407039`). Extended analysis: [`VNEXT_FULL_HISTORY_ARCHAEOLOGICAL_SUPPLEMENT.md`](VNEXT_FULL_HISTORY_ARCHAEOLOGICAL_SUPPLEMENT.md) §9.

| Period / Commit Range | Major Additions | Major Removals | Major Refactors | Risk Notes |
|----------------------|-----------------|----------------|-----------------|------------|
| **2026-02-07** `484c899b`–`873990cf` | Scaffold **20 modules**; Vito; Tshepo 6-way split; Tuso/Varapi; **one-ui-shell from birth** | — | `product-registry` → **msika** (`683821bc`) | Sovereign registry foundation |
| **2026-02–03** service waves | +43 services (63 modules by Mar 11); portal/ops consoles | Local event types (`95e2883e`) | Wave skeletons | Rapid AI expansion |
| **2026-03-11** `0501e103` | **`ui/experience` fork created** (parallel UX) | — | Dual web stacks begin | **78-day duplication window** |
| **2026-03-11** `319b2d3e` | Experience BFF skeleton (DB-backed) | — | v1.1 enforcement | BFF not yet pure proxy |
| **2026-04-11** `b6776009` | Health OS doctrine; **79 modules** | — | Header/identity alignment | `doctrine-gap-matrix` dated here |
| **2026-04-13** `8c561460` | Resilience/cache proxy | **BFF: 40 Flyway, 123 tables, 23 entities** (~5,367 lines) | **Pure proxy architecture** | Doctrine-aligned SoR fix |
| **2026-04-13** `2b82d766` | — | **33× SecurityConfig.java** | Compile fix | dispatch/nhume still open at HEAD |
| **2026-04** `04b2a0cb` | `routes.ts`; **EXPECTED_ROUTE_COUNT = 252** | `next.config.js` | Route invariant born | — |
| **2026-05-16** `47de393f` | **`core-transaction.ts`**; **98 modules**; routes **321** | — | Doctrine runtime + dual-emit | Events in **1 commit only** |
| **2026-05 mid** `c58b3f21`/`2fad91b7` | **Ndila**, **Nhume** | — | Logistics plane | nhume smoke fail |
| **2026-05-28** `1f26349e` | GAP-010 lifts; routes **374** | **`ui/experience` ~989 files** | CI/E2E → one-ui-shell | **Largest deletion ever** |
| **2026-05-28** `d8cfe5e2` | No-stub CI | — | routes **400** | Docs still cite 370 |
| **2026-05-29** `9a52cd34` | Wave 20 sovereign overlay | `surveillance V003` | Demo hardening | Logistics sovereign-only |
| **2026-05-29** `b3407039` HEAD | Logistics UX; routes **417** | — | Mobile core-tx parity | Core-tx hook present (`55e9a983`→`d8cfe5e2`) |

### Growth summary (full history)

| Metric | Start (`484c899b`) | HEAD (`b3407039`) |
|--------|-------------------|-------------------|
| Maven modules | 20 | 103 (+415%) |
| EXPECTED_ROUTE_COUNT | N/A → 252 (Apr) | 417 (monotonic increase) |
| Deletion commits (total) | — | 28 (bundled bulk deletes) |
| `pom.xml` deletions | 0 | **0** |
| Parallel UX forks | 0 → 1 (Mar 11) | 0 (since May 28) |

---

## 17.5 Lost / Reduced / Replaced Functionality Register

| Area | File / Component / Service | Commit Removed or Changed | Previous Capability | Current State | Assessment | Recommendation |
|------|---------------------------|---------------------------|---------------------|---------------|------------|----------------|
| Web UX fork | `ui/experience/**` | `1f26349e` | Full Next.js app (~989 files), Playwright, clinical chrome | Merged into `one-ui-shell` | **Equivalent replacement** (if lift complete) | Run convergence diff audit; keep deprecated-surface guard |
| Web hook | `useCoreTransactionExperience.ts` | Added `55e9a983`; extended `d8cfe5e2` | Core transaction feed + dispatch operator feed | **Present at HEAD** (`b3407039`) | **Preserved** | Pass 1 false positive — file exists |
| Surveillance schema | `V003__outbox_companion_columns.sql` | `9a52cd34` | Outbox companion columns | Deleted | **Requires human review** | Confirm replacement migration or restore |
| Security configs | 33× `SecurityConfig.java` | `2b82d766` | Per-service Spring Security | Many re-added; dispatch/nhume omit | **Partial regression** | Add OAuth2 RS pattern to logistics services |
| Route invariant docs | `ROUTE_MAP.md`, etc. | Multiple | 370 routes documented | Code = 417 | **Documentation stale** | Update all docs to 417 |
| Core tx surfacing audit | `BACKEND_NOT_SURFACED_REGISTER.md` BNS-001 | Pre-`55e9a983` | Fixture-only core transaction | BFF live; hook broken | **Documentation stale** | Update register; fix hook |
| Clinical AI assist | `AIDiagnosticAssistant.tsx` MOCK_* | Ongoing | Live CDS via API | MOCK on failure | **Fixture-presented-as-live** | Label partial; remove silent MOCK |
| Nompilo BFF actions | `CoreTransactionController` feedback/handoff/command | Post-`47de393f` | Full Nompilo orchestration | Stub `ACCEPTED` | **Partial regression** | Wire to guidance/LLM services |
| Event envelope | Domain outbox dual-emit | `47de393f` wave | Canonical AsyncAPI envelope | Raw domain JSON on `core.transaction.events` | **Weaker replacement** | Transform at publish or document bridge |
| Workflow events | `workflow-service` | — | Transaction state SoR | No `core.transaction.events` | **BFF missing event meaning** | Add dual-emit or composition events |
| RTC telehealth | rtc-gateway-service | — | WebRTC media | Blocked by policy | **Acceptable retirement** (staging) | Keep Blocked maturity label |
| UBOMI surfaces | `/ubomi` page | Recent | CRVS UI | Placeholder | **Backend-only** | BFF bridge + honest Not Wired until live |

---

## 17.6 Core Transaction Doctrine Compliance Matrix

| Capability | Core Transaction Stage | State Machine Alignment | Event Emitted | Trust/Audit Meaning | Journey Alignment | Status | Required Fix |
|------------|------------------------|-------------------------|---------------|---------------------|-------------------|--------|--------------|
| Workflow orchestration | MANAGE_STATE_MACHINE | Partial — workflow owns instances | No canonical core topic | Via Tshepo on BFF path | Platform journey | **Partial regression** | Emit workflow lifecycle to `core.transaction.events` |
| PCT queue/encounter | QUEUED → IN_SERVICE | Domain states map partially | Dual-emit (raw) | Purpose-of-use via headers | Person + Provider | **Partial** | Envelope transform |
| Costa/MusheX payment | PRE_SERVICE_PAYMENT_* → FINANCIAL_PROCESSING | Contract states exist | Dual-emit (raw) | Payment gates documented | Person + Platform | **Partial** | Surface payment gate UI depth |
| OROS orders | ORDERS_PENDING → ANCILLARY_IN_PROGRESS | Partial | Dual-emit (raw) | Clinical audit via services | Provider | **Partial** | Unified journey DTO |
| Pharmacy dispense | IN_SERVICE → ORDERS_PENDING | Partial | Dual-emit (raw) | Yes | Person + Provider | **Partial** | Rx journey end-to-end test |
| Msika Flow marketplace | SERVICE_SELECTED → ACCESS_GRANTED | Partial | Dual-emit (raw) | Commerce gating | Person | **Partial** | Fix 501 list routes |
| BFF composition | COMPOSE_EXPERIENCE_VIEW | Reads workflow state | No emit | Correlation IDs in view | All three | **Partial** | Complete hook + Nompilo wire |
| Web `/core-transaction` | Reporting/audit | Displays `currentState` | N/A (read) | Trust banner | Cross-cutting | **Broken chain** | Restore hook file |
| Provider mobile shell | SEE_MY_WORK → COMPLETE_TRANSACTION | Lists/applies actions | Via BFF | Partial | Provider | **Partial** | Deepen command/handoff |
| Nompilo feedback | GIVE_FEEDBACK | Contract events defined | Stub only | Not audited | Person | **Serious regression** | Implement BFF downstream |
| Emergency override | EMERGENCY_OVERRIDE branch | In `core-transaction.ts` | Unclear emit path | Break-glass policy | All | **Requires human review** | Trace emergency flow E2E |

---

## 17.7 Seven-Plane Regression Matrix

| Plane | Services / Surfaces Reviewed | Preserved | Regressed | Duplicated | Missing / Orphaned | Required Fix |
|-------|------------------------------|-----------|-----------|------------|-------------------|--------------|
| **1. Trust** | Tshepo cluster, Mvumo, audit, BFF admin trust | Policy engine, ext_authz path | SecurityConfig gaps on logistics | — | Keys/federation admin blocked | Expand trust surfaces; secure nhume/dispatch |
| **2. Registry** | Vito, Varapi, Tuso, Zibo, Msika, Indawo, Ubomi | Services + BFF proxies | Partial UI depth | — | **UBOMI not wired** | BFF bridge for Ubomi |
| **3. Clinical** | Butano, PCT, OROS, Pharmacy, Inpatient | SHR Live (matrix) | MOCK CDS fallback | — | Unified PCT journey model | Journey DTO; remove MOCK |
| **4. Data / Intelligence** | NDR, reporting, surveillance, Ndila, search | Ndila service built | Surveillance V003 removed | — | Ops map dashboards thin | Restore migration; surface intelligence |
| **5. Integration** | Hub, notifications, jobs, offline, PACS | Hub + adapters in build | ~65 services not in compose | Nhume direct API + BFF | rtc-gateway registry gap | Register rtc-gateway; compose coverage |
| **6. Experience** | one-ui-shell, BFF, mobile, Nompilo | Single shell canonical | **Missing core-tx hook** | Logistics dual paths | Core-tx not in app-registry | Fix hook; registry maturity |
| **7. Enterprise** | Costa, MusheX, coverage, GL, HR, nhume, dispatch | Finance slices wired partial | Nhume runtime unstable | Dispatch vs nhume UX | Fleet/contracts ERP gaps | Stabilize nhume; unify operator UX |

---

## 17.8 Service Architecture Register Compliance

| Service | In Register | In Code | In Build | In Deployment | Plane Correct | Ring Correct | Boundary Violations | Recommendation |
|---------|-------------|---------|----------|---------------|---------------|--------------|---------------------|----------------|
| experience-bff | Yes | Yes | Yes | experience compose | Yes (experience) | Ring 1 | Must not become SoR — **compliant** | Keep composition-only |
| workflow-service | Yes | Yes | Yes | core-tx e2e mock only | Yes | Ring 1 | Missing event emit | Add events |
| nhume-service | Yes | Yes | Yes | sovereign overlay only | Yes (enterprise) | Ring 2 | No SecurityConfig | Add OAuth2 RS |
| ndila-service | Yes | Yes | Yes | sovereign overlay | Yes (data) | Ring 2 | PostGIS disabled in demo | Document demo limits |
| dispatch-service | Yes | Yes | Yes | sovereign overlay | Yes (enterprise) | Ring 2 | No SecurityConfig | Add OAuth2 RS |
| msika-apps-service | **No** (prod registry) | Yes | Yes | No | Unclear | — | **Unregistered** | **Escalate** — add to registry |
| rtc-gateway-service | **No** (prod registry) | Yes | Yes | No | Integration | — | **Unregistered** | Register; mark Blocked for RTC |
| surveillance-service | Yes | Yes | Yes | sovereign | Yes (data) | Ring 2 | V003 deleted | Human review |
| community-service | Yes | Yes | Yes | No | Yes | Ring 2 | Only 1 of 3 `wired` registry entries | OK for social Live |
| tshepo-service (monolith) | Yes | Yes | Yes | trust e2e | Trust | Ring 0 | Monolith risk noted in register | Sunset aliases per policy |
| **All other ~80 services** | Mostly yes | Yes | Yes | Mostly **no** compose | Per register | Per register | Frontend wiring unknown-or-partial | Prioritize by parity matrix |

---

## 17.9 Backend / BFF / Frontend / Mobile Parity Matrix

| Capability | Backend | Contract | BFF | Web | Citizen Mobile | Provider Mobile | Real Integration | Mock/Fixture | Status | Action |
|------------|---------|----------|-----|-----|----------------|-----------------|------------------|--------------|--------|--------|
| Core Transaction | workflow + domain | `core-transaction.ts` | Yes 11 endpoints | **Hook missing** | Partial shell | Live shell | Partial | No | **Partial regression** | Restore hook |
| Social Timeline | community-service | social.openapi | Yes | Live | Live | Partial | Yes | No | **Preserved** | Moderation depth |
| BUTANO SHR | butano-service | butano.custom | Yes | Live | Partial | Partial | Yes | No | **Improved** | Mobile personal sections |
| Trust Admin | tshepo-* | tshepo-authz | Yes | Partial | Partial | Partial | Partial | No | Partial | Expand settings |
| Vito/Varapi/Tuso | registry services | openapi slices | Yes | Partial | Partial | Partial | Partial | No | Partial | Registry hub depth |
| Telemedicine | BFF orchestration | experience-bff | Yes | Partial | Partial | Partial | Scheduling yes | RTC blocked | Partial | Keep Blocked label |
| Ndila | ndila-service | ndila.openapi | Partial proxy | Partial panel | Partial SDK | Partial | Sovereign compose | No | Partial | Map component rollout |
| Nhume | nhume-service | controllers | Yes + direct API | Partial `/nhume/*` | nhume-track | Partial | Unstable runtime | No | Partial | Stabilize + unify UX |
| Dispatch ops | dispatch-service | workflow/dispatch | Yes | Partial dispatch page | — | Partial | Sovereign only | No | Partial | Fix dispatch hook |
| MusheX/Costa | mushex, costa | finance openapi | Yes | Partial | Partial | Partial | Partial | No raw browser mushex | Partial | Mobile finance parity |
| Msika/Marketplace | msika-flow | msika-flow | Yes | Partial | Partial | Partial | Partial | Some 501 routes | Partial | Honest blocked states |
| UBOMI CRVS | ubomi-service | ubomi.openapi | Minimal | **Not wired** | **Not wired** | — | Backend only | Placeholder | **Backend-only** | BFF bridge |
| Nompilo | guidance, llm | experience-bff | Partial stubs | Partial `/ask` | Global launcher | Global launcher | Partial | Offline fallback OK | Partial | Wire command path |
| Fundo LMS | learning-service | learning v11 | Yes | Partial | Shallow | Shallow | Partial | No | Partial | Mobile module depth |
| Integration Hub | integration-hub | hub openapi | Yes | Partial admin | — | — | Partial | No | Partial | Admin depth |
| Public Health | surveillance, campaigns | surveillance | Yes | Partial | Partial | Field tasks | Partial | No | Partial | Mobile field parity |
| PACS/Imaging | pacs-adapter, oros | PACS pipeline doc | Yes | Partial viewer | — | tools/pacs | Partial | No | Partial | E2E imaging journey |

---

## 17.10 Build, POM, Dependency and Configuration Regression Register

| Area | File | Commit / Change | Previous State | Current State | Risk | Assessment | Recommendation |
|------|------|-----------------|----------------|---------------|------|------------|----------------|
| Maven modules | `services/pom.xml` | Stable growth | All services built | 89 modules, no deletions | Low | **Preserved** | Add CI full-reactor compile job |
| Deleted POMs | `**/pom.xml` | git history | — | **None deleted** | Low | **Preserved** | — |
| skipTests CI | `.github/workflows/ci.yml` | Multiple | Tests on install | `-DskipTests` on package paths | Medium | **Test regression** | Separate verify job without skip |
| SecurityConfig | 33 services | `2b82d766` | Broken compile configs | Stripped then partial restore | Medium | **Dependency regression** | Audit dispatch/nhume |
| Surveillance migration | `V003__*.sql` | `9a52cd34` | Companion columns | Deleted | Medium | **Migration regression** | Human review |
| Route count | `routes.ts` | `b3407039` | 370 documented | **417** enforced | Low (growth) | **Doc drift** | Update docs |
| Compose coverage | `compose/experience/*.yml` | `9a52cd34` | Base + sovereign | ~23 Java services deployed | High | **Runtime/deployment missing** | Expand staging matrix |
| Nhume port | `application.yml` vs register | — | 8120 in docs | **8210** live | Low | **Documentation stale** | Fix port-allocation doc |
| Registry seed | `services-registry.yaml` | seed script | 86 services | Missing msika-apps, rtc-gateway | Medium | **Register gap** | Regenerate seed |
| Flyway surveillance | deleted V003 | `9a52cd34` | Outbox columns | Gone | Medium | **Migration regression** | Verify schema still valid |
| OAuth2 BFF tests | ci.yml | — | Full security | Auto-config excluded in one job | Low | **Acceptable for CI** | Document test profile |
| ui/experience guard | `deprecated-surface-guard.yml` | GAP-010 | Two shells | Guard active | Low | **Improved** | Keep |

---

## 17.11 Route and Surface Integrity Register

### EXPECTED_ROUTE_COUNT invariant

| Source | Value |
|--------|-------|
| **`ui/one-ui-shell/src/lib/routes.ts` (authoritative)** | **417** |
| `routes.test.ts` + `route-parity-check.mjs` | Enforces 417 |
| `FRONTEND_ARCHITECTURE.md`, `ROUTE_MAP.md`, `FRONTEND_IMPLEMENTATION_STATUS.md` | **370 (stale)** |
| User audit prompt reference | 370 (stale) |
| File header comment in `routes.ts` | "398 routes" (stale) |

**Verdict:** The invariant **holds in code** at **417**, not 370. Documentation and audit prompts lag by **47 routes** (post-GAP-010 shadow routes + nhume family + upstream additions). This is **registry growth**, not silent deletion — but **doc drift is a regression risk** for future agents that might "correct" count downward.

### Selected route / screen register

| Route / Screen | Journey | Plane | Expected Capability | Current Wiring | Maturity | Problem | Required Fix |
|----------------|---------|-------|---------------------|----------------|----------|---------|--------------|
| `/core-transaction` | Cross-cutting | Experience | Transaction list, audit | BFF yes; **hook missing** | partial | Broken import | Restore hook |
| `/operations/dispatch` | Platform | Enterprise | Live dispatch commands | BFF yes; **hook missing** | partial | Broken import | Restore hook |
| `/nhume/*` (18 routes) | Platform | Enterprise | Logistics ops | BFF + direct API | partial | Dual path confusion | Unify operator UX |
| `/ndila` | Platform | Data | Geospatial intel | BFF proxy partial | partial | Map depth | Ndila panel rollout |
| `/ubomi` | Registry | Registry | CRVS | Placeholder | not_wired | No BFF depth | Bridge or label |
| `/telemedicine/*` | Person/Provider | Clinical | Teleconsult | BFF live scheduling | partial | RTC blocked | Keep Blocked |
| `/social`, `/communities` | Person | Experience | Social timeline | BFF live | **live** | Moderation thin | OK for staging |
| `/ehr/[patientId]/*` | Provider | Clinical | SHR workspace | BFF live | **live** | Mobile gap | Citizen record parity |
| `/wallet`, `/finance/*` | Person | Enterprise | Payments | BFF no raw mushex | partial | Command depth | Mobile parity |
| `/production-command-centre` | Platform | Experience | Ops maturity hub | Registry JSON | partial | Fake metrics risk (R8) | Live data or label fixture |
| Provider `CoreTransactionJourneyShellScreen` | Provider | Experience | Core tx actions | mobile-api live | partial | Shallow vs web | Deepen handoff |
| Citizen `PersonalScreen` sections | Person | Multi | Health record, wallet, nhume | Mixed BFF | partial | Many sections partial | Per-section matrix |
| Citizen tabs | Person | Experience | 7 main tabs | All present | partial | Depth varies | Parity sweep continue |
| Provider `ClinicalToolsScreen` | Provider | Clinical | SOAP, triage, etc. | Mixed | partial | Tool sub-surfaces vary | Tools inventory test |

---

## 17.12 Source-of-Truth and Duplicate Truth Register

| Domain Truth | Canonical Owner | Duplicate Location Found | Risk | Assessment | Recommendation |
|--------------|-----------------|--------------------------|------|------------|----------------|
| Client identity | Vito | BFF DTOs (compose only) | Low | Acceptable read model | Keep |
| Provider identity | Varapi | BFF registry proxies | Low | Acceptable | Keep |
| Facility/workspace | Tuso | Shell context store (session) | Low | Orchestration state OK | Document boundary |
| Clinical record (SHR) | Butano | — | Low | **Preserved** | — |
| Transaction state | workflow-service | BFF composed view | Medium | BFF must not write SoR | Enforce read-only compose |
| Payment/claim | MusheX/Costa | Finance UI local form state | Low | OK for commands | Idempotency keys |
| Core transaction events | Kafka canonical topic | Raw domain payloads duplicated | **High** | **Dangerous duplicate semantics** | Envelope adapter |
| Logistics deliveries | nhume-service | dispatch-service tasks | Medium | Overlapping ops models | Clarify ownership |
| Terminology | Zibo | zibo-web direct `/v1` | Medium | Intentional sovereign console | Link + maturity only |
| CDS suggestions | Clinical knowledge API | `AIDiagnosticAssistant` MOCK_* | **High** | **Fixture as live** | Remove silent fallback |
| Route maturity | `registry-maturity.json` | Hardcoded `app-registry.ts` | Low | Dual label sources | Single generator |
| Service registry | `docs/registry/services-registry.yaml` | `docs/architecture/services-registry.yaml` | Medium | **Conflicting implementation** | Merge or declare roles |

---

## 17.13 High-Priority Restoration Backlog

| Priority | Area | Problem | Impact | Recommended Fix |
|----------|------|---------|--------|-----------------|
| **P0** | Trust/security logistics | nhume/dispatch lack SecurityConfig | Unauthenticated surface risk | Apply OAuth2 RS + Tshepo |
| **P0** | Event envelope | Dual-emit raw JSON | Downstream consumers can't trust state machine | Publish canonical envelope |
| **P1** | workflow-service events | No `core.transaction.events` | State SoR invisible to transaction bus | Add dual-emit |
| **P1** | Nompilo BFF stubs | feedback/handoff/command noop | Doctrine violation (unaudited channel) | Wire guidance/LLM |
| **P1** | Documentation | Route count 370 vs 417 | Agents may delete routes | Mass doc update + CI doc check |
| **P1** | UBOMI | Not wired | CRVS capability invisible | BFF bridge + maturity |
| **P1** | Registry gaps | msika-apps, rtc-gateway | "Service not in register" | Regenerate registry |
| **P2** | Nhume runtime | 15/16 smoke | Demo failures | Fix schema/compose health |
| **P2** | Surveillance V003 | Migration deleted | Outbox schema drift | Human review + restore if needed |
| **P2** | MOCK CDS | AIDiagnosticAssistant | Clinical safety perception | Remove or label fixture |
| **P2** | CI coverage | skipTests + partial modules | Regressions slip through | Full reactor compile job |
| **P3** | Audit doc refresh | ui/experience references | Wrong restoration targets | Rewrite audits for one-ui-shell |
| **P3** | ERP surfacing | Fleet/contracts gaps | Enterprise journey incomplete | Phased parity per backlog |

---

## 17.14 Reintegrate / Retire / Keep / Merge Decisions

| Item | Decision | Reason | Suggested Method |
|------|----------|--------|------------------|
| `ui/experience` fork | **Keep New Version** (one-ui-shell) | GAP-010 complete with CI guard | Do not restore fork |
| `useCoreTransactionExperience.ts` | **Keep New Version** | Present at HEAD since `55e9a983` | No restoration needed |
| Dual-emit raw payloads | **Rebuild Using Current Architecture** | Envelope doctrine exists | Outbox transformer library |
| `ui/experience` route files | **Retire** | Superseded by routes.ts entries | N/A |
| Direct nhume/ndila API from browser | **Escalate for Human Review** | Doctrine prefers BFF | ADR on exceptions |
| rtc-gateway-service | **Keep New Version** | RTC intentionally blocked | Register + Blocked label |
| AIDiagnosticAssistant MOCK | **Merge Old and New** | API path exists | Fallback → explicit partial state |
| surveillance V003 | **Escalate for Human Review** | Deleted in Wave 20 | DBA review |
| Two services-registry YAML files | **Merge Old and New** | Confusion | Single source + generated views |
| 370 route count docs | **Retire** stale numbers | Misleading | Replace with 417 everywhere |
| Provider mobile core-tx shell | **Keep New Version** | Live BFF | Deepen only |
| SecurityConfig mass deletion | **Keep New Version** | Fixed compile | Re-add per service with deps |

---

## 17.15 Specific Reintegration Plan

| Capability | Current Problem | Recommended Approach | Why | Risk | Required Files / Areas |
|------------|-----------------|----------------------|-----|------|------------------------|
| Canonical event envelope | Raw dual-emit | Shared `CoreTransactionEventPublisher` in `shared-core` | Doctrine + AsyncAPI compliance | Medium — consumer breakage | 6 outbox publishers, `shared-core`, contract tests |
| Workflow lifecycle events | Silent on bus | Add dual-emit in workflow outbox | BFF SoR must emit | Medium | `workflow-service/.../OutboxPublisher.java` |
| Nompilo transaction commands | BFF stubs | Proxy to `guidance-service` / `llm-orchestration-service` | Auditable Nompilo | Medium | `CoreTransactionController.java`, OpenAPI |
| UBOMI CRVS | Placeholder web | Add BFF `/internal/v1/ubomi/*` + `useUbomiRegistry.ts` wiring | Registry truth in ubomi-service | Low | `experience-bff`, `ui/one-ui-shell/src/app/ubomi/` |
| Documentation route count | 370 stale | Run doc generator; grep CI for `EXPECTED_ROUTE_COUNT = 370` | Prevent agent regression | Low | `ROUTE_MAP.md`, `FRONTEND_*`, acceptance pack |
| GAP-010 verification | Uncertainty on full lift | Diff `c1e23126` inventory vs current `routes.ts` + page files | Prove no orphan routes | Low | `docs/frontend/GAP-010` inventory, scripts |
| Nhume stability | Smoke fail | Fix Flyway/entity mismatch; sovereign health wait | Demo readiness | Medium | `nhume-service`, compose, `PRODUCTION_READINESS_AUDIT.md` |
| Service registry | 2 missing modules | Update `seed-registry.mjs` | Register governance | Low | `docs/registry/services-registry.yaml`, CI advisory job |

---

## 17.16 Guardrails for Future AI Coding Agents

### Mandatory preflight checklist

1. Read `AGENTS.md`, `docs/doctrine/CORE_TRANSACTION_DOCTRINE.md`, and `docs/registry/services-registry.yaml`.
2. Map feature to Core Transaction stage + journey + plane before coding.
3. Prove chain: screen → hook → BFF → service → contract → test.
4. Check `EXPECTED_ROUTE_COUNT` in `routes.ts` — **never decrease without documented rationale and test update**.
5. Verify service exists in **both** registry and `services/pom.xml`.
6. No new files under `ui/experience/` (CI guard).
7. No stubs in production paths (`npm run test:no-stubs`).
8. Label maturity: Live / Partial / Fixture / Not wired / Blocked.

### CI checks to add or strengthen

| Check | Purpose |
|-------|---------|
| Full reactor `mvn -DskipTests compile` | Catch module drift |
| Hook file existence vs surfacing matrix | Prevent orphaned imports |
| `EXPECTED_ROUTE_COUNT` doc sync | Grep docs for stale 370 |
| Deleted route/page guard | Fail if `src/app/**/page.tsx` removed without routes.ts update |
| Contract drift: `core-transaction.ts` vs AsyncAPI | Enum parity |
| Registry completeness vs `services/pom.xml` | msika-apps class gaps |
| `core.transaction.events` consumer contract test | Envelope validation |
| Web-mobile hook parity | Same BFF path names |

### Protected artifacts

- `docs/doctrine/CORE_TRANSACTION_*.md` — require human review for deletion
- `contracts/core-transaction.ts` — ADR for state enum changes
- `ui/one-ui-shell/src/lib/routes.ts` — route count invariant
- `docs/registry/services-registry.yaml` — ownership changes via seed script + PR rationale
- `services/pom.xml` module list — no silent removals

### Code owners recommendation

| Area | Owner module |
|------|--------------|
| Core transaction contracts | Platform architecture |
| Experience BFF | Experience plane squad |
| one-ui-shell routes | Web orchestration |
| Mobile parity | Mobile squad |
| Trust/Tshepo | Trust plane |
| Registry services | Registry squad |
| Logistics (nhume/ndila/dispatch) | Enterprise supply |

### ADR enforcement

Structural changes (new service, state enum, registry ownership, retirement of UX fork) require `docs/adr/` entry before merge.

---

## Appendix A — Wired services in production registry (only 3 of 87)

| Service | `frontend_wiring_status` |
|---------|--------------------------|
| community-service | wired |
| experience-bff | wired |
| learning-service | wired |

All other 84 services: `unknown-or-partial`.

---

## Appendix B — GAP-010 convergence assessment

| Phase | Commit | Outcome |
|-------|--------|---------|
| Inventory | `c1e23126` | Documented lift scope |
| 1a–1f | `25b632a7`–`5239eac3` | Clinical chrome, forms, telemed, registry, facility-ops lifted |
| 3 | `b23cc907` | CI + Playwright → one-ui-shell |
| 4–5 | `6ab3bd5e` | Scripts, registries, meta docs |
| 6 | `1f26349e` | **ui/experience deleted** |
| 7 | `af85e16a`, `d8cfe5e2` | Forward docs + no-stub policy |

**Assessment:** Intentional **equivalent replacement**. Residual risk: hook file and audit doc lag, not fork survival.

---

*End of audit report. See `docs/VNEXT_FEATURE_INVENTORY.md` for persistent feature memory.*
