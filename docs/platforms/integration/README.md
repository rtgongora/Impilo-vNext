# Integration Platform (Wave 12)

## Overview

The Integration Platform provides the connectors, workflow, forms, and search infrastructure for the Impilo vNext ecosystem. It consists of three services plus extensions to the existing notification service:

| Service | Port | Purpose |
|---------|------|---------|
| integration-hub | 8110 | Route definitions, connector kit (HTTP/Kafka/FileDrop), mapping templates, dispatch engine, dead-letter + replay |
| forms-service | 8131 | JSON Schema-based form definitions, versioning, validation API |
| search-service | 8132 | SQL-backed entity indexing, full-text search via ILIKE, tenant-isolated |
| notification-service | 8111 | Template versioning, delivery receipts (Wave 12 extensions) |

All services enforce the v1.1 header contract (X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID) and use the outbox pattern for reliable Kafka event publishing.

## Architecture

### Integration Hub

The integration hub is the core dispatch engine. Inbound requests are matched to a **route definition** that specifies a connector type, target URL/topic/path, and an optional mapping template. The dispatch pipeline:

```
Inbound Request → Route Resolution → Transform (mapping template) → Connector Dispatch
                                                                          ↓
                                                                 success → DispatchAttempt(OK)
                                                                 failure → Dead Letter + DispatchAttempt(FAIL)
```

Key components:

- **RouteDefinitionEntity**: tenant-scoped route with name, connector_type, target config, and mapping template reference
- **MappingTemplateEntity**: versioned transform templates with active/inactive tracking
- **DispatchService**: orchestrates route lookup, transform, connector execution, and dead-letter handling
- **TransformService**: applies mapping templates to inbound payloads before dispatch
- **DeadLetterEntity**: stores failed dispatches for later replay
- **OutboxEventEntity**: outbox pattern for all hub events

### Forms Service

A JSON Schema-based form definition platform with lifecycle management:

```
Create Schema (DRAFT) → Create Version → Publish → Retire
                                            ↓
                            Validate Payload (against published schema)
```

- **FormSchemaEntity**: tenant-scoped form definition with name, JSON Schema, and lifecycle status (DRAFT, PUBLISHED, RETIRED)
- **FormSchemaVersionEntity**: immutable version snapshots linked to a schema
- **SchemaValidationService**: validates arbitrary JSON payloads against the published schema version
- Lifecycle transitions are enforced: DRAFT -> PUBLISHED -> RETIRED (no backwards transitions)

### Search Service

A SQL-backed entity indexing service providing full-text search across the platform:

- **SearchIndexEntity**: stores entity content with searchable text, entity type, and entity ID
- Upsert semantics on duplicate (entityType + entityId + tenantId)
- Full-text search via PostgreSQL ILIKE on the searchable_text column
- All results are tenant-isolated (X-Tenant-ID scoped)
- Pagination via page/size parameters

### Notification Service (Wave 12 Extensions)

Extensions to the existing notification service add template lifecycle management and delivery tracking:

- **TemplateVersionEntity**: versioned template snapshots with changelog, following DRAFT -> PUBLISHED -> RETIRED lifecycle
- **DeliveryReceiptEntity**: records delivery outcomes (DELIVERED, FAILED, BOUNCED, etc.) per notification
- Receipts queryable by notification ID and delivery status

## API Reference

### Integration Hub (port 8110)

| Method | Path | Description |
|--------|------|-------------|
| POST | /internal/v1/routes | Create route definition |
| GET | /internal/v1/routes | List route definitions |
| POST | /internal/v1/dispatch | Dispatch request through route engine |
| GET | /internal/v1/deadletters | List dead letters |
| POST | /internal/v1/deadletters/{id}/replay | Replay a dead letter |
| POST | /internal/v1/mapping-templates | Create mapping template |
| GET | /internal/v1/mapping-templates | List mapping templates |

### Forms Service (port 8131)

