# Biometric identification and governance (Impilo vNext)

This wave adds a **policy-first** biometric layer: technical capability in VITO (clients) and VARAPI (providers) is always preceded by a **Tshepo policy evaluation** describing whether a workflow is `REQUIRED`, `OPTIONAL`, `ALLOWED`, `RESTRICTED`, or `PROHIBITED`, together with fine-grained flags (enrollment, verification / step-up lane, identification, deduplication assist, fallback).

## Services and responsibilities

| Area | Responsibility |
|------|----------------|
| **Tshepo** (`tshepo-service`) | Persists `biometric_policy_rule` rows and exposes `POST /v1/biometric-policy/evaluate`. |
| **VITO** (`vito-service`) | Client biometric profiles, governed enroll / verify / identify / dedup-assist, exception records, outbox events. |
| **VARAPI** (`varapi-service`) | Provider biometric profiles and templates; governed enroll and verify; outbox events. |
| **Experience BFF** | Proxies policy evaluation and biometric routes with trust headers. |
| **Experience UI** | Minimal governance preview at `/operations/vito/biometrics` (policy only — no samples). |

## APIs

### Tshepo

- `POST /v1/biometric-policy/evaluate`  
  JSON body: `subjectType`, `workflowType`, `contextType`, optional `modality`, `biometricIntent` (`ENROLL`, `VERIFY`, `IDENTIFY`, `DEDUP_SUPPORT`, `STEP_UP`), optional `resourceSensitivity` (reserved), optional `actorType`, optional numeric `assuranceLevel` (from upstream `X-Assurance-Level` / `LOA3` style headers).

### VITO (internal / trust lane)

- `POST /v1/biometric/enroll` — governed enrollment (`workflowType`, `contextType`, consent/authorization refs, vendor metadata).
- `GET /v1/biometric/{healthId}/templates` — safe summaries (no raw template bytes).
- `GET /v1/biometric/{healthId}/profile`
- `POST /v1/biometric/verify` — 1:1 verification event + outbox; optional probe metadata `livenessScore`, `deviceAttestationRef`, `engineHint` (see `BiometricProbeContext`).
- `POST /v1/biometric/identify` — scoped 1:N (digest engine placeholder).
- `POST /v1/biometric/dedup-assist` — duplicate-review assist signal.
- `POST /v1/biometric/exception` — record fallback / policy bypass attempts.

### VARAPI (internal)

- `POST /v1/provider-biometric/{providerPublicId}/enroll`
- `GET /v1/provider-biometric/{providerPublicId}/templates`
- `GET /v1/provider-biometric/{providerPublicId}/profile`
- `POST /v1/provider-biometric/{providerPublicId}/verify` — persists `provider_biometric_verification_event` and emits outbox.

### Experience BFF

- `POST /internal/v1/trust/biometric-policy/evaluate`
- `POST /internal/v1/identity/biometric/vito/...` (see `VitoBiometricBffController`)
- `/internal/v1/identity/biometric/varapi/...` (see `VarapiBiometricBffController`)

## Matching engine (placeholder)

`TemplateDigestMatchingEngine` (VITO) and the VARAPI equivalent compare **SHA-256 digests** of probe bytes to stored `template_hash`. This is **not** a population biometric matcher; it exists for deterministic integration tests and local demos. Replace with a `BiometricMatchingEngine` implementation backed by your accredited vendor SDK.

## Configuration

| Property | Default | Notes |
|----------|---------|-------|
| `vito.biometric.policy-enabled` | `true` | When `false`, Tshepo HTTP call is skipped (development only — logged in policy response reasons). |
| `vito.biometric.tshepo-policy-base-url` | `http://localhost:8079` | Must point at the deployment that hosts Tshepo’s REST controllers. |
| `vito.biometric.liveness-min-score` | `0` | When &gt; `0`, verify requires `livenessScore` on the probe context. |
| `vito.pickup.biometric-policy-on-redeem` | `false` | When `true`, delegated pickup **redeem** evaluates `CLIENT` / `DELEGATED_PICKUP` / `FACILITY` / `VERIFY` before OTP validation. |
| `varapi.biometric.*` | same pattern | VARAPI → Tshepo URL for policy. |
| `msika-flow.biometric-policy.enabled` | `false` | When `true`, `POST /v1/pickup/claim` calls Tshepo for `CLIENT` / `PICKUP_RELEASE` / `LOGISTICS` / `VERIFY` before token match. |
| `msika-flow.biometric-policy.tshepo-base-url` | `http://localhost:8079` | Tshepo base URL for Msika pickup gate. |
| `impilo.services.tshepo-authz-base-url` (BFF) | `http://localhost:8081` | BFF policy proxy; align with the real Tshepo HTTP port in your environment (`TSHEPO_AUTHZ_BASE_URL`). |

## Manual testing

1. Start Postgres + apply Flyway for `tshepo`, `vito`, `varapi`.
2. Run `tshepo-service` and verify `POST /v1/biometric-policy/evaluate` with `CLIENT` / `POINT_OF_CARE` / `FACILITY` / `VERIFY` returns `ALLOWED`.
3. Run `vito-service` with `TSHEPO_POLICY_BASE_URL` pointing at Tshepo; call `POST /v1/biometric/enroll` with `X-Access-Mode: INTERNAL` (and other trust headers) — expect `201`.
4. Run `experience-bff` + UI; open `/operations/vito/biometrics` and run a few evaluations.

## Recent extensions (actor / assurance / handoff)

- Tshepo rules support optional `actor_type`, `min_assurance_level`, `max_assurance_level` (see Flyway `V010__biometric_policy_actor_assurance.sql`).
- VITO and VARAPI policy clients forward `actorType` from `TrustContext` and parse inbound `X-Assurance-Level` when the servlet request is available.
- **Msika Flow** optional pickup gate (`msika-flow.biometric-policy.*`) and **`PickupClaimTrust`** bundle trust headers for the Tshepo call.
- **Delegated pickup** (VITO portal) optional pre-redeem policy (`vito.pickup.biometric-policy-on-redeem`).

## Known gaps / next wave

- Wire **OROS**, **PCT**, and other domains only where product code already defines handoff points.
- Replace digest matcher with **vendor adapter** + hardware attestation + liveness pipeline end-to-end.
