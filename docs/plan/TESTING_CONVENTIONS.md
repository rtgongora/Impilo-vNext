# Impilo vNext — Testing Conventions (v1.1)

**Version**: 1.0
**Date**: 2026-02-14
**Scope**: All new services (Outstanding 27). Legacy services adopt where feasible.

---

## 1. Testing Pyramid

Every service in the Outstanding 27 MUST include tests at three levels:

```
         ┌──────────────┐
         │  Golden       │  ← 1 class: GoldenContractIT extends GoldenContractSuite
         │  Contract IT  │     Verifies v1.1 compliance (headers, errors, idempotency, federation)
         └──────┬───────┘
                │
     ┌──────────▼──────────┐
     │  Behavior Tests      │  ← 3+ classes per service
     │  (Integration)       │     Verifies domain-specific business logic with Spring context
     └──────────┬──────────┘
                │
  ┌─────────────▼─────────────┐
  │  Unit Tests                │  ← 5+ classes per service
  │  (Pure domain logic)       │     Verifies individual service/engine methods with Mockito
  └────────────────────────────┘
```

---

## 2. GoldenContractIT — v1.1 Compliance Gate

### 2.1 Requirement

Every new service MUST include exactly one test class:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public class MyServiceGoldenContractIT extends GoldenContractSuite {
    // All tests inherited from GoldenContractSuite
}
```

### 2.2 What GoldenContractSuite Tests

The `GoldenContractSuite` in `libs/tech-companion-harness` auto-discovers endpoints and runs these test categories:

#### Category 1: Header Enforcement (4 tests)

| Test | Assertion |
|---|---|
| `missingTenantIdReturns400` | Request without `X-Tenant-ID` → 400 + `MISSING_REQUIRED_HEADER` |
| `missingPodIdReturns400` | Request without `X-Pod-ID` → 400 + `MISSING_REQUIRED_HEADER` |
| `blankTenantIdReturns400` | Request with blank `X-Tenant-ID` → 400 + `MISSING_REQUIRED_HEADER` |
| `allHeadersPresentSucceeds` | Request with all 4 headers → 2xx |

#### Category 2: Error Envelope Format (2 tests)

| Test | Assertion |
|---|---|
| `errorResponseContainsAllFields` | Error response has `error.code`, `error.message`, `error.details`, `error.request_id`, `error.correlation_id` |
| `errorResponseIsJson` | Error response Content-Type is `application/json` |

#### Category 3: Idempotency (3 tests)

| Test | Assertion |
|---|---|
| `missingIdempotencyKeyReturns400` | POST without `Idempotency-Key` → 400 + `IDEMPOTENCY_KEY_REQUIRED` |
| `sameKeyAndBodyReplays` | Same key + same body → identical response (replay) |
| `sameKeyDifferentBodyReturns409` | Same key + different body → 409 + `IDENTITY_CONFLICT` |

#### Category 4: Federation Authority (2 tests)

| Test | Assertion |
|---|---|
| `privatePodReturns403` | Non-national pod on `@NationalOnly` endpoint → 403 + `FEDERATION_AUTHORITY_VIOLATION` |
| `nationalPodSucceeds` | National pod on `@NationalOnly` endpoint → 2xx |

### 2.3 Auto-Discovery

`GoldenContractSuite` uses `EndpointDiscovery` to find real endpoints:
- **Read endpoint**: First `GET /internal/v1/**` mapping
- **Command endpoint**: First `POST /internal/v1/**` mapping
- **Federation endpoint**: Must be provided via `getFederationEndpointOverride()`

If a service has no v1.1 endpoints, tests are **SKIPPED** (not failed).
If a service has no command endpoint, idempotency tests are **SKIPPED**.
If no federation endpoint is configured, federation tests are **SKIPPED**.

### 2.4 Overriding Discovery

```java
public class VarapiGoldenContractIT extends GoldenContractSuite {

    @Override
    protected String getReadEndpointOverride() {
        return "/internal/v1/providers";
    }

    @Override
    protected String getCommandEndpointOverride() {
        return "/internal/v1/providers";
    }

    @Override
    protected String getFederationEndpointOverride() {
        return "/internal/v1/providers";  // @NationalOnly for provider creation
    }
}
```

### 2.5 Test Dependencies

```xml
<dependency>
    <groupId>zw.gov.mohcc.impilo</groupId>
    <artifactId>tech-companion-harness</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>zw.gov.mohcc.impilo</groupId>
    <artifactId>tech-companion-mock</artifactId>
    <scope>test</scope>
</dependency>
```

---

## 3. Behavior Tests — Domain Logic

### 3.1 Requirement

Every new service MUST include at least 3 behavior test classes that verify domain-specific business logic.

### 3.2 Naming Convention

```
{ServiceName}{Domain}Test.java         — Unit test (Mockito, no Spring context)
{ServiceName}{Domain}IT.java           — Integration test (SpringBootTest, H2 or Testcontainers)
```

Examples:
- `VarapiPrivilegingServiceTest.java` — unit test for privilege grant/revoke logic
- `TusoControlTowerIT.java` — integration test for alert rule evaluation with DB
- `SchedulingSlotGenerationTest.java` — unit test for slot generation algorithm

### 3.3 Per-Service Behavior Test Matrix

#### Ring 0 — Registry Services

| Service | Required Behavior Tests | Focus |
|---|---|---|
| varapi-service | `ProviderLifecycleTest`, `PrivilegingServiceTest`, `CredentialingWorkflowTest`, `CouncilSyncTest`, `SnapshotEndpointIT` | Practitioner state machine, privilege grant/revoke, credentialing workflow, snapshot pagination |
| tuso-service | `FacilityHierarchyTest`, `CapabilityRegistryTest`, `ControlTowerRulesTest`, `ResourceCalendarTest`, `SnapshotEndpointIT` | Hierarchy CRUD, capability matching, alert rule evaluation, slot availability, snapshot |
| msika-service | `ProductCatalogTest`, `TariffEffectiveDateTest`, `FormularyServiceTest`, `PackManagementTest`, `SnapshotEndpointIT` | CRUD, tariff date range queries, formulary validation, pack assignment, snapshot |
| ubomi-service | `BirthNotificationTest`, `DeathNotificationTest`, `VerificationServiceTest`, `CrvsReconciliationTest` | Birth→VITO event, death→trust.revocation event, verification logic, reconciliation |

#### Ring 1 — Clinical Services

| Service | Required Behavior Tests | Focus |
|---|---|---|
| inpatient-service | `BedManagementTest`, `WardAllocationTest`, `TransferServiceTest`, `DischargePlanningTest` | Bed status tracking, acuity-based assignment, inter-ward transfer, discharge readiness |
| scheduling-service | `SlotGenerationTest`, `AppointmentBookingTest`, `WaitListTest`, `CapacityManagementIT` | Template-based slots, booking lifecycle, wait-list priority, overbooking handling |
| referral-service | `ReferralRoutingTest`, `CareNetworkTest`, `CounterReferralTest` | Capability matching, multi-facility coordination, feedback loop |

#### Ring 2 — Integration & Data Services

| Service | Required Behavior Tests | Focus |
|---|---|---|
| fhir-gateway-service | `BundleRoutingTest`, `QueryTranslationTest`, `ConsentEnforcementTest` | FHIR Bundle processing, search→REST translation, consent check |
| pacs-adapter-service | `StudyCorrelationTest`, `DicomProxyTest`, `ButanoWritebackTest` | Study-to-order matching, DICOM proxy, ImagingStudy FHIR creation |
| offline-sync-service | `DataPackGenerationTest`, `CrdtMergeTest`, `ReconciliationTest` | Pack contents, conflict-free merge, offline action reconciliation |
| jobs-service | `JobSchedulingTest`, `JobExecutionTest`, `DeadLetterTest` | Cron parsing, execution tracking, retry/dead-letter handling |
| analytics-pipeline-service | `EtlTransformTest`, `AggregationTest`, `ReportScheduleTest` | Event→aggregate transform, dimensional aggregation, schedule trigger |
| surveillance-service | `CaseDetectionTest`, `ThresholdAlertTest`, `Dhis2PushTest` | Rule-based detection, epidemic threshold breach, DHIS2 format |
| data-governance-service | `DeIdentificationTest`, `ExportLifecycleTest`, `ConsentCheckTest` | k-anonymity, request lifecycle, consent verification |
| developer-portal-service | `ApiKeyManagementTest`, `SandboxProvisioningTest`, `SpecAggregationTest` | Key generation/rotation, tenant isolation, OpenAPI merge |

#### Platform Components

| Component | Required Behavior Tests | Focus |
|---|---|---|
| consistency-class-enforcement | `ClassAEnforcerTest`, `ClassBStalenessTest`, `ClassCEntitlementTest` | Sync check blocking, staleness threshold, entitlement validation |
| decision-evidence-pipeline | `DecisionEvidencePublisherTest`, `DecisionEvidenceSerializationTest` | Outbox persistence, JSON round-trip |
| federation-control-module | `PodRegistrationTest`, `AuthorityResolutionTest`, `RevocationChannelTest` | Pod lifecycle, domain authority lookup, high-priority publish |
| delta-snapshot-framework | `DeltaTrackerTest`, `SnapshotPaginationTest`, `CursorEncodingTest` | Field comparison, cursor-based page, cursor encoding/decoding |
| vault-kms-integration | `VaultKmsProviderTest`, `KeyWrappingTest`, `CpidDerivationTest` | Vault API call, encrypt/decrypt, HMAC derivation |
| contract-testing-gate | `SchemaCompatibilityTest`, `BackwardCompatibilityTest` | Schema validation, breaking change detection |
| chaos-resilience-framework | `ServiceKillerTest`, `CircuitBreakerTest` | Graceful degradation, fallback activation |

---

## 4. Test Infrastructure

### 4.1 Database Configuration (Test Profile)

All behavior/integration tests use H2 in PostgreSQL compatibility mode:

```yaml
# application-test.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate.dialect: org.hibernate.dialect.H2Dialect
  flyway:
    enabled: false  # Flyway disabled in tests; JPA creates schema
```

### 4.2 Kafka Configuration (Test Profile)

Tests that do not need Kafka use `@MockBean KafkaTemplate`:

```java
@SpringBootTest
@ActiveProfiles("test")
class MyServiceIT {
    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;
}
```

Tests that need real Kafka use embedded Kafka:

```java
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"clinical.pct.journey.created"})
class MyKafkaIT { ... }
```

### 4.3 External Service Mocking

Tests for services that call other services use `tech-companion-mock`:

```java
@SpringBootTest
@ActiveProfiles("test")
class VarapiServiceIT {
    @MockBean
    private TshepoAuthzClient tshepoClient;  // from tech-companion-mock

    @BeforeEach
    void setup() {
        when(tshepoClient.evaluate(any())).thenReturn(AuthzResponse.allow());
    }
}
```

### 4.4 Test Data Conventions

| Convention | Rule |
|---|---|
| Tenant ID | Use `"moh-zw"` or UUID `"f47ac10b-58cc-4372-a567-0e02b2c3d479"` |
| Pod ID | Use `"national-spine"` for national, `"pod-test-01"` for pod tests |
| Actor ID | Use `"test-actor-001"` |
| Actor Type | Use `SYSTEM` for automated tests |
| Facility ID | Use `"fac-test-001"` |
| CPID | Use `"cpid-test-abcde"` |
| CRID | Use `"crid-test-12345"` |

---

## 5. Definition of Done — Per Service

Every service in the Outstanding 27 MUST satisfy ALL of the following before it is considered complete:

### 5.1 Code Quality

| # | Criterion | Verification Method |
|---|---|---|
| DoD-1 | Compiles without warnings (`-Xlint:all`) | `mvn compile` with warnings-as-errors |
| DoD-2 | No unused imports or dead code | IDE inspection or SpotBugs |
| DoD-3 | Follows package convention: `zw.gov.mohcc.impilo.{service}.[api|core|persistence|events|config]` | Directory structure review |

### 5.2 v1.1 Compliance

| # | Criterion | Verification Method |
|---|---|---|
| DoD-4 | `GoldenContractIT extends GoldenContractSuite` passes all applicable categories | `mvn test` in CI |
| DoD-5 | All endpoints use `/internal/v1/**` prefix | GoldenContractSuite endpoint discovery |
| DoD-6 | Required headers enforced: X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID | GoldenContractSuite header tests |
| DoD-7 | Idempotency-Key enforced on POST/PUT/PATCH | GoldenContractSuite idempotency tests |
| DoD-8 | Error responses use canonical error envelope | GoldenContractSuite error envelope tests |
| DoD-9 | Federation authority checked on `@NationalOnly` endpoints | GoldenContractSuite federation tests |

### 5.3 Data & Events

| # | Criterion | Verification Method |
|---|---|---|
| DoD-10 | Flyway V001 migration creates all domain tables + event_outbox | `mvn flyway:migrate` on fresh DB |
| DoD-11 | Event outbox uses v1.1 schema (all mandatory columns) | Schema comparison test |
| DoD-12 | Events use correct bus channel and topic naming | OutboxPublisher topic routing review |
| DoD-13 | CREATE events carry full state; UPDATE events carry delta only | Event payload assertions in behavior tests |
| DoD-14 | Idempotency table present with TTL expiry | Flyway migration review |

### 5.4 Testing

| # | Criterion | Verification Method |
|---|---|---|
| DoD-15 | At least 5 unit tests covering core domain logic | Test count in `mvn test` output |
| DoD-16 | At least 3 behavior/integration tests | Test count in `mvn test` output |
| DoD-17 | GoldenContractIT (1 class, inherits 11 tests) | CI test phase |
| DoD-18 | All tests pass on `mvn verify` | CI pipeline |

### 5.5 Infrastructure

| # | Criterion | Verification Method |
|---|---|---|
| DoD-19 | Helm chart in `helm/<service-name>/` with Chart.yaml + values.yaml | Directory existence |
| DoD-20 | Dockerfile present and builds successfully | `docker build .` |
| DoD-21 | application.yml with correct port, datasource, Kafka config | Configuration review |
| DoD-22 | Database added to `scripts/seed/init-databases.sql` | File content check |
| DoD-23 | Service module listed in parent `services/pom.xml` | POM review |

### 5.6 Documentation

| # | Criterion | Verification Method |
|---|---|---|
| DoD-24 | OpenAPI contract in `contracts/openapi/{service}.openapi.yaml` | File existence |
| DoD-25 | Event schemas in `contracts/schemas/events/` for all emitted event types | File existence |
| DoD-26 | Service listed in `docs/plan/SERVICE_CATALOG.md` with correct port/DB | Catalog review |

---

## 6. CI Pipeline Structure

### 6.1 Per-PR Pipeline

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  Compile     │────▶│  Unit Tests  │────▶│  Integration │────▶│  Schema     │
│  (mvn        │     │  (mvn test)  │     │  Tests       │     │  Compat     │
│   compile)   │     │              │     │  (mvn verify)│     │  Check      │
└─────────────┘     └─────────────┘     └─────────────┘     └─────────────┘
                                                                    │
                                                              ┌─────▼─────┐
                                                              │  PR Gate  │
                                                              │  Pass/Fail│
                                                              └───────────┘
```

### 6.2 Gate Criteria

| Gate | Threshold | Action on Failure |
|---|---|---|
| Compilation | 0 errors | Block merge |
| Unit tests | 100% pass | Block merge |
| GoldenContractIT | 100% pass (non-skipped) | Block merge |
| Behavior tests | 100% pass | Block merge |
| Schema compatibility | BACKWARD compatible | Block merge |
| Test coverage | No mandatory threshold (quality over quantity) | Warning only |

### 6.3 Test Execution Order

```bash
# Full verification for a single service
mvn -pl {service-module} -am clean verify

# Run only golden contract test
mvn -pl {service-module} -am test -Dtest="*GoldenContractIT"

# Run only behavior tests
mvn -pl {service-module} -am test -Dtest="*Test,*IT" -Dexclude="*GoldenContractIT"
```

---

## 7. Test Checklist Template

Use this checklist when completing each service in the Outstanding 27:

```markdown
### {Service Name} — Test Checklist

#### Golden Contract (1 class)
- [ ] `{Service}GoldenContractIT extends GoldenContractSuite` — created
- [ ] Header enforcement tests pass (4/4 or skipped)
- [ ] Error envelope tests pass (2/2 or skipped)
- [ ] Idempotency tests pass (3/3 or skipped)
- [ ] Federation tests pass (2/2 or skipped)

#### Behavior Tests (3+ classes)
- [ ] `{Domain1}Test.java` — {description}
- [ ] `{Domain2}Test.java` — {description}
- [ ] `{Domain3}IT.java` — {description}

#### Unit Tests (5+ classes)
- [ ] `{Service1}Test.java` — {description}
- [ ] `{Service2}Test.java` — {description}
- [ ] `{Service3}Test.java` — {description}
- [ ] `{Service4}Test.java` — {description}
- [ ] `{Service5}Test.java` — {description}

#### Event Tests
- [ ] Outbox entity has all v1.1 fields
- [ ] CREATE events carry full state
- [ ] UPDATE events carry delta only
- [ ] Topic routing matches convention

#### DoD Sign-Off
- [ ] All 26 DoD criteria satisfied
- [ ] `mvn verify` passes with 0 failures
```

---

## 8. DoD Checklist — This Document Set

| # | Criterion | Status |
|---|---|---|
| 1 | `docs/plan/IMPILO_VNEXT_BUILD_PLAN.md` created with all 27 components, 6 bundles, P20-P30 sequence | DONE |
| 2 | `docs/plan/SERVICE_CATALOG.md` created with service name, module path, port, DB, responsibilities | DONE |
| 3 | `docs/plan/EVENTING_AND_TOPICS.md` created with EventEnvelope, outbox, topic naming, emit-mode | DONE |
| 4 | `docs/plan/API_CONVENTIONS_V11.md` created with /internal/v1/**, headers, errors, idempotency | DONE |
| 5 | `docs/plan/TESTING_CONVENTIONS.md` created with GoldenContractIT, behavior tests, DoD | DONE |
| 6 | No placeholders or TODOs in any document | DONE |
| 7 | All content verifiable from repo contents only | DONE |
| 8 | No modifications to existing 16 legacy services | DONE |
| 9 | Commit message follows conventional commits | PENDING (commit step) |
| 10 | Push to current branch | PENDING (push step) |
