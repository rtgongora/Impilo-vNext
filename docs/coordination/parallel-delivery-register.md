# Impilo vNext — Parallel Delivery Register

Live coordination register for the seven-pipeline parallel delivery effort.
Companion to [`seven-pipeline-parallel-delivery-board.md`](seven-pipeline-parallel-delivery-board.md).
The coordinator updates this file on every assignment, evidence submission, status change, and merge decision.

**Anchor**: `claude/web-session-anchor-nnnkf6` @ `d44bb6022` (verified 2026-07-04; remembered tip `e101a2701` not found — superseded).
**Integration branch**: not yet cut. Planned: `integration/fable-seven-pipeline-delivery-2026-07-04` (cut only when ≥2 workstreams pass gates).

Status vocabulary: `PROPOSED` → `ASSIGNED` → `IN_PROGRESS` → `EVIDENCE_SUBMITTED` → `GATES_PASSED` → `MERGED_TO_INTEGRATION` → `DONE` | `BLOCKED` | `QUARANTINED`

---

## WS-P1-A — Finance journey closure (COSTA/MusheX/Coverage)
- **Priority**: P1 · **Risk class**: AMBER
- **Assigned agent/session**: Claude worker A (not yet dispatched)
- **Base branch**: `claude/web-session-anchor-nnnkf6` @ `d44bb6022`
- **Working branch**: `fable/e2e-costa-mushex-coverage` · **Worktree**: `../wt-fable-costa-coverage`
- **Owned files/dirs**: `services/experience-bff/**/Finance*`, `services/experience-bff/**/MobileEncounter*`, `services/costing-engine-service` (read + tests), additive BFF migrations only
- **Forbidden**: mushex payment-rail adapters, Kafka topics/event schemas, GL logic, `shared-core`, `ui/one-ui-shell/src/lib/routes.ts`, board §11 list
- **Expected deliverable**: server-derived shortfall intent amount; web encounter-close→bill trigger; `costa_bill_id` bridge verdict; double-bill hazard documented in `docs/registry/mock-and-stub-register.md`
- **Required gates**: `mvn test` (costing-engine, mushex, coverage, experience-bff); `check-bff-downstream-mappings.sh`; no fake paid states
- **Current status**: PROPOSED · **Last evidence**: — · **Merge readiness**: NO · **Blockers**: dispatch pending

## WS-P1-B — EHR billing visibility panel
- **Priority**: P1 · **Risk class**: GREEN
- **Assigned**: Cursor Ultra (not yet dispatched)
- **Base**: anchor @ `d44bb6022` · **Branch**: `cursor/ui-pipeline-gaps` · **Worktree**: `../wt-cursor-ui`
- **Owned**: `ui/ehr/**` (new billing panel only)
- **Forbidden**: BFF/Java, `one-ui-shell` route registry, api-client core, package locks, mocks
- **Deliverable**: read-only billing/coverage/payment panel on encounter view via existing `/internal/v1/finance/*`
- **Gates**: type-check/lint/build for `ui/ehr`; screenshots; `check-frontend-mocks-and-stubs.sh`
- **Status**: PROPOSED · **Evidence**: — · **Merge readiness**: NO · **Blockers**: none

## WS-P1-C — Partial-coverage journey spec
- **Priority**: P1 · **Risk class**: GREEN (tests only)
- **Assigned**: Zen Coder Max (not yet dispatched)
- **Base**: anchor @ `d44bb6022` · **Branch**: `zen/test-hardening-pipelines` · **Worktree**: `../wt-zen-tests`
- **Owned**: new test files only
- **Deliverable**: bill→applyCoverage(split)→shortfall intent→PAID→receipt spec
- **Status**: PROPOSED · **Blockers**: none

## WS-P2-A — Queue triage-transition fix + escalate action
- **Priority**: P1 · **Risk class**: AMBER
- **Assigned**: Claude worker B (not yet dispatched)
- **Base**: anchor @ `d44bb6022` · **Branch**: `fable/e2e-intelligent-queues` · **Worktree**: `../wt-fable-queues`
- **Owned**: `services/pct-service/**/Queue*`, `domain/QueueItemStatus.java`, `services/experience-bff/**/QueueController.java`, `ui/one-ui-shell/src/hooks/queries/useQueue.ts`, `ui/pct-web` queue pages; additive pct migration if needed
- **Forbidden**: shift logic (PCT/TUSO/Vashandi — serialized R2), `TusoIntegration` materialisation contract, trust context, global nav
- **Deliverable**: dead `IN_TRIAGE` transition fixed (BFF `QueueController.java:294-302` vs enum); queue-item ESCALATE end-to-end with outbox event + audit
- **Gates**: `QueueEngineTest`, BFF `QueueControllerTest`, e2e `pct-queue-*.spec.ts`, repro test flipped green
- **Status**: PROPOSED · **Blockers**: none

## WS-P2-B — IN_TRIAGE repro test
- **Priority**: P1 · **Risk class**: GREEN (tests only)
- **Assigned**: Zen Coder Max · **Branch**: `zen/test-hardening-pipelines`
- **Deliverable**: failing repro of BFF→PCT `IN_TRIAGE` 500
- **Status**: PROPOSED · **Blockers**: none

