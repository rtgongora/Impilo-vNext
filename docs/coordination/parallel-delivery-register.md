# Impilo vNext — Parallel Delivery Register

Live coordination register for the seven-pipeline parallel delivery effort.
Companion to [`seven-pipeline-parallel-delivery-board.md`](seven-pipeline-parallel-delivery-board.md).
The coordinator updates this file on every assignment, evidence submission, status change, and merge decision.

**Anchor**: `claude/web-session-anchor-nnnkf6` @ `d44bb6022` (verified 2026-07-04; remembered tip `e101a2701` not found — superseded).
**Integration branch**: `integration/fable-seven-pipeline-delivery-2026-07-04` @ `9d180f2f1`, PUSHED 2026-07-04. Cut from anchor tip `f4381a841` (anchor advanced d44bb6022→f4381a841 with W0's W1–W3 waves; zero file overlap with staged branches verified; session-template contract unchanged). Contains: queue stack (fable `1340a9d39` → zen `e0729b16f` → cursor-ui `fe1d9a8fb`, merged via cursor tip) + telemedicine virtual-hospitals (`494cf0663`). Both merges conflict-free; no Flyway version collisions (pct V030/V031, notification V011 unique). **Combined gates on the integrated tree, coordinator-run with real exit codes**: backend `mvn -pl pct,booking,notification,experience-bff -am test` = 0; type-check one-ui-shell = 0, pct-web = 0; combined vitest (queue, scheduling, notifications, telemedicine) = 76/76 across 22 files. **FINAL STATE (2026-07-04): tip `31704a270` after SIX integration rounds — ALL SEVEN PIPELINES staged** (P1 finance, P2 queues ×3, P3 PCT, P4 Dura, P5 telemedicine substrate, P6 PACS, P7 Fundo; 10 workstreams, 9 unconditional + 1 conditional). **ANCHOR MERGED 2026-07-04 (user-authorized)**: `claude/web-session-anchor-nnnkf6` fast-forwarded `f4381a841` → `31704a270` and pushed. The WS-P6-B oros condition was unclearable in this environment (dcm4che repo 403-blocked; not on Maven Central); converted — with explicit user merge authorization — to POST-MERGE FOLLOW-UP: oros-service suite must run green at the next dcm4che-capable build (W0 preview lane / CI). Residual risk bounded: oros diff manually verified compile-safe (symbols `OrderItemRepository.findByOrderId` + `OrderItemEntity.getModality` confirmed present at anchor; no dcm4che API touched; null-tolerant fallbacks).

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
- **Coordinator review 2026-07-04 (post-push)**: PASSED strict RED-lease screening. 26 files, +4434/−0, ALL NEW — zero modifications to existing code; no touches on rtc-gateway, live-service, khuluma, `libs/session-templates`, contracts, helm, nav shell, or api-client. Content honesty verified by inspection: virtual-hospital directory is config-as-data with per-entry `substrateStatus` (never claims runtime capability); session-mode matrix MIRRORS the W0 template contract (does not redefine it) and marks unbacked modes PLANNED with named handoffs HO-2/HO-3; hooks call only endpoints that exist at anchor (W0 `GET /internal/v1/session-templates`, real `TeleconsultController` routing searches); 501 routing kinds documented as fail-closed, not faked; operations page reuses existing RTC-health hooks.
- **Coordinator gates (independent re-run)**: type-check one-ui-shell = 0; vitest over `app/work/telemedicine` + `lib/telemedicine` = 0 (51/51 across 10 files).
- **Caveat (recorded)**: the virtual-hospital directory is registry-plane truth parked in the experience layer — acceptable ONLY as the explicitly-labeled seed spec for backend handoff HO-2. The eventual backend substrate is a new AMBER workstream; the UI file is a spec input, never the system of record.
- **Status**: GATES_PASSED · **Merge readiness**: YES — via integration branch per board policy §9; NOT direct-to-anchor without explicit user instruction

## WS-P1-D — COSTA/MusheX/Coverage journey closure (external session) — GATES_PASSED
- **Branch**: `cursor/e2e-costa-mushex-coverage` @ `8f59c4616` · 11 commits · base `98d43b1cd` (genuine anchor ancestor, pre-W2-close)
- **Coordinator review 2026-07-04**: PASSED. 23 files; mushex touched by a QUARANTINE TEST ONLY (`CostaEventConsumerBillFinalizedQuarantineTest` pins the R3 double-bill hazard without changing serialized production code); no payment rails, Kafka production code, GL, migrations, or forbidden files; `docs/registry/mock-and-stub-register.md` updated. Zero file overlap with the integration branch AND with anchor `98d43b1cd..f4381a841`. Content verified: server-derived shortfall from `patientPayable` with `amount_source` meta + fail-closed `NOTHING_PAYABLE`; read-only tenant-scoped bills-by-encounter; encounter billing status surfaced in clinical EHR context (closes the audit's headline P1 gap); wallet coverage-split fix. Delivers WS-P1-A items 1–2 and WS-P1-B — those drafted workstreams are superseded and closed.
- **Coordinator gates (independent)**: backend `mvn -pl costing-engine,mushex,experience-bff -am test` = 0; type-check one-ui-shell = 0; vitest touched surfaces 15/15 (6 files).
- **Follow-up (hardening, non-blocking)**: `BillService.getBillsForEncounter` tenant filter is in-memory and fail-open on null tenant — push down into the repository query.
- **Status**: GATES_PASSED · merged into `integration/fable-seven-pipeline-delivery-2026-07-04` (round 2, `f8830a8c3`, PUSHED); combined round-2 gate green: backend=0, type-check=0, cross-stream vitest 15/15

## WS-P7-B — Fundo learning defect waves D1–D8 (external session) — GATES_PASSED
- **Branch**: `cursor/e2e-fundo-learning` @ `c1abd6e76` · 13 work commits · properly contains anchor tip `f4381a841` (W3 merged in by the stream)
- **Coordinator review 2026-07-04**: PASSED. 43 files +2100/−80. Lease-clean (no rtc/live/khuluma/session-templates/helm/nav/api-client). Single migration `vashandi V005__training_requirement` verified: additive new table, next-in-sequence, ownership doctrinally correct (Vashandi owns role→course requirement, learning-service owns satisfaction via existing FundoTrainingGateService, Tshepo/OPA owns decision). Zero overlap with integration rounds 1–2. Honesty verified: no-fabricated-KPIs dashboard fix, honest "required" bucket, honest preceptor sign-off, defect register doc. Delivers: CPD candidate accept/reject council surface (varapi), governed training requirements + eligibility gate (vashandi + BFF proxy + UI), placement sign-off with outbox events, live-linkage derivation fix, IT baseline repair.
- **Coordinator gates (independent)**: backend `mvn -pl learning,varapi,vashandi,experience-bff -am test` = 0; type-check one-ui-shell = 0; vitest learning/registry/vashandi surfaces 42/42 (16 files).
- **Status**: GATES_PASSED · merged into integration round 3 (`1c685f89e`, PUSHED); combined round-3 gate green: backend=0, type-check=0

## WS-P4-C — Dura commodities stream (external session) — GATES_PASSED
- **Branch**: `cursor/e2e-dura-commodities` @ `8d165ce64` · 7 commits · base `3411d6ca2` (anchor ancestor); zero overlap with anchor W1–W3
- **Coordinator review 2026-07-04**: PASSED. 10 files +1054/−9, exactly the WS-P4-A/B board scope: low-stock telemetry wired (verified additive-only in `LedgerServiceImpl` — post-movement, outflow-only STOCKOUT_RISK, fail-safe, same transactional outbox; posting semantics untouched), the three missing test suites (`PharmacyConsumerTest`, `DuraPctControllerTest`, `LedgerServiceImplTelemetryTest`), pharmacy-elmis stub recorded in mock-and-stub register, eLMIS/NatPharm sync-status BFF proxy, stockouts/sync/balance/ledger on the Dura ops page. PharmacyConsumer touched by TEST only; no migrations; no connector behavior changes. WS-P4-A/B drafts superseded and closed.
- **Coordinator gates (independent)**: backend `mvn -pl inventory-service,experience-bff -am test` = 0; type-check one-ui-shell = 0; vitest dura/hooks surfaces 97/97 (14 files).
- **Integration**: merged as round 4 (`77d55abe6`, PUSHED); union conflict in `mock-and-stub-register.md` resolved keeping all entries; combined round-4 gate green: backend=0, type-check=0
- **Status**: GATES_PASSED

## WS-P3-B — PCT care-tracker gaps (external session) — GATES_PASSED
- **Branch**: `cursor/e2e-pct-care-tracker` @ `97169ab03` · 13 commits · base `98d43b1cd` (stale but benign: zero overlap with anchor W2/W3 advance, verified)
- **Coordinator review 2026-07-04**: PASSED. 27 files. Serialized items respected: R1 pinned by `OrosIntegrationContractTest` (documents the pct-vs-inpatient OROS payload mismatch, changes neither contract); prescribing routed through the EXISTING form-extraction→OROS seam via seeded `MEDICATION_REQUEST` form (countersign-gated); CadreEngine matrices + `JourneyState` untouched. Discharge-summary countersign gate: inpatient `V019` additive/next-in-sequence; different-actor rule; single-shot signature invalidated by draft edits; policy-configurable requirement (no hardcoded cadre split). UI: countersign actions, medications panel, extraction-provenance/routing status, discharge draft editor. Closes WS-P3-A scope items 1–2 (drafted workstream superseded).
- **Coordinator gates (independent)**: backend `mvn -pl pct,inpatient,experience-bff -am test` = 0; type-check = 0; vitest encounter/discharge surfaces 36/36 (10 files).
- **Integration**: merged as round 5; the two anticipated collisions (`PctServiceClient.java` escalation-vs-provenance methods, EHR encounter page billing-vs-forms panels) auto-resolved cleanly by git (different regions); combined round-5 gate green: backend=0, type-check=0, collision-file vitest 39/39 (13 files); round 5 PUSHED @ `0054a7ced`
- **Status**: GATES_PASSED

## WS-P6-B — PACS imaging integration (external session) — GATES_PASSED (CONDITIONAL)
- **Branch**: `cursor/e2e-pacs-imaging-integration` @ `5786a8f01` · 9 commits · base `d44bb6022` (anchor ancestor); zero overlap with integration rounds 1–5 AND with anchor W1–W3
- **Coordinator review 2026-07-04**: PASSED. 36 files. Closes the audit's P6 headline gap: per-facility `ImagingDeploymentMode` (PACS/VNA, gateway, machines-only, manual, digitisation-bridge, referral-only, offline-sync, none) + modality/machine registry in pacs-adapter (V006/V007 additive, sequential); auditable study-reconciliation exception queue (no silent patient attachment); real modality carried into MWL + FHIR ImagingStudy; order-context viewer-launch fix; honest doc correction (critical-result notify is DEFAULT-OFF, not missing). No config/flag flips — MWL/outbound stay default-OFF. **Ownership note**: facility imaging capability lives in pacs-adapter (imaging-deployment config), NOT TUSO — deliberate; revisit if TUSO claims general facility capability truth.
- **Coordinator gates (independent)**: pacs-adapter + experience-bff `-am test` = 0; type-check = 0; imaging vitest 3/3.
- **⚠ CONDITION (environment limitation)**: oros-service CANNOT be built/tested in the coordination container — `maven.dcm4che.org` is 403-blocked by the environment network policy (only `.lastUpdated` failure markers in the local repo). The branch's oros changes (modality carry in `OrderController`/`ImagingWorkflowService` + 2 test classes) are small/additive but UNVERIFIED here. **Anchor merge of any tree containing this branch requires a green oros-service suite in an environment with dcm4che access (W0 preview lane or CI).** Fix available: allowlist `maven.dcm4che.org` in the environment network policy, then coordinator re-runs and clears this condition.
- **Integration**: merged as round 6 (`31704a270`), conflict-free, no migration dupes; combined round-6 gate green: backend=0, type-check=0; round 6 PUSHED @ `31704a270`
- **Status**: GATES_PASSED (CONDITIONAL on oros verification)

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

---

# IATG PROGRAM — Identity, Access and Trust Governance (Wave 1, opened 2026-07-05)

**Plan**: user-approved (plan mode). Doctrine: Platform Origin → Country Operation → National Admins; Health-ID-first/Provider-ID-second; Channels A/B/C; honest registries; granular trust; adjudication. **Global invariant: `services/tshepo-service/**` untouched program-wide** (ext_authz frozen; pre-existing fail-open defects flagged for a future RED wave).
**Base**: anchor `74c22f480` (W0 advanced past our seven-pipeline merge; IATG surfaces verified untouched by the advance).
**Fixed decisions**: org-registry port **8153**; Flyway pre-assignments — governance V005/V006 (A), V007 (D), V008 (G) ⇒ **governance merge order A→D→G mandatory**; varapi V017/V018 (C); tuso V016 (E); tshepo-authz V031 (A); workflow V003 (G); org-registry V001–V003 (B). BFF single-owner file map in `docs/registry/iatg-wave1-leases.md` (WS-H deliverable).
**Schedule**: Batch 0 WS-A (RED, merges first) ∥ Batch 1 WS-B/C/E/H (parallel) → Batch 2 WS-D/F (after C) → Batch 3 WS-G (after D).

## IATG-WS-A — Platform Origin + Country Operation + two-person (RED serialized-merge)
- Branch `fable/iatg-a-platform-origin` · worktree `/home/user/wt-iatg-a` · agent running 2026-07-05 · Status: ASSIGNED

## IATG-WS-B — organization-registry-service (new, 8153)
- Branch `fable/iatg-b-org-registry` · worktree `/home/user/wt-iatg-b` · agent running · Status: ASSIGNED

## IATG-WS-C — varapi trust + channel typing
- Branch `fable/iatg-c-varapi-trust` @ `e05e607c3` PUSHED · 8 commits · Status: GATES_PASSED (coordinator gate EXIT=0 at HEAD, 156 tests; lease-clean; trust-API contract frozen for D/F; council numbers proven absent from responses)

## IATG-WS-E — tuso facility source legitimacy
- Branch `fable/iatg-e-tuso-legitimacy` @ `03bb2fba8` PUSHED · 4 commits · Status: GATES_PASSED (coordinator gate EXIT=0; lease-clean; GOVERNMENT_OPERATIONAL_EXCEPTION requires reason; doctrine example encoded as test; status-history append skipped — NOT NULL constraint would fabricate regulatory entries, previous values ride outbox events instead)

## IATG-WS-H — doctrine + lease docs (GREEN)
- Branch `fable/iatg-h-doctrine` @ `6d3f7702e` PUSHED · 6 commits · Status: GATES_PASSED (docs-only diff verified; canonical closing doctrine paragraph restored by coordinator)

## IATG-WS-D — Channel B EC matching + trust composition
- Branch `fable/iatg-d-channel-b` · worktree `/home/user/wt-iatg-d` · agent running 2026-07-05 · Status: ASSIGNED

## IATG-WS-F — provider self-service claim/recovery
- Branch `fable/iatg-f-provider-claim` · worktree `/home/user/wt-iatg-f` · agent running 2026-07-05 · Status: ASSIGNED

## IATG-WS-G — adjudication
- PENDING dispatch (merges after WS-D; buildable earlier)

## WS-M — Mobile Runtime Truth Wave (user-approved plan, parallel to IATG)
- Branch `fable/mobile-runtime-truth` · worktree `/home/user/wt-mobile-truth` · agent running 2026-07-05 · Status: ASSIGNED
- Scope: build truth (SDK attempt in-container), preview connectivity fixes (network security config for http://41.57.127.235, scheme mismatch, eas alignment), credential-parameterized Maestro login/journey flows, CI gate + APK artifacts, scripts/mobile/runtime-truth.sh VM runbook, honest truth report (status ceiling BUILDS_ONLY until VM evidence). Leases: apps/mobile, docs/mobile, scripts/mobile, ci.yml mobile jobs, parity matrix. Runtime proof executes on the user's VM against the preview VM.
