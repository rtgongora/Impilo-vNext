# Impilo vNext — Authoritative Build Plan (Outstanding 27)

**Version**: 1.0
**Date**: 2026-02-14
**Status**: APPROVED — Canonical reference for Prompts 20–30
**Scope**: 27 v1.1-native platform components across 6 bundles

---

## 1. Background

Impilo vNext currently has **16 fully-implemented legacy services** (TSHEPO cluster ×6, VITO, BUTANO, PCT, OROS, Pharmacy, Inventory, MSIKA Flow, COSTA, MUSHEX, ZIBO) plus **3 v1.1-native integration services** (integration-hub, notification-service, rules-service) and **7 supporting services** (card-print-agent, landela-adapter, document-service, credential-verification-service, share-slip-service, pharmacy-elmis-adapter, inventory-elmis-adapter).

A v1.1 compliance audit (`docs/architecture/v1.1/00-compliance-summary.md`) found the platform at **~15% compliance** with Manifest v1.1. The gap analysis identified 13 of 18 requirements as NOT IMPLEMENTED.

The **Outstanding 27** are the remaining platform components required to achieve full v1.1 compliance and complete the national health operating system. They are organized into 6 platform bundles with a strict rollout sequence gated by dependency constraints.

---

## 2. Constraints

| # | Constraint | Enforcement |
|---|---|---|
| C1 | No modification to existing 16 legacy services during this plan | All 27 components are new services, libraries, or infrastructure |
| C2 | New services MUST use `/internal/v1/**` routing prefix | Enforced by tech-companion HeaderEnforcementFilter |
| C3 | New services MUST depend on `tech-companion` for v1.1 compliance | Parent POM enforces dependency |
| C4 | New services MUST include `GoldenContractIT extends GoldenContractSuite` | CI gate rejects without passing golden tests |
| C5 | Ring 0 services have zero dependencies on Ring 1+ | Dependency scanner in CI |
| C6 | Each service gets its own PostgreSQL database | `scripts/seed/init-databases.sql` extended per prompt |
| C7 | Every service uses the outbox pattern with v1.1 EventEnvelope | Verified by GoldenContractSuite event assertions |

---

## 3. Platform Bundles

### Bundle I — Integration (5 components)

Cross-system connectivity, interoperability, and message routing.

| # | Component | Type | Ring | Prompt |
|---|---|---|---|---|
| 1 | fhir-gateway-service | Deployable service | 2 | P21 |
| 2 | pacs-adapter-service | Deployable service | 2 | P21 |
| 3 | offline-sync-service | Deployable service | 2 | P21 |
| 4 | jobs-service | Deployable service | 2 | P21 |
| 5 | bus-topology | Kafka configuration + migration | Infra | P20 |

### Bundle D — Data & Analytics (5 components)

Data pipelines, reporting, schema governance, event evolution.

| # | Component | Type | Ring | Prompt |
|---|---|---|---|---|
| 6 | schema-registry | Infrastructure deployment | Infra | P20 |
| 7 | analytics-pipeline-service | Deployable service | 2 | P26 |
| 8 | surveillance-service | Deployable service | 2 | P26 |
| 9 | data-governance-service | Deployable service | 2 | P26 |
| 10 | delta-snapshot-framework | Shared library | Lib | P25 |

### Bundle S — IoT & Supply (3 components)

Clinical resource scheduling, bed management, supply optimization.

| # | Component | Type | Ring | Prompt |
|---|---|---|---|---|
| 11 | scheduling-service | Deployable service | 1 | P27 |
| 12 | inpatient-service | Deployable service | 1 | P27 |
| 13 | supply-planning-module | Library module | 2 | P28 |

### Bundle X — Experience (6 components)

Registry completion, developer portal, care coordination.

| # | Component | Type | Ring | Prompt |
|---|---|---|---|---|
| 14 | varapi-service | Deployable service | 0 | P22 |
| 15 | tuso-service | Deployable service | 0 | P22 |
| 16 | msika-service (complete) | Deployable service | 0 | P23 |
| 17 | ubomi-service (complete) | Deployable service | 0 | P23 |
| 18 | referral-service | Deployable service | 1 | P27 |
| 19 | developer-portal-service | Deployable service | 2 | P28 |

### Bundle A — Assurance (4 components)

Safety classification, compliance enforcement, testing gates.

| # | Component | Type | Ring | Prompt |
|---|---|---|---|---|
| 20 | consistency-class-enforcement | Framework module | 0 | P24 |
| 21 | decision-evidence-pipeline | Framework module | 0 | P24 |
| 22 | federation-control-module | Module in TSHEPO | 0 | P24 |
| 23 | contract-testing-gate | CI pipeline + lib | Infra | P30 |

### Bundle R — Resilience & Ops (4 components)

Infrastructure hardening, secrets management, observability, disaster recovery.

| # | Component | Type | Ring | Prompt |
|---|---|---|---|---|
| 24 | vault-kms-integration | Infrastructure + lib | Infra | P29 |
| 25 | observability-stack | Infrastructure | Infra | P29 |
| 26 | dr-orchestration | Runbooks + automation | Infra | P30 |
| 27 | chaos-resilience-framework | Test framework | Infra | P30 |

