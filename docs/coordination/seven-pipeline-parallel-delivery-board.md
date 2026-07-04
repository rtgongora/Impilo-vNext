# Impilo vNext Seven Pipeline Parallel Delivery Board

**Coordinator**: Fable coordinating session (`claude/impilo-vnext-coordination-75fzl0`)
**Date**: 2026-07-04
**Status**: ACTIVE — first board issue. Update this document and
[`parallel-delivery-register.md`](parallel-delivery-register.md) on every assignment,
evidence submission, and merge decision.

---

## 1. Anchor branch verification

| Item | Value |
|---|---|
| Anchor branch | `claude/web-session-anchor-nnnkf6` |
| Verified tip (local == `origin`) | `d44bb6022` — `chore(preview): Kafka listener opt-ins for live + khuluma (W0)` |
| Remembered tip `e101a2701` | **NOT FOUND** in this clone. Anchor has moved past it. `d44bb6022` is authoritative. |
| Anchor working state | Clean; no uncommitted changes in the coordinator clone. |
| In-flight work on anchor | **W0 session-suite/telemedicine workstream is ACTIVE** (last ~14 commits: rtc-gateway LiveKit webhook ingestion, outbox→Kafka bridge, live/khuluma `impilo.rtc.*` consumers, session-template registry, adaptive session room, mobile session package, LiveKit helm/preview lane). Treat the W0 surface as **LEASED by another session** — see the No-Touch List (§11). |

## 2. Existing worktree / branch state

- Worktrees: **one** — the coordinator clone at `/home/user/Impilo-vNext` on
  `claude/impilo-vnext-coordination-75fzl0` (based exactly on anchor tip `d44bb6022`).
- Local branches: coordination branch + local `claude/web-session-anchor-nnnkf6` (both at `d44bb6022`).
- Relevant remote branches: `origin/claude/web-session-anchor-nnnkf6` (anchor),
  `origin/staging`, `origin/production`, `origin/peter/vnext-1.0|2.0`,
  `origin/split/pr3-fundo-ui`, `origin/split/pr4-stabilization`,
  `origin/fix-impilo-fundo`, `origin/impilo-fundo-upgrade`,
  `origin/claude/staging-ux-orchestration-remediation-*`, `origin/claude/implement-impilo-vnext-issues-fr4iV`.
- **Fundo branches are stale/superseded**: audit confirms HEAD's native LMS
  (`V006__learning_fundo_native_lms.sql`, `FundoNativeLmsIT`, `V014` language metadata)
  re-implements and exceeds `fix-impilo-fundo` / `impilo-fundo-upgrade` / `split/pr3-fundo-ui`.
  **Do not merge or resurrect them.**

## 3. Seven-pipeline capability map (repo-grounded, 2026-07-04)

Legend: ✅ wired · 🟡 partial · 🔶 stubbed (honest seam) · ❌ missing

### P1 — MusheX / COSTA / Coverage (financial)
- ✅ COSTA bill lifecycle (`costing-engine-service` `BillService`: draft→lines→applyCoverage→approve→finalize; outbox events).
- ✅ Coverage adjudication split (`ExemptionEngine.split()` → patient/insurer/subsidy/write-off; `CoverageServiceClient` → coverage-service eligibility).
- ✅ MusheX payment intents + receipts (`PaymentIntentService`, `ReceiptService` on PAID), reconciliation (`ReconciliationService`, BFF triple-match), GL journals from `costa.bill.finalized` / `mushex.payment.status.changed`.
- ✅ Finance UI: `one-ui-shell` `finance/costa/encounter/[encounterId]`, `BillingPanel`, plus `costa-console`, `mushex-*` consoles.
- 🟡 Encounter→bill hook lives **only in BFF** (`MobileEncounterController.java:133`, `MobileDischargeController.java:65`, `TeleconsultController.java:1291`); `pct DischargeWorkflow` "BILLING" blocker makes no COSTA call. BFF `V17` `costa_bill_id` bridge column appears unused (no write-back).
- 🟡 Payment-intent amount is caller-supplied, not derived from `patientPayable`.
- 🔶 All MusheX payment rails are honest stubs (`PaymentRailAdapter.liveCapable()=false`, `SandboxMockAdapter`); no live money movement.
- ⚠️ **Hazard**: `mushex kafka/CostaEventConsumer.onBillFinalized` is a NO-OP that would **double-bill the full amount if naively enabled** (`CostaEventConsumer.java:60-68`).
- ❌ No billing/payment surface in `ui/ehr` (clinical context) — the "billing visible in clinical journey" gap.
- ❌ No dedicated shortfall entity (implicit `BillHeader.patientPayable`); no e2e test spanning encounter→coverage→payment→receipt (only `ExemptionEngineTest` unit math, which is good).

