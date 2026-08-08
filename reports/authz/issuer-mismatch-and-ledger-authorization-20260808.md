# Three gaps closed, and one recorded belief corrected

**Date:** 2026-08-08 · **Namespace:** `impilo-full-preview` · **Branch:** `phase0/e-probe-sweep`

## 1. 36 services rejected every real token 🔴 → closed

costa's issuer defect was not a one-off. Probing all 308 write endpoints **with a real bearer** and
classifying by `WWW-Authenticate`:

| | before | after |
|---|---|---|
| services failing token decode | **36** | **0** |
| endpoints unblocked | — | **108** |
| `401` across the sweep | 109 | **0** |

One identical root cause. Every one carries the same `application.yml` line —
`issuer-uri: ${KEYCLOAK_URL:...}/realms/${KEYCLOAK_REALM:impilo}` — resolving the issuer to the
**internal** Keycloak URL while tokens carry the **public** `iss`. The JWKS half is right, so every
pod starts clean and the fault appears only on a request that presents a bearer.

**That is why it survived: an unauthenticated probe sees 401 and reads it as enforcement.** The
entire Phase 0 E sweep scored these 36 green. They were not enforcing — they were *unreachable*.

**The fix was not in the chart.** `values-full-preview-runtime.generated.yaml` already carried the
correct pair for 99 services and the render was correct all along; the **live pods predated it**.
This was chart-vs-live divergence, found by rendering and diffing against live env rather than
assuming the chart described the estate. A guarded default was added to `microservice.yaml` as a
backstop — render-verified, with a negative control showing it accounts for exactly one deployment,
which is how the redundancy was *measured* rather than assumed.

> **Honest consequence:** 19 of the unblocked endpoints now return 201 and 8 return 200. They were
> never protected by this — they were broken. Fixing authentication makes their authorization gap
> **real rather than theoretical** across 36 services. This enlarges the P2/P3 work; it does not
> close it.

## 2. Nine of twelve national-ledger writes had no authorization 🔴 → closed

`general-ledger-service`. The register named `GlJournalsController` — **stale again**: journals were
already gated and were the only three of twelve that were. The other nine had nothing:

- chart of accounts (create, seed, amend)
- budgets and refresh-actuals
- **open, close and year-end** an accounting period
- trial-balance snapshot

Closing a period and running year-end decide what the national ledger says happened in a financial
year. Unlike costa's `/internal/v1/finance/**`, these are **not** behind `FederationIdentityFilter`
— GL does not have `federation-connector` on its classpath at all, so the `/internal/v1/` prefix
bought it nothing. Checked, not assumed by symmetry with costa.

Live proof, authenticated, varying only `X-Actor-Type`:

| Endpoint | CITIZEN | CAREGIVER | PROVIDER | OPERATOR | SYSTEM |
|---|---|---|---|---|---|
| `periods/year-end` | **403** | **403** | **403** | 400 | 400 |
| `accounts` | **403** | **403** | **403** | 500 | 500 |
| `budgets` | **403** | — | — | 500 | — |
| `trial-balance/snapshot` | **403** | — | — | 400 | — |

400/500 mean the gate passed and the handler rejected an empty probe body.

## 3. Correction: the duty **is** on the wire — for user tokens

Recorded as *"no duty reaches a service, so any OPERATOR can approve any waiver"*. That was measured
on a **workload** token, which has no user and therefore no roles **by construction**. Checked
properly against Keycloak:

- Realm users **do** hold realm roles — `FINANCE` (2), `SYSTEM_ADMIN` (3), `ADMIN` (4),
  `FACILITY_ADMIN` (3), `NATIONAL_ADMINISTRATOR` (1); 44 users hold some role.
- `experience-ui`, `impilo-mobile-citizen`, `impilo-mobile-provider` and `impilo-backend` each carry
  an active `oidc-usermodel-realm-role-mapper` writing `realm_access.roles` into the **access
  token** (`access.token.claim = true`).
- The BFF forwards the inbound user token downstream.

So `ActorTypeGuard`'s statement is precisely true as written — *no role header* reaches a service —
but the **JWT is a different channel, and it does carry roles** for user-originated calls.

**Why this matters more than a footnote:** `X-Actor-Type` is a *header*. Any in-cluster caller can
set it. I proved that during this work — a workload token with `X-Actor-Type: OPERATOR` produced
**19 × 201** across the estate. A realm role in the JWT is *signed* and cannot be forged. Enforcing
duty on the token is therefore strictly stronger than the actor-type gate, and is **possible today**
for user-originated calls rather than only after P3.

**Not built here, deliberately.** Enforcing a finance role on money decisions without a census of
who legitimately holds one is exactly the mistake recorded against P1 (*"15% census, no duty token —
do NOT enforce on it"*). The estate's own discipline is shadow-then-enforce. The correct next step
is a shadow evaluation logging what *would* be refused, then enforcement once the census holds.

## Still open

- **Duty enforcement itself** — mechanism now known to be available; needs the shadow pass above.
- **Read authorization** in costa and GL is untouched.
- **`matcher-engine`** remains contained by NetworkPolicy rather than authenticated.
- The 36 newly-reachable services have no service-side duty check — see the consequence note in §1.