---

## 4. Rollout Sequence (Prompts 20–30)

### P20 — Platform Foundation

**Gate**: Nothing in P21+ proceeds until P20 exits.
**Duration estimate**: Single prompt session

#### Deliverables

| # | Deliverable | Description |
|---|---|---|
| 20.1 | `libs/shared-kernel-java` enhancements | Add `DeltaTracker`, `SnapshotContract`, `ConsistencyClass` enum, `DecisionEvidence` DTO to existing shared-kernel-java |
| 20.2 | `libs/tech-companion` enhancements | Add `ConsistencyClassFilter`, `StalenessHeader`, `PodIdEnforcement` to existing tech-companion auto-config |
| 20.3 | Schema Registry in docker-compose | Add Apicurio Registry container, port 8180, health check |
| 20.4 | Schema Registry Helm chart | `helm/schema-registry/Chart.yaml` + `values.yaml` |
| 20.5 | Event schema definitions | JSON Schema files for v1.1 event envelope + delta payload in `contracts/schemas/` |
| 20.6 | Bus topology Kafka configuration | 5-channel topic creation script: `trust.*`, `kernel.*`, `clinical.*`, `telemetry.*`, `analytics.*` |
| 20.7 | `scripts/seed/init-databases.sql` update | Add databases for new services: `varapi`, `tuso`, `scheduling`, `referral`, `analytics_pipeline`, `surveillance`, `data_governance`, `developer_portal` |

#### Exit Criteria

- [ ] Schema Registry container starts and responds to health check
- [ ] `ConsistencyClass` enum compiles in shared-kernel-java
- [ ] `DeltaTracker.computeDelta(old, new)` returns changed fields map
- [ ] 5 Kafka topic prefixes configured with differentiated retention policies
- [ ] Event envelope JSON Schema v1 registered in Schema Registry
- [ ] `tech-companion` auto-config injects `ConsistencyClassFilter` when on classpath

#### Stop Condition

If Schema Registry cannot be configured to validate JSON Schema (Apicurio may not support JSON Schema natively), fall back to Confluent Schema Registry with JSON Schema support or implement CI-only schema validation using JSON Schema validation in the Maven build.

---

### P21 — Integration Services

**Gate**: P20 complete.
**Dependencies**: bus-topology (P20.6), schema-registry (P20.3)

#### Deliverables

| # | Service | Module Path | Port | DB |
|---|---|---|---|---|
| 21.1 | fhir-gateway-service | `services/fhir-gateway-service` | 8113 | `fhir_gateway` |
| 21.2 | pacs-adapter-service | `services/pacs-adapter-service` | 8114 | `pacs_adapter` |
| 21.3 | offline-sync-service | `services/offline-sync-service` | 8115 | `offline_sync` |
| 21.4 | jobs-service | `services/jobs-service` | 8116 | `jobs` |

All four services MUST:
- Depend on `tech-companion` for header enforcement, idempotency, federation authority
- Include `GoldenContractIT extends GoldenContractSuite`
- Use `EventOutboxEntity` with all v1.1 fields
- Emit events to the correct bus channel
- Include Flyway V001 migration creating domain tables + `event_outbox`
- Include Helm chart in `helm/<service-name>/`

#### Per-Service Responsibilities

**fhir-gateway-service (8113)**:
- FHIR R4 Bundle routing (transaction/batch/document)
- Query translation (FHIR search → service-specific REST)
- Token-to-CPID resolution via TSHEPO Identity client
- ZIBO terminology validation interceptor
- Consent enforcement via TSHEPO Consent client
- Emits: `clinical.fhir.bundle.processed`, `clinical.fhir.query.executed`

**pacs-adapter-service (8114)**:
- DICOM C-STORE/C-FIND/C-MOVE proxy to Orthanc
- Study correlation: maps Orthanc Study UID → OROS order ID
- BUTANO writeback: creates ImagingStudy FHIR resource
- Emits: `clinical.pacs.study.received`, `clinical.pacs.study.correlated`

**offline-sync-service (8115)**:
- Edge data pack generation (FHIR Bundle + entitlements)
- Conflict-free upload processing (CRDT merge strategy)
- Reconciliation queue for offline actions
- Emits: `integration.sync.pack.generated`, `integration.sync.upload.reconciled`

**jobs-service (8116)**:
- Cron-like job definitions with tenant/pod scope
- Job execution tracking (PENDING → RUNNING → COMPLETED/FAILED)
- Dead-letter re-queue for failed jobs
- Emits: `integration.jobs.executed`, `integration.jobs.failed`

#### Exit Criteria

- [ ] All 4 services compile, start, and pass `GoldenContractIT`
- [ ] fhir-gateway-service routes a FHIR Bundle to BUTANO in integration test
- [ ] pacs-adapter-service correlates a study to an OROS order in unit test
- [ ] offline-sync-service generates a data pack containing FHIR resources
- [ ] jobs-service executes a scheduled job and records completion

