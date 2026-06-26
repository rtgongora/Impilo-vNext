# Fundo Delegated Space Administration — Policy Specification

Status: **SPEC + QUEUED rego** (2026-06-26)
Owner lane: Fundo LMS. Decision authority: **TSHEPO** (`PolicyEngine` + OPA `impilo.authz`).

> As with the training-gate (`fundo-training-gated-access.md`), the OPA rego is
> single-writer locked to the CZO cluster. Fundo provides the data + spec; the rego is
> **queued for WS-OPA / the CZO lead**. `PolicyEngine.java` and `.rego` files are untouched.

## Problem

A learning-space (academy) admin must be able to administer **only their own space's**
content/learners — not the whole tenant. Fundo scopes content by `learning_space_id`
(V022); the access decision (who may administer space S) is policy.

## Fundo-provided data (the contract)

- Content is space-scoped: `lrn_course.learning_space_id` (also cohort/enrolment/program).
- `GET /v11/spaces/{spaceId}/courses`, `GET /v11/spaces/{spaceId}/summary` — space-scoped reads.
- `POST /v11/courses/{courseId}/learning-space` — bind/unbind a course to a space.
- A learning space binds to a workforce-governance `OrganisationUnit` (`org_unit_ref`) and is
  owned by an accredited provider (C1/C2). The space admin's authority derives from a
  governance role on that OrganisationUnit + an active provider accreditation.

## Authorization rule (for Tshepo)

A subject may administer learning-space `S` iff:
1. they hold a space-admin / org-unit-admin role for `S.org_unit_ref` (workforce-governance), AND
2. the owning provider's `accreditation_status == ACCREDITED` (C2), AND
3. the action's target content has `learning_space_id == S` (or is being bound to `S`).

National (NULL `learning_space_id`) content remains tenant-admin scoped, not space-admin scoped.

## Queued rego skeleton (for WS-OPA — DO NOT merge from this lane)

```rego
package impilo.authz

default may_admin_learning_space := false

may_admin_learning_space {
    some role
    role := input.subject.org_unit_roles[_]
    role.org_unit_id == input.fundo_space.org_unit_ref
    role.capability == "LEARNING_SPACE_ADMIN"
    input.fundo_space.provider_accreditation == "ACCREDITED"
}

deny[msg] {
    input.action.learning_space_id != null
    not may_admin_learning_space
    msg := sprintf("not authorised to administer learning space %v", [input.action.learning_space_id])
}
```

## Integration checklist (Tshepo side, when the lock lifts)

- [ ] PolicyEngine resolves `input.fundo_space` from the space + provider accreditation.
- [ ] `org_unit_roles` sourced from workforce-governance.
- [ ] Space-scoped Fundo mutations (course bind, cohort/assignment within space) gated by `may_admin_learning_space`.
- [ ] Audit records the space id + governance role used.
