# Integration Platform — Impilo vNext (Wave 7)

## Overview

The Integration Platform is a set of three **v1.1-native** services that provide cross-cutting infrastructure capabilities to the Impilo platform. These services are designed from the ground up to comply with Manifest v1.1 and the Tech Companion specification.

They do **not** replace or refactor any of the existing 16+ domain services. Instead, they provide reusable primitives that both legacy and new services can consume over time.

## Services

### A) integration-hub (port 8110)

**Purpose**: Central routing and dispatch framework for inter-service command/event delivery with route matching, transforms, and dead-letter handling.

| Endpoint | Method | Access | Description |
|---|---|---|---|
| `/internal/v1/routes` | POST | national-only | Create a route definition with match + transform config |
| `/internal/v1/routes` | GET | any pod | List current route definitions |
| `/internal/v1/dispatch` | POST | any pod | Submit dispatch command (method/path/body); matches routes, applies transforms, records attempt, writes outbox |
| `/internal/v1/deadletters` | GET | any pod | List dead-letter entries (paged, filterable by resolved status) |

**Route matching**: Routes define `matchMethod` (HTTP method or `*`) and `matchPathRegex` (Java regex) to match incoming dispatch requests.

**Transform rules**:
- `transformHeaders`: Header name mapping (e.g., `{"X-Old": "X-New"}`)
- `transformFieldRenames`: Top-level JSON field renaming (e.g., `{"orderId": "order_id"}`)

**Target config**: `targetUrl` (service URL) + `targetTimeoutMs` (timeout in milliseconds).

**Event types emitted**:
- `impilo.integration.route.created.v1`
- `impilo.integration.dispatch.accepted.v1`
- `impilo.integration.dispatch.failed.v1`

**Data model**:
- `ih_route_definitions` — Route registry with match criteria, transforms, target config
- `ih_dispatch_attempts` — Records every dispatch command (matched or unmatched)
- `ih_dead_letter_queue` — Failed dispatch attempts for inspection/retry
- `ih_event_outbox` — v1.1 outbox events pending Kafka publication

### B) notification-service (port 8111)

**Purpose**: Template-driven notification delivery engine (SMS, email, push).

| Endpoint | Method | Access | Description |
|---|---|---|---|
| `/internal/v1/templates` | POST | national-only | Create/update a notification template |
| `/internal/v1/templates` | GET | any pod | List templates |
| `/internal/v1/notify` | POST | any pod | Request a notification send |

**Event types emitted**:
- `impilo.notify.template.upserted.v1`
- `impilo.notify.send.requested.v1`

**Data model**: Template (channel, name, content, enabled), NotificationRequest (channel, to, template_id, variables, status)

### C) rules-service (port 8112)

**Purpose**: Simple rule storage and evaluation with decision logging.

| Endpoint | Method | Access | Description |
|---|---|---|---|
| `/internal/v1/rules` | POST | national-only | Create/update a rule |
| `/internal/v1/rules` | GET | any pod | List rules |
| `/internal/v1/evaluate` | POST | any pod | Evaluate facts against active rules |

**Event types emitted**:
- `impilo.rules.rule.upserted.v1`
- `impilo.rules.decision.recorded.v1`

**Data model**: Rule (name, expression, enabled), DecisionLog (rule_id, outcome, reason, facts)

**Rule DSL**: Simple boolean expression language supporting:
- Fact references: `facts.age`, `facts.country`
- Comparisons: `==`, `!=`, `>`, `>=`, `<`, `<=`
- Logical operators: `AND`, `OR`
- Parentheses for grouping
- Example: `facts.age >= 18 AND facts.country == 'ZW'`

## v1.1 Compliance

All three services enforce the full v1.1 contract via `tech-companion` auto-configuration:

1. **Header enforcement**: X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID required on all `/internal/v1/**` endpoints
2. **Idempotency**: `Idempotency-Key` required on all POST/PUT/PATCH commands; replay on same key+body, 409 on conflict
3. **Federation authority**: National-only endpoints reject non-national pods with 403
4. **Timeout propagation**: X-Client-Timeout-MS honored via timeout filter
5. **Error envelope**: All errors return canonical `{"error": {"code", "message", "details", "request_id", "correlation_id"}}` format

## Outbox Pattern (v1.1 EventEnvelope)

Each service persists events to a `*_event_outbox` table before any external publishing. The outbox schema includes all v1.1 envelope fields:

| Column | Description |
|---|---|
| tenant_id | Tenant scope |
| pod_id | Originating pod |
| correlation_id | End-to-end correlation |
| idempotency_key | Client deduplication key |
| event_type | Dot-notation event type |
| schema_version | Payload schema version |
| occurred_at | When the domain event happened |
| payload_json | Serialized event payload |
| published_at | NULL until published to Kafka |

## How These Services Connect to Legacy Services

These services are designed to integrate with the existing fleet **without requiring legacy changes**:

### integration-hub
- Legacy services can register routes via the `/internal/v1/routes` endpoint
- The dispatch endpoint accepts method/path/body and matches against registered routes
- Transform rules allow adapting payloads between services with different field conventions
- Failed dispatches are captured in a dead-letter queue for ops visibility
- Future: A Kafka consumer will read legacy outbox events and route them through the hub

### notification-service
- Any service (legacy or new) can call `/internal/v1/notify` to send notifications
- Templates are managed centrally, reducing per-service notification logic
- Future: Kafka consumers will trigger notifications from domain events automatically

### rules-service
- Services can evaluate business rules without embedding rule logic
- Decision logs provide an audit trail for rule-based decisions
- Future: Rules can be updated at runtime without service redeployment

## Running Tests (when Maven is online)

```bash
# Build and test all three services
cd services
mvn -pl integration-hub,notification-service,rules-service -am clean verify

# Run a single service's tests
mvn -pl integration-hub clean test
mvn -pl notification-service clean test
mvn -pl rules-service clean test
```

## Architecture Decisions

1. **No Kafka wiring yet**: Services persist outbox events to the database. A future outbox publisher (cron or CDC) will handle Kafka delivery. This keeps the services self-contained for initial deployment.

2. **H2 for tests**: Tests use H2 in PostgreSQL-compatibility mode with `ddl-auto: create-drop`. Flyway is disabled in tests to avoid dialect issues.

3. **InMemoryIdempotencyRepository for tests**: The tech-companion auto-configuration uses an in-memory implementation when no JdbcTemplate bean is present (or when H2 is the datasource).

4. **Service-agnostic design**: No country-specific constants. Tenant/pod are runtime parameters passed via headers. Test data uses placeholder values like `moh-zw` / `national`.
