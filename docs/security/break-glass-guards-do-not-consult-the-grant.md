# Break-glass guards do not consult the grant model

**Status:** OPEN. Described, not fixed.
**Found:** Phase 0 workstream A (P0 containment), 2026-08-07.
**Measured on:** `feat/trust-domain-responsibility-foundations` @ `73cc29d27`.

## The exposure

Two guards gate emergency clinical access on a bare purpose-of-use string comparison:

| Guard | Module | Callers |
|---|---|---|
| `EmergencyAccessGuard` | `libs/shared-kernel-java` | `madi-service` only (MHP activation, MHP pack issue, emergency blood release) |
| `ClinicalAccessGuard` | `services/pct-service` | pct-service |

Both compare the supplied purpose against `EMERGENCY` / `BREAK_GLASS` and nothing else. Any caller
able to set `X-Purpose-Of-Use: BREAK_GLASS` satisfies them. On these paths, break-glass unlocks
emergency clinical access with no emergency, no approval, and no grant.

`EmergencyAccessGuard`'s javadoc claimed an override "is NEVER a silent bypass" and that "the
authoritative decision record stays upstream in the ext_authz/TSHEPO ledger". Both are false on this
path: Envoy defines two clusters, both `experience_bff`, and routes to no domain service, so
madi-service is not behind ext_authz. No upstream decision is made, none is recorded, and the purpose
being compared is client-supplied. That javadoc is corrected in the same commit as this document; the
enforcement is unchanged.

The `log.warn` in the guard is real, and is the only artefact an override currently produces.

## Why it was not wired to the grant model

A real grant model exists in `tshepo-authz-service` — `BreakGlassRequestEntity`,
`VisibilityEscalationGrantEntity`, `BreakGlassService`, `VisibilityEscalationService` — and
`VisibilityEscalationService.resolveActiveGrant` validates a grant token properly, against tenant,
actor and expiry. Wiring the guards to it was the intended Phase 0 fix. Three measurements blocked it:

1. **No validation seam is exposed.** `resolveActiveGrant` has exactly one production caller:
   `PolicyEngine:280`, inside the PDP's own authorize path. tshepo-authz's API surface is
   `/v1/visibility-escalations/requests`, `/requests/pending`, `/requests/{id}/review` and
   `/v1/break-glass` — the workflow for *obtaining* a grant. Nothing exposes *validating* one to a
   downstream service.

2. **`x-escalation-grant-id` has no downstream consumer that validates it.** The header is
   PDP-stamped and is listed in Envoy's `allowed_upstream_headers`, so it is trustworthy — but only
   on the Envoy path, which reaches experience-bff alone. Its two uses in experience-bff are
   `ShadowObservationFilter` (copies it into the P1 shadow envelope; verdict-free by design, changes
   nothing) and `VisibilityProfileController` (echoes it into a response body). Neither validates it.
   varapi's `OversightEscalationGrantEntity` is an unrelated varapi-local table for HPA oversight,
   not this header.

3. **A present-and-well-formed check would be worse than nothing.** Requiring the header at the guard
   would verify that a caller who can already forge the purpose can also supply a well-formed UUID.
   That reads like validation and is not — the same class of defect as the javadoc corrected above,
   and as costa's "Authz enforced upstream by Envoy ext_authz" comment.

`ClinicalAccessGuard` additionally sits in `pct-service`, which was reserved for the parallel
workstream B and was not touched.

## What closing this requires

- A grant-validation call that tshepo-authz exposes to downstream services (or the guards moving
  behind ext_authz, which today fronts only experience-bff).
- A decision to fail closed when no grant resolves. This is a **patient-safety decision**, not a
  refactor: the affected path includes O-negative and uncrossmatched blood release. Refusing an
  emergency release because a grant service is unreachable is a different kind of harm from allowing
  an ungoverned one, and the trade belongs to whoever owns the trust plane and the clinical policy —
  not to a containment sweep.

Until then the exposure stands, accurately described in both the guard and here.
