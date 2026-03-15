# Cost Controls & Capacity Planning — Impilo vNext

> Wave 25 Deliverable | Status: Draft | Date: 2026-03-15

## 1. Purpose

This document defines the recurring processes for monitoring platform costs, planning capacity, and ensuring the Impilo vNext infrastructure scales efficiently with the national rollout. It ties capacity signals to the observability-driven backlog and feeds budget projections into the quarterly platform review.

## 2. Ownership Model

| Role | Responsibility |
|------|---------------|
| **Platform Lead** | Owns capacity planning; chairs monthly cost review; approves scaling actions |
| **SRE / Platform Engineering** | Collects infrastructure metrics; executes scaling actions; maintains capacity dashboards |
| **Domain Owners** | Justify resource requests for their services; optimize queries and processing within their domain |
| **Finance / Budget Owner** | Approves quarterly budget requests; tracks spend vs forecast |
| **Observability Lead** | Provides saturation metrics from weekly observability review; flags capacity-related signals |
| **Rollout Lead** | Provides site rollout schedule (Phase 1–4 timelines) for demand forecasting |

## 3. Cost Categories and Monitoring

### 3.1 Cost Inventory

| Category | Components | Metrics | Collection Method |
|----------|-----------|---------|-------------------|
| **Compute (K8s)** | Pod CPU and memory across all namespaces (ring0, ring1, ring2, outer) | CPU cores allocated vs used; memory GB allocated vs used; pod count | `kubectl top`, Prometheus `container_cpu_usage_seconds_total`, `container_memory_working_set_bytes` |
| **Database (PostgreSQL 16)** | Per-service databases (tshepo, vito, varapi, tuso, zibo, msika, pct, oros, pharmacy, mushex, etc.) | DB size (GB); table row counts; connection pool utilization; query execution time | `pg_database_size()`, `pg_stat_user_tables`, HikariCP metrics |
| **Object Storage (MinIO)** | document-service (S3 buckets), Orthanc PACS DICOM storage | Bucket size (GB); object count; GET/PUT request rates | MinIO admin API, `minio_bucket_usage_total_bytes` |
| **Message Streaming (Kafka KRaft)** | 5 channels: trust, kernel, clinical, telemetry, analytics | Storage per topic (GB); partition count; consumer group lag; message throughput (msg/sec) | Kafka JMX metrics, `kafka_log_log_size`, consumer lag exporters |
| **Caching (Redis 7)** | Session storage, TSHEPO decision cache, ZIBO terminology cache | Memory usage (MB); key count; hit/miss ratio; eviction rate | Redis `INFO` command, `redis_memory_used_bytes` |
| **Network** | Envoy gateway (port 10000); inter-service traffic; federation sync; external egress (SMS, eLMIS, DHIS2) | Ingress/egress bytes; request rate; connection count | Envoy admin metrics, K8s network policies |
| **Identity (Keycloak 25)** | Realms, users, sessions, client registrations | Active user count; session count; token issuance rate | Keycloak admin API, Keycloak metrics SPI |
| **Observability Stack** | Prometheus, Grafana, Loki, Jaeger/OTel Collector | Metric series count; log volume (GB/day); trace storage (GB) | Prometheus TSDB stats, Loki ingestion rate |
| **CI/CD** | GitHub Actions runners, container registry storage | Build minutes/month; registry storage (GB); image count | GitHub usage API, registry admin |

### 3.2 Per-Service Resource Baseline

Each service has a defined resource baseline (from Helm chart `values.yaml`):

| Ring | Service | CPU Request | CPU Limit | Memory Request | Memory Limit | Replicas (min) | DB Storage |
|------|---------|------------|-----------|----------------|-------------|----------------|------------|
| 0 | tshepo-authz-service | 500m | 2000m | 512Mi | 2Gi | 3 | 10Gi |
| 0 | vito-service | 500m | 2000m | 1Gi | 4Gi | 2 | 50Gi |
| 0 | butano-service (HAPI FHIR) | 1000m | 4000m | 2Gi | 8Gi | 2 | 100Gi |
| 1 | pct-service | 500m | 2000m | 512Mi | 2Gi | 2 | 20Gi |
| 1 | oros-service | 500m | 2000m | 512Mi | 2Gi | 2 | 20Gi |
| 1 | pharmacy-service | 250m | 1000m | 512Mi | 2Gi | 2 | 10Gi |
| 1 | mushex-service | 500m | 2000m | 512Mi | 2Gi | 2 | 20Gi |
| 2 | integration-hub | 250m | 1000m | 256Mi | 1Gi | 2 | 5Gi |
| 2 | notification-service | 250m | 1000m | 256Mi | 1Gi | 2 | 5Gi |
| 2 | search-service | 500m | 2000m | 1Gi | 4Gi | 2 | 50Gi |
| Outer | one-ui-shell | 100m | 500m | 128Mi | 512Mi | 2 | — |

