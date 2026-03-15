# Wave 20 — Game Day Scenarios

> Date: 2026-03-15
> Scope: Disaster recovery game day exercises for Impilo vNext
> Branch: `claude/review-project-manifest-jb5O0`

---

## 1. Overview

Game days are structured failure injection exercises that validate DR capabilities under realistic conditions. Unlike restore drills (which test backup/restore mechanics), game days test the human + system response to unexpected failures.

### Schedule

| Exercise Type | Frequency | Duration | Participants |
|--------------|-----------|----------|-------------|
| Game day (announced) | Quarterly | Half day | Platform + SRE + clinical systems |
| Game day (surprise) | Bi-annually | Full day | All engineering |

### Ground Rules

1. **Safety first:** Game days run in staging unless explicitly approved for production
2. **Kill switch:** Drill lead can abort at any time by announcing "DRILL STOP"
3. **No cheating:** Participants must use documented runbooks, not undocumented shortcuts
4. **Record everything:** All actions, timestamps, and decisions are logged for post-drill review
5. **Clinical safety:** If any real clinical workflow is at risk, abort immediately

---

## 2. Scenario GD-1: Primary DB Failure for TSHEPO

### Objective

Validate that TSHEPO (authorization gateway) recovers within RTO (≤ 15 min) with zero data loss (RPO = 0) when its primary database instance fails.

### Prerequisites

- [ ] Staging environment with TSHEPO running and processing requests
- [ ] PostgreSQL streaming replica configured for TSHEPO database
- [ ] Load generator running baseline traffic against Envoy → TSHEPO
- [ ] Prometheus/Grafana monitoring dashboard open
- [ ] On-call SRE available
- [ ] Drill report template ready (`docs/dr/dr-drill-evidence-pack-template.md`)

### Injection Method

```bash
# Option A: Kill the PostgreSQL primary pod (K8s)
kubectl delete pod postgres-primary-0 -n impilo --grace-period=0 --force

# Option B: Simulate disk failure (if using local volumes)
kubectl exec -n impilo postgres-primary-0 -- chmod 000 /var/lib/postgresql/data

# Option C: Network isolation (tc netem)
kubectl exec -n impilo postgres-primary-0 -- \
  tc qdisc add dev eth0 root netem loss 100%
```

### Expected Behavior

| Time | Expected Event |
|------|---------------|
| T+0 | DB primary killed |
| T+0–10s | TSHEPO starts returning 500s (connection refused to DB) |
| T+10–30s | Monitoring detects TSHEPO health degradation (BurnCritical alert) |
| T+30s–2m | Patroni detects primary failure, begins failover |
| T+2–5m | Replica promoted to primary; TSHEPO reconnects via connection pool refresh |
| T+5–10m | TSHEPO returns to healthy state; error rate drops to 0 |
| T+15m | Full verification confirms zero data loss |

### Success Criteria

| Criterion | Target | Measurement |
|-----------|--------|-------------|
| **RTO** | ≤ 15 min | Time from injection to TSHEPO `/actuator/health` returning 200 |
| **RPO** | 0 min | No committed transactions lost (verify via `SELECT MAX(id) FROM event_outbox` before and after) |
| **Detection** | ≤ 2 min | Time from injection to first alert firing |
| **Auto-recovery** | Yes | Patroni promoted replica without manual intervention |
| **Error rate** | Recovers to < 0.1% | Prometheus error rate within 5 min of recovery |

### Validation Queries

```sql
-- Pre-injection: record watermarks
SELECT MAX(id) AS max_outbox_id FROM tshepo.event_outbox;
SELECT COUNT(*) AS total_rows FROM tshepo.event_outbox;

-- Post-recovery: verify no data loss
SELECT MAX(id) AS max_outbox_id FROM tshepo.event_outbox;
-- Must be >= pre-injection value

-- Verify all events from test period are intact
SELECT COUNT(*) FROM tshepo.event_outbox
WHERE created_at >= '<injection_time>'
AND created_at <= '<recovery_time>';
```

---

## 3. Scenario GD-2: Kafka Network Partition

### Objective

Validate that clinical care operations continue when Kafka is unreachable, and that events are delivered without duplicates once connectivity is restored.

### Prerequisites

- [ ] Staging environment with Ring 0 services running
- [ ] Load generator producing clinical transactions (registrations, encounters)
- [ ] Kafka consumer lag monitoring in place
- [ ] Outbox lag metrics visible in Grafana

### Injection Method

