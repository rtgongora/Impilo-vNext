# Wave 19 — Production Readiness Gate

> Status: Not Started | Date: 2026-03-14

## Goal

Prove the platform meets the "Production-Ready" definition in the Manifest. Every Ring 0 service must have defined SLOs, error budgets, load baselines, and security posture validation before the platform is cleared for national rollout.

## Prerequisites

| Wave | Dependency |
|------|-----------|
| 13 | ops-instrumentation (golden signal metrics, health endpoints, structured logging) |
| 14 | security-baseline (input sanitization, rate limiting, admin audit, secrets management) |
| 18 | Chaos & resilience framework (fault injection, circuit breaker validation) |

## Deliverables

### 1. SLOs/SLIs per Ring 0 Service

Define and instrument Service Level Objectives and Indicators for each Ring 0 service.

| Service | SLI: Availability | SLI: Latency (p95) | SLI: Latency (p99) | SLI: Freshness |
|---------|-------------------|---------------------|---------------------|----------------|
| TSHEPO (8081) | % of ext_authz calls returning non-5xx | ≤ 50ms | ≤ 150ms | N/A (stateless authz) |
| VITO (8082) | % of MPI lookups returning non-5xx | ≤ 100ms | ≤ 300ms | Patient record staleness ≤ 5s |
| VARAPI (8083) | % of provider/facility queries returning non-5xx | ≤ 80ms | ≤ 250ms | Registry staleness ≤ 30s |
| TUSO (8084) | % of terminology lookups returning non-5xx | ≤ 50ms | ≤ 150ms | Codeset freshness ≤ 24h |
| ZIBO (8085) | % of billing lookups returning non-5xx | ≤ 100ms | ≤ 300ms | Tariff freshness ≤ 24h |

#### SLO Targets (Ring 0)

| Metric | Target | Window |
|--------|--------|--------|
| Availability | ≥ 99.9% | 30-day rolling |
| Latency (p95) | Per service table above | 30-day rolling |
| Latency (p99) | Per service table above | 30-day rolling |
| Error rate | ≤ 0.1% of total requests | 30-day rolling |

#### Implementation

- SLIs derived from `impilo_ops_http_latency` and `impilo_ops_http_errors` (Wave 13 metrics)
- Prometheus recording rules compute SLO burn rates
- Grafana dashboards per service with SLO compliance panels

### 2. Error Budgets + Alerting Rules

#### Error Budget Policy

```
Error budget = 1 - SLO target
Example: 99.9% availability → 0.1% error budget → ~43 minutes/month
```

| Burn Rate | Window | Alert Severity | Action |
|-----------|--------|---------------|--------|
| 14.4× | 1h (5m lookback) | Critical (page) | Immediate incident response |
| 6× | 6h (30m lookback) | Warning (page) | On-call investigates within 30m |
| 3× | 1d (2h lookback) | Warning (ticket) | Engineering investigates within 24h |
| 1× | 3d (6h lookback) | Info (ticket) | Track in weekly review |

#### Prometheus Alerting Rules

```yaml
# alerts/impilo-slo-alerts.yml
groups:
  - name: impilo_slo_burn_rate
    rules:
      - alert: ImpiloHighBurnRate_Critical
        expr: |
          (
            sum(rate(impilo_ops_http_errors[5m])) by (service)
            /
            sum(rate(impilo_ops_http_latency_count[5m])) by (service)
          ) > (14.4 * 0.001)
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "{{ $labels.service }} burning error budget at 14.4× rate"
          runbook: "docs/runbooks/high-burn-rate.md"

      - alert: ImpiloLatencyBudgetBreach_P95
        expr: |
          histogram_quantile(0.95,
            sum(rate(impilo_ops_http_latency_bucket[5m])) by (le, service)
          ) > 0.3
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "{{ $labels.service }} p95 latency exceeds 300ms"
```

### 3. Load/Performance Baselines

#### Baseline Test Plan

| Test | Tool | Target | Duration |
|------|------|--------|----------|
| Steady-state load | k6 / Gatling | 500 RPS across Ring 0 | 30 min |
| Spike test | k6 / Gatling | 2000 RPS burst (10s ramp) | 5 min |
| Soak test | k6 / Gatling | 200 RPS sustained | 4 hours |
| Capacity test | k6 / Gatling | Ramp to failure | Until p99 > 2× baseline |

#### Baseline Metrics to Capture

| Metric | Measurement |
|--------|------------|
| Throughput (RPS) | Sustained max without error rate breach |
| Latency p50/p95/p99 | Per service under steady-state load |
| CPU utilization | Per pod at steady-state |
| Memory utilization | Per pod at steady-state (watch for leaks in soak) |
| GC pause frequency | Per service JVM |
| Outbox lag | Events pending publish under load |
| DB connection pool | Active/idle/waiting connections |
| Kafka producer lag | Messages pending acknowledgment |

#### Load Test Scripts

Location: `scripts/perf/`

```
scripts/perf/
├── k6/
│   ├── steady-state.js       # 500 RPS, 30min
│   ├── spike.js               # 2000 RPS burst
│   ├── soak.js                # 200 RPS, 4h
│   └── capacity.js            # Ramp to failure
├── scenarios/
│   ├── tshepo-authz.json      # ext_authz flow
│   ├── vito-mpi-lookup.json   # Patient lookup
│   ├── varapi-facility.json   # Facility query
│   └── clinical-workflow.json # End-to-end clinical
└── baselines/
    └── README.md              # Baseline results template
```

