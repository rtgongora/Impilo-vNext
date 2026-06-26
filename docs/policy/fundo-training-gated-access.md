# Fundo Training-Gated Access — Policy Specification

Status: **SPEC + QUEUED rego** (2026-06-26)
Owner lane: Fundo LMS (`task_e7e0f1dd`, branch `intake/fundo-lms`)
Decision authority: **TSHEPO** (`tshepo-authz-service` `PolicyEngine` + OPA `impilo.authz`)

> Tshepo `PolicyEngine.java` and the OPA rego are **single-writer locked** to the CZO
> cluster (see memory `provider-clinical-place-batch-coordination`). This document is
> the contract Fundo provides; the rego that consumes it is **queued for WS-OPA / the
> CZO lead** and is reproduced below as a skeleton. Fundo did **not** edit
> `PolicyEngine.java` or any `.rego` file.

## 1. Problem

Some roles / workspaces must not be enabled until the actor has completed required
learning (Journey C: role/workspace enablement gated on completion). Today nothing
enforces this — `learning-service` knows completion truth, but Tshepo had no signal.

## 2. Boundary

- **Fundo (learning-service)** = source of truth for "is required learning satisfied?"
  It exposes a read-model and emits events. It does **not** make the access decision.
- **Tshepo** = makes the gate decision (allow/deny role/workspace enablement) using the
  Fundo signal as one input among its 10 access dimensions.
- **Vashandi** = workforce readiness view (separate consumer of the same completion truth
  via `/v1/internal/fundo/learners/{id}/cpd-summary`).

## 3. Fundo-provided signal (the contract)

### 3.1 Synchronous read-model (pull)

```
GET /v1/internal/fundo/learners/{subjectId}/training-gate
      ?subjectType=PROVIDER&courseCodes=REG-101,EHR-101
```
Trusted server-to-server (companion `X-Tenant-ID` header; same `/v1/internal/fundo/**`
chain as the cpd-summary endpoint). Response:
```jsonc
{
  "subjectType": "PROVIDER",
  "subjectId": "PRV-1",
  "tenantId": "…",
  "satisfied": false,                     // overall: all requirements met
  "requirements": [
    { "courseCode": "REG-101", "courseFound": true, "completed": true,
      "certificateValid": true, "satisfied": true, "basis": "completed_enrolment" },
    { "courseCode": "EHR-101", "courseFound": true, "completed": false,
      "satisfied": false, "basis": "not_completed" }
  ],
  "outstanding": ["EHR-101"],
  "evaluatedAt": "…",
  "decisionAuthority": "tshepo-authz-service"
}
```
Satisfaction rule: a requirement is satisfied when the subject has a **COMPLETED**
native enrolment for the course **and** (if the course issues certificates) at least one
**non-expired** certificate. Unknown course codes are reported `course_not_found` and
**never silently pass**.

### 3.2 Asynchronous signals (push, `lrn_event_outbox` → `platform.learning.events`)

| Event | Meaning for the gate |
|-------|----------------------|
| `impilo.learning.course.completed.v1` | a requirement may now be satisfied — re-evaluate |
| `impilo.learning.certificate.issued.v1` | certificate-gated requirement satisfied |
| `impilo.learning.certificate.expired.v1` | previously-satisfied requirement **revoked** |
| `impilo.learning.certificate.refresher.due.v1` | satisfaction will lapse soon (pre-warning) |

Tshepo may cache the gate decision and invalidate on these events, or evaluate live via 3.1.

## 4. Requirement source

The set of required `courseCodes` for a role/workspace is **policy data**, owned by
Tshepo (or the requiring domain), not by Fundo. The legacy `lrn_role_learning_requirement`
table maps role → *legacy* resource/path ids and is **not** expanded here; native course
requirements are passed explicitly to the read-model. Wiring a native
role→course requirement registry is a **future integration point** (left to the role/policy
owner) and intentionally out of this lane's scope.

## 5. Queued rego skeleton (for WS-OPA / CZO lead — DO NOT merge from this lane)

```rego
package impilo.authz

# Training-gated role/workspace enablement.
# `input.fundo_training_gate` is populated by the PolicyEngine from
# GET /v1/internal/fundo/learners/{subjectId}/training-gate (section 3.1).

default training_requirement_satisfied := false

training_requirement_satisfied {
    input.fundo_training_gate.satisfied == true
}

# Deny enablement of a gated workspace/role when required learning is outstanding.
deny[msg] {
    some gated_role
    gated_role := input.requested_role
    data.training_gated_roles[gated_role]            # policy data: which roles are gated
    not training_requirement_satisfied
    msg := sprintf("role %v requires outstanding learning: %v",
                   [gated_role, input.fundo_training_gate.outstanding])
}
```

## 6. Integration checklist (Tshepo side, when the lock lifts)

- [ ] PolicyEngine calls 3.1 (or subscribes to 3.2) for gated roles/workspaces.
- [ ] `data.training_gated_roles` populated with the gated role→courseCodes mapping.
- [ ] Decision cached; invalidated on `certificate.expired.v1` / `course.completed.v1`.
- [ ] Audit chain records the Fundo `evaluatedAt` + `requirements` used.
