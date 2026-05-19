# Trust Plane Production Readiness Audit

Date: 2026-05-14  
Scope: Trust, Identity Assurance & Governance Plane only (plus explicitly reviewed security-critical adjacent services).

## Services Audited

Primary trust-plane services:
- `tshepo-service`
- `tshepo-authz-service`
- `tshepo-identity-service`
- `tshepo-consent-service`
- `tshepo-audit-service`
- `tshepo-keys-service`
- `tshepo-offline-service`
- `identity-assurance-service`
- `mvumo-service` (trust-consent orchestration, trust domain)

Security-critical adjacent services reviewed for trust dependency clarity:
- `audit-ledger-service` (integration plane, trust-critical evidence store)
- `security-hardening-service` (integration plane, security governance controls)

Key infrastructure references validated:
- `infra/envoy/envoy.yaml` (Envoy `ext_authz` routing to TSHEPO authz)
- Keycloak issuer usage across trust services (`spring.security.oauth2.resourceserver.jwt.issuer-uri`)
- OpenAPI contracts under `contracts/openapi/tshepo-*.openapi.yaml` and `contracts/openapi/identity-assurance.openapi.yaml`

## Per-Service Status

| Service | Status | Summary |
|---|---|---|
| `tshepo-authz-service` | PARTIAL | Production-grade ext_authz, break-glass, step-up, device and policy flows; still primarily legacy `/v1/*` surface with limited canonical `/internal/v1/*` parity. |
| `tshepo-consent-service` | PARTIAL | Real consent directives/evaluation, outbox, audit, and tests; route versioning remains mostly `/v1/*`. |
| `tshepo-identity-service` | PARTIAL | Real identity resolution/tokenisation and tests; route migration to canonical `/internal/v1/*` still pending. |
| `tshepo-audit-service` | PARTIAL | Real append/chain/audit query mechanics and tests; route conventions still legacy-heavy. |
| `tshepo-keys-service` | PARTIAL | Real key lifecycle/JWKS/signing and tests; endpoint surface mostly `/v1/*`. |
| `tshepo-offline-service` | PARTIAL | Real capability token + reconciliation flow, eventing, and tests; version harmonisation pending. |
| `identity-assurance-service` | PARTIAL | Real attestation/risk controls with tests and contract checks; trust-plane ownership boundaries still require ADR clarity. |
| `mvumo-service` | PARTIAL | Consent decisioning path is live and remote-session/template trust workflows are now implemented with persistence and audited transitions; remaining blocker is E2E cross-service readiness evidence and static contract parity. |
| `tshepo-service` | LEGACY/COMPATIBILITY | Legacy monolith still active with overlapping trust responsibilities; default permissive posture has been constrained to authenticated non-public routes, but retirement/delegation plan remains required. |
| `audit-ledger-service` | PARTIAL | Security-critical and test-validated; integration-plane owned, retained as trust dependency. |
| `security-hardening-service` | PARTIAL | Security-critical controls present and tested; integration-plane owned, retained as trust dependency. |

No service in this pass is marked `READY` due to unresolved route/versioning and decomposition blockers.

## Findings by Audit Dimension

### A) Purpose and ownership
- Trust-plane SoR boundaries are represented in `docs/registry/services-registry.yaml`, but `tshepo-service` still overlaps decomposed TSHEPO sub-services.
- `audit-ledger-service` and `security-hardening-service` are security-critical but not primary trust-plane owners; retained as cross-plane dependencies.

### B) API and contract readiness
- TSHEPO services have OpenAPI artifacts (`contracts/openapi/tshepo-*.openapi.yaml`).
- `identity-assurance-service` has OpenAPI (`contracts/openapi/identity-assurance.openapi.yaml`).
- `mvumo-service` does not yet have a dedicated static OpenAPI contract artifact in `contracts/openapi`; implemented endpoints currently rely on runtime springdoc.
- Trust services still expose significant legacy `/v1/*` APIs, while canonical architecture expects stronger `/internal/v1/*` consistency.

### C) Security and policy enforcement
- `tshepo-authz-service` intentionally keeps `/v1/authorize` open for Envoy ext_authz; management surfaces require JWT.
- `tshepo-consent-service` intentionally keeps `/v1/consent/evaluate` open for service-to-service authz checks.
- `tshepo-service` legacy auth posture was tightened in this pass (no default `anyRequest().permitAll()`); overlap/retirement remains a major trust-plane blocker.

