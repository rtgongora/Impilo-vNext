# Runbook: Ring 0 Service Degradation

> Scope: Ring 0 services (TSHEPO, VITO, VARAPI, TUSO, ZIBO)
> Triggers: `Ring0AvailabilityBurnCritical`, `Ring0AvailabilityBurnWarningPage`, `*LatencyP95High`, `*LatencyP99Critical`, `Ring0ServiceFlapping`

---

## 1. When This Runbook Applies

Use this runbook when a Ring 0 service is degraded but not fully down:
- Elevated 5xx error rate (availability burn rate alerts)
- Latency exceeding SLO thresholds (p95/p99 alerts)
- Service flapping (repeated restarts)
- Partial health failures (one health indicator DOWN, service still responding)

For a fully unreachable service, see [ring0-incident-triage.md](ring0-incident-triage.md).

---

## 2. Severity Classification

| Condition | Severity | Error Budget Impact |
|-----------|----------|---------------------|
| TSHEPO degraded (any level) | **SEV-1** — every request transits TSHEPO | Platform-wide; 99.95% SLO = 21.6 min/month budget |
| VITO/TUSO degraded (care-path) | **SEV-1** — blocks patient lookup/encounters | Per-service; 99.9% SLO = 43.2 min/month |
| VARAPI/ZIBO degraded | **SEV-2** — provider/billing lookup affected | Per-service; 99.9% SLO = 43.2 min/month |

---

## 3. Immediate Triage (First 5 Minutes)

### Step 1: Confirm the alert is real

```bash
# Check service health endpoint directly
curl -s http://<service>:<port>/actuator/health | jq .

# Service port map:
#   TSHEPO=8081, VITO=8082, VARAPI=8083, TUSO=8084, ZIBO=8085

# Check Prometheus for current error rate (5m window)
curl -s 'http://localhost:9090/api/v1/query?query=1-sum(rate(http_server_requests_seconds_count{application="<service>-service",status=~"5.."}[5m]))/sum(rate(http_server_requests_seconds_count{application="<service>-service"}[5m]))' | jq .data.result[0].value[1]
```

### Step 2: Identify the failure mode

```bash
# Check which health indicators are failing
curl -s http://<service>:<port>/actuator/health | jq '.components | to_entries[] | select(.value.status != "UP")'

# Check recent error logs (Kubernetes)
kubectl logs -l app=<service>-service --tail=200 --since=10m | grep -E "ERROR|WARN|Exception"

# Check recent error logs (Docker Compose)
docker compose -f docker-compose.runtime.yml logs --tail=200 <service> | grep -E "ERROR|WARN|Exception"
```

### Step 3: Check for recent changes

```bash
# Recent deployments
kubectl rollout history deployment/<service>-service
# or
git log --oneline -10 -- services/<service>-service/
```

---

## 4. Common Degradation Patterns

### 4.1 Latency Spike — Database

**Symptoms:**
- p95/p99 latency exceeds SLO
- `hikaricp_connections_active` rising toward `hikaricp_connections_max`
- Health component `db` still UP

**Diagnosis:**
```bash
# Check connection pool saturation
curl -s http://<service>:<port>/actuator/metrics/hikaricp.connections.active | jq .measurements[0].value
curl -s http://<service>:<port>/actuator/metrics/hikaricp.connections.max | jq .measurements[0].value

# Check for long-running queries (PostgreSQL)
PGPASSWORD=changeme psql -h localhost -U impilo -d <db-name> -c "
  SELECT pid, now() - pg_stat_activity.query_start AS duration, query, state
  FROM pg_stat_activity
  WHERE state != 'idle' AND now() - pg_stat_activity.query_start > interval '5 seconds'
  ORDER BY duration DESC;
"

# Check table lock contention
PGPASSWORD=changeme psql -h localhost -U impilo -d <db-name> -c "
  SELECT blocked_locks.pid AS blocked_pid, blocking_locks.pid AS blocking_pid,
         blocked_activity.query AS blocked_query
  FROM pg_catalog.pg_locks blocked_locks
  JOIN pg_catalog.pg_stat_activity blocked_activity ON blocked_activity.pid = blocked_locks.pid
  JOIN pg_catalog.pg_locks blocking_locks
    ON blocking_locks.locktype = blocked_locks.locktype
    AND blocking_locks.database IS NOT DISTINCT FROM blocked_locks.database
    AND blocking_locks.relation IS NOT DISTINCT FROM blocked_locks.relation
  WHERE NOT blocked_locks.granted;
"
```

