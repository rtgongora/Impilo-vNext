# Phase 0 · H — Nhume's Msika Flow write-backs: identity and purpose

**Measured 2026-08-08** against `impilo-full-preview`, branched from `main` at `de77b4d93`.

## Summary

The finding as raised was that Nhume declares `X-Purpose-Of-Use: SYSTEM` without a workload
token, that the PDP now refuses exactly that, and that the write-back would therefore fail
closed on the next real delivery.

**The refusal cannot happen on this path, because the PDP is not on this path.** The real
defect at the same two call sites is the opposite one: both callback endpoints executed their
handlers with **no credential at all**.

## What was measured

### 1. The write-backs never traverse Envoy

Envoy in this estate is a single north-south deployment (`envoy-55b8d45db`), whose listener
routes `/api/v1/*` prefixes to service clusters. Neither `nhume-service` nor
`msika-flow-service` has a sidecar — each pod runs exactly one container. Nhume calls
`http://msika-flow-service:8100/internal/v1/msika-flow/...` pod-to-pod, so `ext_authz` is
never consulted and `AuthorizeController` never sees the request.

The `X-Purpose-Of-Use` header is therefore inert on this path today. `msika-flow` reads
`X-Actor-ID` / `X-Actor-Type` / `X-Correlation-ID` for attribution and ignores the purpose.

### 2. Both endpoints were reachable unauthenticated

Probed from inside the cluster, with a non-existent id so that nothing was mutated:

| Probe | Result |
|---|---|
| `POST .../selections/PROBE-NONEXISTENT-H/delivery-status`, no headers | `400 MISSING_REQUIRED_HEADER` |
| same, **with** v1.1 trust headers, **no bearer** | `422 SELECTION_DELIVERY_MISMATCH` — the handler ran |
| `POST .../orders/PROBE-NONEXISTENT-H/dispatch-status`, headers, no bearer | `400 "Order not found"` — the handler ran |
| **negative control**: adjacent `/internal/v1/...` path, same headers, no bearer | `401` |

The negative control is what makes this readable: the resource server was live and the probe
could observe a refusal, so the two callbacks were genuinely outside the gate rather than the
probe being blind. The first row is the standing trap — a `400` from the header filter is not
a `401`, and reading it as a refusal would have hidden this entirely.

Reachable from any pod in the namespace: `vito-service` and `tuso-service` both got through
to the same header gate, so there is no network isolation behind it either.

`SecurityConfig` justified the exemption as "authenticated at the Envoy ext_authz mesh edge".
There is no mesh edge. The premise was never true for this path.

**Consequence.** The order callback drives the order state machine (fast-forwarding through
`ACCEPTED → IN_PROGRESS → OUT_FOR_DELIVERY`) and writes a `NHUME_COURIER` chain-of-custody
record. The selection callback projects proof-of-delivery onto the selection and opens the
escrow / refund seams. The order callback has no cross-check at all — a valid order id is
sufficient. The selection callback fail-closes unless the reported delivery id matches the
stored `dispatch_ref`, which is a correlation check, not an authorization one.

### 3. A workload token would not have satisfied `isWorkloadSession` anyway

`AuthorizeController.isWorkloadSession` accepts a `SYSTEM`/`SERVICE` actor type from a
validated token, or the same as a role in `realm_access.roles`. A live token minted by the
only service running the reference implementation (`vashandi-workforce-service`) decodes to:

```
azp    : vashandi-workforce-service
sub    : 8171e231-…
scope  : profile email openid impilo-tenant
realm_access : ABSENT        resource_access : {}
```

No roles, and no actor-type claim. So the estate's s2s tokens satisfy **neither** branch.
Option (a) — "give Nhume a workload token so it can keep saying SYSTEM" — would additionally
have required a Keycloak realm change (a service role plus a mapper that actually emits it).
The in-code comment asserting that an s2s token "carries no `sub` and … arrives as a ROLE" is
contradicted by measurement: it carries a `sub` and no role.

## The decision

Not (a). Not plain (b) either. **Authenticate the caller; narrow the purpose to `OPERATIONS`.**

### Purpose: `OPERATIONS`

- **`SYSTEM`** is wrong twice over. In `PolicyEngine.evaluatePolicies` it returns
  `continueWith(null)` from *both* the no-rules and the no-matching-ALLOW-rule branches, and
  it short-circuits consent. It is the broadest code in the vocabulary. A courier reporting
  that a parcel arrived has no business holding it — which is precisely why it was locked down.
