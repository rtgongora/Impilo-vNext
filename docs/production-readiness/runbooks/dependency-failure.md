# Runbook: Infrastructure Dependency Failure

> Scope: Ring 0 services — PostgreSQL, Redis, Kafka, Keycloak dependency failures
> Triggers: `Ring0ServiceDown`, `Ring0DbPoolCritical`, `Ring0HeapCritical`, health component DOWN

---

## 1. When This Runbook Applies

Use this runbook when a Ring 0 service is impacted by failure of a shared infrastructure dependency:

| Dependency | Port | Services Affected | Care-Path Impact |
|-----------|------|-------------------|-----------------|
| **PostgreSQL** | 5432 | ALL Ring 0 | Total: all persistence fails |
| **Redis** | 6379 | ALL Ring 0 | Partial: cache miss, session re-auth |
| **Kafka** | 9092 | ALL Ring 0 (outbox) | None immediate: outbox buffers events |
| **Keycloak** | 8080 | ALL Ring 0 (auth) | Total: no new token validation |

**Key design principle (Wave 19C verified):** Data-platform services (data-ingestion, data-pipeline, data-warehouse) are NOT synchronous dependencies. Their failure does NOT block care-path operations. See `scripts/production-readiness/verify-data-plane-nonblocking.sh`.

---

## 2. PostgreSQL Failure

### 2.1 Symptoms

- All Ring 0 `/actuator/health` show `db` component DOWN
- Connection pool exhaustion: `hikaricp_connections_active / hikaricp_connections_max > 0.95`
- 5xx errors across all endpoints that require persistence
- Flyway migration failure on service startup

### 2.2 Diagnosis

```bash
# Check PostgreSQL reachability
pg_isready -h localhost -p 5432 -U impilo

# Check PostgreSQL container
docker ps -f name=postgres
docker logs --tail=50 $(docker ps -qf name=postgres)

# Check disk space
docker exec $(docker ps -qf name=postgres) df -h /var/lib/postgresql/data

# Check connection count (from inside PostgreSQL)
PGPASSWORD=changeme psql -h localhost -U impilo -c "
  SELECT datname, count(*) FROM pg_stat_activity GROUP BY datname ORDER BY count DESC;
"

# Check for max_connections limit
PGPASSWORD=changeme psql -h localhost -U impilo -c "SHOW max_connections;"
PGPASSWORD=changeme psql -h localhost -U impilo -c "SELECT count(*) FROM pg_stat_activity;"
```

### 2.3 Remediation

| Root Cause | Action |
|-----------|--------|
| PostgreSQL container crashed | Restart: `docker compose restart postgres`. Data persists on `pg_data` volume. |
| Disk full | Expand volume or clean WAL archives. VACUUM FULL on large tables during maintenance window. |
| Too many connections | Increase `max_connections` or reduce service connection pool sizes. Check for connection leaks. |
| Replication lag (if replica configured) | Check `pg_stat_replication`. Promote replica if primary unrecoverable. |
| Corruption | Follow [restore-drill.md](../../resilience-ops-platform/runbooks/restore-drill.md). |

### 2.4 Service Behavior During PostgreSQL Outage

- All write operations fail with 500
- Read operations from cache may still succeed briefly (Redis-backed)
- **Care path is blocked** — this is a SEV-1 incident
- Outbox events cannot be written, so no new events enter the pipeline

---

## 3. Redis Failure

### 3.1 Symptoms

- Health component `redis` DOWN
- Elevated latency on auth-dependent endpoints (token cache miss)
- Session-based operations may require re-authentication

### 3.2 Diagnosis

```bash
# Check Redis container
docker ps -f name=redis
docker logs --tail=50 $(docker ps -qf name=redis)

# Test Redis connectivity
redis-cli -h localhost ping

# Check Redis memory
redis-cli -h localhost info memory

# Check Redis slow log
redis-cli -h localhost slowlog get 10
```

### 3.3 Remediation

| Root Cause | Action |
|-----------|--------|
| Redis container crashed | Restart: `docker compose restart redis`. Cache cold-starts (empty). |
| Memory exhausted (maxmemory) | Check eviction policy: `redis-cli config get maxmemory-policy`. Consider LRU eviction. |
| Network partition | Check container networking: `docker network inspect`. |

### 3.4 Service Behavior During Redis Outage

- **TSHEPO**: Token validation falls back to Keycloak introspection (higher latency, not blocked)
- **VITO/VARAPI/TUSO/ZIBO**: Session cache miss — each request re-validates JWT (functional but slower)
- **Care path continues** — Redis failure causes degradation, not outage
- **Expected latency impact**: p95 may increase 50–200% until cache rebuilds

---

## 4. Kafka Failure

### 4.1 Symptoms

- `Ring0OutboxLagCritical` firing across multiple services
- Health component `kafka` (if configured) shows DOWN
- Outbox publisher logs show `TimeoutException` or `NetworkException`

### 4.2 Diagnosis

