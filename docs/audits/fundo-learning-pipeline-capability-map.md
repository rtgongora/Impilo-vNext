# Fundo Learning Pipeline — Wave 0 Capability Map (repo-grounded)

**Stream**: Fundo Learning Pipeline (Fable Seven Pipeline Parallel Delivery Board, P7)
**Worker branch**: `cursor/e2e-fundo-learning` · **Base**: `origin/claude/web-session-anchor-nnnkf6` @ `a4a32e0b5`
**Audited**: 2026-07-05 · **Coordination**: `claude/impilo-vnext-coordination-75fzl0` @ `bf9c06aca`

This map records what is REAL vs PARTIAL vs MISSING at the current anchor tip, before any
Fundo-stream implementation work. It extends (does not replace)
`docs/product/fundo-lms-completion-and-honesty.md` and board §3 P7.

## 0. Branch-safety facts

- Anchor tip verified `a4a32e0b5` (board's `d44bb6022` is an ancestor; anchor moved +20
  commits — W0/W2 telemedicine plus Fundo steel-thread fixes `687a8386a..d1655d924`).
- Stale Fundo remotes inspected and **NOT resurrected** (board §2 do-not-merge verdict
  confirmed): `fix-impilo-fundo` (9 commits, merge-base 1147 behind HEAD),
  `impilo-fundo-upgrade` (3 commits, 926 behind), `split/pr3-fundo-ui` (4 commits, 1171
  behind). HEAD's native LMS (V001–V026, 128 BFF endpoints, 65 learning pages,
  `scripts/e2e/fundo-learner-journey.sh` 12-check steel thread) supersedes all three.

## 1. Test-harness honesty (gate-integrity finding)

`services/learning-service` has **no maven-failsafe plugin** and surefire default includes
only `*Test`. Therefore `mvn -pl learning-service test` **and** `mvn -pl learning-service
verify` run only the 16 `*Test` classes (64 tests) and **silently skip all 18 `*IT`
classes** (FundoNativeLmsIT, FundoAcademicIT, FundoAttendanceIT, …). Baseline evidence:

- `mvn test` → `MVN_EXIT=0`, 64 tests, 0 failures (surefire-reports).
- `mvn test -Dtest='*IT'` at pristine HEAD → `MVN_EXIT=1`, **6 errors** in
  `FundoCohortReportIT` and `FundoNativeLmsIT` (Certificates/LearningRecord/Standalone).

**Root cause (pre-existing at HEAD, invisible to default gates):** `5b86637aa`
(2026-07-04, "record a notification intent on certificate issuance") made
`FundoCertificateService.issueForEnrolment` write to `lrn_learning_notification` via
`FundoNotificationIntentWriter`. That table exists only as Flyway DDL (V008) with no JPA
entity; the test profile disables Flyway and relies on Hibernate `create-drop`, so every
certificate-issuing IT failed with `BadSqlGrammarException`. Fixed in this stream with a
test-only `src/test/resources/schema.sql` mirroring the V008 DDL (no production change).
Post-fix: `mvn test -Dtest='*IT'` → `MVN_EXIT=0`, unit + IT combined 131 tests, 0
failures, 0 errors, 4 skips (pre-existing deliberate GoldenContractSuite skips).

Any prior "full Fundo IT suite green" claim based on bare `mvn test`/`verify` exit codes
did not execute the ITs. Gate commands for this stream always use the explicit include.

## 2. Capability classification

Legend: ✅ real-and-wired · 🟡 partially-wired · 🔶 honest stub/seam · ❌ missing ·
⛔ W0-owned (handoff only)

### 2.1 Learner journey (Wave 1 scope)

