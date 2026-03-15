# Wave 19B — SLO/Alerting Acceptance Pack

> Date: 2026-03-15
> Scope: Ring 0 SLO/SLI definitions, error budgets, alerting rules, and dashboards
> Wave: 19B
> Branch: `claude/review-project-manifest-jb5O0`

---

## 1. Deliverable Inventory

| # | Deliverable | Path | Status |
|---|------------|------|:------:|
| 1 | Ring 0 SLI/SLO Specification | `docs/production-readiness/ring0-slo-sli-spec.md` | DELIVERED |
| 2 | Error Budgets & Alerting | `docs/production-readiness/error-budgets-and-alerting.md` | DELIVERED |
| 3 | Prometheus Alert Rules | `tools/ops/prometheus/rules/ring0-alerts.yml` | DELIVERED |
| 4 | Grafana Dashboard (Golden Signals) | `tools/ops/grafana/dashboards/ring0-golden-signals.json` | DELIVERED |
| 5 | Acceptance Pack (this document) | `docs/acceptance/wave19b-slo-alerting-acceptance-pack.md` | DELIVERED |

---

## 2. Gaps Addressed by Wave 19B

| Gap ID | Description | Resolution |
|--------|-------------|------------|
| G-05 | No SLO recording rules defined in Prometheus | Recording rules for availability (7 windows), latency (p50/p95/p99), request rate, and saturation defined in `ring0-alerts.yml` |
| G-06 | No error budget burn rate alerts defined | 4-tier multi-window burn rate alerts implemented (14.4×/6×/3×/1×) with per-service thresholds |
| G-11 | Alert routing not configured | Alertmanager configuration template provided in `error-budgets-and-alerting.md` section 4.2 with PagerDuty/Slack routing |
| G-12 | Alert-specific runbooks missing | Alert-to-runbook mapping defined; runbook paths specified in all alert annotations |
| G-13 | Grafana dashboards not verified as provisioned | New Ring 0 dashboard created as importable JSON with 11 panels covering all golden signals |

---

## 3. Acceptance Criteria

### 3.1 SLI/SLO Specification (ring0-slo-sli-spec.md)

| # | Criterion | Evidence | Pass |
|---|-----------|----------|:----:|
| 1 | Availability SLI defined for all 5 Ring 0 services | Sections 3.1–3.5: PromQL queries using `http_server_requests_seconds_count` with `status=~"5.."` | YES |
| 2 | Latency SLI defined at p50, p95, p99 for all 5 services | Sections 3.1–3.5: `histogram_quantile()` queries per service | YES |
| 3 | Freshness/staleness SLI defined where relevant | VITO (5s), VARAPI (30s), TUSO (24h), ZIBO (24h); TSHEPO N/A (stateless) | YES |
| 4 | Outbox lag SLI defined for all services with event outbox | All 5 services: `impilo_ops_outbox_lag` thresholds specified | YES |
| 5 | SLO targets are concrete numbers, not placeholders | 99.95% (TSHEPO), 99.9% (others); latency thresholds in ms per service | YES |
| 6 | TSHEPO sub-services covered | Section 3.6: 6 sub-services with SLO targets and instrumentation caveats | YES |
| 7 | Recording rules defined for efficient SLO computation | Section 4: 7 availability windows, 3 latency percentiles, request rate, saturation | YES |
| 8 | Measurement assumptions documented | Section 6: metric availability, label consistency, freshness proxy, error classification, exclusion windows | YES |

### 3.2 Error Budgets & Alerting (error-budgets-and-alerting.md)

