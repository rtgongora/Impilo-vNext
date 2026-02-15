# Observability Baseline

## Overview

Every Impilo v1.1 service exposes a consistent observability surface. This document
defines the baseline metrics, health checks, and logging standards that all services
must implement.

## Health Endpoints

All services expose Spring Boot Actuator endpoints:

| Endpoint | Purpose |
|----------|---------|
| `/actuator/health` | Liveness + readiness (Kubernetes probes) |
| `/actuator/health/liveness` | Liveness probe |
| `/actuator/health/readiness` | Readiness probe (includes DB, Kafka) |
| `/actuator/info` | Build info (git commit, version) |
| `/actuator/prometheus` | Prometheus metrics scrape endpoint |

## Metrics

### Standard Metrics (auto-collected)

- `http_server_requests_seconds` — request duration by method, URI, status
- `jvm_memory_used_bytes` — JVM heap/non-heap usage
- `jvm_threads_live_threads` — active thread count
- `hikaricp_connections_active` — active DB connections
- `spring_kafka_listener_seconds` — Kafka consumer processing time

### Custom Metrics (per service)

Each service should register domain-specific counters and timers:

```
impilo_<service>_operations_total{type="...", outcome="success|failure"}
impilo_<service>_operation_duration_seconds{type="..."}
impilo_<service>_outbox_published_total
impilo_<service>_outbox_lag_count
```

### Tagging Convention

All metrics are tagged with:

- `application` — service name (e.g., `tshepo-service`)
- `instance` — pod name (from `HOSTNAME` env var)

## Logging

### Format

All services use structured JSON logging in production:

```json
{
  "timestamp": "2025-01-15T10:30:00.000Z",
  "level": "INFO",
  "logger": "zw.gov.mohcc.impilo.pct.core.JourneyService",
  "message": "Journey state transition",
  "tenantId": "uuid",
  "correlationId": "uuid",
  "actorId": "string",
  "traceId": "hex",
  "spanId": "hex"
}
```

### Log Levels

| Level | Usage |
|-------|-------|
| ERROR | Unrecoverable failures, data integrity issues |
| WARN | Degraded operation, retry scenarios, missing optional data |
| INFO | State transitions, API calls, Kafka events published |
| DEBUG | Detailed processing steps (disabled in production) |

### MDC Context

Trust headers are injected into MDC for every request:

- `tenantId` — from `X-Tenant-Id`
- `actorId` — from `X-Actor-Id`
- `correlationId` — from `X-Correlation-Id`

## Dashboards

The observability-service (port 8210) maintains a registry of dashboard definitions.
Standard dashboards include:

1. **Service Health** — per-service liveness, error rates, latency P50/P95/P99
2. **Kafka Lag** — consumer group lag per topic partition
3. **Database Pool** — connection pool utilization, query duration
4. **Outbox Monitor** — unpublished event count, publish latency
5. **Trust Pipeline** — TSHEPO authorization rates, policy evaluation times

## Alert Rules

The observability-service also manages alert rule definitions. Standard alerts:

| Alert | Condition | Severity |
|-------|-----------|----------|
| High Error Rate | error_rate > 5% over 5m | CRITICAL |
| Slow Responses | p99_latency > 2s over 5m | WARNING |
| DB Pool Exhaustion | active_connections > 80% | ERROR |
| Kafka Lag | consumer_lag > 1000 | WARNING |
| Outbox Backlog | unpublished_count > 100 | ERROR |
| Service Down | health_check fails 3x | CRITICAL |

## Tracing

Distributed tracing is supported via OpenTelemetry:

- Traces propagated via W3C TraceContext headers
- Spans auto-created for HTTP requests, Kafka messages, DB queries
- Custom spans for domain operations (state transitions, external calls)
- Export to Grafana Tempo via OTLP gRPC
