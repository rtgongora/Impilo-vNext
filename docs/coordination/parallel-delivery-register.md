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

## WS-P2-A — Queue/Booking journey (EXPANDED per user 2026-07-04: Booking → Appointment → Check-in → Queue → Care Start → Updates)
- **Priority**: P1 · **Risk class**: AMBER
- **Assigned**: Claude Worker B = the Fable session itself (user decision: Worker B is primary implementer)
- **Base**: anchor @ `d44bb6022` · **Branch**: `fable/e2e-queue-booking-appointment-coordination` · **Worktree**: `/home/user/wt-fable-queue-booking`
- **Owned**: pct queue/journey lifecycle, booking `AppointmentService.checkIn`, BFF Queue/AppointmentCheckIn controllers+clients, notification-service queue consumer + `V011` templates, additive pct migrations `V030`/`V031`
- **Forbidden (respected)**: shift logic (R2), TUSO materialisation contract, trust context, global nav, W0 telemedicine core, Kafka topic renames
- **Delivered** (7 commits, `79e2fdfbd..769296629`):
  - `IN_TRIAGE` 500 fixed; transition outbox events (no silent state changes); transfer CPID NOT-NULL bug fixed
  - BFF priority normalized to PCT 1–5 scale; IN_TRIAGE contract test
  - Queue-level escalation end-to-end (V030, reason mandatory, optional transfer, Tshepo audit, `QUEUE_ITEM_ESCALATED`)
  - Journey appointment provenance (V031); **repaired the dead appointment check-in → queue chain** (booking read wrong JSON key so no check-in ever enqueued; TUSO numeric facility id vs PCT UUID fixed via trust-header resolution; dead encounter-at-check-in removed; `CHECKED_IN_NO_QUEUE`/`queue_linked` explicit states)
  - Queue lifecycle patient notifications: `pct.queue.item.updated` → notification-service → Mvumo-gated neutral IN_APP messages (`QUEUE_CITIZEN_*`)
  - `docs/architecture/queue-management-journey.md` (state model, repairs, gap register, demo script)
- **Evidence**: `mvn test` green for pct-service, booking-service, notification-service AND experience-bff (full modules). Coordinator `git diff --stat` review passed: 24 files, 1150+/36−, all within lease (pct, booking, notification, BFF, docs; additive migrations V030/V031/V011 only; zero W0/shared-core/config/nav touches)
- **Status**: GATES_PASSED · **Merge readiness**: YES — recommend as first branch into the integration branch once a second workstream passes gates · **Blockers**: none

## WS-P2-B — Queue regression hardening (Zen Coder Max)
- **Priority**: P1 · **Risk class**: GREEN (tests only)
- **Assigned**: Zen worker agent (run by Fable session per user instruction) · **Branch**: `zen/test-hardening-pipelines` · **Worktree**: `/home/user/wt-zen-tests` · **Base**: `769296629` (WS-P2-A tip)
- **Owned**: test files only in pct-service, booking-service, notification-service (+BFF tests if needed)
- **Forbidden**: production code, migrations, configs, scripts/guard
- **Delivered** (5 commits `04629eb5d..e0729b16f`, rebased on WS-P2-A tip `1340a9d39`): `AppointmentProvenanceJourneyIT` (appointment_id persisted + outboxed + queueable), no-show/reschedule coverage (booking + pct outbox contract), queue→encounter handoff IT with full outbox-chain assertions, `NotifyServicePreferenceGateTest` (Mvumo deny → CANCELLED + preference_blocked event; fail-open on empty), consumer tenant-context + malformed-payload tests, `VirtualQueueGapIT` (teleconsult creates zero queue items — deliberate tripwire for when virtual queues land)
- **Evidence**: coordinator-verified `mvn -pl pct-service,booking-service,notification-service test` → `MVN_EXIT=0` (pct 143, notification 53, booking 21 tests); diff review: 7 files, tests only
- **Status**: GATES_PASSED · **Merge readiness**: YES (stacks cleanly on WS-P2-A) · **Blockers**: none

## WS-P2-C — Queue/check-in/inbox UI surfacing (Cursor Ultra)
- **Priority**: P1 · **Risk class**: GREEN/AMBER (UI only)
- **Assigned**: Cursor worker agent (run by Fable session per user instruction) · **Branch**: `cursor/ui-pipeline-gaps` · **Worktree**: `/home/user/wt-cursor-ui` · **Base**: `769296629` (WS-P2-A tip)
- **Owned**: `ui/one-ui-shell` useQueue/scheduling surfaces, `ui/pct-web` queue pages, citizen inbox rendering
- **Forbidden**: routes.ts, app-registry.ts, api-client core, package locks, Java, mocks for missing endpoints
- **Delivered** (6 commits, rebased on WS-P2-A tip `1340a9d39`, head `fe1d9a8fb`): escalate action with mandatory-reason dialog + escalated badges on one-ui-shell `/queue` board and pct-web `(ops)/queues` (target-queue select on pct-web); honest check-in states (`queue_linked` banners, persistent `CHECKED_IN_NO_QUEUE` badge, no fabricated tokens anywhere); citizen inbox repaired — it was shape-broken (expected `attributes` envelope, BFF returns flat `NotificationResponse`) so NO notifications ever rendered; rewired to existing hooks + template-key labels for all QUEUE_CITIZEN_*/APPOINTMENT_CITIZEN_* keys, no fabricated body text
- **Evidence**: worker gates green (type-check/lint/build both apps, 54/54 vitest); coordinator re-ran post-rebase: type-check shell=0, pct-web=0, touched-page vitest 5/5; diff review: 12 UI files, zero forbidden touches, lockfiles unchanged
- **Status**: GATES_PASSED · **Merge readiness**: YES (stacks on WS-P2-A) · **Blockers**: none
- **Follow-ups surfaced for backend owners**: (a) notifications list API exposes `templateKey` only — no rendered subject/body/vars, so inbox rows can't show token numbers until the API returns rendered content; (b) pre-existing shape seam: BFF `GET /internal/v1/queue/entries?facility_id=` without `queue_type` returns queue definitions (flat) where the shell expects entry rows — pre-dates this work, defensively handled in new code only

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

