# Ring 0 — SLI/SLO Specification

> Date: 2026-03-15
> Scope: Ring 0 services (TSHEPO, VITO, VARAPI, TUSO, ZIBO), extended Ring 0 (MSIKA, BUTANO, MUSHEX), and TSHEPO sub-services
> Wave: 19B
> Branch: `claude/review-project-manifest-jb5O0`
> Prerequisite: [Wave 19A Baseline Inventory](wave19a-baseline-inventory.md), [Wave 19A Gap Register](wave19a-gap-register.md)

---

## 1. Definitions

| Term | Definition |
|------|-----------|
| **SLI** (Service Level Indicator) | A quantitative measure of a specific aspect of service quality. |
| **SLO** (Service Level Objective) | A target value or range for an SLI, measured over a rolling 30-day window. |
| **Error Budget** | The permitted amount of unreliability: `1 - SLO target`. A 99.9% SLO yields a 0.1% error budget (approximately 43 minutes/month). |
| **Burn Rate** | The rate at which the error budget is being consumed relative to steady-state. A burn rate of 1.0 means the budget will be exhausted exactly at window end. |
| **Ring 0** | Services whose failure directly blocks clinical care delivery. These receive the strictest SLOs. |

---

## 2. Measurement Window and Data Sources

| Parameter | Value |
|-----------|-------|
| SLO compliance window | 30-day rolling |
| Prometheus scrape interval | 15 s |
| Prometheus evaluation interval | 15 s |
| Availability SLI source metric | `http_server_requests_seconds_count` (label `status`) |
| Availability SLI (golden signals) | `impilo_ops_http_errors` / `impilo_ops_http_latency_count` |
| Latency SLI source metric | `http_server_requests_seconds_bucket` (histogram) |
| Latency SLI (golden signals) | `impilo_ops_http_latency_bucket` (histogram) |
| Outbox lag SLI source metric | `impilo_ops_outbox_lag` (gauge) |
| Freshness SLI source metric | Custom per-service gauge (defined below) |

---

## 3. Ring 0 Service SLI/SLO Matrix

### 3.1 TSHEPO (Trust & Governance Plane — Authorization Gateway)

**Role:** Every inbound request flows through TSHEPO ext_authz. It is the single highest-criticality service.

| SLI Category | SLI Definition | Metric / Query | SLO Target |
|-------------|----------------|----------------|------------|
| **Availability** | Proportion of non-5xx responses to total responses | `1 - (sum(rate(http_server_requests_seconds_count{application="tshepo-service", status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count{application="tshepo-service"}[5m])))` | >= 99.95% |
| **Latency (p50)** | Median response time for ext_authz decisions | `histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket{application="tshepo-service"}[5m])) by (le))` | <= 10 ms |
| **Latency (p95)** | 95th percentile response time | `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application="tshepo-service"}[5m])) by (le))` | <= 50 ms |
| **Latency (p99)** | 99th percentile response time | `histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{application="tshepo-service"}[5m])) by (le))` | <= 150 ms |
| **Outbox Lag** | Count of unpublished events in `tshepo.event_outbox` | `impilo_ops_outbox_lag{application="tshepo-service"}` | <= 50 |

**Rationale:** TSHEPO sits on the critical path of every API call. A 99.95% availability SLO (26 min/month budget) reflects that any TSHEPO outage is a platform-wide outage. Latency targets are aggressive because ext_authz adds directly to every request's total latency.

---

### 3.2 VITO (Registry Spine — Patient Identity)

**Role:** Master Patient Index. Provides CPID resolution for all downstream clinical and billing operations.

| SLI Category | SLI Definition | Metric / Query | SLO Target |
|-------------|----------------|----------------|------------|
| **Availability** | Proportion of non-5xx responses | `1 - (sum(rate(http_server_requests_seconds_count{application="vito-service", status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count{application="vito-service"}[5m])))` | >= 99.9% |
| **Latency (p50)** | Median MPI lookup time | `histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket{application="vito-service"}[5m])) by (le))` | <= 30 ms |
| **Latency (p95)** | 95th percentile response time | `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application="vito-service"}[5m])) by (le))` | <= 100 ms |
| **Latency (p99)** | 99th percentile response time | `histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{application="vito-service"}[5m])) by (le))` | <= 300 ms |
| **Freshness** | Staleness of patient data after write | Time between successful write and read-back consistency | <= 5 s |
| **Outbox Lag** | Count of unpublished events in `vito.event_outbox` | `impilo_ops_outbox_lag{application="vito-service"}` | <= 100 |

