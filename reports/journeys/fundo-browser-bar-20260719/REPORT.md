# Fundo Browser-Bar Remediation — 2026-07-19

**PO verdict that opened the wave:** "By today's bar, Fundo doesn't actually work. From a
browser nothing works — can't create a course, can't enrol. It just looks like mocks."

**Wave verdict:** the PO was right about the experience and wrong about the cause. Fundo was
never mocks — it is a real, fully-wired stack (69/72 UI pages on live hooks, ~93 BFF
passthroughs, deployed healthy service) that had **never once worked end-to-end from a
browser**, and failed silently at every layer that broke.

## Root causes found (all live-verified)

| # | Layer | Defect | Fix |
|---|-------|--------|-----|
| 1 | Shell route registry | All 16 `/learning/studio/**` routes gated `requiredRole: ADMIN_OR_HIE` — the TRAINER cadre (the intended authors, per the existing `LEARNING_AUTHOR` group) was silently bounced to `/home` by the AuthGuard. Course authoring was unreachable for trainers, with zero feedback. | `a19ca0f79` re-gates studio to `LEARNING_AUTHOR` |
| 2 | learning-service schema | `chk_lrn_enrolment_subject_type` (V006) only admitted legacy cohort classes (`PROVIDER/STUDENT/STAFF/FACILITY_TEAM/OTHER`) while the shell enrols as `USER_HEALTH_ID`/`PROVIDER_PUBLIC_ID` → **every browser self-enrolment ever attempted failed on insert** (raw 500). The old mocked e2e used `PROVIDER_PUBLIC_ID` and "passed" because it never touched the DB. | `258a0ce99` V030 widens the constraint + 400 on unknown types + lockstep test |
| 3 | experience-bff | `LearningServiceClient` swallowed ALL write failures into `null` → controllers answered `200 {"data":{}}` → the browser could not distinguish success from failure even in principle. | `ec4743093` writes now propagate upstream status + message |
| 4 | experience-bff | Missing/mistyped request params fell into the generic 500 handler (both advice classes). | `e3a7d3bd1` + `2bce1a839` → 400 BAD_REQUEST |
| 5 | Shell UX | Create-course and enrol handlers had no try/catch and no error UI — any failure was a silent no-op; enrol could POST an empty `subjectId` pre-hydration. | `c01d33f50` visible errors, id-less success treated as error, enrol disabled until subject resolves |
| 6 | Estate data | Catalogue nearly empty (4 courses seeded 2026-07-18 only); no Fundo seed lane existed. | `a39ff124c` — API-driven starter catalogue: **10 published courses** (EHR, IPC, PV, MNH, ETAT, privacy, surveillance, CHW, wellness, leadership) with modules/markdown lessons/quizzes + Clinical Foundations pathway. Applied live; idempotent re-run = 10 skips/0 failures |
| 7 | Trust plane (prod posture) | Zero tshepo ALLOW rules for `/internal/v1/learning/**` + fail-closed PDP → all of Fundo 403s the moment real ext_authz turns on (masked on preview: envoy configmap is a bare passthrough with **no ext_authz filter**). | `fbdef64ed` V041 policy seed (ALLOW-only matrix mirroring LEARNING_AUTHOR; applies at next tshepo-authz deploy) |

Historical note: the only prior "green" Fundo proofs were dishonest at today's bar —
`fundo-learning-flow.spec.ts` runs against `installFundoLearningMocks` (full network
interception), and `scripts/e2e/fundo-learner-journey.sh` curls pod-to-pod, bypassing
envoy + BFF. The real journey rig (`fundo-author-learner.journey.spec.ts`) failed at A1 in
all four recorded runs (2026-07-10) and was never re-run.

Also corrected during the wave: the initial "every lrn_* table is 0 rows" reading was a
`pg_stat_user_tables.n_live_tup` estimate artifact + a wrong-tenant probe; the estate held
4 courses (2026-07-18) under the canonical tenant. The seven defects above are unaffected.

## Live write-path proof (API, through the real ingress)

`w2-write-proof` — Traefik(443) → envoy → experience-bff → learning-service, real personas:
**12/12 ok** (run `w2proof-1784459728`): trainer login → create course → module → lesson →
publish → learner login → catalogue visibility → enrol (USER_HEALTH_ID) → start → progress →
non-UUID input answers 400 → DB rows verified (`lrn_course`, `lrn_enrolment`).

