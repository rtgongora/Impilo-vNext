# Forms Service

Clinical forms and content pack management service for the Impilo platform.
Manages form definitions, versioned content JSON (immutable), and publish workflows (draft → published).

**v1.1-native** — uses Tech Companion auto-config for header enforcement, idempotency, and timeout propagation.

## Port

`8130` (local dev)

## Endpoints

All endpoints are under `/internal/v1/forms` and require v1.1 headers:

| Header             | Required | Description                     |
|--------------------|----------|---------------------------------|
| X-Tenant-ID        | Yes      | Tenant identifier               |
| X-Pod-ID           | Yes      | Pod identifier (e.g. `national`)|
| X-Request-ID       | Yes      | Unique request ID               |
| X-Correlation-ID   | Yes      | Correlation ID for tracing      |
| Idempotency-Key    | POST/PUT | Required on command endpoints    |

## curl Samples

### Create a form definition

```bash
curl -X POST http://localhost:8130/internal/v1/forms \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: form-create-001" \
  -d '{
    "code": "vitals-form",
    "name": "Vital Signs Form",
    "description": "Standard vitals capture form",
    "category": "clinical"
  }'
```

### Get a form by ID

```bash
curl http://localhost:8130/internal/v1/forms/{formId} \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)"
```

### Update a form

```bash
curl -X PUT http://localhost:8130/internal/v1/forms/{formId} \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: form-update-001" \
  -d '{
    "name": "Updated Vital Signs Form",
    "description": "Revised vitals capture form",
    "category": "clinical"
  }'
```

### Create a version (content must include "fields" array)

```bash
curl -X POST http://localhost:8130/internal/v1/forms/{formId}/versions \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: ver-create-001" \
  -d '{
    "contentJson": "{\"fields\": [{\"name\": \"systolic\", \"type\": \"number\"}, {\"name\": \"diastolic\", \"type\": \"number\"}]}"
  }'
```

### List versions for a form

```bash
curl http://localhost:8130/internal/v1/forms/{formId}/versions \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)"
```

### Publish the latest version

```bash
curl -X POST http://localhost:8130/internal/v1/forms/{formId}/publish \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: form-publish-001"
```

## Outbox Events

| Event Type                        | Topic                  | Trigger                    |
|-----------------------------------|------------------------|----------------------------|
| impilo.forms.form.created.v1      | impilo.forms.form      | Form definition created    |
| impilo.forms.form.updated.v1      | impilo.forms.form      | Form definition updated    |
| impilo.forms.version.created.v1   | impilo.forms.version   | New version created        |
| impilo.forms.form.published.v1    | impilo.forms.form      | Latest version published   |

## Database

- Schema: `forms`
- Tables: `form_definitions`, `form_versions`, `form_publications`, `event_outbox`, `idempotency_keys`
- Migration: `V001__init.sql`