**Freshness measurement:** The freshness SLI for VITO tracks read-after-write consistency. In practice this is measured by the outbox lag gauge — if events remain unpublished for > 5 s, downstream consumers (e.g., SHR sync) see stale data.

**Freshness PromQL (proxy via outbox publish age):**
```promql
# Derived freshness: if outbox lag is 0, freshness is current.
# If outbox lag > 0, staleness = lag_count * avg_publish_interval.
# Alert when staleness exceeds threshold:
impilo_ops_outbox_lag{application="vito-service"} > 20
```

---

### 3.3 VARAPI (Registry Spine — Provider & Facility Registry)

**Role:** Source of truth for healthcare provider and facility data. Referenced by clinical workflows for provider validation.

| SLI Category | SLI Definition | Metric / Query | SLO Target |
|-------------|----------------|----------------|------------|
| **Availability** | Proportion of non-5xx responses | `1 - (sum(rate(http_server_requests_seconds_count{application="varapi-service", status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count{application="varapi-service"}[5m])))` | >= 99.9% |
| **Latency (p50)** | Median provider/facility lookup | `histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket{application="varapi-service"}[5m])) by (le))` | <= 25 ms |
| **Latency (p95)** | 95th percentile response time | `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application="varapi-service"}[5m])) by (le))` | <= 80 ms |
| **Latency (p99)** | 99th percentile response time | `histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{application="varapi-service"}[5m])) by (le))` | <= 250 ms |
| **Freshness** | Provider/facility registry update propagation | `impilo_ops_outbox_lag{application="varapi-service"}` proxy | <= 30 s |
| **Outbox Lag** | Count of unpublished events in `varapi.event_outbox` | `impilo_ops_outbox_lag{application="varapi-service"}` | <= 100 |

**Freshness rationale:** Provider and facility data changes infrequently (registration, accreditation updates). A 30 s freshness SLO is generous because downstream consumers cache this data.

---

### 3.4 TUSO (Registry Spine — Terminology Service)

**Role:** ICD-10, LOINC, SNOMED CT, and national codeset resolution. Referenced by clinical forms and EHR data entry.

| SLI Category | SLI Definition | Metric / Query | SLO Target |
|-------------|----------------|----------------|------------|
| **Availability** | Proportion of non-5xx responses | `1 - (sum(rate(http_server_requests_seconds_count{application="tuso-service", status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count{application="tuso-service"}[5m])))` | >= 99.9% |
| **Latency (p50)** | Median terminology lookup | `histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket{application="tuso-service"}[5m])) by (le))` | <= 15 ms |
| **Latency (p95)** | 95th percentile response time | `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application="tuso-service"}[5m])) by (le))` | <= 50 ms |
| **Latency (p99)** | 99th percentile response time | `histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{application="tuso-service"}[5m])) by (le))` | <= 150 ms |
| **Freshness** | Codeset version currency | Time since last codeset sync completed successfully | <= 24 h |
| **Outbox Lag** | Count of unpublished events in `tuso.event_outbox` | `impilo_ops_outbox_lag{application="tuso-service"}` | <= 100 |

**Freshness rationale:** Terminology codesets (ICD-10, LOINC) update on weekly/monthly cycles from upstream standards bodies. A 24 h freshness SLO ensures that after a codeset update is published, TUSO reflects it within one business day.

**Freshness PromQL:**
```promql
# If TUSO provides a codeset_last_sync_epoch gauge:
(time() - tuso_codeset_last_sync_epoch_seconds) > 86400
```
> **Note:** If `tuso_codeset_last_sync_epoch_seconds` does not yet exist as a metric, it must be instrumented as part of TUSO's domain metrics. Until then, freshness is monitored operationally via the outbox lag gauge and deployment logs.

---

### 3.5 ZIBO (Clinical Execution — Billing & Tariff Engine)

**Role:** Tariff resolution and billing calculations. Referenced during encounter finalization and claims generation.

| SLI Category | SLI Definition | Metric / Query | SLO Target |
|-------------|----------------|----------------|------------|
| **Availability** | Proportion of non-5xx responses | `1 - (sum(rate(http_server_requests_seconds_count{application="zibo-service", status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count{application="zibo-service"}[5m])))` | >= 99.9% |
| **Latency (p50)** | Median tariff lookup | `histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket{application="zibo-service"}[5m])) by (le))` | <= 30 ms |
| **Latency (p95)** | 95th percentile response time | `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application="zibo-service"}[5m])) by (le))` | <= 100 ms |
| **Latency (p99)** | 99th percentile response time | `histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{application="zibo-service"}[5m])) by (le))` | <= 300 ms |
| **Freshness** | Tariff schedule currency | Time since last tariff schedule update applied | <= 24 h |
| **Outbox Lag** | Count of unpublished events in `zibo.event_outbox` | `impilo_ops_outbox_lag{application="zibo-service"}` | <= 100 |

