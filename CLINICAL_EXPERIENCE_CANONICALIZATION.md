# Clinical Experience Canonicalization

This wave closes high-risk parity drift while preserving existing functionality.

## Doctrine Rule

- `ui/one-ui-shell` is the canonical execution surface of the one experience doctrine.
- `ui/experience` is a precursor/reference surface and must not carry richer behavior than `ui/one-ui-shell`.
- Any richer behavior discovered in `ui/experience` must be ported into `ui/one-ui-shell` first, then mirrored.

## Completed in this pass

### 1) Contract coverage expansion

- Added clinical and mobile clinical route coverage in `contracts/openapi/experience-bff.openapi.yaml` for:
  - encounters
  - queue entries and queue actions
  - triage
  - vitals
  - referrals
  - lab orders
  - pharmacy prescriptions
  - clinical notes/documents
  - patient timeline
  - mobile provider encounters/vitals/diagnosis/prescriptions/labs/lab results/referrals/triage
  - mobile telemedicine sessions (marked as legacy mobile route)

### 2) `one-ui-shell` parity closure with `ui/experience`

- Ported richer queue hooks into `ui/one-ui-shell`:
  - queue type filtering
  - transfer action
  - abandon action
  - safer query enablement behavior
- Restored encounter pathway/protocol mutation in `ui/one-ui-shell`.

### 3) Offline clinical write capture (mobile provider)

- Added durable offline queue binding for retryable failures on clinical writes:
  - vitals create
  - diagnosis record
  - prescription create
  - lab order create
  - referral create
  - encounter note patch
- Added shared helper `offlineClinicalQueue.ts` to write local pending records and enqueue idempotent sync operations.
- Added focused tests in `offlineClinicalQueue.test.ts`.

### 4) Clinician tasking inbox/worklist abstraction

- Added composed BFF endpoint:
  - `GET /internal/v1/clinical-worklist`
  - `GET /internal/v1/mobile/provider/clinical-worklist` (mobile adapter)
- Worklist now composes queue backlog, referrals, tasks, OROS order worklist, pharmacy worklist, and telemedicine session actions into one action rail.
- Wired to:
  - `ui/one-ui-shell` control tower
  - `ui/experience` control tower
  - provider mobile dashboard (`Unified Clinical Inbox`)

### 5) Orders orchestration beyond lab-only posture

- Upgraded `/ehr/[patientId]/orders` in `ui/one-ui-shell` with a unified orchestration section that surfaces lane counts for:
  - orders
  - pharmacy
  - referrals
  - telemedicine
- Added explicit lane pivots to medication, referral, imaging, and procedure workflows from the same orchestration context.
- Added a **guided cross-domain composer** on the same page:
  - live submit lanes: lab, medication, referral, teleconsult
  - explicit read-only/no-fake lanes: imaging, procedure, order sets until typed write contracts exist
  - this keeps orchestration unified without fabricating backend capability.

### 6) Telemedicine contract collapse with compatibility

- Canonicalized frontend query/mutation hooks to `/internal/v1/teleconsult/sessions*`.
- Added compatibility aliases in teleconsult controller:
  - `POST /internal/v1/teleconsult/sessions/{id}/join` (maps to accept)
  - `POST /internal/v1/teleconsult/sessions/{id}/end` (maps to complete)
- Updated provider mobile telemedicine screen to use canonical teleconsult routes.

### 7) Mobile UX depth improvements

- Added structured triage scoring support (HR, RR, SpO2, mental status) and merged acuity scoring in triage submission.
- Added referral facility discovery in referral panel using teleconsult routing search.
- Added prescribing interaction guardrail in prescription panel using live drug interaction checks before create.

### 8) Coverage expansion tests

- Added focused component-level order orchestration coverage:
  - `ui/one-ui-shell/src/app/ehr/[patientId]/orders/page.test.tsx`
  - validates guided lane contract behavior and canonical medication submit endpoint wiring.

## Telemedicine canonical path decision

- Canonical frontend contract for orchestrated teleconsult workflows remains `/internal/v1/teleconsult/*`.
- `/internal/v1/mobile/provider/telemedicine/*` remains a transitional mobile adapter route and is documented as legacy mobile path in OpenAPI until collapse is complete.

## Remaining depth work (next wave)

- Deepen telemedicine RTC/video integration quality and no-show orchestration fidelity.
- Expand order orchestration into a single multi-step create flow (not just lane-level pivots and counts).
- Add broader component and integration tests across encounter start → triage → orders/referrals/prescriptions → offline conflict resolution.
