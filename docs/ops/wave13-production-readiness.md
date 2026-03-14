# Wave 13 — Production Readiness Runbook

## 1. Overview

Wave 13 adds production readiness tooling across all platform services via the
`libs/ops-instrumentation` shared library. This library provides structured
logging (MDC injection), golden signal metrics, health indicators, and
OpenTelemetry trace context propagation as drop-in Spring Boot auto-configuration.

Every service that depends on `ops-instrumentation` automatically gains:

- Consistent MDC fields on every log line
- Prometheus-compatible golden signal metrics
- Custom health indicators for database connectivity and outbox lag
- W3C traceparent propagation (with or without a full OTel SDK)

---

## 2. Structured Logging

All services inject the following MDC fields into every log line via a chain of
servlet filters registered in strict order:

| Filter | Order | Responsibility |
|--------|-------|----------------|
| `OtelPropagationFilter` | 4 | Extracts `trace_id` and `span_id` from the W3C `traceparent` header |
| `MdcFilter` | 5 | Injects `tenant_id`, `pod_id`, `request_id`, `correlation_id`, `user_id` |
| `GoldenSignalsFilter` | 6 | Records latency histogram and error counter after response |
| `V11HeaderFilter` | 10 | Validates v1.1 trust header contract |

### MDC Fields

| Field | Source |
|-------|--------|
| `tenant_id` | Trust header `X-Tenant-ID` |
| `pod_id` | Trust header `X-Pod-ID` |
| `request_id` | Trust header `X-Request-ID` |
| `correlation_id` | Trust header `X-Correlation-ID` |
| `user_id` | Trust header `X-Actor-ID` |
| `trace_id` | W3C `traceparent` header (bytes 3-34) |
| `span_id` | W3C `traceparent` header (bytes 36-51) |

All MDC fields are cleared after each request completes to prevent context leaking
between requests on the same thread.

---

## 3. Golden Signal Metrics

All metrics are exposed at `/actuator/prometheus`.

### `impilo_ops_http_latency`

Histogram recording request duration in seconds.

| Label | Values |
|-------|--------|
| `endpoint_class` | `internal`, `external` |
| `method` | `GET`, `POST`, `PUT`, `DELETE`, etc. |
| `status` | HTTP status code |

### `impilo_ops_http_errors`

Counter incremented on every 4xx/5xx response.

| Label | Values |
|-------|--------|
| `endpoint_class` | `internal`, `external` |
| `method` | HTTP method |
| `status` | HTTP status code |
| `error_code` | Application-level error code (if available) |

### `impilo_ops_idempotency_replays`

Counter tracking the number of idempotent request replays (same idempotency key,
previously successful response returned from cache).

### `impilo_ops_idempotency_conflicts`

Counter tracking the number of idempotency key conflicts (same key, different
request body).

### `impilo_ops_outbox_lag`

Gauge reporting the number of unpublished events in the service's outbox table.
A sustained non-zero value indicates the outbox publisher is not keeping up with
event production or Kafka is unreachable.

---

## 4. Health Endpoints

Available at `/actuator/health`.

### `opsDatabaseHealth`

Executes `SELECT 1` against the service's primary datasource. Reports `UP` when
the query succeeds, `DOWN` otherwise.

### `opsOutboxHealth`

Queries the service's outbox table for the count of unpublished events (rows where
`published_at IS NULL`). Reports `UP` when the count is below the configured
threshold, `DOWN` when it equals or exceeds the threshold.

Default threshold: **1000**.

---

## 5. OpenTelemetry Integration

The `OtelPropagationFilter` (order 4) extracts the W3C `traceparent` header and
places `trace_id` and `span_id` into the SLF4J MDC. This works **without** a full
OpenTelemetry SDK being present on the classpath.

When the OTel Java agent is attached (e.g. in production), the agent's own context
propagation takes precedence and trace/span IDs flow into structured logs
automatically via the agent's log correlation feature.

This design ensures that:

- In local dev (no agent): trace context still appears in logs if the caller sends
  `traceparent`.
- In production (agent attached): full distributed tracing works end-to-end, and
  log correlation is automatic.

---

## 6. Configuration Reference

```yaml
impilo:
  ops:
    enabled: true                    # Master switch (default: true)
    outbox-table: ih_event_outbox    # Per-service outbox table name
    outbox-lag-threshold: 1000       # Health check threshold (default: 1000)

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
```

Each service must set `outbox-table` to match its own outbox table name. See the
Service Adoption Matrix below for the correct value per service.

