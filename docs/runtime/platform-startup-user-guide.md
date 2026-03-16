# Impilo vNext — Platform Startup User Guide

This guide explains how to start, use, and stop the Impilo vNext platform for local development. It is written for developers, testers, and operators who may not be familiar with the platform internals.

## Before You Start

### System Requirements

- **Docker Desktop** (or Docker Engine + Compose v2) — version 24+ recommended
- **RAM**: At minimum 8 GB allocated to Docker (16 GB recommended for full profile)
- **Disk**: 10 GB free space for images and volumes
- **OS**: macOS, Linux, or Windows with WSL2

### Verify Prerequisites

```bash
docker --version          # Should be 24+
docker compose version    # Should be v2.x
docker info               # Should not error (daemon running)
```

## Starting the Platform

### Choose Your Profile

| If you want to... | Use this command |
|---|---|
| Work on a single feature (fastest) | `./scripts/runtime/platformctl.sh up lite` |
| Run the full platform locally | `./scripts/runtime/platformctl.sh up full` |
| Test cross-service integration | `./scripts/runtime/platformctl.sh up integration` |
| Run acceptance tests | `./scripts/runtime/platformctl.sh up pilot` |

### Step-by-Step: Starting dev-lite

1. **Navigate to the project root**:
   ```bash
   cd /path/to/Impilo-vNext
   ```

2. **Start the platform**:
   ```bash
   ./scripts/runtime/platformctl.sh up lite
   ```

3. **What happens** (automatically):
   - Checks prerequisites (Docker, Compose)
   - Creates `.env` if missing
   - Starts infrastructure (Postgres, Redis, Kafka, MinIO)
   - Waits for infrastructure to be healthy
   - Starts Keycloak and HAPI FHIR
   - Starts OPA and Envoy gateway
   - Starts TSHEPO (trust), then core registries
   - Starts PCT, OROS, Experience BFF, Experience UI
   - Runs readiness checks on all services
   - Prints service URLs

4. **What to expect on success**:
   ```
   ═══ Platform Started (lite) ═══

     Service URLs:
     ─────────────────────────────────────────
     Envoy Gateway:    http://localhost:10000
     Experience UI:    http://localhost:3020
     Keycloak:         http://localhost:8080
     ...
   ```

5. **Open the Experience UI**:
   Visit http://localhost:3020 in your browser.

### After Starting: Bootstrap

The first time you start, run the bootstrap to set up auth, topics, and seed data:

```bash
./scripts/runtime/platformctl.sh bootstrap
```

This:
- Verifies/imports the Keycloak realm with test users
- Creates Kafka topics
- Validates database schemas
- Seeds test data

## Checking Status

```bash
# See all running services and their health
./scripts/runtime/platformctl.sh status

# Run readiness checks
./scripts/runtime/platformctl.sh verify

# Run smoke tests
./scripts/runtime/platformctl.sh smoke
```

## Viewing Logs

```bash
# All service logs
./scripts/runtime/platformctl.sh logs

# Specific service logs
./scripts/runtime/platformctl.sh logs tshepo
./scripts/runtime/platformctl.sh logs vito
./scripts/runtime/platformctl.sh logs experience-bff
```

## Stopping the Platform

```bash
./scripts/runtime/platformctl.sh down
```

This stops services in reverse layer order (UIs first, infrastructure last) to ensure clean shutdown.

**Note**: Your data is preserved in Docker volumes. To completely reset:
```bash
./scripts/runtime/platformctl.sh down
docker volume rm $(docker volume ls -q | grep impilo) 2>/dev/null
```

## Common Failures and What to Do

### "Docker daemon not running"

**Cause**: Docker Desktop is not started.
**Fix**: Start Docker Desktop, then retry.

### Service stuck in "starting" or "unhealthy"

**Cause**: Usually a slow startup (especially Keycloak, HAPI FHIR on first run).
**Fix**:
```bash
# Check the specific service logs
./scripts/runtime/platformctl.sh logs keycloak

# If truly stuck, restart just that service
docker compose -f ops/runtime/docker-compose.shared.yml restart keycloak
```

### "Port already in use"

**Cause**: Another process is using a port the platform needs.
**Fix**:
```bash
# Find what's using the port (e.g., 8080)
lsof -i :8080

# Stop the conflicting process, then retry
```

### Keycloak "realm not found" errors

**Cause**: Realm import didn't happen on first start.
**Fix**:
```bash
./scripts/bootstrap/bootstrap-auth.sh
```

### Services can't connect to Postgres

**Cause**: Postgres isn't ready yet or init SQL didn't run.
**Fix**:
```bash
# Check postgres health
docker inspect --format='{{.State.Health.Status}}' impilo-postgres

# If "unhealthy", check logs
docker logs impilo-postgres

# Force re-init (destroys data)
docker volume rm impilo-vnext_pg_data
./scripts/runtime/platformctl.sh up lite
```

### Out of memory

**Cause**: Too many services for available Docker RAM.
**Fix**:
- Use `dev-lite` profile instead of `full`
- Increase Docker memory allocation (Docker Desktop → Settings → Resources)

## Who Should Use Which Profile

| Role | Recommended Profile |
|------|-------------------|
| Frontend developer | dev-lite |
| Backend developer (single service) | dev-lite |
| Backend developer (cross-service) | dev-full |
| QA / Tester | integration |
| DevOps / Release | pilot |
| Demo / Presentation | dev-lite |

## Key URLs Reference

| Service | URL | Credentials |
|---------|-----|-------------|
| Experience UI | http://localhost:3020 | — |
| Keycloak Admin | http://localhost:8080 | admin / admin |
| HAPI FHIR | http://localhost:8090/fhir | — |
| Envoy Gateway | http://localhost:10000 | — |
| MinIO Console | http://localhost:9001 | minioadmin / minioadmin |
| Grafana | http://localhost:3100 | admin / admin |
| Jaeger | http://localhost:16686 | — |

## Test Users (after bootstrap)

| Username | Password | Role |
|----------|----------|------|
| dr.mapfumo | test | CLINICIAN |

## Getting Help

- Check platform status: `./scripts/runtime/platformctl.sh status`
- Check specific logs: `./scripts/runtime/platformctl.sh logs <service>`
- Run diagnostics: `./scripts/runtime/platformctl.sh verify`
- Collect evidence: `./scripts/runtime/collect-runtime-evidence.sh`
