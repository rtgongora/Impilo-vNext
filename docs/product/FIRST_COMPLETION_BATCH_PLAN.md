# First Completion Batch Plan — Provider Patient Encounter

> Phase 4A–4B | Branch: `claude/staging-ux-orchestration-remediation-Yypyl`  
> Status: **implemented** (2026-06-05)

## Selected batch

| Field | Value |
|-------|-------|
| Journey ID | `provider-patient-encounter` |
| Core transaction type | `FACILITY_WALK_IN` |
| Actor / persona | Regulated provider (clinician, nurse, midwife, allied health) |
| Classification (pre-Phase 4) | `frontend-route-exists-but-disconnected` |
| Classification (post-Phase 4) | `orchestration-linked` — encounter page resolves live core transaction |

## Actor and context

- **Actor:** Provider with active role + facility/workspace context (trust headers via `api-client.ts`).
- **Workspace:** EHR encounter workspace (`/ehr/[patientId]/encounter/[encounterId]`).
- **Context activation:** Facility store + auth store must be populated before encounter APIs succeed.

## Entry points

1. `/queue/search` → chart → encounters → start encounter → encounter detail.
2. `/ehr/[patientId]/encounters` → start new encounter → redirect to encounter detail.
3. `/queue/waiting` or `/queue/triage` → encounter handoff (existing queue flows).
4. Mobile provider app → patient search → active encounter screen.

## Start state

- Encounter exists in PCT with status `ACTIVE` or `IN_PROGRESS`.
- Provider has clinical role group and facility context.
- Optional: workflow/dispatch has composed a `FACILITY_WALK_IN` core transaction with `clinicalContext.encounterId`.

## Transaction steps

| Step | Provider action | System behaviour |
|------|-----------------|------------------|
| 1 | Search / select patient | VITO patient resolve; queue context preserved |
| 2 | Start encounter | PCT creates encounter; redirect to encounter page |
| 3 | Triage / vitals / notes | Clinical writes via BFF (`/internal/v1/triage`, `/vitals`, `/clinical-notes`) |
| 4 | Review transaction rail | BFF lists core transaction filtered by `encounter_id` |
| 5 | Apply next action | POST `/internal/v1/core-transactions/{id}/actions/{code}` |
| 6 | Close encounter | PCT close + disposition via Visit Outcome |

## Backend services

| Service | Role |
|---------|------|
| `experience-bff` | Composition, core-transaction list/detail/actions, encounter proxy |
| `pct-service` (clinical) | Encounter SoR — create, update, close |
| `workflow-service` | Dispatch deliveries feeding core-transaction composition |
| `tshepo-service` | ext_authz policy on every BFF call |

## APIs integrated

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/internal/v1/core-transactions?type=FACILITY_WALK_IN&encounter_id={id}` | Resolve encounter-linked transaction |
| GET | `/internal/v1/core-transactions/{id}` | Detail (shell link) |
| POST | `/internal/v1/core-transactions/{id}/actions/{code}` | Apply orchestration next action |
| GET | `/internal/v1/encounters/{id}` | Encounter detail (existing) |
| POST | `/internal/v1/encounters` | Start encounter (existing) |
| POST | `/internal/v1/vitals` | Vitals capture (existing) |
| POST | `/internal/v1/clinical-notes` | Documentation (existing) |
| POST | `/internal/v1/triage` | Triage (existing) |

## Frontend routes and components

| Surface | Path / component |
|---------|------------------|
| Encounter page | `/ehr/[patientId]/encounter/[encounterId]` |
| Encounters list | `/ehr/[patientId]/encounters` |
| Queue search | `/queue/search` |
| **New** | `EncounterOrchestrationRail` on encounter page |
| **New hook** | `useEncounterCoreTransaction(encounterId)` |

## Mobile screens

| Screen | Change |
|--------|--------|
| `EncounterScreen` | Loads FACILITY_WALK_IN transaction by `encounter_id`; shows state, provider stage, next action |
| `coreTransactionService` | `encounterId` query param support |

## State management

- TanStack Query: `["core-transaction", "list", "", "FACILITY_WALK_IN", encounterId]`
- Mutations invalidate `["core-transaction"]` on action apply.
- Mobile: `["provider-encounter-core-transaction", encounterId]`

## Validation

- Encounter page renders rail only when `encounterId` is non-empty.
- BFF filters items where `clinicalContext.encounterId` matches query param.
- Permission denial surfaces amber banner (no silent bypass).

## Loading / error / empty states

| State | UX |
|-------|-----|
| Loading | Spinner + “Loading encounter transaction context…” |
| Error | Amber banner — clinical work continues |
| Empty | Explain queue/walk-in will link transaction when dispatch in scope |
| Ready | Transaction type/state badges, provider/person stages, next actions |

## Trust and security

- All calls through `api-client.ts` with mandatory trust headers.
- Envoy → TSHEPO ext_authz unchanged.
- Action payloads include `encounterId`, `patientId`, `source: encounter-orchestration-rail`.

## Data writes and reads

- **Reads:** Core transaction composition, encounter, triage, referrals, CDS feeds.
- **Writes:** Vitals, notes, triage, pathway protocol, core-transaction actions, encounter close.

## Events and audit

- Core-transaction timeline events composed from workflow/dispatch deliveries.
- Action apply posts to BFF with correlation ID from trust headers.
- Encounter close emits PCT domain events via outbox (existing).

## Related services affected

- Workflow dispatch (transaction source)
- PCT (encounter lifecycle)
- Experience BFF only code change in Phase 4

## Completion state

Journey is **orchestration-complete** when:

1. Provider opens encounter page and sees linked transaction OR explicit empty state (no fixture).
2. Next actions are actionable via BFF (when composition returns them).
3. Mobile encounter screen shows matching transaction context.
4. Product-owner test script passes on preview sandbox.

## Test cases

| ID | Test |
|----|------|
| TC-ENC-01 | `EncounterOrchestrationRail` loading state renders |
| TC-ENC-02 | Empty state when no transaction linked |
| TC-ENC-03 | Transaction context with next actions renders |
| TC-ENC-04 | BFF `listCoreTransactions` accepts `encounter_id` |
| TC-ENC-05 | Mobile encounter screen shows transaction card when linked |

## Product-owner acceptance

See [`PRODUCT_OWNER_TEST_SCRIPTS.md`](./PRODUCT_OWNER_TEST_SCRIPTS.md) — **Provider Patient Encounter E2E**.
