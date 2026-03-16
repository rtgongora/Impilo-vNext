# NDR — National Data Repository Service

Analytics-ready data store for national datasets. **Not a shared health record** — stores aggregate and de-identified data for reporting, dashboards, and policy analysis.

## Port

| Environment | Port |
|-------------|------|
| Local dev   | 8150 |

## API Endpoints

All endpoints are v1.1-native (`/internal/v1/` prefix) with full header enforcement, idempotency, and timeout support via Tech Companion auto-configuration.

| Method | Path | Description |
|--------|------|-------------|
| POST | `/internal/v1/datasets` | Create a new dataset |
| GET | `/internal/v1/datasets` | List all datasets for tenant |
| POST | `/internal/v1/datasets/{key}/versions` | Create a new version of a dataset |
| POST | `/internal/v1/query` | Query materialized views by dataset key + filters |

### Required Headers (v1.1)

| Header | Required | Description |
|--------|----------|-------------|
| `X-Tenant-ID` | Yes | Tenant identifier |
| `X-Pod-ID` | Yes | Pod identifier (e.g., `national`) |
| `X-Request-ID` | Yes | Unique request identifier |
| `X-Correlation-ID` | Yes | Correlation identifier for tracing |
| `Idempotency-Key` | POST/PUT/PATCH | Deduplication key for commands |

## Kafka Events

Published via the transactional outbox pattern:

| Topic | Event Type | Trigger |
|-------|-----------|---------|
| `impilo.ndr.dataset.created.v1` | `DATASET_CREATED` | New dataset registered |
| `impilo.ndr.dataset.versioned.v1` | `DATASET_VERSIONED` | New version added to dataset |

## Database

- **Schema**: `ndr` (PostgreSQL 16)
- **Migration**: Flyway (`V001__init.sql`)

### Tables

| Table | Purpose |
|-------|---------|
| `ndr_datasets` | Top-level dataset registry |
| `ndr_dataset_versions` | Immutable version snapshots |
| `ndr_dataset_access_policies` | RBAC rules for dataset-level access |
| `ndr_materialized_views` | Pre-computed query results for fast analytics |
| `ndr_event_outbox` | Transactional outbox for Kafka |
| `idempotency_keys` | v1.1 command deduplication |

## Tech Stack

- Java 21, Spring Boot 3.3.6
- PostgreSQL 16, Flyway
- Kafka (outbox pattern)
- Tech Companion v1.1 (header enforcement, idempotency, timeout)

## Testing

```bash
cd services && mvn test -pl national-data-repository-service
```

- `NdrGoldenContractIT` — v1.1 compliance (header enforcement, error envelope, idempotency)
- `DatasetServiceTest` — dataset CRUD and outbox event tests (7 tests)
- `QueryServiceTest` — materialized view query tests (4 tests)
- `OutboxPublisherTest` — event routing tests (3 tests)
- `DatasetControllerTest` — REST endpoint integration tests (3 tests)
- `QueryControllerTest` — query endpoint integration tests (2 tests)
