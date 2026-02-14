# Search Service

Enterprise search service for the Impilo platform. Provides index definition management, document ingestion, and query API with filters, pagination, and simple ranking.

## Port

- **8120** (local dev)

## API Endpoints

All endpoints require v1.1 trust headers:
- `X-Tenant-ID`, `X-Pod-ID`, `X-Request-ID`, `X-Correlation-ID`
- Write operations require `Idempotency-Key`

### Index Definitions

```bash
# Create an index
curl -X POST http://localhost:8120/internal/v1/indexes \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: idx-$(uuidgen)" \
  -d '{
    "name": "patient-index",
    "sourceType": "vito-patient",
    "fields": [
      {"name": "given_name", "type": "text", "searchable": true, "filterable": false},
      {"name": "family_name", "type": "text", "searchable": true, "filterable": true}
    ],
    "enabled": true
  }'

# Get index by ID
curl http://localhost:8120/internal/v1/indexes/{id} \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)"

# List all indexes
curl http://localhost:8120/internal/v1/indexes \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)"

# Update an index
curl -X PUT http://localhost:8120/internal/v1/indexes/{id} \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: idx-upd-$(uuidgen)" \
  -d '{
    "name": "patient-index-v2",
    "sourceType": "vito-patient",
    "fields": [
      {"name": "given_name", "type": "text", "searchable": true, "filterable": true}
    ],
    "enabled": true
  }'

# Delete an index
curl -X DELETE http://localhost:8120/internal/v1/indexes/{id} \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)"
```

### Documents

```bash
# Upsert a document
curl -X POST http://localhost:8120/internal/v1/documents \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: doc-$(uuidgen)" \
  -d '{
    "indexId": "{index-id}",
    "externalId": "patient-12345",
    "title": "John Doe",
    "bodyText": "Patient John Doe, DOB 1990-01-15, Harare Central Hospital",
    "metadata": {"facility": "central-hospital", "department": "outpatient"}
  }'

# Delete a document
curl -X DELETE http://localhost:8120/internal/v1/documents/{docId} \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)"
```

### Search

```bash
# Full-text search
curl -X POST http://localhost:8120/internal/v1/search \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: search-$(uuidgen)" \
  -d '{
    "query": "John Doe",
    "indexId": "{index-id}",
    "page": 0,
    "size": 20
  }'
```

## Event Types

All events are published via the outbox pattern (`srch_event_outbox` table):

| Event Type | Trigger |
|---|---|
| `impilo.search.index.created.v1` | New index definition created |
| `impilo.search.index.updated.v1` | Index definition updated |
| `impilo.search.index.deleted.v1` | Index definition deleted |
| `impilo.search.document.upserted.v1` | Document created or updated |
| `impilo.search.document.deleted.v1` | Document deleted |

## Database

- **Database**: `impilo_search`
- **Tables**: `srch_index_definitions`, `srch_documents`, `srch_event_outbox`, `idempotency_keys`
- **Migration**: Flyway V001

## Tech Companion (v1.1)

This service is v1.1-native:
- Header enforcement via `V11HeaderFilter`
- Idempotency via `IdempotencyFilter`
- Timeout enforcement via `TimeoutEnforcementFilter`
- Federation authority on write endpoints (national pod only)
- EventEnvelope-compatible outbox schema