## Browser journey proof (Playwright, journeys project, no mocks)

`fundo-author-learner.journey.spec.ts` against `https://impilo.mohcc.gov.zw` (real login,
10-point acceptance standard, `expectSaved` requires observed 2xx mutations):

- **Run 1** (`artifacts-proof1-142124`): author ✓ 13.6s, learner ✓ 29.9s — **2 passed**
- **Run 2** (`artifacts-proof2-142223`): author ✓ 13.5s, learner ✓ 29.7s — **2 passed**

DB truth after the runs: each run's course row is PUBLISHED with exactly 1 enrolment
(`lrn_course` / `lrn_enrolment`); live published catalogue = 19 courses.

The road to green itself caught **three more real product defects** (each fixed + committed
before the passes; the mocked rig could never have seen them):

| Layer | Defect | Fix |
|---|---|---|
| Studio create form | Sent no `code`; backend requires code+title → every browser create 400'd ("code and title are required" — visible only thanks to fix #5) | `5d7c2e0c4` auto-suggested Course code field |
| Course builder | Add-lesson button dead behind an unselected module dropdown right after adding the first module | `7efecdf10` auto-select newest module |
| Studio Publish page | Backend catalogue list defaults `status=PUBLISHED` → the publish surface could never show the drafts it exists to publish; no per-row action either | `5159edf7c` one-click row Publish/Archive + `f0e1a1e68` studio lists request ALL |

Journey-spec robustness fixes in the same commits: language assertion anchored to the form
label (shell top bar also has a language control), structure assertion anchored to the
Current-structure list (bare text hit the hidden `<option>`), row locator pinned to `li`,
strict-mode `.or()` removed (`32fec7186`).

## Deployed hotfixes (preview estate, digest-pinned)

- learning-service `@sha256:1c1cc222…` (V030 migrated live at 11:08Z)
- experience-bff `@sha256:b3cb1920…`
- one-ui-shell `@sha256:ff4927eb…` (fundo-w5b, commit 5159edf7c)
- tshepo-authz V041: repo-landed; applies at next tshepo deploy (deliberately not
  hot-redeployed: trust chokepoint, CZO lock, zero preview effect due to envoy passthrough)

## Verdict

Fundo now clears today's bar: a trainer can create, build and publish a course from a
browser; a citizen learner can find it, enrol, learn and resume — live, twice, with the
data landing in the sovereign store. What remains honest-partial is unchanged from the
Fundo programme register (notification provider wiring, offline, Tshepo rego authoring
queued behind the CZO lock).

---

## Extended surface coverage (2026-07-19, second pass)

Same bar as the core loop: a real Chromium driving the real TLS ingress, real Keycloak
logins, `expectSaved` requiring observed 2xx mutations, then a DB row check in the
sovereign store. New specs live in `ui/one-ui-shell/e2e/journeys/`. Deployed after this
pass: `one-ui-shell@sha256:fb2339f0` (tag `fundo-w6-reports-daf0387dd`) — carries the two
UI defect fixes below. learning-service / experience-bff digests unchanged from the core
pass (no backend change was needed).

| Surface | Verdict | Evidence |
|---|---|---|
| **1. Assessment-taking → scoring → certificate** | **PROVEN LIVE** | `fundo-assessment-certificate.journey.spec.ts` — 2 tests green. Learner enrols in the scored EHR course, takes its 2-question quiz → **auto-graded 100 / passed / AUTO_GRADED** (real POST `…/assessments/{id}/attempts`); completes all 3 required lessons → enrolment **COMPLETED** → certificate **ISSUED** and viewable. DB: `lrn_assessment_attempt` (score 100, passed t), `lrn_certificate` (ISSUED); outbox `assessment.attempt.submitted.v1` + `certificate.issued.v1` + `course.completed.v1`. |
| **2. Media & library** | **PROVEN LIVE** (library resource); media-asset embed not separately proven | `fundo-library.journey.spec.ts` — 2 tests green. trainer registers a governed resource via Library Uploads (real POST `…/library/resources`), learner browses `/learning/library` and opens the detail — cross-user, resume-after-logout. DB: `lrn_library_resource` row. |
| **3. Reports / cohorts** | **PROVEN LIVE** | `fundo-reports.journey.spec.ts` — 2 tests green. Manager (`LEARNING_AUTHOR`) sees real numbers on `/learning/reports` **and** Studio Analytics: EHR at **100%** completion, `certificatesIssued`/`completed` non-zero, `publishedCourses=13` — each observed via a live GET 2xx **and** cross-checked against the rendered DOM (a page of zeros fails these steps). |
| **4. Live sessions / classroom** | **PARTIAL** — check-in PROVEN LIVE; live media classroom is a GAP | `fundo-session-checkin.journey.spec.ts` — green. Learner finds a facilitator's scheduled session, opens check-in, enters the code, records attendance via real POST `…/sessions/{id}/checkin`. DB: `lrn_session_attendance` (PRESENT/CODE). Classroom: see GAP notes. |