```bash
# Check Kafka container
docker ps -f name=kafka
docker logs --tail=50 $(docker ps -qf name=kafka)

# Test broker API
docker exec $(docker ps -qf name=kafka) /opt/kafka/bin/kafka-broker-api-versions.sh \
  --bootstrap-server localhost:9092 2>&1 | head -5

# Check KRaft controller status
docker exec $(docker ps -qf name=kafka) /opt/kafka/bin/kafka-metadata.sh \
  --snapshot /tmp/kraft-combined-logs/__cluster_metadata-0/00000000000000000000.log \
  --cluster-id impilo-local-dev-cluster-01 2>/dev/null | head -20

# Check disk usage
docker exec $(docker ps -qf name=kafka) df -h /tmp/kraft-combined-logs
```

### 4.3 Remediation

| Root Cause | Action |
|-----------|--------|
| Kafka container crashed | Restart: `docker compose restart kafka`. KRaft recovers from local log. |
| Disk full | Increase `log.retention.hours` or expand volume. Delete old segments. |
| Network isolation | Check docker network connectivity between services and Kafka. |

### 4.4 Service Behavior During Kafka Outage

- **Care path continues unblocked** — this is by design (outbox pattern)
- Writes succeed: events accumulate in `event_outbox` table
- Outbox lag grows until Kafka recovers
- After Kafka recovery: publisher automatically drains backlog
- **Downstream impact**: Data-ingestion, analytics, audit-ledger see delayed events
- **Risk**: Extended outage → large outbox table → potential DB performance impact (monitor table size)

```bash
# Monitor outbox table size during extended Kafka outage
PGPASSWORD=changeme psql -h localhost -U impilo -d vito -c "
  SELECT pg_size_pretty(pg_total_relation_size('vito.event_outbox')) AS outbox_size,
         (SELECT COUNT(*) FROM vito.event_outbox WHERE published_at IS NULL) AS unpublished;
"
```

---

## 5. Keycloak Failure

### 5.1 Symptoms

- New login attempts fail
- Token refresh fails (existing tokens expire without renewal)
- TSHEPO ext_authz cannot validate tokens against Keycloak
- Eventually all API calls begin failing as tokens expire

### 5.2 Diagnosis

```bash
# Check Keycloak container
docker ps -f name=keycloak
docker logs --tail=50 $(docker ps -qf name=keycloak)

# Check Keycloak health
curl -s http://localhost:8080/health/ready

# Check if Keycloak's database connection is the problem
docker exec $(docker ps -qf name=keycloak) curl -s http://localhost:8080/health/ready | jq .
```

### 5.3 Remediation

| Root Cause | Action |
|-----------|--------|
| Keycloak container crashed | Restart: `docker compose restart keycloak`. |
| Keycloak DB (PostgreSQL) down | Fix PostgreSQL first (Section 2). Keycloak shares the PostgreSQL instance. |
| Realm misconfiguration | Check `impilo` realm configuration in Keycloak admin: `http://localhost:8080/admin/`. |

### 5.4 Service Behavior During Keycloak Outage

- **Existing valid JWTs** continue to work (TSHEPO validates JWT signature locally using cached JWKS)
- **New token requests** fail — users cannot log in
- **Token refresh** fails — sessions expire within token lifetime (typically 5–15 min)
- **Time to full impact**: Duration of JWT token lifetime after Keycloak goes down
- **Care path impact**: Partially blocked — existing sessions work, new sessions fail

---

## 6. Cascading Failure Detection

Multiple dependencies failing simultaneously indicates a deeper issue:

| Pattern | Likely Root Cause |
|---------|-------------------|
| PostgreSQL + Keycloak down | Shared PostgreSQL instance failure |
| All services flapping | Node resource exhaustion (CPU/memory/disk) |
| Network-related errors across services | Container network failure or DNS resolution failure |
| All health checks timeout | Node-level issue (host machine overloaded) |

```bash
# Check node-level resources
docker stats --no-stream

# Check disk across all volumes
df -h

# Check container DNS resolution
docker exec $(docker ps -qf name=tshepo) nslookup postgres
```

---

## 7. Escalation Matrix

| Dependency Failed | Time Threshold | Escalation |
|-------------------|---------------|------------|
| PostgreSQL | Immediate | SEV-1: Page SRE lead + DBA |
| Keycloak | 5 min | SEV-1: Page SRE lead + Identity team |
| Redis | 15 min | SEV-2: On-call SRE |
| Kafka | 30 min | SEV-2: On-call SRE (care path not blocked) |
| Data-platform | Informational | No page — care path unaffected |

---

## 8. Related Runbooks

- [service-degradation.md](service-degradation.md) — service-level degradation
- [outbox-backlog.md](outbox-backlog.md) — outbox lag during Kafka outage
- [ring0-incident-triage.md](ring0-incident-triage.md) — full incident triage
- [restore-drill.md](../../resilience-ops-platform/runbooks/restore-drill.md) — database restore procedure
