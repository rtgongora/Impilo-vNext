# Wave 20 — Disaster Recovery and Continuity

> Status: Not Started | Date: 2026-03-14

## Goal

DR is real, not a slide. Every critical service has automated backup/restore, measured RPO/RTO, and tested failover playbooks.

## Prerequisites

| Wave | Dependency |
|------|-----------|
| 19 | Production readiness gate (SLOs, alerting, baselines established) |

## Deliverables

### 1. Backup + Restore Automation

#### Backup Strategy by Data Class

| Data Class | Services | Backup Method | Frequency | Retention |
|------------|----------|---------------|-----------|-----------|
| Ring 0 — Trust | TSHEPO, Keycloak | pg_dump + WAL archiving | Continuous WAL + daily full | 90 days full, 7 days WAL |
| Ring 0 — Registry | VITO, VARAPI, TUSO, ZIBO | pg_dump + WAL archiving | Continuous WAL + daily full | 90 days full, 7 days WAL |
| Ring 1 — Clinical | MSIKA, UBOMI, BUTANO, PCT, OROS | pg_dump + WAL archiving | Continuous WAL + daily full | 365 days full, 30 days WAL |
| Ring 1 — Operational | Pharmacy, Inventory, Inpatient | pg_dump + daily snapshot | Daily full | 90 days |
| Ring 2 — Platform | Integration Hub, Notification, Jobs | pg_dump + daily snapshot | Daily full | 30 days |
| Kafka | All topics | MirrorMaker 2 to DR cluster | Continuous replication | Topic-specific retention |
| Keycloak | Realm config + user store | pg_dump + realm export | Daily full + realm export | 90 days |
| MinIO (documents) | Document, PACS | mc mirror to DR bucket | Continuous replication | Match source retention |

#### Backup Automation Scripts

Location: `scripts/dr/`

```
scripts/dr/
├── backup/
│   ├── pg-full-backup.sh          # Full PostgreSQL backup per service
│   ├── pg-wal-archive.sh          # WAL archiving configuration
│   ├── kafka-mirrormaker.yml      # MirrorMaker 2 configuration
│   ├── minio-mirror.sh            # MinIO bucket replication
│   └── keycloak-realm-export.sh   # Keycloak realm export
├── restore/
│   ├── pg-restore-full.sh         # Full PostgreSQL restore
│   ├── pg-restore-pitr.sh         # Point-in-time recovery
│   ├── kafka-topic-restore.sh     # Topic restore from mirror
│   ├── minio-restore.sh           # Document restore
│   └── keycloak-realm-import.sh   # Keycloak realm import
├── verify/
│   ├── backup-integrity-check.sh  # Verify backup checksums
│   ├── restore-smoke-test.sh      # Post-restore health checks
│   └── data-consistency-check.sh  # Cross-service data integrity
└── schedules/
    └── backup-cron.yml            # K8s CronJob definitions
```

#### Backup Verification

| Check | Frequency | Method |
|-------|-----------|--------|
| Backup file integrity | Every backup | SHA-256 checksum comparison |
| Restore to isolated environment | Weekly | Automated restore → smoke test |
| Cross-service consistency | Monthly | Row count + FK integrity checks |
| WAL replay verification | Weekly | PITR to random timestamp → query test |

### 2. Restore Drills (Documented)

#### Drill Schedule

| Drill Type | Frequency | Participants | Duration |
|------------|-----------|-------------|----------|
| Single-service restore | Monthly | Platform engineering | 2 hours |
| Full-stack restore | Quarterly | Platform + clinical + ops | 4 hours |
| Failover drill | Quarterly | Platform + SRE + on-call | 2 hours |
| Game day (surprise) | Bi-annually | All engineering | Full day |

#### Drill Procedure Template

```markdown
# DR Drill Report — [Date] — [Drill Type]

## Scope
- Service(s) targeted: ___
- Failure mode simulated: ___
- DR region/cluster: ___

## Timeline
| Time | Event | Actor |
|------|-------|-------|
| T+0 | Drill initiated | Drill lead |
| T+?m | Failure detected by monitoring | Automated |
| T+?m | Incident declared | On-call |
| T+?m | Restore initiated | Platform eng |
| T+?m | Service healthy | Automated health check |
| T+?m | Data integrity verified | Platform eng |
| T+?m | Drill closed | Drill lead |

## Measurements
- Detection time (T_detect): ___ minutes
- Recovery time (T_recover): ___ minutes
- Data loss window: ___ minutes
- Total RTO achieved: ___ minutes

## Findings
- What went well: ___
- What needs improvement: ___
- Action items: ___

## Sign-Off
- Drill Lead: _________________ Date: _______
- Platform Lead: _________________ Date: _______
```

