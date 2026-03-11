# Experience Platform Acceptance Pack

> **Impilo vNext** | Generated 2026-03-11 | Release/QA Captain review

---

## 1. Git State

| Field | Value |
|-------|-------|
| **Branch** | `claude/review-project-manifest-jb5O0` |
| **HEAD commit** | `dcc32ab7b211d5a9eae8809ba57baf1b8f34987a` |
| **Commit message** | `fix: align experience-bff to v1.1 Spec 2.0 compliance` |
| **Parent commit** | `4ee3c13` (feat: align tech-companion, harness, and services to v1.1 Spec 2.0 4-header rule) |

---

## 2. What Changed in `dcc32ab`

Three production fixes and supporting test/tooling additions:

1. **Encounter status mismatch fix** — `EncounterController.java` and `Encounter.java` now use `IN_PROGRESS`/`COMPLETED` instead of `OPEN`/`CLOSED`, aligning the controller's SQL and outbox writes with the Flyway migration default and test expectations.
   - Files: `services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/EncounterController.java`, `services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/domain/Encounter.java`

2. **Duplicate `ResourceNotFoundException` removal** — Removed the inner class `WorkspaceController.ResourceNotFoundException` (duplicate of the standalone exception) and its redundant handler in `GlobalExceptionHandler`, consolidating to a single exception class.
   - Files: `services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/WorkspaceController.java`, `services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/GlobalExceptionHandler.java`

3. **`ExperienceV11ComplianceTest` added** — 7 new integration tests (TestContainers + MockMvc) validating header enforcement (4 headers), idempotency replay/conflict, and outbox field completeness.
   - File: `services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/ExperienceV11ComplianceTest.java`

Additional: Static verifier extended to cover experience-bff (total: 95/95 checks); spec conflict #8 documented.

---

## 3. Static Verification (Offline — No Docker/Maven Required)

The static verifier inspects source files for v1.1 Spec 2.0 compliance wiring without starting any services.

### Prerequisites

- Java 21 on `PATH`

### Command

```bash
cd /home/user/Impilo-vNext
javac tools/static-verifier/V11ComplianceStaticVerifier.java \
  && java -cp tools/static-verifier V11ComplianceStaticVerifier .
```

### Expected Output (PASS)

```
=== VERIFICATION RESULTS ===
Passed:  95
Failed:  0
Skipped: 0
Total:   95
```

Exit code: `0`

### What Failure Means

- **Exit code 1**: One or more compliance checks failed. The output will list each failing check with a `FAIL:` prefix. Common causes:
  - Missing `V11HeaderFilter` registration in a new service
  - Outbox entity missing required columns (`tenant_id`, `pod_id`, `correlation_id`, `schema_version`)
  - `GoldenContractIT.java` not extending `GoldenContractSuite`
  - `ExperienceV11ComplianceTest.java` not asserting `MISSING_REQUIRED_HEADER` or `IDENTITY_CONFLICT`

---

## 4. Maven Test Execution (Online — Requires Docker for TestContainers)

### Prerequisites

- Java 21 on `PATH`
- Maven 3.9+
- Docker daemon running (required by TestContainers)
- Network access (Maven Central dependency resolution)

### 4a. Build shared libraries first

```bash
cd /home/user/Impilo-vNext
mvn -f services/pom.xml \
    -pl ../libs/tech-companion,../libs/tech-companion-harness,../libs/tech-companion-mock,../libs/shared-kernel-java \
    -am clean install -q
```

**Expected**: `BUILD SUCCESS`

### 4b. Experience BFF tests (includes ExperienceV11ComplianceTest)

```bash
cd /home/user/Impilo-vNext
mvn -f services/pom.xml \
    -pl experience-bff \
    -am clean test
```

**Expected success signals**:
- `BUILD SUCCESS` in Maven output
- Test report shows all tests passing, including:
  - `ExperienceV11ComplianceTest` — 7 tests (3 header enforcement, 2 idempotency, 2 outbox)
  - `GoldenContractIT` — contract suite auto-discovery tests
  - `GoldenPathIntegrationTest` — 13 tests across 6 golden paths

**Expected test count**: All tests green, zero failures, zero errors.

### 4c. Experience UI — Route parity, lint, type-check, build

```bash
cd /home/user/Impilo-vNext/ui/experience

# Route parity check (98 routes expected)
npm run test:routes

# TypeScript type check
npm run type-check

# ESLint
npm run lint

# Production build
npm run build
```

**Expected success signals**:
- `test:routes`: Output confirms 98/98 route parity — no missing or extra routes
- `type-check`: Exits 0, no type errors
- `lint`: Exits 0, no lint errors
- `build`: `✓ Compiled successfully` / exits 0

