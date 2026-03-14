# Observability Conventions — Impilo vNext

> Canonical reference for log fields, trace propagation, metric naming,
> and golden signals across all Impilo v1.1 / v3 services.

## 1. Required Log Fields

Every structured log entry MUST include the following MDC-injected fields
(populated from v1.1 trust headers by the shared `CorrelationMdcFilter`):

| Field            | Source Header       | Required | Notes                            |
|------------------|---------------------|----------|----------------------------------|
| `tenant_id`      | `X-Tenant-ID`       | Yes      | UUID — tenant isolation boundary |
| `pod_id`         | `X-Pod-ID`          | Yes      | Federation pod identifier        |
| `request_id`     | `X-Request-ID`      | Yes      | Unique per-request identifier    |
| `correlation_id` | `X-Correlation-ID`  | Yes      | End-to-end correlation chain     |
| `actor_id`       | JWT `sub` claim     | If available | Authenticated actor identity  |
| `trace_id`       | W3C TraceContext    | Auto     | Injected by OTel agent           |
| `span_id`        | W3C TraceContext    | Auto     | Injected by OTel agent           |

### Log Format (JSON, production)

```json
{
  "timestamp": "2026-03-14T10:30:00.000Z",
  "level": "INFO",
  "logger": "zw.gov.mohcc.impilo.pct.core.JourneyService",
  "message": "Journey state transition",
  "tenant_id": "550e8400-e29b-41d4-a716-446655440000",
  "pod_id": "national",
  "request_id": "d290f1ee-6c54-4b01-90e6-d701748f0851",
  "correlation_id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "actor_id": "nurse-001",
  "trace_id": "4bf92f3577b34da6a3ce929d0e0e4736",
  "span_id": "00f067aa0ba902b7"
}
```

### Logback Pattern (local dev)

```
%d{ISO8601} [%thread] %-5level %logger{36} - [tenant=%X{tenant_id} pod=%X{pod_id} req=%X{request_id} corr=%X{correlation_id}] %msg%n
```

### PII Rules

- **NEVER** log PII (patient names, national IDs, phone numbers)
- Log CPIDs only — PII resolution happens exclusively via VITO
- Mask or redact any field that could contain PII before logging

## 2. Trace Propagation Rules

| Rule | Detail |
|------|--------|
| Protocol | W3C TraceContext (`traceparent` / `tracestate` headers) |
| Propagation | Automatic via OpenTelemetry Java Agent or Micrometer Tracing |
| `correlation_id` injection | `CorrelationMdcFilter` copies `X-Correlation-ID` → trace baggage key `correlation_id` |
| Kafka propagation | OTel auto-instruments Kafka producer/consumer; `traceparent` carried in Kafka headers |
| Cross-service calls | `RestTemplate` / `WebClient` auto-propagate W3C headers when OTel agent is attached |
| Span naming | `HTTP {METHOD} {path_template}` for HTTP, `{topic} send/process` for Kafka |

### Custom Span Requirements

Services MUST create explicit spans for:

- Domain state transitions (e.g., journey state change, order status update)
- External system calls (MOSIP, eLMIS, DHIS2)
- Break-glass activations
- Outbox event publication

## 3. Metric Naming and Label Rules

### Naming Convention

```
impilo_<service>_<domain>_<unit>
```

Examples:

| Metric | Type | Description |
|--------|------|-------------|
| `impilo_pct_journeys_total` | Counter | Total journeys created |
| `impilo_pct_journey_duration_seconds` | Histogram | Journey processing duration |
| `impilo_vito_registrations_total` | Counter | Patient registrations |
| `impilo_tshepo_policy_decisions_total` | Counter | Policy evaluation outcomes |
| `impilo_support_tickets_total` | Counter | Support tickets created |

### Required Labels (all custom metrics)

| Label | Description |
|-------|-------------|
| `service` | Service name (e.g., `tshepo-service`) |
| `tenant_id` | Tenant UUID (use `unknown` if unavailable) |
| `pod_id` | Pod identifier (e.g., `national`, `harare-central`) |
| `outcome` | `success` or `failure` |