#### Stop Condition

If HAPI FHIR client library causes classpath conflicts with tech-companion, isolate fhir-gateway-service into a separate Maven build profile and resolve dependency tree before continuing.

---

### P22 — Ring 0 Registry Build-Out (VARAPI + TUSO)

**Gate**: P20 complete. P21 may run in parallel.
**Dependencies**: shared-kernel-java enhancements (P20.1), tech-companion (P20.2)

#### Deliverables

| # | Service | Module Path | Port | DB |
|---|---|---|---|---|
| 22.1 | varapi-service | `services/varapi-service` | 8083 | `varapi` |
| 22.2 | tuso-service | `services/tuso-service` | 8084 | `tuso` |

**varapi-service (8083) — Provider Registry**:
- Practitioner identity lifecycle: PROVISIONAL → VERIFIED → ACTIVE → SUSPENDED → DEREGISTERED
- Licensure verification: council sync adapters (MCAZ, NMCZ, PCAZ, AHPCZ)
- Privilege management: facility-scoped, role-based privilege grants/revocations
- Credentialing workflow: application → verification → committee review → approval/rejection
- CPD tracking: continuous professional development credit management
- Revocation propagation: emit to `trust.revocation.privilege` on privilege revoke
- Snapshot endpoint: `GET /internal/v1/providers/snapshot`
- Delta events on `kernel.varapi.provider.*`
- Tables: practitioners, licensures, privileges, credentials, cpd_records, council_sync_log, event_outbox
- Controllers: ProviderController, CouncilController, CredentialingController, PrivilegeController, CpdController, OpsController

**tuso-service (8084) — Facility Registry**:
- Facility hierarchy: country → province → district → facility → department → workspace
- Capability registry: what services each facility can provide, operating hours, equipment
- Resource calendar: bookable resources (rooms, equipment, clinicians) with availability slots
- Control tower: real-time occupancy snapshots, alert rules, bed management, queue depth
- Telemetry ingest: receives operational metrics from PCT/OROS for dashboard rendering
- Facility federation: pod-aware facility ID mapping for cross-pod resolution
- Snapshot endpoint: `GET /internal/v1/facilities/snapshot`
- Delta events on `kernel.tuso.facility.*`, telemetry on `telemetry.tuso.occupancy.*`
- Tables: facilities, departments, workspaces, capabilities, resources, resource_calendar, control_tower_snapshots, alert_rules, event_outbox
- Controllers: FacilityController, WorkspaceController, ResourceController, RosterController, ControlTowerController, ConfigController, GofrSyncController

#### Exit Criteria

- [ ] varapi-service: Practitioner registration → licensure verification → privilege grant flow passes
- [ ] varapi-service: Privilege revocation emits `trust.revocation.privilege` event with correct envelope
- [ ] varapi-service: Snapshot endpoint returns paginated provider records with cursor
- [ ] tuso-service: Facility hierarchy CRUD with department/workspace nesting works
- [ ] tuso-service: Control tower snapshot ingest and alert rule evaluation passes
- [ ] tuso-service: Facility federation mapping resolves cross-pod facility IDs
- [ ] Both pass `GoldenContractIT`

#### Stop Condition

If VARAPI council sync requires external system connectivity not available in local dev, implement council sync as a pluggable adapter interface with a `StubCouncilAdapter` for dev/test and defer real adapter to P28.

---

### P23 — Ring 0 Registry Completion (MSIKA + UBOMI)

**Gate**: P22 complete (VARAPI needed for UBOMI death→identity flow).
**Dependencies**: VITO (existing), VARAPI (P22)

#### Deliverables

| # | Service | Module Path | Port | DB |
|---|---|---|---|---|
| 23.1 | msika-service (complete) | `services/msika-service` | 8086 | `msika` |
| 23.2 | ubomi-service (complete) | `services/ubomi-service` | 8186 | `ubomi` |

**msika-service (8086) — Product & Service Registry**:
- Complete the partial skeleton (has TariffController, Flyway V001)
- Product catalog CRUD: items, categories, unit of measure, GTIN barcodes
- Service catalog CRUD: clinical services, billing codes, HCPCS/CPT mappings
- Tariff engine: effective-dated pricing, versioned tariff schedules, currency support
- Pack management: curated bundles of products/services per facility scope
- Import/export: CSV and FHIR Bundle (ValueSet representation)
- Formulary management: approved item lists per facility/tenant
- Consolidate `product-registry-service` into `msika-service` (Law 9: module-first)
- Snapshot endpoint: `GET /internal/v1/catalog/snapshot`
- Delta events on `kernel.msika.catalog.*`
- Additional tables: products, service_items, categories, formulary_items, pack_items
- Controllers: ProductController, ServiceCatalogController, FormularyController, PackController, ImportExportController

