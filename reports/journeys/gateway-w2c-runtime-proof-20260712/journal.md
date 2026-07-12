# Gateway W2c runtime proof — `gateway-emergency-anon`

**Journey:** anonymous emergency SOS intake with the PD-3 callback dispatch gate.
**Date:** 2026-07-12 · **Result:** **PASS 7 / 7** (see `run-output.txt` + `c*.json` artifacts).

Workstream C of Wave 2, National Health Services Gateway. Proves the anonymous SOS write
end-to-end: capture → hold `AWAITING_CALLBACK` → dispatch refused → dispatcher verifies →
triage succeeds, plus the anonymous-write abuse controls.

## Rig topology

HEAD jars booted against scratch infra on unique ports so this can run alongside another rig.
Cached images only.

| Component | Container / process | Port | Notes |
|---|---|---|---|
| Postgres 16 | `gw2c-rig-pg` (postgres:16-alpine) | 15833 | daidzai DB; V001+V002 flyway applied |
| Redis 7 | `gw2c-rig-redis` (redis:7-alpine) | 16599 | BFF SOS rate-limit store |
| daidzai HEAD jar | java | 28392 | `IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true` |
| experience-bff HEAD jar | java | 28462 | RBAC active (lazy JwtDecoder, Keycloak unreachable) |

- **Kafka: none.** daidzai's `KafkaTemplate` is lazy — outbox rows just stay unpublished and
  the scheduled relay retries; boot is unaffected. No redpanda needed.
- **daidzai oauth disabled** for the rig: it proves the SOS flow + PD-3 gate, not daidzai's own
  PDP authz (covered by daidzai's own suite). The dispatcher `verify-callback`/`triage` calls
  carry full trust headers (tenant/pod/request/correlation/actor/idempotency), exercising the
  real authenticated-lane controller path.
- **BFF → daidzai** used the header-only S2S hop (`serviceAccountBearer()` is null with no
  Keycloak); daidzai accepted the synthesized SYSTEM identity.

Scripts: `rig-boot.sh` (infra + jars + health wait), `gateway-w2c-journeys.sh` (the 7 checks),
`rig-cleanup.sh` (tear down all `gw2c-rig-*` containers + rig jars).

## Checks

| # | Check | Result | Evidence |
|---|---|---|---|
| 1 | Anonymous SOS POST (no auth) + callback → **202** + reference | PASS | `c1-sos-response.json` |
| 1b | psql read-back: `status=AWAITING_CALLBACK`, `callback_verified=f`, `requester_type=PUBLIC_ANONYMOUS`, callback normalized to E.164 (`+263771234567`), `subject_identity_mode=ANONYMOUS`, `channel=PUBLIC_WEB` | PASS | `c1-readback.txt` |
| 2 | POST without `callbackNumber` → **400 VALIDATION**, row count unchanged | PASS | `c2-nocallback.json` |
| 3 | Triage the AWAITING_CALLBACK request → **409 CALLBACK_VERIFICATION_REQUIRED** (gate holds) | PASS | `c3-triage-gated.json` |
| 4 | Dispatcher `verify-callback` → **200** (`callbackVerified=true`, status→RECEIVED); then triage → **201** incident created | PASS | `c4-verify.json`, `c4-triage-ok.json` |
| 5 | Hammer public SOS from one IP → 5×202 then **429 + Retry-After: 600** at the per-IP threshold | PASS | `c5-hit-*.json`, `c5-hdr-6.txt` |
| 6 | 202 response body carries **no** internal service names | PASS | `c6-naming.json` |

## Abuse thresholds (as enforced)

- Per-IP: **5 / 600 s** fixed window (429 + `Retry-After`).
- Global: **60 / 60 s** fixed window.
- Callback: **REQUIRED**, normalized to E.164 (reuses `ContactOtpService.normalize`); blank/invalid → 400, never reaches daidzai.
- Body caps: description 2000, location 512 chars.
- Rate-limiter **fails open** on Redis error (life-safety) — unlike the OTP lane.

## Rig-caught boot-blockers

None in the service code. Two rig-harness corrections were needed (test-script only, not product):
1. The BFF companion filter requires the 4 mandatory v1.1 headers **and** an `Idempotency-Key`
   on POST. This is not a public-lane bug — the real web client (`apiClient`) always sends them;
   the "anonymous" guest is "no auth/actor", not "no platform headers". Journeys updated to mirror
   the real client.
2. daidzai rejects a reused `Idempotency-Key` with `IDENTITY_CONFLICT`; the dispatcher calls now
   mint a fresh key per call.

## Honest gaps

- **Dispatcher verify-callback UI is not built here.** `verify-callback` is an operator surface
  (dispatcher console / `/work/daidzai`), out of this workstream's scope — proven only via the API
  in this rig. The citizen-facing form and the anonymous intake lane are complete.
- **Callback is verified by a human dispatcher, not automated.** There is no SMS/voice OTP on the
  callback number — PD-3 is "a responder reaches the caller", a manual dispatcher action. An
  automated callback-OTP is a future option, not required by PD-3.
- **Rig auth shortcuts** (daidzai oauth disabled; BFF Keycloak unreachable so header-only S2S hop).
  Production BFF→daidzai carries the service-account bearer (already wired, resolved when Keycloak
  is reachable). The SecurityConfig RBAC / permitAll correctness is proven by the strict
  `check-public-lane.sh` guard + the BFF unit tests, and the RBAC path was active in this rig.
