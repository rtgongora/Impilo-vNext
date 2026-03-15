# Runbook: Ring 0 Failover

> Scope: Failover procedures for Ring 0 services (TSHEPO, VITO, VARAPI, TUSO, ZIBO) and their dependencies (Keycloak, PostgreSQL, Envoy)
> Severity: SEV-1 — Ring 0 failure blocks clinical care
> RTO target: ≤ 15 minutes for all Ring 0 services

---

## 1. When to Use This Runbook

- Ring 0 service is DOWN and not recovering via K8s restart
- Ring 0 database primary is unresponsive
- Keycloak is DOWN (no new tokens can be issued)
- Envoy gateway is routing errors to all Ring 0 backends
- Ring 0 availability burn rate exceeds 14.4× (BurnCritical alert)

---

## 2. Ring 0 Service Map

```
               ┌──────────┐
   Client ────→│  Envoy   │────→ ext_authz ────→ TSHEPO (8081)
               │ (10000)  │                       ├── authz (Keycloak)
               └────┬─────┘                       ├── policy engine
                    │                              └── audit
                    ├───────────────────────────→ VITO   (8082) — MPI
                    ├───────────────────────────→ VARAPI (8083) — Provider registry
                    ├───────────────────────────→ TUSO   (8084) — Terminology
                    └───────────────────────────→ ZIBO   (8085) — Billing/tariffs
```

**Critical dependency chain:**
1. **Envoy** → must be running for any request to reach services
2. **TSHEPO** → must be running for any request to be authorized
3. **Keycloak** → must be running for new token issuance (existing tokens continue working for 5–15 min)
4. **PostgreSQL** → must be running for any service to process requests

---

## 3. Triage Decision Tree

```
Ring 0 alert received
    │
    ├─ Is Envoy responding?
    │   ├─ No → §4.1 Envoy Recovery
    │   └─ Yes → continue
    │
    ├─ Is TSHEPO healthy?
    │   ├─ No → §4.2 TSHEPO Recovery (all requests blocked)
    │   └─ Yes → continue
    │
    ├─ Is Keycloak healthy?
    │   ├─ No → §4.3 Keycloak Recovery (new sessions blocked)
    │   └─ Yes → continue
    │
    ├─ Is PostgreSQL responsive?
    │   ├─ No → §4.4 Database Failover
    │   └─ Yes → continue
    │
    └─ Which specific Ring 0 service is down?
        └─ §4.5 Service-Specific Recovery
```

---

## 4. Recovery Procedures

### 4.1 Envoy Gateway Recovery

**Impact:** Total platform outage — no requests reach any service.

**RTO target:** ≤ 1 minute (K8s auto-restart).

```bash
# 1. Check Envoy pod status
kubectl get pods -n impilo -l app=envoy

# 2. If CrashLoopBackOff, check logs
kubectl logs -n impilo -l app=envoy --tail=50

# 3. Common causes:
#    - Bad config: validate envoy config
docker run --rm -v /path/to/envoy.yaml:/etc/envoy/envoy.yaml:ro \
  envoyproxy/envoy:v1.31.0 --mode validate -c /etc/envoy/envoy.yaml

#    - ext_authz backend unreachable: check TSHEPO first
#    - Resource limits: check OOM kills
kubectl describe pod -n impilo -l app=envoy | grep -A5 "Last State"

# 4. Force restart
kubectl delete pod -n impilo -l app=envoy

# 5. If persistent failure: bypass ext_authz temporarily (EMERGENCY ONLY)
# This allows unauthenticated access — use only in life-threatening situations
# Edit Envoy config to disable ext_authz filter, then reload
```

### 4.2 TSHEPO Recovery

**Impact:** All API requests fail authorization — complete platform outage.

**RTO target:** ≤ 15 minutes.