> These baselines are for a single-pod deployment (Tier 1/2 site). National spine multiplies by federation pod count.

## 4. Capacity Thresholds and Alerts

### 4.1 Warning and Critical Thresholds

| Resource | Warning (amber) | Critical (red) | Auto-Action | Manual Action |
|----------|----------------|----------------|-------------|---------------|
| CPU utilization (pod) | 70% sustained 15 min | 85% sustained 5 min | Alert to SRE | Scale horizontally (add replica) or scale vertically (increase limit) |
| Memory utilization (pod) | 75% sustained 15 min | 90% sustained 5 min | Alert to SRE | Scale vertically (increase limit); investigate memory leak if trend is upward |
| DB storage used | 70% of PVC allocation | 85% of PVC allocation | Alert to SRE + Domain Owner | Expand PVC; archive old data; optimize storage (vacuum, reindex) |
| DB connection pool | 70% of max pool size | 85% of max pool size | Alert to Domain Owner | Increase pool size (HikariCP `maximumPoolSize`); add read replica; optimize long-running queries |
| Kafka partition lag | >1,000 messages sustained 10 min | >10,000 messages sustained 5 min | Alert to SRE | Add consumer instances; increase partitions; investigate slow consumers |
| Kafka storage per topic | 70% of retention quota | 85% of retention quota | Alert to SRE | Reduce retention; increase storage; archive to analytics channel |
| Redis memory | 70% of `maxmemory` | 85% of `maxmemory` | Alert to SRE | Increase `maxmemory`; review TTL policies; evict cold keys |
| MinIO bucket size | 70% of allocated quota | 85% of allocated quota | Alert to SRE | Expand storage; implement lifecycle rules (delete temp files >30 days) |
| Envoy connection count | 70% of `max_connections` | 85% of `max_connections` | Alert to SRE | Increase `max_connections`; add Envoy replicas; investigate connection leaks |
| Prometheus series count | >1M active series | >2M active series | Alert to Observability Lead | Review label cardinality; drop unused metrics; increase retention storage |

### 4.2 Alert Definitions (Prometheus)

```yaml
# Capacity alerts — included in observability stack
groups:
  - name: capacity
    rules:
      - alert: HighCpuUtilization
        expr: rate(container_cpu_usage_seconds_total{namespace=~"ring.*|outer"}[5m]) / container_spec_cpu_quota * 100 > 70
        for: 15m
        labels:
          severity: warning
          category: capacity
        annotations:
          summary: "CPU >70% for {{ $labels.pod }} in {{ $labels.namespace }}"

      - alert: CriticalCpuUtilization
        expr: rate(container_cpu_usage_seconds_total{namespace=~"ring.*|outer"}[5m]) / container_spec_cpu_quota * 100 > 85
        for: 5m
        labels:
          severity: critical
          category: capacity

      - alert: DatabaseStorageHigh
        expr: pg_database_size_bytes / pg_database_size_allocated_bytes > 0.70
        for: 30m
        labels:
          severity: warning
          category: capacity

      - alert: KafkaConsumerLagHigh
        expr: kafka_consumer_group_lag > 1000
        for: 10m
        labels:
          severity: warning
          category: capacity
```

## 5. Cost Review Cadence

### 5.1 Monthly Cost Review

**Schedule**: First Tuesday of each month, 60 minutes.
**Chair**: Platform Lead.
**Participants**: SRE, Domain Owners (rotating — 2 per meeting), Finance/Budget Owner.

| Agenda Item | Duration | Owner | Detail |
|-------------|----------|-------|--------|
| Cost dashboard walkthrough | 15 min | SRE | Review Grafana cost dashboard: compute, storage, network, Kafka by namespace/ring |
| Month-over-month trends | 10 min | SRE | Compare this month vs last; highlight >10% increases |
| Right-sizing opportunities | 10 min | SRE | Services where actual usage is <30% of request (over-provisioned) |
| Idle resource cleanup | 5 min | SRE | Unused PVCs, orphaned pods, stale container images |
| Domain-specific cost discussion | 10 min | Domain Owners (2) | Justify growth, propose optimizations for their services |
| Action items and budget impact | 10 min | Platform Lead + Finance | Record actions; update budget forecast if needed |

### 5.2 Monthly Cost Review Output

