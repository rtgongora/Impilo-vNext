# Runbook: Incident Response

## Scope
This runbook covers triage, escalation, and resolution for incidents affecting Impilo vNext services.

## Severity Levels

| Level | Definition | Response Time | Escalation |
|-------|-----------|---------------|------------|
| SEV-1 | Clinical workflow blocked (Ring 1 down) | 15 min | On-call SRE + Engineering Lead |
| SEV-2 | Degraded service (elevated errors, high latency) | 30 min | On-call SRE |
| SEV-3 | Non-critical service issue (integration plane, platform-ops domain, batch jobs) | 4 hours | Team lead |
| SEV-4 | Minor issue, no user impact | Next business day | Ticket assignment |

## Triage Procedure

### 1. Detect
- Grafana alert fires (golden signals dashboard)
- User-reported issue via support-service ticket
- Outbox lag exceeds threshold (>1000 pending events)

### 2. Assess
```bash
# Check service health
curl -s http://<service>:<port>/actuator/health | jq .

# Check recent error logs
kubectl logs -l app=<service> --tail=100 | grep ERROR

# Check database connections
curl -s http://<service>:<port>/actuator/metrics/hikaricp.connections.active | jq .
```

### 3. Classify
- **Is Ring 1 (clinical) affected?** → SEV-1, immediately page
- **Is it a single service?** → Check dependency graph, may be cascading
- **Is data integrity at risk?** → Verify audit chain: `GET /internal/v1/audit/chain/verify`

### 4. Communicate
- Create support ticket: `POST /internal/v1/support/tickets` with category=INCIDENT
- Update status channel with: service affected, impact scope, ETA

## Common Scenarios

### Kafka Consumer Lag
1. Check consumer group lag: `kafka-consumer-groups.sh --describe --group <group>`
2. If lag > 10,000: scale consumer replicas
3. If DLQ growing: check DLQ topic for error patterns

### Database Connection Exhaustion
1. Check HikariCP metrics for active/idle/pending
2. If active == max: look for long-running transactions
3. Emergency: increase `maximumPoolSize` and restart

### Outbox Publisher Stuck
1. Check `SELECT COUNT(*) FROM <prefix>_event_outbox WHERE published_at IS NULL`
2. If growing: check Kafka broker connectivity
3. Manual flush: restart the outbox poller scheduled task

## Post-Incident
1. Create post-mortem document
2. Update runbooks with any new learnings
3. Append audit record: action=INCIDENT_RESOLVED
