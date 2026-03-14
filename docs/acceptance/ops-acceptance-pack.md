# Wave 13 — Ops Instrumentation Acceptance Pack

## 1. Module List

### Shared Library

- `libs/ops-instrumentation` — Spring Boot auto-configuration library providing
  structured logging, golden signal metrics, health indicators, and OpenTelemetry
  trace context propagation.

### Adopted Services

| Service | Port | Outbox Table |
|---------|------|-------------|
| tshepo-service | 8081 | `tshepo.event_outbox` |
| vito-service | 8082 | `vito.event_outbox` |
| varapi-service | 8083 | `varapi.event_outbox` |
| tuso-service | 8084 | `tuso.event_outbox` |
| integration-hub | 8110 | `ih_event_outbox` |
| notification-service | 8111 | `ns_event_outbox` |
| rules-service | 8112 | `rs_event_outbox` |
| forms-service | 8131 | `fs_event_outbox` |
| search-service | 8132 | `ss_event_outbox` |
| data-governance-service | 8220 | `dgv_event_outbox` |
| iot-ingestion-service | 8330 | `iot_event_outbox` |
| support-service | 8340 | `sup_event_outbox` |

---

## 2. Build & Test

```bash
cd services
mvn test -pl ../libs/ops-instrumentation -Dspring.profiles.active=test
```

---

## 3. Acceptance Criteria

### A) Structured Logging (MDC)

- [ ] `MdcFilter` injects `tenant_id`, `pod_id`, `request_id`, `correlation_id` into MDC
- [ ] `user_id` injected from `X-Actor-ID` when present
- [ ] MDC cleared after request completes (no leak between requests)

### B) OpenTelemetry Propagation

- [ ] `OtelPropagationFilter` extracts `traceparent` header
- [ ] `trace_id` and `span_id` available in MDC after extraction
- [ ] Graceful handling when `traceparent` header is absent (no errors, fields simply omitted)

### C) Golden Signal Metrics

- [ ] `impilo.ops.http.latency` histogram recorded per request
- [ ] `impilo.ops.http.errors` counter incremented on 4xx/5xx responses
- [ ] `impilo.ops.idempotency.replays` counter available
- [ ] `impilo.ops.idempotency.conflicts` counter available
- [ ] `impilo.ops.outbox.lag` gauge reports unpublished event count

### D) Health & Readiness

- [ ] `/actuator/health` returns `UP` when DB is reachable
- [ ] `/actuator/health` returns `DOWN` when DB is unreachable
- [ ] Outbox health indicator checks unpublished count vs configured threshold
- [ ] `/actuator/prometheus` endpoint exposes all `impilo_ops_*` metrics

### E) Service Adoption

- [ ] All Wave 9-12 v1.1-native services have `ops-instrumentation` dependency
- [ ] TSHEPO, VITO, TUSO, VARAPI pilots have `ops-instrumentation` dependency
- [ ] Each service configures the correct `outbox-table` name matching its schema
- [ ] Each service exposes actuator endpoints (`health`, `info`, `prometheus`, `metrics`)

---

## 4. Test Coverage Matrix

| Test Class | Type | Coverage |
|------------|------|----------|
| `OpsInstrumentationTest` | Integration | MDC injection, OTel propagation, traceparent parsing |

Test location:
`libs/ops-instrumentation/src/test/java/zw/gov/mohcc/impilo/ops/OpsInstrumentationTest.java`

---

## 5. Smoke Test Scripts

### Health check

```bash
# Replace <port> with the service port from the module list
curl -s http://localhost:<port>/actuator/health | jq .
```

Expected: `"status": "UP"` with `opsDatabaseHealth` and `opsOutboxHealth` components.

### Prometheus metrics

```bash
curl -s http://localhost:<port>/actuator/prometheus | grep impilo_ops
```

Expected: lines containing `impilo_ops_http_latency`, `impilo_ops_http_errors`,
`impilo_ops_outbox_lag`, `impilo_ops_idempotency_replays`,
`impilo_ops_idempotency_conflicts`.

### Verify MDC in logs

Make any authenticated request to the service, then inspect log output for:

```
tenant_id=<value> pod_id=<value> request_id=<value> correlation_id=<value> user_id=<value>
```

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

## 6. Failure Triage Map

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| No MDC fields in logs | `ops-instrumentation` not on the classpath | Add `ops-instrumentation` dependency to the service's `pom.xml` |
| No `/actuator/prometheus` endpoint | Missing actuator or Micrometer dependency | Add `spring-boot-starter-actuator` and `micrometer-registry-prometheus` |
| Outbox lag health shows DOWN | Outbox publisher not draining events | Check Kafka connectivity; verify the outbox publisher scheduled task is running; inspect logs for Kafka producer errors |
| `trace_id` missing from logs | No `traceparent` header on incoming request | Ensure the upstream caller (Envoy, API gateway, or test client) sends a W3C `traceparent` header |
| `user_id` missing from logs | No `X-Actor-ID` header | Ensure the request passes through TSHEPO (ext_authz), which injects `X-Actor-ID` |
| Metrics show 0 for all counters | `GoldenSignalsFilter` not registered | Verify auto-configuration is enabled (`impilo.ops.enabled=true`) and the library is on the classpath |
| Health endpoint returns 404 | Actuator endpoints not exposed | Add `management.endpoints.web.exposure.include=health,info,prometheus,metrics` to `application.yml` |
