# Observability-Driven Backlog Process — Impilo vNext

> Wave 25 Deliverable | Status: Draft | Date: 2026-03-15

## 1. Purpose

Platform telemetry (metrics, logs, traces, alerts) directly drives the engineering backlog. This document defines how observability signals are triaged, prioritized, and converted into actionable work items on a recurring cadence.

## 2. Ownership Model

| Role | Responsibility |
|------|---------------|
| **Observability Lead** | Chairs weekly review; maintains Grafana dashboards; owns alert tuning backlog |
| **Ring 0 On-Call** | Presents Ring 0 golden signal trends at weekly review |
| **Domain Leads** (Trust, Registry, Clinical, Finance, Integration, Data) | Review domain-specific metrics; accept or dispute generated backlog items |
| **SRE / Platform Engineering** | Provide infrastructure-level metrics (K8s, Kafka, PostgreSQL, Redis); execute scaling actions |
| **Product Owner** | Prioritizes observability backlog items against feature work in sprint planning |

## 3. Signal Sources

### 3.1 Metrics (Prometheus + Grafana)

| Signal | Metric Name | Source | Dashboard |
|--------|-------------|--------|-----------|
| Service availability | `up{job="{service}"}` | Prometheus scrape | Platform Overview |
| HTTP latency (p95/p99) | `http_server_requests_seconds{quantile="0.95\|0.99"}` | Spring Boot Actuator / Micrometer | Per-Service Latency |
| HTTP error rate | `rate(http_server_requests_seconds_count{status=~"5.."}[5m])` | Micrometer | Error Budget Tracker |
| Outbox lag | `impilo_{service}_outbox_lag_count` | ops-instrumentation lib | Outbox Health |
| DB connection pool | `hikaricp_connections_active / hikaricp_connections_max` | HikariCP | Database Health |
| JVM heap usage | `jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}` | JVM MXBean | JVM Health |
| Kafka consumer lag | Consumer group lag (Kafka metrics) | Kafka broker | Kafka Health |
| Policy decision rate | `impilo_v11_decisions{decision="DENY"}` | Tech Companion CompanionMetrics | Trust Decisions |
| Idempotency replays | `impilo_v11_idempotency_replays` | Tech Companion CompanionMetrics | Idempotency Health |

### 3.2 Logs (Loki)

| Signal | Log Query Pattern | Threshold |
|--------|-------------------|-----------|
| Error spike | `{service="{name}"} \|= "ERROR" \| rate > baseline` | >2x normal error rate over 15 min |
| Slow query | `{service="{name}"} \|= "slow query" \| duration > 1s` | Any occurrence |
| PII leak attempt | `{service="{name}"} \|~ "(national_id\|phone_number\|patient_name)"` | Any occurrence (P1 security) |
| Outbox retry exhaustion | `{service="{name}"} \|= "outbox retry exhausted"` | Any occurrence |
| Trust header missing | `{service="{name}"} \|= "missing required header"` | >10 per hour |

### 3.3 Traces (OpenTelemetry → Jaeger)

| Signal | Detection Method | Threshold |
|--------|-----------------|-----------|
| Cross-service latency regression | Trace duration p99 by service pair | >20% week-over-week increase |
| Broken trace propagation | Traces with missing parent spans | >1% of traces |
| External call timeout | Spans tagged with external system (MOSIP, eLMIS, DHIS2) | p99 > 5s |

### 3.4 Alerts (Prometheus Alertmanager)

| Signal | Source |
|--------|--------|
| Alert firing count by service | Alertmanager API |
| Alert false positive rate | Manual classification in weekly review |
| Alert fatigue score | (total alerts − actionable alerts) / total alerts |

## 4. Weekly Observability Review

### 4.1 Meeting Structure

| Item | Duration | Owner | Detail |
|------|----------|-------|--------|
| **SLO compliance check** | 10 min | Observability Lead | Review SLO dashboard: which services are within budget, which are burning |
| **Error budget burn rate** | 5 min | Observability Lead | Project when each service will exhaust its 30-day error budget at current burn rate |
| **Top-5 error codes** | 5 min | Ring 0 On-Call | Most frequent error responses by service in the past 7 days |
| **Latency trends** | 5 min | SRE | p95 and p99 trends for Ring 0 and Ring 1 services, week-over-week comparison |
| **Outbox and Kafka health** | 5 min | SRE | Outbox lag, consumer group lag, partition distribution |
| **Alert review** | 5 min | Observability Lead | Alerts fired in past 7 days; classify as actionable vs false positive; queue tuning items |
| **Backlog item generation** | 10 min | All | Convert signals above threshold into prioritized backlog items (see Section 5) |
| **Previous item status** | 5 min | Domain Leads | Update on observability backlog items from prior weeks |

