# Impilo vNext — Troubleshooting Guide

## Quick Diagnostics

```bash
# 1. What's running?
./scripts/runtime/platformctl.sh status

# 2. What's unhealthy?
./scripts/runtime/platformctl.sh verify

# 3. What are the logs saying?
./scripts/runtime/platformctl.sh logs <service-name>
```

## Common Failure Signatures

### 1. Service shows "starting" for more than 2 minutes

**Signature**: `docker ps` shows status as `starting` or `health: starting`

**Likely causes**:
- Database not ready (postgres still initializing)
- Kafka not ready (KRaft cluster formation)
- Keycloak not ready (realm import in progress)
- Insufficient memory for JVM

**Diagnosis**:
```bash
# Check the specific service
docker logs impilo-<service> --tail 50

# Check dependencies
docker inspect --format='{{.State.Health.Status}}' impilo-postgres
docker inspect --format='{{.State.Health.Status}}' impilo-kafka
docker inspect --format='{{.State.Health.Status}}' impilo-keycloak
```

**Fix**:
```bash
# If dependency is unhealthy, restart it first
docker compose -f ops/runtime/docker-compose.infra.yml restart postgres

# Then restart the dependent service
docker compose -f ops/runtime/docker-compose.kernel.yml restart tshepo
```

### 2. "Connection refused" errors in service logs

**Signature**: `java.net.ConnectException: Connection refused` or `ECONNREFUSED`

**Likely causes**:
- Service started before its dependency was ready
- Wrong hostname (should be container name, not localhost)
- Network not connected

**Diagnosis**:
```bash
# Check if the target service is running
docker ps | grep <target>

# Check Docker network
docker network inspect impilo-network
```

**Fix**: Restart the failing service after its dependency is healthy.

### 3. Port conflict on startup

**Signature**: `Bind for 0.0.0.0:8080 failed: port is already allocated`

**Likely causes**:
- Another process using the same port
- Previous containers not fully stopped

**Diagnosis**:
```bash
# Find what's using the port
lsof -i :<port>
# or
netstat -tlnp | grep <port>

# Check for orphaned containers
docker ps -a | grep impilo
```

**Fix**:
```bash
# Stop conflicting process, or
# Stop and remove old containers
docker compose -f ops/runtime/docker-compose.infra.yml down
docker compose -f ops/runtime/docker-compose.shared.yml down
# ... for each layer

# Or use platformctl
./scripts/runtime/platformctl.sh down
```

### 4. Keycloak realm import fails

**Signature**: Bootstrap auth shows "Realm import failed (HTTP 409)" or similar

**Likely causes**:
- Realm already exists with different configuration
- Realm JSON syntax error

**Diagnosis**:
```bash
# Check Keycloak logs
docker logs impilo-keycloak --tail 50

# Try accessing realm directly
curl http://localhost:8080/admin/realms/impilo \
  -H "Authorization: Bearer $(curl -s -X POST http://localhost:8080/realms/master/protocol/openid-connect/token \
  -d 'grant_type=password&client_id=admin-cli&username=admin&password=admin' | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)"
```

**Fix**: Delete and re-import the realm:
```bash
# The bootstrap-auth.sh from integration-closure handles this
bash scripts/integration-closure/bootstrap-auth.sh
```

### 5. Kafka topics not created

**Signature**: Services log "Topic not found" or bootstrap-topics shows failures

**Likely causes**:
- Kafka not fully ready when topic creation attempted
- Docker exec permission issue

**Diagnosis**:
```bash
# List existing topics
docker exec impilo-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --list
```

**Fix**:
```bash
# Re-run topic bootstrap (idempotent)
./scripts/bootstrap/bootstrap-topics.sh
```

### 6. Out of Memory (OOM)

**Signature**: Container killed, `docker inspect` shows OOMKilled=true

**Likely causes**:
- Too many services for available Docker memory
- JVM heap too large

**Diagnosis**:
```bash
docker inspect --format='{{.State.OOMKilled}}' impilo-<service>
docker stats --no-stream
```

**Fix**:
- Increase Docker memory (Settings → Resources → 12 GB+)
- Use `dev-lite` profile instead of `full`

### 7. One UI Shell not loading

**Signature**: Browser shows blank page or connection refused at :3000

**Likely causes**:
- UI container not built
- BFF not healthy (UI depends on BFF)
- Environment variables not set

**Diagnosis**:
```bash
docker logs impilo-one-ui-shell --tail 20
curl http://localhost:8160/actuator/health  # Check BFF
```

**Fix**:
```bash
# Rebuild UI
docker compose -f ops/runtime/docker-compose.apps.yml build one-ui-shell
docker compose -f ops/runtime/docker-compose.apps.yml up -d one-ui-shell
```

### 8. v1.1 header enforcement not working

**Signature**: Requests without headers pass through (should be denied)

**Likely causes**:
- OPA policies not loaded
- Envoy not connected to OPA

**Diagnosis**:
```bash
# Check OPA policies
curl http://localhost:8181/v1/policies | grep impilo

# Check Envoy config
curl http://localhost:9901/config_dump | grep ext_authz
```

**Fix**:
```bash
docker compose -f ops/runtime/docker-compose.edge.yml restart
```

## Service-Specific Diagnostics

### PostgreSQL
```bash
docker exec impilo-postgres psql -U impilo -l  # List databases
docker exec impilo-postgres pg_isready          # Check readiness
docker logs impilo-postgres --tail 20           # Recent logs
```

### Redis
```bash
docker exec impilo-redis redis-cli ping         # Should return PONG
docker exec impilo-redis redis-cli info memory  # Memory usage
```

### Kafka
```bash
# List topics
docker exec impilo-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

# Describe a topic
docker exec impilo-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic platform.audit.events
```

### Spring Boot services
```bash
# Health with details
curl http://localhost:<port>/actuator/health

# Env (if exposed)
curl http://localhost:<port>/actuator/env

# Metrics
curl http://localhost:<port>/actuator/prometheus
```

## Reset Procedures

### Soft reset (keep data):
```bash
./scripts/runtime/platformctl.sh down
./scripts/runtime/platformctl.sh up lite
```

### Hard reset (destroy data):
```bash
./scripts/runtime/platformctl.sh down
docker volume rm $(docker volume ls -q | grep -E "pg_data|minio_data|orthanc_data") 2>/dev/null
./scripts/runtime/platformctl.sh up lite
./scripts/runtime/platformctl.sh bootstrap
```

### Nuclear reset (remove everything):
```bash
./scripts/runtime/platformctl.sh down
docker system prune -af --volumes
./scripts/runtime/platformctl.sh build
./scripts/runtime/platformctl.sh up lite
./scripts/runtime/platformctl.sh bootstrap
```
