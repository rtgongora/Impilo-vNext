# Schema Governance Cycle — Impilo vNext

> Wave 25 Deliverable | Status: Draft | Date: 2026-03-15

## 1. Scope

This document governs all schemas in the Impilo vNext platform:

| Schema Type | Location | Example |
|-------------|----------|---------|
| **Kafka event schemas** | `libs/shared-kernel-java` (EventEnvelope record), schema-registry-service | `trust.revocation.consent`, `clinical.pct.journey.created` |
| **Database schemas** | Flyway migrations per service (`src/main/resources/db/migration/`) | `vito.event_outbox`, `pct.journeys`, `oros.orders` |
| **REST API contracts** | OpenAPI specs per service, enforced by Tech Companion (`libs/tech-companion`) | `/internal/v1/vito/clients`, `/external/v1/portal/verify` |
| **Trust header contracts** | `libs/tshepo-contracts` (TrustHeaders.java), `contracts.ts`, `envoy.yaml` | 14+ trust headers (X-Tenant-ID, X-Pod-ID, etc.) |
| **FHIR profiles** | zibo-service terminology packs, butano-service FHIR resources | ICD-10, LOINC, SNOMED-CT subsets |

## 2. Ownership Model

### 2.1 Schema Governance Board

| Role | Person/Team | Responsibilities |
|------|-------------|-----------------|
| **Schema Steward** | Platform Architect (named individual) | Chairs governance reviews; maintains schema registry; enforces versioning policy; approves backward-compatible changes after 48h window |
| **Domain Owners** | Service team leads (one per domain cluster) | Propose schema changes for their services; ensure backward compatibility; update consumer documentation |
| **Data Governance Lead** | Data governance team lead | Enforces PII/CPID separation (EM-7: no PII in patient events); reviews FHIR profile changes; validates POPIA compliance |
| **Security Lead** | Security team lead | Reviews schema changes that affect trust headers, consent models, or audit chain integrity |
| **Consumer Representatives** | Nominated engineers from major consuming services | Validate that schema changes do not break downstream consumers; sign off on deprecation timelines |

### 2.2 Domain-to-Owner Mapping

| Domain Cluster | Services | Schema Owner |
|---------------|----------|-------------|
| Trust & Identity | tshepo-authz, tshepo-identity, tshepo-consent, tshepo-audit, tshepo-keys, tshepo-offline | Trust domain owner |
| Registry | vito-service, varapi-service, tuso-service, zibo-service, msika-service, ubomi-service, indawo-service | Registry domain owner |
| Clinical | pct-service, oros-service, pharmacy-service, inpatient-service, costing-engine-service, coverage-service | Clinical domain owner |
| Finance | mushex-service, costing-engine-service | Finance domain owner |
| Integration | integration-hub, notification-service, channels-service, fhir-gateway-service, pacs-adapter-service | Integration domain owner |
| Data & Analytics | data-pipeline-service, reporting-service, surveillance-service, search-service, ndr-service | Data domain owner |
| Offline | offline-sync-service, tshepo-offline-service | Offline domain owner |

## 3. Schema Change Classification

| Category | Definition | Approval Path | Lead Time | Examples |
|----------|-----------|--------------|-----------|---------|
| **Additive** | New optional field, new event type, new endpoint | Schema Steward auto-approves after 48h review window; no CAB | 2 business days | Add `discharge_reason` optional field to `clinical.pct.journey.updated` event |
| **Default-Safe** | New required field with a default value that does not change existing consumer behavior | Schema Steward review + 1 consumer representative sign-off | 5 business days | Add `schema_version` field defaulting to `"1.0"` |
| **Behavioral** | Change to validation rules, field semantics, or enum values that may alter consumer logic | Full governance board review | 10 business days | Change `status` enum to add `TRANSFERRED` value in PCT journey |
| **Breaking** | Field removal, field rename, type change, required field without default, topic restructure | Full governance board + CAB Major change request + deprecation plan | 20 business days minimum | Remove `legacy_id` field from VITO client event; rename Kafka topic |
| **Trust-Critical** | Any change to trust headers, consent models, audit chain format, or TSHEPO policy structure | Full governance board + CAB Major + Security Lead explicit sign-off | 30 business days minimum | Add new trust header; change consent evaluation semantics |

## 4. Governance Cadence

