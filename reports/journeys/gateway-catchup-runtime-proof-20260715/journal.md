# Gateway UI catch-up — runtime proof (2026-07-15)

Phase E of the gateway hardening follow-ups: the strongest proof short of a deploy for the
"gateway UI catch-up" flows (the BE-1/BE-2/BE-3 backend surfaces that back the new dispatcher
console, the citizen SOS tracker, the public health-info search, and the R1 contact-OTP register).

Branch: `claude/gateway-catchup-runtime-proof` (worktree, based on `claude/gateway-be-additions`,
which carries the BE-1/2/3 endpoints under test — those are NOT yet merged into the canonical
`…-Yypyl` branch, so this proof is stacked on the branch that owns the code).

Boots **daidzai + guidance + experience-bff** HEAD jars (built from this branch) against scratch
postgres/redis on UNIQUE ports (containers `gw-e-rig-*`, pg 15833, redis 16799, services daidzai
28692 / guidance 28660 / bff 28760). Cached images only; **Kafka intentionally absent** (outbox
sends fail-and-retry, non-fatal — lazy KafkaTemplate). No external deps.

## Result: 18/18 PASS

Every UI-backing HTTP call is driven **through the experience-bff**; the daidzai/guidance SoR is
read back with `psql` for the state-transition proofs.

### J1 — Dispatcher callback console (BE-1, backs `/work/daidzai/verify-callbacks`)
| # | Proof | Lane | Result |
|---|-------|------|--------|
| J1.1 | anonymous `POST /internal/v1/public/gateway/sos` → 202 + `requestReference`; psql: `dai_emergency_request.status = AWAITING_CALLBACK` | BFF public | PASS |
| J1.2 | `GET /internal/v1/daidzai/requests?status=AWAITING_CALLBACK` lists the request (the exact worklist the console renders) | **through BFF** | PASS |
| J1.3 | triage BEFORE verify → **409 CALLBACK_VERIFICATION_REQUIRED** (PD-3 dispatch gate holds; side-effect-free) | daidzai SoR¹ | PASS |
| J1.4 | `POST /internal/v1/daidzai/requests/{id}/verify-callback` → 200; psql: `status→RECEIVED`, `callback_verified=true` | **through BFF** | PASS |
| J1.5 | request drops off the `AWAITING_CALLBACK` worklist | through BFF | PASS |
| J1.6 | triage AFTER verify → **201 TRIAGED** (the console action released the gate) | through BFF | PASS |

### J2 — Public SOS status-by-reference (BE-2, backs `/welcome/emergency/track`)
| # | Proof | Result |
|---|-------|--------|
| J2.1 | `GET /internal/v1/public/gateway/sos/{reference}` → 200 carrying **EXACTLY** the five allow-listed keys `requestReference, status, stage, callbackPending, createdAt` (extra_keys `[]`, missing_required `[]`) and **NONE** of the banned substrings `callbacknumber / +263 / 0771 / description / market / location / subject / hid- / lat / lng` (PII_LEAKS `[]`) — even though the underlying row holds a callback number, description, location, and subject | PASS |
| J2.2 | unknown reference → **404** (uniform, no existence oracle) | PASS |
| J2.3 | after the console verify (J1.4), the same public read flips to `RECEIVED` + `callbackPending=false` — the release is reflected to the anonymous citizen tracker | PASS |

### J3 — Public health-info text search (BE-3, backs the PublicHealthInfo search box)
| # | Proof | Result |
|---|-------|--------|
| J3.0 | seed 3 PUBLISHED `national-spine` articles (idempotent: prior rows cleared first) | PASS |
| J3.1 | `GET …/guidance/education?q=malaria` returns the ONE matching article, allow-listed topic fields only (`id/title/summary/category/domain`) | PASS |
| J3.2 | `?q=zzz-no-such-topic` returns 0 rows (genuine search, not a browse-list echo) | PASS |
| J3.3 | `?category=immunisation` (no `q`) returns the ONE immunisation article — the browse lane is intact alongside the new search precedence | PASS |

### J4 — R1 contact-OTP register (backs `/auth/register/contact`)
Auth-lane approach: the full REGISTER → Keycloak user → auto-login token → `CONTACT_VERIFIED`
attestation hop was already proven **live** on this same codebase in
`reports/journeys/gateway-w1-runtime-proof-20260712/` (needs Keycloak + identity-assurance +
notification, which that rig stood up). This rig boots only daidzai+guidance+bff, so J4 proves the
**NEW BFF surface at the contract level, live** — the two contact-OTP endpoints are mounted on the
BFF public auth lane and respond correctly, with the verify path consulting the **real Redis OTP
store**:
| # | Proof | Result |
|---|-------|--------|
| J4.1 | `POST /internal/v1/auth/contact/otp/request` blank value → 400 VALIDATION (endpoint reachable through the BFF public auth lane) | PASS |
| J4.2 | `…/otp/request` malformed phone → 400 VALIDATION (rejected by the shared R1 normalizer before any downstream send) | PASS |
| J4.3 | `…/otp/verify` purpose=REGISTER, no prior OTP → **400 OTP_EXPIRED** (the verify genuinely reads the Redis OTP store) | PASS |
| J4.4 | `…/otp/verify` bad purpose → 400 VALIDATION ("purpose must be REGISTER or ATTACH") | PASS |
| J4.5 | `…/otp/verify` missing code → 400 VALIDATION | PASS |

