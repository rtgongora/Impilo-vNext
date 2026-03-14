# Wave 25 — Continuous Improvement Loop

> Status: Not Started | Date: 2026-03-14

## Goal

The platform evolves safely. Regular governance cycles ensure schema integrity, observability drives the backlog, security patches flow through a defined pipeline, incidents produce learning, and costs remain controlled.

**Note**: This wave is ongoing — it defines recurring processes, not a one-time deliverable.

## Prerequisites

| Wave | Dependency |
|------|-----------|
| 24 | National rollout (release trains, CAB, support model operational) |

## Deliverables

### 1. Regular Schema Governance Cycles

#### Schema Governance Board

| Role | Responsibility |
|------|---------------|
| Schema Steward | Chair governance reviews, maintain schema registry |
| Domain Owners | Propose schema changes for their domain |
| Platform Architect | Assess cross-domain impact |
| Data Governance Lead | Ensure PII/CPID separation compliance |

#### Governance Cadence

| Activity | Frequency | Participants |
|----------|-----------|-------------|
| Schema change review | Bi-weekly | Schema Steward + Domain Owners |
| Schema compatibility audit | Monthly | Schema Steward + Platform Architect |
| Event contract review | Monthly | Schema Steward + all service owners |
| Breaking change assessment | Ad-hoc (triggered by proposals) | Full governance board |

#### Schema Change Process

```
1. Domain owner proposes schema change (PR with JSON Schema diff)
2. CI validates backward compatibility (Apicurio / JSON Schema diff tool)
3. Schema Steward reviews for cross-domain impact
4. If backward-compatible → auto-approve after 48h review window
5. If breaking → full governance board review
6. Approved changes versioned in schema registry
7. Deprecation window applied to old version (minimum 6 months)
```

#### Schema Health Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| Schema versions in use | ≤ 2 per event type | Schema registry query |
| Deprecated schemas still in production | 0 after sunset date | Producer/consumer audit |
| Schema validation failures | ≤ 0.01% of events | Kafka consumer metrics |
| Time to approve schema change | ≤ 5 business days | Governance tracker |

### 2. Observability-Driven Backlog

#### Process

```
Weekly Observability Review (30 min)
├── Review SLO compliance (Grafana dashboard)
├── Review error budget consumption
├── Review top-N error codes (by frequency)
├── Review p95/p99 latency trends
├── Review outbox lag trends
├── Review alert fatigue metrics (false positive rate)
└── Output: Prioritized backlog items
```

#### Observability Signals → Backlog Items

| Signal | Threshold | Backlog Priority |
|--------|-----------|-----------------|
| SLO breach | Any 30-day window breach | P1 (immediate) |
| Error budget > 50% consumed | Mid-month check | P2 (this sprint) |
| p99 latency regression > 20% | Week-over-week comparison | P2 (this sprint) |
| Outbox lag sustained > 100 | For > 1 hour | P2 (this sprint) |
| New error code appearing | Not seen in last 30 days | P3 (next sprint) |
| Alert false positive rate > 30% | Monthly review | P3 (tune alerts) |
| GC pause increase > 50% | Week-over-week comparison | P3 (investigate) |

#### Quarterly Observability Health Report

```markdown
# Observability Health Report — Q[N] [YYYY]

## SLO Compliance
| Service | Availability | Latency p95 | Latency p99 | Status |
|---------|-------------|-------------|-------------|--------|
| TSHEPO  | ___% | ___ms | ___ms | ✅/⚠️/❌ |
| VITO    | ___% | ___ms | ___ms | ✅/⚠️/❌ |
| ...     | ... | ... | ... | ... |

## Error Budget
| Service | Budget Remaining | Trend |
|---------|-----------------|-------|
| TSHEPO  | ___% | ↑/→/↓ |
| ...     | ... | ... |

## Key Findings
1. ___
2. ___
3. ___

## Backlog Items Generated: ___
## Backlog Items Resolved: ___
```

### 3. Security Patch Pipeline + Incident Learning Reviews

#### Security Patch Pipeline

```
1. Vulnerability detected (Dependabot / Snyk / manual CVE review)
2. Severity assessment (CVSS score + Impilo context)
3. Patch prioritization:
   - Critical (CVSS ≥ 9.0): Patch within 24 hours
   - High (CVSS 7.0-8.9): Patch within 7 days
   - Medium (CVSS 4.0-6.9): Patch within 30 days
   - Low (CVSS < 4.0): Next scheduled maintenance
4. Patch development + testing
5. Emergency change request (Critical/High) or Normal CR
6. Ring-based deployment (Ring 0 first for critical)
7. Post-patch verification
```

#### Dependency Update Cadence

| Dependency Type | Update Frequency | Automation |
|----------------|-----------------|------------|
| Security patches (critical) | Immediate | Dependabot + auto-merge for minor |
| Security patches (high) | Weekly | Dependabot PR + manual review |
| Spring Boot minor | Monthly | Manual + full regression |
| Spring Boot major | Per release train schedule | Manual + full regression + CAB |
| Java minor (21.x) | Quarterly | Manual + performance baseline |