**ZIBO instrumentation caveat (Gap G-01):** As of Wave 19A, ZIBO does not have `impilo.ops` configuration. The `impilo_ops_outbox_lag` and `impilo_ops_http_latency` / `impilo_ops_http_errors` metrics will only become available after G-01 is resolved. Until then, availability and latency SLIs must be measured using standard Spring Boot Actuator metrics (`http_server_requests_seconds_*`).

---

### 3.6 MSIKA (Clinical Execution — Clinical Engine)

**Role:** Core clinical workflow engine. Manages encounters, clinical notes, prescriptions, lab orders, and referrals. Referenced by EHR UI and clinical decision support.

| SLI Category | SLI Definition | Metric / Query | SLO Target |
|-------------|----------------|----------------|------------|
| **Availability** | Proportion of non-5xx responses | `1 - (sum(rate(http_server_requests_seconds_count{application="msika-service", status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count{application="msika-service"}[5m])))` | >= 99.9% |
| **Latency (p50)** | Median clinical operation | `histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket{application="msika-service"}[5m])) by (le))` | <= 40 ms |
| **Latency (p95)** | 95th percentile response time | `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application="msika-service"}[5m])) by (le))` | <= 120 ms |
| **Latency (p99)** | 99th percentile response time | `histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{application="msika-service"}[5m])) by (le))` | <= 350 ms |
| **Outbox Lag** | Count of unpublished events in `msika.event_outbox` | `impilo_ops_outbox_lag{application="msika-service"}` | <= 100 |

**Rationale:** MSIKA handles active clinical encounters. Its latency targets are slightly more relaxed than TUSO (read-heavy terminology lookups) because clinical write operations involve more complex domain logic and transactional boundaries.

---

### 3.7 BUTANO (Clinical Execution — FHIR Shared Health Record)

**Role:** HAPI FHIR-based Shared Health Record. Stores clinical resources (Encounters, Observations, Conditions) keyed by CPID (no PII). Referenced by clinical summaries, continuity of care, and data exchange.

| SLI Category | SLI Definition | Metric / Query | SLO Target |
|-------------|----------------|----------------|------------|
| **Availability** | Proportion of non-5xx responses | `1 - (sum(rate(http_server_requests_seconds_count{application="butano-service", status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count{application="butano-service"}[5m])))` | >= 99.9% |
| **Latency (p50)** | Median FHIR resource read | `histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket{application="butano-service"}[5m])) by (le))` | <= 50 ms |
| **Latency (p95)** | 95th percentile response time | `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application="butano-service"}[5m])) by (le))` | <= 200 ms |
| **Latency (p99)** | 99th percentile response time | `histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{application="butano-service"}[5m])) by (le))` | <= 500 ms |
| **Freshness** | FHIR resource currency after clinical write | Time between MSIKA event publication and BUTANO resource update | <= 30 s |
| **Outbox Lag** | Count of unpublished events in `butano.event_outbox` | `impilo_ops_outbox_lag{application="butano-service"}` | <= 100 |

**Rationale:** BUTANO wraps HAPI FHIR, which has inherently higher latency due to FHIR resource parsing, validation, and search indexing. The 200 ms p95 target accounts for FHIR Bundle operations and `$everything` queries. Freshness tracks how quickly clinical data written via MSIKA appears in the SHR.

**BUTANO instrumentation note:** BUTANO is a HAPI FHIR wrapper. If HAPI's default Micrometer integration does not expose `http_server_requests_seconds_*`, SLIs must be measured via Envoy upstream metrics or a custom filter. Verify metric availability during staging deployment.

---

### 3.8 MUSHEX (Finance — Payer & Claims Engine)

**Role:** Manages payer contracts, claims adjudication, and reimbursement workflows. Referenced during encounter finalization for coverage validation and post-encounter for claims submission.

