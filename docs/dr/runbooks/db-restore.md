# Runbook: Database Restore

> Scope: PostgreSQL database restore for any Impilo vNext service
> Severity trigger: SEV-1 (Ring 0 database loss), SEV-2 (Ring 1/2 database loss)
> RPO/RTO reference: [RPO/RTO Matrix](../rpo-rto-matrix.md)

---

## 1. When to Use This Runbook

- Database corruption detected (checksum failures, inconsistent reads)
- Accidental data deletion (DROP TABLE, TRUNCATE, bad migration)
- Storage failure (disk loss, EBS volume failure)
- Database instance unrecoverable after crash
- Planned restore for DR drill

---

## 2. Prerequisites

- [ ] Access to PostgreSQL backup storage (S3/MinIO bucket: `impilo-backups`)
- [ ] PostgreSQL client tools (`psql`, `pg_restore`, `pg_basebackup`)
- [ ] Network access to target PostgreSQL instance
- [ ] Database superuser credentials (`impilo` user or equivalent)
- [ ] DR scripts available: `scripts/dr/restore-db.sh`, `scripts/dr/post-restore-verify.sh`

---

## 3. Identify the Situation

### 3.1 Determine Scope

```bash
# Check which databases are affected
for db in tshepo vito varapi tuso zibo msika butano mushex pct oros; do
  status=$(psql -h "${DB_HOST}" -U impilo -d "${db}" -tAc "SELECT 1;" 2>&1)
  echo "${db}: ${status}"
done
```

### 3.2 Classify Severity

| Condition | Severity | RPO Target | RTO Target |
|-----------|:--------:|:----------:|:----------:|
| TSHEPO or Keycloak database lost | SEV-1 | 0 min | ≤ 15 min |
| VITO, VARAPI, TUSO, or ZIBO database lost | SEV-1 | ≤ 5 min | ≤ 15 min |
| MSIKA, BUTANO, or MUSHEX database lost | SEV-2 | ≤ 5 min | ≤ 30 min |
| Ring 1 clinical (PCT, OROS) database lost | SEV-2 | ≤ 5 min | ≤ 30 min |
| Ring 1 operational database lost | SEV-3 | ≤ 1 hr | ≤ 1 hr |
| Ring 2 database lost | SEV-3 | ≤ 4 hr | ≤ 2 hr |

---

## 4. Restore Procedures

### 4.1 Option A: Restore from Daily Backup (pg_dump)

**Use when:** Full database replacement is needed. Simplest option.

```bash
# Automated restore
./scripts/dr/restore-db.sh --db <DATABASE> --from-s3

# Or with a specific backup date
./scripts/dr/restore-db.sh --db <DATABASE> --from-s3 --backup-date 20260315

# Or from a local file
./scripts/dr/restore-db.sh --db <DATABASE> --file /path/to/backup.dump
```

**Manual steps if script is unavailable:**

```bash
# 1. List available backups
aws s3 ls "s3://impilo-backups/postgres/daily/${DATABASE}/" | tail -5

# 2. Download latest backup
BACKUP_FILE=$(aws s3 ls "s3://impilo-backups/postgres/daily/${DATABASE}/" | \
  grep '\.dump$' | sort | tail -1 | awk '{print $4}')
aws s3 cp "s3://impilo-backups/postgres/daily/${DATABASE}/${BACKUP_FILE}" /tmp/

# 3. Verify checksum
aws s3 cp "s3://impilo-backups/postgres/daily/${DATABASE}/${BACKUP_FILE}.sha256" /tmp/
sha256sum -c "/tmp/${BACKUP_FILE}.sha256"

# 4. Terminate existing connections
psql -h "${DB_HOST}" -U impilo -d postgres \
  -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity
      WHERE datname = '${DATABASE}' AND pid <> pg_backend_pid();"

# 5. Drop and recreate
psql -h "${DB_HOST}" -U impilo -d postgres -c "DROP DATABASE IF EXISTS ${DATABASE};"
psql -h "${DB_HOST}" -U impilo -d postgres -c "CREATE DATABASE ${DATABASE};"

# 6. Restore
pg_restore -h "${DB_HOST}" -U impilo -d "${DATABASE}" \
  --no-owner --no-privileges --jobs=4 "/tmp/${BACKUP_FILE}"
```

### 4.2 Option B: Point-in-Time Recovery (PITR)

**Use when:** You need to recover to a specific moment (e.g., just before a bad migration ran, or before an accidental DELETE).

**Requires:** WAL archiving enabled and WAL files available for the target time range.

