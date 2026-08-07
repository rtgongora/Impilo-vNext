# Phase 0 · E — Estate-wide unauthenticated probe sweep

**Date:** 2026-08-07 · **Namespace:** `impilo-full-preview` · **Branch:** `phase0/e-probe-sweep`
**Gate condition #2 of 5:** *unauthenticated probes 401 across the estate.*

## Verdict — the gate condition is NOT met

| | count |
|---|---|
| Services enumerated from `docs/registry/services-registry.yaml` | **104** |
| …deployed in `impilo-full-preview` | **103** |
| …registered but NOT deployed | **1** (`tshepo-service`, the legacy monolith) |
| Write endpoints probed | **322** |
| Services probed | **103** |
| Services **refused** (401/403 on every endpoint) | **85** |
| Services with at least one **unknown** endpoint | **7** |
| Services with at least one genuinely **OPEN** write | **11** |
| Endpoints refused / unknown / OPEN | **290 / 20 / 12** |

**20 endpoints are `unknown`. They are not counted as protected.** Every one of them reached
application code without a credential; only input validation or a server error stopped the write,
never a guard.

## The instrument was proven before any result was trusted

A probe that cannot produce a 200 returns "refused" for everything and reads as a clean estate.
Before the sweep, from the probe pod, in-cluster:

| Control | Result |
|---|---|
| `GET tuso-service:8084/actuator/health` | **200** |
| `GET pct-service:8088/actuator/health` | **200** |
| `GET vito-service:8082/actuator/health` | **200** |
| `POST opa:8181/v1/data` (JSON body — the shape of the sweep) | **200** |
| `GET no-such-service-xyz:8080/…` (negative control) | **000** — unreachable is distinguishable from any real status |

The POST control matters more than the GETs: it proves the instrument can drive a **write-shaped**
request to a 2xx, so a "refused" verdict is about the target and not about the probe.

## Method

- **Enumerated from the registry**, not a hand list: 104 services, reconciled against
  `kubectl get deploy` (117 deployments — the 14 extra are infrastructure: envoy, kafka, keycloak,
  postgres, redis, minio, opa, orthanc, livekit, hapi-fhir, and 3 UI surfaces).
- **Probed in-cluster over cluster DNS**, from a pod inside the namespace. Envoy fronts only
  `experience-bff`; probing through the public ingress cannot reach ~100 services and would have
  produced a falsely clean result.
- **Write endpoints only** (`@PostMapping` / `@PutMapping` / `@PatchMapping` / `@DeleteMapping`
  extracted from the controllers — 4,371 discovered, 322 probed). `/actuator/health` being open
  proves nothing; the gate is about consequential writes.
- **Escalating well-formedness.** This is the core of the method — see below.

### A 400 is not a 401 — and the estate has *three* gates that produce one

The `audit-ledger` hole hid behind a `400 MISSING_REQUIRED_HEADER`. This sweep found the same shape
stacked three deep. Counting any non-200 as "protected" would have reported the estate clean:

| Pass | Request shape | Result |
|---|---|---|
| A | bare, no headers, no credential | 263 × 401, **36 × 400**, 4 × 000, 2 × 500, 2 × 403, 1 × 202 |
| B | bodies captured | the 400s split: `MISSING_REQUIRED_HEADER` vs. real validation errors |
| C | + well-formed v1.1 trust headers | a **second** gate appears: `IDEMPOTENCY_KEY_REQUIRED` |
| D | + `Idempotency-Key` | **10 × 201**, 16 × 400, 5 × 500, 5 × 403, 4 × 401 |

Only at pass D does the estate's real posture appear. **The 36 bare 400s were not refusals.**

### The 4 × `000` were a NetworkPolicy, not a guard

`workforce-governance-service` was unreachable from the probe pod — `cohort1-workforce-governance-ingress`
admits only `experience-bff` and `vashandi-workforce-service`. Re-probed from an admitted pod:
**401 on all four endpoints, bare and well-formed.** Refused. Had this been left as "000 = no
response = safe", it would have been an unmeasured service reported as protected.

*(NetworkPolicy is enforced in this cluster for the pods that have one — worth noting against the
prior "NetworkPolicy not enforced" reading.)*