| Capability | Status | Ground truth |
|---|---|---|
| Catalogue (facet filters, language metadata V014) | ✅ | `/learning/catalog` → `useFundoCatalog` → BFF `/v11/catalog` → `FundoCatalogController` |
| Text search | 🟡 | client-side title filter only; no server text search |
| Course detail + structure | ✅ | `/v11/courses/{id}/structure` |
| Enrolment (self, bulk; states ENROLLED/IN_PROGRESS/COMPLETED/CANCELLED/EXPIRED) | ✅ | `FundoEnrolmentService`; player page wired |
| Lesson progress + completion reconciliation | ✅ | all-lessons-100% reconciler; steel-thread proven |
| Assessment attempt / manual review / max-attempts | ✅ | `FundoAssessmentController`; `passed` may be null pending manual review (honest) |
| Certificate lifecycle (ISSUED→EXPIRED sweep, digest verification) | ✅ | V012/V015; UI states not-PKI-signed honestly |
| CPD evidence (Fundo side) | ✅ | `/learning/cpd` → `/v11/cpd/evidence` |
| Transcript / learning record | ✅ | `/v11/subjects/{type}/{id}/record` |
| My-learning buckets | 🟡 | backend returns `recommended`/`assignedPathways`/`cancelled`/`cpdEligibleCompletions`; UI renders only 4 of 9 buckets |
| Hub "required" KPI | 🟡 **defect** | `summarizeFundoMyLearning` falls back to overdue count; backend has no `required` bucket |
| Orchestration-rail enrolment count | 🟡 **defect** | expects array at `data`, API returns `{items:[]}` → always 0 |
| Pathway enrolment action | 🟡 | pathways read-only; no enrol-from-pathway UI |
| Reports (overview/cohorts/courses/overdue/assessments) | ✅ | wired |
| Programme/regulator dashboards | 🟡 | page exists + real API; unlinked from reports home, unregistered route |
| Mandatory/assigned learning surfaces | 🟡 | catalog `mandatory` filter + `enrolmentType` exist; no dedicated UI bucket |

### 2.2 Studio / authoring (Wave 2 scope)

| Capability | Status | Ground truth |
|---|---|---|
| Course/module/lesson authoring, draft→published→retired | ✅ | studio pages + `/v11/catalog` POST/PUT |
| Assessment authoring | ✅ | studio assessments |
| AI authoring | 🔶 | provider-agnostic, safe local stub default (honest) |
| Studio media (recordings/scripts/voiceovers) | 🟡 | metadata CRUD real; browser recording stays local blob + `localOnly: true` draft — **no binary upload pipeline** (honest copy in UI) |
| Library governance metadata (V025: review_status, access_level, expiry, audience, cadre, ai_usage_permission) | ✅ metadata / 🟡 binary | document-service multipart not wired; reference-URL uploads only |
| De-identification / consent / retention fields on artefacts | ❌ | no columns, no workflow |

### 2.3 CPD governance into Varapi (Wave 3 scope)

| Capability | Status | Ground truth |
|---|---|---|
| certificate.issued.v1 → Varapi PENDING candidate (Kafka + HMAC webhook, idempotent) | ✅ | `FundoCertificateIssuedListener`, `FundoWebhookController`, `FundoCpdGovernanceService` |
| Council accept/reject → CPD ledger (`CpdService.recordEvent` FUNDO) | ✅ backend | requires IN_PROGRESS cycle; autoAccept default **false** |
| Council accept/reject **UI** | ❌ | `useAcceptFundoCpd`/`useRejectFundoCpd` hooks exist, **no page imports them**; self-service page is read-only list |
| Council policy gating | 🟡 | `CouncilRegulatoryPolicyClient` → Tshepo evaluator is a permit-stub; `policyEnabled` default false |
| Regulator CPD review workspace | ❌ | `/work/regulators/[id]/cpd-review` is an empty shell |
| Ledger visibility | ✅ | `/home/credentials` shows Varapi CPD summary |
| Candidate-state visibility next to Fundo evidence | ❌ | `/learning/cpd` does not show Varapi candidate state |

### 2.4 Workforce gating (Wave 4 scope)

