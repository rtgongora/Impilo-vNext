# ADR-0052: Vashandi as Operational Workforce System of Record

## Status
Accepted — 2026-06-19

## Context

Impilo vNext needs a first-class Workforce Operating System / HRIS layer that answers operational workforce questions: who works where, roster/shift state, check-in/out, leave/availability, access risk, and staffing analytics — without impersonating person identity (VITO), professional registry (VARAPI), regulatory councils, public-sector employment authority (HSC via workforce-governance), or facility registry (TUSO).

Adjacent services exist:
- **workforce-governance-service** — organisation bootstrap, governance assignments, HSC employment records, access requests, bulk imports
- **scheduling-service** — clinical care-delivery scheduling (distinct product concern)
- **hr-payroll-service** — payroll, payslips, statutory HR records (distinct product concern)

Vashandi must not duplicate those SoRs; it must consume upstream truth and own **operational workforce execution truth**.

## Decision

1. Create **`vashandi-workforce-service`** (port **8167**, package `zw.gov.mohcc.impilo.vashandi`) as the canonical **operational workforce SoR**.
2. Vashandi owns `vsh_*` tables: workforce profile, membership, assignment, roster, shift, attendance event, leave/availability, access risk.
3. Vashandi **consumes** (does not re-own): Health ID (VITO), Provider/Worker ID and professional status (VARAPI), council/HPA regulatory summary, HSC employment/posting (workforce-governance), facility validity (TUSO), training/CPD evidence (Fundo).
4. Policy and access decisions remain with **Tshepo/OPA**; Vashandi stores `opa_decision_id` on assignments and returns honest degraded states when dependencies are unavailable — never fake success.
5. Experience BFF exposes `/internal/v1/vashandi/**`; Session Experience Contract carries `visibleVashandiWorkspaces` and workforce context fields populated from real Vashandi data.
6. **scheduling-service** and **hr-payroll-service** remain authoritative for clinical scheduling and payroll respectively; their data may feed Vashandi imports but Vashandi is authoritative for workforce operations (roster/shift/attendance/leave as operational layer).

## Consequences

### Positive
- Clear product module for Work → Vashandi
- End-to-end workforce lifecycle with real persistence, audit, and policy gates
- No hidden superusers or spreadsheet auto-activation

### Negative
- New service in full-boot matrix and registry
- Boundary discipline required vs workforce-governance assignments (Facility Staff & Access integrates with Vashandi assignment records)

## Alternatives considered

- **Extend workforce-governance-service only:** Rejected — governance bootstrap/onboarding must remain separate from operational HRIS execution
- **Extend hr-payroll-service:** Rejected — payroll SoR must not absorb roster/check-in/access-risk operational layer
- **UI-only scaffold:** Rejected per gap-closure doctrine
