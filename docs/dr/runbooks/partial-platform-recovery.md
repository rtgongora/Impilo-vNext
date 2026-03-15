# Runbook: Partial Platform Recovery

> Scope: Recovery from partial infrastructure loss affecting multiple services or an entire availability zone
> Severity: SEV-1 (Ring 0 affected) or SEV-2 (Ring 1+ only)
> Use case: AZ failure, storage cluster loss, network partition, or cascading failure

---

## 1. When to Use This Runbook

- Multiple Ring 0 services are simultaneously DOWN
- An availability zone is unreachable
- Shared infrastructure component failure affects multiple services (e.g., PostgreSQL cluster, Kafka cluster, network segment)
- Cascading failure detected (one service failure triggers others)
- DR site activation required

---

## 2. Recovery Priority Order

Recovery must follow the dependency chain. Services are restored in strict priority order:

```
Priority 1: Infrastructure Foundation
    ├── PostgreSQL (all state)
    ├── Keycloak (authentication)
    └── Kafka (event transport — but services start without it)

Priority 2: Ring 0 Trust (authorization gateway)
    └── TSHEPO (every request requires ext_authz)

Priority 3: Ring 0 Registry (identity + registries)
    ├── VITO (Master Patient Index — needed for care operations)
    ├── VARAPI (provider/facility registry — needed for clinical context)
    ├── TUSO (terminology — needed for clinical data entry)
    └── ZIBO (tariffs/billing — needed for financial operations)

Priority 4: Ring 0 Extended
    ├── MSIKA (clinical encounters)
    ├── BUTANO (FHIR SHR)
    └── MUSHEX (claims/payer)

Priority 5: Ring 1 Clinical
    ├── PCT (patient care tracker)
    ├── OROS (orders & results)
    └── UBOMI (longitudinal record)

Priority 6: Ring 1 Operational
    ├── Pharmacy, Inventory, Inpatient, Costing
    └── Landela suite (credentials, documents)

Priority 7: Ring 2 Platform
    └── Notification, Jobs, Integration Hub, etc.

Priority 8: Non-critical
    ├── MinIO (documents — not on care path)
    └── Redis (self-healing cache)
```

---

## 3. Assessment Phase (First 5 Minutes)

### 3.1 Rapid Situational Assessment

```bash
#!/usr/bin/env bash
# Quick platform health scan — run this first
echo "=== INFRASTRUCTURE ==="
echo -n "PostgreSQL: "; psql -h "${DB_HOST}" -U impilo -d postgres -tAc "SELECT 'UP'" 2>/dev/null || echo "DOWN"
echo -n "Keycloak:   "; curl -s -o /dev/null -w "%{http_code}" --max-time 3 http://localhost:8080/health/ready 2>/dev/null || echo "000"
echo -n "Kafka:      "; kafka-broker-api-versions.sh --bootstrap-server localhost:9092 2>/dev/null | head -1 || echo "UNREACHABLE"
echo -n "Redis:      "; redis-cli -h localhost ping 2>/dev/null || echo "DOWN"
echo -n "Envoy:      "; curl -s -o /dev/null -w "%{http_code}" --max-time 3 http://localhost:9901/ready 2>/dev/null || echo "000"

echo ""
echo "=== RING 0 SERVICES ==="
for entry in tshepo:8081 vito:8082 varapi:8083 tuso:8084 zibo:8085; do
  name=${entry%:*}; port=${entry#*:}
  status=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "http://localhost:${port}/actuator/health" 2>/dev/null || echo "000")
  echo "  ${name}: HTTP ${status}"
done

echo ""
echo "=== RING 0 EXTENDED ==="
for entry in msika:8086 mushex:8087 butano:8090; do
  name=${entry%:*}; port=${entry#*:}
  status=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "http://localhost:${port}/actuator/health" 2>/dev/null || echo "000")
  echo "  ${name}: HTTP ${status}"
done

echo ""
echo "=== RING 1 ==="
for entry in pct:8088 oros:8089; do
  name=${entry%:*}; port=${entry#*:}
  status=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "http://localhost:${port}/actuator/health" 2>/dev/null || echo "000")
  echo "  ${name}: HTTP ${status}"
done
```

### 3.2 Classify the Failure Mode

