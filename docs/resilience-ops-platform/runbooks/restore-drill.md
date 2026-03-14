# Runbook: Database Restore Drill

## Scope
Quarterly drill to validate backup integrity and restore procedures for all Impilo vNext PostgreSQL databases.

## Pre-Drill Checklist

- [ ] Identify target environment (staging recommended; never drill on production without approval)
- [ ] Confirm latest backup exists and is accessible
- [ ] Notify team of drill window (minimum 2 hours)
- [ ] Ensure restore target database server is provisioned
- [ ] Document current chain head state for audit-ledger-service

## Procedure

### 1. Locate Latest Backup
```bash
# List available backups
pg_basebackup --list

# Or from backup storage (S3/MinIO)
aws s3 ls s3://impilo-backups/postgres/ --recursive | tail -5
```

### 2. Restore to Staging
```bash
# Create fresh database
createdb -h staging-db -U postgres impilo_restore_drill

# Restore from dump
pg_restore -h staging-db -U postgres -d impilo_restore_drill \
  --no-owner --no-privileges latest_backup.dump
```

### 3. Validate Data Integrity

#### Audit Ledger Chain Verification
```bash
# Verify chain integrity across all tenants
curl -s "http://audit-ledger-staging:8350/internal/v1/audit/chain/verify?from_seq=1&to_seq=999999" \
  -H "X-Tenant-ID: <tenant>" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" | jq .
```
**Expected:** `{"valid": true}`

If chain verification fails, the backup may be corrupted or incomplete. **Do not promote to production.**

#### Row Count Comparison
```sql
-- Compare counts between production and restored DB
SELECT 'sup_tickets' AS tbl, COUNT(*) FROM sup_tickets
UNION ALL
SELECT 'ald_audit_records', COUNT(*) FROM ald_audit_records
UNION ALL
SELECT 'ofe_entitlements', COUNT(*) FROM ofe_entitlements
UNION ALL
SELECT 'ofe_captured_actions', COUNT(*) FROM ofe_captured_actions;
```

#### Immutability Trigger Verification
```sql
-- This MUST fail with "Audit records are immutable"
UPDATE ald_audit_records SET action = 'TAMPERED' WHERE id = 1;

-- This MUST fail with "Audit records are immutable"
DELETE FROM ald_audit_records WHERE id = 1;
```

### 4. Measure Recovery Time
Record the following metrics:
- **RTO (Recovery Time Objective):** Time from "start restore" to "service healthy"
- **RPO (Recovery Point Objective):** Time gap between backup timestamp and current time
- **Chain validation time:** Duration of audit chain verification

### 5. Cleanup
```bash
dropdb -h staging-db -U postgres impilo_restore_drill
```

## Post-Drill Report

Document in a support ticket (category=DRILL):
- Date and participants
- RTO achieved vs. target (< 1 hour)
- RPO achieved vs. target (< 15 minutes)
- Chain integrity: PASS/FAIL
- Immutability triggers: PASS/FAIL
- Issues discovered and remediation actions