#### Incident Learning Reviews (Post-Mortems)

| Severity | Review Required | Timeline | Participants |
|----------|----------------|----------|-------------|
| SEV-1 (outage) | Mandatory blameless post-mortem | Within 48 hours | All involved + leadership |
| SEV-2 (degraded) | Written review | Within 5 business days | Service owners involved |
| SEV-3 (minor) | Brief write-up | Within 10 business days | Service owner |
| SEV-4 (cosmetic) | Optional | — | — |

#### Post-Mortem Template

```markdown
# Incident Post-Mortem — INC-[YYYY]-[NNN]

## Summary
- **Date**: ___
- **Duration**: ___
- **Severity**: SEV-1/2/3
- **Services affected**: ___
- **Impact**: ___

## Timeline
| Time | Event |
|------|-------|
| T+0  | ___ |
| T+?m | ___ |

## Root Cause
___

## Contributing Factors
1. ___
2. ___

## What Went Well
1. ___

## What Didn't Go Well
1. ___

## Action Items
| # | Action | Owner | Deadline | Status |
|---|--------|-------|----------|--------|
| 1 | ___ | ___ | ___ | Open |

## Lessons Learned
___
```

### 4. Cost Controls + Capacity Planning

#### Cost Monitoring

| Cost Category | Metric | Review Frequency |
|---------------|--------|-----------------|
| Compute (K8s) | CPU/memory utilization per namespace | Weekly |
| Storage (PostgreSQL) | DB size growth rate per service | Monthly |
| Storage (MinIO) | Object storage growth rate | Monthly |
| Network | Egress traffic volume | Monthly |
| Kafka | Storage per topic, partition count | Monthly |
| Licensing | Keycloak users, connected devices | Quarterly |

#### Capacity Planning Process

```
Quarterly Capacity Review
├── Current utilization per service (CPU, memory, storage, connections)
├── Growth rate extrapolation (linear + seasonal adjustment)
├── Projected capacity needs (next quarter + next year)
├── Bottleneck identification (which resource hits limit first)
├── Scaling recommendations (horizontal vs vertical)
└── Budget request for next quarter
```

#### Scaling Thresholds

| Resource | Warning Threshold | Critical Threshold | Action |
|----------|------------------|-------------------|--------|
| CPU utilization | 70% sustained | 85% sustained | Scale horizontally |
| Memory utilization | 75% sustained | 90% sustained | Scale vertically or add replicas |
| DB storage | 70% of allocated | 85% of allocated | Expand volume |
| DB connections | 70% of pool max | 85% of pool max | Increase pool or add read replicas |
| Kafka partition lag | > 1000 messages | > 10000 messages | Add consumers or increase partitions |

#### Cost Optimization Opportunities

| Opportunity | Method | Review Frequency |
|-------------|--------|-----------------|
| Right-sizing | Compare requested vs actual resource usage | Monthly |
| Idle resource cleanup | Identify unused services/storage | Monthly |
| Storage tiering | Move cold data to cheaper storage | Quarterly |
| Reserved capacity | Commit to reserved instances for steady-state | Annually |
| Query optimization | Identify slow queries via pg_stat_statements | Monthly |

## Deliverable: Ongoing Quarterly Roadmap + Governance Cadence

```markdown
# Quarterly Platform Review — Q[N] [YYYY]

## Governance Activities Completed
- [ ] Schema governance reviews: ___/___  scheduled
- [ ] Observability reviews: ___/___ weekly sessions held
- [ ] Security patch pipeline: ___ patches applied
- [ ] Incident post-mortems: ___/___ completed on time
- [ ] CAB change requests processed: ___

## Platform Health
- SLO compliance: ___% of services meeting targets
- Error budget status: ___% of services with budget remaining
- Security posture: ___ open vulnerabilities (by severity)
- Cost trend: ___% change vs previous quarter

## Capacity Planning
- Services at warning threshold: ___
- Services at critical threshold: ___
- Scaling actions taken: ___
- Projected needs (next quarter): ___

## Roadmap for Next Quarter
| Priority | Item | Owner | Target Date |
|----------|------|-------|-------------|
| P1 | ___ | ___ | ___ |
| P2 | ___ | ___ | ___ |
| P3 | ___ | ___ | ___ |

## Sign-Off
- [ ] Platform Lead: _________________ Date: _______
- [ ] Security Lead: _________________ Date: _______
- [ ] Clinical Lead: _________________ Date: _______
- [ ] Finance/Budget: _________________ Date: _______
```

## Exit Criteria

This wave has no terminal exit criteria — it defines ongoing operational cadences. The following conditions indicate the loop is running correctly:

- [ ] Schema governance reviews happening on schedule
- [ ] Observability-driven backlog producing and resolving items
- [ ] Security patches flowing through pipeline within SLA
- [ ] Incident post-mortems completed for all SEV-1/SEV-2
- [ ] Cost/capacity reviews happening quarterly
- [ ] Quarterly roadmap published and reviewed with stakeholders
