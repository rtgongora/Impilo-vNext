# Wave 19A — Baseline Inventory: Production Readiness Discovery

> Date: 2026-03-14
> Scope: Ring 0 services only (TSHEPO, VITO, VARAPI, TUSO, ZIBO)
> Branch: `claude/review-project-manifest-jb5O0`

---

## 1. Ring 0 Service Inventory

### 1.1 Service Summary

| Service | Port | DB | Schema | Spring Boot | OAuth2/JWT | Redis | Kafka |
|---------|------|----|--------|------------|------------|-------|-------|
| tshepo-service | 8081 | `tshepo` | default | 3.3.6 | ✅ Keycloak | ✅ | ✅ |
| vito-service | 8082 | `vito` | `vito` | 3.3.6 | ✅ Keycloak | ✅ | ✅ |
| varapi-service | 8083 | `varapi` | `varapi` | 3.3.6 | ✅ Keycloak | ✅ | ✅ |
| tuso-service | 8084 | `tuso` | `tuso` | 3.3.6 | ✅ Keycloak | ✅ | ✅ |
| zibo-service | 8085 | `zibo` | default | 3.3.6 | ✅ Keycloak | ✅ | ✅ |

### 1.2 TSHEPO Sub-Services (Trust Plane Decomposition)

| Service | Port | Purpose |
|---------|------|---------|
| tshepo-authz-service | — | Authorization policy engine |
| tshepo-audit-service | — | Audit chain management |
| tshepo-consent-service | — | Consent management |
| tshepo-identity-service | — | Identity resolution |
| tshepo-keys-service | — | Key management |
| tshepo-offline-service | — | Offline capability tokens |

---

## 2. Health & Metrics Endpoint Inventory

### 2.1 Actuator Configuration

| Service | `management.endpoints.web.exposure.include` | `show-details` | Prometheus tag |
|---------|----------------------------------------------|----------------|----------------|
| tshepo-service | `health,info,prometheus,metrics` | `when-authorized` | `application: tshepo-service` |
| vito-service | `health,info,prometheus,metrics` | `when-authorized` | `application: vito-service` |
| varapi-service | `health,info,prometheus,metrics` | `when-authorized` | — (not explicitly tagged) |
| tuso-service | `health,info,prometheus,metrics` | `when-authorized` | `application: tuso-service` |
| zibo-service | `health,info,prometheus` | — (not set) | `application: zibo-service` |

**Observations:**
- ZIBO does not expose the `metrics` actuator endpoint (only `health,info,prometheus`)
- VARAPI does not have explicit `metrics.tags.application` in application.yml
- TUSO has explicit Redis and DB health indicators enabled (`management.health.redis.enabled: true`, `management.health.db.enabled: true`)

### 2.2 ops-instrumentation Adoption

| Service | `impilo.ops.enabled` | `impilo.ops.outbox-table` | Dependency Present |
|---------|---------------------|--------------------------|-------------------|
| tshepo-service | `true` | `tshepo.event_outbox` | ✅ (via shared-core) |
| vito-service | `true` | `vito.event_outbox` | ✅ (via shared-core) |
| varapi-service | `true` | `varapi.event_outbox` | ✅ (via shared-core) |
| tuso-service | `true` | `tuso.event_outbox` | ✅ (via shared-core) |
| zibo-service | — | — | ⚠️ NOT CONFIGURED |

**Key Finding:** ZIBO does not have `impilo.ops` configuration in its `application.yml`. This means it lacks:
- `MdcFilter` (structured logging context)
- `GoldenSignalsFilter` (latency histogram, error counter)
- `OtelPropagationFilter` (trace context)
- `opsOutboxHealth` (outbox lag health check)
- `opsDatabaseHealth` (DB health indicator)

### 2.3 SecurityBaselineConfig (Wave 14) Adoption

| Service | `SecurityBaselineConfig.java` | Rate Limiting | Input Sanitization | Admin Audit | SecretProvider |
|---------|-------------------------------|---------------|-------------------|-------------|----------------|
| tshepo-service | ✅ | ✅ (configurable via `tshepo.rate-limit`) | ✅ | ✅ | ✅ |
| vito-service | ✅ | ✅ (200 burst, 3/s refill) | ✅ | ✅ | ✅ |
| varapi-service | ✅ | ✅ (100 burst, 2/s refill) | ✅ | ✅ | ✅ |
| tuso-service | ✅ | ✅ (150 burst, 2/s refill) | ✅ | ✅ | ✅ |
| zibo-service | ✅ | ✅ (150 burst, 2/s refill) | ✅ | ✅ | ✅ |

All Ring 0 services have Wave 14 security baseline adopted.

---

## 3. Observability Infrastructure Inventory

### 3.1 Observability Stack (docker-compose.ops.yml)