| Failure Mode | Indicators | Recovery Path |
|-------------|-----------|--------------|
| **AZ failure** | All pods in one AZ unreachable; DB replicas in other AZ healthy | §4.1 AZ Failover |
| **Database cluster failure** | PostgreSQL unreachable; all services return 500 | §4.2 Database Cluster Recovery |
| **Network partition** | Some services reachable, others not; cross-AZ communication broken | §4.3 Network Partition Recovery |
| **Cascading failure** | Service failures spreading; circuit breakers tripping | §4.4 Cascading Failure Containment |
| **Storage failure** | EBS/PVC errors; databases cannot write | §4.5 Storage Recovery |

---

## 4. Recovery Procedures

### 4.1 AZ Failover

**Scenario:** One of two availability zones is completely unreachable.

```bash
# 1. Confirm AZ failure
kubectl get nodes -o wide | grep -E "ZONE|NotReady"

# 2. Check if DB primary was in the failed AZ
psql -h "${DB_HOST}" -U impilo -d postgres -c "SELECT inet_server_addr();"
# If unreachable, the replica in the surviving AZ must be promoted

# 3. Promote replica (if primary was in failed AZ)
# With Patroni:
patronictl -c /etc/patroni.yml failover
# Without Patroni: see docs/dr/runbooks/ring0-failover.md §4.4

# 4. Verify services in surviving AZ can reach new primary
kubectl get pods -n impilo -o wide | grep Running

# 5. Services in failed AZ will be rescheduled by K8s
# If using PodDisruptionBudgets:
kubectl get pdb -n impilo

# 6. Force reschedule stuck pods
for pod in $(kubectl get pods -n impilo --field-selector status.phase=Pending -o name); do
  kubectl delete "${pod}" -n impilo --grace-period=30
done

# 7. Verify all Ring 0 services are running (at reduced capacity)
kubectl get deployments -n impilo -l ring=0
```

### 4.2 Database Cluster Recovery

**Scenario:** PostgreSQL is completely unreachable. All services are failing.

```bash
# This is the most critical failure mode. Follow strictly in order.

# PHASE 1: Restore database (T+0 to T+15 min)
echo "Phase 1: Database restoration"

# Option A: If a streaming replica exists
patronictl -c /etc/patroni.yml list
# If a replica is healthy, promote it (see ring0-failover.md §4.4)

# Option B: If no replica, restore from backup
# Restore Ring 0 Trust databases first
for db in tshepo keycloak; do
  ./scripts/dr/restore-db.sh --db "${db}" --from-s3 --force
done

# PHASE 2: Start critical services (T+15 to T+20 min)
echo "Phase 2: Start TSHEPO + Keycloak"
kubectl rollout restart deployment/keycloak -n impilo
kubectl rollout status deployment/keycloak -n impilo --timeout=120s

kubectl rollout restart deployment/tshepo-service -n impilo
kubectl rollout status deployment/tshepo-service -n impilo --timeout=120s

# Verify authorization works
curl -s http://localhost:8081/actuator/health | jq .status

# PHASE 3: Restore Ring 0 Registry (T+20 to T+30 min)
echo "Phase 3: Ring 0 Registry databases"
for db in vito varapi tuso zibo; do
  ./scripts/dr/restore-db.sh --db "${db}" --from-s3 --force
done

for svc in vito varapi tuso zibo; do
  kubectl rollout restart deployment/${svc}-service -n impilo
done

# PHASE 4: Verify Ring 0 (T+30 to T+35 min)
echo "Phase 4: Ring 0 verification"
./scripts/dr/post-restore-verify.sh --ring 0 --check-service

# PHASE 5: Restore Ring 1 if needed (T+35+)
echo "Phase 5: Ring 1 databases"
for db in msika butano mushex pct oros; do
  ./scripts/dr/restore-db.sh --db "${db}" --from-s3 --force
done
```

### 4.3 Network Partition Recovery

**Scenario:** Services can reach some dependencies but not others.

