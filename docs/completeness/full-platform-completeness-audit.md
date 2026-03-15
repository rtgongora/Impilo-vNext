# Impilo vNext — Full Platform Completeness Audit

> Date: 2026-03-15
> Scope: Complete monorepo — all services, libraries, UIs, mobile apps, infrastructure
> Method: Automated + manual code-level inspection of every component

---

## 1. Executive Summary

The Impilo vNext platform contains **113 classifiable components**: 13 shared libraries, 67 backend services, 24 web UIs, 2 mobile apps, and 7 mobile shared packages.

**Overall assessment: ADEQUATE with targeted gaps**

- **36 components (32%)** are COMPLETE — full implementation with tests
- **69 components (61%)** are ADEQUATE — working but with minor gaps (typically missing tests or README)
- **5 components (4%)** are MINIMAL — very thin implementations
- **1 component (1%)** is FRAGILE — `ui/ehr` is empty and should be removed or documented as superseded
- **2 components (2%)** are MOBILE-READY

The platform has excellent architectural coverage. Every expected service exists, has a pom.xml, Application.java entrypoint, at least one Flyway migration, and at least one test (GoldenContractIT). The TODO.md build tracker is significantly outdated — many items marked as `[ ]` pending have been implemented.

---

## 2. Methodology

### 2.1 Quantitative Analysis
For each component, we measured:
- Source file count (Java/TypeScript)
- Test file count
- Migration count (Flyway V*.sql)
- Helm chart presence
- README presence
- Build configuration (pom.xml / package.json)

### 2.2 Qualitative Inspection
For representative components in each tier, we read source files to verify:
- Real domain logic (not just empty wrappers)
- Proper security configuration
- Outbox/eventing patterns where required
- Controller → Service → Repository layering
- v1.1 Tech Companion compliance

### 2.3 Automated Scripts
Created 5 CI-friendly scripts in `scripts/completeness/`:
- `inspect-components.sh` — enumerate all expected components
- `check-service-minimums.sh` — verify service completeness criteria
- `check-app-runnability.sh` — verify app prerequisites
- `check-doc-and-acceptance-coverage.sh` — doc coverage
- `run-all.sh` — master runner

---

## 3. Key Findings

### 3.1 Strengths

1. **Universal service scaffold**: All 67 services have pom.xml, Application.java, Flyway migrations, and GoldenContractIT
2. **v1.1 Tech Companion compliance**: GoldenContractIT extends GoldenContractSuite for header/idempotency/error envelope compliance across all services
3. **Outbox pattern**: EventOutbox entities found across virtually all write-path services
4. **Security config**: SecurityConfig.java present in all services with dual-mode (INTERNAL/EXTERNAL) support
5. **Ring 0 services deeply implemented**: TSHEPO suite (7 services), VITO (105 src, 25 tests, 19 migrations), TUSO (112 src), VARAPI (109 src) are production-grade
6. **Mobile apps real**: citizen-app (React Native, 41 src, 5 tests) and provider-app (68 src, 13 tests) with proper shared packages
7. **Shared libraries comprehensive**: tech-companion (v1.1 compliance), shared-kernel-java (eventing), tshepo-contracts/sdk (trust) all well-tested

### 3.2 Weaknesses

1. **Test depth gap**: 15 services have only 1-2 test files (typically just GoldenContractIT). No unit tests for domain logic
2. **README coverage**: Only 15 of 67 services have READMEs (22%)
3. **Web UI test desert**: Only 2 of 24 web UIs have any tests (support-console, developer-console)
4. **Empty `ui/ehr`**: Package.json only — superseded by `ui/experience`
5. **Helm chart gaps**: 28 of 67 services reference Helm charts, but the 12 charts in `helm/` lack `/templates/` directories — no Deployment/Service/Ingress templates. Cannot deploy to K8s as-is
6. **TODO.md outdated**: Many items marked as pending are actually implemented
7. **16 OpenAPI contracts exist** but TSHEPO sub-services and experience-bff lack dedicated specs

### 3.3 Risk Areas

1. **No integration test suite**: Individual services have GoldenContractIT but no cross-service integration tests
2. **Mobile messaging package has no tests**: `apps/mobile/packages/mobile-messaging` (6 src, 0 tests)
3. **ops-instrumentation library**: Only 1 test for 8 source files
4. **tech-companion-harness**: 0 tests (though it IS the test harness itself)

---

## 4. Service-by-Service Analysis

### 4.1 Ring 0 — Trust & Governance (7 services)
All COMPLETE. The TSHEPO suite is the most thoroughly implemented component:
- PolicyEngine with 7-step PDP
- ext_authz gRPC integration
- Break-glass, step-up, device risk
- Consent evaluation with Redis caching
- Ed25519 key management with rotation
- SHA-256 audit hash chain

