# Integration Platform — Acceptance Pack

## Module List

| Module | Type | Port |
|--------|------|------|
| `services/integration-hub` | Spring Boot | 8110 |
| `services/forms-service` | Spring Boot | 8131 |
| `services/search-service` | Spring Boot | 8132 |
| `services/notification-service` | Spring Boot | 8111 |

## Build & Test

```bash
cd services
mvn test -pl integration-hub,forms-service,search-service,notification-service -Dspring.profiles.active=test
```

## Acceptance Criteria

### A) Connector Kit (integration-hub)

- [ ] HTTP connector dispatches real HTTP requests with configurable timeout (default 30s, connect 10s)
- [ ] Kafka connector writes outbox event for Kafka publishing
- [ ] File-drop connector writes body to configured directory
- [ ] Connector registry resolves correct connector by type (HTTP, KAFKA, FILE_DROP)
- [ ] Route definitions support connector_type field
- [ ] Mapping templates stored in DB with version/active tracking
- [ ] Dead letters created on dispatch failure
- [ ] Dead letter replay re-dispatches through the route engine

### B) Forms & Content Platform (forms-service)

- [ ] Create form schema with JSON Schema definition
- [ ] Version bumping creates new FormSchemaVersionEntity linked to parent schema
- [ ] DRAFT -> PUBLISHED -> RETIRED lifecycle enforced (no backwards transitions)
- [ ] Validate payload against published schema returns structured validation errors
- [ ] Tenant isolation verified (X-Tenant-ID scoped)
- [ ] Outbox events emitted for all mutations (created, version_created, published, retired)

### C) Search Indexing Platform (search-service)

- [ ] Index entity stores content + searchable text in SearchIndexEntity
- [ ] Upsert on duplicate entityType + entityId + tenantId
- [ ] ILIKE search returns matching results on searchable_text column
- [ ] List by entity type with page/size pagination
- [ ] Remove from index by entityType + entityId
- [ ] Tenant isolation verified (X-Tenant-ID scoped)

### D) Workflow Mini-Engine

- [ ] Route inbound event -> transform (mapping template) -> dispatch via connector (integration-hub dispatch pipeline)
- [ ] Form submission -> validate against published schema -> publish outbox event (forms-service validate + outbox)

### E) Notification Extensions (notification-service)

- [ ] Template versioning: DRAFT -> PUBLISHED -> RETIRED lifecycle
- [ ] Template version creation with changelog via TemplateVersionEntity
- [ ] Delivery receipt recording via DeliveryReceiptEntity
- [ ] Delivery receipt querying by notification ID and delivery status

## v1.1 Compliance

- [ ] All new services have GoldenContractIT passing
- [ ] All endpoints use /internal/v1/ prefix
- [ ] 4 mandatory headers enforced (X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID)
- [ ] Idempotency-Key required on POST/PUT/PATCH
- [ ] Error envelope format returned on all error responses
- [ ] Outbox pattern implemented for all events (event_outbox table per service)

## Test Coverage Matrix

### integration-hub

| Test Class | Type | Coverage |
|------------|------|----------|
| `IntegrationHubGoldenContractIT` | GoldenContract | v1.1 headers, idempotency, timeout, error envelope |
| `IntegrationHubServiceTest` | Behavior | Route creation, dispatch pipeline, dead-letter + replay |
| `IntegrationHubConnectorTest` | Behavior | HTTP/Kafka/FileDrop connector execution, registry resolution |
| `IntegrationHubV11ComplianceTest` | Unit | v1.1 header validation, idempotency enforcement |

### forms-service

| Test Class | Type | Coverage |
|------------|------|----------|
| `FormsGoldenContractIT` | GoldenContract | v1.1 headers, idempotency, timeout, error envelope |
| `FormsServiceTest` | Behavior | Schema CRUD, versioning, lifecycle transitions, validation |

### search-service

| Test Class | Type | Coverage |
|------------|------|----------|
| `SearchGoldenContractIT` | GoldenContract | v1.1 headers, idempotency, timeout, error envelope |
| `SearchServiceTest` | Behavior | Index, upsert, search, remove, tenant isolation |

### notification-service

| Test Class | Type | Coverage |
|------------|------|----------|
| `NotificationGoldenContractIT` | GoldenContract | v1.1 headers, idempotency, timeout, error envelope |
| `NotificationServiceTest` | Behavior | Notification send, template CRUD, provider dispatch |
| `NotificationExtensionsTest` | Behavior | Template versioning, delivery receipts |
| `NotificationV11ComplianceTest` | Unit | v1.1 header validation, idempotency enforcement |

## Test Coverage Summary

