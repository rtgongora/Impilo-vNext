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

**Exemptions:**
- Headless adapters (inventory-elmis-adapter, pharmacy-elmis-adapter) — no HTTP API surface
- STUB services (no runtime code) — not deployable
- shared-core — shared library, not a service

## 2. Verification Commands

### 2.1 Static Compliance Check (runs in CI without Docker/Maven)

```bash
./scripts/compliance/full-platform-compliance-check.sh
```

**Expected output:** `ALL REAL SERVICES COMPLIANT.` with exit code 0.

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

# Scan for dedicated outbox_v11 migrations
find services -name "*outbox_v11*" | sort
```

### 2.4 Tech-Companion Dependency Verification

```bash
# Count services with tech-companion dependency
grep -rl "tech-companion" services/*/pom.xml | wc -l
```

Expected: All services with code (minus 2 headless adapters).

### 2.5 GoldenContractIT Existence Verification

```bash
# Count services with GoldenContractSuite inheritance
grep -rl "extends GoldenContractSuite" services/ | wc -l
```

Expected: All services with code and API surface (56+).

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

### Fully Verified in Code (static analysis passes)

All 56 real services with API surface:
- 20 services received new V11ProbeController in this closure wave
- 13 services received spring-boot-starter-actuator dependency
- 1 service (card-print-agent) received tech-companion + harness + GoldenContractIT
- 36 services were already compliant

### Require Runtime Execution to Fully Verify

All 56 GoldenContractIT tests need Maven execution. This is blocked in the current environment.

```bash
# To execute all golden contract tests:
mvn -pl services -am verify -Dtest="*GoldenContractIT" -DfailIfNoTests=false
```

### STUB Services (no verification needed)

| Service | Reason |
|---|---|
| butano-fhir | HAPI FHIR config, no custom code |
| fhir-gateway-service | Infrastructure placeholder |
| inpatient-service | Migration only |
| jobs-service | No source code |
| offline-sync-service | Migration only |
| pacs-adapter-service | Orthanc placeholder |
| product-registry-service | Registry placeholder |

### Adapter Services (reduced scope)

| Service | Health | Notes |
|---|---|---|
| inventory-elmis-adapter | PASS | Headless Kafka adapter, no API surface |
| pharmacy-elmis-adapter | PASS | Headless Kafka adapter, no API surface |

## 5. What This Closure Delivered

### New Files Created

1. **20 V11ProbeController files** — `/internal/v1/health` + `/internal/v1/test-command` endpoints for all previously PARTIAL services
2. **1 GoldenContractIT** — card-print-agent
3. **1 compliance matrix** — `docs/compliance/full-platform-compliance-matrix.md`
4. **1 compliance verifier** — `scripts/compliance/full-platform-compliance-check.sh`
5. **1 acceptance pack** — `docs/acceptance/full-platform-compliance-acceptance-pack.md`
6. **1 spec conflicts log** — `docs/spec-conflicts/full-platform-compliance-conflicts.md`

### Files Modified

1. **13 pom.xml files** — Added `spring-boot-starter-actuator` dependency
2. **1 pom.xml** — card-print-agent: Added `tech-companion` + `tech-companion-harness`

### Legacy Path Preservation

All existing `/v1/**` endpoints in legacy services remain intact. The new V11ProbeController files add `/internal/v1/**` routes alongside (not replacing) legacy routes.

## 6. Open Items (Spec Conflicts)

See `docs/spec-conflicts/full-platform-compliance-conflicts.md` for 6 documented ambiguities requiring architectural decision.