> **Note**: Run `npm install` first if `node_modules/` is not present.

---

## 5. Docker Compose Runtime Verification

### Compose file

```
compose/experience/docker-compose.yml
```

Services started: `experience-db` (Postgres 16), `experience-bff` (Spring Boot on :8160), `experience-ui` (Next.js on :3020)

### 5a. Build and start

```bash
cd /home/user/Impilo-vNext
docker compose -f compose/experience/docker-compose.yml up -d --build
```

### 5b. Wait for health (max ~120s)

```bash
# Poll BFF health — retries every 5s
for i in $(seq 1 24); do
  curl -sf http://localhost:8160/health && echo " BFF UP" && break
  echo "Waiting... ($i/24)"
  sleep 5
done
```

### 5c. Healthcheck endpoints + curl checks

| Service | URL | Expected |
|---------|-----|----------|
| **BFF custom health** | `curl -sf http://localhost:8160/health` | `{"status":"UP","service":"experience-bff"}` |
| **BFF actuator** | `curl -sf http://localhost:8160/actuator/health` | `{"status":"UP",...}` (HTTP 200) |
| **Experience UI** | `curl -sf http://localhost:3020` | HTML page (HTTP 200) |
| **Postgres** | `docker exec <experience-db-container> pg_isready -U impilo -d experience_bff` | `accepting connections` |

### 5d. Run BFF smoke tests

```bash
BFF_BASE="http://localhost:8160" bash scripts/experience/smoke/bff-smoke.sh
```

**Expected**: `BFF Smoke: All 5 checks passed`, exit code 0.

### 5e. Full online verification (all-in-one)

```bash
./scripts/experience/verify-online.sh
```

**Expected**: `PASS — All N checks passed`, exit code 0. Covers: lib build, BFF tests, Docker Compose up, healthcheck, smoke tests, UI route parity, idempotency replay/conflict, outbox proof query.

### 5f. Teardown

```bash
docker compose -f compose/experience/docker-compose.yml down -v
```

---

## 6. Golden Path Smoke Checklist

Manual UI flows to verify after Docker Compose is running (UI at `http://localhost:3020`).

| # | Flow | Steps | Expected Outcome |
|---|------|-------|------------------|
| 1 | **Login & session** | Navigate to `/auth/login`, enter credentials, submit | Redirect to `/home`, session cookie set, trust headers injected on subsequent API calls |
| 2 | **Facility selection → Workspace** | From home, select a facility → open a workspace | `/facility` and `/workspace` pages load, BFF returns facility and workspace data with HTTP 200 |
| 3 | **Queue → Encounter → Close** | Open queue (`/queue`), select a patient, start encounter, record chief complaint, close encounter | Encounter created with status `IN_PROGRESS`, encounter close sets status to `COMPLETED`, outbox event `impilo.experience.encounter.created.v1` written to DB |
| 4 | **Admin governance** | Navigate to `/admin/users`, view user list, check role assignments | Admin pages render, RBAC-gated content shown/hidden based on role, no 403 errors for authorized admin |
| 5 | **Registry browse** | Navigate to `/registry/providers`, browse provider list, click a provider | Provider list loads from BFF, detail page renders, v1.1 headers visible in browser DevTools network tab |
| 6 | **Report generation (idempotency proof)** | Navigate to `/reports`, generate a facility report, click generate again quickly | First request returns 201, second request with same idempotency key returns identical replay response (not a duplicate record) |
| 7 | **Error envelope (negative)** | Open browser DevTools, manually issue a `fetch()` to `/internal/v1/facilities` without `X-Tenant-ID` header | Response is 400 with JSON body: `{"error":{"code":"MISSING_REQUIRED_HEADER","message":"...","request_id":"...","correlation_id":"...","details":{"missing":["X-Tenant-ID"]}}}` |

---

## 7. Failure Triage Map