### P2 — Intelligent Queue Management
- ✅ Physical facility queue is wired e2e: `pct-service` `QueueEngine`/`QueueController`, TUSO-authored definitions materialised via `QueueMaterializationService`, BFF proxy (fails clean 502), UI `useQueue.ts` + `pct-web` queue/control-tower/sorting pages. Provider actions: call/complete/no-show/transfer/abandon/pause/resume.
- ✅ Triage is rich: `EdTriageDiscriminatorEngine` (ESI+MTS, acuity 1–5); acuity→priority in `RoutingEngine` (`priority = 6 − acuity`).
- 🟡 Shift truth is **split three ways**: PCT `WorkspaceSessionService` vs BFF `ShiftController`→TUSO vs Vashandi `RosterService`.
- 🔴 **Bug**: BFF `QueueController.triageEntry` (`QueueController.java:294-302`) sends status `"IN_TRIAGE"`; `QueueItemStatus` enum has no such value → `valueOf` throws → 500. The `/entries/{id}/triage` transition is dead.
- ❌ No queue-level **escalate** action (Khuluma `EscalationController` is telemedicine-SLA only, unwired to physical queue).
- ❌ No virtual-queue engine — only `pct_referrals.routing_kind/routing_pool_id` metadata (V020); routing is `EXPLICIT_V1` (caller supplies target queue; no auto workspace/cadre/urgency selection).

### P3 — PCT Patient Care Tracker
- ✅ Encounter lifecycle (STARTED/ON_HOLD/COMPLETED, allow-set validation, single-active-encounter, journey state machine, outbox events); shift start (`WorkspaceSessionService`).
- ✅ Cadre engine (`core/cadre/CadreEngine.java` — deterministic families/workflow sets/step-up/break-glass) + `FormScopeEngine` + form lifecycle with author signature and **form-level countersignature**.
- ✅ Inpatient: admission w/ idempotent PCT handshake, beds, MAR/EWS, **procedures with WHO checklist + MVUMO consent gate + anaesthesia scoring** (strong), discharge summary gated on clearances → FHIR Composition → Butano event + Khuluma follow-up.
- 🟡 Lab/imaging orders flow via **form extraction** → `OrosIntegration.submitOrder`, best-effort (OROS down → FAILED provenance row, no retry). Observation/Procedure extraction to BUTANO is event-only PENDING.
- 🔴 **Contract divergence**: `pct OrosIntegration.submitOrder` posts `{journeyId, payload}` (`OrosIntegration.java:54`) while `inpatient OrosOrderClient.placeOrder` posts `{orderType, priority, patientCpid, encounterRef, items[]}` (`OrosOrderClient.java:50-56`) — two shapes for OROS `/v1/orders`.
- ❌ **Prescribing is absent from the encounter cockpit** — cadre surfaces a PRESCRIBE action but pct-service has no medication/prescribe controller or PharmacyIntegration; prescriptions live only in pharmacy-service fed from OROS.
- ❌ Ward-round `newOrders`/`escalation` are free text (not routed to OROS, no events). No countersign gate on discharge summary or ward-round entries (form-level only).

### P4 — Dura Commodities
- ✅ `inventory-service` **is Dura** (9 `Dura*Controller`s, `V005–V011__dura_*` migrations, BFF `DuraBffController`, UI `app/work/dura/page.tsx` + `useDura.ts`). Append-only ledger + atomic on-hand projection + idempotency + outbox (`LedgerServiceImpl.java:69-136`); reserve/issue/consume/return/administer; FEFO; stockouts query.
- ✅ Clinical hooks: `DuraPctController` (availability/reserve/consume, real ledger postings); pharmacy → Kafka `pharmacy.stock.movement.requested` → `PharmacyConsumer` → consumption postings (store-code resolution included).
- 🔶 `inventory-elmis-adapter` is a real fail-closed connector (NOT_LIVE on placeholder URL — honest); `ExternalSyncServiceImpl` state machine real.
- 🔶 `pharmacy-elmis-adapter` `DispenseSyncService.triggerSync` (`:37-46`) is a stub — persists RUNNING, never syncs.
- 🟡 `LedgerServiceImpl.publishStockLevelTelemetrySnapshot` (`:224-249`) is defined but never called — low-stock telemetry gap.
- ❌ No tests for the `PharmacyConsumer` Kafka path or `DuraPctController`.

