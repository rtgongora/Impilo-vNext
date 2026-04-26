# Impilo vNext — Server Runtime Evidence

**Date**: 2026-03-18
**Context**: Retry validation after reported network resolution

---

## Evidence Collection Status

### ❌ No Runtime Evidence Available

Runtime evidence could not be collected because the Claude Code sandbox environment cannot reach the target server (`197.221.242.150`) due to egress proxy restrictions.

---

## What Was Attempted

### 1. HTTP Reachability (6 endpoints)
```
$ curl -sS --connect-timeout 10 http://197.221.242.150:13020
Host not allowed

$ curl -sS --connect-timeout 10 http://197.221.242.150:13021
Host not allowed

$ curl -sS --connect-timeout 10 http://197.221.242.150:13022
Host not allowed

$ curl -sS --connect-timeout 10 http://197.221.242.150:13023/fhir
Host not allowed

$ curl -sS --connect-timeout 10 http://197.221.242.150:13024/actuator/health
Host not allowed

$ curl -sS --connect-timeout 10 http://197.221.242.150:13025
Host not allowed
```

All returned `HTTP 403` with `x-deny-reason: host_not_allowed` from egress proxy at `21.0.0.77:15004`.

### 2. SSH Reachability
```
$ ssh -o ConnectTimeout=10 -o BatchMode=yes 197.221.242.150
ssh: connect to host 197.221.242.150 port 22: Connection timed out

$ ssh -o ConnectTimeout=10 -o BatchMode=yes -p 7557 197.221.242.150
ssh: connect to host 197.221.242.150 port 7557: Connection timed out
```

### 3. Deployment Script Execution
Not attempted — requires server access.

### 4. Docker/Container Status
Not available — requires server access.

---

## Expected Runtime Evidence (to be collected from the server directly)

When deployment succeeds, the following evidence should be captured:

### Container Status
```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

### Service Health
```bash
./scripts/server-deploy.sh health
```

### Expected Services (from docker-compose.runtime.yml)
| Container | Image | Health Check |
|-----------|-------|--------------|
| impilo-postgres | postgres:16-alpine | `pg_isready` |
| impilo-redis | redis:7-alpine | `redis-cli ping` |
| impilo-kafka | apache/kafka:3.7.1 | broker metadata |
| impilo-keycloak | keycloak:25.0 | `/health/ready` |
| impilo-hapi-fhir | hapi:v7.4.0 | `/fhir/metadata` |
| impilo-minio | minio:latest | `/minio/health/live` |
| impilo-opa | opa:0.68.0 | `/health` |
| impilo-envoy | envoy:v1.31 | admin `/ready` |
| impilo-tshepo | custom build | `/actuator/health` |
| impilo-vito | custom build | `/actuator/health` |
| impilo-varapi | custom build | `/actuator/health` |
| impilo-tuso | custom build | `/actuator/health` |
| impilo-zibo | custom build | `/actuator/health` |
| impilo-pct | custom build | `/actuator/health` |
| impilo-oros | custom build | `/actuator/health` |
| impilo-experience-bff | custom build | `/actuator/health` |
| impilo-one-ui-shell | custom build | HTTP 200 on `:3000` |

---

## How to Collect Evidence Manually

SSH into the server and run:
```bash
ssh -p 7557 rgongora@197.221.242.150

# Container status
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# Health checks
./scripts/server-deploy.sh health

# Logs for any unhealthy service
./scripts/server-deploy.sh logs <service-name>

# Platform status
./scripts/runtime/platformctl.sh status
```