| Capability | Status | Ground truth |
|---|---|---|
| Fundo cpd-summary internal API (`NO_REQUIREMENTS/READY/AT_RISK/NOT_READY`) | ✅ | `FundoWorkforceReadinessController` |
| Training-gate read-model (graduated ALLOW/ADVISE/CONDITIONAL/BLOCK, PO-20260629-01) | ✅ signal | `FundoTrainingGateService`, 8/8 tests |
| Vashandi consumes cpd-summary | ✅ fetch / 🟡 use | stored in `trainingStatusSummaryJson`; eligibility only fails on *degraded* upstream — `NOT_READY` **does not affect the decision** |
| Vashandi calls training-gate | ❌ | client only calls cpd-summary; G-FU-02 enforcement half unbuilt (PO says "plain build") |
| Role→native-course requirement registry | ❌ | `lrn_role_learning_requirement` maps legacy resources; no native course-code bridge, no admin UI |
| Tshepo/OPA rego consuming training-gate | 🔶 queued | spec written; rego locked to WS-OPA/CZO lane — **coordination, not this stream** |
| Vashandi workforce UI training evidence | ❌ | `/work/vashandi/workforce/[id]` shows profile metadata only |
| BFF exposure of readiness/training-gate to web | ❌ | internal-only today |

### 2.5 Live sessions (Wave 5 scope)

| Capability | Status | Ground truth |
|---|---|---|
| Scheduled learning sessions (Fundo DB) | ✅ backend | `createScheduledSession`; optional Impilo Live link |
| Impilo Live webinar link (`POST /internal/v1/live/fundo-webinars`) | 🟡 | soft-degrade if live-service down (session still created, no live id); no scheduling UI (`useCreateSession` unused) |
| Session list/detail/join UI | ❌ | only `sessions/[sessionId]/checkin` page exists |
| Attendance capture (code/QR check-in, bulk mark, single-use tokens) | ✅ backend | `FundoAttendanceService` V017; facilitator mark UI missing |
| Attendance → completion (Fundo track) | ❌ | attendance rows never drive progress/completion |
| Live CPD completion bridge | 🟡 | live-service `CertificateService.issueCpdCertificate` → `POST /v11/sessions/live-completion` → enrol+100%+certificate; trigger is manual cert issuance, not attendance threshold |
| Course-page live embed | 🟡 **defect** | embed reads `structure.impiloLiveEventId` which `getCourseStructure` never returns (live ids live on session metadata) |
| RTC room/join/registration/replay internals | ⛔ W0 | live-service `onRecordingAvailable` is **real** (W1 replay pipeline), not a stub — but Fundo does not ingest replays |

### 2.6 Recorded learning (Wave 6 scope)

| Capability | Status | Ground truth |
|---|---|---|
| Webinar replay (live-service → document-service → PUBLISHED_REPLAY) | ⛔ W0-owned, real | Fundo UI links to `/live/event/{id}/replay` via embed |
| Fundo media library ingest of replays | ❌ | no consumer of `impilo.rtc.recording.available.v1` in learning-service (by lease design — handoff item) |
| Artefact governance (approval/access-level) | 🟡 | V025 metadata real; de-id/consent/retention/audit-of-access missing |

### 2.7 Supervision / mentorship / placements (Wave 7 scope)

| Capability | Status | Ground truth |
|---|---|---|
| Facilitator registry (TRAINER/TEACHER/PRECEPTOR/GUEST) + venues | ✅ | V016; studio delivery UI |
| Cohort↔facilitator assignment | ✅ backend / ❌ UI | `useAssignCohortFacilitator` hook unused by any page |
| Clinical placement + preceptor sign-off | 🟡 | V021; sign-off works but **any actor can sign** (no preceptor authorization check); no create-placement UI; `signoffNotes` not sent by UI |
| Placement lifecycle outbox events | ❌ | `FundoRegistrationService` never appends outbox events |
| Supervision plan / competency observation / skills logbook / mentor review cycle | ❌ | no schema, service, or UI |
| PRACTICAL_TASK "competency" checklist | 🔶 | ephemeral client state only; never persisted |
| PCT countersignature ↔ Fundo evidence bridge | ❌ | zero cross-references either direction; PCT countersign policy untouched (no-touch respected) |
| Assignment marking + feedback | ✅ | `/learning/teach/marking` |

### 2.8 Mobile parity