| Component | Image | Port | Purpose |
|-----------|-------|------|---------|
| Prometheus | `prom/prometheus:v2.51.2` | 9090 | Metrics storage & query |
| Grafana | `grafana/grafana:10.4.2` | 3100 | Dashboards & visualization |
| OTel Collector | `otel/opentelemetry-collector-contrib:0.98.0` | 4317 (gRPC), 4318 (HTTP), 8889 (Prom exporter) | Trace/metric/log collection |
| Jaeger | `jaegertracing/all-in-one:1.56` | 16686 | Distributed tracing UI |

### 3.2 Prometheus Scrape Targets (Ring 0)

| Job Name | Target | Plane Label | Scrape Path |
|----------|--------|-------------|-------------|
| `tshepo` | `tshepo-service:8081` | `trust` | `/actuator/prometheus` |
| `vito` | `vito-service:8082` | `registry` | `/actuator/prometheus` |
| `varapi` | `varapi-service:8083` | `registry` | `/actuator/prometheus` |
| `tuso` | `tuso-service:8084` | `clinical` | `/actuator/prometheus` |
| `zibo` | `zibo-service:8085` | `clinical` | `/actuator/prometheus` |

All Ring 0 services are registered in Prometheus scrape config.

### 3.3 OTel Collector Pipeline

| Pipeline | Receivers | Processors | Exporters |
|----------|-----------|------------|-----------|
| Traces | OTLP | memory_limiter → batch → attributes → resource | Jaeger (OTLP), logging |
| Metrics | OTLP | memory_limiter → batch → resource | Prometheus exporter, logging |
| Logs | OTLP | memory_limiter → batch → resource | logging only |

### 3.4 ops-instrumentation Library Features

| Feature | Class | Purpose |
|---------|-------|---------|
| MDC injection | `MdcFilter` (order 5) | `tenant_id`, `pod_id`, `request_id`, `correlation_id`, `user_id` |
| OTel propagation | `OtelPropagationFilter` (order 4) | `trace_id`, `span_id` from W3C `traceparent` |
| Golden signal metrics | `GoldenSignalsFilter` (order 6) | `impilo_ops_http_latency`, `impilo_ops_http_errors` |
| Idempotency metrics | Auto-config | `impilo_ops_idempotency_replays`, `impilo_ops_idempotency_conflicts` |
| Outbox lag gauge | Auto-config | `impilo_ops_outbox_lag` |
| DB health | `opsDatabaseHealth` | `SELECT 1` liveness check |
| Outbox health | `opsOutboxHealth` | Unpublished event count vs threshold |

### 3.5 Baseline Documents

| Document | Path | Content |
|----------|------|---------|
| Observability Baseline | `docs/ops/OBSERVABILITY_BASELINE.md` | Standard metrics, logging format, MDC context, dashboard definitions, alert rules, tracing |
| Security Hardening Baseline | `docs/ops/SECURITY_HARDENING_BASELINE.md` | Trust pipeline, TLS, JWT validation, PII isolation, encryption, audit, policy packs |
| Observability Conventions | `docs/ops/observability-conventions.md` | Naming conventions, tagging |
| Security Baseline | `docs/ops/security-baseline.md` | Security posture reference |

---

## 4. Deployment Artifacts Inventory

### 4.1 Helm Charts

| Service | Helm Chart | Location |
|---------|-----------|----------|
| tshepo-service | ✅ | `services/tshepo-service/helm/` |
| vito-service | ✅ | `services/vito-service/helm/` |
| varapi-service | ✅ | `services/varapi-service/helm/` |
| tuso-service | ✅ | `services/tuso-service/helm/` |
| zibo-service | ✅ | `services/zibo-service/helm/` |

### 4.2 Flyway Migrations

| Service | Migrations | Location |
|---------|-----------|----------|
| tshepo-service | ✅ | `services/tshepo-service/src/main/resources/db/migration/` |
| vito-service | ✅ | `services/vito-service/src/main/resources/db/migration/` |
| varapi-service | ✅ | `services/varapi-service/src/main/resources/db/migration/` |
| tuso-service | ✅ | `services/tuso-service/src/main/resources/db/migration/` |
| zibo-service | ✅ | `services/zibo-service/src/main/resources/db/migration/` |

### 4.3 Docker Compose

- Main platform: `docker-compose.yml` (at repo root or `infra/`)
- Observability stack: `tools/ops/docker-compose.ops.yml`

---

## 5. Acceptance & Runbook Inventory

### 5.1 Acceptance Packs

| Pack | Path | Relevance to Wave 19 |
|------|------|---------------------|
| Ops Acceptance | `docs/acceptance/ops-acceptance-pack.md` | Direct — Wave 13 ops instrumentation acceptance |
| Security Acceptance | `docs/acceptance/security-acceptance-pack.md` | Direct — Wave 14 security baseline acceptance |
| Resilience-Ops Acceptance | `docs/acceptance/resilience-ops-acceptance-pack.md` | Direct — resilience tooling acceptance |
| Dev Runtime Acceptance | `docs/acceptance/dev-runtime-acceptance-pack.md` | Indirect — dev environment validation |
| Offline Acceptance | `docs/acceptance/offline-acceptance-pack.md` | Indirect — offline service acceptance |

### 5.2 Runbooks

