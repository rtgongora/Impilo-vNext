# P1 Shadow Evidence Report

**Estate:** `impilo-full-preview` · **Commit:** `921b60f54` · **Date:** 2026-08-07
**Enforcement changed:** none. **Production:** untouched. **Flag:** on in preview only.

---

## 1. The headline

**The PDP has now seen a browser user's roles for the first time.** Before this run, `roles` was
empty for every signed-in human, and 489 of 525 active ALLOW rules (93%) require one.

Measured, across four principals driven through the real sign-in page in a real browser:

| Principal | Roles the PDP resolved |
|---|---|
| `dr.mapfumo` | `CLINICIAN` |
| `clerk.dube` | `SUPPORT_AGENT` |
| `admin.harare` | `FACILITY_ADMIN` |
| `citizen.moyo` | `CITIZEN` |

**No route observed would break under enforcement.**

| Delta | Count |
|---|---|
| `PERMIT_TO_DENY` — would break live traffic | **0** |
| `ACTOR_TYPE_CHANGE` | 23 |
| `NONE` | 5 |
| `DENY_TO_PERMIT` | **structurally unobservable** — see §5 |

Every one of the 28 evaluated requests returned `ALLOW` in shadow, exactly as it did in production.

---

## 2. The finding that matters more than the deltas

Getting to those 28 rows required fixing two defects that were invisible until something actually
tried to use the path. Both are now fixed and deployed.

### 2a. The PDP could not validate *any* bearer

`KeycloakAdapter` derived its JWKS URL from `issuer-uri`, one value doing two incompatible jobs: it
must equal the token's `iss` claim — which Keycloak mints as the **public** realm URL — and it was
also used to **fetch** keys, which must be reachable from inside the cluster. Measured from a pod:

```
https://impilo.mohcc.gov.zw/realms/impilo/…/certs  ->  000   (public ingress, pods do not hairpin)
http://keycloak:8080/realms/impilo/…/certs         ->  200
```

JWKS initialisation threw, `jwtProcessor` stayed null, and every presented bearer was rejected.

**This makes the headline gap worse, not better.** The known finding was *"489 of 525 rules cannot
match a signed-in human because no bearer reaches the PDP."* It is now measured that until
`impilo.trust.keycloak.jwks-uri` is set, **a bearer that did reach the PDP could not be validated
either.** Any plan that assumed attaching the bearer was sufficient rested on an untested
assumption.

It survived unnoticed because the failure logged at `WARN`, without the URL, describing itself as
*"deferred"* — so an estate rejecting every bearer read exactly like an estate where no bearer ever
arrived. It is now `ERROR`, names the endpoint, and states the consequence.

### 2b. Shadow could starve the connection pool it promised never to contend for

The first live run produced:

```
HikariPool-1 - Connection is not available, request timed out after 3000ms
(total=3, active=3, idle=0, waiting=14)
```

plus eight `CannotCreateTransactionException`. `maxConcurrent` defaulted to 4; this estate pins
`SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=3`, overriding the chart's 30. The semaphore bounded
shadow *evaluations* and said nothing about their connection footprint — so the promise the design
rests on, *production authorization wins every contention*, was false while appearing to be enforced
by a mechanism named for it.

**The flag was turned off in the estate the moment this was measured**, and stayed off until
`ShadowCapacityPolicy` was built, tested and deployed. It sizes shadow against the pool it actually
competes for, and refuses to run at all when there is no headroom. Second run: **zero starvation
events.**

---

## 3. What the deltas actually say

All 23 `ACTOR_TYPE_CHANGE` rows have the same shape:

| | production | shadow (proven) |
|---|---|---|
| `actor_type` | `OPERATOR` | `CITIZEN` |
| persona | — | `MY_LIFE` |

**Production is asserting `OPERATOR` on a header nothing proves.** The projection refuses to derive
a persona from anything unproven, so with no duty token minted it answers `MY_LIFE`/`CITIZEN` — the
narrowest answer, not the most flattering one.

By route class, all `ALLOW` in both: `other` (10), `shell-chrome` (7), `identity` (6), `mobile` (4),
`facility-registry` (1).

---

## 4. Coverage — read this before quoting §1

| Outcome | Count | Meaning |
|---|---|---|
| `OK` | **28** | evaluated; the dataset above |
| `DROPPED_CAPACITY` | **155** | shadow refused a permit so production kept its connections |
| `BEARER_UNRESOLVED` | 90 | all from runs **before** the JWKS fix; none since |

**The census is ~15% complete.** That is a deliberate trade: with the estate's pool pinned at 3,
`ShadowCapacityPolicy` allows shadow one concurrent evaluation, and the rest are dropped rather than
queued against production authorization. The drops are *counted*, not silent — a dataset that
quietly omitted the busiest moments would look complete and be wrong in the direction that matters.

**To widen it:** raise the PDP's Hikari pool. Postgres reports `max_connections=600` with 129 in
use, so there is ample headroom, and a pool of 3 is tight for a service on the request path
regardless of shadow. That is an estate config change and is left as a separate decision.

**Latency.** PDP evaluation p50 **33 ms**, p95 531 ms, max 897 ms (n=28). BFF-side inline canary
round trip p50 **159 ms**, p95 559 ms (n=2 — too few to quote as a percentile; reported as a range).
Observer health: 80 eligible, 79 dispatched, 1 transport error, 0 queue drops.

---

## 5. What this evidence structurally cannot say

- **`DENY_TO_PERMIT` is unobservable.** Denials are returned by Envoy and never reach the BFF, so
  the observer only ever sees allowed traffic. This answers *what would break*, never *what would
  newly be permitted*.
- **No work persona was proven.** Every row projects `MY_LIFE`, including the clinical-day journey.
  No duty token was minted in any session, and `varapi.provider_affiliations` is empty table-wide.
  **This has measured the absence of duty tokens, not the absence of work-persona deltas.** Rules
  keyed on a proven `WorkMode` remain entirely unexercised.
- **Sample size.** 28 evaluations over four principals and five route classes. Enough to say *no
  observed route breaks*; **not** enough to say *no route breaks*.
- **Caregiver / proxy: NOT COVERED.** No end-to-end proxy persona exists in this estate.
  `PERSONA_PROXY` is unreachable today. It was reported, not simulated.

---

## 6. Recommendation

**Do not enable enforcement on this evidence.** Not because it looks bad — it looks good — but
because 15% coverage with zero work personas cannot support the claim that would be made from it.

In order:

1. Set `impilo.trust.keycloak.jwks-uri` **wherever this PDP runs**, including production. §2a is not
   a preview quirk; it is a split-horizon deployment that one config value could not express.
2. Raise the PDP connection pool, then re-run for a census rather than a sample.
3. Mint a duty token in at least one journey so work personas are exercised.
4. Only then revisit enforcement, and separately for `DENY_TO_PERMIT`, which needs an observation
   point at Envoy rather than behind it.

---

## Appendix — how to reproduce

```bash
cd ui/one-ui-shell
PLAYWRIGHT_BASE_URL=https://impilo.mohcc.gov.zw \
PLAYWRIGHT_HOST_RESOLVER_RULES="MAP impilo.mohcc.gov.zw 127.0.0.1" \
PLAYWRIGHT_SKIP_WEBSERVER=1 PREVIEW_SANDBOX_E2E=1 \
npx playwright test e2e/journeys/start-menu-discoverability.journey.spec.ts --project=journeys
```

The host-resolver rule is required: the VM does not hairpin to its own public ingress. Queries for
reading the log are in [`docs/runbooks/p1-authorization-shadow-evidence.md`](../runbooks/p1-authorization-shadow-evidence.md).
