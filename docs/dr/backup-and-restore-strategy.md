# Wave 20 — Backup and Restore Strategy

> Date: 2026-03-15
> Scope: All Impilo vNext services — Ring 0 through Ring 2, infrastructure components
> Branch: `claude/review-project-manifest-jb5O0`
> Prerequisites: [Wave 19 Production Readiness](../production-readiness/production-readiness-report.md), [Wave 20 DR Specification](../ops/wave20-disaster-recovery.md)

---

## 1. Backup Architecture Overview

Impilo vNext uses a tiered backup strategy based on service criticality (ring classification) and data sensitivity. All persistent state resides in PostgreSQL databases; Kafka provides durable event streaming with outbox-pattern decoupling; Redis serves as a cache (no persistent state requiring backup); MinIO stores documents and PACS images.

```
┌─────────────────────────────────────────────────────────────────────┐
│                        BACKUP ARCHITECTURE                         │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  PostgreSQL (primary state store)                                   │
│  ├─ Continuous WAL archiving → S3/MinIO backup bucket               │
│  ├─ Daily pg_dump full backups → S3/MinIO backup bucket             │
│  └─ Streaming replication → standby replica (Ring 0 + Ring 1)       │
│                                                                     │
│  Kafka (event bus)                                                  │
│  ├─ KRaft internal replication (min.insync.replicas=2)              │
│  ├─ Topic retention policies (7–30 days)                            │
│  └─ MirrorMaker 2 → DR Kafka cluster (Ring 0 topics)               │
│                                                                     │
│  MinIO (documents, PACS)                                            │
│  ├─ mc mirror → DR MinIO bucket (continuous)                        │
│  └─ Erasure coding for on-cluster durability                        │
│                                                                     │
│  Keycloak (identity provider)                                       │
│  ├─ pg_dump of keycloak database (daily)                            │
│  └─ Realm JSON export (daily)                                       │
│                                                                     │
│  Redis (cache only)                                                 │
│  └─ No backup required — warm-up from DB on restart                 │
│                                                                     │
│  Configuration & Artifacts                                          │
│  ├─ Git repo (source of truth for code, migrations, Helm)           │
│  ├─ Helm release snapshots via `helm get values`                    │
│  └─ Kubernetes secrets exported (encrypted) to secure storage       │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 2. Database Backup Strategy

### 2.1 Database Inventory

All databases are created by `scripts/seed/init-databases.sql`. The complete list:

| Ring | Service Class | Databases |
|------|--------------|-----------|
| 0 — Trust | TSHEPO + sub-services | `tshepo`, `tshepo_authz`, `tshepo_identity`, `tshepo_consent`, `tshepo_audit`, `tshepo_keys`, `tshepo_offline` |
| 0 — Registry | Core registries | `vito`, `varapi`, `tuso`, `zibo` |
| 0 — Extended | Clinical/Finance | `msika`, `butano`, `mushex` |
| 1 — Clinical | Care delivery | `ubomi`, `pct`, `oros` |
| 1 — Operational | Facility ops | `pharmacy`, `inpatient`, `inventory`, `costing` |
| 1 — Landela Suite | Credentials & docs | `landela_adapter`, `credential_verification`, `share_slip`, `card_print`, `msika_flow` |
| 2 — Platform | Support services | `document_service`, `notification`, `jobs`, `offline_sync`, `integration_hub`, `experience_bff`, `impilo_forms`, `impilo_search` |
| Infra | Identity provider | `keycloak` |

**Total: 36 databases** on a shared PostgreSQL 16 instance (dev); production uses per-ring database clusters.

### 2.2 Backup Methods

#### Full Backup (pg_dump)

Runs daily via CronJob. Produces a compressed custom-format dump per database.

```bash
# Executed by scripts/dr/backup-all.sh
pg_dump -h "${DB_HOST}" -U "${DB_USER}" -Fc -Z 6 \
  --file="${BACKUP_DIR}/${db}_${TIMESTAMP}.dump" \
  "${db}"