- **`CARE_COORDINATION`** is the tempting answer, because the enum's own javadoc lists
  "delivery write-backs" and `V049` names "the nhume write-back gateway". But that describes
  the sibling `trustHeaders` builder, which serves the OROS / MADI / PCT write-backs — moving
  specimens, blood orders and referrals. `CARE_COORDINATION` is HL7 ActReason COC, *a
  specialization of TREAT*, carrying the same visibility envelope as `TREATMENT`. Attaching it
  to a marketplace parcel callback that touches no patient and no clinical record would widen
  clinical visibility, not narrow it.
- **`OPERATIONS`** — "facility operations, scheduling, queue management" — is what these two
  endpoints actually drive, and is the narrowest honest fit. There is no `LOGISTICS` code;
  that was tried once and denied `INVALID_PURPOSE` at Step 2 for not being a `PurposeOfUse`
  constant.

Narrowing the purpose is **not** what makes these calls safe today, and this document should
not be read as claiming it is. It is what the decision log and any future rule will read if
this path is ever placed behind `ext_authz`.

### Identity

Nhume gains its own workload credential (`impilo.s2s.token`, the vashandi/madi/pct shape,
default **disabled**), attached by `WorkloadTokenInterceptor` — so a delegated human token,
when the write-back runs inside a sign-off request, is never overwritten by the stronger
service credential. A scheduled retry, which has no request context and previously sent
nothing at all, now authenticates as Nhume.

`msika-flow` stops exempting the two callbacks and pins them to the calling workload's own
client id, read from the validated JWT (`azp`, falling back to `client_id`) — never from a
header. "Authenticated" alone would have let any realm token, including a human's, report a
courier milestone.

## Verification

Every check below was proven by breaking what it guards and confirming red:

| Break | Went red |
|---|---|
| purpose back to `SYSTEM` | 2 of 6 (both purpose assertions) |
| workload-token interceptor removed | 2 of 6 (both `Authorization` assertions) |
| `permitAll` restored on both callbacks | 5 of 7 |
| callback URL patterns typo'd so they bind nothing | 3 of 7 — the wrong-caller and handler-reached cases, while the unauthenticated cases stayed green |

That last row is why the filter-chain test exists alongside the unit test: an unmatched
pattern falls through to `anyRequest().authenticated()`, which keeps refusing anonymous
callers — so the unauthenticated assertions still pass — while quietly admitting *any*
authenticated principal.

Suites: `nhume-service` **83/83**, `msika-flow-service` **302 run, 0 failures, 5 skipped**
(pre-existing skips).

`policy_decision_log` was **not** consulted as proof. The brief proposed querying it for
`deny_reason = 'PURPOSE_NOT_PERMITTED'`, but since these requests never reach the PDP, no row
would ever have been written for this path — the absence would have meant "never evaluated",
not "blocked".

## Rollout — not done here, and order matters

Not deployed. `tshepo-authz-service` is under a PO hold until Phase 0 closes, and this change
does not need it.

The two halves are **deploy-coupled**. `msika-flow` now refuses an uncredentialed callback, and
Nhume's `impilo.s2s.token.enabled` defaults to `false`. Deploying msika-flow first breaks the
write-back — fail-closed and detectable, and the path has fired zero times in seven days, but
it is still a break. Required order:

1. Create a `nhume-service` Keycloak client and set `IMPILO_S2S_CLIENT_ID` /
   `IMPILO_S2S_CLIENT_SECRET` / `IMPILO_S2S_TOKEN_ENABLED=true` on `nhume-service`. Note that
   the existing s2s clients were created out-of-band and are **not** in the committed
   `governed-realms.json`.
2. Deploy `nhume-service`; confirm outbound calls carry `Authorization`.
3. Deploy `msika-flow-service`.

No realm **role** is needed — the pin is on client id, which the estate's tokens already carry.

## Left open, deliberately

- `mushex-service`'s `/internal/v1/claims/*/appeal-resubmit` was cited as the precedent for
  this exemption and is likely to carry the same defect. Not probed, not fixed — outside this
  branch's blast radius, and it deserves its own measurement.
- `msika-flow`'s `/v1/internal/**` rule requires `SCOPE_internal`, which no observed estate
  token carries. Untouched.