## WS-P3-A — PCT gaps: discharge countersign + prescribing hook
- **Priority**: P1 · **Risk class**: AMBER
- **Assigned**: Claude worker C (not yet dispatched)
- **Base**: anchor @ `d44bb6022` · **Branch**: `fable/e2e-pct-care-tracker` · **Worktree**: `../wt-fable-pct`
- **Owned**: `services/inpatient-service/**/DischargeSummary*`, `services/pct-service` (prescribe wiring via existing form-extraction→OROS route), gap report doc
- **Forbidden**: OROS `/v1/orders` contract shape (serialized R1), CadreEngine matrices beyond prescribe wiring, `JourneyState` enum, shared-core
- **Deliverable**: countersign gate on discharge-summary finalise; minimal encounter→OROS medication order; ward-round/BUTANO gap report
- **Gates**: pct + inpatient ITs green (`FormResponseLifecycleIT`, `DischargeSummaryServiceTest`, `ProcedureEpisodeIT`); golden contract ITs
- **Status**: PROPOSED · **Blockers**: Blocker Check report required before coding

## WS-P4-A — Dura low-stock telemetry + pharmacy-elmis verdict
- **Priority**: P2 · **Risk class**: AMBER (small)
- **Assigned**: Claude worker D (not yet dispatched)
- **Base**: anchor @ `d44bb6022` · **Branch**: `fable/e2e-dura-commodities` · **Worktree**: `../wt-fable-dura`
- **Owned**: `services/inventory-service/**/LedgerServiceImpl.java` (wire `publishStockLevelTelemetrySnapshot` call sites only), `services/pharmacy-elmis-adapter` (document stub honestly in mock-and-stub register)
- **Forbidden**: ledger posting semantics, `PharmacyConsumer` contract, Dura migrations, eLMIS connector behavior changes
- **Deliverable**: low-stock telemetry actually emitted; pharmacy-elmis `DispenseSyncService` stub recorded as deferred seam
- **Gates**: `LedgerServiceTest` + new telemetry test
- **Status**: PROPOSED · **Blockers**: none

## WS-P4-B — Dura consumer tests + stockout UI
- **Priority**: P2 · **Risk class**: GREEN
- **Assigned**: Zen (tests: `PharmacyConsumer`, `DuraPctController`) + Cursor (stockout visibility on `app/work/dura/page.tsx` via existing `/stockouts`)
- **Status**: PROPOSED · **Blockers**: none

## WS-P5-A — Telemedicine (W0 anchor session lease)
- **Priority**: P1 · **Risk class**: RED (leased)
- **Assigned**: existing anchor W0 session (external to this coordination)
- **Owned (leased)**: `services/rtc-gateway-service`, `services/live-service`, `services/khuluma-service` consumers, `libs/session-templates`, `contracts/schemas/session-templates`, LiveKit helm/preview values
- **Coordinator actions**: liaison note sent (board §7): confirm lease boundary; flag helm webhook port `8196` vs `server.port 8195`; ask whether routing 501s (ON_CALL/POOL/NATIONAL_POOL/UNIT) + waiting-room admission gate are W1 or delegable
- **Status**: BLOCKED (awaiting W0 owner response) · **Merge readiness**: N/A

## WS-P6-A — Facility imaging capability modes
- **Priority**: P2 · **Risk class**: AMBER
- **Assigned**: Claude worker E (not yet dispatched)
- **Base**: anchor @ `d44bb6022` · **Branch**: `fable/e2e-pacs-imaging` · **Worktree**: `../wt-fable-pacs`
- **Owned**: `services/tuso-service` additive capability seeds, `services/oros-service/**/integration/dicom/*` per-facility routing layer, `services/pacs-adapter-service` provider selection, imaging demo script
- **Forbidden**: compose/helm Orthanc, enabling MWL/outbound flags by default, BFF PacsController auth, `contracts/` non-additive changes
- **Deliverable**: per-facility `IMAGING_PACS` vs `IMAGING_MODALITY_ONLY` capability consulted by routing; result-return loop test; central-vs-district demo script
- **Gates**: oros + pacs-adapter + tuso tests; `imaging-order-result-golden-thread.test.ts`
- **Status**: PROPOSED · **Blockers**: none

## WS-P7-A — Fundo evidence pack + demo script
- **Priority**: P2 · **Risk class**: GREEN
- **Assigned**: Zen Coder Max
- **Owned**: `docs/demo/**` (new), evidence artifacts; no production code
- **Deliverable**: full Fundo IT suite + `fundo-learning-flow`/`cpd-council-flow` e2e run, results recorded; demo script for catalog→enrol→learn→complete→CPD
- **Note**: remote fundo branches (`fix-impilo-fundo`, `impilo-fundo-upgrade`, `split/pr3-fundo-ui`) are superseded by HEAD's native LMS — **do not merge**
- **Status**: PROPOSED · **Blockers**: none

---

## Serialized queue (coordinator-gated, one at a time; not yet scheduled)
| ID | Item | Class |
|---|---|---|
| R1 | OROS `/v1/orders` contract unification (`pct OrosIntegration.java:54` vs `inpatient OrosOrderClient.java:50-56`) | RED |
| R2 | Shift-truth consolidation (PCT `WorkspaceSessionService` / BFF→TUSO `ShiftController` / Vashandi `RosterService`) | RED |
| R3 | `mushex CostaEventConsumer.onBillFinalized` double-bill hazard remediation (`CostaEventConsumer.java:60-68`) | RED |

## Decisions log
- 2026-07-04 · Anchor tip verified `d44bb6022`; remembered `e101a2701` nonexistent — superseded.
- 2026-07-04 · Fundo remote branches declared superseded by HEAD native LMS; do-not-merge.
- 2026-07-04 · P5 telemedicine core declared leased to the in-flight W0 anchor session; peripheral work blocked pending boundary confirmation.
- 2026-07-04 · Integration branch deferred until ≥2 workstreams pass gates (board §9).
