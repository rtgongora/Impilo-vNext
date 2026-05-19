# Trust Plane Production Readiness Final Report

Date: 2026-05-14  
Scope: Trust, Identity Assurance, Governance plane (including trust-critical dependencies).

## Plane-Level Verdict

**NOT READY (cutover hardening advanced, critical cutover blockers remain).**

The plane is materially improved and operationally safer, but cannot be marked READY until CI-grade cross-service runtime E2E trust evidence is green in CI, legacy monolith retirement gates reach zero-usage closure, and trust contract convergence exceptions are reduced to explicit bounded compatibility windows.

## Services Reviewed

- Core TSHEPO: `tshepo-authz-service`, `tshepo-consent-service`, `tshepo-identity-service`, `tshepo-audit-service`, `tshepo-keys-service`, `tshepo-offline-service`, `tshepo-service` (legacy)
- Trust-critical: `identity-assurance-service`, `audit-ledger-service`, `security-hardening-service`, `mvumo-service`

## Endpoints Reviewed

- Canonical endpoint inventory: `docs/architecture/trust-plane-endpoint-inventory.md`
- Dependency graph: `docs/architecture/trust-plane-dependency-map.md`

## Functionality Completed in This Cutover Pass

1. **Runtime full-stack trust harness added:**
   - Added dedicated Docker Compose stack: `compose/trust/docker-compose.trust-e2e.yml` (`mvumo`, `tshepo-authz`, `tshepo-consent`, `tshepo-audit`, `tshepo-service` + infra).
   - Added executable harness scripts:
     - `test/integration/trust-fullstack-runtime.sh`
     - `test/integration/trust-fullstack-runtime.ps1`
   - Harness validates success, deny, dependency-failure, missing-context, telemetry, and persisted outbox evidence.
   - Reliability hardening completed: strict preflight checks, deterministic compose project naming, per-service health waits, explicit timeout controls, deterministic failure diagnostics/log capture, and guaranteed teardown.
2. **CI gate expanded to include runtime harness path:**
   - Added `trust-fullstack-runtime` job in `.github/workflows/ci.yml` (runs after `trust-e2e-gates`).
   - CI preflight now explicitly verifies `docker`, `docker compose`, daemon availability, and compose config validity before harness execution.
   - CI uploads runtime diagnostics artifacts on failure from `.tmp/trust-e2e-artifacts`.
3. **Legacy consumer migration closure tightened:**
   - Added no-regression tests ensuring migrated policy consumers do not default to `:8079`:
     - `vito-service` `TshepoPolicyBaseUrlDefaultGuardTest`
     - `varapi-service` `TshepoPolicyBaseUrlDefaultGuardTest`
     - `msika-flow-service` `TshepoPolicyBaseUrlDefaultGuardTest`
   - Updated `VarapiProperties` default policy base URL to `http://localhost:8081`.
   - Removed runtime fallback references to `TSHEPO_POLICY_BASE_URL` in active policy consumers (`vito-service`, `varapi-service`, `msika-flow-service`) so default runtime cutover is now authz-only.
4. **Compatibility contract convergence tightened:**
   - Marked authz compatibility proxy routes as deprecated in `contracts/openapi/tshepo-authz.openapi.yaml`.
   - Added retirement checklist: `docs/architecture/tshepo-legacy-retirement-checklist.md`.
   - Added contract convergence matrix: `docs/architecture/trust-api-contract-convergence-matrix.md`.

## Remaining Blockers

1. **Runtime harness evidence blocker remains open until first green CI run:** local environment has Docker daemon unavailable (`dockerDesktopLinuxEngine` pipe missing), so local runtime proof cannot be asserted here. CI command of record is:
   - `bash test/integration/trust-fullstack-runtime.sh`
   - with deterministic env: `TRUST_E2E_COMPOSE_PROJECT=trust-e2e-${{ github.run_id }}-${{ github.run_attempt }}`
2. `tshepo-service` remains active compatibility surface pending zero-usage telemetry window and compatibility-route decommission (runtime default consumer fallbacks to legacy host have been removed).
3. Route/error/decision envelope convergence across trust services remains partial (`/v1` vs `/internal/v1` and mixed envelope shapes).
4. Some trust admin/BFF operational surfaces remain partial even when backend APIs are available.

## Tests Added/Updated

- Updated: `services/mvumo-service/src/test/java/zw/gov/mohcc/impilo/mvumo/api/MvumoInternalControllerTest.java`
  - validate implemented remote/template routes and denial/error behavior.
- Added: `services/mvumo-service/src/test/java/zw/gov/mohcc/impilo/mvumo/service/MvumoServiceRemoteSessionAndTemplateTest.java`
  - remote token verification, state transition guards, template create/update persistence behavior.
