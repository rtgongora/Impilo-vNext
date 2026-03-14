# Data, Analytics & Governance Platform

## Architecture Overview

The Data Platform consists of 4 v1.1-native services forming the analytics and governance layer of the Impilo vNext platform. All services enforce the v1.1 header contract (X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID) and use the EventEnvelope standard for eventing.

### Services

| Service | Port | Purpose |
|---------|------|---------|
| data-ingestion-service | 8210 | Kafka → Bronze raw append-only store |
| data-governance-service | 8220 | Policy gate for query/export + immutable audit |
| data-warehouse-service | 8230 | Gold dataset materializer (encounters, medications, labs) |
| surveillance-service | 8180 | Aggregate counters + threshold alerts |

### Data Flow

```
Clinical Services → Kafka Topics → data-ingestion-service → Bronze Store
                                                                   ↓
                                                   data-warehouse-service → Gold Tables
                                                                   ↓
                                                   surveillance-service → Daily Counters/Alerts
                                                                   ↓
                                              data-governance-service → Policy-gated Export
```

### Bronze Layer (data-ingestion-service)

- **Append-only** raw event store: every v1.1 EventEnvelope is stored as-is
- Kafka consumer listens to `impilo.*` topic pattern
- Poison/invalid messages routed to dead-letter table
- Schema validation: rejects `schema_version` < 1
- Deduplication via event_id + idempotency_key unique constraints
- Snapshot bootstrap: `GET /internal/v1/bronze/events?cursor=&limit=`

### Gold Layer (data-warehouse-service)

Three materialized datasets built from bronze events:

| Table | Source Events | Key Fields |
|-------|--------------|------------|
| dwh_gold_encounter | `*.encounter.*` | encounter_id, patient_id, facility_id, encounter_type |
| dwh_gold_medication | `*.medication.*`, `*.prescription.*`, `*.dispense.*` | medication_id, patient_id, drug_code, drug_name |
| dwh_gold_lab | `*.lab.*`, `*.observation.*`, `*.diagnostic.*` | lab_result_id, patient_id, test_code, result_value |

- Materializer is **idempotent** and **replay-safe** (upserts by natural key)
- Watermark tracking prevents re-processing

### Governance Layer (data-governance-service)

- **Decide endpoint**: Evaluates subject + action + resource + purpose_of_use
- **Export endpoint**: Deny-by-default; requires explicit ALLOW rule + purpose_of_use
- **Immutable audit log**: Every allow/deny decision is recorded with policy version
- **Rules CRUD**: Create, list, get, deactivate governance rules
- **Outbox events**: All decisions emit events for downstream consumption

### Surveillance Layer (surveillance-service)

- Consumes from bronze events (not directly from clinical services)
- Maintains **daily counters** by facility + syndrome code
- **Alert definitions**: Configurable threshold-based alerts
- **Alert events**: Auto-triggered when counter exceeds threshold
- eIDSR-style event counting WITHOUT blocking clinical execution

## Topic Conventions

| Topic Pattern | Producer | Consumer |
|--------------|----------|----------|
| `impilo.clinical.*` | Clinical services | data-ingestion-service |
| `impilo.pharmacy.*` | Pharmacy services | data-ingestion-service |
| `impilo.lab.*` | Lab services | data-ingestion-service |
| `impilo.data.ingestion.bronze.received.v1` | data-ingestion-service | data-warehouse-service, surveillance-service |
| `impilo.data.governance.*.v1` | data-governance-service | audit-ledger-service |
| `impilo.surv.*` | surveillance-service | notification-service |

## Governance Model

### Purpose Limitation

All data access requires a declared `purpose_of_use` from the set:
- `TREATMENT` — Direct patient care
- `PUBLIC_HEALTH` — Disease surveillance, outbreak response
- `RESEARCH` — Approved research protocols
- `ADMIN` — System administration, data quality

### Export Rules

Exports are **denied by default**. To allow an export:
1. Create an ALLOW rule: `POST /internal/v1/governance/rules`
2. Rule must specify `resourcePattern` (dataset name) and optionally `requiredPurpose`
3. Rules are evaluated in priority order (lowest number = highest priority)
4. First matching rule's effect (ALLOW/DENY) wins

### Audit Trail

Every governance decision is recorded in `dgv_decision_audit` with:
- Decision (ALLOW/DENY)
- Reason codes
- Policy version
- Correlation ID
- Query fingerprint

## Database Schemas

| Service | Schema/Prefix | Key Tables |
|---------|--------------|------------|
| data-ingestion-service | `din_` | din_bronze_event, din_event_outbox, din_dead_letter_event |
| data-governance-service | `dgv_` | dgv_dataset, dgv_grant, dgv_policy, dgv_governance_rule, dgv_decision_audit |
| data-warehouse-service | `dwh_` | dwh_gold_encounter, dwh_gold_medication, dwh_gold_lab, dwh_materializer_watermark |
| surveillance-service | `surv.` | surv.signals, surv.cases, surv.daily_counter, surv.alert_definition, surv.alert_event |

## API Reference

### Data Ingestion Service (port 8210)

| Method | Path | Description |
|--------|------|-------------|
| POST | /internal/v1/ingest/events | Ingest single event |
| POST | /internal/v1/ingest/batch | Batch ingest events |
| GET | /internal/v1/bronze/events | Snapshot bootstrap (cursor-paged) |
| GET | /internal/v1/ingestion/status | Ingestion status/counts |

### Data Governance Service (port 8220)

| Method | Path | Description |
|--------|------|-------------|
| POST | /internal/v1/governance/decide | Access decision |
| POST | /internal/v1/governance/datasets | Register dataset |
| GET | /internal/v1/governance/datasets | List datasets |
| POST | /internal/v1/governance/grants | Grant access |
| POST | /internal/v1/governance/grants/revoke | Revoke grant |
| POST | /internal/v1/governance/policies | Publish policy |
| POST | /internal/v1/governance/rules | Create rule |
| GET | /internal/v1/governance/rules | List active rules |
| GET | /internal/v1/governance/rules/{id} | Get rule |
| DELETE | /internal/v1/governance/rules/{id} | Deactivate rule |
| POST | /external/v1/exports | Request export |
| GET | /external/v1/governance/datasets | Public dataset listing |

### Data Warehouse Service (port 8230)

| Method | Path | Description |
|--------|------|-------------|
| POST | /internal/v1/gold/materialize | On-demand materialization |
| GET | /internal/v1/gold/query | Query gold tables |
| GET | /internal/v1/gold/stats | Materializer stats |
| GET | /external/v1/gold/datasets | List available gold datasets |

### Surveillance Service (port 8180)

| Method | Path | Description |
|--------|------|-------------|
| POST | /internal/v1/ingest | Ingest surveillance event |
| GET | /internal/v1/cases | List cases |
| POST | /internal/v1/signals | Create signal |
| GET | /internal/v1/signals | List signals |
| GET | /internal/v1/surveillance/counters | Get daily counters |
| POST | /internal/v1/surveillance/counters | Increment counter |
| GET | /internal/v1/surveillance/alerts | Get alert events |
| POST | /internal/v1/surveillance/alerts/definitions | Create alert definition |
| GET | /internal/v1/surveillance/alerts/definitions | List alert definitions |
