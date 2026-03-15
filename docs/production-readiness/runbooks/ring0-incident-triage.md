# Runbook: Ring 0 Incident Triage

> Scope: Structured triage procedure for any Ring 0 production incident
> Triggers: Any Ring 0 alert at WARNING or CRITICAL severity

---

## 1. Purpose

This runbook provides a structured, step-by-step triage procedure for Ring 0 incidents. It is the entry point for all on-call responses and routes to specialized runbooks based on diagnosis.

---

## 2. Triage Flowchart

```
Alert fires for Ring 0 service
        │
        ▼
┌─────────────────────────┐
│ Step 1: Is the service  │
│ reachable?              │
│ curl /actuator/health   │
└─────────┬───────────────┘
          │
    ┌─────┴─────┐
    │           │
  YES          NO
    │           │
    ▼           ▼
┌──────────┐  ┌───────────────────────┐
│ Step 2:  │  │ Service DOWN          │
│ Check    │  │ → Check container/pod │
│ health   │  │ → Check dependencies  │
│ details  │  │ → See dependency-     │
│          │  │   failure.md          │
└────┬─────┘  └───────────────────────┘
     │
     ▼
┌─────────────────────────┐
│ Step 3: Which component │
│ is unhealthy?           │
└─────────┬───────────────┘
          │
   ┌──────┼──────────────┐
   │      │              │
  db    redis        outbox
   │      │              │
   ▼      ▼              ▼
  See    See            See
  dep.   dep.          outbox-
  fail.  fail.         backlog.
  md     md            md
```

---

## 3. Step-by-Step Triage

### Step 1: Acknowledge and Assess (0–2 minutes)

```bash
# 1. Acknowledge the alert in PagerDuty/Opsgenie

# 2. Identify the affected service from alert labels
#    Alert label: application="<service>-service"

# 3. Check if this is TSHEPO (platform-wide impact)
#    If application="tshepo-service" → immediately classify as SEV-1

# 4. Quick health check
for svc in tshepo:8081 vito:8082 varapi:8083 tuso:8084 zibo:8085; do
  name=$(echo $svc | cut -d: -f1)
  port=$(echo $svc | cut -d: -f2)
  status=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 http://localhost:${port}/actuator/health)
  echo "${name}: ${status}"
done
```

### Step 2: Classify Severity (2–3 minutes)

| Condition | Severity | Justification |
|-----------|----------|--------------|
| TSHEPO down or degraded | **SEV-1** | Every API request transits TSHEPO ext_authz |
| VITO down (MPI unreachable) | **SEV-1** | Patient lookup blocked → clinical workflow blocked |
| TUSO down (facility unreachable) | **SEV-1** | Shift-start and encounter creation blocked |
| VARAPI degraded | **SEV-2** | Provider lookup degraded; clinical can proceed with cached data |
| ZIBO degraded | **SEV-2** | Billing calculations degraded; clinical encounters can still proceed |
| Outbox lag only (services healthy) | **SEV-3** | Care path unblocked; analytics/audit delayed |
| Single endpoint returning errors | **SEV-2/3** | Depends on endpoint criticality |

### Step 3: Identify Failure Domain (3–5 minutes)

```bash
# Check if multiple services are affected (infrastructure issue)
echo "=== Service Health ==="
for svc in tshepo:8081 vito:8082 varapi:8083 tuso:8084 zibo:8085; do
  name=$(echo $svc | cut -d: -f1)
  port=$(echo $svc | cut -d: -f2)
  echo "--- ${name} ---"
  curl -s --max-time 3 http://localhost:${port}/actuator/health | jq '{status, components: (.components | to_entries | map({key: .key, status: .value.status}) | from_entries)}' 2>/dev/null || echo "UNREACHABLE"
done

echo ""
echo "=== Infrastructure ==="
# PostgreSQL
pg_isready -h localhost -p 5432 -U impilo 2>/dev/null && echo "postgres: UP" || echo "postgres: DOWN"
# Redis
redis-cli -h localhost ping 2>/dev/null && echo "redis: UP" || echo "redis: DOWN"
# Kafka
docker exec $(docker ps -qf name=kafka) /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 >/dev/null 2>&1 && echo "kafka: UP" || echo "kafka: DOWN"
# Keycloak
curl -s -o /dev/null -w "%{http_code}" --max-time 3 http://localhost:8080/health/ready | grep -q 200 && echo "keycloak: UP" || echo "keycloak: DOWN"
```

### Step 4: Route to Specialized Runbook (5 minutes)