```

**Storage:** Uploaded to `s3://${BACKUP_BUCKET}/postgres/daily/${db}/` with lifecycle policy matching retention period.

**Verification:** SHA-256 checksum computed and stored alongside the dump file.

#### WAL Archiving (Continuous)

PostgreSQL WAL segments are archived continuously to enable point-in-time recovery (PITR).

**postgresql.conf settings (production):**
```ini
wal_level = replica
archive_mode = on
archive_command = 'aws s3 cp %p s3://${BACKUP_BUCKET}/postgres/wal/%f --sse AES256'
archive_timeout = 60
```

**Recovery:** WAL files allow restoring to any point in time within the retention window.

#### Streaming Replication (Ring 0 + Ring 1)

Ring 0 and Ring 1 database clusters maintain at least one synchronous streaming replica.

```ini
# Primary postgresql.conf
synchronous_standby_names = 'impilo_standby_1'
synchronous_commit = on        # Ring 0: on (zero data loss)
                               # Ring 1: remote_apply (near-zero)
```

### 2.3 Backup Tiers

| Tier | Databases | Full Dump | WAL Archive | Streaming Replica | Retention (Full) | Retention (WAL) |
|------|-----------|:---------:|:-----------:|:-----------------:|:----------------:|:---------------:|
| **Tier 1** (Ring 0 Trust) | `tshepo`, `keycloak`, `tshepo_*` | Daily | Continuous | Synchronous | 90 days | 7 days |
| **Tier 2** (Ring 0 Registry) | `vito`, `varapi`, `tuso`, `zibo`, `msika`, `butano`, `mushex` | Daily | Continuous | Synchronous | 90 days | 7 days |
| **Tier 3** (Ring 1 Clinical) | `ubomi`, `pct`, `oros` | Daily | Continuous | Async | 365 days | 30 days |
| **Tier 4** (Ring 1 Operational) | `pharmacy`, `inpatient`, `inventory`, `costing`, Landela DBs | Daily | N/A | N/A | 90 days | N/A |
| **Tier 5** (Ring 2 Platform) | `document_service`, `notification`, `jobs`, `offline_sync`, `integration_hub`, `experience_bff`, `impilo_forms`, `impilo_search` | Daily | N/A | N/A | 30 days | N/A |

### 2.4 Backup Schedule (K8s CronJob)

```yaml
# Tier 1 + Tier 2: Daily at 02:00 UTC (off-peak for Zimbabwe = 04:00 CAT)
schedule: "0 2 * * *"

# Tier 3 + Tier 4: Daily at 03:00 UTC
schedule: "0 3 * * *"

# Tier 5: Daily at 04:00 UTC
schedule: "0 4 * * *"

# WAL archiving: Continuous (PostgreSQL archive_command)
# Streaming replication: Continuous (PostgreSQL replication slot)
```

---

## 3. Kafka Backup Strategy

### 3.1 Design Principle

Kafka is an **event transport**, not a source of truth. All domain state originates in PostgreSQL and is published via the transactional outbox pattern. Therefore:

- **Kafka topics are rebuildable** from service databases by replaying outbox tables.
- **MirrorMaker 2 replication** provides cross-cluster topic mirroring for DR, not as a primary backup mechanism.
- **Topic retention** provides built-in short-term recovery (consumers can re-read from offset).

### 3.2 Topic Retention Policy

| Topic Category | Retention | Compaction | Example Topics |
|---------------|-----------|:----------:|----------------|
| Platform audit events | 30 days | No | `platform.audit.events`, `tshepo.audit.events` |
| Clinical events | 14 days | No | `pct.encounter.started`, `oros.order.placed` |
| Pharmacy/inventory events | 14 days | No | `pharmacy.dispense.complete`, `inventory.ledger.event.created` |
| Financial events | 30 days | No | `mushex.payment.status.changed`, `costa.bill.finalized` |
| Control channels | 7 days | Yes | `impilo.control.revocation.v1` |
| IoT telemetry | 3 days | No | `impilo.iot.telemetry.device.raw` |

