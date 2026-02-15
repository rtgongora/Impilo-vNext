# Integration Hub

Central routing and dispatch framework for inter-service command/event delivery in Impilo vNext.

## Purpose

The Integration Hub provides a registry of route definitions with match criteria, transform rules, and dispatch handling including dead-letter support. It is v1.1-native: all endpoints enforce mandatory headers, idempotency, federation authority, and timeout propagation via `tech-companion`.

## Endpoints

### POST /internal/v1/routes (national-only)

Create a route definition with match criteria, transform rules, and target config.

```bash
curl -X POST http://localhost:8110/internal/v1/routes \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: route-$(uuidgen)" \
  -d '{
    "matchMethod": "POST",
    "matchPathRegex": "/api/v1/orders/.*",
    "targetUrl": "http://oros:8089/internal/v1/orders",
    "targetTimeoutMs": 5000,
    "transformHeaders": {"X-Source": "X-Origin"},
    "transformFieldRenames": {"orderId": "order_id"},
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

Submit a dispatch command. The hub matches the request method+path against registered routes, applies transforms, records the attempt, and writes an outbox event. If no route matches, the dispatch is recorded as failed and a dead-letter entry is created.

```bash
curl -X POST http://localhost:8110/internal/v1/dispatch \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: dispatch-$(uuidgen)" \
  -d '{
    "method": "POST",
    "path": "/api/v1/orders/create",
    "body": "{\"orderId\":\"123\"}",
    "headers": {"X-Source": "oros"}
  }'
```

### GET /internal/v1/deadletters (any pod)

List dead-letter entries (paged). Supports optional `resolved` filter and `page`/`size` params.

```bash
curl "http://localhost:8110/internal/v1/deadletters?page=0&size=20&resolved=false" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)"
```

## Route Matching

Routes are matched against dispatch requests using:
- **matchMethod**: HTTP method (`POST`, `GET`, `PUT`, etc.) or `*` for any method
- **matchPathRegex**: Java regex pattern matched against the dispatch path

Both criteria must match for a route to be selected. Routes without match criteria (legacy v1 routes) are not matched by the dispatch engine.

## Transform Rules

When a route is matched, the hub applies configured transforms:
- **transformHeaders**: Maps incoming header names to new header names (e.g., `{"X-Old": "X-New"}`)
- **transformFieldRenames**: Renames top-level JSON fields in the request body (e.g., `{"orderId": "order_id"}`)

## Event Types Emitted

| Event Type | Trigger |
|---|---|
| `impilo.integration.route.created.v1` | Route created |
| `impilo.integration.dispatch.accepted.v1` | Dispatch matched a route and was accepted |
| `impilo.integration.dispatch.failed.v1` | Dispatch failed (no matching route) |

## Data Model

- **ih_route_definitions**: Route registry with match criteria, transforms, and target config
- **ih_dispatch_attempts**: Records every dispatch command (matched or unmatched)
- **ih_dead_letter_queue**: Failed dispatch attempts for later inspection/retry
- **ih_event_outbox**: v1.1 outbox events pending Kafka publication
- **idempotency_keys**: Idempotency deduplication (managed by tech-companion)

## Running Tests

```bash
cd services
mvn -pl integration-hub -am clean test
```
