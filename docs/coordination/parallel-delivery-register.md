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
- **Status**: ✅ **CLOSED (2026-07-06)** — condition cleared. The canonical VM gate (`scripts/test/run-backend-checks.sh` on the preview VM `41.57.127.235`, repo tip `619399104`) ran the `oros-imaging-tests` gate (`mvn test -pl oros-service -am`) and reported **`PASS oros-imaging-tests`** (log `/tmp/impilo-preview-gates/oros-imaging-tests.log`). dcm4che resolved from `maven.dcm4che.org` (reachable on the VM), the WS-P6-B modality changes compiled and their suite ran green. The CONDITIONAL merge risk is retired. oros is now permanently in the VM gate (Phase 0) so this cannot silently regress.

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
- Branch `fable/iatg-a-platform-origin` @ `80aebbf6f` PUSHED · 8 commits, 24 files +2162/−2 · Status: GATES_PASSED (coordinator gate EXIT=0 across governance+authz+BFF; lease verified: zero tshepo-service/bootstrap-schema/admingovernance touches; N-of-M enforced with legacy path preserved; Keycloak roles added with NO bypass linkage). WATCH ITEM for integration: governance TrustContextFilter extended to /internal/v1/* — WS-D's match endpoint lands under it.

## IATG-WS-B — organization-registry-service (new, 8153)
- Branch `fable/iatg-b-org-registry` @ `091e4c952` PUSHED · 7 commits · Status: GATES_PASSED (coordinator gate EXIT=0 org-registry+BFF; module packaging proven; BFF exactly the 3 permitted files; dual-SoR-with-mirror adoption doc + registry/port/SoR entries delivered)

## IATG-WS-C — varapi trust + channel typing
- Branch `fable/iatg-c-varapi-trust` @ `e05e607c3` PUSHED · 8 commits · Status: GATES_PASSED (coordinator gate EXIT=0 at HEAD, 156 tests; lease-clean; trust-API contract frozen for D/F; council numbers proven absent from responses)

## IATG-WS-E — tuso facility source legitimacy
- Branch `fable/iatg-e-tuso-legitimacy` @ `03bb2fba8` PUSHED · 4 commits · Status: GATES_PASSED (coordinator gate EXIT=0; lease-clean; GOVERNMENT_OPERATIONAL_EXCEPTION requires reason; doctrine example encoded as test; status-history append skipped — NOT NULL constraint would fabricate regulatory entries, previous values ride outbox events instead)

## IATG-WS-H — doctrine + lease docs (GREEN)
- Branch `fable/iatg-h-doctrine` @ `6d3f7702e` PUSHED · 6 commits · Status: GATES_PASSED (docs-only diff verified; canonical closing doctrine paragraph restored by coordinator)

## IATG-WS-D — Channel B EC matching + trust composition
- Branch `fable/iatg-d-channel-b` @ `4a170266b` PUSHED · 7 commits, 16 files · Status: GATES_PASSED (coordinator gate EXIT=0: governance 24 + BFF 761 tests; lease clean; EC numbers masked everywhere with serialization proofs; CONFLICT never leaks other person's Health ID; four-block trust profile with per-block honest degradation; no provider creation in Wave 1 — providerCreationRequired flag)

## IATG-WS-F — provider self-service claim/recovery
- Branch `fable/iatg-f-provider-claim` @ `70980a4d7` PUSHED · 5 commits · Status: GATES_PASSED (coordinator gates: mvn=0, type-check=0, vitest=0; varapi adds-only verified by name-status; recover-not-reissue with same-providerPublicId integrity guard; EC evidence flag-gated 501 until WS-D wiring enabled; honest 501s for document/org-invitation)

## IATG INTEGRATION — COMPLETE 2026-07-05
- Branch `integration/fable-iatg-wave1-2026-07-05` @ `fdf3b1f46` PUSHED. All NINE branches merged conflict-free in mandated order (A→B→C→E→H→D→F→G→M) off anchor `74c22f480`.
- **Program invariants verified on merged tree**: `services/tshepo-service/**` touches = 0 across 171 files (+14,893/−37); governance Flyway perfect sequence V005→V008; zero migration duplicates in any touched service; WS-A TrustContextFilter × WS-D/WS-G endpoint interaction proven by green governance suite on the merged tree.
- **Combined gates (coordinator-run, real exit codes)**: backend `mvn -pl governance,varapi,tuso,workflow,org-registry,tshepo-authz,experience-bff -am test` = 0 with zero [ERROR]; one-ui-shell type-check = 0; citizen/auth vitest = 0; mobile typecheck = 0.
- **ANCHOR MERGED 2026-07-05 (user-authorized)**: `claude/web-session-anchor-nnnkf6` `c2ab4cbba` → `ca871d21d` (real merge — anchor had advanced with W0 W6 live-event work). One file overlap (`apps/mobile/citizen-app/app.json`: WS-M scheme fix vs anchor expo-video plugin) auto-resolved by 3-way merge, JSON validity re-verified. Post-merge gates on the merged anchor tree, coordinator-run real exit codes: backend (experience-bff+governance+varapi+org-registry) = 0, one-ui-shell type-check = 0, mobile typecheck = 0, app.json valid = 0. **READY** Contents: complete IATG Wave 1 (Platform Origin→Country Operation→National Admins with two-person enforcement; organization-registry-service:8153; provider trust/channels/registry-status; Channel-B EC matching + four-block trust profile; tuso per-source legitimacy; provider claim/recovery journey; adjudication definitions + append-only decision records; doctrine + leases + demo script) + WS-M mobile truth fixes (client-id mismatch, network security config, scheme fix, Maestro flows, CI gate, VM runbook — statuses honestly NOT_PROVEN pending VM evidence).

## IATG-WS-G — adjudication
- Branch `fable/iatg-g-adjudication` @ `ba43c320e` PUSHED · 4 commits · Status: GATES_PASSED (coordinator gate EXIT=0; engine untouched — SQL seed + tests only; append-only enforced at trigger/service/test layers; controller tolerates both TrustContextFilter registrations). MERGE CONSTRAINT: after WS-D (governance V008 > V007).

## WS-M — Mobile Runtime Truth Wave — GATES_PASSED (in-container phase), runtime pending VM
- Branch `fable/mobile-runtime-truth` @ `b744f4cc` PUSHED · 9 commits · coordinator-verified: lease clean (mobile-scoped incl. ci.yml mobile job only), typecheck=0, mobile tests=0, yaml=0
- **Truth found**: (1) Keycloak CLIENT-ID MISMATCH — apps sent citizen-app/provider-app but preview realm defines impilo-mobile-citizen/impilo-mobile-provider ⇒ every preview mobile login would have failed; fixed in eas preview profile. (2) Release builds would block the plain-HTTP preview (no network security config) — fixed with bounded cleartext exception (41.57.127.235 + 10.0.2.2 only; prod stays https-only). (3) app.json deep-link schemes disagreed with app.config.ts — fixed. (4) mobile typecheck was RED at anchor (8 pre-existing TS errors) — fixed. (5) In-container SDK install policy-blocked (dl.google.com CONNECT 403 verbatim recorded) ⇒ no in-container APK; CI job + user VM are the proof paths.
- **Statuses (honest)**: citizen-app NOT_PROVEN · provider-app NOT_PROVEN — upgrade only on VM/CI evidence. Seeded preview users exist (citizen.moyo, vashandi.* — no seed blocker); provider journey needs a searchable seeded patient + facility (MAESTRO_PATIENT_QUERY).
- Report: `docs/mobile/mobile-runtime-truth-report.md` (VM handoff checklist §9). Architecture flag for IATG: mobile auth bypasses the BFF (direct Keycloak PKCE).

---

# CI INFRASTRUCTURE FINDING (2026-07-06) — GitHub Actions is startup-failing repo-wide

**While closing the oros/WS-P6-B follow-up via Path A (add oros to a CI gate), the CI run itself revealed a larger problem:**
- Added job `oros-imaging-test` to `.github/workflows/ci.yml` on branch `claude/oros-ci-gate` (@ `51e996113`) — YAML valid, mirrors the proven `backend-test` shared-lib bootstrap; oros suite is in-memory (no DB/dcm4chee-arc needed).
- The push triggered CI run **#889** which concluded **`failure` with ZERO jobs**, `run_started_at == updated_at` (instant) — a **startup failure**, not a test failure.
- **Not caused by this change**: runs **#882–#888** (on `claude/web-session-anchor-nnnkf6` and the coordination branch, untouched ci.yml) ALL insta-fail identically (0–6s, conclusion=failure, 0 jobs), spanning 2026-07-05 13:51 → 2026-07-06 00:06. GitHub Actions has not actually executed ANY job for this repo across recent history.
- **Implication**: Path A cannot produce green oros evidence until Actions runs again. More importantly, the repo's EXISTING CI gates (backend-test, trust-e2e, etc.) have not been executing either — this session's confidence came entirely from local `mvn`/vitest gates, never CI. The entire CI-coverage plan (Phases 1–3) is moot until Actions is restored.
- **Likely cause** (not fixable from here — GitHub account/repo settings): Actions spending-limit/billing exhausted, or Actions disabled for the repo, or an org policy block. Repo-wide + instant + 0-job is the billing/disabled signature (a per-branch YAML error would not fail the anchor's own unchanged workflow).
- **Status of oros/WS-P6-B**: STILL OPEN — NOT closed. The `claude/oros-ci-gate` branch holds the correct gate, ready to verify once Actions runs. Immediate fallback to close it independently: run `cd services && mvn -pl oros-service -am test` on the preview VM (reaches dcm4che), return exit code + surefire summary.
- **Recommended user actions**: (1) check GitHub → repo Settings → Actions (enabled?) and Billing → Actions spending limit; (2) once green, re-push `claude/oros-ci-gate` (or re-run #889) to get the oros evidence and confirm the existing gates actually pass; (3) meanwhile, oros can be closed via the VM fallback.

## CI finding — RESOLVED PATH (2026-07-06, user confirmed billing issue; gates run on VM)

- User confirmed: the Actions startup-failures are a **known GitHub billing issue**; gates are run on the **preview VM** instead. So the canonical gate is `scripts/test/run-backend-checks.sh` (VM), not CI.
- **True root cause of "oros never verified"**: oros was in NEITHER the CI job list NOR `scripts/test/run-backend-checks.sh` (which gated only shared-libs/tech-companion/experience-bff/tshepo-authz). Fixed on branch `claude/oros-ci-gate` @ `f729a4043` with an ADDITIVE gate in BOTH places: the VM script (`oros-imaging-tests` gate_run — active on the VM now) + the ci.yml job (dormant until billing restored). Verified: oros has 32 surefire unit tests (incl. the WS-P6-B `DicomMwlTest`/`ImagingWorkflowServiceTest` modality changes) needing NO DB; the one `OrosGoldenContractIT` (failsafe) is not run by `mvn test`.
- **To CLOSE oros/WS-P6-B (on the VM):** either `bash scripts/test/run-backend-checks.sh` (now includes oros) or `cd services && mvn -pl oros-service -am test`. Return exit code + surefire summary → oros marked closed with that evidence. Requires the `claude/oros-ci-gate` change (merge to anchor, or apply the one-liner directly).
- **CI-coverage plan retarget**: Phases 1–3 (the 96-ungated-module fix) now target the VM gate script `scripts/test/run-backend-checks.sh` (expand toward full-reactor test with quarantine), NOT ci.yml — since CI is billing-dead. The in-container Phase-1 baseline still yields the per-module test-health matrix for ~112 modules (oros excluded here — dcm4che blocked in-container; VM-only).

## CI-coverage closure — Phases 0–2 LANDED on anchor (2026-07-06, user-authorized: "Merge then Phase 2")

Anchor advanced `ca871d21d` → **`619399104`** via three additive, user-authorized merges:

1. **Phase 1 baseline (truth-finding)** — ran the full reactor `mvn test` in-container on anchor `ca871d21d`. Result: **113 modules SUCCESS, 2 FAILURE**:
   - `oros-service` — infra-only (dcm4che `maven.dcm4che.org` 403 in-container; resolves on VM). NOT a code defect.
   - `pharmacy-service` — **REAL anchor compile break**: `DispenseEngineImpl.publishStockMovementRequested` called `order.getId()` (non-existent) instead of `getOrderId()` at two sites, added by `1453bc36e`. Invisible because pharmacy was one of the 96 ungated modules. This is the concrete cost of the coverage gap — a broken module sitting on the anchor.
2. **Pharmacy fix** — `fable/pharmacy-dispense-getid-fix` @ `83e7165cb`, merged as `28445c16e`. One-symbol correction (`getId()`→`getOrderId()`) matching the file's 5 other call sites. Verified: `mvn test -pl pharmacy-service -am` → **21 tests, 0 failures**.
3. **oros VM gate** — `claude/oros-ci-gate` @ `f729a4043`, merged as `cc302e5dc`. Adds `oros-imaging-tests` to the VM gate script + dormant ci.yml job. **Closes WS-P6-B's gate gap** (oros now in the canonical VM gate).
4. **Phase 2 full-reactor gate** — `fable/backend-reactor-gate` @ `21b6e00fc`, merged as `619399104`. New `backend-reactor-tests` gate in `scripts/test/run-backend-checks.sh`: `mvn test` across every reactor module, excluding only oros/experience-bff/tshepo-authz (each separately gated). **Proven green on anchor `cc302e5dc` after the pharmacy fix: 112 modules, 3248 tests, 0 failures, 0 errors** — quarantine list EMPTY. `mvn test` runs surefire (`*Test`) only, never failsafe (`*IT`), so no Docker/Kafka/Postgres needed; the 4 Testcontainers modules are compile-checked here and covered by their IT suites elsewhere.

**Net**: the 96-ungated-module gap is closed on the VM gate — every reactor module's unit tests now run on every VM gate invocation. **Phase 3** (burn down any future quarantine additions) is a no-op today — the list is empty.

**VM gate confirmation — FINAL, all six gates green (2026-07-06, preview VM `41.57.127.235`, tip `619399104`):** full `bash scripts/test/run-backend-checks.sh` run to completion inside tmux (survives SSH disconnect), aggregate **exit code `0`**:
`maven-shared-libs` ✅ · `maven-tech-companion` ✅ · `experience-bff-tests` ✅ · `tshepo-authz-tests` ✅ · **`oros-imaging-tests` ✅ (closes WS-P6-B)** · **`backend-reactor-tests` ✅ CONFIRMED GREEN**.
The reactor gate — the one item left pending after an SSH hangup killed the 03:51 run before a verdict — re-ran clean: `PASS backend-reactor-tests`, script `EXIT_CODE=0` (real exit code captured by `gate_run`, no piping, per this register's own gate-integrity rule). Log `/tmp/impilo-preview-gates/backend-reactor-tests.log`: zero `[ERROR]` lines, zero `<<< FAILURE`/`<<< ERROR` markers, matching the in-container baseline (112 modules, 3248 tests, 0 failures, 0 errors). Run window 05:46→05:58 local (`START 2026-07-06T03:46:27Z` → `END 2026-07-06T03:58:04Z` UTC). The 96-ungated-module coverage gap is now proven closed on the VM gate with a real green run end-to-end. The oros PASS is the direct WS-P6-B closer — see WS-P6-B status above.

---

# IATG WAVE 2 — opened 2026-07-06 (post Wave-1 verification)

**Wave-1 re-verified DONE** (3 read-only audit agents against merged anchor code, not the register): all 8 workstreams present, coherent, tested with real assertions, no stubs in prod paths; `services/tshepo-service/**` = 0 files touched across the IATG merge; modules green on the VM reactor gate.

**Key Wave-2 discovery — TWO PolicyEngines.** The flagged fail-open/SUPER_ADMIN defects are in the LEGACY `tshepo-service` monolith, which is **deprecated-retired and NOT deployed in prod/preview** (zero matches in `deploy/helm/**`; catalog `deprecated_retired`/`no_runtime_image_required`). The LIVE ext_authz engine everywhere is `tshepo-authz-service` (default-DENY, already fail-closed at Envoy `failure_mode_allow:false`). So IATG did NOT avoid needed authz work out of fear — it correctly added rules to the LIVE admin PDP (tshepo-authz V031) and froze only the dead monolith. The "RED wave" was reframed accordingly (user decision: **finish the legacy retirement**, not fix dead code).

## Batch 1 — MERGED TO ANCHOR 2026-07-06 (user-authorized "merge")
Integration branch `integration/fable-iatg-wave2-batch1-2026-07-06` @ `b2bab9195` off anchor `619399104`; 4 branches merged conflict-free (disjoint leases); combined gate green (varapi 170 / hr-payroll 11 / tuso 125 / org-registry 17 / experience-bff 807, 0 failures); no Flyway collisions; RED YAML valid. **Anchor `619399104` → `35534fba2`, pushed.**

- **W2-1 varapi status-axis** (`fable/w2-varapi-status-axis`) — consolidated the 5 overlapping provider status axes onto lifecycle-derived projections via a single `deriveStatusProjections()` normalizer (status/active_flag/licence can no longer diverge); `isActive()` reads lifecycle only; dropped the dead `professional_standing_status` axis (V019, DROP-only). Frozen: ProviderTrustController/Service + PRELOADED/CLAIMED untouched. Decision note `docs/architecture/varapi-provider-status-axes.md`.
- **W2-3 hr-payroll employment boundary** (`fable/w2-hrpayroll-overlap`) — added forbidden-responsibility `must-not-own-employment-trust-status`; scoped `hr.employees.employment_status` to payroll-financial only (governance stays the trust SoR); `EmploymentStatusBoundaryGuardTest` scans governance/vashandi/BFF to prove none consume it for trust. Docs + guard, no migration.
- **W2-4 facility-claim & admin-appointment** (`fable/w2-facility-claim`) — mirrors WS-F: tuso `FacilityAdminAppointmentEntity` + `FacilityClaimService` (eligibility gated on `FacilitySourceLegitimacy.allowedOnPlatform`; submit→PENDING, approve→ACTIVE; V017); org-registry records the org↔facility `AffiliationEntity` (reuses V001, no new migration); BFF `FacilityClaimController` (claimant from X-Actor-ID, consent required, masking proven by serialized-JSON, honest 501s for document/org-invitation). Frozen FacilityEntity/legitimacy untouched.
- **W2-RED finish legacy retirement (safe subset)** (`fable/w2-red-legacy-retirement`) — closed the local-compose split-brain: re-pointed `/api/v1/authorize|step-up|break-glass|policies|devices|health` from the legacy `tshepo_service` cluster to fail-closed `tshepo-authz`; the fail-open PolicyEngine (reachable only via /v1/authorize) is now off ALL live paths in every environment. Coordinator adjustment: RETAINED the `tshepo` container (worker had removed it) because 8 non-PDP routes (identity/consent/audit/keys/sign/certificates/offline/external) have no other server in the runtime compose — they are NOT the fail-open PDP. Stale `:8079` ref fixed; `EnvoyRuntimeNoLegacyTshepoRouteGuardTest` prevents regression. Retirement checklist exit conditions (30-day telemetry, compat-proxy removal, federation ADR) remain HONESTLY OPEN — nothing marked met. FOLLOW-UP: wire split-out identity/consent/audit/keys/offline services into runtime compose, re-point those 8 families, then remove the container.

## Batch 2 — queued (dispatch after Batch-1 anchor merge)
- **W2-2 council/HPA adapter** (varapi V020) — live adapter into `ExternalProviderCollaborationService.runCouncilVerification()`, writes evidence to the WS-C `provider_verification_attempt` sink, gated by existing `varapi.council-regulatory` flag (default off), mirrors `connector-fhir-adapter` RelayDestination pattern. Serialized after W2-1 (shared ProviderTrustService).
- **W2-5 org-registry cutover phase-1** (WGV V009 + org-registry V004) — build the MISSING mirror producer (WgvMirrorController has no writer today) + backfill + soak. FK-repoint / write-freeze deferred to a later gated step.
- **W2-6 adjudication wiring** (WGV V010 + BFF) — connect the 3 dead-ended arcs: Channel-C claim dispute → workflow startInstance; decision event → consumer; decision → claim/trust/affiliation feedback.
- **RED follow-up** — wire split-out identity/consent/audit/keys/offline services into `docker-compose.runtime.yml`, re-point the 8 non-PDP route families, then remove the legacy `tshepo` container.

## Batch 2 wave A — MERGED TO ANCHOR 2026-07-06 (user-authorized "merge")
Integration `integration/fable-iatg-wave2-batch2a-2026-07-06` @ `4e8152f96` off anchor `35534fba2`; 3 branches merged conflict-free; combined gate green (varapi 182 / workforce-governance 67 / experience-bff 808, 0 failures); no Flyway collisions; RED YAML valid. **Anchor `35534fba2` → `a79aa561d`, pushed.**

- **W2-2 council/HPA live adapter** (`fable/w2-council-adapter`, varapi V020) — CouncilRegistryAdapter seam + HttpCouncilRegistryAdapter, double-gated OFF (`varapi.council-regulatory.live-adapter-enabled` default false + per-registration `enabled` default false); wired as an optional pre-step in ExternalProviderCollaborationService.runCouncilVerification (definitive live match → COUNCIL_LIVE external attempt with the AUTHORITY's own confidence; everything else falls through to the unchanged local IMPORTED_REGISTRY path). NO fabricated results anywhere; unreachable honours policy-deny-when-unreachable. Registration + call-audit tables (V020). Frozen trust API untouched.
- **W2-5 org-registry cutover phase-1** (`fable/w2-org-cutover-phase1`, WGV no migration) — the MISSING mirror producer: OrganisationService publishes an in-tx event consumed AFTER_COMMIT by OrgMirrorRelay → OrgRegistryMirrorClient POSTs to the org-registry WgvMirrorController; best-effort (three independent guarantees a mirror failure cannot fail/rollback the primary wgv write, proven by OrgMirrorTransactionIntegrityTest). Backfill endpoint `POST /internal/v1/workforce-governance/org-mirror/backfill` (paginated, idempotent, re-runnable). Flag `impilo.org-mirror.enabled` default false. FK-repoint/write-freeze still deferred (phase 2+).
- **W2-RED follow-up** (`fable/w2-red-splitout-wiring`) — wired tshepo-identity/consent/audit/keys/offline into `docker-compose.runtime.yml` (host ports 18181-18185) + Envoy clusters; re-pointed 7 of 8 non-PDP families to their PROVEN split-out owners (identity→identity, consent→consent, audit→audit, keys/sign/certificates→keys, offline→offline). `/external/v1/` has NO owner anywhere (legacy source never served it either) → residual, legacy `tshepo` container + `tshepo_service` cluster retained SOLELY for it, documented like the federation ADR. Guard test extended.

## Batch 2 wave B — W2-6 adjudication caller wiring (dispatched, off anchor a79aa561d)
Wire the org-registry Channel-C claim ↔ adjudication loop end-to-end: claim escalation → workflow-service startInstance (existing API, engine untouched) → decision recorded (WGV, append-only) → `impilo.governance.adjudication.decision.recorded` consumed → claim ACCEPTED/REJECTED. WS-D employment CONFLICT and WS-F provider-claim producers = documented follow-ups. This is the last Wave-2 workstream.

## Batch 2 wave B — MERGED TO ANCHOR 2026-07-06 (user-authorized "merge") — WAVE 2 COMPLETE
- **W2-6 adjudication caller wiring** (`fable/w2-adjudication-wiring`, org-registry V004) — the org-registry Channel-C claim ↔ IATG adjudication loop is now real end-to-end: `POST /claims/{id}/escalate` → `WorkflowServiceClient` starts a `facility-claim-adjudication` workflow instance (workflow-service existing API, engine + V003 definitions UNTOUCHED) → decision recorded in WGV (append-only, untouched) → `AdjudicationDecisionConsumer` (@KafkaListener on `impilo.governance.events`, zero WGV change) resolves the claim ACCEPTED/REJECTED, keyed by `workflow_instance_id`. Honest failure handling (workflow down → claim stays UNDER_REVIEW pending; unknown claim → dead-lettered, acked; CONDITIONAL/unknown → not auto-resolved). Config-gated off by default (`impilo.orgregistry.adjudication.enabled` + `kafka-events-enabled` both false). Coordinator gate: org-registry 29 tests, 0 failures. **Anchor `a79aa561d` → `075fba349`, pushed.**

### IATG WAVE 2 — COMPLETE (anchor `075fba349`, 2026-07-06)
All six additive workstreams + the reframed RED retirement are on the anchor across three user-authorized merges (`35534fba2` Batch-1, `a79aa561d` Batch-2A, `075fba349` Batch-2B):
W2-1 status-axis consolidation · W2-2 council/HPA adapter seam · W2-3 hr-payroll employment boundary · W2-4 facility-claim & admin-appointment · W2-5 org-registry mirror producer (cutover phase-1) · W2-6 adjudication caller wiring · W2-RED (+follow-up) legacy fail-open engine severed from all live paths, split-out routes wired.
Every workstream coordinator-gated with real exit codes; frozen Wave-1 contracts (tshepo-service = still 0 files, varapi trust API, PRELOADED/CLAIMED, FacilityEntity/legitimacy) held throughout.

**Non-blocking follow-ups on the books (none required for Wave-2 done):**
1. **org-cutover phase 2** — repoint internal FKs (HscEmployment.employer_organisation_id etc.) to org-registry via source_ref, freeze the wgv write-path, disable the mirror endpoint. Gated on mirror-completeness soak evidence (adoption-doc criteria 2-5). Requires running the W2-5 producer/backfill with the flag ON first.
2. **`/external/v1/` ownership ADR** — the one legacy non-PDP route with no split-out owner; needs an ADR (like the federation route) before the legacy `tshepo` container can be fully removed from runtime compose.
3. **Additional adjudication producers** — WS-D employment CONFLICT and WS-F provider-claim should each start an adjudication via the same workflow-startInstance pattern (clean seams left).
4. **Council adapter go-live** — real council/HPA endpoint registration + flip `varapi.council-regulatory.live-adapter-enabled` (framework is in place, default off).

**Recommend:** run `bash scripts/test/run-backend-checks.sh` on the preview VM against anchor `075fba349` for the full-reactor green confirmation of the complete Wave-2 tree (as was done for `619399104`).

## Org-cutover phase-2a — MERGED TO ANCHOR 2026-07-06 (user-authorized "merge")
`fable/w2-org-cutover-phase2a` @ reviewed clean; additive/dormant only. **Anchor `075fba349` → `263ec744e`, pushed.** Delivered: WGV V009 nullable `org_registry_org_id` on the 5 FK-carrier tables (no backfill); org-mirror reconciliation/drift report (`GET /internal/v1/workforce-governance/org-mirror/reconcile` → completeness% + missing + drift = the criterion-1 evidence generator); org-registry mirror-inventory read endpoint; dormant read-preference resolver (default WGV = strict no-op); callable-off key-backfill; `docs/architecture/org-registry-cutover-phase2-runbook.md`. Defaults byte-identical; nothing irreversible. Coordinator gate: WGV 81 / org-registry 31, 0 failures. Unblocks the 2b VM soak.

# IATG END-TO-END FUNCTIONALITY — program opened 2026-07-06 (user directive "continue the IATG pipeline to end to end functionality")
Target = the doctrine demo journey (WS-H): origin admin creates Country Operation (two approvals) → appoints National Administrator → org onboarded → provider preloaded (Channel A) → citizen claims Provider ID → EC evidence upgrades trust to EMPLOYMENT_MATCHED → four-block trust profile → facility per-source legitimacy (with GOVERNMENT_OPERATIONAL_EXCEPTION). All backend pieces are BUILT + TESTED across Wave 1 + Wave 2, but most are gated OFF and may be BFF/internal-only (not surfaced in the experience shell), and no verified end-to-end run exists. Phase 1 = four parallel gap audits (governance spine; provider trust journey; facility+adjudication; cross-cutting flags/seed/deploy/demo) mapping built-vs-wired-vs-demonstrable. Findings + execution plan to follow.

## IATG e2e Phase-1 — MERGED TO ANCHOR 2026-07-06 (user-authorized "Merge")
Integration `integration/fable-iatg-e2e-phase1-2026-07-06` @ `d6235064e` off anchor `263ec744e`; 3 branches merged conflict-free; combined BFF gate 808 tests / 0 failures; all helm/compose YAML + realm JSON valid; seed + e2e scripts lint-clean. **Anchor `263ec744e` → `0f3554583`, pushed.**

Makes the IATG doctrine journey RUNNABLE + PROVABLE in preview (runtime proof on the preview VM):
- **E1-DEPLOY+ENABLE** (`fable/e2e-iatg-deploy-enable`) — deployed organization-registry-service in full-boot preview via GENERATOR SOURCES (edited config/full-boot-service-classification.yml + waves.yml + the two scripts/full-boot/*.mjs, regenerated values-full-preview-*.generated.yaml) — org-registry port 8153, DB organization_registry (helm) added to fullBootServices + initDatabases. Enabled journey flags as service env: BFF IMPILO_FEATURES_EC_MATCHING=true + ORGANIZATION_REGISTRY_BASE_URL; org-registry ORG_REGISTRY_ADJUDICATION_ENABLED + ORG_REGISTRY_KAFKA_EVENTS_ENABLED + SPRING_KAFKA_LISTENER_AUTO_STARTUP + WORKFLOW_SERVICE_URL; WGV GOVERNANCE_KAFKA_EVENTS_ENABLED. Reconciled the one mismatched BFF @Value key (OrgRegistryFacilityAdminClient → impilo.services.organization-registry-base-url). Added org-registry to docker-compose.runtime.yml. Off-journey flags (org-mirror/read-preference/council-regulatory/gofr/zibo/booking-forward) stay OFF. BFF gate 808/0.
- **E1-SEED** (`fable/e2e-iatg-seed`) — PLATFORM_ORIGIN_ADMINISTRATOR + NATIONAL_ADMINISTRATOR roles (mirror production defs) + 3 principals (origin.admin.one/.two, national.admin.one) in realm-impilo-preview.json (55 roles); scripts/seed/iatg-e2e-seed.sh (Channel-A provider preload token + 3 facility legitimacy rows incl. GOVERNMENT_OPERATIONAL_EXCEPTION; preconditions only); impilo_org_registry compose DB init. Shellcheck clean.
- **E1-E2E** (`fable/e2e-iatg-harness`) — test/integration/iatg-end-to-end-runtime.sh: executable 9-step journey (corrected nested appointment path), --dry-run green, RUN_ADJUDICATION=1 gates the heavy Channel-C section, tuso legitimacy direct-in-mesh (annotated Phase-E2 BFF-proxy gap).

**PENDING: runtime proof on the preview VM** — deploy the regenerated preview values, then `bash scripts/seed/iatg-e2e-seed.sh > reports/iatg-e2e/seed.env && bash test/integration/iatg-end-to-end-runtime.sh`. The e2e's real exit code + asserted four-block trust profile / facility composite converts IATG from "built" to "proven functional end-to-end". **Phase E2 (UI surfacing)** = follow-on session (governance-spine screens, trust-profile screen, facility legitimacy BFF proxy + panel, facility-claim UI, EC-lane re-point, provider-claim nav).

## IATG e2e Phase-E2 (UI surfacing) — MERGED TO ANCHOR 2026-07-06 (user-authorized "Merge")
Integration `integration/fable-iatg-e2-ui-2026-07-06` @ `53a70d5cd` off anchor `0f3554583`; 3 branches merged conflict-free (routes.ts EXPECTED_ROUTE_COUNT→691 and ExperienceSidebar.tsx reconciled at integration). **Fast-forward `0f3554583` → `53a70d5cd`, pushed.** 38 files, +3947/−25 (28 UI, 6 experience-bff, 4 workforce-governance-service). **`services/tshepo-service/**` = 0 files** (invariant held pre- and post-merge). No org-registry phase-2c / write-freeze / irreversible cutover flip in the diff.

Surfaces the doctrine journey in `ui/one-ui-shell` so an operator/citizen can drive it (previously API/BFF-only):
- **E2-GOV-UI** (`fable/e2-gov-ui`) — Platform-Origin admin console (`/platform-origin` + `/[id]`), two-person approve/execute panel, org-onboarding wizard. Additive reads only: WGV `PlatformActionApprovalView` + `PlatformOriginService.listApprovals()` + controller GET approvals (READ ONLY — two-person write-engine untouched); BFF `PlatformOriginGovernanceClient` list-pending / action-approvals + controller. Gates: backend 0 (PlatformOriginController 7/7, PlatformOriginService 9/9), type-check 0, vitest 19.
- **E2-TRUST-UI** (`fable/e2-trust-ui`) — four-block trust profile (`/citizen/wallet/trust`, honest per-block UNAVAILABLE); EC split-brain fix (EcNumberFlow re-pointed off report-only `/api/v1/provider-claim/evidence` 501 onto trust-asserting `POST /api/v1/trust/employment-match` → EMPLOYMENT_MATCHED, raw-map response); provider-claim nav registered. UI-only, no BFF change. Gates: type-check 0, vitest 18/18.
- **E2-FACILITY-UI** (`fable/e2-facility-ui`) — one new BFF read proxy `FacilityController /{id}/status-composite` → TusoServiceClient (fail-closed 502 TUSO_UNAVAILABLE); facility legitimacy panel (per-source verdicts + GOVERNMENT_OPERATIONAL_EXCEPTION); facility-claim journey (`/facility/claim`). Gates: BFF 0 (FacilityControllerStatusCompositeTest 3/3), type-check 0, vitest 21.

**Combined integration gates (coordinator-run, real exit codes on the merged tree):** integrated type-check `TC_EXIT=0`; full E2 vitest **57/57** across 9 files `VITEST_EXIT=0`; route-count 11/11 (`EXPECTED_ROUTE_COUNT=691`); BFF `mvn test` `COORD_BFF_EXIT=0`; combined backend (BFF+WGV) `COMBINED_BE_EXIT=0`.

**Runtime proof status UNCHANGED** — the preview-VM seed + `iatg-end-to-end-runtime.sh` run (VERDICT lines + exit 0) is still separately PENDING. IATG remains **built + surfaced**, NOT yet relabeled "proven functional end-to-end". E2 surfaces the journey in the shell; it does not substitute for the runtime proof.

## IATG e2e Phase-E3 (journey-honesty pre-fixes) — MERGED TO ANCHOR 2026-07-07 (user-authorized "merge")
Branch `fable/e3-journey-honesty` @ `39bea26c5` off anchor `53a70d5cd`; **fast-forward `53a70d5cd` → `39bea26c5`, pushed.** 9 files, +529/−8 (4 WGV, 2 seed, 2 UI, 1 runbook). **`services/tshepo-service/**` = 0 files**; no org-registry/cutover/phase-2c files touched. Pre-fixes the honesty gaps found while designing the VM run, so the journey can go genuinely green (user decision: pre-fix first, keep "merged anchor only" by merging E3 before the run).
- **WGV EC-upsert wiring** — the HSC employment upsert DTO/controller/service could not set `ec_number`, so no seed could create a matchable row → harness Step 6 (EMPLOYMENT_MATCHED) was structurally impossible. Added `ecNumber` through `UpsertHscEmploymentRequest` → controller map → `HscEmploymentService.upsert` (canonicalised via existing `EcNumbers`; `linkedHealthId` already worked). New test `EmploymentMatchWebMvcTest.upsertPersistsCanonicalisedEcNumberEnablingMatchedBoth` drives the full upsert→match round-trip. Correct WGV base path is `/v1/internal/governance/**` (the design-audit's `/internal/v1/workforce-governance/**` was wrong; caught by the failing test).
- **Seed EC employment + seed.env bridge** — `scripts/seed/iatg-e2e-seed.sh` now POSTs an EC-bearing HSC employment for citizen.moyo (`b0000000-…-001`, `EC-000001`) via the real governance upsert and emits `CLAIMANT_HEALTH_ID` + `EC_NUMBER`; new `scripts/seed/iatg-seed-to-env.sh` bridges the `IATG_E2E_SEED_OUTPUT` block → `reports/iatg-e2e/seed.env` (neither script wrote it before; fails loudly if the block is absent).
- **UI facility-detail 502 fail-closed** — `ui/one-ui-shell/src/app/facility/[id]/page.tsx` previously omitted the legitimacy panel silently on 502 TUSO_UNAVAILABLE; now renders an explicit `facility-legitimacy-unavailable` notice ("…verdict is withheld until the service is reachable"). Co-located test asserts the notice renders and the real panel does not.
- **Runbook** — `docs/runbooks/iatg-e2e-preview-journey-runbook.md`: clean deploy at the post-E3 anchor, service-up checks, seed+bridge, harness with `RUN_ADJUDICATION=1`, browser walkthrough A–E (exact principals/testids), evidence template + 5-label verdict rubric.
- **E3 gates (coordinator-run, real exit codes):** WGV `mvn test` 84 tests / 0 failures (EmploymentMatchWebMvcTest 10/10); UI type-check 0; UI vitest 32/32 (6 files, incl. new facility-detail test); shellcheck 0 (both scripts).

**IATG VERDICT STILL PENDING** — E3 removes the structural blockers only. The milestone verdict (one of `IATG_PROVEN_END_TO_END` / `IATG_UI_MERGED_RUNTIME_FAILED` / `IATG_UI_MERGED_BROWSER_JOURNEY_FAILED` / `IATG_BLOCKED_BY_PREVIEW_DEPENDENCY` / `IATG_PARTIAL_WITH_DEFECTS`) is assigned by the coordinating session ONLY after the user runs the runbook on the preview VM (deploy at `39bea26c5` + seed + harness + browser A–E) and the returned evidence is adjudicated. This container is egress-blocked from `41.57.127.235` (403) and cannot run it. NOT relabeled "proven end-to-end".

## IATG e2e Phase-E3.1 (VM-run runtime fixes) — MERGED TO ANCHOR 2026-07-07 (user-authorized "merge")
Branch `fable/e3.1-e2e-runtime-fixes` @ `8efda86fc` off anchor `39bea26c5`; **fast-forward `39bea26c5` → `8efda86fc`, pushed.** 3 files (seed, harness, runbook), +23/−4. **`services/tshepo-service/**` = 0; `services/varapi-service/**` = 0** (scripts + docs only). Fixes surfaced by Cursor's first VM run of the harness against a (stale, wrongly-rendered) preview:
- **B seed tuso path** (code) — `POST /v1/facilities/search` → `/v1/internal/facilities/search` (verified against `tuso FacilityController @RequestMapping("/v1/internal/facilities") + @PostMapping("/search")`; response `PagedResponse.items[].{code,facilityUid}` already matched, no parser change).
- **C harness Idempotency-Key** (code) — added a fresh `Idempotency-Key` to the harness `HDRS` array (all BFF POSTs); harness made shellcheck-clean (reworded a comment shellcheck misparsed as a directive; `# shellcheck source=/dev/null` on the seed.env source).
- **A varapi preload 403** (NO code — deploy-config, documented in runbook §2.1) — root cause traced: varapi imposes no role on `/v1/internal/providers/bootstrap/preload`; Envoy ext_authz/tshepo-authz is NOT in varapi's preview path (Envoy fronts only experience-bff). In `full-preview` the helm helper injects `IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true` → `/v1/**` permitAll → 200. A 403 = oauth "on but issuer blank" (Spring default `Http403ForbiddenEntryPoint`), i.e. the stack was rendered from `values-preview.yaml`/`values.yaml`, NOT `values-full-preview.yaml`. Fix = redeploy full-preview at the correct commit; do NOT touch varapi security (guarded by `SecurityConfigSourceGuardTest`) or any PDP policy.
- **Root cause of the whole first VM run:** Cursor deployed the **stale `0f3554583`** (pre-E2-UI, pre-E3) and likely with non-full-preview values. The clean redeploy of `8efda86fc` via `full-boot-preview-deploy.sh` fixes commit + flag (preload 200) + surfaces the E2 UI screens in one move.
- **Gates:** shellcheck 0 (all 3 scripts), `bash -n` harness 0, seed `--dry-run` 0, harness `--dry-run` 0. No Java/TS touched → no mvn/vitest needed.
