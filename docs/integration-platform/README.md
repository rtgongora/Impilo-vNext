# Integration Platform — Impilo vNext (Wave 7+)

## Overview

The Integration Platform is a set of **v1.1-native** services that provide cross-cutting infrastructure capabilities to the Impilo platform. These services are designed from the ground up to comply with Manifest v1.1 and the Tech Companion specification.

They do **not** replace or refactor any of the existing 16+ domain services. Instead, they provide reusable primitives that both legacy and new services can consume over time.

The platform spans two waves:
- **Wave 7** (initial): integration-hub, notification-service, rules-service
- **Wave 7+** (expansion): workflow-service, connector-fhir-adapter, plus enhancements to search-service and forms-service

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

**Purpose**: Rule registry with versioning, activation lifecycle, evaluation audit, and decision logging.

| Endpoint | Method | Access | Description |
|---|---|---|---|
| `/internal/v1/rules` | POST | national-only | Create a rule container (key + name) |
| `/internal/v1/rules` | GET | any pod | List rules for tenant |
| `/internal/v1/rules/{key}/versions` | POST | national-only | Create a new DSL version for a rule |
| `/internal/v1/rules/{key}/activate?version=N` | POST | national-only | Activate a specific version |
| `/internal/v1/rules/{key}/deactivate` | POST | national-only | Deactivate rule (evaluations return 422) |
| `/internal/v1/rules/{key}/evaluate` | POST | any pod | Evaluate facts against active version; writes audit row |

**Lifecycle**: `CREATE rule → CREATE version(s) → ACTIVATE → EVALUATE → DEACTIVATE`

**Event types emitted**:
- `impilo.rules.rule.created.v1`
- `impilo.rules.version.created.v1`
- `impilo.rules.rule.activated.v1`
- `impilo.rules.rule.deactivated.v1`
- `impilo.rules.evaluated.v1`

**Data model**:
- `rs_rules` — Rule containers (key, name, status, tenant_id)
- `rs_rule_versions` — Versioned DSL expressions (rule_id, version, dsl_text)
- `rs_rule_activations` — Activation windows (rule_id, version_id, active_from, active_to)
- `rs_evaluation_audit` — Audit trail (rule_key, version, input_hash SHA-256, result_json)
- `rs_decision_logs` — Legacy v1 evaluation outcomes (kept for backward compat)

**Rule DSL**: Simple boolean expression language supporting:
- Fact references: `facts.age`, `facts.country`
- Comparisons: `==`, `!=`, `>`, `>=`, `<`, `<=`
- Logical operators: `AND`, `OR`
- Parentheses for grouping
- Example: `facts.age >= 18 AND facts.country == 'ZW'`

---

## Wave 7+ Expansion Services

### D) workflow-service (port 8140)

**Purpose**: Workflow engine for versioned definitions with step-based task execution, state transitions, and temporal snapshots.

**Lifecycle**:
- Definitions: `DRAFT → PUBLISHED` (immutable once published)
- Instances: `CREATED → RUNNING → COMPLETED | FAILED | CANCELLED`
- Tasks: `PENDING → IN_PROGRESS → COMPLETED` (auto-created per step)

| Endpoint | Method | Access | Description |
|---|---|---|---|
| `/internal/v1/workflows/definitions` | POST | national-only | Create a workflow definition (starts as DRAFT) |
| `/internal/v1/workflows/definitions` | GET | any pod | List definitions (filterable by status, category) |
| `/internal/v1/workflows/definitions/{id}/publish` | POST | national-only | Publish a DRAFT definition |
| `/internal/v1/workflows/instances` | POST | any pod | Start a workflow instance from a PUBLISHED definition |
| `/internal/v1/workflows/instances` | GET | any pod | List instances (filterable by status) |
| `/internal/v1/workflows/instances/{id}/transition` | POST | any pod | Transition instance (COMPLETE, FAIL, CANCEL, ADVANCE) |
| `/internal/v1/workflows/instances/{id}/tasks` | GET | any pod | List tasks for an instance |
| `/internal/v1/snapshots/workflows` | GET | any pod | Temporal snapshot of definitions |
| `/internal/v1/snapshots/instances` | GET | any pod | Temporal snapshot of instances |

**Event types emitted**:
- `impilo.workflow.definition.created.v1`
- `impilo.workflow.definition.published.v1`
- `impilo.workflow.instance.started.v1`
- `impilo.workflow.instance.transitioned.v1`

**Data model**:
- `wf_definitions` — Versioned workflow definitions with steps_json, category, status
- `wf_instances` — Running workflow instances with current_step, context_json
- `wf_tasks` — Individual step tasks with assignee, input/output JSON
- `wf_event_outbox` — v1.1 outbox events

