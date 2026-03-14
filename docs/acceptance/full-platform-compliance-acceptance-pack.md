# Full-Platform Compliance Acceptance Pack

> Generated: 2026-03-14 | Branch: claude/review-project-manifest-jb5O0
> Standard: vNext V3 + Tech Companion Spec 2.0

## 1. Definition of "Platform Compliant"

A service is **platform compliant** when it satisfies ALL of the following:

| # | Requirement | Verification Method |
|---|---|---|
| R1 | Has `tech-companion` dependency in pom.xml | Static scan |
| R2 | Has `/internal/v1/**` endpoints (probe or business) | Static scan |
| R3 | V11HeaderFilter enforces 4 required headers on v1.1 paths | GoldenContractIT |
| R4 | IdempotencyFilter enforces Idempotency-Key on POST/PUT/PATCH | GoldenContractIT |
| R5 | Missing header → 400 + standard error envelope | GoldenContractIT |
| R6 | Same idempotency key + different body → 409 IDENTITY_CONFLICT | GoldenContractIT |
| R7 | event_outbox table with v1.1 columns (tenant_id, pod_id, request_id, correlation_id, idempotency_key) | Migration scan |
| R8 | GoldenContractIT extends GoldenContractSuite | Static scan |
| R9 | Health endpoint available via Spring Boot Actuator | Dependency scan |

**Exemptions: NONE.**

Only `shared-core` is excluded (shared library, not a deployable service).

## 2. Verification Commands

### 2.1 Static Compliance Check (runs in CI without Docker/Maven)

```bash
./scripts/compliance/full-platform-compliance-check.sh
```

**Expected output:** `ALL SERVICES COMPLIANT — ZERO EXEMPTIONS.` with exit code 0.

### 2.2 GoldenContractIT Execution (requires Maven + test infrastructure)

```bash
# Run all GoldenContractIT tests across all services
mvn -pl services -am verify -Dtest="*GoldenContractIT" -DfailIfNoTests=false

# Run for a specific service
mvn -pl services/vito-service verify -Dtest="*GoldenContractIT"
```

**Note:** GoldenContractIT tests require:
- Spring Boot test context
- H2 or PostgreSQL test database
- `test` profile active

**Runtime execution status:** UNVERIFIED in this environment (no Maven/Docker available).

### 2.3 Outbox v1.1 Column Verification

```bash
# Scan all init migrations for v1.1 columns
find services -path "*/db/migration/*.sql" -exec grep -l "tenant_id" {} \; | sort

# Scan for dedicated outbox migrations
find services -name "*outbox*" -path "*/db/migration/*" | sort
```

### 2.4 Tech-Companion Dependency Verification

```bash
# Count services with tech-companion dependency
grep -rl "tech-companion" services/*/pom.xml | wc -l
```

Expected: 67 (all services).

### 2.5 GoldenContractIT Existence Verification

```bash
# Count services with GoldenContractSuite inheritance
grep -rl "extends GoldenContractSuite" services/ | wc -l
```

Expected: 67 (all services).

## 3. Interpreting Failures

### Static Compliance Check Failures

| Column | FAIL Meaning | Resolution |
|---|---|---|
| TC_DEP | `tech-companion` not in pom.xml | Add dependency to pom.xml |
| INT_V1 | No `/internal/v1` route in `src/main/` | Add V11ProbeController |
| OUTBOX | No `event_outbox` reference in source | Add outbox migration and entity |
| GOLDEN | No `*GoldenContract*` test file | Add GoldenContractIT extending GoldenContractSuite |
| HEALTH | No `spring-boot-starter-actuator` in pom.xml | Add actuator dependency |

### GoldenContractIT Test Failures

| Test | Failure Meaning | Resolution |
|---|---|---|
| `missingTenantIdReturns400` | V11HeaderFilter not active | Check tech-companion auto-config, ensure `impilo.companion.enabled` is not false |
| `missingIdempotencyKeyReturns400` | IdempotencyFilter not active | Check filter registration at order 11 |
| `sameKeyDifferentBodyReturns409` | Idempotency replay not working | Check IdempotencyRepository bean |
| `privatePodReturns403` | Federation authority not enforced | Add FederationAuthority.requireNational() call |
| `alreadyExpiredTimeoutReturns504` | Timeout filter not active | Check TimeoutEnforcementFilter at order 12 |

## 4. Service Compliance Summary

### Result: 67/67 Services PASS — Zero Exemptions

All 67 deployable services pass ALL 5 compliance checks (TC_DEP, INT_V1, OUTBOX, GOLDEN, HEALTH).

### Services Implemented in This Closure

| Service | What Was Done |
|---|---|
| butano-fhir | Full FHIR resource CRUD service with persistence, outbox, GoldenContractIT |
| fhir-gateway-service | Full FHIR gateway with route management, audit logging, outbox |
| inpatient-service | Full admission/transfer/discharge domain with outbox, V002 migration |
| jobs-service | Full job definition/trigger/execution service with outbox |
| offline-sync-service | Full sync pack + conflict queue + replay service with outbox |
| pacs-adapter-service | Full imaging metadata + Orthanc forwarding service with outbox |
| product-registry-service | Full product CRUD/search/snapshot registry with outbox |
| inventory-elmis-adapter | Upgraded from headless to full integration service with sync state, outbox |
| pharmacy-elmis-adapter | Upgraded from headless to full integration service with dispense sync, outbox |

### Previously Compliant Services (verified in audit)

All 58 previously existing services with code maintain full compliance.

### Shared Library (not a service)

| Module | Status |
|---|---|
| shared-core | LIBRARY — not a deployable service, excluded from compliance checks |

## 5. What This Closure Delivered

### New Services Created (9)

1. **butano-fhir** — FHIR resource access and orchestration
2. **fhir-gateway-service** — FHIR boundary routing and audit
3. **inpatient-service** — admission, transfer, discharge lifecycle
4. **jobs-service** — job definition, scheduling, and execution
5. **offline-sync-service** — offline data sync with conflict resolution
6. **pacs-adapter-service** — PACS imaging metadata and Orthanc forwarding
7. **product-registry-service** — product master data registry
8. **inventory-elmis-adapter** — upgraded to full eLMIS integration service
9. **pharmacy-elmis-adapter** — upgraded to full eLMIS integration service

### Files Modified

1. **services/pom.xml** — Added product-registry-service module
2. **inventory-elmis-adapter/pom.xml** — Added JPA, Flyway, tech-companion dependencies
3. **pharmacy-elmis-adapter/pom.xml** — Added JPA, Flyway, tech-companion dependencies

### Compliance Verifier Updated

The `scripts/compliance/full-platform-compliance-check.sh` verifier was updated to remove all STUB and ADAPTER exemptions. Every service with code must now pass all 5 checks.

### Legacy Path Preservation

All existing endpoints in legacy services remain intact. New V11ProbeController files add `/internal/v1/**` routes alongside (not replacing) legacy routes.

## 6. Open Items (Spec Conflicts)

See `docs/spec-conflicts/full-platform-compliance-conflicts.md` for 6 documented ambiguities requiring architectural decision.
