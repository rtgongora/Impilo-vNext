# Rito — Tshepo Policy SPEC (queued, not authored)

> **Status:** SPEC ONLY. The Tshepo `PolicyEngine.java` / OPA is **single-writer-locked to the
> CZO cluster**. Rito does **not** edit PolicyEngine. This document is the queued spec the CZO
> single-writer applies. Access decisions evaluate the platform's **10 access dimensions**
> (person identity, active role, attached role id, org affiliation, facility/workspace context,
> subject relationship, purpose of use, consent/legal basis, assurance level, workflow state).

## 1. Roles

| Role key | Who | Context |
|---|---|---|
| `rito.reporter.client` | citizen/patient (or anonymous token) | self-service / mobile front door |
| `rito.reporter.worker` | frontline health worker | facility workspace |
| `rito.supervisor` | supervisor / line manager | facility/department |
| `rito.quality_focal` | facility quality focal point | facility |
| `rito.qi_officer` | district/programme QI officer | district/programme |
| `rito.regulator` | district/national/regulator/oversight | jurisdiction |
| `rito.admin` | Rito service/config admin | tenant |

These map onto the multi-class identity model: a person signs in (Health ID) and *activates* a
provider/staff role (Provider/Staff ID) with facility context + purpose-of-use before any
provider-side Rito action.

## 2. Access rules (capability → conditions)

| Capability | Allowed roles | Key conditions |
|---|---|---|
| Submit client-voice case (complaint/compliment/suggestion/feedback) | `reporter.client`, `reporter.worker` | MINIMAL friction; anonymous allowed; no PII written to case beyond CPID ref |
| Submit clinical-quality incident / near-miss | `reporter.worker`, `supervisor`, `quality_focal` | active provider role + facility context (MODERATE) |
| View own submitted case + status | submitter (any reporter) | subject-relationship = is-reporter; anon tracked by case token |
| Triage / route / assign | `quality_focal`, `supervisor`, `qi_officer` | facility/district scope match; cannot triage outside own scope |
| Conduct quality audit / supervision | `supervisor`, `quality_focal`, `qi_officer` | supervisee/facility within scope; checklist pack permitted |
| Create/manage CAPA & QI/PDSA | `quality_focal`, `qi_officer` | linked to in-scope source case |
| Manage risk register | `quality_focal`, `qi_officer` | facility/district scope |
| View case detail incl. reporter identity | `quality_focal`, `qi_officer`, `regulator` | purpose-of-use = quality/safety; **anonymity honored** (identity masked if reporter chose anon) |
| Close case (non-severe) | `quality_focal`, `qi_officer` | workflow-state = VERIFICATION/RESOLVED |
| Close case (severe harm / sentinel) | `qi_officer` + `regulator` co-sign | MAXIMUM friction: assurance level ↑ + purpose-of-use + dual control |
| Regulator export / cross-facility rollups | `regulator` | jurisdiction scope; governed export; audited |
| Publish learning loop | `quality_focal`, `qi_officer` | source case CLOSED |
| Service/config admin | `rito.admin` | tenant admin |

## 3. Cross-cutting rules
- **Provider/citizen separation:** `reporter.client` can never reach triage/quality/regulator
  surfaces or other people's cases. Citizen routes are scoped to is-reporter relationship only.
- **Anonymity:** when a reporter elects anonymity, reporter identity is withheld from all roles
  (including regulator); only the case content and routing metadata are visible. Enforced as a
  field-level visibility rule (reuse `shared-core` visibility guards pattern).
- **Scope containment:** facility-scoped roles see only their facility's cases; district/regulator
  roles see their jurisdiction; national sees aggregate.
- **Workflow-state gating:** transitions and closures are permitted only from valid prior states
  (see lifecycle in the design doc); severe-harm closure requires dual-control.
- **Audit:** every sensitive action (case access, identity reveal, closure, export) emits a
  non-repudiation event to `tshepo-audit-service`.
- **Boundary enforcement:** `ROUTED_OUT` decisions (to patient-safety/madi/tuso/support) are
  authz-checked and audited; Rito never grants access to the foreign owner's case record.

## 4. Distinction from patient-safety roles
patient-safety queues its own roles (MCAZ/regulator PV reviewer, citizen/caregiver/provider
safety-reporter). Rito's `regulator` is a **quality/oversight** regulator, not the PV/MCAZ
reviewer. Both specs are queued separately to the CZO single-writer; role keys are namespaced
(`rito.*` vs the PV service's keys) to avoid collision.

## 5. Hand-off
This spec is queued for the CZO PolicyEngine single-writer. No PolicyEngine/OPA edits are made on
`intake/rito-design`. ⚠️ Final role granularity + assurance thresholds depend on the truncated
brief's governance section — see truncation gaps.