```bash
# Option A: Network partition via iptables on Kafka pods
for pod in $(kubectl get pods -n impilo -l app=kafka -o name); do
  kubectl exec -n impilo "${pod}" -- \
    iptables -A INPUT -p tcp --dport 9092 -j DROP
  kubectl exec -n impilo "${pod}" -- \
    iptables -A OUTPUT -p tcp --sport 9092 -j DROP
done

# Option B: Pause Kafka containers (Docker Compose)
docker pause kafka

# Option C: Scale Kafka to 0 (K8s)
kubectl scale statefulset kafka -n impilo --replicas=0
```

### Expected Behavior

| Time | Expected Event |
|------|---------------|
| T+0 | Kafka partitioned from services |
| T+0–30s | Outbox publisher detects Kafka connection failure |
| T+30s | Events start accumulating in `event_outbox` tables |
| T+1–5m | OutboxLagWarning alert fires (> 50 unpublished for TSHEPO) |
| T+5m | **Clinical operations continue normally** — writes succeed, reads succeed |
| T+10m | OutboxLagCritical alert fires (> 200 unpublished for TSHEPO) |
| T+15m | Restore Kafka connectivity |
| T+15–20m | Outbox publisher drains backlog; events delivered to Kafka |
| T+25m | Outbox lag returns to 0; consumer groups catch up |

### Success Criteria

| Criterion | Target | Measurement |
|-----------|--------|-------------|
| **Care path availability** | 100% during partition | TUSO read + VITO write requests succeed with non-5xx throughout |
| **Outbox buffering** | Events accumulate, not lost | `COUNT(*) FROM event_outbox WHERE published_at IS NULL` increases during partition |
| **No data loss** | All events eventually delivered | Post-recovery: all events have `published_at` set |
| **No duplicates** | Idempotent event delivery | Consumer-side: no duplicate event IDs (check `idempotency_key` column) |
| **Drain time** | < 10 min for backlog to clear | Outbox lag returns to normal within 10 min of Kafka restoration |

### Validation Script

```bash
#!/usr/bin/env bash
# Run during Kafka partition to prove care path works

echo "=== CARE PATH DURING KAFKA PARTITION ==="

# Attempt 20 TUSO reads (terminology lookup)
TUSO_OK=0; TUSO_FAIL=0
for i in $(seq 1 20); do
  status=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    http://localhost:8084/actuator/health 2>/dev/null)
  [ "${status}" = "200" ] && TUSO_OK=$((TUSO_OK+1)) || TUSO_FAIL=$((TUSO_FAIL+1))
done
echo "TUSO reads: ${TUSO_OK}/20 OK, ${TUSO_FAIL}/20 failed"

# Attempt 20 VITO reads (patient lookup)
VITO_OK=0; VITO_FAIL=0
for i in $(seq 1 20); do
  status=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    http://localhost:8082/actuator/health 2>/dev/null)
  [ "${status}" = "200" ] && VITO_OK=$((VITO_OK+1)) || VITO_FAIL=$((VITO_FAIL+1))
done
echo "VITO reads: ${VITO_OK}/20 OK, ${VITO_FAIL}/20 failed"

# Check outbox accumulation
echo ""
echo "=== OUTBOX STATE ==="
for db in tshepo vito varapi tuso zibo; do
  count=$(psql -h "${DB_HOST}" -U impilo -d "${db}" \
    -tAc "SELECT COUNT(*) FROM event_outbox WHERE published_at IS NULL;" 2>/dev/null || echo "N/A")
  echo "  ${db}: ${count} unpublished events"
done

echo ""
echo "Result: care path should show 100% availability despite Kafka outage"
```

### Teardown

```bash
# Restore Kafka connectivity
# Option A: Remove iptables rules
for pod in $(kubectl get pods -n impilo -l app=kafka -o name); do
  kubectl exec -n impilo "${pod}" -- iptables -F
done

# Option B: Unpause containers
docker unpause kafka

# Option C: Scale back up
kubectl scale statefulset kafka -n impilo --replicas=3
```

---

## 4. Scenario GD-3: Corrupt Backup Recovery

### Objective

Validate that the team can detect a corrupted backup and recover using an alternate backup, within 30 minutes.

### Prerequisites

- [ ] At least 2 daily backups available for VITO database in S3/MinIO
- [ ] Latest backup file accessible
- [ ] DR restore scripts available

### Injection Method