## Auth-lane approach (why through-BFF is genuine here)

The BE rig (`gateway-be-runtime-proof-20260715`) could only reach the **public** lanes through the
BFF; its authenticated `/internal/v1/daidzai/**` lane returned 401 because a lazy `JwtDecoder` was
present. This rig boots the **experience-bff fully open**:

- `SPRING_AUTOCONFIGURE_EXCLUDE=…OAuth2ResourceServerAutoConfiguration` → **no `JwtDecoder` bean**,
- `IMPILO_SECURITY_ALLOW_ANONYMOUS=true` → `SecurityConfig` takes its `anyRequest().permitAll()`
  branch,
- `KEYCLOAK_URL=http://127.0.0.1:1` → `serviceAccountBearer()`/ROPC fail fast to null, so the S2S
  hop stays header-only (same as the BE rig).

So **both** the public gateway lanes AND the authenticated dispatcher lane are reachable through the
BFF in-rig — J1.2/J1.4/J1.5/J1.6 are proven **through the BFF** (stronger than the BE rig, which
had to hit the daidzai SoR directly for BE-1). The thin BFF proxies are additionally covered by
`DaidzaiControllerTest` (`verifyCallback_delegatesAndReturns200`, `listRequests_delegatesWithStatus`).

The companion filter still requires the 4 mandatory v1.1 headers (`X-Tenant-ID / X-Pod-ID /
X-Request-ID / X-Correlation-ID`, plus `Idempotency-Key` on POST) on EVERY inbound request — "public"
means no JWT/actor, not header-free (the live edge synthesizes them). The journeys send them with
fresh ids per call, using bash **arrays** to preserve the quoting of space-bearing header values.

¹ **J1.3 gate proof is taken against the daidzai SoR** (the gate lives in `EmergencyService`), not
through the BFF — see the honest finding below.

## Rig-caught issues (harness, not product)
1. **guidance DB create race** — the official postgres image reports `pg_isready` on a temporary
   init server before the real server restarts, so a single `CREATE DATABASE guidance` can be lost.
   `rig-boot.sh` uses a create-and-verify retry loop (inherited from the BE rig).
2. **header-quoting bug in the first journeys draft** — emitting `-H "…: value"` via `echo`+command
   substitution word-split the space-bearing header values (curl then treated `national-spine`, the
   tenant UUID, etc. as extra URLs → "could not resolve host"). Fixed by building the header sets as
   bash arrays and expanding `"${ARR[@]}"`.
3. **non-idempotent J3 seed** — a second run doubled the seeded articles (2 malaria hits instead of
   1), failing the exact-count assertions. Fixed by clearing `national-spine` rows before seeding.

No product **boot-blockers** were found; all three service jars boot clean from HEAD.

## Honest findings (out of scope of BE-1/2/3 — journalled, not fixed)
- **BFF daidzai triage proxy swallows downstream 4xx into 500.** `POST /internal/v1/daidzai/
  requests/{id}/triage` through the BFF returns **HTTP 500 `INTERNAL_ERROR`** when daidzai returns
  the 409 `CALLBACK_VERIFICATION_REQUIRED` gate (evidence `j1-3-triage-gated-bff.json`). The gate
  itself is real and enforced in daidzai (`j1-3-triage-gated.json` = 409 at the SoR). Cause:
  `DaidzaiServiceClient.triageRequest` → the shared `post()` helper does not map
  `HttpClientErrorException`, so the BFF global handler renders a generic 500. Effect: the console
  would show a generic error instead of "callback verification required" for a gated triage. This
  is a pre-existing BFF proxy behaviour, not part of the BE-1/2/3 catch-up surfaces, so it is
  recorded here rather than fixed in this proof pass. (The BE-1 verify-callback proxy — the actual
  thing under test — maps cleanly and is proven live end-to-end in J1.4.)

## Reproduce
```
bash rig-boot.sh      # boots infra + daidzai/guidance/bff jars (idempotent; trap-cleanup separate)
bash journeys.sh      # 18 checks; writes j*-* evidence + run-output.txt
bash rig-cleanup.sh   # removes gw-e-rig-* containers + service ports 28692/28660/28760 only
```
Jars must be built first from this branch: `cd services && mvn -o -pl daidzai-service,guidance-service,experience-bff -DskipTests package`.