### P5 — Telemedicine end-to-end  ⚠️ W0 ACTIVE ON ANCHOR
- ✅ RTC stack (W0): rtc-gateway session provisioning, template-driven **fail-closed** token grants, HS256-verified LiveKit webhook ingestion, `impilo.rtc.*.v1` outbox→Kafka, live/khuluma consumers (helm opt-in), shared `libs/session-templates` doctrine-as-data, `AdaptiveSessionRoom` UI + mobile session package.
- ✅ Teleconsult request + lifecycle (`TelemedicineOrchestrationService` DRAFT→…→COMPLETED); routing validated for PRACTITIONER/PROVIDER, TEAM/SPECIALTY_POOL, WORKSPACE, FACILITY_SERVICE.
- ✅ Consent (MVUMO → tshepo decision; media token blocked without consent reference unless EMERGENCY); billing hook (TELECONSULT_COMPLETED value-trigger → COSTA draft→approve→finalize).
- 🟡 Routing: **ON_CALL / POOL / NATIONAL_POOL / UNIT → 501 NOT_IMPLEMENTED** (`TeleconsultController.java:46,855`); no virtual-hospital/on-call directory.
- 🟡 Waiting room is event-only (`waiting_room.entered` then immediately `started`) — no admission gate backend. Documentation-into-encounter is a thin FHIR `DiagnosticReport` (`TeleconsultController.java:1263`), not a real encounter note.
- 🔶 live-service `onRecordingAvailable` is an explicit W1 TODO stub (`RtcSessionEventsConsumer.java:168-182`).
- ❌ No orders from teleconsult; no integration test proving webhook→Kafka→live/khuluma e2e.
- ⚠️ Possible defect to verify with W0 owner: helm `webhookUrl` uses port `8196` vs rtc-gateway `server.port: 8195`.

### P6 — PACS / Imaging
- ✅ OROS order routing `IMAGING → RouteTarget.PACS` (`RoutingEngine.java:226`); full imaging lifecycle state machine (`ImagingWorkflowService`), each step an outbox event.
- ✅ PACS adapter is real + pluggable: `OrthancClient` (real Orthanc REST) and `ExternalPacsClient` (real DICOMweb QIDO/WADO), fail-closed, selected by `impilo.pacs.backend.provider`; real Orthanc in compose/helm.
- ✅ Machine bridging exists as seams: `RestMwlPublisher` + `DimseMwlPublisher` (real dcm4che UPS N-CREATE) — **default OFF** (honest); BFF `PacsController` STOW-RS/C-STORE-style ingest proxy to Orthanc.
- ✅ Viewer wired (3 engines: DICOMWEB_STACK / OHIF iframe / DWV, policy-resolved) + mobile viewer; result return via `ButanoIntegration.createImagingStudy`/`createDiagnosticReport` (flag-gated, default off); PCT `ImagingLinkService` links studies to encounters.
- ❌ **Facility capability modes not modeled**: PACS backend is one global config; `tuso FacilityCapabilityEntity` generic, no imaging capability seeds — central-with-PACS vs district-machines-only cannot be represented per facility.

### P7 — Fundo Learning (strongest pipeline)
- ✅ Native LMS wired e2e: catalog, enrolment (incl. bulk), progress reconciliation, assessments, certificate lifecycle w/ digest verification; studio/authoring (incl. AI authoring foundation); live sessions bidirectional with live-service (CPD webinars, attendance→completion, check-in tokens); language metadata `V014`.
- ✅ CPD governance: Fundo `certificate.issued.v1` → Varapi PENDING candidate → council-policy-gated accept → CPD ledger (Kafka listener + HMAC webhook); Vashandi consumes CPD summaries for workforce eligibility; role learning requirements + training gates.
- ✅ ~70 UI pages under `one-ui-shell/app/learning/**`; BFF passthrough `LearningController`; 20+ Fundo ITs + golden threads + e2e specs.
- 🟡 Minor: nothing blocking the minimum journey. Main work is evidence, demo script, and polish.

## 4. Safe parallel tasks (GREEN — start immediately)