**Schedule**: Every Monday 09:30, 50 minutes.

### 4.2 Meeting Artifacts

| Artifact | Produced By | Consumed By |
|----------|------------|-------------|
| Weekly Observability Snapshot (Grafana screenshot export) | Observability Lead | Meeting attendees, decision log |
| Generated backlog items (ticket IDs) | Meeting consensus | Sprint planning, domain leads |
| Alert tuning queue (alert IDs to tune) | Observability Lead | SRE |
| Decision log entry for each generated item | Observability Lead | Governance audit trail |

## 5. Signal-to-Backlog Conversion Rules

### 5.1 Priority Matrix

| Signal | Threshold | Auto-Generated Priority | Backlog Category | Response |
|--------|-----------|------------------------|------------------|----------|
| SLO breach (30-day window) | Any service breaches availability or latency SLO | **P1** | Reliability | Immediate: engineering drops feature work; fix within current sprint |
| Error budget >50% consumed | Mid-month (day 15) check | **P2** | Reliability | This sprint: investigate root cause; deploy fix before month-end |
| p99 latency regression >20% | Week-over-week comparison | **P2** | Performance | This sprint: profile and optimize; revert if caused by recent change |
| Outbox lag sustained >100 | For >1 hour in the past 7 days | **P2** | Data Integrity | This sprint: investigate publisher, Kafka connectivity, consumer lag |
| HTTP 5xx rate >1% of traffic | Any Ring 0/1 service | **P2** | Reliability | This sprint: error classification, fix or add retry/circuit-breaker |
| New error code not seen in 30 days | First occurrence | **P3** | Investigation | Next sprint: investigate root cause, add to known-error database |
| Alert false positive rate >30% | Monthly calculation | **P3** | Alert Quality | Next sprint: tune alert thresholds, add inhibition rules |
| GC pause increase >50% | Week-over-week comparison | **P3** | Performance | Next sprint: heap analysis, tune GC parameters |
| DB connection pool >80% | Sustained for >30 min | **P3** | Capacity | Next sprint: increase pool size, optimize query patterns, add read replica |
| Kafka consumer lag >1000 | Sustained for >30 min | **P3** | Throughput | Next sprint: add consumer instances, optimize processing |
| Broken trace propagation >1% | Weekly trace analysis | **P4** | Observability | Backlog: fix OTel instrumentation in affected service |
| Idempotency replay rate >5% | Per service, weekly | **P4** | Data Quality | Backlog: investigate duplicate event sources |

### 5.2 Backlog Item Template

```markdown
## OBS-{YYYY}-{WW}-{NN} — {Title}

- **Signal**: {metric name or alert name}
- **Observed Value**: {actual value}
- **Threshold**: {threshold that triggered this item}
- **Affected Service(s)**: {service name(s), port(s), ring}
- **Priority**: P1 / P2 / P3 / P4
- **Category**: Reliability / Performance / Data Integrity / Alert Quality / Capacity / Observability
- **Generated**: {date, weekly review #}
- **Assigned To**: {domain lead or engineer}
- **Sprint Target**: {current / next / backlog}
- **Resolution**: {pending / fixed in {commit/PR} / won't fix — {reason}}
- **Verification**: {metric to check after fix is deployed}
```

## 6. Quarterly Observability Health Report

Generated at the end of each quarter by the Observability Lead, reviewed in the quarterly platform review (see `docs/governance/platform-governance-cadence.md`).

### 6.1 Report Structure

