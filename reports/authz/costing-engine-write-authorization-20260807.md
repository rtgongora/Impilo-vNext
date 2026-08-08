# Closing the costing-engine authorization gap

**Date:** 2026-08-07/08 · **Namespace:** `impilo-full-preview` · **Branch:** `phase0/e-probe-sweep`

The P0 register named `costing-engine-service` as having consequential writes with no
authorization. This closes it, and corrects two things the register said.

## Two corrections to the register, both by measurement

**1. `WaiverController` was already fixed.** The register lists it as
`@PreAuthorize:0 actorType:0 requireX:0`. Commit `1673bc2c6` gated all four waiver operations to
`ActorTypeGuard.BACK_OFFICE_WRITERS` before this work started. It was the *only* guarded controller
in the service — so the register named the one place that had been dealt with.

**2. "81 unguarded writes" overstates it.** That was my own first count, from grepping the
controllers for `@PreAuthorize` / `ActorTypeGuard` / `requireX`. It missed a filter. Of 85 write
mappings:

| Prefix | Writes | Prior state |
|---|---|---|
| `/internal/v1/finance/**` | 41 | **Already behind `FederationIdentityFilter`** — requires a JWT with the `federation` audience. Proven live: `FEDERATION_IDENTITY_INVALID`. |
| `/costa/v1/**` | 28 | No authorization, except the 4 waiver methods. |
| `/api/costa/**` | 15 | No authorization. |
| probe | 1 | Exempt. |

So the genuinely unguarded consequential surface was **~39 write mappings**, not 81. A
controller-level grep cannot see a servlet filter; the number is corrected here rather than left
standing because it flattered the finding.

The federation filter is an **identity** gate, not a **duty** gate — it cannot tell a citizen from a
finance officer. The interceptor added here does, and applies to those paths too.

## Why not `@PreAuthorize`, which is what the register asked for

The obvious instrument does not work here, and it fails *silently*:

- **`@EnableMethodSecurity` is not enabled in this service.** Only 5 of ~103 services enable it. The
  annotations would be inert — present, reviewed, enforcing nothing.
- **Even switched on, `hasAnyRole(...)` would refuse everyone.** Measured against the live realm:
  the workload token experience-bff mints (`client_credentials` on `impilo-bff`) carries
  `realm_access: null`, `resource_access: {}` and scopes only. **No role reaches this service.**

`ActorTypeGuard`'s javadoc had already reached this conclusion: there is no role header in the trust
contract and none in Envoy's `allowed_upstream_headers`, so *"no service-side guard can express 'a
finance officer, but not a ward clerk' today"*, and the honest ceiling is `BACK_OFFICE_WRITERS`.
This uses the estate's own conclusion rather than reaching past it for an annotation that would look
like authorization and be none. The finer distinction stays recorded in `Duty.intendedRoles()` —
declared, visibly unenforced — as input to P3 workload identity.

## What is enforced

A deny-by-default `HandlerInterceptor` over every write, with the duty decided in one map:

- **`BACK_OFFICE_WRITERS`** `{SYSTEM, SERVICE, OPERATOR}` — default, and everything that *decides*
  money: approve, finalise, refund, write-off, budgets, period close.
- **`CHARGE_CAPTURE`** `{SYSTEM, SERVICE, OPERATOR, PROVIDER}` — new, for *capturing* care.

**Both exclude `CITIZEN` and `CAREGIVER`** — the exposure P0 named.

The wider set is not a convenience. `POST /costa/v1/bills/draft` is called by
`EncounterController`, `MobileEncounterController`, `MobileProviderExtendedController`,
`MobileDischargeController` and `TeleconsultController` — all clinician-facing — and the BFF forwards
`X-Actor-Type` unchanged, so a clinician arrives as `PROVIDER`. Gating that to back-office would 403
every bill raised at the bedside. Each capture entry was justified by finding a clinician-facing
caller, not by reading the path name.

Reads are untouched, and said so rather than silently skipped.

## Live proof

Authenticated with a real workload token, varying only `X-Actor-Type`:

| Endpoint | CITIZEN | CAREGIVER | PROVIDER | OPERATOR | SYSTEM |
|---|---|---|---|---|---|
| `POST /costa/v1/waivers` (decision) | **403** | **403** | **403** | 400 | 400 |
| `POST /costa/v1/bills/draft` (capture) | **403** | **403** | 500 | 500 | — |

400 and 500 mean *the gate passed* and the handler rejected an empty probe body — the outcomes that
matter are the 403s and the PROVIDER split between the two rows.

## Three defects this found, all only visible by testing live

**1. The guard masked real failures.** Spring re-runs the interceptor chain on the `ERROR` dispatch,
by which point `TrustContextFilter` has cleared its thread-local. The guard saw no actor type,
refused, and rewrote the real outcome as a bare 403. Caught when a `PROVIDER` bill draft **passed**
the gate, reached `BillService.createDraft`, failed a `facility_id` constraint — and came back 403
while the log showed the insert. Now guards `DispatcherType.REQUEST` only.

**2. costa rejected every real token, and had before this branch.** Its `application.yml` derives
both the JWKS route and the issuer from `${KEYCLOAK_URL}`, so the issuer resolved to the *internal*
`http://keycloak:8080/realms/impilo` while tokens carry the *public* `iss`:
`401 invalid_token — "The iss claim is not valid"`. The JWKS half was right, so the pod started
clean and the fault appeared only on a request presenting a bearer. **No authenticated caller could
reach any costa endpoint.** An unauthenticated probe sees 401 and reads it as enforcement — this is
only visible if you mint a real token and confirm the instrument gets *past* authentication before
concluding anything about authorization.

**3. Teleconsult auto-billing would have broken silently.** `triggerTeleconsultBilling` submits,
approves and finalises a bill in the completing clinician's request thread, inside a catch that only
logs. Gating those decisions would have 403'd it and left bills unfinalised behind a warning nobody
reads. The draft stays attributed to the clinician; the approval sequence now asserts `SYSTEM`,
which is also the more honest audit record for a step with no human author.

## What is still open

- **Actor type is not a duty.** Any `OPERATOR` can approve any waiver. Separating finance from other
  back-office staff needs a role or duty on the wire — P3 workload identity — and is recorded in
  `intendedRoles`, not faked.
- **Read authorization** in this service is untouched.
- The same pattern almost certainly applies to `general-ledger-service`, which the P0 register also
  names and which was not in this scope.
