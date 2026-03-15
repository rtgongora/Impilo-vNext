# Runbook: Outbox Event Publication Backlog

> Scope: Ring 0 services with `event_outbox` table (TSHEPO, VITO, VARAPI, TUSO, ZIBO)
> Triggers: `Ring0OutboxLagWarning`, `Ring0OutboxLagCritical`, `TshepoOutboxLagWarning`, `TshepoOutboxLagCritical`

---

## 1. When This Runbook Applies

Use this runbook when the `impilo_ops_outbox_lag` metric (or `opsOutboxHealth` indicator) shows unpublished events accumulating in the outbox table beyond SLO thresholds.

| Service | Warning Threshold | Critical Threshold |
|---------|:-----------------:|:------------------:|
| TSHEPO | > 50 events | > 200 events |
| VITO | > 100 events | > 500 events |
| VARAPI | > 100 events | > 500 events |
| TUSO | > 100 events | > 500 events |
| ZIBO | > 100 events | > 500 events |

**Key principle:** Outbox backlog does NOT block care-path operations. The outbox pattern is designed to decouple writes from event publication. However, extended backlogs indicate downstream systems (audit, analytics, SHR sync) are receiving stale data.

---

## 2. Impact Assessment

| Lag Duration | Impact |
|-------------|--------|
| < 1 min | Minimal — transient burst, normal catch-up expected |
| 1–5 min | VITO freshness SLO (5 s) breached — downstream consumers see stale MPI data |
| 5–30 min | VARAPI freshness SLO (30 s) breached — provider registry stale |
| > 1 hour | Audit chain gap — compliance risk; analytics pipeline stale |
| > 24 hours | TUSO/ZIBO freshness SLOs breached; data warehouse significantly behind |

---

## 3. Immediate Diagnosis (First 5 Minutes)

### Step 1: Confirm outbox lag

```bash
# Via Prometheus
curl -s 'http://localhost:9090/api/v1/query?query=impilo_ops_outbox_lag' | jq '.data.result[] | {application: .metric.application, lag: .value[1]}'

# Via actuator health (per service)
curl -s http://localhost:8082/actuator/health | jq '.components.opsOutboxHealth'

# Via direct SQL (VITO example)
PGPASSWORD=changeme psql -h localhost -U impilo -d vito -c "
  SELECT COUNT(*) AS unpublished,
         MIN(created_at) AS oldest_unpublished,
         now() - MIN(created_at) AS max_staleness
  FROM vito.event_outbox
  WHERE published_at IS NULL;
"
```

### Step 2: Check Kafka connectivity

```bash
# Test Kafka broker availability
docker exec -it $(docker ps -qf name=kafka) /opt/kafka/bin/kafka-broker-api-versions.sh \
  --bootstrap-server localhost:9092 2>&1 | head -5

# List topics (verify outbox topics exist)
docker exec -it $(docker ps -qf name=kafka) /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --list | grep impilo

# Check topic lag for a specific consumer group
docker exec -it $(docker ps -qf name=kafka) /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --group data-ingestion-service
```

### Step 3: Check outbox publisher thread

```bash
# Check for publisher errors in logs
kubectl logs -l app=<service>-service --tail=500 --since=30m | grep -i "outbox\|publish\|kafka"

# Docker variant
docker compose -f docker-compose.runtime.yml logs --tail=500 <service> | grep -i "outbox\|publish\|kafka"
```

---

## 4. Root Cause Analysis

### 4.1 Kafka Broker Unreachable

**Symptoms:**
- All services show outbox lag increasing simultaneously
- Kafka health check fails
- Publisher logs show `org.apache.kafka.common.errors.TimeoutException`

**Remediation:**
1. Check Kafka container/pod status
2. Check Kafka disk space: `docker exec kafka df -h /tmp/kraft-combined-logs`
3. If Kafka crashed, restart: `docker compose restart kafka`
4. After Kafka recovery, outbox will drain automatically — monitor lag decreasing
5. Verify no events were lost: compare outbox table `published_at` timestamps with Kafka topic offsets

### 4.2 Outbox Publisher Thread Stopped