### 3.3 MirrorMaker 2 Configuration (DR Cluster)

```yaml
# MirrorMaker 2 — replicate Ring 0 topics to DR cluster
apiVersion: kafka.strimzi.io/v1beta2
kind: KafkaMirrorMaker2
metadata:
  name: impilo-dr-mirror
spec:
  version: 3.7.0
  replicas: 2
  connectCluster: dr-cluster
  clusters:
    - alias: primary
      bootstrapServers: primary-kafka:9092
    - alias: dr-cluster
      bootstrapServers: dr-kafka:9092
  mirrors:
    - sourceCluster: primary
      targetCluster: dr-cluster
      sourceConnector:
        config:
          replication.factor: 3
          offset-syncs.topic.replication.factor: 3
          sync.topic.acls.enabled: false
      topicsPattern: "platform\\..*|tshepo\\..*|pct\\..*|oros\\..*"
      groupsPattern: ".*"
```

### 3.4 Kafka Recovery Procedure

If Kafka state is lost entirely:

1. **Deploy fresh Kafka cluster** (KRaft mode, no ZooKeeper dependency)
2. **Recreate topics** with correct partition counts and retention
3. **Replay outbox tables** from each service database:
   ```sql
   -- Mark all published events as unpublished to trigger replay
   UPDATE <schema>.event_outbox SET published_at = NULL
   WHERE published_at IS NOT NULL
   AND created_at >= '<cutoff_timestamp>';
   ```
4. **Restart outbox publisher** in each service — events will be re-published to Kafka
5. **Consumer groups** re-read from earliest available offset