```bash
# 1. Create a genuinely corrupted backup
LATEST_BACKUP=$(aws s3 ls "s3://impilo-backups/postgres/daily/vito/" | \
  grep '\.dump$' | sort | tail -1 | awk '{print $4}')

# Download and corrupt
aws s3 cp "s3://impilo-backups/postgres/daily/vito/${LATEST_BACKUP}" /tmp/corrupted.dump
# Truncate to 50% of original size (simulates incomplete upload)
truncate -s 50% /tmp/corrupted.dump
# Upload corrupted version back (to a drill-specific path)
aws s3 cp /tmp/corrupted.dump \
  "s3://impilo-backups/postgres/drill/vito/${LATEST_BACKUP}"

# 2. Simulate VITO database failure
psql -h "${DB_HOST}" -U impilo -d postgres \
  -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='vito';"
psql -h "${DB_HOST}" -U impilo -d postgres -c "DROP DATABASE vito;"
```

### Expected Behavior

| Time | Expected Event |
|------|---------------|
| T+0 | VITO database dropped (simulating failure) |
| T+0–2m | Team detects VITO is down |
| T+2–5m | Team attempts restore from "latest" backup (corrupted) |
| T+5–8m | pg_restore fails or checksum verification fails → team recognizes corruption |
| T+8–15m | Team locates the previous day's backup (known good) |
| T+15–25m | Restore from previous day's backup succeeds |
| T+25–30m | VITO service restarted and verified healthy |

### Success Criteria

| Criterion | Target | Measurement |
|-----------|--------|-------------|
| **Corruption detection** | < 5 min | Team recognizes the backup is corrupt (via checksum failure or pg_restore error) |
| **Alternate backup located** | < 5 min | Team finds and downloads the second-latest backup |
| **Total RTO** | ≤ 30 min | Time from database loss to VITO healthy |
| **RPO** | ≤ 24 hr | Data loss limited to events since the previous day's backup (worst case) |

---

## 5. Scenario GD-4: Simultaneous Ring 0 Service Loss

### Objective

Validate recovery when two critical Ring 0 services (TSHEPO + VITO) fail simultaneously, testing the team's ability to prioritize and parallelize recovery.

### Prerequisites

- [ ] Staging environment with full Ring 0 running
- [ ] Backups available for both TSHEPO and VITO databases
- [ ] At least 2 engineers available (parallel recovery)

### Injection Method

```bash
# Simultaneously kill TSHEPO and VITO databases
psql -h "${DB_HOST}" -U impilo -d postgres -c "
  SELECT pg_terminate_backend(pid) FROM pg_stat_activity
  WHERE datname IN ('tshepo', 'vito');"
psql -h "${DB_HOST}" -U impilo -d postgres -c "DROP DATABASE tshepo;"
psql -h "${DB_HOST}" -U impilo -d postgres -c "DROP DATABASE vito;"
```

### Expected Behavior

| Time | Expected Event |
|------|---------------|
| T+0 | Both databases dropped |
| T+0–1m | All API requests fail (TSHEPO ext_authz down = total outage) |
| T+1–2m | Team detects dual failure; declares SEV-1 |
| T+2–5m | **Priority decision:** TSHEPO first (unblocks all services) |
| T+5–10m | Engineer 1: restoring TSHEPO database; Engineer 2: preparing VITO backup |
| T+10–15m | TSHEPO restored and healthy → partial platform recovery (auth works, MPI still down) |
| T+12–20m | VITO restored and healthy → full Ring 0 operational |

### Success Criteria

| Criterion | Target | Measurement |
|-----------|--------|-------------|
| **TSHEPO RTO** | ≤ 15 min | Time from failure to TSHEPO healthy |
| **VITO RTO** | ≤ 20 min | Time from failure to VITO healthy |
| **Correct prioritization** | TSHEPO first | Team restores TSHEPO before VITO (documented in drill log) |
| **Parallel execution** | Yes | Both restores overlap where possible |

---

## 6. Scenario GD-5: Full AZ Failure Simulation

### Objective

Validate that the platform can operate at reduced capacity when an entire availability zone is lost, and that no data is lost.

### Prerequisites

- [ ] Multi-AZ Kubernetes cluster (at least 2 AZs)
- [ ] PostgreSQL replication across AZs
- [ ] Services scheduled across AZs (pod anti-affinity)

### Injection Method

```bash
# Drain all pods from AZ-1 nodes
AZ1_NODES=$(kubectl get nodes -l topology.kubernetes.io/zone=az-1 -o name)
for node in ${AZ1_NODES}; do
  kubectl drain "${node}" --ignore-daemonsets --delete-emptydir-data --force --timeout=60s
done

# Cordon nodes to prevent rescheduling back
for node in ${AZ1_NODES}; do
  kubectl cordon "${node}"
done
```

### Expected Behavior