**ubomi-service (8186) — CRVS Interface**:
- Complete the partial skeleton (has Birth/Death controllers, Flyway V001)
- Birth notification service: validate → register → issue newborn ID (via VITO event)
- Death notification service: validate → certify → flag deceased (via VITO event)
- Vital event verification: civil registry interop client
- CRVS reconciliation: match Impilo records against civil registry
- Death event propagation: emit to `trust.revocation.identity` for death-triggered identity updates
- Reporting obligations: emit birth/death aggregates to `analytics.reporting.crvs`
- Additional tables: birth_details, death_details, verification_results, reconciliation_queue
- Controllers (complete existing): BirthNotificationController, DeathNotificationController, VerificationController, ReconciliationController

#### Exit Criteria

- [ ] msika-service: Product CRUD + tariff effective-date query + pack assignment works
- [ ] msika-service: Snapshot endpoint returns catalog items with delta cursor
- [ ] msika-service: `product-registry-service` directory deprecated (README pointing to msika-service)
- [ ] ubomi-service: Birth notification → VITO newborn ID issuance event chain works
- [ ] ubomi-service: Death notification → `trust.revocation.identity` propagation works
- [ ] Both pass `GoldenContractIT`

#### Stop Condition

If VITO's existing event format is incompatible with UBOMI's required birth/death event consumption, create a translation adapter within ubomi-service rather than modifying VITO (C1 constraint).

---

### P24 — Assurance Framework

**Gate**: P20 complete. P22 recommended but not blocking.
**Dependencies**: shared-kernel-java (P20.1), tech-companion (P20.2), tshepo-contracts (existing)

#### Deliverables

| # | Component | Location | Description |
|---|---|---|---|
| 24.1 | consistency-class-enforcement | `libs/tech-companion` | Class A/B/C enforcement interceptors |
| 24.2 | decision-evidence-pipeline | `libs/shared-kernel-java` | Decision evidence DTOs + audit publisher |
| 24.3 | federation-control-module | `libs/federation-connector` | Pod registration, authority boundaries, revocation routing |
| 24.4 | action-classification-seed | `contracts/schemas/action-classification.json` | Initial classification of all platform actions into A/B/C |

**consistency-class-enforcement (24.1)**:
- `ConsistencyClassFilter` (OncePerRequestFilter): reads action from HTTP method + path, looks up classification, injects `x-consistency-class` header
- `ClassAEnforcer`: blocks request if sync Kernel check fails (calls tshepo-authz); allows under break-glass with elevated audit
- `ClassBStalenessChecker`: reads `x-projection-staleness-ms` from upstream; denies if exceeds threshold
- `ClassCEntitlementValidator`: validates signed offline entitlement token (signature, scope, expiry, device binding)
- Auto-configured via `@ConditionalOnProperty("impilo.consistency-class.enabled")`

**decision-evidence-pipeline (24.2)**:
- `DecisionEvidence` record: actor, patientReference, action, consistencyClass, decision (ALLOW/DENY/BREAK_GLASS), reasonCodes, policyVersion, projectionStalenessMs, maxAllowedStalenessMs, breakGlass, context map
- `DecisionEvidencePublisher`: persists to outbox with event type `trust.decision_evidence`
- `DecisionEvidenceInterceptor`: captures evidence from TrustContext after each request

**federation-control-module (24.3)**:
- `PodEntity`: podId, name, deploymentLevel, organization, status, authorityDeclarations, federationEndpoint
- `AuthorityBoundaryEntity`: podId, domain, authorityType (NATIONAL_AUTHORITATIVE/POD_AUTHORITATIVE/HYBRID/CONSUMER_ONLY), syncDirection
- `FederationControlService`: pod registration, authority resolution, reporting obligation enforcement
- `HighPriorityChannelPublisher`: publishes to `trust.revocation.*` topics with acks=all, min.insync.replicas=2
- `RevocationConsumer`: listens on `trust.revocation.*`, updates local consent/privilege caches
- Lives in `libs/federation-connector` as a library; TSHEPO sub-services and new services depend on it

**action-classification-seed (24.4)**:
- JSON file listing every known action across all services with its consistency class, staleness limit, and rationale
- Matches the classification table defined in `docs/architecture/v1.1/06-consistency-classes.md`
- Loaded by `ConsistencyClassFilter` at startup (classpath resource)

#### Exit Criteria

- [ ] `ConsistencyClassFilter` blocks a Class A action when sync check fails
- [ ] `ConsistencyClassFilter` allows a Class A action under break-glass with elevated audit
- [ ] `ClassBStalenessChecker` denies when staleness exceeds threshold
- [ ] `DecisionEvidence` record round-trips through JSON serialization
- [ ] `FederationControlService` registers a pod and resolves authority for a domain
- [ ] `HighPriorityChannelPublisher` publishes to `trust.revocation.consent` with acks=all

#### Stop Condition

If OPA integration is required for Class A enforcement (per v1.1 Law 2), implement as an optional `OpaClassAEnforcer` behind a feature flag. The default Java `ClassAEnforcer` remains operational for environments without OPA.

---

### P25 — Data & Analytics Infrastructure

**Gate**: P20 complete.
**Dependencies**: schema-registry (P20.3)

#### Deliverables

