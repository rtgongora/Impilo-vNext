# Break-glass guards do not consult the grant model

**Status:** **EmergencyAccessGuard CLOSED** (madi-service). **ClinicalAccessGuard still OPEN** (pct-service).
**Found:** Phase 0 workstream A (P0 containment), 2026-08-07.
**Measured on:** `feat/trust-domain-responsibility-foundations` @ `73cc29d27`.

## The exposure

> **Read the next two sections as history.** They record the original state and why it could not be
> fixed inside a containment sweep. What was actually built is below, under *RESOLVED*. The exposure
> described here still stands verbatim for `ClinicalAccessGuard`.


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

---

## RESOLVED for EmergencyAccessGuard — PO ruling, 2026-08-07

The blocker recorded above ("closing this needs a grant-validation call that tshepo-authz does not
expose, and the decision to fail closed on it is a patient-safety decision") was escalated and
decided. The ruling distinguishes the two cases that the old guard could not tell apart:

| Case | Outcome |
|---|---|
| No grant exists | **REFUSE** |
| Grant service unreachable | **ALLOW**, with a hard audit record and a named post-hoc review obligation |

"Fail closed always" was considered and rejected: the affected paths include O-negative and
uncrossmatched blood release, and a tshepo-authz outage is not a clinical fact. Letting one block a
transfusion trades a governance risk for a mortality risk.

### What was built

1. **The missing seam.** `POST /v1/visibility-escalations/grants/validate` on tshepo-authz —
   read-only, purpose-built, and deliberately *not* an authorization endpoint: it answers "is this
   grant active for this actor in this tenant" and nothing else. `VisibilityEscalationService
   .validateGrant` backs it. Absent, malformed, expired, revoked and wrong-actor are all `NO_GRANT`,
   indistinguishable from the caller's side so the endpoint cannot be used to probe which grants
   exist. The token travels in the body, not the query string, so it stays out of access logs.

2. **`EscalationGrantValidator`** (shared-kernel-java) — three outcomes, `VALID` / `NO_GRANT` /
   `UNREACHABLE`, with the load-bearing rule stated at the seam: **an implementation must not have a
   catch-all that turns a transport failure into `NO_GRANT`.** That single line is the difference
   between a real control and another one that looks like validation and is not.

3. **`TshepoEscalationGrantValidator`** (madi-service) — the HTTP mapping, which is where the ruling
   is actually implemented: 200+`VALID` → VALID; 200+`NO_GRANT` → NO_GRANT; **4xx → NO_GRANT** (the
   service answered; a malformed request is not an outage, and must not be a route to an ungoverned
   override); **5xx / timeout / connect / DNS / unparseable → UNREACHABLE**.

4. **`UngovernedOverrideRecorder`** — a *required* collaborator, so the guard writes the record
   itself rather than returning a flag and trusting each call site. `OutboxUngovernedOverrideRecorder`
   writes to MADI's event outbox rather than calling an audit service: this record is written
   precisely when remote calls are failing, and a shared network fault would otherwise take out the
   record for the same reason it took out the grant check. Event type
   `BREAK_GLASS_UNGOVERNED_OVERRIDE`, aggregate `EMERGENCY_ACCESS`, payload carrying
   `reviewRequired: true` / `reviewStatus: PENDING` — its own type so the review is a selection, not
   a scan.

5. **`EmergencyAccessGuard`** is now an injected component with both collaborators mandatory. There
   is no constructor that yields a guard which can allow without recording.

### What is verified

All three branches red-proved by mutation:

- folding `UNREACHABLE` into `NO_GRANT` — the exact defect the ruling warns about — fails 4 tests
- allowing on `UNREACHABLE` without writing the record fails 2
- letting `NO_GRANT` through (restoring the forged-purpose hole) fails 3

Suites: shared-kernel-java 207, madi-service 61, tshepo-authz-service 399 — all green.

### Still open

**`ClinicalAccessGuard` in pct-service is unchanged and still gates on a bare purpose-of-use string
compare.** It was not touched because pct-service was reserved for the parallel workstream B, which
has it as its last remaining conversion. The pieces it needs now exist — the endpoint, the SPI and a
reference implementation — so wiring it is no longer blocked on anything but coordination.
