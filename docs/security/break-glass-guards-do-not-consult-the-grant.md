# Break-glass guards do not consult the grant model

**Status:** CLOSED in source, on `phase0/a2-breakglass-grant`. Not deployed. See
[What is closed, and what is not](#what-is-closed-and-what-is-not) — the fix's full value depends on
a credential the estate has not yet provisioned.
**Found:** Phase 0 workstream A (P0 containment), 2026-08-07.
**Measured on:** `feat/trust-domain-responsibility-foundations` @ `73cc29d27`.
**Closed:** Phase 0 workstream A2, 2026-08-07, off `fc42a640c`.

## The exposure

Two guards gated emergency clinical access on a bare purpose-of-use string comparison:

| Guard | Module | Callers |
|---|---|---|
| `EmergencyAccessGuard` | `libs/shared-kernel-java` | `madi-service` only (MHP activation, MHP pack issue, emergency blood release) |
| `ClinicalAccessGuard` | `services/pct-service` | pct-service |

Both compared the supplied purpose against `EMERGENCY` / `BREAK_GLASS` and nothing else. Any caller
able to set `X-Purpose-Of-Use: BREAK_GLASS` satisfied them. On these paths, break-glass unlocked
emergency clinical access with no emergency, no approval, and no grant.

`EmergencyAccessGuard`'s javadoc claimed an override "is NEVER a silent bypass" and that "the
authoritative decision record stays upstream in the ext_authz/TSHEPO ledger". Both are false on this
path: Envoy defines two clusters, both `experience_bff`, and routes to no domain service, so
madi-service is not behind ext_authz. No upstream decision is made, none is recorded, and the purpose
being compared is client-supplied. `ClinicalAccessGuard` carried the same false claim ("The waiver is
still fully audited upstream"). Both javadocs were corrected — the first in the same commit as the
original version of this document, the second when the guard was rewired.

The `log.warn` in each guard was real, and was the only artefact an override produced.

## Why it was not wired to the grant model in workstream A

A real grant model exists in `tshepo-authz-service` — `BreakGlassRequestEntity`,
`VisibilityEscalationGrantEntity`, `BreakGlassService`, `VisibilityEscalationService` — and
`VisibilityEscalationService.resolveActiveGrant` validates a grant token properly, against tenant,
actor and expiry. Wiring the guards to it was the intended Phase 0 fix. Three measurements blocked it:

1. **No validation seam was exposed.** `resolveActiveGrant` had exactly one production caller:
   `PolicyEngine:280`, inside the PDP's own authorize path. tshepo-authz's API surface was
   `/v1/visibility-escalations/requests`, `/requests/pending`, `/requests/{id}/review` and
   `/v1/break-glass` — the workflow for *obtaining* a grant. Nothing exposed *validating* one to a
   downstream service.

2. **`x-escalation-grant-id` had no downstream consumer that validated it.** The header is
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

`ClinicalAccessGuard` additionally sat in `pct-service`, which was reserved for the parallel
workstream B and was not touched.

**Workstream A stopped here and escalated rather than half-fixing it. That was the right call**, and
the reasoning below is why: the missing piece was not a refactor, it was a decision nobody in a
containment sweep had the standing to make.

## The PO ruling — 2026-08-07

Escalated deliberately as a patient-safety decision. The ruling:

| Outcome | Behaviour |
|---|---|
| No grant exists | **REFUSE** |
| Grant service unreachable | **ALLOW**, with a hard audit record and a named post-hoc review obligation |

"Fail closed always" was considered and rejected. The affected path includes O-negative and
uncrossmatched blood release; refusing an emergency transfusion because a policy service is
unreachable is a different kind of harm from allowing an ungoverned one.

The cost of that choice is real and is accepted: an outage produces allowed-but-unvalidated clinical
actions. That is exactly why the third branch is obliged to leave a record someone will read, and why
the record is written *before* the allow rather than after.

## What was built

### 1. The validation seam — `POST /v1/break-glass/validate`

`BreakGlassGrantValidationService` consults both grant models: the token-scoped escalation grant and
the actor-scoped break-glass declaration. An unresolvable token falls through to the actor-scoped
check rather than short-circuiting, so a forged header cannot suppress a genuine break-glass
declaration and turn itself into a denial-of-care.

Shaped so it cannot drift into a second PDP: it takes no resource and no permission, returns no
roles / ceilings / capabilities / tokens, is read-only on every path, and issues no credential. It
returns 200 with `NO_GRANT` rather than 404, because a 404 is indistinguishable at the client from a
wrong URL — and a client that cannot tell "refused" from "could not ask" is the defect being removed.

### 2. Three outcomes as values, not an exception plus a default

This is the load-bearing part. The obvious shape — a boolean plus a thrown exception — is the bug:
every `catch` around it collapses "the trust plane refused" into "the trust plane did not answer",
and the only sensible-looking thing to write inside that `catch` is "treat it as no grant".

So `BreakGlassGrantClient` is contractually forbidden from throwing, and the three outcomes are
enumerated values on a returned object. `NO_GRANT` may only come from a 2xx response that said so.
A 401, a 404, a 503, a timeout, a connection refusal, an empty body and an unrecognised verdict are
all `UNREACHABLE`. Both directions matter and they fail differently:

- outage read as `NO_GRANT` → refused emergency transfusions over a typo in a values file;
- `NO_GRANT` read as `UNREACHABLE` → the forged-purpose hole silently reopens, with the audit record
  misdescribing it as an outage.

`UngovernedOverrideRecorder` is the mirror image: it **must** throw if it cannot record. An allow
with no trace is the ungoverned override this mechanism exists to expose, happening silently inside
the code written to expose it. That is not in tension with the ruling — the ruling protects against
*tshepo-authz* being unavailable, whereas a recorder failure means the service's own database is
gone, and the clinical write would fail moments later regardless.

### 3. The audit record

Written to the calling service's own transactional outbox, in the same transaction as the clinical
action, then routed to `tshepo.audit.events` with an `AuditEventRequest`-shaped payload that
`AuditKafkaConsumer` appends to the hash chain.

**The HTTP audit path was measured, not assumed**, as the brief required. The only HTTP writer to
`/v1/audit/events` in the estate is experience-bff, which has a client-credentials
`ServiceTokenProvider`. madi and pct have none, and workstream A made that endpoint
`anyRequest().authenticated()` with an attributable trust context — so **that path does not work for
these callers today**. The Kafka path does, and both services already produce on it.

The outbox is also the right sink on its own merits: this code runs at the moment a remote call has
just failed, so a record-of-last-resort that depends on another remote call would be most likely to
vanish exactly when it matters.

- **Guaranteed:** the local outbox row. Transactional with the clinical write; marked published
  rather than deleted, so it stays queryable indefinitely.
- **Best-effort:** arrival in tshepo-audit's hash chain, which depends on the poller draining and
  that consumer running.

Stated rather than glossed, because a reviewer looking only at the chain could otherwise conclude no
overrides occurred when the answer is sitting in the service's outbox.

Legibility: event type `BREAK_GLASS_UNGOVERNED_OVERRIDE` and outcome `ALLOW_UNGOVERNED`, both
distinct from ordinary break-glass traffic. A governed release and an override nobody could check
must not land in the same bucket, or the second becomes unfindable among the first. The payload
labels the purpose-of-use and grant token as **unverified** — recorded so a reviewer sees what was
claimed, not offered as evidence — and carries the review obligation and the reason the grant could
not be checked.

## Verification

All three branches red-proved by mutating the real guards and confirming the right tests fail.

`EmergencyAccessGuard` (9 tests):

| Mutation | Result |
|---|---|
| `NO_GRANT` allows instead of refusing | 2 red |
| `UNREACHABLE` skips the recorder | 3 red, incl. *"the audit record MUST be written, not just the allow"* |
| `UNREACHABLE` collapsed into `NO_GRANT` | 4 red |
| recorder failure swallowed | 1 red |

`HttpBreakGlassGrantClient` (11 tests): replacing the catch-all with `noGrant()` turns 4 red with
`expected: <UNREACHABLE> but was: <NO_GRANT>`.

`ClinicalAccessGuard` (10 tests): `NO_GRANT` waives → 2 red; waiver without recording → 3 red.

Suite totals, all restored to green: shared-kernel 207, shared-core 11 (new class), madi 9 + 11 + 3
on the affected classes, pct 959 with 0 failures.

The third branch is asserted on the **audit write**, not on the allow. An allow that produced no
record passes a naive test and is precisely the ungoverned override this exists to make visible.

## What is closed, and what is not

**Closed.** Neither guard can now be satisfied by a client-supplied purpose-of-use alone. Where the
trust plane answers, a forged `X-Purpose-Of-Use: BREAK_GLASS` is refused. Where it does not answer,
the override is allowed but leaves a durable, attributable, findable record with a named review owner
— instead of the single `log.warn` that was the only artefact before.

**Not closed, and it matters.** The full value depends on the grant check actually reaching
tshepo-authz, and that depends on a credential:

- `impilo.s2s.token.enabled` defaults to `false` estate-wide, and neither madi nor pct had the
  property block at all before this change. Only experience-bff, vashandi and workforce-governance
  reference it.
- The client therefore falls back to the **inbound caller's own bearer token**. This is legitimate
  authority for this question — "does *this actor* hold a grant", asked of a read-only endpoint that
  returns no entitlements — and it makes the refusing branch live for ordinary authenticated
  requests today rather than dead.
- But any call path with **no authenticated inbound request and no workload credential** resolves
  `UNREACHABLE`, allows, and records an override. That is the designed behaviour, not a bug, and the
  record names the misconfiguration rather than an outage — but it means a chunk of traffic gets
  audited-and-allowed where it should be refused.

**To finish this, provision per-workload Keycloak clients for madi-service and pct-service and set
`IMPILO_S2S_TOKEN_ENABLED=true`.** Until then, `BREAK_GLASS_UNGOVERNED_OVERRIDE` rows whose
`grantCheckFailureReason` reads *"no credential available to call tshepo-authz"* are the measure of
how much of the path is still ungoverned. That is a metric someone should watch, and it is the
honest completion criterion for this item.

**Also not done:** this branch is not deployed. No production deploy, no ext_authz change and no
policy-rule change were made.

## Related

- `EmergencyAccessGuard`, `BreakGlassGrantClient`, `UngovernedOverrideRecorder` — `libs/shared-kernel-java`
- `HttpBreakGlassGrantClient` — `services/shared-core`
- `BreakGlassGrantValidationService`, `BreakGlassGrantValidationController` — `services/tshepo-authz-service`
- `MadiUngovernedOverrideRecorder`, `PctUngovernedOverrideRecorder` — the two audit sinks