| # | Component | Location | Description |
|---|---|---|---|
| 25.1 | delta-snapshot-framework | `libs/shared-kernel-java` | `DeltaTracker`, `SnapshotController` base class, `SnapshotResponse` DTO |
| 25.2 | CI schema validation | `.github/workflows/schema-check.yml` | GitHub Actions workflow validating event schemas on PR |
| 25.3 | Event schema catalog | `contracts/schemas/events/` | JSON Schema files for all known event types |

**delta-snapshot-framework (25.1)**:
- `DeltaTracker<T>`: compares two entity states via reflection, produces `Map<String, ChangedField>` with old/new values; ignores `@DeltaIgnore`-annotated fields
- `ChangedField` record: fieldName, oldValue, newValue
- `SnapshotResponse<T>` record: snapshotTimestamp, schemaVersion, producer, tenantId, podId, resourceType, totalCount, items (List<SnapshotItem<T>>), nextCursor, hasMore
- `SnapshotItem<T>` record: subjectId, entityVersion, lastModifiedAt, state (T)
- `AbstractSnapshotController<T>`: base REST controller providing `GET /internal/v1/{resource}/snapshot?cursor={c}&limit={l}&since={ts}` with cursor-based pagination
- `@EnableSnapshot` annotation for auto-registering snapshot endpoint

**CI schema validation (25.2)**:
- Runs on every PR that modifies `contracts/schemas/`
- Validates JSON Schema syntax
- Checks backward compatibility: new schema must be able to read events produced by previous schema version
- Fails PR if compatibility check fails

**Event schema catalog (25.3)**:
- `contracts/schemas/events/event-envelope.v1.schema.json` — canonical envelope
- `contracts/schemas/events/delta-payload.v1.schema.json` — delta payload
- `contracts/schemas/events/snapshot-response.v1.schema.json` — snapshot response
- Per-domain event schemas: `kernel.vito.client.*.v1.schema.json`, `clinical.pct.journey.*.v1.schema.json`, etc.

#### Exit Criteria

- [ ] `DeltaTracker` computes correct changed fields between two entity instances
- [ ] `AbstractSnapshotController` serves paginated snapshot with cursor
- [ ] CI schema validation workflow passes for existing schemas
- [ ] CI schema validation workflow rejects a breaking schema change
- [ ] At least 10 event schemas registered in `contracts/schemas/events/`

#### Stop Condition

If reflection-based `DeltaTracker` proves too slow for high-throughput services, provide an alternative `ManualDeltaTracker` interface that services can implement with explicit field comparison.

---

### P26 — Data & Analytics Services

**Gate**: P25 complete. P24 recommended.
**Dependencies**: delta-snapshot-framework (P25.1), schema-registry (P20.3)

#### Deliverables

| # | Service | Module Path | Port | DB |
|---|---|---|---|---|
| 26.1 | analytics-pipeline-service | `services/analytics-pipeline-service` | 8117 | `analytics_pipeline` |
| 26.2 | surveillance-service | `services/surveillance-service` | 8118 | `surveillance` |
| 26.3 | data-governance-service | `services/data-governance-service` | 8119 | `data_governance` |

**analytics-pipeline-service (8117)**:
- Kafka consumer reading from `clinical.*`, `kernel.*`, `analytics.*` topics
- ETL engine: transforms raw events into dimensional aggregates
- NDR (National Data Repository) writer: persists aggregates for BI queries
- Reporting schedule: daily/weekly/monthly aggregate generation per tenant/facility
- REST API for ad-hoc aggregate queries
- Emits: `analytics.reporting.aggregate`
- Tables: aggregate_definitions, aggregate_snapshots, pipeline_runs, etl_errors, event_outbox

**surveillance-service (8118)**:
- eIDSR (electronic Integrated Disease Surveillance and Response) engine
- Case detection rules: configurable disease-specific triggers (fever + location = malaria suspect)
- Notifiable disease event generation from clinical data patterns
- Threshold alerting: when case counts exceed epidemic thresholds
- DHIS2 push adapter: formats and forwards surveillance data to DHIS2
- Emits: `analytics.surveillance.event`, `analytics.surveillance.alert`
- Tables: case_definitions, detected_cases, threshold_rules, alerts, dhis2_sync_log, event_outbox

**data-governance-service (8119)**:
- Research export framework: de-identified data extracts with purpose limitation
- Consent verification: checks TSHEPO Consent before any data release
- De-identification engine: k-anonymity, suppression, generalization
- Data access request lifecycle: REQUESTED → APPROVED → GENERATING → DELIVERED → EXPIRED
- Audit trail for all data releases
- Emits: `analytics.governance.export.requested`, `analytics.governance.export.delivered`
- Tables: export_requests, export_definitions, de_identification_rules, access_approvals, event_outbox

#### Exit Criteria

- [ ] analytics-pipeline-service: Consumes a `clinical.pct.journey.created` event and produces an aggregate
- [ ] surveillance-service: Detects a notifiable disease pattern and generates an alert
- [ ] data-governance-service: Produces a de-identified export with k-anonymity guarantee
- [ ] All 3 pass `GoldenContractIT`

