# Ring 0 — Error Budgets and Alerting

> Date: 2026-03-15
> Scope: Ring 0 services (TSHEPO, VITO, VARAPI, TUSO, ZIBO)
> Wave: 19B
> Branch: `claude/review-project-manifest-jb5O0`
> Companion: [Ring 0 SLI/SLO Specification](ring0-slo-sli-spec.md)

---

## 1. Error Budget Model

### 1.1 Budget Calculation

Error budget is the inverse of the SLO target, expressed as a fraction of the measurement window.

```
Error Budget = (1 - SLO_target) × window_duration
```

| Service | SLO Target | Error Budget (fraction) | Budget per 30-day window |
|---------|:----------:|:-----------------------:|:------------------------:|
| TSHEPO | 99.95% | 0.0005 | 21.6 min (1,296 s) |
| VITO | 99.9% | 0.001 | 43.2 min (2,592 s) |
| VARAPI | 99.9% | 0.001 | 43.2 min (2,592 s) |
| TUSO | 99.9% | 0.001 | 43.2 min (2,592 s) |
| ZIBO | 99.9% | 0.001 | 43.2 min (2,592 s) |

### 1.2 Budget Consumption Rate (Burn Rate)

Burn rate quantifies how fast the error budget is being consumed relative to steady-state.

```
burn_rate = (observed_error_rate) / (1 - SLO_target)
```

- **Burn rate = 1.0**: Budget will be fully consumed exactly at window end (30 days).
- **Burn rate = 14.4**: Budget will be consumed in ~50 hours. Requires immediate action.
- **Burn rate = 0**: Zero errors observed. Budget is intact.

### 1.3 Multi-Window Burn Rate Strategy

Following Google SRE multi-window, multi-burn-rate alerting:

| Tier | Long Window | Short Window | Burn Rate | Time to Budget Exhaustion | Alert Severity | Response Expectation |
|:----:|:-----------:|:------------:|:---------:|:-------------------------:|:--------------:|:--------------------:|
| 1 | 1 h | 5 m | 14.4× | ~2 h (99.9% SLO) | **CRITICAL** | Page on-call immediately. Acknowledge within 5 min. Mitigate within 15 min. |
| 2 | 6 h | 30 m | 6× | ~5 h (99.9% SLO) | **WARNING** (page) | Page on-call. Investigate within 30 min. |
| 3 | 1 d | 2 h | 3× | ~10 h (99.9% SLO) | **WARNING** (ticket) | Create incident ticket. Investigate within 4 h. |
| 4 | 3 d | 6 h | 1× | 30 d (steady burn) | **INFO** (ticket) | Track in weekly SLO review. No immediate action. |

**Why multi-window?** The short window prevents stale alerts (the problem must be ongoing). The long window prevents noise from brief spikes.

---

## 2. Error Budget Policies

### 2.1 Budget Breach Consequences

When a Ring 0 service exhausts its error budget within a 30-day window:

| Remaining Budget | Policy |
|:----------------:|--------|
| > 50% | Normal operations. Feature development proceeds. |
| 25–50% | Caution. Prioritize reliability work. No risky deployments without rollback plan. |
| 5–25% | Freeze non-critical deployments to the affected service. Engineering focus shifts to reliability. |
| 0–5% | Full deployment freeze for the affected service. Only reliability fixes and rollbacks permitted. |
| Exhausted (0%) | Post-incident review required. Service owner presents remediation plan to SRE lead before deployments resume. |

### 2.2 Budget Reset

- Error budgets reset on a rolling 30-day window (not calendar month).
- There is no manual reset mechanism. The budget recovers naturally as old errors fall outside the window.

### 2.3 Exemptions

The following events are excluded from error budget consumption:
- **Planned maintenance** annotated in Prometheus at least 24 h in advance.
- **Dependency failures** where the root cause is an external system (Keycloak, PostgreSQL, Kafka infrastructure) — but only if the service's own retry/circuit-breaker behavior is functioning correctly.
- **Load-test traffic** tagged with a dedicated header (`X-Synthetic: true`) and excluded via metric relabeling.

---

## 3. Alert Rules Specification

All alert rules are implemented in `tools/ops/prometheus/rules/ring0-alerts.yml`.

