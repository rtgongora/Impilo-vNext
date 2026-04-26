# Impilo vNext — Operations Runbook

## Quick Reference

| Task | Command |
|------|---------|
| Start platform | `./scripts/runtime/platformctl.sh up lite` |
| Stop platform | `./scripts/runtime/platformctl.sh down` |
| Check status | `./scripts/runtime/platformctl.sh status` |
| View all logs | `./scripts/runtime/platformctl.sh logs` |
| View service logs | `./scripts/runtime/platformctl.sh logs tshepo` |
| Run bootstrap | `./scripts/runtime/platformctl.sh bootstrap` |
| Run smoke tests | `./scripts/runtime/platformctl.sh smoke` |
| Verify readiness | `./scripts/runtime/platformctl.sh verify` |
| Collect evidence | `./scripts/runtime/platformctl.sh evidence` |

## Startup Procedure

### Standard startup:
```bash
./scripts/runtime/platformctl.sh up lite
./scripts/runtime/platformctl.sh bootstrap
./scripts/runtime/platformctl.sh smoke
```

### Full platform startup:
```bash
./scripts/runtime/platformctl.sh up full
./scripts/runtime/platformctl.sh bootstrap
./scripts/runtime/platformctl.sh verify full
./scripts/runtime/platformctl.sh smoke
```

## Shutdown Procedure

### Standard shutdown:
```bash
./scripts/runtime/platformctl.sh down
```

### Emergency shutdown (force):
```bash
docker compose -f ops/runtime/docker-compose.infra.yml \
               -f ops/runtime/docker-compose.shared.yml \
               -f ops/runtime/docker-compose.edge.yml \
               -f ops/runtime/docker-compose.kernel.yml \
               -f ops/runtime/docker-compose.operations.yml \
               -f ops/runtime/docker-compose.apps.yml down --timeout 10
```

## Restart Specific Layer

### Restart infrastructure only:
```bash
docker compose -f ops/runtime/docker-compose.infra.yml restart
```

### Restart a specific service:
```bash
docker compose -f ops/runtime/docker-compose.kernel.yml restart tshepo
```

### Restart edge layer:
```bash
docker compose -f ops/runtime/docker-compose.edge.yml restart
```

### Restart all Java services (keep infra running):
```bash
docker compose -f ops/runtime/docker-compose.kernel.yml restart
docker compose -f ops/runtime/docker-compose.operations.yml restart
```

## Verify Platform

### Full verification:
```bash
./scripts/runtime/platformctl.sh verify
```

### Manual health checks:
```bash
# Infrastructure
pg_isready -h localhost -p 5432 -U impilo
redis-cli ping
curl http://localhost:9092  # (connection = kafka alive)

# Shared services
curl http://localhost:8080/health/ready      # Keycloak
curl http://localhost:8090/fhir/metadata     # HAPI FHIR

# Edge
curl http://localhost:8181/health            # OPA
curl http://localhost:9901/ready             # Envoy

# Services
curl http://localhost:8081/actuator/health   # TSHEPO
curl http://localhost:8082/actuator/health   # VITO
curl http://localhost:8083/actuator/health   # VARAPI
curl http://localhost:8084/actuator/health   # TUSO
curl http://localhost:8085/actuator/health   # ZIBO
curl http://localhost:8088/actuator/health   # PCT
curl http://localhost:8089/actuator/health   # OROS
curl http://localhost:8160/actuator/health   # BFF

# UI
curl http://localhost:3000                   # Experience UI
```

## Collect Logs and Evidence

### Stream logs:
```bash
./scripts/runtime/platformctl.sh logs
```

### Collect evidence pack:
```bash
./scripts/runtime/collect-runtime-evidence.sh
# Output: docs/evidence/runtime-evidence-<timestamp>.txt
```

### Export specific service logs:
```bash
docker logs impilo-tshepo > /tmp/tshepo.log 2>&1
docker logs impilo-vito > /tmp/vito.log 2>&1
```

## Recover from Partial Failure

### One service crashed:
```bash
# Check which service is down
./scripts/runtime/platformctl.sh status

# Restart just that service
docker compose -f ops/runtime/docker-compose.kernel.yml restart vito

# Verify it recovered
curl http://localhost:8082/actuator/health
```

### Database connection failures:
```bash
# Check postgres
docker logs impilo-postgres --tail 20

# Restart postgres (services will reconnect)
docker compose -f ops/runtime/docker-compose.infra.yml restart postgres

# Wait for health
docker compose -f ops/runtime/docker-compose.infra.yml up -d --wait postgres
```

### Kafka connection failures:
```bash
# Check kafka
docker logs impilo-kafka --tail 20

# Restart kafka
docker compose -f ops/runtime/docker-compose.infra.yml restart kafka

# Re-create topics (idempotent)
./scripts/bootstrap/bootstrap-topics.sh
```

### Keycloak not responding:
```bash
docker logs impilo-keycloak --tail 30
docker compose -f ops/runtime/docker-compose.shared.yml restart keycloak

# Re-bootstrap auth (idempotent)
./scripts/bootstrap/bootstrap-auth.sh
```

### Complete reset:
```bash
./scripts/runtime/platformctl.sh down
docker volume rm $(docker volume ls -q | grep impilo) 2>/dev/null || true
./scripts/runtime/platformctl.sh up lite
./scripts/runtime/platformctl.sh bootstrap
```

## Data Management

### Preserve data between restarts:
Data is stored in Docker volumes. `platformctl.sh down` preserves volumes by default.

### Destroy all data:
```bash
./scripts/runtime/platformctl.sh down
docker volume rm impilo-vnext_pg_data impilo-vnext_minio_data 2>/dev/null || true
docker volume prune -f
```

### Backup postgres:
```bash
docker exec impilo-postgres pg_dumpall -U impilo > backup.sql
```