| SLI Category | SLI Definition | Metric / Query | SLO Target |
|-------------|----------------|----------------|------------|
| **Availability** | Proportion of non-5xx responses | `1 - (sum(rate(http_server_requests_seconds_count{application="mushex-service", status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count{application="mushex-service"}[5m])))` | >= 99.9% |
| **Latency (p50)** | Median claims/payer operation | `histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket{application="mushex-service"}[5m])) by (le))` | <= 40 ms |
| **Latency (p95)** | 95th percentile response time | `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application="mushex-service"}[5m])) by (le))` | <= 150 ms |
| **Latency (p99)** | 99th percentile response time | `histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{application="mushex-service"}[5m])) by (le))` | <= 400 ms |
| **Outbox Lag** | Count of unpublished events in `mushex.event_outbox` | `impilo_ops_outbox_lag{application="mushex-service"}` | <= 100 |

**Rationale:** MUSHEX handles financial workflows that are critical for revenue cycle but are not on the immediate clinical care path. Latency targets are moderate — claims adjudication can involve external payer lookups. The 99.9% availability SLO matches other Ring 0 services; financial operations must not silently fail.

---

### 3.9 TSHEPO Sub-Services

The 6 TSHEPO sub-services form the internal decomposition of the Trust Plane. They share the same SLO tier as TSHEPO itself because they are on the authorization critical path.

**Instrumentation caveat (Gap G-16):** All 6 sub-services currently lack `ops-instrumentation`. The SLIs below are defined against standard Actuator metrics. Once G-16 is resolved, golden-signal metrics (`impilo_ops_http_latency`, `impilo_ops_http_errors`) should be used instead.

| Sub-Service | Port | Availability SLO | Latency p95 SLO | Latency p99 SLO | Outbox Lag SLO |
|-------------|------|:-----------------:|:----------------:|:----------------:|:--------------:|
| tshepo-authz-service | 8081/9090 | >= 99.95% | <= 30 ms | <= 100 ms | <= 50 |
| tshepo-audit-service | 8183 | >= 99.9% | <= 100 ms | <= 300 ms | N/A (consumer) |
| tshepo-consent-service | 8182 | >= 99.9% | <= 50 ms | <= 150 ms | <= 50 |
| tshepo-identity-service | 8181 | >= 99.9% | <= 80 ms | <= 250 ms | <= 50 |
| tshepo-keys-service | 8184 | >= 99.9% | <= 50 ms | <= 150 ms | <= 50 |
| tshepo-offline-service | 8185 | >= 99.9% | <= 100 ms | <= 300 ms | <= 100 |

**Notes:**
- `tshepo-authz-service` gets the same 99.95% SLO as TSHEPO main because it is the ext_authz decision point.
- `tshepo-audit-service` is a Kafka consumer with no outbox; its availability SLI measures consumer health, not HTTP success rate.
- `tshepo-offline-service` has a relaxed outbox lag threshold because offline token generation is batch-oriented.

---

## 4. SLO Recording Rules

The following Prometheus recording rules pre-compute SLI values for efficient SLO compliance queries and dashboard rendering. These rules are implemented in `tools/ops/prometheus/rules/ring0-alerts.yml`.

### 4.1 Availability Recording Rules

```yaml
# Per-service availability ratio over 5m window
- record: impilo:ring0:availability:ratio_rate5m
  expr: |
    1 - (
      sum by (application) (rate(http_server_requests_seconds_count{application=~"tshepo-service|vito-service|varapi-service|tuso-service|zibo-service|msika-service|butano-service|mushex-service", status=~"5.."}[5m]))
      /
      sum by (application) (rate(http_server_requests_seconds_count{application=~"tshepo-service|vito-service|varapi-service|tuso-service|zibo-service|msika-service|butano-service|mushex-service"}[5m]))
    )

# 30m window for burn-rate alerting
- record: impilo:ring0:availability:ratio_rate30m
  expr: |
    1 - (
      sum by (application) (rate(http_server_requests_seconds_count{application=~"tshepo-service|vito-service|varapi-service|tuso-service|zibo-service|msika-service|butano-service|mushex-service", status=~"5.."}[30m]))
      /
      sum by (application) (rate(http_server_requests_seconds_count{application=~"tshepo-service|vito-service|varapi-service|tuso-service|zibo-service|msika-service|butano-service|mushex-service"}[30m]))
    )

# 2h window for slow-burn alerting
- record: impilo:ring0:availability:ratio_rate2h
  expr: |
    1 - (
      sum by (application) (rate(http_server_requests_seconds_count{application=~"tshepo-service|vito-service|varapi-service|tuso-service|zibo-service|msika-service|butano-service|mushex-service", status=~"5.."}[2h]))
      /
      sum by (application) (rate(http_server_requests_seconds_count{application=~"tshepo-service|vito-service|varapi-service|tuso-service|zibo-service|msika-service|butano-service|mushex-service"}[2h]))
    )

# 6h window for slow-burn alerting
- record: impilo:ring0:availability:ratio_rate6h
  expr: |
    1 - (
      sum by (application) (rate(http_server_requests_seconds_count{application=~"tshepo-service|vito-service|varapi-service|tuso-service|zibo-service|msika-service|butano-service|mushex-service", status=~"5.."}[6h]))
      /
      sum by (application) (rate(http_server_requests_seconds_count{application=~"tshepo-service|vito-service|varapi-service|tuso-service|zibo-service|msika-service|butano-service|mushex-service"}[6h]))
    )
```