**Symptoms:**
- Only ONE service shows lag; others normal
- No publish-related log entries (thread is silently dead)
- Service health otherwise UP

**Remediation:**
1. Restart the affected service: `kubectl rollout restart deployment/<service>-service`
2. If thread dies repeatedly, check for deadlock in publisher code
3. Check `publish_error` column for stuck events:
```sql
SELECT id, aggregate_type, event_type, publish_error, created_at
FROM <schema>.event_outbox
WHERE published_at IS NULL AND publish_error IS NOT NULL
ORDER BY created_at ASC LIMIT 20;
```
4. If specific events are poison (unpublishable), manually mark them:
```sql
-- CAUTION: Only after investigating the error. This skips the event.
UPDATE <schema>.event_outbox
SET published_at = now(), publish_error = 'MANUALLY_SKIPPED: <reason>'
WHERE id = <event_id>;
```

### 4.3 High Write Throughput Exceeding Publisher Rate

**Symptoms:**
- Lag growing steadily but slowly
- Publisher is active (log entries show publishing)
- High request rate on write endpoints

**Remediation:**
1. Check publisher poll interval (`<service>.outbox.poll-interval-ms`) — reduce if >2000 ms
2. Check publisher batch size — increase if possible
3. If sustained, this is a capacity issue: scale horizontally (more pods) or increase poll frequency
4. Short-term: this is safe as long as lag stays below SLO threshold

### 4.4 Database Lock Contention on Outbox Table

**Symptoms:**
- Publisher active but events not transitioning to `published_at IS NOT NULL`
- Long-running transactions holding locks on `event_outbox`

**Diagnosis:**
```sql
-- Check for locks on event_outbox
SELECT pg_stat_activity.pid, pg_stat_activity.query, pg_locks.granted
FROM pg_locks
JOIN pg_class ON pg_locks.relation = pg_class.oid
JOIN pg_stat_activity ON pg_locks.pid = pg_stat_activity.pid
WHERE pg_class.relname = 'event_outbox';
```

**Remediation:**
1. Terminate blocking transaction: `SELECT pg_terminate_backend(<pid>);`
2. Investigate what caused the long transaction (typically a slow domain write)

---

## 5. Manual Replay Procedure

If events were skipped or the publisher needs assistance, manual replay can be triggered:

```bash
# Method 1: Reset published_at to re-publish events
PGPASSWORD=changeme psql -h localhost -U impilo -d <db-name> -c "
  UPDATE <schema>.event_outbox
  SET published_at = NULL, publish_error = NULL
  WHERE id BETWEEN <start_id> AND <end_id>
  AND published_at IS NOT NULL;
"
# The outbox publisher will pick these up on its next poll cycle.

# Method 2: Restart the service (resets publisher state)
kubectl rollout restart deployment/<service>-service
```

**Warning:** Replayed events must be idempotent. All downstream consumers should handle duplicate events gracefully (they use `idempotency_key` for deduplication).

---

## 6. Verification After Resolution

```bash
# Confirm lag is decreasing
watch -n 5 'curl -s "http://localhost:9090/api/v1/query?query=impilo_ops_outbox_lag" | jq ".data.result[] | {app: .metric.application, lag: .value[1]}"'

# Confirm no unpublished events older than 5 minutes
PGPASSWORD=changeme psql -h localhost -U impilo -d <db-name> -c "
  SELECT COUNT(*) FROM <schema>.event_outbox
  WHERE published_at IS NULL AND created_at < now() - interval '5 minutes';
"
# Expected result: 0
```

---

## 7. Escalation

| Lag Level | Time Threshold | Action |
|-----------|---------------|--------|
| Warning | > 15 min at warning | Engage secondary on-call |
| Critical | > 5 min at critical | Engage service owner + SRE lead |
| Critical | > 30 min at critical | Invoke SEV-2 incident; audit chain gap risk |

---

## 8. Related Runbooks

- [service-degradation.md](service-degradation.md) — general service degradation
- [dependency-failure.md](dependency-failure.md) — Kafka/DB infrastructure failure
- [replay-failures.md](../../resilience-ops-platform/runbooks/replay-failures.md) — outbox replay failure diagnosis
