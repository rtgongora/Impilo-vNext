# Fundo Facility & Regulatory Learning (B3)

Status: facility-context scope REAL; facility identity consumed from TUSO (2026-06-26)

## How regulatory / facility-operator learning is delivered
- **Facility/operator providers** are first-class via the **FACILITY** provider kind
  (C1) bound to a **TUSO** facility, accredited through the facility regulator route (C2).
  Regulatory learning (registration, inspection readiness, IPC, compliance) is authored
  and run by such accredited providers and scoped to their academy/space (C3).
- **Facility-context scope**: enrolments carry a nullable `facility_id` (V026), stamped
  from the `X-Facility-ID`/`X-Tuso-Facility-Id` trust header via the in-lane accessor (B0).
  TUSO remains the SoR for facility identity — learning stores the reference only.
- **Regulator dashboard** (B4 `regulator-summary`) shows provider accreditation posture.

## Boundaries
- No facility master data in learning-service; facility/place names are composed at the
  BFF from TUSO/Indawo when needed.
- Tshepo gates who may administer facility-scoped/regulatory learning (space-admin policy
  spec, queued rego — `fundo-space-admin-access.md`).

## Honest partial
Facility-level readiness reporting beyond accreditation posture + per-facility enrolment
scoping (e.g. inspection-cycle-linked pathways) is a future enhancement; the scope column
and provider/accreditation machinery are in place to build it.