| ID | Task | Why safe |
|---|---|---|
| G1 | P7 Fundo demo script + evidence pack (run existing ITs/e2e, record results) | Docs/tests only, no shared files |
| G2 | P1 e2e partial-coverage journey spec (encounter→applyCoverage→intent→receipt), initially against service APIs | New test files only |
| G3 | P4 tests for `PharmacyConsumer` Kafka path + `DuraPctController` | New test files only |
| G4 | P2 failing regression test reproducing the `IN_TRIAGE` 500 | New test file only (fix itself is A1) |
| G5 | P6 imaging demo script + honest-seam documentation (MWL OFF, outbound flags) | Docs only |
| G6 | Pipeline demo scripts for P2/P3 minimum journeys | Docs only |
| G7 | P1 EHR billing visibility panel in `ui/ehr` reading **existing** BFF finance endpoints | New UI page in `ui/ehr` (no `one-ui-shell` route registry edits) |

## 5. Serialized / high-risk tasks (RED — coordinator-gated, one at a time)

| ID | Task | Why serialized |
|---|---|---|
| R1 | OROS `/v1/orders` contract unification (pct vs inpatient shapes) | Shared cross-service contract; touches pct, inpatient, oros |
| R2 | Shift-truth consolidation (PCT vs TUSO vs Vashandi) | Trust/operational context foundation; three services + BFF + UI stores |
| R3 | `mushex CostaEventConsumer` double-bill hazard remediation | Payment/event-topic semantics; needs finance-owner decision |
| R4 | Anything touching W0 lease surface (rtc-gateway, live, khuluma consumers, `libs/session-templates`, `contracts/schemas/session-templates`, LiveKit helm/preview values) | Actively owned by the anchor W0 session |
| R5 | Kafka topic/event schema changes (`costa.*`, `mushex.*`, `impilo.rtc.*`, `impilo.learning.*`, `pharmacy.stock.movement.requested`) | Cross-service contracts, silent-breakage risk |
| R6 | `shared-core` (`TrustContext`/`TrustContextHolder`), `ui/one-ui-shell/src/lib/routes.ts`, `app-registry.ts`, api-client | Every pipeline depends on them |
| R7 | Root configs, package locks, helm/docker/compose/ingress, shared DB migrations, Tshepo/Keycloak/OPA, `scripts/guard/*` gate definitions, preview/deploy scripts | Standing no-touch list (§11) |

## 6. Proposed worker assignment (one owner per pipeline)

| Pipeline | Owner | Sub-workers permitted under owner |
|---|---|---|
| P1 Finance | **Claude worker A** | Zen (G2 spec), Cursor (G7 EHR billing panel) |
| P2 Queues | **Claude worker B** | Zen (G4 repro test), Cursor (queue UI polish) |
| P3 PCT | **Claude worker C** | Zen (journey specs) |
| P4 Dura | **Claude worker D** (small scope) | Zen (G3 tests) |
| P5 Telemedicine | **Anchor W0 session (existing)** — coordinator liaises; peripheral-only worker for routing 501s once lease boundary confirmed | — |
| P6 PACS | **Claude worker E** | Cursor (viewer UX polish) |
| P7 Fundo | **Zen Coder Max** (evidence/demo) | Cursor (UI polish only if gaps found) |

Fable coordinator owns: register, branch/worktree hygiene, risk classes, integration sequencing, evidence review, merge recommendation.

## 7. Exact worker prompts

### Claude worker A — P1 finance closure (branch `fable/e2e-costa-mushex-coverage`, worktree `../wt-fable-costa-coverage`)
> You own Pipeline 1 (COSTA/MusheX/Coverage) closure work ONLY. Base: `claude/web-session-anchor-nnnkf6` @ `d44bb6022`. Work exclusively in worktree `../wt-fable-costa-coverage` on branch `fable/e2e-costa-mushex-coverage`.
> Scope, in order: (1) Derive payment-intent amount from `BillHeader.patientPayable` server-side in BFF `FinanceController.createPaymentIntent` (keep caller override behind explicit flag); (2) wire the encounter→bill trigger for the standard web encounter-close path (today only mobile/discharge/teleconsult BFF controllers create bills) reusing the existing BFF pattern — do NOT put COSTA calls inside pct-service without coordinator approval; (3) investigate BFF `V17` `costa_bill_id` bridge column and either wire write-back or document it as dead; (4) document (do not "fix") the `mushex CostaEventConsumer.onBillFinalized` double-bill hazard in `docs/registry/mock-and-stub-register.md`.
> Forbidden: mushex payment-rail adapters, Kafka topic/event schemas, GL posting logic, shared-core, migrations other than additive BFF ones, `ui/one-ui-shell/src/lib/routes.ts`, anything in §11 no-touch. Gates: `mvn test` for experience-bff + costing-engine; guard scripts `check-bff-downstream-mappings.sh`, `check-backend-frontend-parity.sh`; evidence per register format. Payment rails stay honest stubs — no fake "paid" states.