### 3. RPO/RTO per Ring/Service Class

| Ring | Service Class | RPO Target | RTO Target | Backup Method |
|------|--------------|------------|------------|---------------|
| 0 | Trust (TSHEPO, Keycloak) | 0 min (WAL) | ≤ 15 min | Continuous WAL + streaming replica |
| 0 | Registry (VITO, VARAPI, TUSO, ZIBO) | ≤ 5 min | ≤ 15 min | Continuous WAL + streaming replica |
| 1 | Clinical (MSIKA, UBOMI, BUTANO) | ≤ 5 min | ≤ 30 min | Continuous WAL + daily full |
| 1 | Finance (PCT, OROS) | ≤ 5 min | ≤ 30 min | Continuous WAL + daily full |
| 1 | Operational (Pharmacy, Inventory) | ≤ 1 hr | ≤ 1 hr | Daily full |
| 2 | Platform (Integration Hub, etc.) | ≤ 4 hr | ≤ 2 hr | Daily full |
| — | Kafka (event bus) | 0 (replicated) | ≤ 5 min | MirrorMaker 2 continuous |
| — | MinIO (documents/PACS) | ≤ 1 hr | ≤ 1 hr | Continuous mirror |

#### RPO/RTO Verification Matrix

| Ring | RPO Verified | RTO Verified | Last Drill Date | Notes |
|------|:---:|:---:|:---:|-------|
| 0 | ❌ | ❌ | — | Pending Wave 20 |
| 1 | ❌ | ❌ | — | Pending Wave 20 |
| 2 | ❌ | ❌ | — | Pending Wave 20 |

### 4. Failover Playbooks + Game-Day Scenarios

#### Failover Playbooks

| Scenario | Playbook | Automation Level |
|----------|----------|-----------------|
| Primary DB failure | `playbooks/db-failover.md` | Semi-automated (Patroni promotes replica) |
| Kafka broker loss | `playbooks/kafka-broker-failover.md` | Automated (KRaft re-elects leader) |
| Keycloak failure | `playbooks/keycloak-failover.md` | Semi-automated (standby promotion) |
| Full AZ failure | `playbooks/az-failover.md` | Manual (DNS failover + restore) |
| Envoy gateway failure | `playbooks/envoy-failover.md` | Automated (K8s restarts pod) |
| MinIO failure | `playbooks/minio-failover.md` | Semi-automated (mirror promotion) |

#### Game-Day Scenarios

| # | Scenario | Inject Method | Expected Impact | Success Criteria |
|---|----------|--------------|-----------------|------------------|
| GD-1 | Kill primary DB for TSHEPO | `kubectl delete pod` | Auth requests fail briefly | RTO ≤ 15 min, zero data loss |
| GD-2 | Network partition between Kafka and services | `tc netem` | Events queue in outbox | Events eventually delivered, no duplicates |
| GD-3 | Corrupt VITO backup, then fail primary | Truncate backup file | Must use secondary backup | Restore from alternate backup ≤ 30 min |
| GD-4 | Simultaneous loss of 2 Ring 0 services | Kill TSHEPO + VITO | Auth + MPI down | Both recovered within RPO/RTO |
| GD-5 | Full AZ failure simulation | Drain all pods in AZ-1 | 50% capacity loss | Remaining AZ handles load, no data loss |
| GD-6 | Keycloak realm corruption | Inject bad realm config | Auth broken | Realm restored from export ≤ 15 min |

## Deliverable: DR Drill Evidence Pack

```markdown
# DR Drill Evidence Pack — Impilo vNext

## Contents
- [ ] Backup automation scripts (tested in staging)
- [ ] Restore procedures (executed at least once per service class)
- [ ] RPO/RTO measurement results per ring
- [ ] Drill reports (minimum 1 per drill type)
- [ ] Game-day scenario results (minimum 2 scenarios executed)
- [ ] Failover playbook validation evidence
- [ ] Backup integrity verification logs
- [ ] Cross-service consistency check results

## Sign-Off
- [ ] Platform Engineering Lead: _________________ Date: _______
- [ ] SRE Lead: _________________ Date: _______
- [ ] Clinical Safety Officer: _________________ Date: _______
- [ ] Compliance Officer: _________________ Date: _______
```

## Exit Criteria

- [ ] Backup automation runs on schedule for all service classes
- [ ] Restore procedure executed successfully for each ring
- [ ] RPO/RTO targets met in at least one drill per ring
- [ ] At least 2 game-day scenarios completed with passing results
- [ ] Failover playbooks validated in staging
- [ ] Backup integrity checks passing on schedule
- [ ] DR drill evidence pack signed off