## Genuinely open write endpoints, ranked by consequence

### 1. `jobs-service` — `POST /internal/v1/jobs` → **201, persisted** 🔴

The only probe in this sweep proven to write to a database with no credential.

```
HTTP/1.1 201
{"id":1,"tenantId":"…0001","name":"phase0e-probe-job","jobType":"PHASE0E_PROBE",…}
```

Confirmed in `jobs.job_definition` (`id 1`, `created_at 2026-08-07 15:11:24`) — **`id 1` means the
table was empty; this was the first row ever written there.** Highest consequence in the set: a job
definition is a scheduled-execution primitive. The probe row was created with `enabled: false` so it
could never fire, and **has been deleted** — the table is back to 0 rows.

### 2. `analytics-pipeline-service` — `POST /internal/v1/telemedicine/events` → **202 ACCEPTED** 🟠

Accepted a telemedicine event with no credential and minted an id:
`{"eventId":"6fe2dd38-…","status":"ACCEPTED"}`. Whether it persists was not confirmed (the DB
inspection was not available to this session) — recorded as accepted-unauthenticated, not as
proven-persisted.

### 3. Ten × `POST /internal/v1/test-command` → **201** 🟡 (low consequence, real signal)

`community`, `inventory`, `inventory-elmis-adapter`, `jobs`, `live`, `madi`, `mushe-wallet`,
`offline-sync`, `pharmacy-elmis-adapter`, `simba`.

These are `*V11ProbeController` conformance scaffolding — they echo the payload and **persist
nothing** (source read to confirm; the body is `{"echo": {…}}`). Low direct consequence.

**The inference was checked rather than assumed:** if `/internal/v1/**` were ungated on these ten
services, every real write beneath it would be open too. Their real internal write endpoints were
probed — **25 of 28 returned 401**. The exemption is confined to the probe controllers. That is the
difference between a scaffolding smell and an estate-wide hole, and it was worth measuring.

## The 20 unknowns — reached application code, not refused

Not rounded into "protected". Each got past authentication with no credential and was stopped by
bean validation or a server error:

| Service | Endpoint | Status | Why it is unknown |
|---|---|---|---|
| `tshepo-audit-service` | `POST /v1/audit/events` | **500** | 🔴 With a *fully valid* audit-event body it passed validation and threw inside the write path. Nothing refused it. This is the audit plane — unauthenticated audit writes are forgery. Highest-priority unknown. |
| `tshepo-audit-service` | `POST /v1/audit/verify-chain` | 400 | `VALIDATION_ERROR: tenantId is required` — controller-level bean validation, i.e. the request arrived. |
| `nhume-service` | `POST /internal/v1/nhume/{fleet,couriers}`, `POST /api/v1/nhume/{fleet,couriers}` | 500 ×4 | Reached app code and threw. Note `/api/v1/**` is a second unguarded path prefix on the same service. |
| `iot-ingestion-service` | `/internal/v1/devices/register` | 500 | Reached app code. |
| `iot-ingestion-service` | `/internal/v1/telemetry/{ingest,ingest/batch}` | 400 ×2 | Past Spring Security's chain (its response headers are present); app-level validation. |
| `connector-fhir-adapter` | `/internal/v1/fhir/{relay,destinations}` | 400 ×2 | As above. FHIR relay is an interoperability egress path. |
| `support-service` | `/internal/v1/support/{articles,tickets}` | 400 ×2 | As above. |
| `offline-edge-service` | `/internal/v1/offline/{sync,entitlements}` | 400 ×2 | As above. |
| `offline-sync-service` | `/internal/v1/sync-packs` | 400 | As above. |
| `pharmacy-elmis-adapter` | `/internal/v1/dispense-sync/trigger` | 400 | As above — triggers a dispense sync. |
| `matcher-engine` | `POST /v1/engine/{extract,verify,identify}` | 400 ×3 | Biometric matching. Generic Spring 400, no auth challenge. |

The discriminator used: these responses carry Spring Security's own header writers
(`X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`) — the filter chain ran and **permitted**
the request; the 400 came afterwards from the controller. A refusal would have short-circuited first.