### 4.2 Latency Recording Rules

```yaml
# p95 latency per service (5m rate)
- record: impilo:ring0:latency:p95_rate5m
  expr: |
    histogram_quantile(0.95,
      sum by (le, application) (rate(http_server_requests_seconds_bucket{application=~"tshepo-service|vito-service|varapi-service|tuso-service|zibo-service|msika-service|butano-service|mushex-service"}[5m]))
    )

# p99 latency per service (5m rate)
- record: impilo:ring0:latency:p99_rate5m
  expr: |
    histogram_quantile(0.99,
      sum by (le, application) (rate(http_server_requests_seconds_bucket{application=~"tshepo-service|vito-service|varapi-service|tuso-service|zibo-service|msika-service|butano-service|mushex-service"}[5m]))
    )
```

### 4.3 Outbox Lag Recording Rule

```yaml
- record: impilo:ring0:outbox_lag:current
  expr: |
    impilo_ops_outbox_lag{application=~"tshepo-service|vito-service|varapi-service|tuso-service|zibo-service|msika-service|butano-service|mushex-service"}
```

---

## 5. SLO Compliance Targets Summary

| Service | Availability SLO | Monthly Error Budget (min) | p95 Latency | p99 Latency | Outbox Lag | Freshness |
|---------|:----------------:|:--------------------------:|:-----------:|:-----------:|:----------:|:---------:|
| TSHEPO | 99.95% | ~22 min | 50 ms | 150 ms | 50 events | N/A |
| VITO | 99.9% | ~43 min | 100 ms | 300 ms | 100 events | 5 s |
| VARAPI | 99.9% | ~43 min | 80 ms | 250 ms | 100 events | 30 s |
| TUSO | 99.9% | ~43 min | 50 ms | 150 ms | 100 events | 24 h |
| ZIBO | 99.9% | ~43 min | 100 ms | 300 ms | 100 events | 24 h |
| MSIKA | 99.9% | ~43 min | 120 ms | 350 ms | 100 events | N/A |
| BUTANO | 99.9% | ~43 min | 200 ms | 500 ms | 100 events | 30 s |
| MUSHEX | 99.9% | ~43 min | 150 ms | 400 ms | 100 events | N/A |

---

## 6. Measurement Assumptions

1. **Metric availability:** All SLI queries assume that Spring Boot Actuator Prometheus endpoint is functional and scraped at 15 s intervals. Services without `impilo.ops` configuration (ZIBO per G-01, all TSHEPO sub-services per G-16) will use `http_server_requests_seconds_*` as the primary SLI source until ops-instrumentation is deployed.

2. **Label consistency:** SLI queries use `application` label. VARAPI currently lacks `management.metrics.tags.application` (G-03); until fixed, use the Prometheus `job` label as a fallback: replace `application="varapi-service"` with `job="varapi"`.

3. **Freshness proxy:** True freshness SLIs for TUSO (codeset sync) and ZIBO (tariff sync) require domain-specific gauges that do not yet exist. Until these are instrumented, freshness is approximated by outbox lag and operational monitoring of deployment/sync job logs.

4. **Error classification:** The availability SLI counts all HTTP 5xx responses as errors. HTTP 4xx responses (client errors) are excluded from the error budget because they represent correct server behavior (input validation, authorization denial).

5. **Exclusion windows:** Planned maintenance windows must be annotated in Prometheus (via recording rule or Grafana annotation) and excluded from SLO compliance calculations. The error budget burn-rate alerts remain active during maintenance to catch unintended impact.

---

## 7. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-03-15 | Wave 19B | Initial SLI/SLO specification for Ring 0 |
| 2026-03-15 | Wave 19D | Added extended Ring 0 services (MSIKA, BUTANO, MUSHEX) |
