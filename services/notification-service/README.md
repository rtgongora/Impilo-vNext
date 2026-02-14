# Notification Service

Template-driven notification delivery engine for Impilo vNext (SMS, email, push).

## Purpose

The Notification Service manages notification templates and accepts send requests. It is v1.1-native: all endpoints enforce mandatory headers, idempotency, federation authority, and timeout propagation via `tech-companion`.

## Endpoints

### POST /internal/v1/templates (national-only)

Create or update a notification template.

```bash
curl -X POST http://localhost:8111/internal/v1/templates \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: tmpl-$(uuidgen)" \
  -d '{
    "channel": "SMS",
    "name": "appointment-reminder",
    "content": "Your appointment is on {{date}} at {{facility}}.",
    "enabled": true
  }'
```

### GET /internal/v1/templates (any pod)

List templates for the current tenant.

```bash
curl http://localhost:8111/internal/v1/templates \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)"
```

### POST /internal/v1/notify (any pod)

Request a notification send.

```bash
curl -X POST http://localhost:8111/internal/v1/notify \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: notify-$(uuidgen)" \
  -d '{
    "channel": "SMS",
    "to": "+263771234567",
    "templateId": null,
    "variables": {"date": "2026-03-01", "facility": "Parirenyatwa"}
  }'
```

## Event Types Emitted

| Event Type | Trigger |
|---|---|
| `impilo.notify.template.upserted.v1` | Template created or updated |
| `impilo.notify.send.requested.v1` | Notification send requested |

## Data Model

- **ns_templates**: Notification templates (channel, name, content, enabled)
- **ns_notification_requests**: Send requests (channel, recipient, status)
- **ns_event_outbox**: v1.1 outbox events pending Kafka publication
- **idempotency_keys**: Idempotency deduplication (managed by tech-companion)

## Running Tests

```bash
cd services
mvn -pl notification-service -am clean test
```