### 4. Security Posture Checks

#### mTLS Verification

| Check | Method | Pass Criteria |
|-------|--------|--------------|
| Service-to-service mTLS | Attempt plaintext call between pods | Connection refused |
| Certificate rotation | Rotate cert, verify zero-downtime | No failed requests during rotation |
| Certificate expiry monitoring | Prometheus cert_exporter | Alert fires ≥ 30d before expiry |
| Envoy SDS (Secret Discovery Service) | Verify dynamic cert reload | Cert updated without restart |

#### Secrets Rotation Plan

| Secret Type | Rotation Frequency | Method | Verification |
|-------------|-------------------|--------|-------------|
| Database credentials | 90 days | Vault dynamic secrets | Connection test post-rotation |
| Kafka SASL credentials | 90 days | Vault dynamic secrets | Producer/consumer test |
| JWT signing keys | 180 days | Keycloak key rotation | Token validation post-rotation |
| mTLS certificates | 365 days (auto-renew at 30d) | cert-manager | TLS handshake test |
| API keys (external) | 180 days | Manual + audit | Integration test |

#### RBAC/PAM Checks

| Check | Method | Pass Criteria |
|-------|--------|--------------|
| Least-privilege DB access | Audit `GRANT` statements per service | No service has `SUPERUSER` or cross-schema access |
| Keycloak role mapping | Export realm, validate role→permission | No orphan roles, no wildcard permissions |
| Envoy ext_authz bypass | Attempt direct service call bypassing Envoy | Connection refused on service ports from outside mesh |
| Admin audit trail | Trigger admin action, verify outbox event | Event in outbox within 1s |

### 5. "No Care Blocked by Data Plane" Verification

#### Test Scenario

Under sustained load (500 RPS), simulate a data plane degradation and verify clinical workflows continue:

| Degradation | Clinical Impact | Expected Behavior |
|-------------|----------------|-------------------|
| Kafka broker down | Event publishing fails | Outbox buffers events; clinical response still returns 2xx |
| BUTANO (FHIR) slow (5s) | SHR writes delayed | Clinical capture succeeds; SHR write queued via outbox |
| VITO degraded (50% errors) | MPI lookup fails | Cached patient context used; graceful degradation message |
| Redis down | Cache miss | Fallback to DB; latency increase but no 5xx |
| PostgreSQL replica lag > 10s | Stale reads | Staleness header set; no incorrect data served |

## Deliverable: Production Readiness Report

### Sign-Off Checklist

```markdown
# Production Readiness Sign-Off — Impilo vNext

## Service SLOs
- [ ] All Ring 0 services have defined SLOs (availability, latency, freshness)
- [ ] SLO recording rules deployed to Prometheus
- [ ] SLO dashboards live in Grafana
- [ ] Error budget policy reviewed and approved by platform team

## Alerting
- [ ] Burn rate alerts configured (critical/warning/info tiers)
- [ ] Alert routing to on-call confirmed (PagerDuty/Opsgenie)
- [ ] Alert runbooks written for each alert rule
- [ ] Alert silence/snooze procedures documented

## Performance
- [ ] Baseline load test completed (steady-state 500 RPS)
- [ ] Spike test completed (2000 RPS burst)
- [ ] Soak test completed (200 RPS, 4h)
- [ ] Capacity ceiling identified per service
- [ ] Baseline results archived in `scripts/perf/baselines/`

## Security
- [ ] mTLS enforced between all services
- [ ] Secrets rotation plan approved
- [ ] RBAC audit completed — no excessive permissions
- [ ] Envoy bypass test passed
- [ ] Admin audit trail verified

## Clinical Safety
- [ ] "No care blocked by data plane" test passed under load
- [ ] Graceful degradation verified for each dependency failure mode
- [ ] Outbox buffer holds ≥ 1h of events without data loss

## Sign-Off
- [ ] Platform Engineering Lead: _________________ Date: _______
- [ ] Security Lead: _________________ Date: _______
- [ ] Clinical Safety Officer: _________________ Date: _______
- [ ] Operations Lead: _________________ Date: _______
```

### Runbook Index

| Runbook | Trigger | Location |
|---------|---------|----------|
| High burn rate | `ImpiloHighBurnRate_Critical` | `docs/runbooks/high-burn-rate.md` |
| Latency breach | `ImpiloLatencyBudgetBreach_P95` | `docs/runbooks/latency-breach.md` |
| Outbox lag | `ImpiloOutboxLagHigh` | `docs/runbooks/outbox-lag.md` |
| Service unhealthy | `ImpiloServiceUnhealthy` | `docs/runbooks/service-unhealthy.md` |
| Certificate expiry | `ImpiloCertExpiringSoon` | `docs/runbooks/cert-expiry.md` |
| Kafka partition lag | `ImpiloKafkaConsumerLag` | `docs/runbooks/kafka-lag.md` |

## Exit Criteria

- [ ] All Ring 0 services have SLO recording rules in Prometheus
- [ ] Error budget burn rate alerts fire correctly in staging
- [ ] Load test baseline results archived with p50/p95/p99 per service
- [ ] mTLS bypass test fails (connection refused)
- [ ] Secrets rotation procedure executed at least once in staging
- [ ] "No care blocked" scenario passes under 500 RPS sustained load
- [ ] Sign-off checklist completed by all required leads