```bash
# 1. Check TSHEPO health
curl -s http://localhost:8081/actuator/health | jq .

# 2. Check pod status
kubectl get pods -n impilo -l app=tshepo-service
kubectl logs -n impilo -l app=tshepo-service --tail=100

# 3. Common failure modes:
#    a) Database connection failure
psql -h "${DB_HOST}" -U impilo -d tshepo -c "SELECT 1;"
#       → If fails: see §4.4 Database Failover

#    b) Keycloak unreachable
curl -s http://localhost:8080/realms/impilo/.well-known/openid-configuration | jq .status
#       → If fails: see §4.3 Keycloak Recovery

#    c) OOM / resource exhaustion
kubectl top pod -n impilo -l app=tshepo-service
kubectl describe pod -n impilo -l app=tshepo-service | grep -A3 "Resources"

# 4. Restart TSHEPO
kubectl rollout restart deployment/tshepo-service -n impilo

# 5. Wait for health
kubectl rollout status deployment/tshepo-service -n impilo --timeout=120s

# 6. Verify
curl -s http://localhost:8081/actuator/health | jq .status
# Expected: "UP"
```

### 4.3 Keycloak Recovery

**Impact:** No new tokens. Existing sessions work until token expiry (5–15 minutes).

**RTO target:** ≤ 15 minutes (before existing tokens expire).

```bash
# 1. Check Keycloak health
curl -s http://localhost:8080/health/ready

# 2. Check pod status
kubectl get pods -n impilo -l app=keycloak
kubectl logs -n impilo -l app=keycloak --tail=100

# 3. If Keycloak DB is the issue:
psql -h "${DB_HOST}" -U impilo -d keycloak -c "SELECT 1;"
#    → If fails: see §4.4 Database Failover

# 4. If realm is corrupted:
# Import from backup realm export
/opt/keycloak/bin/kc.sh import --dir /tmp/keycloak-export --override true

# Or restore the keycloak database (see docs/dr/runbooks/db-restore.md)
./scripts/dr/restore-db.sh --db keycloak --from-s3 --force

# 5. Restart Keycloak
kubectl rollout restart deployment/keycloak -n impilo

# 6. Verify token issuance
curl -X POST "http://localhost:8080/realms/impilo/protocol/openid-connect/token" \
  -d "grant_type=client_credentials" \
  -d "client_id=tshepo-service" \
  -d "client_secret=${TSHEPO_CLIENT_SECRET}" \
  | jq .access_token
# Expected: a JWT token string
```

### 4.4 Database Failover

**Impact:** Any service connected to the failed database instance is DOWN.

**RTO target:** ≤ 15 minutes for Ring 0 databases.

```bash
# OPTION A: Patroni automatic failover (preferred for Ring 0)
# Patroni monitors primary health and auto-promotes the healthiest replica

# 1. Check Patroni cluster status
patronictl -c /etc/patroni.yml list

# 2. If automatic failover hasn't occurred:
patronictl -c /etc/patroni.yml failover --leader <old-primary> --candidate <replica>

# OPTION B: Manual replica promotion (without Patroni)

# 1. Identify the healthiest replica
psql -h replica-host -U impilo -d postgres -c "
  SELECT pg_last_wal_receive_lsn(), pg_last_wal_replay_lsn();"

# 2. Promote replica to primary
pg_ctl promote -D "${PGDATA}"

# 3. Verify promotion succeeded
psql -h replica-host -U impilo -d postgres -c "SELECT pg_is_in_recovery();"
# Expected: f (false = now primary)

# 4. Update K8s service to point to new primary
kubectl patch svc postgres-primary -n impilo \
  -p '{"spec":{"selector":{"statefulset.kubernetes.io/pod-name":"postgres-1"}}}'

# 5. Restart Ring 0 services to pick up new connection
for svc in tshepo vito varapi tuso zibo; do
  kubectl rollout restart deployment/${svc}-service -n impilo
done

# OPTION C: Full restore from backup (if no replica available)
./scripts/dr/restore-db.sh --db tshepo --from-s3 --force
./scripts/dr/restore-db.sh --db vito --from-s3 --force
# ... repeat for all Ring 0 databases
```

### 4.5 Service-Specific Recovery

For a single Ring 0 service that is down while others are healthy:

```bash
# 1. Identify the failing service
for entry in tshepo:8081 vito:8082 varapi:8083 tuso:8084 zibo:8085; do
  name=${entry%:*}; port=${entry#*:}
  status=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 \
    "http://localhost:${port}/actuator/health" 2>/dev/null || echo "000")
  echo "${name}: HTTP ${status}"
done

# 2. Check the specific service
SVC_NAME="<service>"  # e.g., vito
kubectl describe deployment/${SVC_NAME}-service -n impilo
kubectl logs -l app=${SVC_NAME}-service -n impilo --tail=100

# 3. Check its database
DB_NAME="${SVC_NAME}"  # database name matches service name
psql -h "${DB_HOST}" -U impilo -d "${DB_NAME}" -c "SELECT 1;"

# 4. Restart the service
kubectl rollout restart deployment/${SVC_NAME}-service -n impilo

# 5. If restart fails, restore its database
./scripts/dr/restore-db.sh --db "${DB_NAME}" --from-s3 --force
kubectl rollout restart deployment/${SVC_NAME}-service -n impilo

# 6. Verify health
curl -s "http://localhost:${PORT}/actuator/health" | jq .
```

---

## 5. Ring 0 Health Verification After Failover

After any Ring 0 failover, run this full verification:

```bash
# All Ring 0 services must be UP
echo "=== Ring 0 Health Check ==="
for entry in tshepo:8081 vito:8082 varapi:8083 tuso:8084 zibo:8085; do
  name=${entry%:*}; port=${entry#*:}
  health=$(curl -s --max-time 5 "http://localhost:${port}/actuator/health" 2>/dev/null | \
    python3 -c "import sys,json; print(json.load(sys.stdin).get('status','UNKNOWN'))" 2>/dev/null || echo "UNREACHABLE")
  echo "  ${name} (port ${port}): ${health}"
done

echo ""
echo "=== Authorization Test ==="
# Attempt an ext_authz call through Envoy
curl -s -o /dev/null -w "HTTP %{http_code}\n" \
  http://localhost:10000/v1/internal/facilities \
  -H "Authorization: Bearer <valid-token>"

echo ""
echo "=== Outbox State ==="
for db in tshepo vito varapi tuso zibo; do
  unpublished=$(psql -h "${DB_HOST}" -U impilo -d "${db}" \
    -tAc "SELECT COUNT(*) FROM event_outbox WHERE published_at IS NULL;" 2>/dev/null || echo "N/A")
  echo "  ${db} outbox: ${unpublished} unpublished"
done

echo ""
echo "=== Database Replication ==="
psql -h "${DB_HOST}" -U impilo -d postgres -c "
  SELECT client_addr, state, sent_lsn, write_lsn, flush_lsn, replay_lsn
  FROM pg_stat_replication;"
```

---

## 6. Communication Protocol

### SEV-1 Status Updates

During a Ring 0 outage, provide status updates every 5 minutes:

```
[INCIDENT] Ring 0 Outage — Update #N
Time: HH:MM UTC
Status: INVESTIGATING / IDENTIFIED / RESTORING / RESOLVED
Impact: All/partial clinical workflows affected
ETA: X minutes to restore
Services affected: [list]
Action: [current step being executed]
```

**Channels:**
- Internal: #impilo-incidents Slack channel
- External: Status page update if user-facing
- Stakeholders: Clinical systems lead, facility managers

---

## 7. Escalation Matrix

| Elapsed | Condition | Action | Contact |
|:-------:|-----------|--------|---------|
| T+0 | Ring 0 alert | SRE on-call begins triage | Auto-page |
| T+5 min | TSHEPO or Keycloak down | Page platform engineering lead | Platform lead |
| T+10 min | Multiple Ring 0 services down | Page engineering manager | EM |
| T+15 min | RTO breach imminent | Invoke DR war room | All leads |
| T+20 min | Consider DR site failover | Decision: DR failover vs continue local recovery | EM + CTO |
| T+30 min | Patient safety concern | Notify clinical safety officer | CSO |

---

## 8. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-03-15 | Wave 20 | Initial Ring 0 failover runbook |