### Claude worker B — P2 queue actions (branch `fable/e2e-intelligent-queues`, worktree `../wt-fable-queues`)
> You own Pipeline 2 queue fixes ONLY. Base: anchor @ `d44bb6022`; worktree `../wt-fable-queues`, branch `fable/e2e-intelligent-queues`.
> Scope: (1) Fix the dead triage transition — BFF `QueueController.triageEntry` sends `IN_TRIAGE` which `pct QueueItemStatus` lacks; decide with smallest blast radius (map to an existing status or add enum value + additive migration in pct only) and cover with the Zen repro test; (2) add a queue-item ESCALATE action end-to-end (pct QueueEngine + controller → BFF → `useQueue.ts` + pct-web action), priority-bump semantics, outbox event, audit; (3) do NOT touch shift logic (PCT/TUSO/Vashandi split is serialized R2), TUSO materialisation contract, trust context, or global nav. Gates: `mvn test` pct + bff (incl. `QueueEngineTest`, `QueueControllerTest`), UI type-check/lint/build for touched apps, demo script update.

### Claude worker C — P3 PCT gaps (branch `fable/e2e-pct-care-tracker`, worktree `../wt-fable-pct`)
> You own Pipeline 3 gap closure ONLY. Base: anchor @ `d44bb6022`; worktree `../wt-fable-pct`, branch `fable/e2e-pct-care-tracker`.
> Scope: (1) Add countersignature gate to inpatient discharge-summary finalise (mirror `FormResponseService.countersign` semantics; cadre-aware; auditable); (2) design + implement the minimal prescribing hook from the encounter cockpit: encounter → OROS medication order (reuse the existing form-extraction → `OrosIntegration` route; do NOT invent a new pct↔pharmacy direct integration); (3) write the repo-grounded gap report for ward-round free-text orders and BUTANO PENDING extractions — report, don't refactor.
> Forbidden: changing the OROS `/v1/orders` contract shape (serialized R1 — flag findings to coordinator), CadreEngine family/workflow matrices beyond adding the prescribe wiring, JourneyState enum, shared-core, anything §11. Gates: `mvn test` pct + inpatient + oros touched modules; `FormResponseLifecycleIT`, `DischargeWorkflowTest` green; evidence per register.

### Claude worker E — P6 facility imaging capability (branch `fable/e2e-pacs-imaging`, worktree `../wt-fable-pacs`)
> You own Pipeline 6 capability-mode work ONLY. Base: anchor @ `d44bb6022`; worktree `../wt-fable-pacs`, branch `fable/e2e-pacs-imaging`.
> Scope: (1) Model per-facility imaging capability using the EXISTING generic `tuso FacilityCapabilityEntity` (capability codes e.g. `IMAGING_PACS`, `IMAGING_MODALITY_ONLY`; additive tuso seed migration only — no schema change); (2) make pacs-adapter/OROS MWL routing consult that capability (per-facility provider selection layered over the global default, fail-closed when absent); (3) verify the OROS imaging → PCT `ImagingLinkService` result-return loop with a test using the honest simulator seams; (4) demo script covering central-with-PACS vs district-no-PACS.
> Forbidden: compose/helm Orthanc definitions, enabling MWL/outbound flags by default, BFF PacsController proxy auth, anything §11. Gates: `mvn test` oros + pacs-adapter + tuso touched modules; contract files under `contracts/` unchanged unless additive with coordinator sign-off.