#### Stop Condition

If DHIS2 push in surveillance-service requires external DHIS2 instance, implement `StubDhis2Adapter` for local dev. Defer real adapter testing to staging environment.

---

### P27 — Clinical Extensions

**Gate**: P22 complete (TUSO for facility/bed context, VARAPI for provider scheduling).
**Dependencies**: TUSO (P22), VARAPI (P22), PCT (existing), OROS (existing)

#### Deliverables

| # | Service | Module Path | Port | DB |
|---|---|---|---|---|
| 27.1 | inpatient-service | `services/inpatient-service` | 8120 | `inpatient` |
| 27.2 | scheduling-service | `services/scheduling-service` | 8121 | `scheduling` |
| 27.3 | referral-service | `services/referral-service` | 8122 | `referral` |

**inpatient-service (8120)**:
- Bed management: ward → bay → bed hierarchy, bed status tracking (AVAILABLE/OCCUPIED/RESERVED/BLOCKED/CLEANING)
- Admission workflow: integrates with PCT admission events
- Ward allocation engine: acuity-based bed assignment, infection control constraints
- Transfer management: inter-ward, inter-facility
- Discharge planning: target discharge date, discharge readiness scoring
- Nursing allocation: nurse-to-patient ratio tracking
- Emits: `clinical.inpatient.admission.*`, `clinical.inpatient.transfer.*`, `clinical.inpatient.discharge.*`, `telemetry.inpatient.bed.status`
- Tables: wards, bays, beds, bed_assignments, ward_transfers, nursing_allocations, discharge_plans, event_outbox

**scheduling-service (8121)**:
- Appointment booking: REQUESTED → CONFIRMED → CHECKED_IN → COMPLETED + CANCELLED/NO_SHOW
- Capacity management: resource availability calendar (rooms, equipment, providers)
- Slot generation: configurable templates (recurring schedules)
- Wait-list management: priority queue for overbooked slots
- TUSO integration: reads resource calendars for availability
- VARAPI integration: reads provider schedules for clinician availability
- Emits: `clinical.scheduling.appointment.*`, `clinical.scheduling.slot.*`
- Tables: appointment_types, slots, appointments, wait_list, capacity_templates, event_outbox

**referral-service (8122)**:
- Referral workflow: CREATED → SENT → ACCEPTED → SCHEDULED → COMPLETED + DECLINED/EXPIRED
- Routing engine: matches referral criteria to facility capabilities (via TUSO)
- Priority triage: urgent/routine/elective classification
- Counter-referral: feedback loop from receiving to referring facility
- Care network: multi-facility care coordination for complex cases
- Emits: `clinical.referral.created`, `clinical.referral.accepted`, `clinical.referral.completed`
- Tables: referrals, referral_criteria, care_networks, counter_referrals, event_outbox

#### Exit Criteria

- [ ] inpatient-service: Bed assignment based on acuity + availability works
- [ ] inpatient-service: Transfer between wards updates bed status correctly
- [ ] scheduling-service: Slot generation + appointment booking + check-in flow passes
- [ ] scheduling-service: Overbooked slot correctly adds to wait-list
- [ ] referral-service: Referral routing matches facility capabilities via TUSO
- [ ] All 3 pass `GoldenContractIT`

#### Stop Condition

If inpatient-service bed assignment logic requires real-time TUSO facility data that is not yet available (P22 still in progress), use a `StubTusoClient` returning test facility data and gate the integration test on TUSO availability.

---

### P28 — Experience Completion

**Gate**: P22 + P23 complete (registries needed for portal/developer experience).
**Dependencies**: All Ring 0 registries, tech-companion-harness

#### Deliverables

| # | Component | Location | Description |
|---|---|---|---|
| 28.1 | developer-portal-service | `services/developer-portal-service` | API documentation, sandbox, SDK packaging |
| 28.2 | supply-planning-module | `libs/supply-planning` | Consumption forecasting library for inventory-service |
| 28.3 | OpenAPI specs for all P20-P27 services | `contracts/openapi/` | Machine-generated + manually curated |

**developer-portal-service (port 8123, DB: developer_portal)**:
- API documentation aggregation: pulls OpenAPI specs from all services
- Sandbox environment management: isolated tenant for testing
- API key management: client registration, key generation, usage tracking
- SDK packaging: auto-generated Java and TypeScript SDKs from OpenAPI specs
- Onboarding workflow: registration → sandbox access → production access
- Rate limit configuration: per-client rate limits
- Emits: `integration.portal.client.registered`, `integration.portal.key.issued`
- Tables: api_clients, api_keys, sandbox_tenants, usage_logs, event_outbox
- Controllers: DocumentationController, SandboxController, ApiKeyController, OnboardingController

**supply-planning-module (28.2)**:
- `ConsumptionForecaster`: time-series based consumption prediction (moving average, exponential smoothing)
- `ReorderCalculator`: economic order quantity (EOQ) with lead time and safety stock
- `StockoutPredictor`: predicts days-to-stockout per item per facility
- Packaged as JAR library; inventory-service adopts when ready (no modification to inventory-service in this prompt)