```bash
# 1. Identify the partition boundary
# Check which services can reach which dependencies
for svc_pod in $(kubectl get pods -n impilo -l ring=0 -o name); do
  echo "--- ${svc_pod} ---"
  kubectl exec -n impilo "${svc_pod}" -- sh -c \
    "nc -z -w2 postgres 5432 && echo 'DB: OK' || echo 'DB: FAIL'"
  kubectl exec -n impilo "${svc_pod}" -- sh -c \
    "nc -z -w2 kafka 9092 && echo 'Kafka: OK' || echo 'Kafka: FAIL'"
  kubectl exec -n impilo "${svc_pod}" -- sh -c \
    "nc -z -w2 keycloak 8080 && echo 'KC: OK' || echo 'KC: FAIL'"
done

# 2. If partition is at the K8s network level:
# Check CNI plugin health
kubectl get pods -n kube-system -l app=calico-node  # or flannel, cilium
kubectl logs -n kube-system -l app=calico-node --tail=20

# 3. If partition is at the cloud/infra level:
# Check security groups, NACLs, route tables
# This requires cloud provider CLI access

# 4. Restart networking components if needed
kubectl delete pods -n kube-system -l app=calico-node

# 5. After partition heals:
# Services will auto-reconnect to dependencies
# Outbox events will drain automatically
# Monitor for duplicate processing (consumers should be idempotent)
```

### 4.4 Cascading Failure Containment

**Scenario:** One service failure is causing others to fail (resource starvation, connection pool exhaustion).

```bash
# 1. Identify the root cause service
# Check which service started failing first
kubectl get events -n impilo --sort-by='.metadata.creationTimestamp' | tail -20

# 2. Isolate the failing service
# Scale down the failing service to prevent it from consuming shared resources
kubectl scale deployment/${FAILING_SERVICE}-service -n impilo --replicas=0

# 3. Let other services recover
# Wait for circuit breakers to reset (typically 30–60 seconds)
sleep 60

# 4. Verify other Ring 0 services have recovered
for entry in tshepo:8081 vito:8082 varapi:8083 tuso:8084 zibo:8085; do
  name=${entry%:*}; port=${entry#*:}
  status=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 \
    "http://localhost:${port}/actuator/health" 2>/dev/null || echo "000")
  echo "  ${name}: HTTP ${status}"
done

# 5. Fix the root cause on the isolated service
# Common causes:
#   - Connection pool leak: restart service
#   - Memory leak: increase limits and restart
#   - Database lock contention: kill blocking queries
psql -h "${DB_HOST}" -U impilo -d "${FAILING_DB}" -c "
  SELECT pid, now() - pg_stat_activity.query_start AS duration, query
  FROM pg_stat_activity
  WHERE state = 'active' AND query NOT ILIKE '%pg_stat%'
  ORDER BY duration DESC LIMIT 5;"

# 6. Bring back the isolated service
kubectl scale deployment/${FAILING_SERVICE}-service -n impilo --replicas=2
```

### 4.5 Storage Recovery

**Scenario:** Persistent Volume Claims (PVCs) are in error state.

```bash
# 1. Check PVC status
kubectl get pvc -n impilo

# 2. If PVC is stuck in Pending:
kubectl describe pvc <PVC_NAME> -n impilo
# Check for storage class availability, quota issues

# 3. If EBS volume is detached:
# Re-attach via cloud provider
aws ec2 attach-volume --volume-id vol-XXX --instance-id i-XXX --device /dev/xvdf

# 4. If volume data is corrupted:
# Restore from snapshot
aws ec2 create-volume --snapshot-id snap-XXX --availability-zone us-east-1a

# 5. After storage recovery:
# PostgreSQL may need crash recovery
pg_ctl start -D "${PGDATA}"
# Monitor logs for: "database system was not properly shut down; automatic recovery in progress"
```

---

## 5. Post-Recovery Validation

### 5.1 Full Platform Verification

```bash
# Run comprehensive verification
./scripts/dr/post-restore-verify.sh --all --check-service

# Check outbox state across all services
echo "=== OUTBOX STATE ==="
for db in tshepo vito varapi tuso zibo msika pct oros; do
  unpub=$(psql -h "${DB_HOST}" -U impilo -d "${db}" \
    -tAc "SELECT COUNT(*) FROM event_outbox WHERE published_at IS NULL;" 2>/dev/null || echo "N/A")
  oldest=$(psql -h "${DB_HOST}" -U impilo -d "${db}" \
    -tAc "SELECT COALESCE(MIN(created_at)::text, 'none') FROM event_outbox WHERE published_at IS NULL;" 2>/dev/null || echo "N/A")
  echo "  ${db}: ${unpub} unpublished (oldest: ${oldest})"
done

# Verify event flow end-to-end
echo ""
echo "=== KAFKA CONSUMER LAG ==="
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --all-groups 2>/dev/null | grep -v "TOPIC\|^$" | head -20
```