### Defects found and fixed on the way

| Layer | Defect | Fix |
|---|---|---|
| Studio Analytics (`/learning/studio/analytics`) | KPI tiles read `payload.courses`/`enrolments`/`certificates`, but `/reports/overview` returns `publishedCourses`/`totalEnrolments`/`completed` and no cert count → **3 of 4 headline KPIs rendered a dead `0` regardless of real activity** — the exact "looks like mocks" symptom. | `daf0387dd` — map to the real keys; source Certificates from the cohort-completions `totals.certificatesIssued` block already fetched on the page. Proven live post-deploy. |
| Library resource detail (`/learning/library/[resourceId]`) | Read a non-existent `status` field → always rendered "Status: unknown". | `fa8c4221f` — read `reviewStatus`/`resourceType`. |

### Honest gaps (documented, not smoothed)

- **Seed assessments carry no questions.** Only the seeded EHR fixture quiz
  (`dddddddd-0002-…`) has objective questions + correct answers. The 9 courses created by
  `scripts/seed/21-seed-fundo-learning.sh` each got a PUBLISHED assessment shell with
  **zero questions** — un-takeable (a zero-question submit scores `null`/pending, no pass,
  no cert path). The assessment journey is proven against the EHR fixture; broadening the
  seed to author real questions per course is follow-up work.
- **Certificates are metadata-only.** `FundoCertificateService` issues metadata + a
  SHA-256 verification digest; there is **no PDF / signed credential / download** (the
  certificates page says so). "Viewable" is proven; "downloadable" is out of scope by
  design.
- **Library upload is metadata + reference-URL**, not binary streaming into
  document-service MinIO (that remains the QUEUED partial from the Fundo programme
  register). Minor: `uploaded_by` persisted as `system` (actor id not propagated from the
  principal into `FundoStudioController.actorId()` for this path).
- **Media-asset embed / watch path not separately browser-proven.** `FundoMediaController`
  (watch-progress, chapters, bookmarks, attach) operates on pre-existing `lrn_media_asset`
  rows; the estate has none and there is no author upload that creates one, so the
  in-lesson video-consumption thread was not driven to a green.
- **No in-shell create-session authoring surface.** Scheduled sessions and check-in tokens
  are created via the backend API only (Studio Delivery authors facilitators/venues, not
  sessions); the session + token for journey 4 were provisioned out-of-band through the
  real ingress and injected via `FUNDO_SESSION_ID/TITLE/CHECKIN_CODE`.
- **Live media classroom (`ClassroomShell` over Impilo Live) is reachable but not
  browser-proven.** A `LIVE` session **does** schedule an Impilo Live event (event +
  `/live/event/{id}` join path created and queryable — Impilo Live is wired), and the
  classroom UI is reachable, but the real-time media room (join-room/token/chat/polls +
  WebRTC, inherently non-functional in headless Playwright) was **not** driven end-to-end
  this pass. Recorded as partial, not claimed green.

### Net

Of the four extended surfaces: **three clear the live bar outright** (assessment→scoring→
certificate, library author→consumer, reports/cohorts incl. Studio Analytics), and the
**fourth is proven for attendance check-in with the live-media classroom honestly flagged
as a reachable-but-unproven GAP.** Two more "dead numbers / dead field" UI defects were
caught and fixed live. The mocked chromium-project specs and pod-to-pod scripts still do
not count at this bar; the four new `*.journey.spec.ts` files do.