| Symptom | Check These Files/Configs | Likely Cause |
|---------|--------------------------|--------------|
| Static verifier reports < 95 passed | `tools/static-verifier/V11ComplianceStaticVerifier.java` — look for `FAIL:` lines in output | New service added without v1.1 wiring, or file was renamed/moved |
| `ExperienceV11ComplianceTest` fails on header enforcement | `services/experience-bff/src/main/java/.../controller/GlobalExceptionHandler.java`, check `V11HeaderFilter` bean registration in Spring config | V11HeaderFilter not registered or filter order changed |
| Idempotency replay test fails (different responses) | `services/experience-bff/src/main/java/.../filter/IdempotencyFilter.java`, check `idempotency_store` table schema | Idempotency store not persisting response body, or body hash algorithm changed |
| Idempotency conflict returns 200 instead of 409 | Same idempotency filter, check body hash comparison logic | Body hash comparison disabled or SHA algorithm mismatch |
| Outbox fields test fails (missing columns) | `services/experience-bff/src/main/resources/db/migration/` — check Flyway migrations for `event_outbox` DDL | Migration not applied or column names don't match (`tenant_id`, `pod_id`, `correlation_id`, `schema_version`) |
| Encounter status is `OPEN` instead of `IN_PROGRESS` | `services/experience-bff/src/main/java/.../controller/EncounterController.java:130` | Regression — someone reverted the dcc32ab status fix |
| Docker Compose BFF won't start | `compose/experience/docker-compose.yml`, `services/experience-bff/Dockerfile`, check `docker compose logs experience-bff` | DB connection refused (Postgres not healthy yet), or Dockerfile build failure (missing libs) |
| Docker Compose DB healthcheck fails | `compose/experience/docker-compose.yml` — `experience-db` service healthcheck | Port 5433 already in use, or Postgres image pull failed |
| UI `npm run build` fails | `ui/experience/package.json`, `ui/experience/tsconfig.json`, check TypeScript errors | Type annotation missing (see commit `90f9596` for prior fix pattern), or dependency not installed |
| UI route parity check fails | `ui/experience/src/lib/routes.ts` (expected count = 98), `ui/experience/scripts/route-parity-check.mjs` | Route added/removed in `routes.ts` without updating `EXPECTED_ROUTE_COUNT`, or `src/app/` directory structure doesn't match registry |
| BFF smoke test — missing headers returns 200 | `services/experience-bff/src/main/java/.../filter/V11HeaderFilter.java` | Filter not intercepting the tested endpoint, or endpoint path changed |
| `verify-online.sh` can't find compose file | Script checks repo root for `docker-compose.yml` — but experience compose is at `compose/experience/docker-compose.yml` | Set `COMPOSE_FILE` env var or symlink, or run the compose commands from Section 5 directly |

---

## 8. Spec Integrity Gate

### Purpose

Verifies that `docs/prototype/final/*.md` files are not stubs. These files serve as the index/contract layer pointing to canonical spec sources in `docs/plan/` and `docs/architecture/v1.1/`.

### Command

```bash
cd /home/user/Impilo-vNext
./scripts/spec-integrity-check.sh
```

### Expected Output (PASS)

```
=== Spec Integrity Check ===
PASS: Canonical spec root exists with 7 files
PASS: Architecture spec root exists with 8 files
PASS: 00_executive_summary.md — index doc with >= 10 canonical links
PASS: 01_site_map.md — index doc with >= 10 canonical links
...
(all 8 files pass)

=== RESULTS ===
Passed: 10
Failed: 0

SPEC INTEGRITY CHECK: PASS
```

Exit code: `0`

### What Failure Means

- **Exit code 1**: One or more spec files are stubs or degraded. Output lists each failing file with reasons.
- **Resolution**: See `scripts/spec-integrity-check.README.md` for remediation steps.

---

## Appendix: Key File Paths

| Component | Path |
|-----------|------|
| Experience BFF service | `services/experience-bff/` |
| Experience BFF pom.xml | `services/experience-bff/pom.xml` |
| EncounterController (status fix) | `services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/EncounterController.java` |
| WorkspaceController (duplicate removed) | `services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/WorkspaceController.java` |
| GlobalExceptionHandler (handler removed) | `services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/GlobalExceptionHandler.java` |
| Encounter domain (status fix) | `services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/domain/Encounter.java` |
| ExperienceV11ComplianceTest | `services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/ExperienceV11ComplianceTest.java` |
| GoldenContractIT | `services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/GoldenContractIT.java` |
| GoldenPathIntegrationTest | `services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/GoldenPathIntegrationTest.java` |
| Static verifier | `tools/static-verifier/V11ComplianceStaticVerifier.java` |
| Experience UI | `ui/experience/` |
| UI routes registry | `ui/experience/src/lib/routes.ts` |
| UI API client | `ui/experience/src/lib/api-client.ts` |
| Docker Compose | `compose/experience/docker-compose.yml` |
| BFF smoke tests | `scripts/experience/smoke/bff-smoke.sh` |
| Full online verifier | `scripts/experience/verify-online.sh` |
| BFF application config | `services/experience-bff/src/main/resources/application.yml` |
| Flyway migrations | `services/experience-bff/src/main/resources/db/migration/` |
