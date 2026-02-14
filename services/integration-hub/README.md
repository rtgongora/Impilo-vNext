# Integration Hub

Central routing and dispatch framework for inter-service command/event delivery in Impilo vNext.

## Purpose

The Integration Hub provides a registry of route definitions and a dispatch endpoint for recording inter-service event delivery requests. It is v1.1-native: all endpoints enforce mandatory headers, idempotency, federation authority, and timeout propagation via `tech-companion`.

## Endpoints

### POST /internal/v1/routes (national-only)

Create or update a route definition.

```bash
curl -X POST http://localhost:8110/internal/v1/routes \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: route-$(uuidgen)" \
  -d '{
    "sourceService": "oros",
    "eventTypePrefix": "oros.order.*",
    "targetService": "pharmacy",
    "targetUrl": "http://pharmacy:8096/internal/v1/orders",
    "enabled": true
  }'
```

### GET /internal/v1/routes (any pod)

List route definitions for the current tenant.

```bash
curl http://localhost:8110/internal/v1/routes \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)"
```

### POST /internal/v1/dispatch (any pod)

Submit a dispatch request. The event is recorded in the outbox for later delivery.

```bash
curl -X POST http://localhost:8110/internal/v1/dispatch \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: dispatch-$(uuidgen)" \
  -d '{
    "sourceService": "oros",
    "eventType": "oros.order.placed",
    "targetService": "pharmacy",
    "payloadJson": "{\"orderId\":\"123\"}"
  }'
```

## Event Types Emitted

| Event Type | Trigger |
|---|---|
| `impilo.integration.route.upserted.v1` | Route created or updated |
| `impilo.integration.dispatch.requested.v1` | Dispatch request recorded |

## Data Model

- **ih_route_definitions**: Route registry (source, target, URL, enabled)
- **ih_event_outbox**: v1.1 outbox events pending Kafka publication
- **idempotency_keys**: Idempotency deduplication (managed by tech-companion)

## Running Tests

```bash
cd services
mvn -pl integration-hub -am clean test
```