**Remediation:**
1. Terminate long-running queries: `SELECT pg_terminate_backend(<pid>);`
2. If connection pool exhausted, check for connection leaks (un-closed transactions)
3. If due to table bloat, schedule VACUUM ANALYZE during maintenance window

### 4.2 Latency Spike — Redis

**Symptoms:**
- Cache-dependent operations slow (auth token validation, session lookups)
- Health component `redis` shows elevated latency or DOWN

**Diagnosis:**
```bash
# Check Redis connectivity
redis-cli -h localhost ping

# Check Redis latency
redis-cli -h localhost --latency-history

# Check Redis memory usage
redis-cli -h localhost info memory | grep used_memory_human

# Check slow log
redis-cli -h localhost slowlog get 10
```

**Remediation:**
1. If Redis is down, service should degrade gracefully (re-validate tokens per request)
2. If Redis memory is full, check eviction policy and key TTLs
3. If Redis latency is high, check network between service and Redis pod

### 4.3 Error Rate Spike — Application

**Symptoms:**
- 5xx rate increasing
- No infrastructure-level failures
- Health endpoint returns 200

**Diagnosis:**
```bash
# Get error breakdown by endpoint
curl -s 'http://localhost:9090/api/v1/query?query=topk(10,sum by (uri, status)(rate(http_server_requests_seconds_count{application="<service>-service",status=~"5.."}[5m])))' | jq .data.result

# Check for specific exception types in logs
kubectl logs -l app=<service>-service --tail=500 --since=10m | grep -o 'Exception: [^|]*' | sort | uniq -c | sort -rn | head -10
```

**Remediation:**
1. If errors map to a specific endpoint, consider disabling the endpoint via feature flag
2. If errors are NullPointerException or similar, likely a code bug — rollback deployment
3. If errors are transient TimeoutExceptions, investigate downstream dependency

### 4.4 Service Flapping (Repeated Restarts)

**Symptoms:**
- `Ring0ServiceFlapping` alert fires
- `changes(up{job="<service>"}[10m]) > 3`
- Container restart count increasing

**Diagnosis:**
```bash
# Check restart count and reason
kubectl get pods -l app=<service>-service -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.status.containerStatuses[0].restartCount}{"\t"}{.status.containerStatuses[0].lastState.terminated.reason}{"\n"}{end}'

# Check for OOMKilled
kubectl describe pod -l app=<service>-service | grep -A5 "Last State"

# Check liveness probe configuration
kubectl get deployment <service>-service -o jsonpath='{.spec.template.spec.containers[0].livenessProbe}'
```

**Remediation:**
1. **OOMKilled**: Increase memory limits or investigate memory leak (heap dump)
2. **Liveness probe failure**: Check if probe is too aggressive; increase `failureThreshold` or `timeoutSeconds`
3. **Startup dependency**: Ensure `startupProbe` or `initialDelaySeconds` allows enough time

---

## 5. Escalation

| Time Elapsed | Action |
|-------------|--------|
| 0–5 min | On-call acknowledges alert, begins triage per steps above |
| 5–15 min | If root cause not identified, engage secondary on-call |
| 15–30 min | If not mitigated, engage service owner |
| 30+ min | If TSHEPO or clinical path affected, invoke SEV-1 incident bridge |

---

## 6. Post-Incident

After degradation is resolved:

1. **Verify recovery**: Confirm error rate returns to baseline, latency within SLO
2. **Check error budget**: `remaining_budget = (1 - SLO) * 30d - consumed` — if budget < 25%, raise with SRE lead
3. **Create post-incident report** within 48 hours documenting root cause, timeline, and remediation
4. **Update this runbook** if a new failure mode was discovered

---

## 7. Related Runbooks

- [ring0-incident-triage.md](ring0-incident-triage.md) — complete service outage
- [outbox-backlog.md](outbox-backlog.md) — event publication backlog
- [dependency-failure.md](dependency-failure.md) — infrastructure dependency failure
- [incident-response.md](../../resilience-ops-platform/runbooks/incident-response.md) — general incident response