### 5.2 Care Path Smoke Test

After recovery, verify the clinical care path works end-to-end:

```bash
# 1. Authorization (TSHEPO)
TOKEN=$(curl -s -X POST "http://localhost:8080/realms/impilo/protocol/openid-connect/token" \
  -d "grant_type=client_credentials" \
  -d "client_id=tshepo-service" \
  -d "client_secret=${TSHEPO_CLIENT_SECRET}" | jq -r .access_token)

# 2. Facility lookup (TUSO)
curl -s -H "Authorization: Bearer ${TOKEN}" \
  http://localhost:10000/v1/internal/facilities | jq '.content | length'

# 3. Patient lookup (VITO)
curl -s -H "Authorization: Bearer ${TOKEN}" \
  http://localhost:10000/v1/clients?page=0\&size=1 | jq .totalElements

# 4. Provider lookup (VARAPI)
curl -s -H "Authorization: Bearer ${TOKEN}" \
  http://localhost:10000/v1/internal/providers?page=0\&size=1 | jq .totalElements
```

### 5.3 Monitor for 30 Minutes Post-Recovery

After recovery, actively monitor for:

| Metric | Check | Threshold |
|--------|-------|-----------|
| Error rate | Prometheus: `rate(http_server_requests_seconds_count{status=~"5.."}[5m])` | < 0.1% |
| Latency p99 | Prometheus: `histogram_quantile(0.99, ...)` | Within SLO targets |
| Outbox lag | Each service's outbox unpublished count | Decreasing to normal |
| Kafka consumer lag | Consumer group describe | Decreasing to 0 |
| Pod restarts | `kubectl get pods -n impilo --sort-by='.status.containerStatuses[0].restartCount'` | No new restarts |

---

## 6. Communication Protocol

### Status Update Template

```
[INCIDENT] Partial Platform Recovery — Update #N
Time: HH:MM UTC
Severity: SEV-X
Status: ASSESSING / RESTORING / VERIFYING / RESOLVED

Impact:
- Ring 0 services: [UP/DOWN/DEGRADED]
- Clinical operations: [AVAILABLE/DEGRADED/UNAVAILABLE]
- Estimated data loss: [0 min / X min / under investigation]

Current action: [what is being done right now]
ETA to Ring 0 restoration: X min
ETA to full platform restoration: X min

Next update: HH:MM UTC (every 5 min during SEV-1)
```

---

## 7. Escalation Matrix

| Time | Trigger | Action |
|:----:|---------|--------|
| T+0 | Multiple service failures detected | SRE on-call declares incident, begins assessment |
| T+5 min | Ring 0 partially down | Page platform engineering lead + EM |
| T+10 min | Ring 0 fully down | All engineering leads paged; DR war room opened |
| T+15 min | Ring 0 RTO breach imminent | Decision point: local recovery vs DR site failover |
| T+20 min | Clinical operations impacted > 15 min | Notify clinical safety officer and facility managers |
| T+30 min | Still degraded | Notify CTO; consider MoHCC notification |
| T+1 hr | Full AZ failure confirmed | Execute DR site failover if not already done |

---

## 8. DR Site Failover Decision

If local recovery is not progressing, consider full DR site failover:

| Factor | Stay Local | Failover to DR |
|--------|:----------:|:--------------:|
| Primary DB recoverable | ✅ | |
| Replica available in surviving AZ | ✅ | |
| AZ failure confirmed permanent | | ✅ |
| Multiple infrastructure components failed | | ✅ |
| Recovery estimated > 30 min for Ring 0 | | ✅ |
| DR site verified and warm | | ✅ |

**DR site failover procedure:**
1. Update DNS to point to DR cluster
2. Verify DR database is current (check replication lag before AZ failed)
3. Start Ring 0 services on DR cluster
4. Verify authorization and care path
5. Notify all teams of DR activation

---

## 9. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-03-15 | Wave 20 | Initial partial platform recovery runbook |