| Capability | Status |
|---|---|
| Catalog/enrol/progress/assessments/certificates/CPD-evidence | ✅ (`FundoLearningShellScreen` → same BFF v11) |
| Varapi ledger / council candidates / readiness | ❌ mobile |
| Offline | 🔶 read-through cache only (honest, per completion register) |

### 2.9 Navigation / route registry

Learning pages that exist and are wired but are **not registered in
`ui/one-ui-shell/src/lib/routes.ts`** (R6 no-touch — recorded for coordinator decision,
not edited by this stream): `/learning/reports/dashboards`,
`/learning/admin/{accreditation,providers,academic}`, `/learning/admissions`,
`/learning/students/*`, `/learning/teach/*`, `/learning/spaces/*`,
`/learning/studio/delivery`. `/work/fundo/admin/*` pages are governance navigation shells
(`ScopedAdministrationSurface`), not Fundo API surfaces.

## 3. Defect register (found in Wave 0, candidates for fix waves)

| # | Defect | Layer | Wave |
|---|---|---|---|
| D1 | Hub "required" KPI mis-derived from overdue fallback | UI | 1 |
| D2 | Orchestration rail enrolment count reads wrong response shape (`{items}`) | UI | 1 |
| D3 | My-learning renders 4 of 9 backend buckets (recommended/assigned/cancelled/cpdEligible dropped) | UI | 1 |
| D4 | Course live embed reads `structure.impiloLiveEventId` never populated by backend | UI+backend seam | 5 |
| D5 | Placement sign-off has no preceptor authorization check | backend | 7 |
| D6 | Placement lifecycle emits no outbox events (audit gap) | backend | 7 |
| D7 | CPD council accept/reject hooks exist with no UI surface | UI | 3 |
| D8 | Vashandi eligibility ignores `trainingReadiness` content | backend (vashandi) | 4 |
| D9 | `mvn test`/`verify` silently skip all 18 IT classes (no failsafe) | build | gate hygiene |
| D10 | Attendance never drives completion in the Fundo session track | backend | 5 |

## 4. W0 handoff register (no-touch respected)

1. Fundo replay ingest: learning-service consuming `impilo.rtc.recording.available.v1`
   (or a live-service push) so governed replays can attach to courses/library — needs W0
   owner sign-off on the contract.
2. Attendance-threshold-driven CPD (auto-issue on watch-minutes) — logic lives in
   live-service `CertificateService`; any change is W0.
3. Helm/preview wiring for `learning.integration.live.*` — deployment surface, no-touch.
4. Session-template semantics for learning/supervision rooms — W0 `libs/session-templates`.

## 5. Stream wave plan (safe, additive)

- **Wave 1 (UI)**: learner-journey honesty fixes — D1, D2, D3; my-learning full buckets;
  reports-home link to dashboards page; CPD card on hub.
- **Wave 3 (UI)**: council CPD accept/reject actions on provider-council self-service
  (existing hooks + BFF endpoints); candidate-state chip on `/learning/cpd`.
- **Wave 4 (backend+UI, coordinated)**: Vashandi→Fundo training-gate consumer
  (PO-20260629-01 "plain build" item): call `training-gate` in
  `WorkforceEligibilityService`, interpret graduated decision honestly
  (ADVISE=warn, CONDITIONAL/BLOCK reflected in eligibility), Vashandi profile training
  readiness card. Requirement mapping stays configuration/policy-backed — no hard-coded
  national policy.
- **Wave 5 (backend+UI)**: session list/detail UI; facilitator attendance surfaces;
  honest join states; D4 fix on the learning side (surface live ids from session
  metadata, not fake course-structure fields).
- **Wave 7 (backend+UI)**: placement create UI + preceptor authorization (D5) + placement
  outbox events (D6) + preceptor pending-signoff view. No PCT changes.
- Waves 2/6/8: extend only where seams allow; artefact de-id/consent governance needs a
  data-model decision — documented, not bulldozed.

All work additive; no shared migrations beyond learning-service-local `V027+` if needed;
no Kafka topic renames; no routes.ts/app-registry/api-client core edits (flagged to
coordinator instead).