| Method | Path | Description |
|--------|------|-------------|
| POST | /internal/v1/forms | Create form schema |
| GET | /internal/v1/forms | List form schemas |
| GET | /internal/v1/forms/{id} | Get form schema by ID |
| POST | /internal/v1/forms/{id}/versions | Create new schema version |
| POST | /internal/v1/forms/{id}/publish | Publish schema (DRAFT -> PUBLISHED) |
| POST | /internal/v1/forms/{id}/retire | Retire schema (PUBLISHED -> RETIRED) |
| POST | /internal/v1/forms/{id}/validate | Validate payload against published schema |

### Search Service (port 8132)

| Method | Path | Description |
|--------|------|-------------|
| POST | /internal/v1/search/index | Index an entity |
| DELETE | /internal/v1/search/index/{entityType}/{entityId} | Remove entity from index |
| GET | /internal/v1/search?q=&entityType=&page=&size= | Full-text search |
| GET | /internal/v1/search/index/{entityType} | List indexed entities by type |

### Notification Service (port 8111, Wave 12 extensions)

| Method | Path | Description |
|--------|------|-------------|
| POST | /internal/v1/templates/{id}/publish | Publish template version |
| POST | /internal/v1/templates/{id}/retire | Retire template |
| POST | /internal/v1/templates/{id}/versions | Create new template version |
| GET | /internal/v1/templates/{id}/versions | List template versions |
| POST | /internal/v1/delivery-receipts | Record delivery receipt |
| GET | /internal/v1/delivery-receipts | Query receipts by notification ID / status |

## Eventing Topics

### Integration Hub

| Topic | Trigger |
|-------|---------|
| `integration.hub.route.upserted` | Route created or updated |
| `integration.hub.dispatch.accepted` | Dispatch completed successfully |
| `integration.hub.dispatch.failed` | Dispatch failed, dead letter created |

### Forms Service

| Topic | Trigger |
|-------|---------|
| `impilo.forms.schema.created.v1` | New form schema created |
| `impilo.forms.schema.version_created.v1` | New schema version created |
| `impilo.forms.schema.published.v1` | Schema published |
| `impilo.forms.schema.retired.v1` | Schema retired |

### Search Service

| Topic | Trigger |
|-------|---------|
| `impilo.search.index.created.v1` | Entity indexed (new) |
| `impilo.search.index.updated.v1` | Entity re-indexed (upsert) |
| `impilo.search.index.removed.v1` | Entity removed from index |

### Notification Service (Wave 12 extensions)

| Topic | Trigger |
|-------|---------|
| `impilo.notify.template.published.v1` | Template version published |
| `impilo.notify.template.retired.v1` | Template retired |
| `impilo.notify.template.version_created.v1` | New template version created |
| `impilo.notify.receipt.recorded.v1` | Delivery receipt recorded |

## Connector Types

The integration hub ships with three connector implementations, resolved at runtime via `ConnectorRegistry`:

### HTTP

- Implementation: `HttpConnector` using `java.net.http.HttpClient`
- Connect timeout: 10 seconds
- Request timeout: configurable via `ConnectorRequest.timeoutMs()` (default 30 seconds)
- Supports configurable HTTP method, headers, and body
- Returns status code and response body in `ConnectorResult`

### KAFKA

- Implementation: `KafkaConnector`
- Writes to the `event_outbox` table for reliable Kafka dispatch (outbox pattern)
- Target topic specified in route definition

### FILE_DROP

- Implementation: `FileDropConnector`
- Writes request body to a configurable directory on the filesystem
- Target path specified in route definition config

## Local Development

### Ports

| Service | Port |
|---------|------|
| integration-hub | 8110 |
| notification-service | 8111 |
| forms-service | 8131 |
| search-service | 8132 |

### Databases

| Service | Database |
|---------|----------|
| integration-hub | impilo_integration_hub |
| forms-service | impilo_forms |
| search-service | impilo_search |
| notification-service | impilo_notification |

### Build & Test

```bash
cd services
mvn test -pl integration-hub,forms-service,search-service,notification-service -Dspring.profiles.active=test
```

### Docker Compose (suggested)