```markdown
# Observability Health Report — Q{N} {YYYY}

## 1. SLO Compliance Summary

| Service | Ring | Availability SLO | Actual | Latency p95 SLO | Actual | Latency p99 SLO | Actual | Status |
|---------|------|-----------------|--------|-----------------|--------|-----------------|--------|--------|
| tshepo-authz | 0 | 99.95% | {%} | 100ms | {ms} | 200ms | {ms} | PASS/FAIL |
| vito-service | 0 | 99.9% | {%} | 250ms | {ms} | 500ms | {ms} | PASS/FAIL |
| tuso-service | 0 | 99.9% | {%} | 150ms | {ms} | 300ms | {ms} | PASS/FAIL |
| pct-service | 1 | 99.9% | {%} | 500ms | {ms} | 1000ms | {ms} | PASS/FAIL |
| oros-service | 1 | 99.9% | {%} | 500ms | {ms} | 1000ms | {ms} | PASS/FAIL |
| pharmacy-service | 1 | 99.9% | {%} | 500ms | {ms} | 1000ms | {ms} | PASS/FAIL |
| mushex-service | 1 | 99.9% | {%} | 500ms | {ms} | 1000ms | {ms} | PASS/FAIL |

## 2. Error Budget Status

| Service | Ring | Budget (30-day) | Consumed | Remaining | Trend |
|---------|------|----------------|----------|-----------|-------|
| tshepo-authz | 0 | 21.6 min | {min} | {min} | {up/flat/down} |
| vito-service | 0 | 43.2 min | {min} | {min} | {up/flat/down} |
| pct-service | 1 | 43.2 min | {min} | {min} | {up/flat/down} |

## 3. Alert Health

| Metric | Q{N} Value | Target | Trend |
|--------|-----------|--------|-------|
| Total alerts fired | {count} | Decreasing | {up/flat/down} |
| Actionable alerts | {count} ({%}) | >70% | {up/flat/down} |
| False positive rate | {%} | <30% | {up/flat/down} |
| Mean time to acknowledge | {min} | <15 min (P1), <60 min (P2) | {up/flat/down} |

## 4. Backlog Impact

| Metric | Q{N} Value |
|--------|-----------|
| Observability backlog items generated | {count} |
| Items resolved this quarter | {count} |
| Items carried over | {count} |
| P1 items generated | {count} |
| Mean resolution time (P1) | {days} |
| Mean resolution time (P2) | {days} |

## 5. Key Findings and Recommendations

1. {finding}
2. {finding}
3. {finding}

## 6. Alert Tuning Actions Taken

| Alert | Change | Result |
|-------|--------|--------|
| {alert name} | {threshold adjusted / inhibition added / removed} | {false positive reduction %} |
```

## 7. Integration with Other Governance Processes

| Process | Integration Point |
|---------|-------------------|
| **Sprint Planning** | Observability backlog items are presented alongside feature work; P1/P2 items take priority over features |
| **CAB / Change Control** | Observability-driven fixes follow the change request process per `docs/rollout/change-control-and-cab.md` (P1 = Emergency CR, P2 = Normal CR) |
| **Incident Post-Mortems** | Incidents that were first detected by observability signals are tagged; missed detections generate alert-improvement backlog items (see `docs/governance/security-patch-and-incident-learning.md`) |
| **Capacity Planning** | Saturation signals feed directly into quarterly capacity review (see `docs/governance/cost-and-capacity-planning.md`) |
| **Schema Governance** | Schema validation failure metrics (`impilo_{service}_schema_validation_errors_total`) are reviewed; sustained failures trigger schema governance escalation |
| **Quarterly Platform Review** | Observability health report is a required input to the quarterly platform review (see `docs/governance/platform-governance-cadence.md`) |

## 8. Rollout Feedback Integration

During active rollout phases (Wave 24), additional observability signals are monitored:

| Signal | Source | Threshold | Action |
|--------|--------|-----------|--------|
| New-site error rate spike | Per-site error rate within 14 days of go-live | >2x platform average | Hypercare escalation + backlog item |
| Training-correlated errors | Error rate by user cohort (Fundo-trained vs not) | Trained users >50% of untrained rate expected | Training content update request |
| Offline-sync failures | offline-sync-service reconciliation metrics | Any reconciliation failure | P2 backlog item + site support escalation |
| Federation sync latency | Cross-pod replication lag | >5 min sustained | P2 backlog item + federation investigation |
| Site-specific SLO variance | Per-site SLO dashboard | Any site >2 standard deviations from fleet mean | Investigation: site-specific issue vs platform issue |