Resolving each requires a valid body per endpoint. One was resolved that way (`jobs-service`, above)
and it turned out to be a genuine 201. **The remaining 19 should be assumed reachable until each is
individually disproven.**

## Coverage — what was NOT covered

Stated explicitly; a silently truncated sweep reads as a clean estate.

- **322 of 4,371 discovered write endpoints** were probed — up to 4 per service, chosen to span
  distinct path prefixes and to avoid `{pathVariable}` routes (whose 404/400 noise is
  uninterpretable). Every one of the 103 deployed services was probed, but **no service was probed
  exhaustively.** A service marked `refused` here means *the endpoints sampled on it refused*.
- **`tshepo-service`** (registered, not deployed) was not probed — nothing to probe.
- **19 of the 20 unknowns** were not driven to a definitive verdict with per-endpoint valid bodies.
- **Persistence was confirmed for only one finding** (`jobs-service`). The `analytics-pipeline`
  202 is recorded as accepted, not as persisted.
- Infrastructure (postgres, redis, kafka, minio, orthanc, hapi-fhir, opa, keycloak) was **not**
  swept as targets; only used for controls. `hapi-fhir` and `orthanc` accept writes and sit behind
  NetworkPolicies — they deserve their own pass.

## Per-service results (103 services)

| Service | Endpoints probed | Representative endpoint | Method | Status | Verdict |
|---|---|---|---|---|---|
| `abis-service` | 4 | `/v1/abis/identify` | POST | 401 | refused |
| `ai-model-registry-service` | 3 | `/internal/v1/ai-registry/drift-events` | POST | 401 | refused |
| `analytics-pipeline-service` | 1 | `/internal/v1/telemedicine/events` | POST | 202 | **OPEN** |
| `asset-registry-service` | 2 | `/internal/v1/equipment` | POST | 401 | refused |
| `audit-ledger-service` | 1 | `/internal/v1/audit/records` | POST | 401 | refused |
| `booking-service` | 2 | `/v1/appointments` | POST | 401 | refused |
| `butano-fhir` | 3 | `/internal/v1/fhir/resources` | POST | 401 | refused |
| `butano-service` | 2 | `/internal/v1/test-command` | POST | 401 | refused |
| `campaigns-service` | 3 | `/internal/v1/campaigns` | POST | 401 | refused |
| `card-print-agent` | 2 | `/internal/v1/test-command` | POST | 401 | refused |
| `channels-service` | 3 | `/external/v1/channels/inbound` | POST | 401 | refused |
| `clinical-knowledge-platform-service` | 3 | `/internal/v1/clinical/assistant/ask` | POST | 401 | refused |
| `community-service` | 6 | `/internal/v1/test-command` | POST | 201 | **OPEN** |
| `connector-fhir-adapter` | 2 | `/internal/v1/fhir/destinations` | POST | 400 | **unknown** |
| `costing-engine-service` | 4 | `/costa/v1/estimate` | POST | 401 | refused |
| `coverage-service` | 4 | `/internal/v1/appeals` | POST | 401 | refused |
| `credential-verification-service` | 3 | `/internal/v1/test-command` | POST | 401 | refused |
| `daidzai-service` | 3 | `/internal/v1/daidzai/assistance` | POST | 401 | refused |
| `data-access-governance-service` | 4 | `/internal/v1/access-requests` | POST | 401 | refused |
| `data-governance-service` | 4 | `/internal/v1/deid/datasets` | POST | 401 | refused |
| `data-ingestion-service` | 2 | `/internal/v1/ingest/batch` | POST | 401 | refused |
| `data-pipeline-service` | 2 | `/internal/v1/ingest` | POST | 401 | refused |
| `data-warehouse-service` | 3 | `/internal/v1/gold/materialize` | POST | 401 | refused |
| `developer-portal-service` | 3 | `/internal/v1/developer/clients` | POST | 401 | refused |
| `dispatch-service` | 2 | `/internal/v1/dispatch/deliveries` | POST | 401 | refused |
| `document-service` | 3 | `/internal/v1/test-command` | POST | 401 | refused |
| `experience-bff` | 4 | `/internal/v1/bookings` | POST | 401 | refused |
| `fhir-gateway-service` | 4 | `/internal/v1/gateway/forward` | POST | 401 | refused |
| `forms-service` | 2 | `/internal/v1/forms` | POST | 401 | refused |
| `general-ledger-service` | 2 | `/internal/v1/gl/accounts` | POST | 401 | refused |
| `guidance-service` | 2 | `/internal/v1/guidance/advisory/impression` | POST | 401 | refused |
| `hr-payroll-service` | 2 | `/internal/v1/hr/contracts` | POST | 401 | refused |
| `identity-assurance-service` | 4 | `/internal/v1/attestations` | POST | 401 | refused |
| `indawo-service` | 4 | `/internal/v1/place-links` | POST | 401 | refused |
| `inpatient-service` | 4 | `/internal/v1/admissions` | POST | 401 | refused |
| `integration-hub` | 4 | `/internal/v1/dispatch` | POST | 401 | refused |
| `inventory-elmis-adapter` | 3 | `/internal/v1/test-command` | POST | 201 | **OPEN** |
| `inventory-service` | 4 | `/internal/v1/test-command` | POST | 201 | **OPEN** |
| `iot-ingestion-service` | 3 | `/internal/v1/devices/register` | POST | 500 | **unknown** |
| `jobs-service` | 3 | `/internal/v1/jobs` | POST | 201 | **OPEN** |
| `khuluma-service` | 3 | `/internal/v1/khuluma/channels` | POST | 401 | refused |
| `landela-adapter-service` | 3 | `/internal/v1/test-command` | POST | 401 | refused |
| `learning-service` | 2 | `/internal/v1/learning/resource-opened` | POST | 401 | refused |
| `live-service` | 5 | `/internal/v1/test-command` | POST | 201 | **OPEN** |
| `llm-orchestration-service` | 2 | `/internal/v1/llm/chat` | POST | 401 | refused |
| `madi-service` | 7 | `/internal/v1/test-command` | POST | 201 | **OPEN** |
| `matcher-engine` | 3 | `/v1/engine/extract` | POST | 400 | **unknown** |
| `mental-health-service` | 1 | `/internal/v1/mental-health/referrals` | POST | 401 | refused |
| `msika-apps-service` | 1 | `/internal/v1/marketplace/items` | POST | 401 | refused |
| `msika-flow-service` | 4 | `/internal/v1/test-command` | POST | 401 | refused |
| `msika-service` | 4 | `/internal/v1/snapshots/catalogs/emit` | POST | 401 | refused |
| `mushe-wallet-service` | 7 | `/internal/v1/test-command` | POST | 201 | **OPEN** |
| `mushex-service` | 4 | `/internal/v1/test-command` | POST | 401 | refused |
| `mvumo-service` | 2 | `/internal/v1/mvumo/consent-requests` | POST | 401 | refused |
| `national-data-repository-service` | 2 | `/internal/v1/datasets` | POST | 401 | refused |
| `ndila-service` | 4 | `/api/v1/maps/routes` | POST | 401 | refused |
| `ndr-service` | 4 | `/internal/v1/ndr/build/gold/encounters` | POST | 401 | refused |
| `nhume-service` | 4 | `/api/v1/nhume/couriers` | POST | 500 | **unknown** |
| `notification-service` | 4 | `/internal/v1/delivery-receipts` | POST | 401 | refused |
| `observability-service` | 4 | `/internal/v1/alert-rules` | POST | 401 | refused |
| `offline-edge-service` | 2 | `/internal/v1/offline/entitlements` | POST | 400 | **unknown** |
| `offline-sync-service` | 3 | `/internal/v1/test-command` | POST | 201 | **OPEN** |
| `organization-registry-service` | 4 | `/v1/governance/responsibility-profiles` | POST | 401 | refused |
| `oros-service` | 4 | `/internal/v1/orders/blood/issued` | POST | 401 | refused |
| `pacs-adapter-service` | 3 | `/internal/v1/imaging-studies` | POST | 401 | refused |
| `participation-service` | 2 | `/internal/v1/participation/contributions` | POST | 401 | refused |
| `patient-safety-service` | 1 | `/internal/v1/patient-safety/reports` | POST | 401 | refused |
| `pct-service` | 4 | `/internal/v1/identity/vito-merge` | POST | 401 | refused |
| `pharmacy-elmis-adapter` | 2 | `/internal/v1/test-command` | POST | 201 | **OPEN** |
| `pharmacy-service` | 4 | `/internal/v1/test-command` | POST | 401 | refused |
| `procedures-service` | 1 | `/internal/v1/procedures/appropriateness/evaluate` | POST | 401 | refused |
| `procurement-service` | 2 | `/internal/v1/procurement/requisitions` | POST | 401 | refused |
| `product-registry-service` | 2 | `/internal/v1/products` | POST | 403 | refused |
| `referral-service` | 2 | `/internal/v1/referrals` | POST | 401 | refused |
| `reporting-service` | 1 | `/internal/v1/reports` | POST | 401 | refused |
| `rito-quality-safety-service` | 4 | `/internal/v1/mpdsr/reviews/from-death-event` | POST | 401 | refused |
| `rtc-gateway-service` | 2 | `/internal/v1/rtc/sessions` | POST | 401 | refused |
| `rules-service` | 3 | `/internal/v1/rules` | POST | 401 | refused |
| `scheduling-service` | 4 | `/v1/internal/scheduling/surgical-waitlist/from-referral` | POST | 401 | refused |
| `schema-registry-service` | 4 | `/internal/v1/schemas` | POST | 401 | refused |
| `search-service` | 1 | `/internal/v1/search/index` | POST | 401 | refused |
| `security-hardening-service` | 4 | `/internal/v1/policy-packs` | POST | 401 | refused |
| `share-slip-service` | 3 | `/internal/v1/test-command` | POST | 401 | refused |
| `simba-service` | 7 | `/internal/v1/test-command` | POST | 201 | **OPEN** |
| `support-service` | 2 | `/internal/v1/support/articles` | POST | 400 | **unknown** |
| `surgery-service` | 1 | `/internal/v1/surgery/episodes` | POST | 401 | refused |
| `surveillance-service` | 4 | `/internal/v1/ingest` | POST | 401 | refused |
| `telemonitoring-service` | 3 | `/v1/device-assignments` | POST | 401 | refused |
| `tshepo-audit-service` | 4 | `/v1/audit/events` | POST | 500 | **unknown** |
| `tshepo-authz-service` | 4 | `/internal/v1/test-command` | POST | 401 | refused |
| `tshepo-consent-service` | 3 | `/internal/v1/test-command` | POST | 401 | refused |
| `tshepo-identity-service` | 4 | `/internal/v1/identity/recovery-cases` | POST | 401 | refused |
| `tshepo-keys-service` | 4 | `/internal/v1/test-command` | POST | 401 | refused |
| `tshepo-offline-service` | 4 | `/internal/v1/test-command` | POST | 401 | refused |
| `tuso-service` | 4 | `/internal/v1/snapshots/facilities/emit` | POST | 401 | refused |
| `ubomi-service` | 3 | `/internal/v1/test-command` | POST | 401 | refused |
| `varapi-service` | 4 | `/internal/v1/snapshots/providers/emit` | POST | 401 | refused |
| `vashandi-workforce-service` | 2 | `/v1/internal/vashandi/leave` | POST | 401 | refused |
| `vito-service` | 4 | `/internal/v1/identities/provisional` | POST | 401 | refused |
| `wellness-service` | 4 | `/internal/v1/mobile/citizen/health-id` | POST | 401 | refused |
| `workflow-service` | 2 | `/internal/v1/workflows/definitions` | POST | 401 | refused |
| `workforce-governance-service` | 4 | `/internal/v1/platform-origin/country-operations` | POST | 401 | refused |
| `zibo-service` | 4 | `/internal/v1/confidentiality/classify` | POST | 401 | refused |

*Verdict is the worst result across that service's probed endpoints; the representative endpoint is
the worst-scoring one. Raw per-endpoint evidence: `phase0-e-probe-sweep-evidence.json`.*