```markdown
# Monthly Cost Review — {YYYY}-{MM}

## Cost Summary
| Category | Last Month | This Month | Change | Trend |
|----------|-----------|------------|--------|-------|
| Compute (CPU cores) | {n} | {n} | {+/-}% | {up/flat/down} |
| Compute (Memory GB) | {n} | {n} | {+/-}% | {up/flat/down} |
| Database Storage (GB) | {n} | {n} | {+/-}% | {up/flat/down} |
| Object Storage (GB) | {n} | {n} | {+/-}% | {up/flat/down} |
| Kafka Storage (GB) | {n} | {n} | {+/-}% | {up/flat/down} |
| Network Egress (GB) | {n} | {n} | {+/-}% | {up/flat/down} |

## Top-5 Cost Drivers (by absolute increase)
1. {service/component}: {reason}
2. ...

## Right-Sizing Actions Taken
| Service | Resource | Old Value | New Value | Savings |
|---------|----------|-----------|-----------|---------|
| {service} | {CPU/Memory} | {old} | {new} | {estimated} |

## Idle Resources Cleaned
| Resource Type | Count | Storage Freed |
|---------------|-------|---------------|
| Orphaned PVCs | {n} | {GB} |
| Stale images | {n} | {GB} |

## Action Items
| # | Action | Owner | Deadline |
|---|--------|-------|----------|
| 1 | {action} | {name} | {date} |

## Budget Status
- YTD spend: {amount or resource units}
- Annual budget: {amount or resource units}
- Projected year-end: {amount or resource units}
- Status: ON_TRACK / AT_RISK / OVER_BUDGET
```

## 6. Quarterly Capacity Planning

### 6.1 Process

**Schedule**: Last week of each quarter, 90 minutes.
**Chair**: Platform Lead.
**Participants**: SRE, all Domain Owners, Finance/Budget Owner, Rollout Lead.

```
Inputs:
  ├── 3 months of resource utilization data (Prometheus/Grafana)
  ├── Site rollout schedule (Phase 1–4 from docs/rollout/national-rollout-plan.md)
  ├── Service growth rates (DB size, event volume, user count)
  ├── Planned feature work (from quarterly roadmap)
  └── Incident history (capacity-related incidents from incident register)

Process:
  ├── Step 1: Current utilization snapshot (per service, per ring)
  ├── Step 2: Growth rate calculation
  │   ├── Linear extrapolation of last 3 months
  │   ├── Step function for rollout phases (e.g., Phase 2 adds 10-15 sites)
  │   └── Seasonal adjustment (if data available — e.g., flu season volume)
  ├── Step 3: Projected utilization (next quarter + next 12 months)
  ├── Step 4: Bottleneck identification
  │   ├── Which resource hits warning threshold first?
  │   ├── Which service has the steepest growth curve?
  │   └── Are there single-points-of-failure (SPOF) in infrastructure?
  ├── Step 5: Scaling recommendations
  │   ├── Horizontal scaling (add replicas) — preferred for stateless services
  │   ├── Vertical scaling (increase resources) — for stateful components (DB, Kafka)
  │   ├── Architecture changes (read replicas, caching, partitioning) — for structural limits
  │   └── Infrastructure additions (new nodes, storage expansion)
  └── Step 6: Budget request for next quarter

Output:
  └── Quarterly Capacity Plan document (see template below)
```

### 6.2 Quarterly Capacity Plan Template

```markdown
# Quarterly Capacity Plan — Q{N} {YYYY}

## 1. Current Utilization Snapshot
| Service | Ring | CPU Used/Limit | Mem Used/Limit | DB Size/Alloc | Replicas |
|---------|------|---------------|---------------|---------------|----------|
| tshepo-authz | 0 | {m}/{m} ({%}) | {Mi}/{Mi} ({%}) | {Gi}/{Gi} ({%}) | {n} |
| vito-service | 0 | ... | ... | ... | ... |
| pct-service | 1 | ... | ... | ... | ... |
| ... | ... | ... | ... | ... | ... |

## 2. Growth Rates (Last Quarter)
| Resource | Monthly Growth Rate | Driver |
|----------|-------------------|--------|
| Total CPU usage | {%}/month | {reason} |
| Total DB storage | {GB}/month | {reason — e.g., 15 new sites in Phase 2} |
| Kafka event volume | {msg/sec increase}/month | {reason} |
| Active users | {count}/month | {reason — rollout phase} |

## 3. Projections (Next Quarter)
| Resource | Current | Projected (Q+1 end) | Warning Date | Critical Date |
|----------|---------|---------------------|-------------|---------------|
| Total CPU | {cores} | {cores} | {date or N/A} | {date or N/A} |
| Total Memory | {GB} | {GB} | {date or N/A} | {date or N/A} |
| Total DB Storage | {GB} | {GB} | {date or N/A} | {date or N/A} |
| Kafka Storage | {GB} | {GB} | {date or N/A} | {date or N/A} |

## 4. Bottleneck Analysis
| Rank | Resource | Service/Component | Projected Hit Date | Risk Level |
|------|----------|------------------|-------------------|------------|
| 1 | {resource} | {component} | {date} | High/Medium/Low |
| 2 | ... | ... | ... | ... |

## 5. Scaling Recommendations
| # | Component | Action | Type | Resource Change | Cost Impact | Priority |
|---|-----------|--------|------|----------------|-------------|----------|
| 1 | {component} | {action} | Horizontal/Vertical/Architectural | {from → to} | {estimated} | P1/P2/P3 |

## 6. Budget Request
| Item | Quantity | Unit Cost | Total | Justification |
|------|----------|-----------|-------|---------------|
| {item} | {qty} | {cost} | {total} | {reason} |

## 7. Sign-Off
| Role | Name | Date | Approval |
|------|------|------|----------|
| Platform Lead | | | Approve / Reject |
| Finance / Budget | | | Approve / Reject |
```

