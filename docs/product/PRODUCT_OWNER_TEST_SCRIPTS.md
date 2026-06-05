# Product Owner Test Scripts

> Executable acceptance scripts for vNext experience completion batches.

---

## Script 1: Provider Patient Encounter (Phase 4 — First Completion Batch)

### Persona

- **Role:** Clinical provider (e.g. `DOCTOR` or `NURSE`)
- **Facility:** Any facility with preview sandbox test data
- **Assurance:** Standard provider login (Keycloak preview realm)

### Starting context

1. Log in to Impilo web experience at preview URL.
2. Activate facility context (facility selector if prompted).
3. Confirm trust headers are set (no 401/403 on `/internal/v1/` calls in network tab).

### Path through UI

| Step | Action | Expected screen |
|------|--------|-----------------|
| 1 | Navigate to **Queue → Patient Search** (`/queue/search`) | Search page with facility badge |
| 2 | Search for a known test patient (name or CPID) | Result list with Open Chart / Add to Queue |
| 3 | Click **Open Chart** | Patient chart `/ehr/{patientId}` |
| 4 | Open **Encounters** (`/ehr/{patientId}/encounters`) | Encounter history + Start encounter |
| 5 | Start new **OUTPATIENT** encounter (or open active) | Redirect to `/ehr/{patientId}/encounter/{encounterId}` |
| 6 | Observe **Encounter transaction journey** rail (below patient journey panel) | Loading → linked transaction OR explicit empty state |
| 7 | If linked: note transaction state, provider stage, correlation ID | Badges and next-action buttons visible |
| 8 | Capture vitals or triage (if encounter active) | Success toast / saved state |
| 9 | Click a **Next trusted action** (if present) | Action submits; no mock/fixture data |
| 10 | Open **Visit Outcome** when ready to close | Discharge flow with encounter ID |
| 11 | (Mobile) Open provider app → active encounter | Transaction context card on encounter screen |

### Expected backend calls

| Call | When |
|------|------|
| `GET /internal/v1/patients?search=…` | Patient search |
| `GET /internal/v1/encounters?patient_id=…` | Encounters list |
| `POST /internal/v1/encounters` | Start encounter |
| `GET /internal/v1/encounters/{id}` | Encounter detail |
| `GET /internal/v1/core-transactions?type=FACILITY_WALK_IN&encounter_id={id}` | **Phase 4** orchestration rail |
| `POST /internal/v1/core-transactions/{txId}/actions/{code}` | Apply next action (if clicked) |
| `POST /internal/v1/vitals` or `/internal/v1/triage` | Clinical capture |
| `POST /internal/v1/encounters/{id}/close` or discharge endpoint | Close encounter |

All requests must include trust headers (`X-Tenant-ID`, `X-Facility-ID`, `X-Actor-ID`, `X-Purpose-Of-Use`, etc.).

### Expected records / data changes

- New encounter row in PCT with `ACTIVE` or `IN_PROGRESS` status.
- Vitals/triage rows associated with `encounter_id` when saved.
- Core-transaction action may update workflow/dispatch state (when composition source exists).

### Expected events / audit

- Encounter create/close events via PCT outbox (existing behaviour).
- Core-transaction timeline entries visible in BFF detail when workflow delivery exists.
- Correlation ID on transaction matches request `X-Correlation-ID`.

### Expected completion state

- Encounter page shows coherent journey: clinical alerts → patient journey → **transaction rail** → closure header.
- Provider sees explicit next step (triage pending, referral review, or closure).
- No “coming soon”, no fixture-only transaction data on encounter page.
- Mobile shows matching transaction state or honest empty message.

### Edge cases

| Case | Expected behaviour |
|------|-------------------|
| No workflow transaction for encounter | Empty state: “Encounter transaction not linked yet” — clinical work unaffected |
| BFF unavailable | Amber error on rail; vitals/notes still work |
| Permission denied on transaction | Amber permission message; no data leak |
| Closed encounter | Rail may show terminal state; closure header says encounter closed |
| Offline mobile | Transaction card shows loading/error per network; no fake data |

### Acceptance criteria

- [ ] Encounter page loads real encounter from BFF/PCT (not stub).
- [ ] Orchestration rail calls live `encounter_id` filter (verify in network tab).
- [ ] No mock JSON or fixture flag on encounter orchestration path.
- [ ] Next actions invoke real BFF POST when clicked.
- [ ] Mobile encounter screen shows transaction context card.
- [ ] Product owner can complete search → encounter → document → close without dead ends.

---

*Additional scripts will be appended as completion batches ship.*