| Service | GoldenContractIT | Behavior Tests | Unit Tests |
|---------|-----------------|----------------|------------|
| integration-hub | IntegrationHubGoldenContractIT | IntegrationHubServiceTest, IntegrationHubConnectorTest | IntegrationHubV11ComplianceTest |
| forms-service | FormsGoldenContractIT | FormsServiceTest | - |
| search-service | SearchGoldenContractIT | SearchServiceTest | - |
| notification-service | NotificationGoldenContractIT | NotificationServiceTest, NotificationExtensionsTest | NotificationV11ComplianceTest |

## Smoke Test Scripts

### Prerequisites

Each service requires PostgreSQL. For local testing, use Docker Compose or the test profile (H2).

### Integration Hub (port 8110)

```bash
# Create route
curl -s -X POST http://localhost:8110/internal/v1/routes \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: smoke-route-$(date +%s)" \
  -d '{"name":"lab-webhook","connectorType":"HTTP","targetUrl":"http://localhost:9999/hook","active":true}' | jq .

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
  -d '{"routeName":"lab-webhook","payload":{"labId":"LAB-001"}}' | jq .

# List dead letters
curl -s http://localhost:8110/internal/v1/deadletters \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" | jq .

# Create mapping template
curl -s -X POST http://localhost:8110/internal/v1/mapping-templates \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: smoke-tmpl-$(date +%s)" \
  -d '{"name":"lab-transform","templateBody":"{ \"id\": \"${labId}\" }","version":1,"active":true}' | jq .

# List mapping templates
curl -s http://localhost:8110/internal/v1/mapping-templates \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" | jq .
```

### Forms Service (port 8131)

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

# List schemas
curl -s http://localhost:8131/internal/v1/forms \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" | jq .

# Create version (replace {id} with actual form ID)
curl -s -X POST http://localhost:8131/internal/v1/forms/{id}/versions \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: smoke-ver-$(date +%s)" \
  -d '{"jsonSchema":{"type":"object","properties":{"name":{"type":"string"},"age":{"type":"integer"},"nhid":{"type":"string"}},"required":["name","nhid"]}}' | jq .

# Publish
curl -s -X POST http://localhost:8131/internal/v1/forms/{id}/publish \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: smoke-pub-$(date +%s)" | jq .

# Validate
curl -s -X POST http://localhost:8131/internal/v1/forms/{id}/validate \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -d '{"name":"John Doe","age":30}' | jq .
```

### Search Service (port 8132)

```bash
# Index entity
curl -s -X POST http://localhost:8132/internal/v1/search/index \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: smoke-idx-$(date +%s)" \
  -d '{"entityType":"PATIENT","entityId":"PAT-001","content":{"name":"John Doe"},"searchableText":"John Doe PAT-001"}' | jq .

# Search
curl -s "http://localhost:8132/internal/v1/search?q=John&entityType=PATIENT&page=0&size=10" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" | jq .

# List by type
curl -s http://localhost:8132/internal/v1/search/index/PATIENT \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" | jq .

# Remove
curl -s -X DELETE http://localhost:8132/internal/v1/search/index/PATIENT/PAT-001 \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" | jq .
```

### Notification Service — Wave 12 Extensions (port 8111)

```bash
# Create template version (replace {id} with template ID)
curl -s -X POST http://localhost:8111/internal/v1/templates/{id}/versions \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: smoke-tver-$(date +%s)" \
  -d '{"templateBody":"Hello {{name}}, your appointment is on {{date}}.","changelog":"Added date variable"}' | jq .

# List template versions
curl -s http://localhost:8111/internal/v1/templates/{id}/versions \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" | jq .

# Publish template
curl -s -X POST http://localhost:8111/internal/v1/templates/{id}/publish \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: smoke-tpub-$(date +%s)" | jq .

# Record delivery receipt
curl -s -X POST http://localhost:8111/internal/v1/delivery-receipts \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: smoke-rcpt-$(date +%s)" \
  -d '{"notificationId":"NOTIF-001","status":"DELIVERED","providerRef":"sms-gw-001","deliveredAt":"2026-03-14T10:00:00Z"}' | jq .

# Query delivery receipts
curl -s "http://localhost:8111/internal/v1/delivery-receipts?notificationId=NOTIF-001&status=DELIVERED" \
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
| Dispatch creates dead letter | Connector target unreachable or returned error | Check target URL/topic/path, inspect dead letter, replay after fix |
| Form publish fails 400 | Schema not in DRAFT state | Check current lifecycle status, only DRAFT can be published |
| Form validate returns errors | Payload violates JSON Schema constraints | Review schema definition and fix payload |
| Search returns empty | Entity not indexed or wrong tenant | Verify indexing was done under correct X-Tenant-ID |
| Template publish fails | Template not in DRAFT state | Check current template lifecycle status |
| H2 test failures | DDL mismatch | Ensure entity column annotations match table schema |
