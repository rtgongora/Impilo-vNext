# Vashandi Completion Wave — "vashandi feels really thin"

> **Wave window:** 2026-07-10 · **Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`
> **PO observation:** "vashandi feels really thin" → grounded audit → full implementation.

## The audit verdict (what "thin" actually was)

Two fan-out audits (backend truth, experience truth) found the engine **mature** and the
experience **starved**:

- **Backend (deep):** 12 tables, an 11-state assignment FSM with DB-level constraints, a
  4-service eligibility gate (Varapi professional status, HSC employment, Tuso facility,
  Fundo training-gate with graduated ADVISORY/SOFT/HARD enforcement), leave types +
  fiscal-year balances, ad-hoc check-in with offline support, an hours-summary bridge that
  hr-payroll already consumes, access-risk scan/resolve/revoke, outbox events on seven topics,
  and the work-context read-model feeding the session contract.
- **BFF (complete):** 40+ governed routes with OPA prechecks and audit events — including the
  full assignment lifecycle and roster/shift mutations the UI never used.
- **UI (the gap):** create-only assignments, view-only rosters, read-only leave, actionless
  access review, no supervisor confirmation, `ADMIN`-only gate, no HR persona.

## What shipped

| Wave | Delivered |
| --- | --- |
| V1 | **Assignment lifecycle actions** — status-appropriate precheck / approve / activate / suspend / end on the assignments page. Gated actions obtain the OPA decision first (the eligibility gate answers `pending` without one) and render the verdict honestly: allowed / conditional training / denied / pending upstream, with the per-dependency check list. The eligibility engine is now *visible*. |
| V2 | **Roster planning** — create draft rosters with a period, expand to schedule shifts per workforce profile, approve as the governance step. Added the missing BFF passthrough for `GET /rosters/{id}/shifts`. |
| V3 | **Leave management** — request form, approve/**reject** decisions (rejection was missing in the backend: the leave FSM now enforces `pending → approved \| rejected \| cancelled; approved → cancelled`, terminal states final, deciding actor recorded on both decisions), fiscal-year balance visibility. Added the missing BFF `/leave/balances` + `/leave/types` passthroughs. (Training-requirements and imports pages already existed — the audit's "blank pages" was stale for those two.) |
| V4 | **Access review actions** (resolve / revoke access on open risks, decider shown on closed ones) + **supervisor attendance confirmation** — the attestation payroll-grade hours derive from. |
| V5 | **HR persona truth** — `HR_OFFICER` realm role, `hr.dziva` persona (PROV-ZW-00013, ACTIVE facility assignment, **seeded + verified live**), `WORKFORCE_ADMIN` role group; launcher and quick action regated from `ADMIN`. Also fixed a seeder defect: `apply_seed` marker guards skipped newly appended personas. |
| V6 | **Workforce golden journey** — hr.dziva walks hub → assignments → rosters → leave → access review asserting live-or-honest states, plus Start-menu discoverability; added to the journey runner. |

## Verification

| Gate | Result |
| --- | --- |
| vashandi component tests | AssignmentLifecycleActions 4/4 · RosterPlanningPanel 4/4 |
| vashandi-workforce-service | leave FSM tests added (rejection + terminal-state guard) — suite green |
| experience-bff | suite green (new passthroughs compile-safe proxies) |
| UI vitest / routes / launchers | full gates green |
| Persona pack | **9 provider + 8 governance/citizen personas verified live** |

## Honest remaining gaps

- **Licence/credential renewal visibility**: inventory correctly lives in Varapi
  (forbidden-responsibility honoured) but nothing surfaces expiring licences to workforce
  managers — needs a Varapi expiry read-model + a Vashandi dashboard card.
- **Workspace scope inputs are IDs**: assignment/roster/leave forms take workforce-profile IDs
  raw; a profile picker (search by name) is the next UX step.
- **Vashandi role templates vs realm roles**: workspace visibility is driven by `vashandi_*`
  role templates on work assignments — the seeded assignments use the generic role definition,
  so hr.dziva sees the work-visible + organisation-derived workspaces, not the full manager
  template set. Mapping wgv role definitions → vashandi role templates is a follow-up.
- Roster/shift and outbox publishing test coverage in the service remains light.