| Runbook | Path | Coverage |
|---------|------|----------|
| Incident Response | `docs/resilience-ops-platform/runbooks/incident-response.md` | Triage, severity levels, escalation, common scenarios |
| Replay Failures | `docs/resilience-ops-platform/runbooks/replay-failures.md` | Outbox replay failure diagnosis |
| Restore Drill | `docs/resilience-ops-platform/runbooks/restore-drill.md` | DB backup restore procedure, chain verification |

### 5.3 Wave Documentation

| Wave | Document | Path |
|------|----------|------|
| 13 | Production Readiness Tooling | `docs/ops/wave13-production-readiness.md` |
| 14 | Security Hardening | `docs/security/wave14-security-hardening.md` |
| 15 | Offline & Edge | `docs/offline/wave15-offline-edge.md` |
| 19 | Production Readiness Gate (spec) | `docs/ops/wave19-production-readiness-gate.md` |

---

## 6. Security Posture Inventory

### 6.1 Trust Pipeline

| Layer | Component | Status |
|-------|-----------|--------|
| Gateway | Envoy (port 10000) — TLS termination, ext_authz | ✅ Code exists |
| Authz | TSHEPO ext_authz endpoint | ✅ Code exists |
| Headers | 14 trust headers contract | ✅ Defined in `TrustHeaders.java` + `contracts.ts` |
| Service filter | tech-companion `V11HeaderFilter` | ✅ Code exists |

### 6.2 Input Validation (Wave 14)

| Feature | Library | Status |
|---------|---------|--------|
| InputSanitizer | `libs/security-baseline` | ✅ Code complete |
| RateLimitGuard | `libs/security-baseline` | ✅ Code complete |
| AdminAuditEmitter | `libs/security-baseline` | ✅ Code complete |
| SecretProvider | `libs/security-baseline` | ✅ Code complete |
| RateLimitFilter | `services/shared-core` | ✅ Code complete |

### 6.3 Security Service

| Component | Port | Status |
|-----------|------|--------|
| security-hardening-service | 8220 | ✅ Code exists (policy packs, compliance scans) |

---

## 7. Consistency Matrix: What Each Ring 0 Service Has

| Capability | TSHEPO | VITO | VARAPI | TUSO | ZIBO |
|------------|:------:|:----:|:------:|:----:|:----:|
| Actuator health | ✅ | ✅ | ✅ | ✅ | ✅ |
| Actuator prometheus | ✅ | ✅ | ✅ | ✅ | ✅ |
| Actuator metrics | ✅ | ✅ | ✅ | ✅ | ❌ |
| ops-instrumentation (MDC) | ✅ | ✅ | ✅ | ✅ | ❌ |
| ops-instrumentation (golden signals) | ✅ | ✅ | ✅ | ✅ | ❌ |
| ops-instrumentation (OTel) | ✅ | ✅ | ✅ | ✅ | ❌ |
| ops-instrumentation (outbox health) | ✅ | ✅ | ✅ | ✅ | ❌ |
| SecurityBaselineConfig | ✅ | ✅ | ✅ | ✅ | ✅ |
| Helm chart | ✅ | ✅ | ✅ | ✅ | ✅ |
| Flyway migrations | ✅ | ✅ | ✅ | ✅ | ✅ |
| Prometheus scrape target | ✅ | ✅ | ✅ | ✅ | ✅ |
| Keycloak OAuth2 | ✅ | ✅ | ✅ | ✅ | ✅ |
| Redis | ✅ | ✅ | ✅ | ✅ | ✅ |
| Kafka producer | ✅ | ✅ | ✅ | ✅ | ✅ |
| Event outbox | ✅ | ✅ | ✅ | ✅ | ✅ |
| Explicit metrics tags | ✅ | ✅ | ❌ | ✅ | ✅ |
| Health show-details | ✅ | ✅ | ✅ | ✅ | ❌ |
| Explicit health indicators (Redis/DB) | ❌ | ❌ | ❌ | ✅ | ❌ |

---

## 8. Discovery Raw Outputs (Reference)

### 8.1 git status
```
On branch claude/review-project-manifest-jb5O0
Your branch is up to date with 'origin/claude/review-project-manifest-jb5O0'.
```

### 8.2 git log -1 --oneline
```
0636e4e refactor: wire DeathNotification and Verification controllers to services
```

### 8.3 Service POMs found
68 service `pom.xml` files (including parent `services/pom.xml` and `services/shared-core/pom.xml`)

### 8.4 Prometheus scrape targets
All Ring 0 services configured in `tools/ops/observability/prometheus/prometheus.yml` (15s scrape interval)

### 8.5 OTel references
- `libs/ops-instrumentation/` — OtelPropagationFilter, auto-config
- `tools/ops/docker-compose.ops.yml` — OTel Collector container
- `tools/ops/observability/otel/otel-collector-config.yaml` — OTLP receivers + Jaeger/Prometheus export

### 8.6 Runbook/acceptance references
- 3 runbooks in `docs/resilience-ops-platform/runbooks/`
- 11 acceptance packs in `docs/acceptance/`