### 4.2 Ring 0 — Registry Spine (9 services)
7 COMPLETE, 2 ADEQUATE.
- VITO is the gold standard: 105 src, 25 tests, 19 migrations, full client lifecycle
- VARAPI and TUSO are 100+ source files each with deep domain logic
- ZIBO (59 src) has full terminology management
- Ubomi and Indawo are thinner but functional

### 4.3 Ring 0 — Clinical Execution (7 services)
4 COMPLETE, 3 ADEQUATE.
- PCT (93 src), OROS (85 src), Pharmacy (85 src) are production-grade
- Butano has HAPI FHIR interceptors for PII prevention
- Inpatient (15 src) is thinner

### 4.4 Ring 0 — Finance (2 services)
Both COMPLETE.
- MUSheX (106 src, 9 tests) — claims adjudication, wallet, reconciliation
- Costing Engine (93 src, 7 tests) — cost models, rule application

### 4.5 Ring 1 — Integration & Ops (16 services)
3 COMPLETE, 13 ADEQUATE.
- Integration Hub (35 src) with route management and dead-letter
- Offline-edge (32 src, 8 tests) is well-tested
- Experience BFF (74 src) provides golden paths
- Adapters (pharmacy-elmis, inventory-elmis, pacs, fhir-gateway, connector-fhir) are thin but functional

### 4.6 Ring 2 — Platform Services (26 services)
8 COMPLETE, 18 ADEQUATE.
- Data platform services (governance, pipeline, warehouse, ingestion) form a complete data lakehouse pattern
- Surveillance, reporting, campaigns, identity-assurance are well-implemented
- Schema registry, search, workflow are functional but thinner

### 4.7 Web UIs (24 apps)
2 COMPLETE, 20 ADEQUATE, 2 MINIMAL, 1 FRAGILE.
- `ui/experience` (125 src) is the main clinical UI
- `support-console` and `developer-console` have tests
- Most domain UIs (10 src average) are functional but untested
- `ui/ehr` is empty — superseded by experience app

### 4.8 Mobile (2 apps + 7 packages)
Both apps MOBILE-READY with React Native.
- citizen-app: 41 src, 5 tests — appointments, prescriptions, telehealth, messaging
- provider-app: 68 src, 13 tests — clinical, offline, queue management
- Shared packages provide API client, auth, design system, offline, trust, timeline

---

## 5. Compliance Verification

### 5.1 v1.1 Request-Path Compliance
✅ **All 67 services** have GoldenContractIT extending GoldenContractSuite.
The GoldenContractSuite validates:
- 14 trust headers (x-tenant-id, x-correlation-id, x-actor-id, x-actor-type, etc.)
- Idempotency-Key header support
- Error envelope format compliance
- Federation authority enforcement

### 5.2 Outbox Pattern
✅ Event outbox found in the vast majority of services. Standard entity: EventOutbox/OutboxEvent with repository and publisher.

### 5.3 Security Configuration
✅ SecurityConfig.java with dual-mode (INTERNAL/EXTERNAL) access control present across all services.

### 5.4 Database Migrations
✅ All services have at least V001 migration. Major services have V002+:
- VITO: 19 migrations (V001–V019)
- TSHEPO: 6 migrations
- TUSO: 4 migrations
- VARAPI: 4 migrations

---

## 6. Recommendations

### 6.1 Immediate (Low-effort, high-value)
1. Remove or formally deprecate `ui/ehr` — it's empty and superseded by `ui/experience`
2. Update TODO.md to reflect actual state
3. Add unit tests to services with only GoldenContractIT (15 services)

### 6.2 Short-term
1. Add basic smoke tests to all web UIs (at least render tests)
2. Complete Helm charts for services missing them (39 services)
3. Add READMEs to the 52 services that lack them

### 6.3 Medium-term
1. Create cross-service integration test suite
2. Add E2E tests for critical golden paths
3. Add mobile-messaging package tests
4. Harden ops-instrumentation library testing

---

## 7. Script Verification Results

### check-service-minimums.sh
- **Services checked**: 67
- **Failures**: 0
- **Warnings**: 8 (services with only 1 test or missing outbox)
- **Result**: ✅ PASS

### check-app-runnability.sh
- **Apps checked**: 26
- **Failures**: 2 (ehr — empty, shared-ui — files not in src/)
- **Warnings**: 28 (mostly missing tests)
- **Result**: ⚠ FAIL (ehr empty)

### check-doc-and-acceptance-coverage.sh
- All acceptance packs present ✅
- All architecture docs present ✅
- 52 services without READMEs ⚠

---

## 8. Conclusion

The Impilo vNext platform is architecturally complete with a strong foundation. All expected services exist and compile. Ring 0 services are production-grade. The primary gaps are in test depth (particularly web UIs and thinner Ring 2 services) and documentation coverage. No components are blocked by external dependencies that would prevent build or local development.

**Overall Classification: ADEQUATE — approaching COMPLETE**