| Diagnosis | Route To |
|-----------|----------|
| Service healthy but elevated errors/latency | [service-degradation.md](service-degradation.md) |
| Outbox lag alert, service otherwise healthy | [outbox-backlog.md](outbox-backlog.md) |
| Infrastructure dependency down | [dependency-failure.md](dependency-failure.md) |
| Service completely unreachable | Continue to Step 5 below |

### Step 5: Service Unreachable — Container/Pod Recovery

```bash
# Docker Compose environment
docker ps -a -f name=<service>
docker logs --tail=100 $(docker ps -aqf name=<service>)

# Check if container exited
docker inspect $(docker ps -aqf name=<service>) --format='{{.State.Status}} {{.State.ExitCode}} {{.State.Error}}'

# Restart the service
docker compose -f docker-compose.runtime.yml restart <service>

# Kubernetes environment
kubectl get pods -l app=<service>-service
kubectl describe pod -l app=<service>-service | tail -30
kubectl logs -l app=<service>-service --previous --tail=100  # logs from crashed container

# Restart (rollout)
kubectl rollout restart deployment/<service>-service
```

---

## 4. Communication Protocol

### During Incident

| Action | Channel | When |
|--------|---------|------|
| Alert acknowledgement | PagerDuty/Opsgenie | Within 5 min of alert |
| Initial assessment | Slack #impilo-incidents | Within 10 min |
| Status update | Slack #impilo-incidents | Every 15 min during SEV-1, every 30 min during SEV-2 |
| Resolution notice | Slack #impilo-incidents + email | Immediately on resolution |

### Status Update Template

```
**Incident Update — [SEV-X] [Service Name]**
Time: [HH:MM UTC]
Status: [Investigating / Identified / Mitigating / Resolved]
Impact: [What is affected for end users]
Current action: [What we are doing right now]
ETA: [When we expect resolution, or "investigating"]
```

---

## 5. Error Budget Tracking

After any incident affecting Ring 0 availability:

```bash
# Check current error budget consumption (requires Prometheus recording rules)
curl -s 'http://localhost:9090/api/v1/query?query=1-avg_over_time(impilo:ring0:availability:ratio_rate5m{application="<service>-service"}[30d])' | jq .data.result[0].value[1]

# Interpret:
#   Value >= 0.999 → TSHEPO SLO met (99.9%)
#   Value >= 0.9995 → TSHEPO SLO met (99.95%)
```

| Service | SLO | 30-day Budget | Budget Consumed → Action |
|---------|-----|:-------------:|--------------------------|
| TSHEPO | 99.95% | 21.6 min | > 75% → deployment freeze |
| VITO | 99.9% | 43.2 min | > 75% → deployment freeze |
| VARAPI | 99.9% | 43.2 min | > 75% → deployment freeze |
| TUSO | 99.9% | 43.2 min | > 75% → deployment freeze |
| ZIBO | 99.9% | 43.2 min | > 75% → deployment freeze |

---

## 6. Post-Incident Checklist

Within 48 hours of incident resolution:

- [ ] Timeline documented (detection → acknowledgement → diagnosis → mitigation → resolution)
- [ ] Root cause identified (or "unknown — further investigation needed")
- [ ] Error budget impact calculated
- [ ] Remediation items created as tickets
- [ ] Runbook updated if new failure mode discovered
- [ ] Post-incident review scheduled (if SEV-1 or SEV-2)

---

## 7. Quick Reference — Service Port Map

| Service | Port | DB | Schema | Plane |
|---------|------|----|--------|-------|
| TSHEPO | 8081 | tshepo | default | Trust |
| VITO | 8082 | vito | vito | Registry |
| VARAPI | 8083 | varapi | varapi | Registry |
| TUSO | 8084 | tuso | tuso | Registry |
| ZIBO | 8085 | zibo | default | Clinical |

| Dependency | Port | Health Check |
|-----------|------|-------------|
| PostgreSQL | 5432 | `pg_isready -h localhost -p 5432 -U impilo` |
| Redis | 6379 | `redis-cli ping` |
| Kafka | 9092 | broker-api-versions check |
| Keycloak | 8080 | `curl http://localhost:8080/health/ready` |

---

## 8. Related Runbooks

- [service-degradation.md](service-degradation.md) — elevated errors/latency
- [outbox-backlog.md](outbox-backlog.md) — event publication lag
- [dependency-failure.md](dependency-failure.md) — infrastructure dependency failure
- [incident-response.md](../../resilience-ops-platform/runbooks/incident-response.md) — general incident response
- [restore-drill.md](../../resilience-ops-platform/runbooks/restore-drill.md) — database restore
- [replay-failures.md](../../resilience-ops-platform/runbooks/replay-failures.md) — outbox replay failures