| Time | Expected Event |
|------|---------------|
| T+0 | AZ-1 nodes drained |
| T+0–2m | Pods rescheduled to AZ-2 nodes |
| T+2–5m | If DB primary was in AZ-1: Patroni promotes AZ-2 replica |
| T+5m | All services running (at reduced replica count) |
| T+5–10m | Load test confirms platform handles traffic at 50% capacity |

### Success Criteria

| Criterion | Target | Measurement |
|-----------|--------|-------------|
| **Service availability** | All Ring 0 services UP within 5 min | Health endpoints return 200 |
| **Data loss** | Zero | All outbox events intact; replication lag was 0 before AZ loss |
| **Capacity** | ≥ 50% of normal | Load test at 50% baseline throughput passes SLO thresholds |

### Teardown

```bash
# Uncordon AZ-1 nodes
for node in ${AZ1_NODES}; do
  kubectl uncordon "${node}"
done

# Pods will gradually rebalance
```

---

## 7. Scenario GD-6: Keycloak Realm Corruption

### Objective

Validate that the team can restore a corrupted Keycloak realm from export within 15 minutes, before existing tokens expire.

### Prerequisites

- [ ] Keycloak realm export available (`impilo` realm JSON)
- [ ] Knowledge of current client configurations
- [ ] Timer: existing tokens expire in 5–15 min

### Injection Method

```bash
# Get admin token
ADMIN_TOKEN=$(curl -s -X POST \
  "http://localhost:8080/realms/master/protocol/openid-connect/token" \
  -d "grant_type=password&client_id=admin-cli&username=admin&password=${KC_ADMIN_PASS}" \
  | jq -r .access_token)

# Delete a critical client (tshepo-service) to simulate corruption
curl -X DELETE "http://localhost:8080/admin/realms/impilo/clients/<TSHEPO_CLIENT_UUID>" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"

# Or: corrupt the realm by changing the token signing key
# This invalidates all existing tokens immediately
```

### Expected Behavior

| Time | Expected Event |
|------|---------------|
| T+0 | Realm corrupted (client deleted or signing key changed) |
| T+0–2m | New token requests for deleted client fail |
| T+0–15m | Existing tokens still work (if signing key unchanged) |
| T+2–5m | Team detects auth failure; identifies Keycloak as root cause |
| T+5–10m | Team restores realm from JSON export or database backup |
| T+10–15m | Keycloak realm restored; new tokens issue successfully |

### Success Criteria

| Criterion | Target | Measurement |
|-----------|--------|-------------|
| **RTO** | ≤ 15 min | Realm restored before token expiry |
| **Recovery method** | Realm import or DB restore | Documented in drill report |
| **Token issuance** | Works post-recovery | `curl -X POST .../token` returns valid JWT |

### Recovery Options

```bash
# Option A: Import realm from JSON export (preferred — faster)
/opt/keycloak/bin/kc.sh import --file /backups/impilo-realm.json --override true
# Restart Keycloak to pick up changes
kubectl rollout restart deployment/keycloak -n impilo

# Option B: Restore keycloak database from backup
./scripts/dr/restore-db.sh --db keycloak --from-s3 --force
kubectl rollout restart deployment/keycloak -n impilo
```

---

## 8. Game Day Report Template

After each game day, complete this report:

```markdown
# Game Day Report — [Date] — [Scenario ID]

## Participants
| Role | Name |
|------|------|
| Drill lead | |
| SRE on-call | |
| Platform engineer(s) | |
| Observer(s) | |

## Scenario Executed
- ID: GD-X
- Description: [brief]
- Injection method used: [which option]

## Timeline
| Time | Elapsed | Event | Actor |
|------|---------|-------|-------|
| | T+0 | Failure injected | Drill lead |
| | T+?m | Failure detected | |
| | T+?m | Recovery action started | |
| | T+?m | Service restored | |
| | T+?m | Verification complete | |

## Measurements
- RTO achieved: __ min (target: __ min) → PASS/FAIL
- RPO achieved: __ min (target: __ min) → PASS/FAIL
- Detection time: __ min
- Runbook followed: Yes / No / Partially

## Findings
### What went well
-

### What needs improvement
-

### Action items
| Action | Owner | Due |
|--------|-------|-----|
| | | |

## Verdict
- [ ] PASS — all success criteria met
- [ ] PARTIAL PASS — RTO/RPO met, but process improvements needed
- [ ] FAIL — RTO/RPO breached; remediation required before next drill
```

---

## 9. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-03-15 | Wave 20 | Initial game day scenarios (6 scenarios) |