#### Exit Criteria

- [ ] developer-portal-service: Aggregates OpenAPI specs from at least 5 services
- [ ] developer-portal-service: API key generation and usage tracking works
- [ ] supply-planning-module: Consumption forecast produces reasonable predictions for test data
- [ ] OpenAPI specs generated for all services built in P20-P27

#### Stop Condition

If auto-generation of SDKs from OpenAPI specs produces incorrect clients, defer SDK packaging to a future prompt and focus on documentation aggregation only.

---

### P29 — Resilience Infrastructure

**Gate**: P24 complete (federation-control needed for vault key distribution).
**Dependencies**: federation-connector (P24.3), docker-compose.yml (existing)

#### Deliverables

| # | Component | Location | Description |
|---|---|---|---|
| 29.1 | vault-kms-integration | `libs/vault-kms` + `infra/vault/` | HashiCorp Vault deployment + Spring Cloud Vault integration library |
| 29.2 | observability-stack | `infra/observability/` + `docker-compose.yml` | Prometheus, Grafana, OpenTelemetry Collector, Loki |

**vault-kms-integration (29.1)**:
- HashiCorp Vault in docker-compose (dev mode, port 8200)
- Vault Helm chart for K8s deployment
- `VaultKmsProvider` library: KEK retrieval from Vault Transit engine, key wrapping/unwrapping, automatic rotation
- `VaultSecretResolver`: resolves `${vault:secret/path}` placeholders in application.yml
- Spring Cloud Vault auto-configuration for new services
- CPID pseudonymization support: HMAC-SHA256 derivation using Vault-held key
- Integration test: tshepo-keys-service can retrieve KEK from Vault

**observability-stack (29.2)**:
- Prometheus in docker-compose (port 9090), pre-configured scrape targets for all services
- Grafana in docker-compose (port 3100), pre-provisioned dashboards: service health, Kafka lag, DB connections, JVM metrics
- OpenTelemetry Collector in docker-compose, receives traces from all services
- Loki in docker-compose (port 3200), log aggregation
- Spring Boot Actuator + Micrometer Prometheus endpoint configuration in parent POM
- SLI definitions: availability (up/total), latency (p50/p95/p99), error rate (5xx/total), Kafka consumer lag
- SLO targets per ring: Ring 0 (99.95% availability, 200ms p99), Ring 1 (99.9%, 500ms p99), Ring 2 (99.5%, 1s p99)
- Alerting rules: Prometheus alertmanager rules for SLO breach

#### Exit Criteria

- [ ] Vault starts in docker-compose and responds to health check
- [ ] `VaultKmsProvider` retrieves a transit key from Vault
- [ ] Prometheus scrapes metrics from at least 3 running services
- [ ] Grafana dashboard shows service health for running services
- [ ] OpenTelemetry traces propagate through Envoy → service → Kafka
- [ ] SLO dashboard shows availability and latency metrics

#### Stop Condition

If Vault in dev mode causes authentication issues with Spring Cloud Vault, use Vault agent sidecar pattern instead. If OpenTelemetry agent causes startup failures, defer trace collection to K8s deployment with auto-instrumentation.

---

### P30 — Hardening & Gates

**Gate**: P29 complete. All services from P20-P28 deployed and passing tests.
**Dependencies**: All previous prompts

#### Deliverables

| # | Component | Location | Description |
|---|---|---|---|
| 30.1 | contract-testing-gate | `.github/workflows/contract-test.yml` + `libs/tech-companion-harness` enhancements | Enhanced contract testing in CI |
| 30.2 | dr-orchestration | `scripts/dr/` + `docs/runbooks/` | Backup automation, restore procedures, DR drill scripts |
| 30.3 | chaos-resilience-framework | `libs/chaos-testing` | Fault injection, circuit breaker validation, degradation tests |
| 30.4 | Release governance documentation | `docs/plan/RELEASE_GOVERNANCE.md` | API versioning policy, canary deployment config, ring-based release train |

**contract-testing-gate (30.1)**:
- Enhanced `GoldenContractSuite` with event emission validation (outbox events have correct envelope fields)
- Pact provider verification: each service verifies consumer contracts
- Schema compatibility gate: CI blocks merge if event schema breaks backward compatibility
- Cross-service contract matrix: documents which services consume which events

**dr-orchestration (30.2)**:
- PostgreSQL backup script: WAL archiving + base backup to MinIO
- Kafka topic backup: MirrorMaker configuration for topic replication
- Restore runbooks: step-by-step procedures per ring (Ring 0 first, then Ring 1, then Ring 2)
- DR drill script: automated drill that simulates data loss and verifies recovery
- RPO/RTO targets: Ring 0 (RPO ≤5min, RTO ≤15min), Ring 1 (RPO ≤15min, RTO ≤30min), Ring 2 (RPO ≤1hr, RTO ≤2hr)