### 3.1 Availability — Burn Rate Alerts

#### Tier 1: Critical (14.4× burn, 1h/5m windows)

| Alert Name | Condition | For | Severity | Services |
|------------|-----------|-----|----------|----------|
| `Ring0AvailabilityBurnCritical` | `impilo:ring0:availability:ratio_rate5m < (1 - 14.4 * 0.001)` AND `impilo:ring0:availability:ratio_rate1h < (1 - 14.4 * 0.001)` | 2 m | critical | VITO, VARAPI, TUSO, ZIBO |
| `TshepoAvailabilityBurnCritical` | Same pattern with `0.0005` budget | 2 m | critical | TSHEPO |

**Operator expectation:** Acknowledge within 5 minutes. Check `/actuator/health` for component status. Check recent deployments. Check Kafka/DB connectivity. If no root cause in 10 minutes, escalate to service owner. Runbook: `docs/runbooks/high-burn-rate.md`.

#### Tier 2: Warning — Page (6× burn, 6h/30m windows)

| Alert Name | Condition | For | Severity |
|------------|-----------|-----|----------|
| `Ring0AvailabilityBurnWarningPage` | 30m and 6h windows both show > 6× burn | 5 m | warning |

**Operator expectation:** Investigate within 30 minutes. Review error logs for the affected service. Check if the issue is transient or sustained. Runbook: `docs/runbooks/high-burn-rate.md`.

#### Tier 3: Warning — Ticket (3× burn, 1d/2h windows)

| Alert Name | Condition | For | Severity |
|------------|-----------|-----|----------|
| `Ring0AvailabilityBurnWarningTicket` | 2h and 1d windows both show > 3× burn | 10 m | warning |

**Operator expectation:** Create JIRA ticket. Investigate within 4 hours during business hours. Review whether a recent deployment or config change correlates.

#### Tier 4: Info (1× burn, 3d/6h windows)

| Alert Name | Condition | For | Severity |
|------------|-----------|-----|----------|
| `Ring0AvailabilityBurnInfo` | 6h and 3d windows both show > 1× burn | 30 m | info |

**Operator expectation:** No immediate action. Flag for weekly SLO review meeting.

### 3.2 Latency Alerts

| Alert Name | Condition | For | Severity | Operator Expectation |
|------------|-----------|-----|----------|---------------------|
| `TshepoLatencyP95High` | p95 > 50 ms (5m rate) | 5 m | warning | Check ext_authz decision time, policy engine, Keycloak token validation latency. |
| `TshepoLatencyP99Critical` | p99 > 150 ms (5m rate) | 3 m | critical | Immediate investigation. TSHEPO latency adds to every request. |
| `VitoLatencyP95High` | p95 > 100 ms (5m rate) | 5 m | warning | Check MPI lookup queries, DB connection pool, Redis cache hit rate. |
| `VitoLatencyP99Critical` | p99 > 300 ms (5m rate) | 3 m | critical | Page on-call. Patient lookups degraded. |
| `VarapiLatencyP95High` | p95 > 80 ms (5m rate) | 5 m | warning | Check provider/facility query performance, index health. |
| `VarapiLatencyP99Critical` | p99 > 250 ms (5m rate) | 3 m | critical | Page on-call. Provider resolution degraded. |
| `TusoLatencyP95High` | p95 > 50 ms (5m rate) | 5 m | warning | Check terminology cache hit rate, codeset table size. |
| `TusoLatencyP99Critical` | p99 > 150 ms (5m rate) | 3 m | critical | Page on-call. Terminology lookups blocking form entry. |
| `ZiboLatencyP95High` | p95 > 100 ms (5m rate) | 5 m | warning | Check tariff lookup queries, billing calculation complexity. |
| `ZiboLatencyP99Critical` | p99 > 300 ms (5m rate) | 3 m | critical | Page on-call. Billing resolution degraded. |

**Runbook for all latency alerts:** `docs/runbooks/latency-breach.md`

### 3.3 Outbox Lag Alerts

