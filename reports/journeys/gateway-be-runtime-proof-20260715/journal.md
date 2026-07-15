# Gateway BE-additions — runtime proof (2026-07-15)

Branch: `claude/gateway-be-additions`. Boots daidzai + guidance + experience-bff HEAD jars against
scratch postgres/redis on unique ports (containers `gw-be-rig-*`, pg 15933, redis 16699, services
daidzai 28492 / guidance 28560 / bff 28562). Cached images only; Kafka intentionally absent
(outbox sends fail-and-retry, non-fatal). All three services boot with oauth disabled so the rig
proves the BE additions' functional wiring — not estate PDP authz (proven in each service's own
suite; public-lane permitAll genuineness is covered by the w2c rig + `check-public-lane.sh`).

## Result: 10/10 PASS

### CHECK 1 — SOS worklist + public status + callback verify (BE-1 + BE-2)
| # | Proof | Result |
|---|---|---|
| 1a | anonymous `POST /internal/v1/public/gateway/sos` (BFF public lane) → 202 + `requestReference` | PASS |
| 1b | BE-1 `GET /internal/v1/daidzai/requests?status=AWAITING_CALLBACK` (daidzai SoR) lists the request | PASS |
| 1c | BE-2 `GET /internal/v1/public/gateway/sos/{reference}` (BFF public lane) → PII-free status | PASS |
| 1d | `POST /internal/v1/daidzai/requests/{id}/verify-callback` flips status→RECEIVED, callbackVerified=true | PASS |
| 1e | worklist no longer lists the verified request | PASS |
| 1f | public status now RECEIVED + callbackPending=false | PASS |
| 1g | unknown reference → 404 (no existence oracle) | PASS |

**PII-free assertion (1c, `c1c-assert.txt`):** the status body carried EXACTLY the five allow-listed
keys `requestReference, status, stage, callbackPending, createdAt` (extra_keys: []) and NONE of the
banned substrings `callbacknumber / +263 / 0771 / description / market / location / subject / hid- /
lat / lng` (PII_LEAKS: []) — even though the underlying request row holds a callback number,
description, location, and subject label.

### CHECK 2 — public health-info text search (BE-3)
| # | Proof | Result |
|---|---|---|
| 2a | seed a PUBLISHED `national-spine` article ("Malaria warning signs") | PASS |
| 2b | `GET /internal/v1/public/gateway/guidance/education?q=malaria` (BFF) returns it, allow-listed fields only | PASS |
| 2c | `?q=zzz-no-such-topic` returns 0 rows (genuine search, not echo) | PASS |

## Rig-caught issues (harness, not product)
1. **guidance DB create race** — the official postgres image reports `pg_isready` on a temporary
   init server before the real server restarts, so a single `CREATE DATABASE guidance` was lost and
   guidance failed flyway on first boot ("database guidance does not exist"). Fixed in `rig-boot.sh`
   with a create-and-verify retry loop.
2. **mandatory v1.1 headers on the public lane** — the BFF companion filter requires
   `X-Tenant-ID / X-Pod-ID / X-Request-ID / X-Correlation-ID` (and `Idempotency-Key` on POST) on
   EVERY inbound request; "public" means no JWT/actor, not header-free (the live edge synthesizes
   them). The journeys script sends them (fresh ids per call).
3. **BFF authenticated-route reachability** — the BFF's permitAll-everything branch requires a null
   JwtDecoder (no issuer configured), not a disable flag; with a lazy decoder the authenticated
   `/internal/v1/daidzai/**` lane returns 401 in the rig. BE-1's list/verify are therefore proven
   directly against the daidzai SoR (oauth-disabled); the thin BFF proxy for both is covered by
   `DaidzaiControllerTest` (verifyCallback + listRequests delegate + status codes).

No product boot-blockers were found; all three service jars boot clean from HEAD.

## Reproduce
```
bash rig-boot.sh          # boots infra + 3 jars (idempotent; trap-cleanup separate)
bash gateway-be-journeys.sh   # 10 checks, writes c*.json evidence + run-output.txt
bash rig-cleanup.sh       # removes gw-be-rig-* containers + service ports only
```
