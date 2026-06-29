# PolicyEngine single-writer lock — LIFTED

**Status:** LIFTED (2026-06-29) · **Supersedes** the per-domain notes that defer policy
authoring to "the CZO single-writer lock" (e.g. `docs/design/rito/policy-spec.md`,
`docs/design/rito/tshepo-policy-spec.md`, `docs/design/provider-clinical-place/implementation-lane-plan.md`,
`docs/policy/*.md`, `ROUTE_MAP.md`, `SERVICE_WIRING_MATRIX.md`).

## Background

During the Consent/Trust Plane (CZO) rearchitecture, `tshepo-authz-service`'s
`PolicyEngine.java`, `ExtAuthzGrpcService.java`, `AuthorizeController.java`, and the
`infra/opa/**` rego were treated as **single-writer-locked** to the CZO lead session to avoid
concurrent edits to the authorization chokepoint while it was being rearchitected (effective-LoA
propagation, delegation Step 4.5, OPA shadow wiring, TPL-1 impersonation fix).

That lock was a **governance constraint, not a technical one** — there is no code mutex.

## Why it is lifted

The CZO cluster work is **complete and merged to the Product-Truth branch** (verified: effective-LoA
gating, delegation with self-grant-IDOR closed, JWT-authoritative ext_authz, OPA shadow hook — all
runtime-proven). With the rearchitecture landed, the chokepoint is stable and the per-domain policy
work that was *queued behind the lock* can proceed under normal review.

## What this unblocks

Per-domain policy **enforcement** (the SYS-1 remediation, Phase 1 of the gap-remediation program):
seeding `policy_rule` rows via `tshepo-authz-service` Flyway migrations (`V019+`, mirroring `V018`)
and adding in-service subject-relationship guards (mirroring `ClinicalAccessGuard` /
`InpatientClinicalService.requireActiveCareContext`). This converts the queued policy **specs**
(`docs/policy/*.md`, `infra/opa/impilo/*.rego`, the Rito/Fundo/Provider/Patient-Safety/OROS specs)
into live, enforced rules.

## Discipline (still required)

`PolicyEngine.java` remains a **high-blast-radius** file. Edits must:
- be reviewed (no silent single-writer assumption, but still careful review);
- keep `PolicyEngineTest` green (currently 36+ cases) and add cases for new rule classes;
- use **collision-safe rule priorities** when seeding (the V018 lesson — last-segment `resource_type`
  collisions need `path_contains` pinning);
- preserve fail-closed posture (no auth off-switch; OPA-down ⇒ Java DENY; Envoy stays fail-closed).

The OPA-as-PDP migration (Phase 7) is a separate, longer track and does not block this.
