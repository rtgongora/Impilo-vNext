# Clinical PCT Encounter vNext-native Implementation Report

## Objective

Implement Lovable encounter/telemedicine feature coverage in a vNext-native way without replacing vNext shell/layout/routing architecture.

## What was implemented in this pass

### 1) Encounter/PCT hardening (no synthetic success)

- `experience-bff` encounter orchestration now fail-closes on upstream unavailability instead of using local in-memory encounter fallbacks.
- Encounter close now delegates to canonical PCT completion API and returns upstream payload/error honestly.
- Encounter create now requires `journey_id` for canonical start; missing journey context is an explicit validation error.
- Encounter discharge endpoint is explicitly blocked (`501 BACKEND_CAPABILITY_MISSING`) instead of synthetic discharge success.

### 2) Mobile encounter/triage hardening

- Mobile encounter list/create/close routes now fail-close with typed `502` envelopes on PCT failure.
- Mobile triage write/list routes now return explicit validation or upstream errors rather than empty success responses.

### 3) Teleconsult honesty guardrails

- `TeleconsultController` no longer returns in-memory synthetic clinical success; all teleconsult routes are fail-closed with explicit backend blocker metadata.
- `ui/experience` teleconsult builder/session pages were updated to remove local-success fallbacks:
  - No local consent token generation.
  - No local message echo on failed send.
  - No local stage/status mutation on failed response/close actions.
  - Audio/video controls now show honest unavailable execution state.

### 4) Referral builder stage/mode coverage

- Builder now explicitly models seven referral stages, including stage 6 `Consultation Mode`.
- Six telemedicine modes are represented in UI (`async`, `chat`, `audio`, `video`, `scheduled`, `board`) as package preferences.
- Mode execution remains explicitly blocked pending canonical backend readiness.

### 5) Encounter state and test coverage

- Added tests for chart loading and not-found chart states in encounter page tests.
- Updated encounter controller tests for new fail-close/validation behavior and canonical close wiring.

## Explicit blockers (not hidden)

1. **Canonical teleconsult backend owner/API still not wired**
   - `experience-bff /internal/v1/teleconsult/*` is now explicitly blocked rather than faking success.

2. **Encounter discharge linkage needs journey-context orchestration**
   - Current encounter-level discharge route lacks canonical journey linkage and remains explicitly blocked.

3. **Referral summary and attachment deep wiring pending**
   - Patient/visit summary auto-assembly and document-service attachment ID linkage require additional BFF/backend integration work.

4. **Referral directory/routing depth pending**
   - Full VARAPI/TUSO backed routing resolution for practitioner/workspace/unit/pool remains partial.

## Readiness statement for this scope

- This pass improves production honesty and architecture coherence for encounter/PCT/teleconsult surfaces.
- It does **not** mark Experience or Clinical plane fully READY for all teleconsult/referral depth.
- Remaining blockers are explicit and documented in the alignment matrix and registers.

## Focused virtual encounter follow-on (this pass)

- Implemented first-class PCT referral package persistence (`pct_referral_packages`) and telehealth sessions (`pct_telehealth_sessions`).
- Added PCT referral lifecycle endpoints (`/v1/referrals*`) and telehealth APIs (`/v1/telehealth*`, `/v1/patient/{cpid}/telehealth`) with typed transition/validation errors.
- Added encounter modality metadata (`modality`, `virtual_mode`) and validation in encounter start workflow.
- Rewired `experience-bff` teleconsult routes to canonical PCT + MVUMO orchestration:
  - create/update/submit/respond/complete now proxy to real backend paths
  - consent stage now initiates MVUMO workflow and persists consent references in PCT
  - live messaging/media routes remain explicit `501` (no fake transport success).
- Replaced encounter discharge blocker route with canonical encounter->journey linkage and PCT discharge initiation.

