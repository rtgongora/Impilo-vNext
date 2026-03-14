# Runbook: Offline Replay Failure Investigation

## Scope
Investigation and remediation of failed offline action replays in the offline-edge-service.

## Background
The offline-edge-service captures clinical actions performed while disconnected (Class C facilities). When connectivity is restored, actions are replayed via `POST /internal/v1/offline/replay/{entitlement_id}`. Failed replays leave actions in `FAILED` status with a `replay_error` message.

## Detection

### Alerts
- Grafana: `impilo_offline_replay_failed_total` increasing
- Reconciliation batch with status=PARTIAL

### Manual Check
```bash
# List failed actions
curl -s "http://offline-edge:8360/internal/v1/offline/actions?status=FAILED" \
  -H "X-Tenant-ID: <tenant>" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" | jq '.items[] | {actionId, replayError, capturedAt}'
```

### Database Query
```sql
SELECT action_id, entitlement_id, action_type, patient_ref,
       replay_error, captured_at, sequence_num
FROM ofe_captured_actions
WHERE status = 'FAILED'
ORDER BY captured_at DESC
LIMIT 50;
```

## Common Failure Causes

### 1. Expired Entitlement
**Symptom:** `replay_error` contains "Entitlement has expired"
**Resolution:** The entitlement TTL elapsed before replay. This is expected for very long offline periods.
```sql
-- Check entitlement expiry
SELECT entitlement_id, expires_at, revoked
FROM ofe_entitlements
WHERE entitlement_id = '<id>';
```
**Action:** Issue a new entitlement, re-capture actions under it, then replay.

### 2. Revoked Entitlement
**Symptom:** `replay_error` contains "Entitlement is revoked"
**Resolution:** The entitlement was revoked (e.g., device reported stolen). Investigate why revocation occurred before re-issuing.

### 3. Downstream Service Unavailable
**Symptom:** `replay_error` contains connection timeout or HTTP 503
**Resolution:** The target clinical service was down during replay. Wait for service recovery, then re-trigger replay.

### 4. Data Validation Failure
**Symptom:** `replay_error` contains validation errors (missing fields, invalid references)
**Resolution:** The captured payload doesn't meet current validation rules (schema may have changed since capture).
```sql
-- Inspect the payload
SELECT payload_json FROM ofe_captured_actions WHERE action_id = '<id>';
```
**Action:** Manually correct the payload if possible, or mark as permanently failed.

## Remediation

### Re-trigger Replay
```bash
# Re-trigger replay for all queued actions under an entitlement
curl -X POST "http://offline-edge:8360/internal/v1/offline/replay/<entitlement_id>" \
  -H "X-Tenant-ID: <tenant>" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)"
```

### Reset Failed Actions to QUEUED (Manual Intervention)
```sql
-- Only after root cause is resolved
UPDATE ofe_captured_actions
SET status = 'QUEUED', replay_error = NULL, replayed_at = NULL
WHERE status = 'FAILED'
  AND entitlement_id = '<entitlement_id>';
```
Then re-trigger replay.

### Verify Replay Completeness
```sql
SELECT status, COUNT(*) as cnt
FROM ofe_captured_actions
WHERE entitlement_id = '<entitlement_id>'
GROUP BY status;
```
**Expected after successful replay:** All rows show status=REPLAYED.

## Escalation
- If > 100 actions stuck in FAILED for > 24 hours → SEV-2 incident
- If patient data affected → Involve clinical team for manual data reconciliation
- If entitlement token compromise suspected → Revoke all entitlements for the facility and investigate
