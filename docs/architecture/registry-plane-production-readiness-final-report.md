# Registry Plane Production Readiness Final Report

Date: 2026-05-14  
Scope: Registry & Sovereign Identity Spine plane.

## Plane-Level Verdict

**NOT READY (cutover-evidence gate pending first green CI runtime execution).**

Registry core services are now in a materially stronger posture with runtime harness and BFF fail-close controls in place, but the plane cannot be marked READY until the first green `registry-fullstack-runtime` CI execution is recorded.

## Services Reviewed

- `vito-service`
- `varapi-service`
- `tuso-service`
- `zibo-service`
- `ubomi-service`
- `indawo-service`
- `msika-service`
- `product-registry-service`

## Functionality Completed In This Pass

1. **Core registry security hardening (server-side):**
   - Removed production `anyRequest().permitAll()` posture in:
     - `vito-service`
     - `varapi-service`
     - `tuso-service`
     - `zibo-service`
     - `product-registry-service`
   - `product-registry-service` now enforces authenticated business routes and TrustContext extraction parity.
2. **Regression guards added to prevent security rollback:**
   - Added `SecurityConfigSourceGuardTest` in:
     - `vito-service`
     - `varapi-service`
     - `tuso-service`
     - `zibo-service`
     - `product-registry-service`
3. **Registry trust-integration defaults tightened:**
   - Active registry consumers now default directly to `TSHEPO_AUTHZ_BASE_URL` without runtime legacy fallback env chain:
     - `vito-service`
     - `varapi-service`
     - `msika-flow-service` (registry-adjacent consumer)
4. **Registry validation suite executed at module scope:**
   - Full requested registry Maven module suite passed.
5. **CI-grade registry runtime harness implemented (new):**
   - Added dedicated stack: `compose/registry/docker-compose.registry-e2e.yml`.
   - Added hardened scripts:
     - `test/integration/registry-fullstack-runtime.sh`
     - `test/integration/registry-fullstack-runtime.ps1`
   - Added CI gate job: `registry-fullstack-runtime` in `.github/workflows/ci.yml`.
   - Harness assertions now cover:
     - multi-service startup/health checks,
     - BFF->TUSO facility and geo wiring responses,
     - fail-close BFF behavior when TUSO dependency is unavailable (`502`),
     - secured VITO endpoint behavior (no anonymous access),
     - TSHEPO Authz dependency reachability/deny behavior.
6. **Registry BFF fail-close hardening + wiring evidence:**
   - `FacilityController` no longer emits synthetic seeded success in live mode when TUSO is down; now returns `502`.
   - `RegistryGeoLocalityController` now returns explicit upstream failure envelopes (`502`) instead of silent empty success on dependency errors.
   - Added route-level tests:
     - `FacilityControllerTest`
     - `RegistryGeoLocalityControllerTest`
7. **Contract convergence tracking tightened:**
   - Added `docs/architecture/registry-api-contract-convergence-matrix.md`.
   - Updated canonical contract references to distinguish converged vs bounded compatibility areas.

## Endpoints Reviewed

- Full endpoint inventory: `docs/architecture/registry-plane-endpoint-inventory.md`.
- Contract baseline source: `contracts/openapi/*.openapi.yaml` for registry services.

## Remaining Blockers

1. **First green CI runtime gate pending:** `registry-fullstack-runtime` is implemented and wired, but there is not yet an observed green CI execution on this branch.
2. **Audit-depth runtime evidence still bounded:** current harness proves startup, routing, fail-close, and trust dependency behavior; deeper mutation-to-audit/outbox assertions across all registry SoR services still need expansion in a follow-up hardening slice.

## Tests Added/Updated

- `services/vito-service/src/test/java/zw/gov/mohcc/impilo/vito/config/SecurityConfigSourceGuardTest.java`
- `services/varapi-service/src/test/java/zw/gov/mohcc/impilo/varapi/config/SecurityConfigSourceGuardTest.java`
- `services/tuso-service/src/test/java/zw/gov/mohcc/impilo/tuso/config/SecurityConfigSourceGuardTest.java`
- `services/zibo-service/src/test/java/zw/gov/mohcc/impilo/zibo/config/SecurityConfigSourceGuardTest.java`
- `services/product-registry-service/src/test/java/zw/gov/mohcc/impilo/productregistry/config/SecurityConfigSourceGuardTest.java`
- `services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/FacilityControllerTest.java`
- `services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/RegistryGeoLocalityControllerTest.java`

## Commands Run

- `mvn -pl vito-service,varapi-service,tuso-service,zibo-service,ubomi-service,indawo-service,msika-service,product-registry-service -am test`
- `mvn -pl experience-bff,tuso-service -am test`
- `node scripts/completeness/generate-completeness-report.mjs`
- `node scripts/completeness/openapi-contracts.mjs`
- `docker compose -f compose/registry/docker-compose.registry-e2e.yml config`

## Build/Test Result

- Required registry Maven validation command: **PASS**
- Completeness generation: **PASS**
- OpenAPI contracts check: **PASS**

## Production Go/No-Go Recommendation

**NO-GO (until first green `registry-fullstack-runtime` CI evidence is captured).**

Rationale: runtime gate and wiring/contract hardening are now implemented; remaining readiness dependency is execution evidence (green CI run) plus deeper audit-depth runtime assertions.