- Added: `services/mvumo-service/src/test/java/zw/gov/mohcc/impilo/mvumo/integration/MvumoCrossServiceFlowIT.java`
  - integration evidence for MVUMO -> TSHEPO-consent boundary behavior with persisted audit/outbox checks.
- Existing guard retained/updated:
  - `services/mvumo-service/src/test/java/zw/gov/mohcc/impilo/mvumo/config/SecurityConfigConditionalModeTest.java`
- Added:
  - `services/tshepo-service/src/test/java/zw/gov/mohcc/impilo/tshepo/config/SecurityConfigSourceGuardTest.java`
  - prevents regression to legacy `anyRequest().permitAll()`.
  - `services/tshepo-service/src/test/java/zw/gov/mohcc/impilo/tshepo/config/LegacyRouteInventoryGuardTest.java`
  - prevents unclassified tshepo-service route-base expansion.
- Added:
  - `services/tshepo-service/src/test/java/zw/gov/mohcc/impilo/tshepo/config/LegacyRouteTelemetryTest.java`
  - proves legacy route telemetry evidence flow via `/internal/v1/legacy/route-usage`.
  - `services/tshepo-authz-service/src/test/java/zw/gov/mohcc/impilo/tshepo/authz/service/LegacyPolicyCompatibilityProxyServiceTest.java`
  - validates compatibility proxy forwarding and dependency failure fail-close behavior.
- Added:
  - `services/vito-service/src/test/java/zw/gov/mohcc/impilo/vito/core/TshepoPolicyBaseUrlDefaultGuardTest.java`
  - `services/varapi-service/src/test/java/zw/gov/mohcc/impilo/varapi/core/TshepoPolicyBaseUrlDefaultGuardTest.java`
  - `services/msika-flow-service/src/test/java/zw/gov/mohcc/impilo/msikaflow/core/TshepoPolicyBaseUrlDefaultGuardTest.java`
  - validate that migrated policy consumers do not default to legacy `:8079`.

## Commands Run

- `mvn -pl mvumo-service -am test`
- `mvn -pl tshepo-service,tshepo-authz-service,tshepo-identity-service,tshepo-consent-service,tshepo-audit-service,tshepo-keys-service,tshepo-offline-service,identity-assurance-service,mvumo-service -am test`
- `mvn -pl audit-ledger-service,security-hardening-service -am test`
- `mvn -pl vito-service,varapi-service,msika-flow-service -am test`
- `rg "TSHEPO_POLICY_BASE_URL" services` (runtime references cleared; only guard tests retain string assertions)
- `node scripts/completeness/generate-completeness-report.mjs`
- `powershell -ExecutionPolicy Bypass -File test/integration/trust-fullstack-runtime.ps1` (failed due local Docker daemon unavailable)
- Local reliability validation:
  - shell preflight checks now fail fast on missing compose plugin or Docker daemon
  - `docker compose -f compose/trust/docker-compose.trust-e2e.yml config`
  - `powershell -NoProfile -Command "[scriptblock]::Create((Get-Content -Raw 'test/integration/trust-fullstack-runtime.ps1')) | Out-Null"`
  - bash syntax check command (`bash -n test/integration/trust-fullstack-runtime.sh`) is not runnable in this local environment because `/bin/bash` is unavailable

## Build/Test Result

- All required Maven validation commands: **PASS**
- Completeness generation: **PASS**
- Compose config validation: **PASS**
- PowerShell harness syntax validation: **PASS**
- Bash harness syntax validation: **BLOCKED (no local bash runtime available)**
- Local runtime harness execution: **BLOCKED (environment: Docker daemon unavailable)**
- Runtime harness reliability hardening: **COMPLETE (pending first CI execution evidence)**

## What A Green Runtime Harness Proves

- Trust runtime stack starts deterministically with health-gated sequencing.
- Core full-stack trust runtime assertions pass (happy path, deny path, dependency-failure fail-close, missing-context fail-close, telemetry reachability, persisted outbox evidence).
- Failures are diagnosable via captured compose state/log artifacts.

## What It Does Not Yet Prove

- It does not yet prove trust plane READY status.
- It does not replace required legacy retirement zero-usage window closure.
- It does not close all trust contract convergence exceptions.

## Production Go/No-Go Recommendation

**NO-GO (for full Trust-plane READY declaration).**

Rationale: cutover controls and migration gates are materially improved, but runtime multi-service E2E must pass in CI, legacy compatibility traffic must reach zero-usage gates, and contract convergence must close remaining bounded exceptions before declaring READY FOR CONTROLLED PRODUCTION BASELINE.