**chaos-resilience-framework (30.3)**:
- `ServiceKiller`: randomly stops a service and verifies system degrades gracefully
- `NetworkPartitioner`: simulates network partition between services
- `KafkaLagInjector`: introduces artificial Kafka consumer lag
- `DatabaseSlowdown`: adds artificial latency to DB queries
- Integration with observability-stack: verifies alerts fire during chaos events
- Test scenarios: Envoy circuit breaker trips, outbox retries succeed, break-glass activates under service outage

#### Exit Criteria

- [ ] Contract testing gate blocks a PR with breaking schema change
- [ ] DR drill: PostgreSQL backup + restore completes within Ring 0 RPO/RTO targets
- [ ] Chaos test: killing tshepo-authz-service triggers break-glass fallback in clinical services
- [ ] Chaos test: Kafka consumer lag beyond threshold triggers alert in Grafana
- [ ] All 27 components have passing `GoldenContractIT`
- [ ] Release governance document approved by platform team

#### Stop Condition

If chaos testing requires full K8s cluster (not available in local dev), implement chaos tests as integration tests against docker-compose with container stop/start via Docker API. Defer K8s-native chaos (Litmus/Chaos Monkey) to staging environment.

---

## 5. Dependency Graph

```
P20 (Foundation)
 ├── P21 (Integration Services) ─────────────────────┐
 ├── P22 (VARAPI + TUSO) ──┐                         │
 │                          ├── P23 (MSIKA + UBOMI)   │
 │                          │    └── P28 (Experience)  │
 │                          └── P27 (Clinical Ext.) ───┤
 ├── P24 (Assurance) ──────────┐                      │
 │                              ├── P29 (Resilience) ──┤
 ├── P25 (Data Infra) ─────────┤                      │
 │                              └── P26 (Analytics) ───┤
 │                                                     │
 └─────────────────────────────────────────────────────┴── P30 (Hardening)
```

**Parallelizable pairs**:
- P21 + P22 (no shared dependencies beyond P20)
- P21 + P24 (no shared dependencies beyond P20)
- P25 + P22 (no shared dependencies beyond P20)
- P26 + P27 (independent domains, both depend on P22 and P25)

---

## 6. Definition of Done (Global)

Every component in the Outstanding 27 MUST satisfy:

| # | Criterion | Verification |
|---|---|---|
| DoD-1 | Compiles without warnings (Java 21, `-Xlint:all`) | Maven build in CI |
| DoD-2 | `GoldenContractIT extends GoldenContractSuite` passes | CI test phase |
| DoD-3 | Flyway migration applies cleanly to fresh database | CI integration test |
| DoD-4 | Event outbox uses v1.1 EventEnvelope with all mandatory fields | GoldenContractSuite event check |
| DoD-5 | `/internal/v1/**` routing prefix on all endpoints | tech-companion HeaderEnforcementFilter |
| DoD-6 | Required headers enforced: X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID | GoldenContractSuite header test |
| DoD-7 | Idempotency-Key enforced on POST/PUT/PATCH commands | GoldenContractSuite idempotency test |
| DoD-8 | Error responses use canonical error envelope | GoldenContractSuite error envelope test |
| DoD-9 | Helm chart present in `helm/<service-name>/` | Directory existence check |
| DoD-10 | At least 3 domain-specific unit/integration tests | Test count assertion in CI |
| DoD-11 | Conventional commit message for each atomic change | Git hook / CI check |
| DoD-12 | No direct database access to another service's schema | Dependency scanner |

---

## 7. Risk Register

| # | Risk | Impact | Mitigation |
|---|---|---|---|
| R1 | Schema Registry configuration complexity delays P20 | Blocks all subsequent work | Fallback to CI-only JSON Schema validation |
| R2 | HAPI FHIR classpath conflicts in fhir-gateway-service | Blocks P21 | Isolate in separate Maven profile |
| R3 | Vault dev mode authentication issues | Blocks P29 | Use Vault agent sidecar pattern |
| R4 | Reflection-based DeltaTracker too slow | Degrades event throughput | Provide ManualDeltaTracker interface |
| R5 | Ring 0 registry completion (P22-P23) runs long | Delays P27 (clinical extensions) | Allow P27 to proceed with stub clients |
| R6 | External system adapters (DHIS2, councils) unavailable | Blocks real integration testing | Implement stub adapters for dev/test |
| R7 | Kafka topic migration disrupts existing consumers | Breaks legacy services | Dual-publish to old + new topics for 2 weeks |

---

## 8. Work Log

| Action | Path | Reason |
|---|---|---|
| Created | `docs/plan/IMPILO_VNEXT_BUILD_PLAN.md` | Master build plan for Outstanding 27 |
| Created | `docs/plan/SERVICE_CATALOG.md` | Authoritative service registry with ports, DBs, responsibilities |
| Created | `docs/plan/EVENTING_AND_TOPICS.md` | v1.1 EventEnvelope, outbox, topic naming, emit-mode rules |
| Created | `docs/plan/API_CONVENTIONS_V11.md` | /internal/v1/** routing, headers, error envelope, idempotency |
| Created | `docs/plan/TESTING_CONVENTIONS.md` | GoldenContractIT, behavior tests, DoD per service |