### D) Consent and audit
- Consent and audit flows are real and persisted in TSHEPO decomposition services.
- Correlation and trust context handling is present through trust context filters and shared contracts.
- `mvumo` consent decision path was previously stubbed and has now been remediated.

### E) Data and persistence
- Flyway migrations and entity models are present across audited trust services.
- Outbox/event tables and supporting persistence layers exist for key trust services.

### F) Integration and eventing
- Trust eventing/outbox is implemented in decomposition services.
- Envoy ext_authz integration references are present and aligned with TSHEPO authz routing.

### G) Observability and operations
- Health, metrics, and actuator exposure exist across services.
- Trust-related runbooks and production-readiness docs exist, but decomposition/legacy overlap still complicates ops ownership.

### H) Tests
- Trust service module builds/tests passed.
- Golden contract tests and targeted service tests exist across trust modules.
- Added targeted trust remediation tests in this pass for MVUMO decision delegation plus implemented remote-session/template workflows.

### I) Frontend/BFF wiring
- Trust capabilities are mostly consumed indirectly through gateway/BFF flows rather than direct UI calls.
- Communication preference and consent surfaces are wired via MVUMO; backend logic for remote-session/template trust flows is now implemented, with UI/admin parity still partial.

## Remediations Implemented (This Pass)

1. Replaced MVUMO trust decision stub with live TSHEPO consent delegation.
   - Updated `services/mvumo-service/src/main/java/zw/gov/mohcc/impilo/mvumo/service/MvumoService.java`
   - Updated `services/mvumo-service/src/main/java/zw/gov/mohcc/impilo/mvumo/integration/TshepoConsentClient.java`
   - Updated `services/mvumo-service/src/main/java/zw/gov/mohcc/impilo/mvumo/api/MvumoInternalController.java`
   - Added/updated test: `services/mvumo-service/src/test/java/zw/gov/mohcc/impilo/mvumo/api/MvumoInternalControllerTest.java`

2. Implemented MVUMO previously fail-closed intended paths.
   - `POST /internal/v1/mvumo/remote-sessions/{id}/{verify|grant|refuse|withdraw}` now executes real persisted workflows.
   - `POST/PUT /internal/v1/mvumo/templates*` now creates/updates persisted templates with versioning behavior.

3. Added bounded negative-path tests:
   - consent deny -> 403 (`/internal/v1/mvumo/evaluate`)
   - TSHEPO dependency failure -> 503
   - missing/invalid context -> 400
   - implemented endpoint regression checks for remote-session and template write paths
   - production-vs-test security chain gating assertions
   - Added/updated tests:
     - `services/mvumo-service/src/test/java/zw/gov/mohcc/impilo/mvumo/api/MvumoInternalControllerTest.java`
     - `services/mvumo-service/src/test/java/zw/gov/mohcc/impilo/mvumo/config/SecurityConfigConditionalModeTest.java`

4. Published TSHEPO legacy monolith ADR draft:
   - `docs/architecture/adr/ADR-TSHEPO-LEGACY-MONOLITH-CONSTRAINT-AND-RETIREMENT.md`

5. Updated trust-plane evidence registers:
   - `docs/registry/service-readiness-register.md`
   - `docs/registry/mock-and-stub-register.md`
   - `docs/registry/backend-to-frontend-wiring-map.md`
   - `docs/registry/gap-remediation-plan.md`
6. Added cross-service trust closure evidence:
   - `services/mvumo-service/src/test/java/zw/gov/mohcc/impilo/mvumo/integration/MvumoCrossServiceFlowIT.java`
   - `services/tshepo-consent-service/src/main/java/zw/gov/mohcc/impilo/tshepo/consent/service/ConsentEvaluationService.java` (explicit deny audit persistence)
   - `services/tshepo-service/src/main/java/zw/gov/mohcc/impilo/tshepo/config/LegacyRouteDeprecationFilter.java`
   - `services/tshepo-service/src/main/java/zw/gov/mohcc/impilo/tshepo/api/LegacyRouteUsageController.java`
   - `services/tshepo-service/src/test/java/zw/gov/mohcc/impilo/tshepo/config/LegacyRouteInventoryGuardTest.java`