| # | Criterion | Evidence | Pass |
|---|-----------|----------|:----:|
| 1 | Error budget calculation formula and per-service budgets | Section 1.1: formula + table (TSHEPO: 21.6 min, others: 43.2 min per 30d) | YES |
| 2 | Burn rate definition and multi-window strategy | Section 1.2–1.3: 4 tiers with long/short windows, burn rates, severity, response expectations | YES |
| 3 | Budget breach consequences policy | Section 2.1: graduated response from >50% to exhausted | YES |
| 4 | Exemption policy for planned maintenance and dependencies | Section 2.3: maintenance, dependency failures, load-test traffic | YES |
| 5 | Availability burn-rate alerts at 4 tiers | Section 3.1: Tiers 1–4 with separate TSHEPO rules (tighter budget) | YES |
| 6 | Latency alerts per service with correct thresholds | Section 3.2: 10 alert rules (p95 warning + p99 critical per service) | YES |
| 7 | Outbox lag alerts with TSHEPO-specific thresholds | Section 3.3: 4 alert rules (TSHEPO: 50/200, others: 100/500) | YES |
| 8 | Service health alerts (down, flapping) | Section 3.4: Ring0ServiceDown (1m critical), Ring0ServiceFlapping (warning) | YES |
| 9 | Saturation alerts (DB pool, heap) | Section 3.5: 4 alert rules (pool 80%/95%, heap 85%/95%) | YES |
| 10 | Alert routing matrix | Section 4.1: critical→PagerDuty, warning→PagerDuty low, ticket→JIRA, info→Slack | YES |
| 11 | Alert-to-runbook mapping complete | Section 5: all alert names mapped to runbook paths | YES |
| 12 | Alertmanager configuration provided | Section 4.2: full YAML config with PagerDuty, Slack, inhibition rules | YES |

### 3.3 Prometheus Alert Rules (ring0-alerts.yml)

| # | Criterion | Evidence | Pass |
|---|-----------|----------|:----:|
| 1 | Valid Prometheus rule file syntax | YAML with `groups` containing `rules` arrays; `record:` for recording rules, `alert:` for alerts | YES |
| 2 | Recording rules for all SLI windows | Group `impilo_ring0_slo_recording`: 7 error ratio windows, 3 latency percentiles, request rate, 2 saturation metrics | YES |
| 3 | Burn-rate alerts implement multi-window correctly | Group `impilo_ring0_availability_alerts`: AND of short+long window per tier | YES |
| 4 | TSHEPO has tighter thresholds (99.95% budget = 0.0005) | `TshepoAvailabilityBurnCritical` uses `0.0005` vs `0.001` for other services | YES |
| 5 | All alerts have `severity`, `plane` labels | Every alert rule includes `severity: critical|warning|info` and `plane: trust|ring0|registry|clinical` | YES |
| 6 | All alerts have `summary`, `description`, `runbook_url` annotations | Every alert rule has all three annotations populated with meaningful text | YES |
| 7 | Latency alerts match per-service SLO thresholds | TSHEPO: 50ms/150ms, VITO: 100ms/300ms, VARAPI: 80ms/250ms, TUSO: 50ms/150ms, ZIBO: 100ms/300ms | YES |
| 8 | No placeholder values | All thresholds, labels, and annotations are concrete | YES |

### 3.4 Grafana Dashboard (ring0-golden-signals.json)

| # | Criterion | Evidence | Pass |
|---|-----------|----------|:----:|
| 1 | Valid Grafana dashboard JSON (importable) | Standard Grafana dashboard export format with `__inputs`, `__requires`, `panels`, `templating` | YES |
| 2 | Datasource templated (not hardcoded) | `${datasource}` template variable with Prometheus type | YES |
| 3 | Service selector variable | `$service` multi-select variable populated from `http_server_requests_seconds_count` label values | YES |
| 4 | SLO compliance gauge panel | Panel 1: availability gauge with red/yellow/green thresholds | YES |
| 5 | Error budget remaining panel | Panel 2: stat panel showing remaining budget in minutes | YES |
| 6 | Burn rate time series | Panel 3: burn rate over time with 6× and 14.4× threshold lines | YES |
| 7 | Latency percentiles panel | Panel 4: p50/p95/p99 using recording rules | YES |
| 8 | Request rate panel | Panel 5: RPS using recording rule | YES |
| 9 | Error rate panel | Panel 6: 5xx percentage with 0.1%/1% thresholds | YES |
| 10 | Outbox lag panel | Panel 7: lag count with 100/500 threshold lines | YES |
| 11 | DB connection pool panel | Panel 8: HikariCP utilization with 80%/95% thresholds | YES |
| 12 | JVM heap usage panel | Panel 9: heap utilization with 85%/95% thresholds | YES |
| 13 | Active alerts panel | Panel 10: alert list filtered to Ring 0 alerts | YES |
| 14 | SLO summary table | Panel 11: table combining availability, latency, outbox metrics | YES |

---

## 4. Assumptions and Known Limitations