```bash
# 1. Identify the target recovery time
# Example: recover to just before the bad migration at 14:30 UTC
TARGET_TIME="2026-03-15 14:29:59+00"

# 2. Stop PostgreSQL on the target instance
pg_ctl stop -D "${PGDATA}"

# 3. Clear the data directory (back it up first if needed)
mv "${PGDATA}" "${PGDATA}.corrupted"
mkdir -p "${PGDATA}"

# 4. Restore the latest base backup
pg_basebackup -h backup-host -D "${PGDATA}" -U replication -X stream

# 5. Configure PITR
touch "${PGDATA}/recovery.signal"
cat >> "${PGDATA}/postgresql.auto.conf" << EOF
restore_command = 'aws s3 cp s3://impilo-backups/postgres/wal/%f %p'
recovery_target_time = '${TARGET_TIME}'
recovery_target_action = 'promote'
EOF

# 6. Start PostgreSQL
pg_ctl start -D "${PGDATA}"

# 7. Monitor recovery
tail -f "${PGDATA}/log/postgresql-*.log" | grep -i "recovery\|redo"
# Wait for: "database system is ready to accept connections"
```

### 4.3 Option C: Failover to Streaming Replica

**Use when:** Primary instance is unrecoverable but streaming replica is healthy. Fastest option for Ring 0.

```bash
# If using Patroni (recommended for Ring 0):
patronictl -c /etc/patroni.yml failover

# Manual promotion (without Patroni):
# 1. On the replica:
pg_ctl promote -D "${PGDATA}"

# 2. Verify promotion
psql -h replica-host -U impilo -d postgres -c "SELECT pg_is_in_recovery();"
# Should return: f (false = this is now the primary)

# 3. Update application connection strings
# If using K8s service: update the service endpoint
kubectl patch svc postgres-primary -n impilo \
  -p '{"spec":{"selector":{"role":"primary","instance":"replica-1"}}}'
```

---

## 5. Post-Restore Validation

### 5.1 Automated Verification

```bash
./scripts/dr/post-restore-verify.sh --db <DATABASE> --check-service
```

### 5.2 Manual Verification Checklist

| # | Check | Command | Expected |
|---|-------|---------|----------|
| 1 | Connectivity | `psql -d ${DATABASE} -c "SELECT 1"` | Returns `1` |
| 2 | Schema version | `psql -d ${DATABASE} -c "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"` | Matches expected version |
| 3 | Row counts | `psql -d ${DATABASE} -c "SELECT tablename, n_live_tup FROM pg_stat_user_tables ORDER BY n_live_tup DESC LIMIT 10"` | Non-zero, reasonable |
| 4 | Outbox state | `psql -d ${DATABASE} -c "SELECT COUNT(*) FROM event_outbox WHERE published_at IS NULL"` | Returns a number |
| 5 | No invalid indexes | `psql -d ${DATABASE} -c "SELECT COUNT(*) FROM pg_index WHERE NOT indisvalid"` | Returns `0` |
| 6 | Service health | `curl http://localhost:<PORT>/actuator/health` | `{"status":"UP"}` |
| 7 | Prometheus metrics | `curl http://localhost:<PORT>/actuator/prometheus \| head -5` | Metrics returned |

### 5.3 Service Restart After Restore

```bash
# Kubernetes
kubectl rollout restart deployment/<SERVICE>-service -n impilo

# Docker Compose
docker compose -f docker-compose.runtime.yml restart <SERVICE>-service
```

### 5.4 Outbox Replay (if events were lost)

After a restore, some events may have been committed to the outbox but not yet published to Kafka. The outbox publisher will automatically retry unpublished events on service restart. If events were lost (restore from an older backup):

```sql
-- Check for gap: events created after backup timestamp but before failure
SELECT COUNT(*) FROM event_outbox
WHERE created_at > '<backup_timestamp>'
AND published_at IS NOT NULL;
-- These events were published to Kafka but may need re-publication
-- if Kafka consumer offsets were also lost

-- Force re-publication of events in a time range:
UPDATE event_outbox SET published_at = NULL
WHERE created_at >= '<backup_timestamp>'
AND created_at <= '<failure_timestamp>';
```

---

## 6. Escalation

| Elapsed Time | Action |
|:------------:|--------|
| T+0 | Incident declared, on-call SRE begins triage |
| T+5 min | If Ring 0: page platform engineering lead |
| T+15 min | If Ring 0 not restored: escalate to engineering manager |
| T+30 min | If Ring 1 not restored: escalate to clinical systems lead |
| T+1 hr | If any ring not restored: invoke DR war room |

---

## 7. Post-Incident

After restore is confirmed:

1. **Verify data integrity** against last known-good state
2. **Check outbox lag** across all affected services
3. **Monitor error rates** for 30 minutes post-restore
4. **Analyze WAL/backup logs** to determine root cause
5. **Update RPO/RTO verification** status in `rpo-rto-matrix.md`
6. **File post-incident report** per incident triage runbook

---

## 8. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-03-15 | Wave 20 | Initial database restore runbook |