```yaml
services:
  integration-hub-db:
    image: postgres:16
    environment:
      POSTGRES_DB: impilo_integration_hub
      POSTGRES_USER: impilo
      POSTGRES_PASSWORD: impilo
    ports: ["5460:5432"]

  forms-db:
    image: postgres:16
    environment:
      POSTGRES_DB: impilo_forms
      POSTGRES_USER: impilo
      POSTGRES_PASSWORD: impilo
    ports: ["5461:5432"]

  search-db:
    image: postgres:16
    environment:
      POSTGRES_DB: impilo_search
      POSTGRES_USER: impilo
      POSTGRES_PASSWORD: impilo
    ports: ["5462:5432"]

  notification-db:
    image: postgres:16
    environment:
      POSTGRES_DB: impilo_notification
      POSTGRES_USER: impilo
      POSTGRES_PASSWORD: impilo
    ports: ["5463:5432"]
```

### Smoke Tests

#### Integration Hub (port 8110)

```bash
# Create route
curl -s -X POST http://localhost:8110/internal/v1/routes \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: smoke-route-$(date +%s)" \
  -d '{"name":"lab-results-http","connectorType":"HTTP","targetUrl":"http://localhost:9999/webhook","active":true}' | jq .

# List routes
curl -s http://localhost:8110/internal/v1/routes \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" | jq .

# Dispatch
curl -s -X POST http://localhost:8110/internal/v1/dispatch \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: smoke-dispatch-$(date +%s)" \
  -d '{"routeName":"lab-results-http","payload":{"labId":"LAB-001","result":"POSITIVE"}}' | jq .

# List dead letters
curl -s http://localhost:8110/internal/v1/deadletters \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" | jq .
```

#### Forms Service (port 8131)

```bash
# Create form schema
curl -s -X POST http://localhost:8131/internal/v1/forms \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: smoke-form-$(date +%s)" \
  -d '{"name":"patient-intake","jsonSchema":{"type":"object","properties":{"name":{"type":"string"},"age":{"type":"integer"}},"required":["name"]}}' | jq .

# Publish
curl -s -X POST http://localhost:8131/internal/v1/forms/{form_id}/publish \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: smoke-publish-$(date +%s)" | jq .

# Validate payload
curl -s -X POST http://localhost:8131/internal/v1/forms/{form_id}/validate \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -d '{"name":"John Doe","age":30}' | jq .
```

#### Search Service (port 8132)

```bash
# Index entity
curl -s -X POST http://localhost:8132/internal/v1/search/index \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: smoke-index-$(date +%s)" \
  -d '{"entityType":"PATIENT","entityId":"PAT-001","content":{"name":"John Doe","nhid":"NH-12345"},"searchableText":"John Doe NH-12345"}' | jq .

# Search
curl -s "http://localhost:8132/internal/v1/search?q=John&entityType=PATIENT&page=0&size=10" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" | jq .

# Remove from index
curl -s -X DELETE http://localhost:8132/internal/v1/search/index/PATIENT/PAT-001 \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" | jq .
```

## Failure Triage Map

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| 400 MISSING_REQUIRED_HEADER | Missing v1.1 headers | Add X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID |
| 400 IDEMPOTENCY_KEY_REQUIRED | Missing Idempotency-Key on POST/PUT/PATCH | Add Idempotency-Key header |
| 409 IDENTITY_CONFLICT | Same Idempotency-Key, different body | Use a new Idempotency-Key |
| 504 CLIENT_TIMEOUT_EXCEEDED | X-Client-Timeout-MS already expired | Increase timeout or remove header |
| Dispatch fails with dead letter | Target unreachable or returned error | Check target URL, inspect dead letter payload, replay after fix |
| Form validation returns errors | Payload does not match published JSON Schema | Review schema constraints and fix payload |
| Search returns empty results | Entity not indexed or wrong tenant | Verify entity was indexed under correct tenant ID |
| DRAFT -> PUBLISH fails | Schema already published or retired | Check current lifecycle status |
