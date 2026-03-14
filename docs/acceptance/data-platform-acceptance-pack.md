# Data Platform — Acceptance Pack

## Module List

| Module | Artifact | Status |
|--------|----------|--------|
| data-ingestion-service | services/data-ingestion-service | v1.1-native |
| data-governance-service | services/data-governance-service | v1.1-native |
| data-warehouse-service | services/data-warehouse-service | v1.1-native |
| surveillance-service | services/surveillance-service | v1.1-native |

## Build & Test Commands

### Build all 4 services
```bash
cd services
mvn clean install -pl data-ingestion-service,data-governance-service,data-warehouse-service,surveillance-service -am
```

### Run all tests
```bash
# Data Ingestion
mvn test -pl data-ingestion-service -Dspring.profiles.active=test

# Data Governance
mvn test -pl data-governance-service -Dspring.profiles.active=test

# Data Warehouse
mvn test -pl data-warehouse-service -Dspring.profiles.active=test

# Surveillance
mvn test -pl surveillance-service -Dspring.profiles.active=test
```

### Run Golden Contract tests only
```bash
mvn test -pl data-ingestion-service -Dtest=DataIngestionGoldenContractIT -Dspring.profiles.active=test
mvn test -pl data-governance-service -Dtest=DataGovernanceGoldenContractIT -Dspring.profiles.active=test
mvn test -pl data-warehouse-service -Dtest=DataWarehouseGoldenContractIT -Dspring.profiles.active=test
mvn test -pl surveillance-service -Dtest=SurvGoldenContractIT -Dspring.profiles.active=test
```

## Docker Compose Suggestions

```yaml
# Add to existing docker-compose.yml
data-ingestion-db:
  image: postgres:16
  environment:
    POSTGRES_DB: impilo_data_ingestion
    POSTGRES_USER: impilo
    POSTGRES_PASSWORD: impilo
  ports: ["5442:5432"]

data-governance-db:
  image: postgres:16
  environment:
    POSTGRES_DB: impilo_data_governance
    POSTGRES_USER: impilo
    POSTGRES_PASSWORD: impilo
  ports: ["5443:5432"]

data-warehouse-db:
  image: postgres:16
  environment:
    POSTGRES_DB: impilo_data_warehouse
    POSTGRES_USER: impilo
    POSTGRES_PASSWORD: impilo
  ports: ["5444:5432"]

surveillance-db:
  image: postgres:16
  environment:
    POSTGRES_DB: impilo_surveillance
    POSTGRES_USER: impilo
    POSTGRES_PASSWORD: impilo
  ports: ["5445:5432"]
```

## Smoke Curl Commands

### Data Ingestion Service (port 8210)

```bash
# Health check
curl -s http://localhost:8210/internal/v1/ingest/health \
  -H "X-Tenant-ID: 00000000-0000-0000-0000-000000000001" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)"

# Ingest a single event
curl -s -X POST http://localhost:8210/internal/v1/ingest/events \
  -H "X-Tenant-ID: 00000000-0000-0000-0000-000000000001" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: smoke-$(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{"eventId":"smoke-1","eventType":"impilo.clinical.encounter.created.v1","schemaVersion":1,"subjectId":"pat-1","subjectType":"Patient","occurredAt":"2024-01-01T00:00:00Z","meta":{"partition_key":"pat-1"}}'

# Snapshot bronze events
curl -s http://localhost:8210/internal/v1/bronze/events?cursor=0&limit=10 \
  -H "X-Tenant-ID: 00000000-0000-0000-0000-000000000001" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)"
```

### Data Governance Service (port 8220)

```bash
# Create a dataset
curl -s -X POST http://localhost:8220/internal/v1/governance/datasets \
  -H "X-Tenant-ID: 00000000-0000-0000-0000-000000000001" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: smoke-ds-$(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{"name":"encounters","classification":"INTERNAL","description":"Clinical encounters"}'

# Access decision
curl -s -X POST http://localhost:8220/internal/v1/governance/decide \
  -H "X-Tenant-ID: 00000000-0000-0000-0000-000000000001" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{"principalId":"user-1","dataset":"encounters","purposeOfUse":"TREATMENT"}'

# Create governance rule
curl -s -X POST http://localhost:8220/internal/v1/governance/rules \
  -H "X-Tenant-ID: 00000000-0000-0000-0000-000000000001" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: smoke-rule-$(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{"name":"allow-research","resourcePattern":"encounters","action":"EXPORT","effect":"ALLOW","requiredPurpose":"RESEARCH"}'

# Export request (should be DENY without rule, or ALLOW with matching rule)
curl -s -X POST http://localhost:8220/external/v1/exports \
  -H "X-Tenant-ID: 00000000-0000-0000-0000-000000000001" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: smoke-exp-$(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{"dataset":"encounters","purposeOfUse":"RESEARCH"}'
```