### E) connector-fhir-adapter (port 8150)

**Purpose**: FHIR adapter boundary that accepts FHIR bundles, routes to configured destinations, validates headers, and audits every relay decision.

| Endpoint | Method | Access | Description |
|---|---|---|---|
| `/internal/v1/fhir/relay` | POST | any pod | Submit a FHIR bundle for relay to a destination |
| `/internal/v1/fhir/destinations` | POST | national-only | Create a relay destination |
| `/internal/v1/fhir/destinations` | GET | any pod | List configured FHIR destinations |
| `/internal/v1/fhir/audit` | GET | any pod | List relay audit log (paged) |
| `/internal/v1/fhir/audit/bundle/{bundleId}` | GET | any pod | Get audit entries for a specific bundle |

**Relay flow**: Bundle received → destination resolved (explicit or default) → resource type validated against destination filter → audit record created (ACCEPTED/REJECTED) → outbox event emitted.

**Event types emitted**:
- `impilo.integration.relay.accepted.v1`
- `impilo.integration.relay.rejected.v1`
- `impilo.integration.destination.created.v1`

**Data model**:
- `cfa_relay_destinations` — Destination registry (endpoint_url, resource_types filter, auth config)
- `cfa_relay_audit` — Audit trail of every relay decision with outcome
- `cfa_event_outbox` — v1.1 outbox events

### F) search-service enhancements

Two new governed query surfaces added to the existing search-service:

| Endpoint | Method | Description |
|---|---|---|
| `/internal/v1/search/clinical-safe` | POST | Search with governance: strips metadata from hits, returns only title/snippet/score |
| `/internal/v1/search/global` | POST | Full search including metadata (requires stricter authorization) |

**Clinical-safe governance**: The clinical-safe endpoint removes metadata from search results to prevent accidental exposure of sensitive categorization data in clinical contexts. The global endpoint returns full results for authorized administrative use.

### G) forms-service enhancements

New snapshot endpoint added to the existing forms-service:

| Endpoint | Method | Description |
|---|---|---|
| `/internal/v1/snapshots/forms` | GET | Temporal snapshot of form definitions with `as_of` parameter |

---

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
- Services can evaluate versioned business rules without embedding rule logic
- Rules are versioned and activated/deactivated at runtime without redeployment
- SHA-256 input hashing and audit rows provide tamper-evident evaluation records
- Decision logs and outbox events enable downstream analytics and compliance

### workflow-service
- Provides a general-purpose workflow engine that any service can use for multi-step processes
- Definitions are versioned and immutable once published, ensuring reproducibility
- Instances track current step and support COMPLETE, FAIL, CANCEL, and ADVANCE transitions
- Snapshot endpoints enable point-in-time queries for reporting and audit

### connector-fhir-adapter
- Acts as a boundary service for outbound FHIR bundle relay
- Destinations are configurable with resource type filters and auth configuration
- Every relay decision (ACCEPTED or REJECTED) is recorded in an audit trail
- Outbox events enable downstream consumers to react to relay outcomes
- Default destination fallback ensures bundles are always routed

### search-service (governed query surfaces)
- Clinical-safe endpoint strips metadata to prevent accidental exposure in clinical UIs
- Global endpoint provides full search results for authorized administrative contexts
- Both surfaces use the same underlying search engine; governance is applied at the API layer

### forms-service (snapshot endpoints)
- Temporal snapshot endpoint supports `as_of` parameter for point-in-time form definition queries
- Integrates with the existing form definition lifecycle without modifying core logic

## Running Tests (when Maven is online)

```bash
# Build and test all integration platform services
cd services
mvn -pl integration-hub,notification-service,rules-service,workflow-service,connector-fhir-adapter -am clean verify

# Run individual service tests
mvn -pl workflow-service clean test
mvn -pl connector-fhir-adapter clean test
```

## Architecture Decisions

1. **No Kafka wiring yet**: Services persist outbox events to the database. A future outbox publisher (cron or CDC) will handle Kafka delivery. This keeps the services self-contained for initial deployment.

2. **H2 for tests**: Tests use H2 in PostgreSQL-compatibility mode with `ddl-auto: create-drop`. Flyway is disabled in tests to avoid dialect issues.

3. **InMemoryIdempotencyRepository for tests**: The tech-companion auto-configuration uses an in-memory implementation when no JdbcTemplate bean is present (or when H2 is the datasource).

4. **Service-agnostic design**: No country-specific constants. Tenant/pod are runtime parameters passed via headers. Test data uses placeholder values like `moh-zw` / `national`.
