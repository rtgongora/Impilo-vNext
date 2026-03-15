# Wave 20 — RPO/RTO Matrix

> Date: 2026-03-15
> Scope: All Impilo vNext services by ring and service class
> Branch: `claude/review-project-manifest-jb5O0`

---

## 1. Definitions

| Term | Definition |
|------|-----------|
| **RPO** (Recovery Point Objective) | Maximum tolerable data loss measured in time. An RPO of 5 min means at most 5 minutes of committed transactions may be lost after recovery. |
| **RTO** (Recovery Time Objective) | Maximum tolerable downtime from failure detection to service restoration. An RTO of 15 min means the service must be operational within 15 minutes of a confirmed outage. |
| **MTTR** (Mean Time to Recovery) | Average observed recovery time across drills and incidents. Must be less than RTO. |
| **Ring** | Service criticality classification. Ring 0 = care path blocked; Ring 1 = care degraded; Ring 2 = no clinical impact. |
| **Care path** | The clinical workflow a healthcare worker executes during patient care. Ring 0 services are on the care path. |

---

## 2. RPO/RTO Matrix by Ring and Service Class

### 2.1 Ring 0 — Trust & Governance

| Service | Database(s) | RPO | RTO | Backup Method | Failover | Rationale |
|---------|------------|:---:|:---:|---------------|----------|-----------|
| **TSHEPO** | `tshepo` | **0 min** | **≤ 15 min** | Continuous WAL + synchronous streaming replica | Patroni auto-promote | Every API request transits TSHEPO ext_authz. Any data loss could mean lost authorization policies or audit records. Synchronous replication ensures zero data loss. |
| **TSHEPO sub-services** | `tshepo_authz`, `tshepo_identity`, `tshepo_consent`, `tshepo_audit`, `tshepo_keys`, `tshepo_offline` | **0 min** | **≤ 15 min** | Continuous WAL + synchronous streaming replica | Patroni auto-promote | Sub-services share TSHEPO's criticality tier — they are the decomposed trust plane. |
| **Keycloak** | `keycloak` | **0 min** | **≤ 15 min** | Continuous WAL + synchronous streaming replica + daily realm export | Standby promotion | Token issuance depends on Keycloak. Existing tokens survive briefly (5–15 min TTL), but new sessions require Keycloak. Zero RPO prevents credential loss. |

### 2.2 Ring 0 — Registry Spine

| Service | Database(s) | RPO | RTO | Backup Method | Failover | Rationale |
|---------|------------|:---:|:---:|---------------|----------|-----------|
| **VITO** | `vito` | **≤ 5 min** | **≤ 15 min** | Continuous WAL + synchronous streaming replica | Patroni auto-promote | Master Patient Index. Lost registrations can be re-entered, but a 5-min RPO caps the re-work window. Clinical workflows depend on CPID resolution. |
| **VARAPI** | `varapi` | **≤ 5 min** | **≤ 15 min** | Continuous WAL + synchronous streaming replica | Patroni auto-promote | Provider/facility registry. Changes are infrequent; 5-min RPO is generous. Care workers cannot be validated without VARAPI. |
| **TUSO** | `tuso` | **≤ 5 min** | **≤ 15 min** | Continuous WAL + synchronous streaming replica | Patroni auto-promote | Terminology service. Codeset data is largely static and can be re-imported from upstream sources. 15-min RTO aligns with clinical SLA. |
| **ZIBO** | `zibo` | **≤ 5 min** | **≤ 15 min** | Continuous WAL + synchronous streaming replica | Patroni auto-promote | Tariff/billing engine. Tariff schedules can be re-loaded. Billing calculations during recovery gap can be re-run. |

### 2.3 Ring 0 — Extended (Clinical + Finance Core)

| Service | Database(s) | RPO | RTO | Backup Method | Failover | Rationale |
|---------|------------|:---:|:---:|---------------|----------|-----------|
| **MSIKA** | `msika` | **≤ 5 min** | **≤ 30 min** | Continuous WAL + async streaming replica | Patroni auto-promote | Clinical encounter engine. Encounter data loss means re-entry by clinician. 5-min RPO minimizes this. 30-min RTO acceptable because MSIKA outage degrades but does not fully block care. |
| **BUTANO** | `butano` | **≤ 5 min** | **≤ 30 min** | Continuous WAL + async streaming replica | Patroni auto-promote | FHIR SHR. Downstream consumer of clinical events — data can be rebuilt from source services. Longer RTO acceptable because SHR is a read optimization, not the source of truth. |
| **MUSHEX** | `mushex` | **≤ 5 min** | **≤ 30 min** | Continuous WAL + async streaming replica | Patroni auto-promote | Payer/claims engine. Financial transactions must be durable; 5-min RPO ensures minimal re-adjudication. Not on immediate care path, so 30-min RTO is acceptable. |

### 2.4 Ring 1 — Clinical Execution

