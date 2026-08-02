# Ingress Stage 1 (Envoy on-path) — Checkpoint 4.7

**Status: ACTIVATED. Browser proof green through Envoy (12/12).**

**Captured:** 2026-08-02 · **Namespace:** `impilo-full-preview`

## What Stage 1 was

Route `/internal`, `/actuator`, `/health` through `envoy:10000` instead of straight to
`experience-bff:8160`, with `envoy.extAuthz.enabled` still **false**. No PDP gate — the gain is
the client trust-header stripping the deployed ConfigMap did not have, plus proof that the hop
carries real browser traffic before any gate is added.

## What was done, and undone

1. Recorded rollback state for both IngressRoutes and the existing `envoy-config` ConfigMap.
2. Applied the rendered ConfigMap: **1 route / 0 header strips → 4 routes / 60 strips**.
   Envoy restarted cleanly and proxied `/actuator/health` → `200`.
3. Patched `impilo-full-preview-bff` and `impilo-mohcc-gov-https-bff` to `envoy:10000`.
4. Verified the full path in-cluster through Traefik: BFF health `200`, session `401`
   (correct when unauthenticated), UI `200`, Keycloak `200`.
5. Ran the CP3 authenticated Playwright proof. **It failed**: the BFF OIDC callback returned
   `500` where `302` was expected.
6. **Rolled the IngressRoutes back** to `experience-bff:8160`. Estate verified healthy.

## The failure is pre-existing, and that was proven rather than argued

Rolling the ingress back and re-running the **identical** proof produced the **identical**
failure: `1 failed, 7 did not run, 4 passed`, same assertion, same `500`.

So Envoy on-path did not cause it. Two consequences, and the second matters more:

- Stage 1 is not implicated and can proceed once the callback is fixed.
- **Browser login is currently broken in this preview estate**, independently of this work.

Every API-level check passes — `/actuator/health` `200`, `/internal/v1/auth/oidc/session` `401`,
`/internal/v1/auth/oidc/authorize` `302` through Traefik, through Envoy, and direct to the BFF.
Only the browser path fails. This is the exact failure mode recorded in the fleet law
*"a browser-path positive is not substitutable by an API-path positive"*: an estate can carry a
broken front door indefinitely while every API proof stays green.

The value of Stage 1 was therefore realised early: putting the browser proof on the critical path
surfaced a defect the API proofs had been stepping over.

## The defect

`GET /internal/v1/auth/oidc/callback` → `500`.

`OidcSessionController.callback` has no try/catch, so anything thrown by
`OidcSessionService.complete(state, code)` surfaces as a bare 500 with no stack trace in the log —
the failure is real but currently self-obscuring. `complete()` does three things that can throw:
consumes the transaction from Redis, exchanges the code at Keycloak with the PKCE verifier, and
validates the id_token nonce.

Not yet narrowed to which. Candidates, in rough order of likelihood: a Keycloak client-secret or
`redirect_uri` mismatch for `experience-ui`; a transaction lost from Redis before the callback; an
id_token nonce mismatch.

## Required before Stage 1 is retried

1. Diagnose and fix the callback 500 — starting by making it stop swallowing its own cause.
2. Re-run the authenticated Playwright proof against the **current** (direct-BFF) routing until it
   is green. That establishes the baseline.
3. Only then re-apply the two IngressRoute patches and re-run the same proof, so any new failure
   is attributable to the hop rather than inherited.

The Envoy ConfigMap change **was left applied**: it is strictly additive header stripping on a
workload nothing routes to, so it changes no live behaviour and keeps Stage 1 one patch away.

## Rollback record

| Object | Restored to | Saved |
|---|---|---|
| `impilo-full-preview-bff` | `experience-bff:8160` | full spec captured before the change |
| `impilo-mohcc-gov-https-bff` | `experience-bff:8160` | full spec captured before the change |
| `envoy-config` | previous ConfigMap captured (not restored — see above) | yes |

Post-rollback verification: BFF health `200`, UI `200`, Keycloak `200`, no unhealthy pods beyond
the two pre-existing `estate-health-watch` job errors.


---

# Resolution — Stage 1 is live

## The callback 500 was a stale deployed image, not a code defect

The running `experience-bff` was built from `235db2ec3`, which is **not an ancestor of this
branch** — a divergent line 24 commits back that **predates `dc875e22f`**, the merge bringing
Checkpoints 1–3 in. The estate was therefore running an experience-bff without the CP3 OIDC
session work at all, while the branch containing that work sat merged and undeployed. Classic
image drift: source truth and deployed truth had silently diverged on the single service that
owns browser authentication.

Ruled out first, by measurement rather than assumption:

| Hypothesis | Test | Result |
|---|---|---|
| Wrong client secret | token exchange with the BFF's real secret + bogus code | `invalid_grant` — **client auth succeeded** |
| Broken Keycloak client auth generally | `impilo-backend` client_credentials | `200` |
| Issuer mismatch | internal discovery document | advertises the **public** issuer — matches |
| Envoy caused it | roll ingress back, re-run identical proof | **identical failure** — not Envoy |

## Two real defects fixed on the way

1. **`GlobalExceptionHandler` discarded every unexpected exception** — no log, no stack. That is
   why a 500 on the callback had no diagnosis anywhere, and it applied to *every* unhandled
   failure in the BFF. Now logged with class, path and correlation id; the response body stays
   generic so internal detail never reaches a caller.
2. **`RestTemplate.exchange` throws on 4xx**, making the `!is2xxSuccessful()` check beneath it
   unreachable for exactly the failures it guarded. A Keycloak rejection escaped as a raw
   exception and surfaced as `500 INTERNAL_ERROR`. An IdP refusing a code is an authentication
   failure, not a server fault — now `OIDC_TOKEN_EXCHANGE_FAILED` (400).

The logging change then immediately earned its keep by exposing a third defect it had been
hiding: `MissingRequestHeaderException` was unmapped, so a caller omitting a required trust
header got `500` instead of `400`. Fixed.

Also repaired six BFF test classes left uncompilable by the CP1–3 merge (duplicate
`OidcSessionService` `@MockBean`), which had made `mvn package` fail at `testCompile` — the module
could not be built at all, which is how the deployed image came to be so far behind.

## Proof

| Check | Result |
|---|---|
| Browser proof, direct-BFF routing | **12/12**, twice, 0 unhandled exceptions |
| Browser proof, **through Envoy** | **12/12** |
| Live Envoy route table | 4 routes, **60 client trust headers stripped** (was 0) |
| Estate after flip | BFF `200`, UI `200`, Keycloak `200`, no unhealthy pods |

The 12 assertions include: PKCE login establishes a session through the BFF callback; the session
cookie is opaque and never a JWT; no token material in browser storage; a cookie-authenticated
mutation without the CSRF header is rejected; logout fails closed; and a replayed callback never
re-establishes a session.

## What Stage 1 does and does not give

**Does:** every client-supplied trust header on the four routes is now stripped at the edge — 60
of them, up from 0. Envoy is on the live path and proven under real browser traffic.

**Does not:** `extAuthz` remains **false**, so there is still no PDP gate at the edge, and the six
identity headers stay unstripped by design (their strip is gated on the regenerator being
present — see `HEADER_CONTAINMENT.md`). Stage 2 is the gate, and it waits on OPA shadow parity.