---

## 7. Service Adoption Matrix

| Service | Port | Outbox Table | Status |
|---------|------|-------------|--------|
| tshepo-service | 8081 | `tshepo.event_outbox` | Adopted |
| vito-service | 8082 | `vito.event_outbox` | Adopted |
| varapi-service | 8083 | `varapi.event_outbox` | Adopted |
| tuso-service | 8084 | `tuso.event_outbox` | Adopted |
| integration-hub | 8110 | `ih_event_outbox` | Adopted |
| notification-service | 8111 | `ns_event_outbox` | Adopted |
| rules-service | 8112 | `rs_event_outbox` | Adopted |
| forms-service | 8131 | `fs_event_outbox` | Adopted |
| search-service | 8132 | `ss_event_outbox` | Adopted |
| data-governance-service | 8220 | `dgv_event_outbox` | Adopted |
| iot-ingestion-service | 8330 | `iot_event_outbox` | Adopted |
| support-service | 8340 | `sup_event_outbox` | Adopted |

---

## 8. Smoke Test Scripts

### Health check

```bash
# Replace <port> with the service port from the matrix above
curl -s http://localhost:<port>/actuator/health | jq .
```

Expected output includes `"status": "UP"` with `opsDatabaseHealth` and
`opsOutboxHealth` components.

### Prometheus metrics

```bash
curl -s http://localhost:<port>/actuator/prometheus | grep impilo_ops
```

Expected output includes `impilo_ops_http_latency`, `impilo_ops_http_errors`,
`impilo_ops_outbox_lag`, and idempotency counters.

### Verify MDC in logs

Make any authenticated request to the service (e.g. a GET to a list endpoint),
then inspect the service's stdout/log output. Every log line produced during that
request should contain:

```
tenant_id=<value> pod_id=<value> request_id=<value> correlation_id=<value> user_id=<value>
```

If a `traceparent` header was sent, `trace_id` and `span_id` should also appear.

### Per-service quick check

```bash
for port in 8081 8082 8083 8084 8110 8111 8112 8131 8132 8220 8330 8340; do
  echo "=== Port $port ==="
  curl -sf http://localhost:$port/actuator/health | jq -r '.status' 2>/dev/null || echo "UNREACHABLE"
  curl -sf http://localhost:$port/actuator/prometheus | grep -c impilo_ops 2>/dev/null || echo "NO METRICS"
  echo
done
```

---

## 9. Alerting Recommendations

> These are documentation-only recommendations. No alert rules are implemented
> as part of Wave 13.

### Outbox lag

```
Alert: ImpiloOutboxLagHigh
Condition: impilo_ops_outbox_lag > 100 for 5 minutes
Severity: warning
Action: Check Kafka connectivity, verify outbox publisher is running,
        inspect service logs for publishing errors.
```

### Error rate

```
Alert: ImpiloHttpErrorRateHigh
Condition: rate(impilo_ops_http_errors[5m]) > 10
Severity: warning
Action: Inspect error_code label distribution, check downstream
        service health, review recent deployments.
```

### Health endpoint down

```
Alert: ImpiloServiceUnhealthy
Condition: probe_success{job="impilo-health"} == 0 for 2 minutes
Severity: critical
Action: Check /actuator/health for component-level status,
        verify database and Kafka connectivity.
```

---

## 10. Failure Triage Map

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| No MDC fields in logs | `ops-instrumentation` not on the classpath | Add `ops-instrumentation` dependency to the service's `pom.xml` |
| No `/actuator/prometheus` endpoint | Missing actuator or Micrometer dependency | Add `spring-boot-starter-actuator` and `micrometer-registry-prometheus` |
| Outbox lag health shows DOWN | Outbox publisher not draining events | Check Kafka connectivity; verify the outbox publisher scheduled task is running; inspect logs for Kafka producer errors |
| `trace_id` missing from logs | No `traceparent` header on incoming request | Ensure the upstream caller (Envoy, API gateway, or test client) sends a W3C `traceparent` header |
| `user_id` missing from logs | No `X-Actor-ID` header | Ensure the request passes through TSHEPO (ext_authz), which injects `X-Actor-ID` |
| Metrics show 0 for all counters | `GoldenSignalsFilter` not registered | Verify auto-configuration is enabled (`impilo.ops.enabled=true`) and the library is on the classpath |
| Health endpoint returns 404 | Actuator endpoints not exposed | Add `management.endpoints.web.exposure.include=health,info,prometheus,metrics` to `application.yml` |