## 7. Cost Optimization Strategies

| Strategy | Method | Review Frequency | Owner |
|----------|--------|-----------------|-------|
| **Right-sizing** | Compare pod resource requests vs actual p95 usage; reduce requests where usage is <30% of request | Monthly (cost review) | SRE |
| **Idle cleanup** | Identify services with 0 traffic for >7 days; remove orphaned PVCs; prune stale container images | Monthly (cost review) | SRE |
| **Query optimization** | Review `pg_stat_statements` for top-10 slowest queries per service; optimize or add indexes | Monthly (domain owners) | Domain Owners |
| **Storage tiering** | Move data older than retention period to cold storage (e.g., analytics channel in Kafka, archive tables in PostgreSQL) | Quarterly (capacity plan) | SRE + Data domain owner |
| **Caching effectiveness** | Review Redis hit/miss ratio; increase TTL for stable data (ZIBO terminology); decrease TTL for volatile data | Quarterly | Domain Owners |
| **Event volume control** | Review Kafka event volumes per topic; identify high-volume low-value events; adjust emission rules (EM-1 through EM-8) | Quarterly (event contract review, see schema governance) | Schema Steward + Domain Owners |
| **Log volume control** | Review Loki ingestion rate; reduce DEBUG/INFO logging where signal-to-noise is low; increase sampling for high-volume services | Monthly | Observability Lead |
| **Replica right-sizing** | Review HPA scaling events; adjust min/max replicas based on traffic patterns | Monthly | SRE |

## 8. Rollout-Driven Demand Forecasting

| Rollout Phase | Sites Added | Estimated User Increase | Compute Increase | DB Growth | Kafka Volume Increase |
|---------------|------------|------------------------|-------------------|-----------|----------------------|
| Phase 1 (Pilot) | 3 | ~100 users | +10% over baseline | +5 GB/month | +500 msg/sec |
| Phase 2 (Early Adopter) | 10–15 | ~500 users | +30% over Phase 1 | +20 GB/month | +2,000 msg/sec |
| Phase 3 (Majority) | 50–100 | ~3,000 users | +150% over Phase 2 | +100 GB/month | +10,000 msg/sec |
| Phase 4 (Full Scale) | All remaining | ~10,000+ users | +200% over Phase 3 | +300 GB/month | +30,000 msg/sec |

> These projections are estimates based on typical health facility usage patterns. Actual growth must be validated against Phase 1 telemetry data and adjusted each quarter.

### 8.1 Per-Phase Scaling Actions

| Phase Transition | Scaling Actions Required |
|-----------------|------------------------|
| Phase 0 → 1 | Baseline established; no scaling expected |
| Phase 1 → 2 | Add replicas for Ring 0 services (tshepo-authz: 3→5, vito: 2→4); expand VITO DB (50Gi→100Gi); add Kafka partitions for clinical channel |
| Phase 2 → 3 | Add K8s worker nodes; add PostgreSQL read replicas for VITO and PCT; expand Kafka cluster (3→5 brokers); add Envoy replicas |
| Phase 3 → 4 | Full production-grade infrastructure; geographic distribution for federation; dedicated Kafka clusters per channel; PostgreSQL horizontal partitioning for VITO |

## 9. Decision Log Integration

All cost and capacity decisions are recorded:

| Field | Description |
|-------|-------------|
| Decision ID | CPD-{YYYY}-{NNN} |
| Date | Decision date |
| Type | Scaling / Right-sizing / Cleanup / Architecture / Budget |
| Component | Service or infrastructure component |
| Current State | Current resource allocation/usage |
| Target State | Proposed allocation |
| Cost Impact | Estimated cost change |
| Justification | Business/technical rationale |
| Approved By | Platform Lead + Finance (if budget impact) |
| Implemented | Date of implementation |
| Verified | Post-implementation utilization check |