7. Added canonical API harmonization artifacts:
   - `docs/architecture/trust-plane-canonical-api-contract.md`
   - `contracts/openapi/mvumo.openapi.yaml`
   - `docs/architecture/tshepo-legacy-retirement-execution-plan.md`
8. Added trust completion pass migration and CI evidence:
   - `services/tshepo-authz-service/src/main/java/zw/gov/mohcc/impilo/tshepo/authz/api/LegacyPolicyCompatibilityController.java`
   - `services/tshepo-authz-service/src/main/java/zw/gov/mohcc/impilo/tshepo/authz/service/LegacyPolicyCompatibilityProxyService.java`
   - `services/tshepo-service/src/test/java/zw/gov/mohcc/impilo/tshepo/config/LegacyRouteTelemetryTest.java`
   - `.github/workflows/ci.yml` (`trust-e2e-gates` job)

## Remediations Deferred (Explicit)

- Trust plane still requires full CI-level multi-service E2E validation across authz + consent + audit + BFF administrative paths.
- `tshepo-service` has retirement controls but still has overlapping ownership with decomposed TSHEPO services and incomplete consumer migration.
- Trust-wide route convention harmonisation (`/v1/*` to canonical `/internal/v1/*`) remains partial across TSHEPO decomposition.

## Commands Run

- Trust module tests:
  - `mvn -pl tshepo-service,tshepo-authz-service,tshepo-identity-service,tshepo-consent-service,tshepo-audit-service,tshepo-keys-service,tshepo-offline-service,identity-assurance-service,mvumo-service -am test`
- Security-critical adjacent tests:
  - `mvn -pl audit-ledger-service,security-hardening-service -am test`
- Post-remediation validation:
  - `mvn -pl mvumo-service -am test`
- Registry/completeness validation (register updates in this slice):
  - `node scripts/completeness/generate-completeness-report.mjs`
- CI trust E2E gates:
  - MVUMO cross-service flow integration test
  - TSHEPO authz denial gate tests
  - TSHEPO consent denial/audit gate tests
  - legacy telemetry + route inventory guard tests
- Relevant lint/diagnostic checks:
  - IDE lint diagnostics for edited MVUMO classes/tests (no new errors)
- Focused code audits via `rg`/file inspection for:
  - security filters (`oauth2ResourceServer`, route authorization posture)
  - route conventions (`/v1/*`, `/internal/v1/*`)
  - stub/not-implemented patterns
  - trust integration references (Envoy ext_authz, Keycloak issuer)

## Build/Test Results

- Trust module reactor: **BUILD SUCCESS**
- Security-critical adjacent reactor: **BUILD SUCCESS**
- MVUMO post-remediation test run: **BUILD SUCCESS**
- Completeness report regeneration: **SUCCESS**
- Added/updated MVUMO controller test passed.

## Production Blockers (Trust Plane)

1. `tshepo-service` remains a legacy compatibility monolith with overlapping responsibilities; retirement execution is active but not complete.
2. CI gate coverage is stronger, but full multi-service runtime orchestration evidence remains incomplete before READY.
3. Trust API surface is still mixed between legacy and canonical prefixes, creating contract governance drift until migration completes.

## Remaining Risks

- Trust governance complexity from parallel legacy/decomposed TSHEPO ownership.
- Potential policy enforcement ambiguity while route/version conventions remain mixed.
- Risk of prolonged partial readiness if legacy TSHEPO compatibility usage does not trend to zero and route retirement gates are not executed.

## Status Changes in This Slice

- `mvumo-service`: remains **PARTIAL WITH EXPLICIT BLOCKERS** with stronger implementation + integration evidence baseline.
- `tshepo-service`: remains **LEGACY/COMPATIBILITY CONSTRAINED** under ADR + execution-gate controls.
- No trust service upgraded to `READY` in this slice.

## Recommended Next Trust-Plane Pass

1. Promote module-level trust evidence to CI-grade cross-service E2E suites (MVUMO + TSHEPO Authz/Consent/Audit + BFF surfaces).
2. Execute legacy retirement gates: migrate known `tshepo-service` consumers and enforce zero-legacy-usage window.
3. Complete controlled route-convention migration plan (`/v1/*` -> canonical routes) with compatibility windows and deprecation cutoffs.

