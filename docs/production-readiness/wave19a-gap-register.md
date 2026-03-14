# Wave 19A — Gap Register: Production Readiness

> Date: 2026-03-14
> Scope: Ring 0 services only (TSHEPO, VITO, VARAPI, TUSO, ZIBO)
> Branch: `claude/review-project-manifest-jb5O0`

---

## Gap Summary

| ID | Severity | Category | Gap Description | Affected Service(s) | Blocking Wave 19 |
|----|----------|----------|----------------|---------------------|:----------------:|
| G-01 | **HIGH** | Observability | ZIBO missing `impilo.ops` configuration | zibo-service | ✅ |
| G-02 | MEDIUM | Observability | ZIBO `metrics` actuator endpoint not exposed | zibo-service | ✅ |
| G-03 | LOW | Observability | VARAPI missing `metrics.tags.application` | varapi-service | ❌ |
| G-04 | LOW | Observability | ZIBO missing `health.show-details` config | zibo-service | ❌ |
| G-05 | **HIGH** | SLOs | No SLO recording rules defined in Prometheus | All Ring 0 | ✅ |
| G-06 | **HIGH** | Alerting | No error budget burn rate alerts defined | All Ring 0 | ✅ |
| G-07 | **HIGH** | Performance | No load test scripts or baseline measurements | All Ring 0 | ✅ |
| G-08 | MEDIUM | Security | mTLS between services not verified (code exists, not tested under load) | All Ring 0 | ✅ |
| G-09 | MEDIUM | Security | Secrets rotation plan documented but not executed | All Ring 0 | ✅ |
| G-10 | **HIGH** | Resilience | "No care blocked by data plane" scenario not tested | All Ring 0 | ✅ |
| G-11 | MEDIUM | Alerting | Alert routing (PagerDuty/Opsgenie) not configured | All Ring 0 | ✅ |
| G-12 | MEDIUM | Runbooks | Alert-specific runbooks not written (only 3 generic runbooks exist) | All Ring 0 | ✅ |
| G-13 | LOW | Observability | Grafana dashboards defined in docs but not verified as provisioned | All Ring 0 | ❌ |
| G-14 | MEDIUM | Observability | TUSO/ZIBO labeled as `plane: clinical` in Prometheus (should be `plane: registry` for TUSO) | tuso-service | ❌ |
| G-15 | LOW | Observability | Only 4 of 5 Ring 0 services have explicit Redis/DB health indicators | TSHEPO, VITO, VARAPI, ZIBO | ❌ |

---

## Gap Details

### G-01 — ZIBO Missing ops-instrumentation Configuration (HIGH)

**Current state:** `zibo-service/src/main/resources/application.yml` has no `impilo.ops` section.

**Impact:** ZIBO lacks:
- Structured logging MDC fields (`tenant_id`, `pod_id`, `request_id`, `correlation_id`, `user_id`)
- Golden signal metrics (`impilo_ops_http_latency`, `impilo_ops_http_errors`)
- OTel trace context propagation (`trace_id`, `span_id` in MDC)
- Outbox health indicator (`opsOutboxHealth`)
- Database health indicator (`opsDatabaseHealth`)

**Required fix:**
```yaml
# Add to zibo-service application.yml
impilo:
  ops:
    enabled: true
    outbox-table: zibo.event_outbox
```

**Wave 19 phase:** 19B (implementation)

---

### G-02 — ZIBO Metrics Actuator Not Exposed (MEDIUM)

**Current state:** ZIBO exposes `health,info,prometheus` but NOT `metrics`.

**Impact:** Cannot query individual metric values via `/actuator/metrics/{metric.name}` (useful for debugging and ad-hoc queries). Prometheus scraping still works via `/actuator/prometheus`.