### Data Warehouse Service (port 8230)

```bash
# List gold datasets
curl -s http://localhost:8230/external/v1/gold/datasets \
  -H "X-Tenant-ID: 00000000-0000-0000-0000-000000000001" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)"

# Query gold encounters
curl -s "http://localhost:8230/internal/v1/gold/query?dataset=encounters&page=0&limit=10" \
  -H "X-Tenant-ID: 00000000-0000-0000-0000-000000000001" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)"

# Get materializer stats
curl -s http://localhost:8230/internal/v1/gold/stats \
  -H "X-Tenant-ID: 00000000-0000-0000-0000-000000000001" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)"
```

### Surveillance Service (port 8180)

```bash
# Get counters
curl -s http://localhost:8180/internal/v1/surveillance/counters \
  -H "X-Tenant-ID: 00000000-0000-0000-0000-000000000001" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)"

# Increment counter
curl -s -X POST http://localhost:8180/internal/v1/surveillance/counters \
  -H "X-Tenant-ID: 00000000-0000-0000-0000-000000000001" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: smoke-cnt-$(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{"facility_id":"00000000-0000-0000-0000-000000000099","syndrome_code":"MALARIA"}'

# Get alerts
curl -s http://localhost:8180/internal/v1/surveillance/alerts \
  -H "X-Tenant-ID: 00000000-0000-0000-0000-000000000001" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)"
```

## Failure Triage Map

| Symptom | Check | Fix |
|---------|-------|-----|
| Missing required header 400 | All requests need X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID | Add missing headers |
| IDEMPOTENCY_KEY_REQUIRED 400 | POST/PUT endpoints require Idempotency-Key header | Add Idempotency-Key header |
| SCHEMA_VALIDATION_FAILED 422 | schema_version must be >= 1 | Set schema_version to 1+ in event envelope |
| MISSING_PURPOSE_OF_USE 400 | Export requests require purpose_of_use | Include purpose_of_use in request body |
| Export DENY 403 | No explicit ALLOW rule for dataset+purpose | Create governance rule with ALLOW effect |
| IDENTITY_CONFLICT 409 | Same Idempotency-Key with different body | Use unique Idempotency-Key per request |
| Kafka consumer lag | Consumer group offset | Check consumer group lag in Kafka |
| Dead letters accumulating | din_dead_letter_event table | Inspect error_message, fix source events |
| Materializer stuck | dwh_materializer_watermark.updated_at | Check warehouse service logs, restart if needed |
| Counter not incrementing | surv.daily_counter | Verify facility_id and syndrome_code values |

## Test Coverage Matrix

| Requirement | Test Class | Test Method |
|------------|-----------|------------|
| Envelope schema_version validation | IngestApiMockMvcTest | ingestMissingSchemaVersionReturns422 |
| Governance denies without purpose_of_use | GovernanceApiMockMvcTest | exportWithoutPurposeReturns400 |
| Ingestion preserves correlation_id | DataIngestionGoldenContractIT | (header enforcement suite) |
| Gold encounters materialization | GoldMaterializerServiceTest | encounterEventMaterializes |
| Gold medications materialization | GoldMaterializerServiceTest | medicationEventMaterializes |
| Gold labs materialization | GoldMaterializerServiceTest | labEventMaterializes |
| Surveillance daily counters | CounterServiceTest | incrementCounterCreatesNew, incrementCounterIncrementsExisting |
| Surveillance alert threshold | CounterServiceTest | alertTriggeredOnThreshold |
| Header enforcement (all services) | *GoldenContractIT | missingTenantIdReturns400, etc. |
| Idempotency enforcement | *GoldenContractIT | missingIdempotencyKeyReturns400 |
| Export denied by default | GovernanceApiMockMvcTest | exportDeniedByDefault |