### Label Cardinality Rules

- **DO NOT** use unbounded values as labels (e.g., patient IDs, request IDs)
- `tenant_id` is acceptable (bounded by number of tenants, typically < 100)
- `pod_id` is acceptable (bounded by number of pods, typically < 50)
- Use `_total` suffix for counters, `_seconds` for durations, `_bytes` for sizes

### Auto-Collected Metrics (Spring Boot Actuator + Micrometer)

| Metric | Source |
|--------|--------|
| `http_server_requests_seconds` | Micrometer HTTP server instrumentation |
| `jvm_memory_used_bytes` | JVM memory MXBean |
| `jvm_threads_live_threads` | JVM threading |
| `hikaricp_connections_active` | HikariCP connection pool |
| `spring_kafka_listener_seconds` | Spring Kafka consumer |
| `impilo_v11_requests` | Tech Companion `CompanionMetrics` |
| `impilo_v11_decisions` | Tech Companion `CompanionMetrics` |
| `impilo_v11_idempotency_replays` | Tech Companion `CompanionMetrics` |

## 4. Golden Signals per Ring 0 Services

### Ring 0 = Services that MUST NOT fail for care delivery

| Service | Port | Plane |
|---------|------|-------|
| TSHEPO (Authz) | 8081 | Trust & Governance |
| VITO (Patient Registry) | 8082 | Registry Spine |
| TUSO (Facility Registry) | 8084 | Registry Spine |
| PCT (Patient Care Tracker) | 8088 | Clinical Execution |

### Golden Signal Definitions

#### Latency

| Service | SLO | Metric |
|---------|-----|--------|
| TSHEPO | P99 < 200ms | `http_server_requests_seconds{service="tshepo",quantile="0.99"}` |
| VITO | P99 < 500ms | `http_server_requests_seconds{service="vito",quantile="0.99"}` |
| TUSO | P99 < 300ms | `http_server_requests_seconds{service="tuso",quantile="0.99"}` |
| PCT | P99 < 1s | `http_server_requests_seconds{service="pct",quantile="0.99"}` |

#### Traffic

| Signal | Metric |
|--------|--------|
| Requests/sec per service | `rate(http_server_requests_seconds_count[5m])` |
| Kafka messages/sec | `rate(spring_kafka_listener_seconds_count[5m])` |

#### Errors

| Signal | Metric | Alert Threshold |
|--------|--------|-----------------|
| HTTP 5xx rate | `rate(http_server_requests_seconds_count{status=~"5.."}[5m])` | > 1% of traffic |
| Policy DENY rate | `rate(impilo_v11_decisions{decision="DENY"}[5m])` | > 5% of decisions |
| Outbox backlog | `impilo_<service>_outbox_lag_count` | > 100 unpublished |

#### Saturation

| Signal | Metric | Alert Threshold |
|--------|--------|-----------------|
| DB pool utilization | `hikaricp_connections_active / hikaricp_connections_max` | > 80% |
| JVM heap utilization | `jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}` | > 85% |
| Kafka consumer lag | Consumer group lag (external metric) | > 1000 messages |

## 5. Actuator Endpoint Configuration

Every service MUST include in `application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true
  metrics:
    tags:
      application: ${spring.application.name}
```

## 6. Observability Stack (Local Dev)

Start the stack:

```bash
docker compose -f tools/ops/docker-compose.ops.yml up -d
```

| Component | URL | Purpose |
|-----------|-----|---------|
| Prometheus | http://localhost:9090 | Metric queries |
| Grafana | http://localhost:3100 | Dashboards (admin/admin) |
| Jaeger | http://localhost:16686 | Distributed traces |
| OTel Collector | localhost:4317 (gRPC), :4318 (HTTP) | Telemetry ingestion |

## 7. Verification

Run the verification script to check service observability readiness:

```bash
scripts/ops/verify-observability.sh <service-host> <port>
```