**Required fix:**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
```

**Wave 19 phase:** 19B (implementation)

---

### G-03 — VARAPI Missing Metrics Application Tag (LOW)

**Current state:** VARAPI `application.yml` does not have `management.metrics.tags.application`.

**Impact:** Prometheus metrics from VARAPI may not be tagged with `application=varapi-service`, making multi-service dashboards inconsistent. The Prometheus job label (`varapi`) provides a workaround.

**Required fix:**
```yaml
management:
  metrics:
    tags:
      application: varapi-service
```

**Wave 19 phase:** 19B (implementation)

---

### G-04 — ZIBO Missing health.show-details (LOW)

**Current state:** ZIBO does not set `management.endpoint.health.show-details`.

**Impact:** Health endpoint returns only top-level `UP`/`DOWN` without component details. Other Ring 0 services set `when-authorized` to show component details to authenticated callers.

**Required fix:**
```yaml
management:
  endpoint:
    health:
      show-details: when-authorized
```

**Wave 19 phase:** 19B (implementation)

---

### G-05 — No SLO Recording Rules (HIGH)

**Current state:** Prometheus is configured with scrape targets but no recording rules or SLO definitions exist.

**Impact:** Cannot measure SLO compliance. No historical SLO data for error budget calculations.

**Required artifacts:**
- `tools/ops/observability/prometheus/rules/slo-recording-rules.yml`
- Grafana SLO compliance dashboard
- Per-service SLO target definitions

**Wave 19 phase:** 19C (SLO definition + alerting)

---

### G-06 — No Error Budget Burn Rate Alerts (HIGH)

**Current state:** Alert rules are documented in `docs/ops/OBSERVABILITY_BASELINE.md` and `docs/ops/wave13-production-readiness.md` but no actual Prometheus alerting rules files exist.

**Impact:** No automated alerting on SLO breaches. On-call team relies on manual dashboard observation.

**Required artifacts:**
- `tools/ops/observability/prometheus/rules/slo-alerts.yml`
- Alert routing configuration (Alertmanager)
- PagerDuty/Opsgenie integration

**Wave 19 phase:** 19C (SLO definition + alerting)

---

### G-07 — No Load Test Scripts or Baselines (HIGH)

**Current state:** No performance test scripts exist in the repository. No baseline measurements for p50/p95/p99 latency, throughput, or resource utilization.

**Impact:** Cannot assess whether the platform meets performance SLOs. No baseline for regression detection.

**Required artifacts:**
- `scripts/perf/` directory with k6 or Gatling test scripts
- Baseline measurements for each Ring 0 service
- Performance regression detection in CI

**Wave 19 phase:** 19D (load testing + baselines)

---

### G-08 — mTLS Not Verified Under Load (MEDIUM)

**Current state:** mTLS configuration exists in docs and Envoy config, but has not been verified under realistic load conditions.

**Impact:** mTLS may introduce latency overhead not captured in baselines. Certificate rotation under load not tested.

**Required actions:**
- mTLS bypass test (verify plaintext calls rejected)
- Certificate rotation during load test
- Latency comparison: mTLS on vs off

**Wave 19 phase:** 19D (security posture verification)

---

### G-09 — Secrets Rotation Not Executed (MEDIUM)

**Current state:** `SecretProvider` abstraction exists (Wave 14). `EnvSecretProvider` for dev, `VaultSecretProvider` config contract defined. Rotation plan documented in `docs/ops/SECURITY_HARDENING_BASELINE.md`.

**Impact:** Rotation procedure never executed. Unknown if rotation causes downtime.

**Required actions:**
- Execute DB credential rotation in staging
- Execute Kafka SASL rotation in staging
- Verify Keycloak key rotation
- Document actual downtime (if any)

**Wave 19 phase:** 19D (security posture verification)

---

### G-10 — "No Care Blocked by Data Plane" Not Tested (HIGH)

**Current state:** The requirement exists in Wave 19 spec but no test scenario has been executed.

**Impact:** Cannot prove clinical workflows continue during data plane degradation.

**Required actions:**
- Test matrix: Kafka down, BUTANO slow, VITO degraded, Redis down, DB replica lag
- Verify outbox buffers events during Kafka outage
- Verify clinical response still returns 2xx during downstream degradation

**Wave 19 phase:** 19D (resilience verification)

---

### G-11 — Alert Routing Not Configured (MEDIUM)

**Current state:** No Alertmanager configuration. No integration with PagerDuty, Opsgenie, or equivalent.

**Impact:** Alerts fire in Prometheus but nobody is notified.

**Required artifacts:**
- `tools/ops/observability/alertmanager/alertmanager.yml`
- Routing rules (critical → page, warning → ticket)
- Silence/snooze procedures

**Wave 19 phase:** 19C (alerting)

---

### G-12 — Alert-Specific Runbooks Missing (MEDIUM)

**Current state:** 3 generic runbooks exist:
1. `docs/resilience-ops-platform/runbooks/incident-response.md`
2. `docs/resilience-ops-platform/runbooks/replay-failures.md`
3. `docs/resilience-ops-platform/runbooks/restore-drill.md`

**Impact:** No runbooks for specific alert conditions (high burn rate, latency breach, cert expiry, Kafka lag).

**Required artifacts (per Wave 19 spec):**
- `docs/runbooks/high-burn-rate.md`
- `docs/runbooks/latency-breach.md`
- `docs/runbooks/outbox-lag.md`
- `docs/runbooks/service-unhealthy.md`
- `docs/runbooks/cert-expiry.md`
- `docs/runbooks/kafka-lag.md`

**Wave 19 phase:** 19C (alerting + runbooks)

---

### G-14 — TUSO Prometheus Plane Label Incorrect (LOW)

**Current state:** In `prometheus.yml`, TUSO is labeled `plane: clinical`. TUSO is a terminology service that is part of the Registry Spine (Wave 2), not Clinical Execution.

**Impact:** Dashboard filtering by plane may misclassify TUSO.

**Required fix:** Change `plane: clinical` to `plane: registry` for the `tuso` scrape job.

**Wave 19 phase:** 19B (implementation)

---

## Shared Assets Present (No Gap)

| Asset | Path | Status |
|-------|------|--------|
| ops-instrumentation library | `libs/ops-instrumentation/` | ✅ Code complete, tests exist |
| security-baseline library | `libs/security-baseline/` | ✅ Code complete, tests exist |
| Prometheus scrape config | `tools/ops/observability/prometheus/prometheus.yml` | ✅ All Ring 0 targets |
| OTel Collector config | `tools/ops/observability/otel/otel-collector-config.yaml` | ✅ OTLP → Jaeger/Prom |
| Grafana provisioning | `tools/ops/observability/grafana/provisioning/` | ✅ Datasource configured |
| Observability docker-compose | `tools/ops/docker-compose.ops.yml` | ✅ Full stack |
| Incident response runbook | `docs/resilience-ops-platform/runbooks/incident-response.md` | ✅ |
| Restore drill runbook | `docs/resilience-ops-platform/runbooks/restore-drill.md` | ✅ |

---

## Wave 19 Phase Breakdown (Recommended)

| Phase | Scope | Gaps Addressed |
|-------|-------|---------------|
| **19A** (this document) | Discovery + baseline inventory | — |
| **19B** | Fix service configuration gaps | G-01, G-02, G-03, G-04, G-14 |
| **19C** | SLO definitions, alerting rules, runbooks | G-05, G-06, G-11, G-12 |
| **19D** | Load testing, security posture, resilience verification | G-07, G-08, G-09, G-10 |
| **19E** | Sign-off checklist + production readiness report | All |

---

## Definition of Done — Wave 19A

- [x] All Ring 0 services identified explicitly
- [x] Health endpoint presence recorded per service
- [x] Metrics endpoint presence recorded per service
- [x] Logging/correlation support evidence recorded per service
- [x] Outbox/event lag observability evidence recorded per service
- [x] Acceptance pack presence recorded
- [x] Known blockers to production-readiness identified
- [x] Shared ops assets documented
- [x] Missing artifacts for Wave 19 completion catalogued
- [x] Gap register created with severity, category, and phase assignment