| Activity | Frequency | Duration | Participants | Inputs | Outputs |
|----------|-----------|----------|-------------|--------|---------|
| **Schema Change Review** | Bi-weekly (Wednesdays 10:00) | 45 min | Schema Steward + Domain Owners with pending proposals | Open schema change PRs, CI compatibility results | Approve / return / escalate decisions; updated decision log |
| **Schema Compatibility Audit** | Monthly (first Wednesday) | 60 min | Schema Steward + Platform Architect + Data Governance Lead | schema-registry-service version inventory, consumer lag report, compliance matrix | Compatibility report; deprecated schema sunset schedule |
| **Event Contract Review** | Monthly (third Wednesday) | 60 min | Schema Steward + all domain owners | Kafka topic inventory, consumer group report, outbox lag metrics | Updated topic catalog in `docs/plan/EVENTING_AND_TOPICS.md`; new consumer onboarding approvals |
| **Breaking Change Assessment** | Ad-hoc (triggered by proposal) | 90 min | Full governance board | Breaking change proposal, impact analysis, migration plan | Approve with migration plan / reject with alternatives |
| **Trust Header Review** | Quarterly | 60 min | Schema Steward + Security Lead + Trust domain owner | Trust header inventory from `libs/tshepo-contracts`, Envoy route config | Updated TrustHeaders.java ↔ contracts.ts ↔ envoy.yaml alignment report |

## 5. Schema Change Process

### 5.1 Standard Flow (Additive / Default-Safe)

```
Step 1: Domain owner creates PR with schema change
  ├── Kafka event: updated JSON Schema in schema-registry-service
  ├── Database: new Flyway migration file (V{NNN}__{description}.sql)
  ├── API: updated OpenAPI spec + controller
  └── EventEnvelope: updated payload definition in producer service

Step 2: CI pipeline validates
  ├── JSON Schema backward-compatibility check (schema-registry-service validation)
  ├── Flyway migration dry-run (non-destructive check)
  ├── Golden contract tests pass (libs/contract-tests / GoldenContractSuite)
  ├── Tech Companion enforcement passes (header validation, idempotency)
  └── Outbox schema columns validated (tenant_id, pod_id, etc.)

Step 3: 48-hour review window opens
  ├── Notification sent to schema-governance channel
  ├── Consumer representatives review impact
  └── Schema Steward reviews cross-domain implications

Step 4: Schema Steward approves or returns
  ├── If approved: PR merged, new schema version registered
  └── If returned: domain owner addresses feedback, resubmits

Step 5: Post-merge
  ├── Schema version incremented in schema-registry-service
  ├── Consumer documentation updated
  └── Decision logged in governance decision log
```

### 5.2 Breaking Change Flow

```
Step 1: Domain owner creates Schema Change Proposal (SCP)
  ├── SCP-{YYYY}-{NNN} document with:
  │   ├── Current schema and proposed change
  │   ├── All affected producers and consumers (with ports)
  │   ├── Migration plan (dual-write period, consumer update sequence)
  │   ├── Rollback plan (if migration fails mid-way)
  │   └── Deprecation timeline for old schema version

Step 2: Breaking Change Assessment meeting convened
  ├── Full governance board reviews SCP
  ├── Consumer representatives present impact assessment
  └── Decision: approve with plan / reject with alternatives

Step 3: If approved, create CAB Major change request
  ├── Link SCP to CR (see docs/rollout/change-control-and-cab.md)
  └── CAB approves deployment schedule

Step 4: Migration execution
  ├── Phase A: Deploy new schema version (producers write both old + new)
  ├── Phase B: Consumer migration (consumers updated to read new version)
  ├── Phase C: Old schema deprecated (producers stop writing old version)
  └── Phase D: Old schema sunset (after deprecation window expires)

Step 5: Post-migration verification
  ├── No consumers reading deprecated schema version
  ├── Schema registry updated (old version marked SUNSET)
  └── Decision log updated with completion date
```

## 6. Deprecation and Sunset Policy