| # | Assumption / Limitation | Impact | Mitigation |
|---|------------------------|--------|------------|
| 1 | ZIBO lacks `impilo.ops` configuration (G-01) | `impilo_ops_outbox_lag` and `impilo_ops_http_*` metrics unavailable for ZIBO | Alert rules use `http_server_requests_seconds_*` (Actuator) which is available. Outbox lag alerts will not fire for ZIBO until G-01 is resolved. |
| 2 | VARAPI lacks `metrics.tags.application` (G-03) | PromQL queries filtering on `application="varapi-service"` may return no data | Prometheus `job` label (`varapi`) can be used as fallback. Recording rules may need adjustment if `application` label is absent. |
| 3 | TSHEPO sub-services lack ops-instrumentation (G-16) | Sub-service SLIs cannot be measured via golden signal metrics | Sub-service SLOs are defined in spec but not alertable until G-16 is resolved. Only the parent `tshepo-service` is covered by alert rules. |
| 4 | TUSO/ZIBO freshness requires domain gauges | `tuso_codeset_last_sync_epoch_seconds` and equivalent ZIBO metric do not exist | Freshness is proxied via outbox lag until domain metrics are instrumented. |
| 5 | Alertmanager not yet deployed (G-11) | Alert rules will fire in Prometheus but notifications will not be routed | Alertmanager config template provided. Ops team must deploy and configure credentials. |
| 6 | Dashboard uses recording rules from `ring0-alerts.yml` | Dashboard will show "No data" until Prometheus loads the rules file | Prometheus config must include `ring0-alerts.yml` in its `rule_files` section. |

---

## 5. Prometheus Configuration Required

To activate the alert rules and recording rules, add the following to `tools/ops/observability/prometheus/prometheus.yml`:

```yaml
rule_files:
  - "/etc/prometheus/rules/ring0-alerts.yml"
```

And mount the rules file into the Prometheus container via `docker-compose.ops.yml` or Kubernetes ConfigMap.

---

## 6. Verification Checklist

| # | Step | Command / Action | Expected Result |
|---|------|------------------|-----------------|
| 1 | Validate Prometheus rules syntax | `promtool check rules tools/ops/prometheus/rules/ring0-alerts.yml` | `SUCCESS: X rules found` |
| 2 | Validate Grafana dashboard JSON | `python3 -m json.tool tools/ops/grafana/dashboards/ring0-golden-signals.json > /dev/null` | Exit code 0 |
| 3 | Import dashboard into Grafana | Grafana UI → Dashboards → Import → Upload JSON | Dashboard renders with template variables |
| 4 | Verify recording rules produce data | PromQL: `impilo:ring0:errors:ratio_rate5m` | Returns values for all Ring 0 services |
| 5 | Verify burn-rate alerts are loaded | Prometheus UI → Alerts → search "Ring0" | All alert rules visible in "inactive" state |
| 6 | Simulate 5xx spike | Generate 5xx responses on a Ring 0 service | `Ring0AvailabilityBurnCritical` fires within 2 minutes |

---

## 7. Definition of Done — Wave 19B (SLO/Alerting)

- [x] SLI definitions for availability, latency, freshness, and outbox lag per Ring 0 service
- [x] SLO targets with concrete numeric thresholds (no placeholders)
- [x] Error budget model with burn rate tiers and breach policies
- [x] Prometheus recording rules for efficient SLI computation (7 availability windows, 3 latency percentiles)
- [x] Multi-window burn-rate alert rules (4 tiers: critical/warning-page/warning-ticket/info)
- [x] Per-service latency alert rules with SLO-derived thresholds
- [x] Outbox lag alert rules with TSHEPO-specific tighter thresholds
- [x] Service health alerts (down, flapping)
- [x] Saturation alerts (DB connection pool, JVM heap)
- [x] All alerts have severity labels, plane labels, runbook URLs, and actionable descriptions
- [x] Alert routing matrix and Alertmanager configuration template
- [x] Grafana dashboard JSON with 11 panels covering all golden signals and SLO compliance
- [x] Dashboard uses template variables for datasource and service filtering
- [x] Assumptions and limitations documented explicitly
- [x] No placeholder values in any deliverable
- [x] TSHEPO sub-service SLOs defined (pending instrumentation for measurement)

---

## 8. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-03-15 | Wave 19B | Initial acceptance pack |
