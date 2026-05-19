# Trust Plane Endpoint Inventory

Date: 2026-05-14  
Scope: Trust-plane and trust-critical services only.

## Canonical TSHEPO Services

### `tshepo-authz-service` (from `contracts/openapi/tshepo-authz.openapi.yaml`)

- `/internal/v1/health` `GET`
- `/internal/v1/test-command` `POST`
- `/v1/authorize` `POST`
- `/v1/break-glass` `POST`
- `/v1/break-glass/review` `POST`
- `/v1/break-glass/review/{id}` `PATCH`
- `/v1/policies` `GET|POST`
- `/v1/policies/{id}` `GET|PUT|DELETE`
- `/v1/step-up/challenge` `POST`
- `/v1/step-up/verify` `POST`
- `/v1/step-up/status/{challengeId}` `GET`
- `/v1/devices/{fingerprint}` `GET`
- `/v1/devices/{fingerprint}/block` `POST`
- `/v1/biometric-policy/evaluate` `POST` (temporary compatibility proxy)
- `/v1/patient-share-policy/evaluate` `POST` (temporary compatibility proxy)
- `/v1/council-regulatory/evaluate` `POST` (temporary compatibility proxy)

Notes:
- `/v1/authorize` is canonical ext_authz compatibility route and intentionally exposed for Envoy.
- policy compatibility proxy routes are transitional migration surfaces; canonical retirement target remains split-service ownership.

### `tshepo-consent-service` (from `contracts/openapi/tshepo-consent.openapi.yaml`)

- `/internal/v1/health` `GET`
- `/internal/v1/test-command` `POST`
- `/v1/consent` `POST`
- `/v1/consent/{id}` `GET|DELETE`
- `/v1/consent/patient/{patientRef}` `GET`
- `/v1/consent/evaluate` `GET`
- `/v1/consent/portal/my-consents` `GET`
- `/v1/consent/portal/revoke/{id}` `POST`
- `/v1/consent/share-links` `POST`
- `/v1/consent/share-links/{token}` `GET`
- `/v1/consent/share-links/{id}` `DELETE`

### `tshepo-identity-service` (from `contracts/openapi/tshepo-identity.openapi.yaml`)

- `/internal/v1/health` `GET`
- `/internal/v1/test-command` `POST`
- `/v1/identity/cpid/generate` `POST`
- `/v1/identity/cpid/provisional` `POST`
- `/v1/identity/resolve` `POST`
- `/v1/identity/mapping/{healthId}` `GET`
- `/v1/identity/mapping` `POST`
- `/v1/identity/mosip/link` `POST`
- `/v1/identity/mosip/verify` `POST`
- `/v1/identity/reconcile` `POST`
- `/v1/identity/provisional` `POST`
- `/v1/identity/tokens` `POST`
- `/v1/identity/tokens/introspect` `POST`
- `/v1/identity/tokens/{jti}` `DELETE`

### `tshepo-audit-service` (from `contracts/openapi/tshepo-audit.openapi.yaml`)

- `/internal/v1/health` `GET`
- `/internal/v1/test-command` `POST`
- `/v1/audit/events` `POST|GET`
- `/v1/audit/events/{id}` `GET`
- `/v1/audit/access-history/{subjectRef}` `GET`
- `/v1/audit/export` `POST`
- `/v1/audit/export/{id}` `GET`
- `/v1/audit/verify/{id}` `GET`
- `/v1/audit/verify-chain` `POST`

### `tshepo-keys-service` (from `contracts/openapi/tshepo-keys.openapi.yaml`)

- `/internal/v1/health` `GET`
- `/internal/v1/test-command` `POST`
- `/v1/keys/jwks` `GET`
- `/v1/keys` `POST`
- `/v1/keys/{keyId}` `GET|DELETE`
- `/v1/keys/rotate` `POST`
- `/v1/sign` `POST`
- `/v1/sign/token` `POST`
- `/v1/certificates` `POST|GET`
- `/v1/certificates/{id}` `GET|DELETE`

### `tshepo-offline-service` (from `contracts/openapi/tshepo-offline.openapi.yaml`)

- `/internal/v1/health` `GET`
- `/internal/v1/test-command` `POST`
- `/v1/offline/capabilities` `POST`
- `/v1/offline/capabilities/{id}` `GET|DELETE`
- `/v1/offline/capabilities/verify` `POST`
- `/v1/offline/actions` `POST|GET`
- `/v1/offline/packs` `POST|GET`
- `/v1/offline/packs/{id}` `GET`
- `/v1/offline/packs/facility/{facilityId}` `GET`
- `/v1/offline/reconcile` `POST`
- `/v1/offline/reconcile/{batchId}` `GET`
- `/v1/offline/reconcile/pending` `GET`

## MVUMO Trust-Orchestration Endpoints

Source: `services/mvumo-service/.../MvumoInternalController.java` under `/internal/v1/mvumo`.

- Consent request lifecycle: create/get/list/transition (`explanation`, `verify-identity`, `grant`, `partial-grant`, `refuse`, `withdraw`, `renew`, `supersede`, `verify-proof`)
- Consent evaluation and requirement evaluation: `/evaluate`, `/requirements/evaluate`
- Remote sessions: create/get + action endpoints (`verify`, `grant`, `refuse`, `withdraw`) now implemented
- Templates: list/get/create/update now implemented
- Communication preferences: read/write/history/patch/withdraw/reenable/evaluate/offline-sync
- Audit/proof read paths: `/audit/{consentId}`, `/consents/{id}/proof`

## Legacy Compatibility Monolith Endpoints (`tshepo-service`)

Primary route groups:
- `/v1/authorize`
- `/v1/biometric-policy/evaluate`
- `/v1/patient-share-policy/evaluate`
- `/v1/council-regulatory/evaluate`
- `/internal/v1/federation/*`
- `/internal/v1/health`, `/internal/v1/test-command`, `/internal/v1/test-federation`
- `/internal/v1/legacy/route-usage` (retirement telemetry endpoint)

Status:
- Classified as **LEGACY/COMPATIBILITY CONSTRAINED**.
- Non-public paths are now authenticated by default; `/v1/*` accesses emit deprecation telemetry headers and usage logs.

## Trust-Critical Dependencies (API Surface)

- `identity-assurance-service`: `/internal/v1/attestations`, `/internal/v1/risk/assess`
- `audit-ledger-service`: `/internal/v1/audit/records*`, `/internal/v1/audit/query`, `/internal/v1/audit/chain/verify`
- `security-hardening-service`: `/internal/v1/scans`, `/internal/v1/policy-packs`