## WS-P5-B — Telemedicine virtual hospitals (external session) — AWAITING ARTIFACTS
- **Claimed**: branch `cursor/e2e-telemedicine-virtual-hospitals`, worktree `/opt/impilo/repos/wt-cursor-telemedicine-virtual-hospitals`, base `d44bb6022`, HEAD `494cf0663`, 6 local commits, unpushed
- **Coordinator finding 2026-07-04**: NOT INSPECTABLE — worktree path absent from this environment, branch on neither local nor origin, commit `494cf0663` not in the object database. The work exists only in the originating session's machine.
- **Decision**: push authorization is NOT sight-unseen. Pushing a branch is safe (a push is not a merge) — the owning session must `git push -u origin cursor/e2e-telemedicine-virtual-hospitals` (or ship a `git bundle`) so the coordinator can review. Review will be strict RED-lease screening: any touch on `services/rtc-gateway-service`, `services/live-service`, khuluma RTC consumers, `libs/session-templates`, `contracts/schemas/session-templates`, LiveKit helm/preview values, or the W0 session-mode contract ⇒ QUARANTINE and safe-part extraction. Virtual-hospital routing (the ON_CALL/POOL/NATIONAL_POOL/UNIT 501s) was explicitly pending W0 boundary confirmation — that confirmation still has not happened.
- **Status**: BLOCKED (awaiting push/bundle) · **Merge readiness**: NO

## Unregistered branches detected on origin (2026-07-04 fetch)
- `cursor/e2e-pacs-imaging-integration` — based on anchor `d44bb6022` ✓; 35 files +3398 (imaging capability/modality registry/reconciliation queue UI + viewer fix); quick forbidden-file grep clean. Not registered before pushing — needs a workstream entry, owner, and gate evidence before merge consideration.
- `cursor/e2e-pct-care-tracker` — **stale base**: merge-base with anchor is `98d43b1cd`, not `d44bb6022`; 20 files +1217 (countersign surfacing, medications panel, gap register doc). Needs rebase onto current anchor + registration + gates before merge consideration.

## Serialized queue (coordinator-gated, one at a time; not yet scheduled)
| ID | Item | Class |
|---|---|---|
| R1 | OROS `/v1/orders` contract unification (`pct OrosIntegration.java:54` vs `inpatient OrosOrderClient.java:50-56`) | RED |
| R2 | Shift-truth consolidation (PCT `WorkspaceSessionService` / BFF→TUSO `ShiftController` / Vashandi `RosterService`) | RED |
| R3 | `mushex CostaEventConsumer.onBillFinalized` double-bill hazard remediation (`CostaEventConsumer.java:60-68`) | RED |

## Decisions log
- 2026-07-04 · **Gate-integrity incident (resolved)**: Worker B's original full-suite gates piped Maven through `tail`, reporting tail's exit code — two fixture regressions (callNext CPID, checkIn startTime) shipped red. Caught by the Zen worker via pristine-HEAD verification; fixture repair cherry-picked onto WS-P2-A (`1340a9d39`), all gates re-run with true `MVN_EXIT` capture: pct/booking/notification/experience-bff all 0. New rule: no piped exit codes in gate commands; coordinator re-runs every worker's gate before push.
- 2026-07-04 · User expanded P2 into the full Booking→Appointment→Check-in→Queue→Care-Start→Updates journey and made this session (Worker B) primary implementer; Fable retains coordination.
- 2026-07-04 · Worker B found and repaired three silently-dead production paths: appointment check-in never enqueued (wrong JSON key + facility id type mismatch), queue transfer violated the CPID NOT NULL constraint, and queue status transitions emitted no events. Recorded here because they invalidate prior assumptions that "physical queue is fully wired" — it is now.
- 2026-07-04 · Queue notification channel decision: IN_APP inbox only via notification-service (Mvumo-gated); Khuluma confirmed to be a conversation hub, not a notifier; `inpatient.discharge.followup_requested` remains an unconsumed contract stub (gap noted for P3).
- 2026-07-04 · Virtual queue engine remains MISSING (routing metadata ≠ queue engine); queue-side handoff contract requested from W0 owner documented in `docs/architecture/queue-management-journey.md`.
- 2026-07-04 · Anchor tip verified `d44bb6022`; remembered `e101a2701` nonexistent — superseded.
- 2026-07-04 · Fundo remote branches declared superseded by HEAD native LMS; do-not-merge.
- 2026-07-04 · P5 telemedicine core declared leased to the in-flight W0 anchor session; peripheral work blocked pending boundary confirmation.
- 2026-07-04 · Integration branch deferred until ≥2 workstreams pass gates (board §9).