| Service | Database(s) | RPO | RTO | Backup Method | Failover | Rationale |
|---------|------------|:---:|:---:|---------------|----------|-----------|
| **PCT** | `pct` | **≤ 5 min** | **≤ 30 min** | Continuous WAL + daily full dump | Manual failover | Patient Care Tracker. Active encounters are at risk; 5-min RPO limits data loss to recent clinical notes. |
| **OROS** | `oros` | **≤ 5 min** | **≤ 30 min** | Continuous WAL + daily full dump | Manual failover | Orders & Results. Lab orders can be re-submitted; results from external LIMS are idempotent. |
| **UBOMI** | `ubomi` | **≤ 5 min** | **≤ 30 min** | Continuous WAL + daily full dump | Manual failover | Longitudinal health record. Important but not on immediate care path — clinicians fall back to paper briefly. |

### 2.5 Ring 1 — Finance & Operations

| Service | Database(s) | RPO | RTO | Backup Method | Failover | Rationale |
|---------|------------|:---:|:---:|---------------|----------|-----------|
| **Pharmacy** | `pharmacy` | **≤ 1 hr** | **≤ 1 hr** | Daily full dump | Manual restore | Dispense records. Pharmacists maintain paper logs as backup. 1-hr RPO aligns with dispensing session length. |
| **Inventory** | `inventory` | **≤ 1 hr** | **≤ 1 hr** | Daily full dump | Manual restore | Stock levels. Physical stock exists regardless of system state; reconciliation corrects discrepancies. |
| **Inpatient** | `inpatient` | **≤ 1 hr** | **≤ 1 hr** | Daily full dump | Manual restore | Bed management, ward assignments. Paper fallback exists for admission/discharge. |
| **Costing** | `costing` | **≤ 1 hr** | **≤ 1 hr** | Daily full dump | Manual restore | Billing calculations. Can be re-derived from encounter and tariff data. |
| **Landela suite** | `landela_adapter`, `credential_verification`, `share_slip`, `card_print`, `msika_flow` | **≤ 1 hr** | **≤ 1 hr** | Daily full dump | Manual restore | Credential and document issuance. Card printing can be re-triggered; verification queries upstream sources. |

### 2.6 Ring 2 — Platform Services

| Service | Database(s) | RPO | RTO | Backup Method | Failover | Rationale |
|---------|------------|:---:|:---:|---------------|----------|-----------|
| **Document Service** | `document_service` | **≤ 4 hr** | **≤ 2 hr** | Daily full dump | Manual restore | Document metadata. Actual files in MinIO (separate backup). Metadata can be regenerated from files. |
| **Notification** | `notification` | **≤ 4 hr** | **≤ 2 hr** | Daily full dump | Manual restore | Notification queue. Missed notifications can be re-sent. No clinical data at risk. |
| **Jobs** | `jobs` | **≤ 4 hr** | **≤ 2 hr** | Daily full dump | Manual restore | Scheduled job metadata. Jobs are defined in code (CronJobs); state tracks execution history only. |
| **Offline Sync** | `offline_sync` | **≤ 4 hr** | **≤ 2 hr** | Daily full dump | Manual restore | Sync state for offline edge. Worst case: offline clients re-sync fully on next connection. |
| **Integration Hub** | `integration_hub` | **≤ 4 hr** | **≤ 2 hr** | Daily full dump | Manual restore | Integration mapping state. Connectors are stateless; hub tracks correlation IDs. |
| **Experience BFF** | `experience_bff` | **≤ 4 hr** | **≤ 2 hr** | Daily full dump | Manual restore | UI session state. Users re-authenticate on loss; no persistent clinical data. |
| **Forms / Search** | `impilo_forms`, `impilo_search` | **≤ 4 hr** | **≤ 2 hr** | Daily full dump | Manual restore | Form definitions (in Git), search indices (rebuildable from source databases). |

### 2.7 Infrastructure Components

| Component | RPO | RTO | Backup Method | Failover | Rationale |
|-----------|:---:|:---:|---------------|----------|-----------|
| **Kafka** | **0** (replicated) | **≤ 5 min** | KRaft internal replication + MirrorMaker 2 | Automatic (KRaft leader election) | Kafka is the event transport, not the source of truth. KRaft replication protects against broker failure. Full cluster loss = redeploy + replay from outbox. |
| **Redis** | **N/A** (cache) | **≤ 2 min** | No backup needed | Automatic (K8s restart) | Redis is cache-only. Services tolerate Redis absence (degraded performance, not outage). Cache warms on restart from DB queries. |
| **MinIO** | **≤ 1 hr** | **≤ 1 hr** | `mc mirror` to DR bucket (continuous) | Manual promotion of DR bucket | Documents and PACS images. Not on immediate care path; clinicians fall back to physical records. 1-hr RPO limits document loss. |
| **Envoy** | **N/A** (stateless) | **≤ 1 min** | No backup needed | Automatic (K8s restart) | Envoy is stateless — config is in Git (`infra/envoy/envoy-runtime.yaml`). K8s restarts crashed pods automatically. |

---

## 3. RPO/RTO Summary Dashboard