| Schema Type | Deprecation Notice | Sunset Window | Enforcement |
|-------------|-------------------|---------------|-------------|
| Kafka event schema version | 6 months minimum | After all consumers migrated + 30 days buffer | schema-registry-service rejects new producers using sunset version |
| REST API endpoint | 6 months minimum | After all clients migrated | Envoy route returns 410 Gone after sunset date |
| Database column | 3 months (internal) | After all queries updated | Flyway migration drops column after sunset |
| Trust header | 12 months minimum | Coordinated with all pods in federation | Envoy config and tshepo-contracts updated simultaneously |
| FHIR profile version | Per HL7 versioning rules | Per FHIR implementation guide | zibo-service validation enforces |

### 6.1 Deprecation Announcement Template

```
Subject: [SCHEMA DEPRECATION] {schema_type} — {identifier} — Sunset: {date}

Schema: {schema name / event type / endpoint}
Current Version: {version being deprecated}
Replacement: {new version or alternative}
Deprecation Date: {date — effective now}
Sunset Date: {date — minimum 6 months from deprecation}
Affected Consumers: {list of consuming services}

Migration Guide: {link to migration documentation}

Owner: {domain owner name}
Approved By: {Schema Steward name, governance meeting date}
SCP Reference: SCP-{YYYY}-{NNN}
```

## 7. Schema Health Metrics

| Metric | Target | Measurement Source | Review Cadence |
|--------|--------|-------------------|---------------|
| Active schema versions per event type | ≤ 2 | schema-registry-service query | Monthly (compatibility audit) |
| Deprecated schemas still in production use | 0 after sunset date | Producer/consumer audit via Kafka consumer group lag | Monthly |
| Schema validation failures | ≤ 0.01% of events | `impilo_<service>_schema_validation_errors_total` metric | Weekly (observability review) |
| Time to approve additive schema change | ≤ 5 business days | Governance decision log | Monthly |
| Time to approve breaking schema change | ≤ 30 business days | Governance decision log | Quarterly |
| Flyway migration success rate | 100% (no failed migrations in production) | Deployment logs | Per release |
| Golden contract test pass rate | 100% | CI pipeline (`libs/contract-tests`) | Per PR |
| Outbox schema compliance rate | 100% of services have v1.1 columns | Compliance matrix (`docs/compliance/full-platform-compliance-matrix.md`) | Monthly |

## 8. Decision Log

All schema governance decisions are recorded in a persistent log:

| Field | Description |
|-------|-------------|
| Decision ID | SGD-{YYYY}-{NNN} |
| Date | Decision date |
| Category | Additive / Default-Safe / Behavioral / Breaking / Trust-Critical |
| Schema Type | Event / Database / API / Header / FHIR |
| Affected Services | List of producer and consumer services |
| Decision | Approved / Approved with conditions / Rejected / Deferred |
| Rationale | Why this decision was made |
| SCP Reference | SCP-{YYYY}-{NNN} (for breaking changes) |
| Migration Deadline | Date by which all consumers must migrate (if applicable) |
| Sunset Date | Date after which old version is removed (if applicable) |
| Decided By | Names of board members who participated |

### Decision Log Format

```markdown
## SGD-{YYYY}-{NNN} — {Title}

- **Date**: {YYYY-MM-DD}
- **Category**: {Additive / Breaking / ...}
- **Schema Type**: {Event / Database / API / Header / FHIR}
- **Proposer**: {domain owner}
- **Affected Services**: {list}
- **Decision**: {Approved / Rejected / Deferred}
- **Rationale**: {1-2 sentences}
- **Conditions**: {if any}
- **Migration Deadline**: {date or N/A}
- **Sunset Date**: {date or N/A}
- **Participants**: {names}
```

## 9. Tooling Integration

| Tool | Role in Schema Governance |
|------|--------------------------|
| **schema-registry-service** | Stores event schema versions; validates compatibility (backward/forward); enforces sunset dates |
| **libs/tech-companion** | Enforces v1.1 header contracts, idempotency keys, and error envelopes at runtime |
| **libs/tech-companion-harness** (`GoldenContractSuite`) | Validates that services conform to contract at test time |
| **libs/contract-tests** | Cross-service contract validation |
| **Flyway** (per service) | Database migration versioning; dry-run validation in CI |
| **CI pipeline** | Runs compatibility checks, golden contract tests, and compliance matrix updates on every PR |
| **`docs/compliance/full-platform-compliance-matrix.md`** | Tracks per-service compliance with outbox, header, and contract requirements |
| **`docs/plan/EVENTING_AND_TOPICS.md`** | Canonical topic catalog; updated via event contract review |