| Alert Name | Condition | For | Severity | Operator Expectation |
|------------|-----------|-----|----------|---------------------|
| `Ring0OutboxLagWarning` | `impilo_ops_outbox_lag > 100` | 5 m | warning | Check Kafka broker connectivity. Verify outbox publisher thread is running. Check for stuck transactions. |
| `Ring0OutboxLagCritical` | `impilo_ops_outbox_lag > 500` | 3 m | critical | Kafka likely unreachable or publisher crashed. Check `opsOutboxHealth` indicator. Manual event replay may be needed. |
| `TshepoOutboxLagWarning` | `impilo_ops_outbox_lag{application="tshepo-service"} > 50` | 5 m | warning | TSHEPO has a tighter threshold due to trust-plane criticality. |
| `TshepoOutboxLagCritical` | `impilo_ops_outbox_lag{application="tshepo-service"} > 200` | 3 m | critical | Trust events not propagating. Audit chain may be broken. |

**Runbook for all outbox alerts:** `docs/runbooks/outbox-lag.md`

### 3.4 Service Health Alerts

| Alert Name | Condition | For | Severity | Operator Expectation |
|------------|-----------|-----|----------|---------------------|
| `Ring0ServiceDown` | `up{job=~"tshepo\|vito\|varapi\|tuso\|zibo"} == 0` | 1 m | critical | Service not responding to Prometheus scrape. Check if pod is running, check container logs, check liveness probe. |
| `Ring0ServiceFlapping` | `changes(up{job=~"tshepo\|vito\|varapi\|tuso\|zibo"}[10m]) > 3` | 5 m | warning | Service is repeatedly restarting. Check OOMKilled, liveness probe misconfiguration, startup dependencies. |

**Runbook:** `docs/runbooks/service-unhealthy.md`

### 3.5 Saturation Alerts

| Alert Name | Condition | For | Severity | Operator Expectation |
|------------|-----------|-----|----------|---------------------|
| `Ring0DbPoolSaturation` | `hikaricp_connections_active / hikaricp_connections_max > 0.8` | 5 m | warning | Connection pool nearing exhaustion. Check for long-running queries, connection leaks. Consider pool size increase. |
| `Ring0DbPoolCritical` | `hikaricp_connections_active / hikaricp_connections_max > 0.95` | 2 m | critical | Connection pool nearly exhausted. Requests will start failing. Investigate immediately. |
| `Ring0HeapPressure` | `jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.85` | 10 m | warning | JVM heap consistently above 85%. Check for memory leaks, GC pressure. |
| `Ring0HeapCritical` | `jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.95` | 5 m | critical | OOM imminent. Trigger heap dump and investigate. |

---

## 4. Alert Routing

### 4.1 Routing Matrix

| Severity | Destination | Notification Channel | Escalation |
|----------|-------------|---------------------|------------|
| **critical** | On-call engineer (primary) | PagerDuty / Opsgenie high-urgency | Auto-escalate to secondary on-call after 10 min unacknowledged |
| **warning** (page) | On-call engineer (primary) | PagerDuty / Opsgenie low-urgency | Auto-escalate after 30 min unacknowledged |
| **warning** (ticket) | Engineering team | JIRA ticket auto-creation | Review in daily standup |
| **info** | SRE dashboard | Slack #impilo-slo-alerts | Review in weekly SLO meeting |

### 4.2 Alertmanager Configuration Reference

The following Alertmanager config is required (not yet deployed — see Gap G-11):

```yaml
# tools/ops/observability/alertmanager/alertmanager.yml
global:
  resolve_timeout: 5m

route:
  receiver: default-slack
  group_by: [alertname, application]
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
  routes:
    - match:
        severity: critical
      receiver: pagerduty-critical
      repeat_interval: 5m
      continue: true
    - match:
        severity: warning
      receiver: pagerduty-warning
      repeat_interval: 30m
    - match:
        severity: info
      receiver: slack-info
      repeat_interval: 24h

receivers:
  - name: default-slack
    slack_configs:
      - api_url: "${SLACK_WEBHOOK_URL}"
        channel: "#impilo-slo-alerts"
        title: '{{ .GroupLabels.alertname }}'
        text: '{{ range .Alerts }}{{ .Annotations.summary }}{{ end }}'

  - name: pagerduty-critical
    pagerduty_configs:
      - service_key: "${PAGERDUTY_SERVICE_KEY}"
        severity: critical
        description: '{{ .GroupLabels.alertname }} on {{ .GroupLabels.application }}'

  - name: pagerduty-warning
    pagerduty_configs:
      - service_key: "${PAGERDUTY_SERVICE_KEY}"
        severity: warning
        description: '{{ .GroupLabels.alertname }} on {{ .GroupLabels.application }}'

  - name: slack-info
    slack_configs:
      - api_url: "${SLACK_WEBHOOK_URL}"
        channel: "#impilo-slo-alerts"
        title: '[INFO] {{ .GroupLabels.alertname }}'
        text: '{{ range .Alerts }}{{ .Annotations.summary }}{{ end }}'

inhibit_rules:
  - source_match:
      severity: critical
    target_match:
      severity: warning
    equal: [alertname, application]
```