### Cursor Ultra — UI closure (branch `cursor/ui-pipeline-gaps`, worktree `../wt-cursor-ui`)
> UI-only closure across pipelines. Base: anchor @ `d44bb6022`; worktree `../wt-cursor-ui`, branch `cursor/ui-pipeline-gaps`. One commit per page/component.
> Scope: (1) `ui/ehr`: add billing/coverage/payment status panel on the patient encounter view consuming EXISTING BFF `/internal/v1/finance/*` endpoints (read-only; show patientPayable/insurer/subsidy split and payment status; no new BFF endpoints); (2) queue UI: surface pause/resume/transfer/escalate states in `pct-web` queues page consistent with worker B's API (coordinate via register before wiring escalate); (3) Dura UI: add low-stock/stockout visibility to `app/work/dura/page.tsx` using the EXISTING `/stockouts` endpoint via `useDura.ts`.
> Forbidden: `ui/one-ui-shell/src/lib/routes.ts`, `app-registry.ts`, `api-client.ts` core, any BFF/Java changes, package.json/lock, global styles/nav. Gates: `npm run type-check && npm run lint && npm run build` per touched app; screenshots as evidence; no mocked data — if an endpoint is missing, file it in the register instead of stubbing.

### Zen Coder Max — test hardening + evidence (branch `zen/test-hardening-pipelines`, worktree `../wt-zen-tests`)
> Tests and evidence ONLY — no production code changes. Base: anchor @ `d44bb6022`; worktree `../wt-zen-tests`, branch `zen/test-hardening-pipelines`.
> Scope: (1) failing repro test for the BFF→PCT `IN_TRIAGE` 500 (mark expected-failure until worker B's fix, then flip); (2) partial-coverage journey spec: bill → applyCoverage (insurer+subsidy+patient split) → payment intent for shortfall → PAID → receipt, asserting no fake paid states; (3) `inventory-service` tests for `PharmacyConsumer` (store-code resolution incl. non-UUID codes) and `DuraPctController` availability/reserve/consume; (4) run the full Fundo IT suite + `fundo-learning-flow` e2e and produce the P7 evidence pack + demo script under `docs/demo/`; (5) PCT journey spec: outpatient encounter (queue→encounter→form→OROS extraction provenance) and one inpatient procedure→discharge-clearance path.
> Forbidden: editing production classes, migrations, configs; touching `scripts/guard/*`. Gates: all new tests runnable via standard `mvn test` / repo e2e commands; evidence (test names, results, logs) into the register.

### P5 liaison note (to the anchor W0 session, via user)
> Coordinator requests: (a) confirm W0 lease boundary — rtc-gateway, live, khuluma, `libs/session-templates`, `contracts/schemas/session-templates`, LiveKit helm values; (b) verify helm `webhookUrl` port `8196` vs rtc-gateway `server.port: 8195`; (c) confirm whether teleconsult routing 501s (ON_CALL/POOL/NATIONAL_POOL/UNIT) and the waiting-room admission gate are in W1 scope or free for a peripheral worker under lease.

## 8. Required gates per pipeline

All pipelines: conventional commits; `git diff --stat` review against forbidden files; branch up to date with anchor before merge review; product-truth update (`scripts/guard/check-product-truth.sh`) where user-facing; no new mocks in production paths (`check-frontend-mocks-and-stubs.sh`); evidence block in the register.

| Pipeline | Additional gates |
|---|---|
| P1 | `mvn test` costing-engine, mushex, coverage, experience-bff; `ExemptionEngineTest` + new partial-coverage journey spec green; `check-bff-downstream-mappings.sh`; no fake "paid" states |
| P2 | `QueueEngineTest`, `QueueMaterializationServiceTest`, BFF `QueueControllerTest`, e2e `pct-queue-*.spec.ts`; IN_TRIAGE repro flipped to green |
| P3 | pct + inpatient ITs (`FormResponseLifecycleIT`, `PctQueueEncounterIT`, `ProcedureEpisodeIT`, `DischargeSummaryServiceTest`); golden contract ITs; explicit gap report for deferred items |
| P4 | inventory + pharmacy tests incl. new consumer tests; ledger invariants (`LedgerServiceTest`) green |
| P5 | Owned by W0 session's own gates; peripheral work: `TelemedicineOrchestrationServiceTest`, BFF `TeleconsultControllerTest`, UI golden thread |
| P6 | oros `ImagingWorkflowTest`/`DicomMwlTest`, pacs-adapter tests, `imaging-order-result-golden-thread.test.ts`; MWL/outbound flags remain default-OFF |
| P7 | Full Fundo IT suite + `fundo-learning-flow`/`cpd-council-flow` e2e; evidence pack committed |

UI work additionally: `npm run type-check && npm run lint && npm run build` per touched app.

## 9. Integration branch proposal

- Name: `integration/fable-seven-pipeline-delivery-2026-07-04`, cut from anchor tip current at cut time (today: `d44bb6022`).
- Created **only when ≥2 workstream branches pass their gates** — not before (no casual integration branches).
- Merge order (dependency/risk-aware): Zen tests → P4 → P2 → P6 → P1 → P3 → (P5 peripheral only after W0 lease sign-off).
- Coordinator merges; each merge requires the §8 gates re-run on the integration branch; anchor merge is a separate, explicitly-authorized step — the coordinator only produces a merge recommendation.

## 10. Risk register

| # | Risk | Class | Mitigation |
|---|---|---|---|
| 1 | Collision with in-flight W0 anchor session (telemedicine surface) | RED | Lease list in §11; P5 peripheral work blocked until boundary confirmed; liaison note §7 |
| 2 | `mushex CostaEventConsumer` enabled naively → double billing | RED | R3 serialized; document in mock-and-stub register; no one enables listeners |
| 3 | Two OROS `/v1/orders` shapes silently diverge further | RED | R1 serialized; workers forbidden from touching contract shape |
| 4 | Kafka topic names are string-typed event types — rename breaks consumers silently | RED | R5: topic/schema changes coordinator-gated |
| 5 | `IN_TRIAGE` dead transition already 500s in production paths | AMBER | G4 repro + A-scope fix, smallest blast radius |
| 6 | Shift-truth triplication causes conflicting worker "fixes" | AMBER | R2 serialized; workers explicitly forbidden from shift logic |
| 7 | Stale fundo branches merged by mistake | AMBER | §2 verdict: superseded, do-not-merge recorded |
| 8 | UI workers stubbing missing endpoints to "finish" pages | AMBER | Cursor prompt forbids mocks; `check-frontend-mocks-and-stubs.sh` gate |
| 9 | Helm webhook port mismatch 8196 vs 8195 breaks LiveKit ingestion in preview | AMBER | Flag to W0 owner (liaison note), don't touch helm ourselves |
| 10 | Anchor advances while workers are in flight | AMBER | Workers rebase from anchor at gate time; coordinator re-verifies tip before any merge |

## 11. No-touch list (unless explicitly authorized by the user)

1. Direct mutations of `claude/web-session-anchor-nnnkf6`.
2. **W0 lease**: `services/rtc-gateway-service`, `services/live-service`, `services/khuluma-service` consumers, `libs/session-templates`, `contracts/schemas/session-templates/*`, `deploy/helm/impilo-vnext/values-full-preview.yaml`, LiveKit helm templates.
3. Root configs, `package-lock.json`/lock files, turbo.json, root poms.
4. Helm/Docker/compose/ingress definitions.
5. Shared/cross-service DB migrations; any non-additive migration.
6. Tshepo/auth/Keycloak/OPA foundations; `shared-core` `TrustContext`/`TrustContextHolder`.
7. Global navigation/shell: `ui/one-ui-shell/src/lib/routes.ts`, `app-registry.ts`, `api-client.ts` core.
8. Product-truth gate definitions: `scripts/guard/*`, `scripts/completeness/*`.
9. Preview/deployment scripts and full-boot lane state files.
10. Kafka topic names / event schemas (R5).
11. Any worktree in use by another session.

## 12. Immediate next-24-hour execution plan

1. **H0–H1 (done)**: Repo recon, seven-pipeline audit, this board + register committed and pushed on `claude/impilo-vnext-coordination-75fzl0`.
2. **H1–H2**: User reviews board; dispatches the §7 prompts to Cursor Ultra and Zen Coder Max; sends the P5 liaison note to the W0 anchor session. Coordinator (or user) creates the five worker worktrees/branches from anchor tip.
3. **H2–H12**: GREEN wave runs in parallel (G1–G7). Claude workers B (queues) and E (PACS capability) start; A (finance) and C (PCT) start after their Blocker Check reports land in the register.
4. **H12–H18**: First evidence review. Coordinator inspects `git diff --stat` per branch against forbidden lists; updates register statuses; quarantines any branch touching §11.
5. **H18–H24**: If ≥2 branches pass gates, cut `integration/fable-seven-pipeline-delivery-<date>` and rehearse merges in §9 order; publish merge recommendation + updated board.
6. Standing: register updated on every assignment/evidence/merge event; serialized R1–R3 scheduled only after the GREEN wave stabilizes.
