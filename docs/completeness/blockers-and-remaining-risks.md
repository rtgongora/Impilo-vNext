# Impilo vNext — Blockers and Remaining Risks

> Date: 2026-03-15
> Source: Full platform completeness audit

---

## Classification Key

| Severity | Definition |
|----------|-----------|
| 🔴 **BLOCKER** | Prevents deployment or critical functionality |
| 🟠 **HIGH RISK** | Could cause production issues if not addressed |
| 🟡 **MEDIUM RISK** | Should be addressed before GA but not blocking |
| 🟢 **LOW RISK** | Nice to have, can be deferred |

---

## Active Blockers

### 🔴 B-001: No Cross-Service Integration Test Suite
**Component**: Platform-wide
**Description**: Individual services have GoldenContractIT for v1.1 compliance, but there is no end-to-end integration test that validates the request path: UI → Envoy → TSHEPO ext_authz → Service → Kafka outbox → Consumer.
**Impact**: Cannot verify the golden thread works at runtime.
**Mitigation**: Docker Compose exists with all services defined. A Testcontainers-based integration suite or compose-up smoke test script would close this gap.
**Owner**: Platform team

### 🔴 B-002: No Keycloak Realm Import Script
**Component**: `scripts/seed/`
**Description**: Keycloak 25.x is in docker-compose but there is no realm import JSON for development. This means local dev requires manual Keycloak configuration.
**Impact**: Blocks local development onboarding.
**Mitigation**: Create `scripts/seed/keycloak-realm.json` with test realm, clients, and roles.
**Owner**: TSHEPO team

---

## High Risks

### 🟠 R-001: `ui/ehr` Is Empty
**Component**: `ui/ehr`
**Description**: Contains only package.json. Superseded by `ui/experience` (125 source files).
**Impact**: Confusion for new developers. Build may reference it in turbo workspace.
**Mitigation**: Remove from workspace or add a README explaining it's deprecated.

### 🟠 R-002: Web UI Test Coverage at 0%
**Component**: 22 of 24 web UIs
**Description**: Only `support-console` (5 tests) and `developer-console` (4 tests) have any test files. The main `ui/experience` app (125 source files) has zero tests.
**Impact**: No regression safety for UI changes.
**Mitigation**: Add at least render/smoke tests for critical pages (login, dashboard, encounter).

### 🟠 R-003: 39 Services Without Helm Charts
**Component**: Ring 1 and Ring 2 services
**Description**: 28 of 67 services have Helm charts. The remaining 39 lack deployment manifests.
**Impact**: Cannot deploy these services to Kubernetes without manual manifest creation.
**Mitigation**: Create a shared library Helm chart with per-service values.

### 🟠 R-004: TODO.md Is Significantly Outdated
**Component**: `TODO.md`
**Description**: Many items marked as `[ ]` pending have been implemented (e.g., VARAPI, ZIBO, BUTANO, PCT, OROS, Pharmacy, all having full implementations). This creates a false impression of incompleteness.
**Impact**: Misleads project tracking and planning.
**Mitigation**: Reconcile TODO.md against actual codebase state.

---

## Medium Risks

### 🟡 R-005: 52 Services Without READMEs
**Component**: Backend services
**Description**: Only 15 of 67 services have README.md files.
**Impact**: Onboarding friction; new developers cannot quickly understand service purpose or configuration.
**Mitigation**: Generate baseline READMEs from pom.xml, application.yml, and controller endpoints.

### 🟡 R-006: Mobile Messaging Package Has No Tests
**Component**: `apps/mobile/packages/mobile-messaging`
**Description**: 6 source files, 0 test files. Other mobile packages all have tests.
**Impact**: Regression risk for real-time messaging features in citizen/provider apps.
**Mitigation**: Add basic unit tests for message handling logic.

### 🟡 R-007: ops-instrumentation Library Under-Tested
**Component**: `libs/ops-instrumentation`
**Description**: 8 source files, 1 test file. This library provides observability wiring used by all services.
**Impact**: Changes to observability config could silently break metrics/tracing.
**Mitigation**: Add tests for metric registration, tracing configuration, and health indicator setup.

### 🟡 R-008: tech-companion-harness Has No Self-Tests
**Component**: `libs/tech-companion-harness`
**Description**: 2 source files, 0 tests. This is the GoldenContractSuite base class used by all 67 services.
**Impact**: A bug in the harness would produce false passes across all services.
**Mitigation**: Add a test that verifies GoldenContractSuite correctly detects non-compliance.

---

## Low Risks

### 🟢 R-009: Inconsistent Outbox Naming
**Component**: Multiple services
**Description**: Some services use `EventOutboxEntity`, others use `OutboxEventEntity`. Some use `EventOutboxRepository`, others `OutboxEventRepository`. Functionality is identical.
**Impact**: Code search confusion. No runtime impact.
**Mitigation**: Standardize naming in a future refactor wave.

### 🟢 R-010: shared-ui Files Not in src/ Directory
**Component**: `ui/shared-ui`
**Description**: Components are in `components/` and `lib/`, not `src/`. The `check-app-runnability.sh` script reports this as a failure.
**Impact**: Script false positive only. Library works correctly.
**Mitigation**: Acceptable pattern for shared UI libraries. Script accounts for this.

### 🟢 R-011: OpenAPI Specs Missing for Most Services
**Component**: `contracts/openapi/`
**Description**: Only VITO has a comprehensive OpenAPI spec. Other services lack formal API contracts.
**Impact**: No generated client SDKs or API documentation.
**Mitigation**: Generate OpenAPI specs from Spring controllers using springdoc-openapi.

---

## Environment/External Constraints (Cannot Fix in Code)

| Constraint | Impact | Workaround |
|-----------|--------|-----------|
| No Keycloak instance for CI | Cannot run auth-dependent integration tests | Use mock/test token issuer |
| No Kafka broker for CI | Cannot test outbox publisher end-to-end | Testcontainers in integration tests |
| No PostgreSQL for CI | Cannot run Flyway or JPA tests | Testcontainers (already used by GoldenContractIT) |
| MOSIP integration not available | MOSIP link service cannot be tested end-to-end | Graceful degradation + mock in place |
| eLMIS/LIMS/DHIS2 not available | Adapter services cannot be integration tested | Mock adapters in test profiles |

---

## Summary

| Severity | Count | Status |
|----------|-------|--------|
| 🔴 BLOCKER | 2 | Documented, requires team action |
| 🟠 HIGH RISK | 4 | Documented, should address before GA |
| 🟡 MEDIUM RISK | 4 | Documented, address in next wave |
| 🟢 LOW RISK | 3 | Documented, defer as needed |