**Key insight:** Because Kafka is not the source of truth, Kafka data loss is a **RTO problem** (how fast can we restore the event stream) not an **RPO problem** (no domain data is lost — it's in PostgreSQL).

---

## 4. MinIO / Object Storage Backup Strategy

### 4.1 Buckets and Usage

| Bucket | Service | Content | Sensitivity |
|--------|---------|---------|-------------|
| `impilo-documents` | document-service | Patient documents, scanned forms | HIGH (may contain PII) |
| `impilo-pacs` | Orthanc PACS | Medical imaging (DICOM) | HIGH (clinical data) |
| `impilo-exports` | Various | Report exports, bulk data | MEDIUM |
| `impilo-backups` | DR scripts | Database backups, WAL archives | CRITICAL (recovery artifacts) |

### 4.2 Replication Strategy

```bash
# Continuous mirror from primary MinIO to DR MinIO
# Executed by scripts/dr/backup-all.sh (minio section)
mc mirror --watch --overwrite \
  primary/impilo-documents dr/impilo-documents

mc mirror --watch --overwrite \
  primary/impilo-pacs dr/impilo-pacs
```

**Retention:** Matches source bucket lifecycle policy. Document retention follows Zimbabwe MoHCC regulatory requirements (minimum 7 years for patient records).

---

## 5. Keycloak Backup Strategy

### 5.1 Dual Backup Approach

Keycloak state is backed up two ways for defense in depth:

1. **Database backup:** The `keycloak` database is included in the Tier 1 pg_dump cycle.
2. **Realm export:** A JSON export of the `impilo` realm captures realm configuration, client definitions, roles, and authentication flows (but not user credentials, which are in the database).

```bash
# Realm export — executed daily by backup-all.sh
/opt/keycloak/bin/kc.sh export \
  --dir /tmp/keycloak-export \
  --realm impilo \
  --users skip
```

### 5.2 Keycloak Recovery Priority

Keycloak is **Tier 1** (Ring 0 Trust) because TSHEPO depends on it for token validation. If Keycloak is unavailable, no new tokens can be issued — existing tokens continue to work until expiry (typically 5–15 minutes).

**Recovery sequence:**
1. Restore `keycloak` database from backup
2. Start Keycloak with restored database
3. Verify realm configuration via admin console
4. If realm config is corrupted, import from JSON export
5. Verify token issuance: `curl -X POST .../realms/impilo/protocol/openid-connect/token`

---

## 6. Configuration and Artifact Backup

### 6.1 Source of Truth: Git Repository

All application code, Flyway migrations, Helm charts, and infrastructure configuration are versioned in the Git repository. The repository itself is the backup for:

- Service source code and configuration (`services/*/src/main/resources/application.yml`)
- Database migrations (`services/*/src/main/resources/db/migration/`)
- Helm chart templates and values (`services/*/helm/`)
- Infrastructure configuration (`infra/envoy/`, `tools/ops/`)
- Operational scripts (`scripts/`)

### 6.2 Kubernetes Secrets

Kubernetes secrets are **not** in Git (by design). They must be backed up separately:

```bash
# Export all secrets in impilo namespace (encrypted)
kubectl get secrets -n impilo -o yaml | \
  kubeseal --format yaml > /tmp/sealed-secrets-export.yaml

# Upload to secure backup storage
aws s3 cp /tmp/sealed-secrets-export.yaml \
  "s3://${BACKUP_BUCKET}/k8s-secrets/${TIMESTAMP}-sealed-secrets.yaml" \
  --sse AES256
```

### 6.3 Helm Release State

```bash
# Capture current Helm release values for all services
for release in $(helm list -n impilo -q); do
  helm get values "${release}" -n impilo -o yaml > \
    "/tmp/helm-values-${release}.yaml"
done
```

---

## 7. Restore Procedures Overview

### 7.1 Full Database Restore (from pg_dump)

```bash
# Executed by scripts/dr/restore-db.sh
# 1. Download backup from S3
aws s3 cp "s3://${BACKUP_BUCKET}/postgres/daily/${DB_NAME}/${BACKUP_FILE}" \
  "/tmp/${BACKUP_FILE}"

# 2. Verify checksum
echo "${EXPECTED_SHA256}  /tmp/${BACKUP_FILE}" | sha256sum -c -

# 3. Drop and recreate database
psql -h "${DB_HOST}" -U "${DB_USER}" -c "DROP DATABASE IF EXISTS ${DB_NAME};"
psql -h "${DB_HOST}" -U "${DB_USER}" -c "CREATE DATABASE ${DB_NAME};"

# 4. Restore
pg_restore -h "${DB_HOST}" -U "${DB_USER}" -d "${DB_NAME}" \
  --no-owner --no-privileges --jobs=4 \
  "/tmp/${BACKUP_FILE}"

# 5. Run Flyway to apply any migrations newer than backup
# (service startup handles this automatically via Spring Boot Flyway auto-config)
```

### 7.2 Point-in-Time Recovery (PITR)

```bash
# 1. Stop PostgreSQL
pg_ctl stop -D "${PGDATA}"

# 2. Restore base backup
pg_basebackup ... # or restore from pg_dump

# 3. Configure recovery
cat > "${PGDATA}/recovery.signal" << 'EOF'
EOF

cat >> "${PGDATA}/postgresql.auto.conf" << EOF
restore_command = 'aws s3 cp s3://${BACKUP_BUCKET}/postgres/wal/%f %p'
recovery_target_time = '${TARGET_TIMESTAMP}'
recovery_target_action = 'promote'
EOF

# 4. Start PostgreSQL — it replays WAL up to target time
pg_ctl start -D "${PGDATA}"
```

### 7.3 Post-Restore Validation

After any restore, the following checks must pass (automated by `scripts/dr/post-restore-verify.sh`):

| Check | Method | Pass Criteria |
|-------|--------|--------------|
| Database connectivity | `psql -c "SELECT 1"` | Returns `1` |
| Schema version | `SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1` | Matches expected version |
| Row count sanity | Compare key table counts against pre-backup baseline | Within 1% of expected |
| Outbox state | `SELECT COUNT(*) FROM event_outbox WHERE published_at IS NULL` | Returns a number (outbox exists and is queryable) |
| Service health | `curl /actuator/health` after service restart | Returns `{"status":"UP"}` |
| Audit chain integrity | Verify hash chain on audit tables (if applicable) | Chain is valid |

---

## 8. Backup Monitoring and Alerting

### 8.1 Backup Health Metrics

| Metric | Source | Alert Threshold |
|--------|--------|----------------|
| `impilo_backup_last_success_timestamp` | Backup CronJob | > 26 hours since last success |
| `impilo_backup_size_bytes` | Backup script | < 50% of previous backup size (possible corruption) |
| `impilo_backup_duration_seconds` | Backup script | > 2× historical average |
| `impilo_wal_archive_lag_bytes` | PostgreSQL `pg_stat_archiver` | > 100 MB (archiver falling behind) |
| `impilo_replication_lag_bytes` | PostgreSQL `pg_stat_replication` | > 10 MB (replica falling behind) |

### 8.2 Alerting Rules

```yaml
groups:
  - name: impilo_backup_alerts
    rules:
      - alert: BackupMissed
        expr: time() - impilo_backup_last_success_timestamp > 93600  # 26 hours
        for: 10m
        labels:
          severity: critical
        annotations:
          summary: "Backup missed for {{ $labels.database }}"
          runbook: "docs/dr/runbooks/db-restore.md"

      - alert: WALArchiveLag
        expr: impilo_wal_archive_lag_bytes > 104857600  # 100 MB
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "WAL archive lag exceeds 100 MB on {{ $labels.instance }}"

      - alert: ReplicationLag
        expr: impilo_replication_lag_bytes > 10485760  # 10 MB
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Replication lag exceeds 10 MB on {{ $labels.instance }}"
```

---

## 9. Restore Decision Tree

```
Incident: Data loss or corruption detected
    │
    ├─ Is it a single database?
    │   ├─ Yes → Is PITR needed (specific point in time)?
    │   │   ├─ Yes → Use pg_restore + WAL replay (PITR)
    │   │   └─ No  → Use pg_restore from latest daily dump
    │   └─ No  → Multiple databases affected?
    │       ├─ Same ring → Restore all databases in ring from same backup timestamp
    │       └─ Cross-ring → Treat as platform-wide recovery (see §9.1)
    │
    ├─ Is Kafka state lost?
    │   ├─ Broker failure (data intact) → KRaft re-elects, automatic recovery
    │   ├─ Topic data lost → Replay from outbox tables
    │   └─ Full cluster loss → Deploy fresh + replay outbox + reset consumer offsets
    │
    ├─ Is MinIO state lost?
    │   ├─ Partial → mc mirror from DR bucket
    │   └─ Full → mc mirror from DR bucket (longer RTO)
    │
    └─ Is Keycloak state lost?
        ├─ Database intact → Restart Keycloak, verify realm
        └─ Database lost → Restore keycloak DB + import realm export
```

### 9.1 Platform-Wide Recovery Sequence

If multiple components fail simultaneously, restore in this order:

1. **PostgreSQL** — all state depends on the database
2. **Keycloak** — required before any authenticated service can start
3. **Kafka** — required for event flow (but services start without it — outbox buffers)
4. **Ring 0 services** — TSHEPO first (gateway), then VITO, VARAPI, TUSO, ZIBO
5. **Ring 1 services** — clinical, then operational
6. **Ring 2 services** — platform support
7. **MinIO** — documents and PACS (not on critical care path)
8. **Redis** — self-heals on restart (cache warming)

---

## 10. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-03-15 | Wave 20 | Initial backup and restore strategy |