> **Note:** This Alertmanager configuration requires environment variables `SLACK_WEBHOOK_URL` and `PAGERDUTY_SERVICE_KEY` to be set at deployment time. These are placeholders for the actual integration credentials that must be provisioned by the ops team.

### 4.3 Silence and Inhibition Rules

| Rule | Purpose |
|------|---------|
| Critical inhibits warning for same alert+service | Prevent alert noise when a critical alert already covers the same issue. |
| Planned maintenance silence | Operator creates silence via Alertmanager UI before maintenance. Must specify affected services and duration. |
| Dependency-failure silence | If Kafka or PostgreSQL infrastructure alerts fire, downstream service alerts may be silenced after operator confirmation. Never auto-silence. |

---

## 5. Alert-to-Runbook Mapping

| Alert Name | Runbook |
|------------|---------|
| `Ring0AvailabilityBurnCritical` | `docs/runbooks/high-burn-rate.md` |
| `TshepoAvailabilityBurnCritical` | `docs/runbooks/high-burn-rate.md` |
| `Ring0AvailabilityBurnWarningPage` | `docs/runbooks/high-burn-rate.md` |
| `Ring0AvailabilityBurnWarningTicket` | `docs/runbooks/high-burn-rate.md` |
| `*LatencyP95High` | `docs/runbooks/latency-breach.md` |
| `*LatencyP99Critical` | `docs/runbooks/latency-breach.md` |
| `Ring0OutboxLagWarning` | `docs/runbooks/outbox-lag.md` |
| `Ring0OutboxLagCritical` | `docs/runbooks/outbox-lag.md` |
| `Ring0ServiceDown` | `docs/runbooks/service-unhealthy.md` |
| `Ring0ServiceFlapping` | `docs/runbooks/service-unhealthy.md` |
| `Ring0DbPoolSaturation` | `docs/runbooks/service-unhealthy.md` |
| `Ring0DbPoolCritical` | `docs/runbooks/service-unhealthy.md` |
| `Ring0HeapPressure` | `docs/runbooks/service-unhealthy.md` |
| `Ring0HeapCritical` | `docs/runbooks/service-unhealthy.md` |

---

## 6. Dashboard Requirements

A Grafana dashboard (`tools/ops/grafana/dashboards/ring0-golden-signals.json`) provides visual SLO compliance tracking with the following panels:

| Panel | Type | Purpose |
|-------|------|---------|
| SLO Compliance Gauge (per service) | Gauge | Shows current 30-day availability vs target. Red/yellow/green thresholds. |
| Error Budget Remaining (per service) | Stat | Shows remaining error budget in minutes. |
| Burn Rate (current) | Time series | Shows burn rate over time for each Ring 0 service. |
| Latency Percentiles (p50/p95/p99) | Time series | Latency breakdown per service with SLO threshold lines. |
| Request Rate (RPS) | Time series | Traffic volume per service. |
| Error Rate (5xx %) | Time series | Error rate percentage per service. |
| Outbox Lag | Time series | Outbox event lag per service with threshold line. |
| DB Connection Pool | Time series | HikariCP utilization per service. |
| JVM Heap Usage | Time series | Heap usage percentage per service. |
| Active Alerts | Alert list | Currently firing Ring 0 alerts. |

Full dashboard JSON is in `tools/ops/grafana/dashboards/ring0-golden-signals.json`.

---

## 7. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-03-15 | Wave 19B | Initial error budget model and alerting specification |