| Ring | Service Count | RPO Range | RTO Range | Backup Automation | Failover Automation |
|------|:------------:|:---------:|:---------:|:-----------------:|:-------------------:|
| **Ring 0 Trust** | 8 (TSHEPO + sub-services + Keycloak) | 0 min | ≤ 15 min | Continuous WAL + daily dump | Semi-automated (Patroni) |
| **Ring 0 Registry** | 4 (VITO, VARAPI, TUSO, ZIBO) | ≤ 5 min | ≤ 15 min | Continuous WAL + daily dump | Semi-automated (Patroni) |
| **Ring 0 Extended** | 3 (MSIKA, BUTANO, MUSHEX) | ≤ 5 min | ≤ 30 min | Continuous WAL + daily dump | Semi-automated (Patroni) |
| **Ring 1 Clinical** | 3 (PCT, OROS, UBOMI) | ≤ 5 min | ≤ 30 min | Continuous WAL + daily dump | Manual |
| **Ring 1 Ops/Finance** | 9 (Pharmacy, Inventory, etc.) | ≤ 1 hr | ≤ 1 hr | Daily dump | Manual |
| **Ring 2 Platform** | 8 (Notification, Jobs, etc.) | ≤ 4 hr | ≤ 2 hr | Daily dump | Manual |
| **Kafka** | 1 cluster | 0 (replicated) | ≤ 5 min | KRaft + MirrorMaker 2 | Automatic |
| **MinIO** | 1 cluster | ≤ 1 hr | ≤ 1 hr | `mc mirror` continuous | Manual |

---

## 4. Operational Implications

### 4.1 Staffing

| RPO/RTO Tier | On-Call Requirement | Response Time |
|-------------|--------------------:|:-------------:|
| Ring 0 (0–5 min RPO, ≤ 15 min RTO) | 24/7 on-call SRE + platform engineer | ≤ 5 min acknowledgement |
| Ring 1 Clinical (5 min RPO, 30 min RTO) | 24/7 on-call SRE | ≤ 15 min acknowledgement |
| Ring 1 Ops (1 hr RPO, 1 hr RTO) | Business hours + on-call | ≤ 30 min acknowledgement |
| Ring 2 (4 hr RPO, 2 hr RTO) | Business hours | Next business day if after hours |

### 4.2 Infrastructure Requirements

| Requirement | Ring 0 | Ring 1 | Ring 2 |
|------------|:------:|:------:|:------:|
| Synchronous streaming replica | Required | Optional (async) | Not required |
| WAL archiving to S3 | Required | Required (clinical) | Not required |
| Daily pg_dump | Required | Required | Required |
| Patroni/auto-failover | Required | Recommended | Not required |
| Cross-AZ deployment | Required | Recommended | Optional |
| DR site replication | Required | Optional | Not required |

### 4.3 Cost-Benefit Analysis

| Investment | Enables | Risk Mitigated |
|-----------|---------|----------------|
| Synchronous replica (Ring 0) | 0-min RPO | Zero data loss for authorization, identity, and audit records |
| WAL archiving (Ring 0 + Ring 1) | PITR capability | Recovery to any second within retention window |
| Daily pg_dump (all rings) | Known-good snapshot | Baseline recovery even if WAL chain breaks |
| MirrorMaker 2 | Kafka DR | Event stream continuity across clusters |
| Patroni | Auto-failover | Sub-minute database failover without human intervention |

---

## 5. Verification Schedule

| Verification | Frequency | Method | Owner |
|-------------|-----------|--------|-------|
| Backup completion check | Daily (automated) | Alert if `impilo_backup_last_success_timestamp` > 26 hours | SRE on-call |
| Restore to isolated environment | Weekly | Automated restore + smoke test (Ring 0 only) | Platform engineering |
| Single-service restore drill | Monthly | Full restore + validation of 1 randomly selected service | Platform engineering |
| Cross-service consistency check | Monthly | Row count + FK integrity between related databases | Platform engineering |
| Full-stack restore drill | Quarterly | Restore all Ring 0 databases + services to DR environment | Platform + clinical |
| Game day exercise | Bi-annually | Surprise failure injection per game-day-scenarios.md | All engineering |

---

## 6. RPO/RTO Verification Status

| Ring | RPO Verified | RTO Verified | Last Drill | Next Scheduled | Notes |
|------|:-----------:|:-----------:|:----------:|:--------------:|-------|
| 0 Trust | ❌ | ❌ | — | First month post-deploy | Pending staging environment |
| 0 Registry | ❌ | ❌ | — | First month post-deploy | Pending staging environment |
| 0 Extended | ❌ | ❌ | — | First month post-deploy | Pending staging environment |
| 1 Clinical | ❌ | ❌ | — | Second month post-deploy | After Ring 0 verified |
| 1 Ops | ❌ | ❌ | — | Second month post-deploy | After Ring 0 verified |
| 2 Platform | ❌ | ❌ | — | Third month post-deploy | Lower priority |

---

## 7. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-03-15 | Wave 20 | Initial RPO/RTO matrix |
