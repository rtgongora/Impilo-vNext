# Impilo vNext — Dev Runtime Acceptance Pack (Wave 18)

## Overview

This document provides exact commands to build, run, and validate the Impilo vNext
dev runtime. It covers all infrastructure, backend services, the experience UI, and
edge enforcement (Envoy + OPA).

---

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Docker | 24.x+ | Container runtime |
| Docker Compose | 2.20+ | Service orchestration |
| bash | 4.x+ | Script execution |
| curl | any | Smoke tests |
| psql | 16.x (optional) | Event-bus proof (falls back to `docker exec`) |

---

## 1. Build

### Pre-warm caches (first time, requires internet)

```bash
# Local connected Maven builds should use the default user cache (~/.m2/repository).
# Use the vendored cache path below when preparing an offline/runtime bundle.

# Maven dependencies
cd services
mvn dependency:go-offline -Dmaven.repo.local=../vendor/m2/repository
cd ..

# npm dependencies
cd ui/experience
npm install --legacy-peer-deps --cache ../../vendor/node-cache
cd ../..
```

### Build all services

```bash
./scripts/dev-runtime.sh build
```

This runs:
1. **Maven builder** — builds all 62 service modules into JARs (`services/*/target/*.jar`)
2. **UI builder** — builds the Experience UI (`ui/experience/.next/`)
3. **Docker image builder** — builds runtime images for all services

**Expected output:**
```
[INFO]  Building all services (Maven + UI)...
[INFO]  Phase 1: Maven build (all backend services)
=== Installing Maven ===
=== Building all services (skip tests for build phase) ===
[INFO] BUILD SUCCESS
[INFO]  Phase 2: UI build (Experience)
=== UI build complete ===
[INFO]  Phase 3: Building Docker images for runtime
[OK]    Build complete. Run './scripts/dev-runtime.sh up' to start.
```

---

## 2. Run

```bash
./scripts/dev-runtime.sh up
```

**Expected output:**
```
[INFO]  Starting Impilo Dev Runtime...
[INFO]  Starting infrastructure (postgres, redis, kafka, keycloak, minio, hapi-fhir)...
[INFO]  Starting edge (OPA + Envoy)...
[INFO]  Starting backend services...
[INFO]  Starting UI...
[OK]    All services starting.
[INFO]  Envoy gateway: http://localhost:10000
[INFO]  Experience UI: http://localhost:3000
```

### Service endpoints after startup

| Service | URL | Health Check |
|---------|-----|-------------|
| Envoy Gateway | http://localhost:10000 | http://localhost:9901/ready |
| OPA | http://localhost:8181 | http://localhost:8181/health |
| TSHEPO | http://localhost:8081 | http://localhost:8081/actuator/health |
| VITO | http://localhost:8082 | http://localhost:8082/actuator/health |
| VARAPI | http://localhost:8083 | http://localhost:8083/actuator/health |
| TUSO | http://localhost:8084 | http://localhost:8084/actuator/health |
| ZIBO | http://localhost:8085 | http://localhost:8085/actuator/health |
| PCT | http://localhost:8088 | http://localhost:8088/actuator/health |
| OROS | http://localhost:8089 | http://localhost:8089/actuator/health |
| Experience BFF | http://localhost:8160 | http://localhost:8160/actuator/health |
| Experience UI | http://localhost:3000 | http://localhost:3000 |
| Keycloak | http://localhost:8080 | http://localhost:8080/health/ready |
| HAPI FHIR | http://localhost:8090/fhir | http://localhost:8090/fhir/metadata |
| PostgreSQL | localhost:5432 | `pg_isready -U impilo` |
| Redis | localhost:6379 | `redis-cli ping` |
| Kafka | localhost:9092 | broker-api-versions check |

### Check status

```bash
./scripts/dev-runtime.sh status
```

---

## 3. Smoke Tests

```bash
./scripts/dev-runtime.sh smoke
```

This runs three test suites in sequence:

### 3a. Smoke & Compliance (`scripts/smoke/smoke.sh`)

| Test | What it checks | Expected |
|------|---------------|----------|
| Infrastructure readiness | Postgres, Redis, Kafka reachable | PASS |
| Service health | `/actuator/health` on all 8 services | PASS (UP) |
| OPA policies loaded | `impilo.gateway.headers` policy present | PASS |
| v1.1 deny without headers | `GET /internal/v1/test` → 403 | PASS |
| v1.1 allow with headers | `GET /internal/v1/test` + 4 headers → not 403 | PASS |
| Non-v1.1 passthrough | `GET /api/v1/facilities` → not 403 | PASS |
| Idempotent replay | Same key+body → same HTTP code | PASS/WARN |
| Idempotency conflict | Same key, different body → 409 | PASS/WARN |
| Experience UI reachable | `GET http://localhost:3000` | PASS |

### 3b. Event Bus Proof (`scripts/smoke/event-bus-proof.sh`)

| Check | What it verifies | Expected |
|-------|-----------------|----------|
| Trigger write | POST to TSHEPO authorize endpoint | HTTP 200/4xx |
| event_outbox exists | Table present in tshepo database | PASS |
| Outbox row count | At least 1 row after write | PASS |
| Governance columns | tenant_id, pod_id, correlation_id, idempotency_key | PASS/WARN |
| Event type format | Matches `impilo.*.v1` pattern | PASS/WARN |
| schema_version | >= 1 | PASS/WARN |
| partition_key | Present in payload | PASS/WARN |
| Integration Hub outbox | ih_event_outbox table exists | PASS/WARN |

### 3c. Route Parity (`scripts/smoke/route-parity.sh`)

| Check | What it verifies | Expected |
|-------|-----------------|----------|
| Registry exists | `route-parity-check.mjs` present | PASS |
| Expected routes | Count from registry > 0 | PASS (117 routes) |
| Implemented pages | `page.tsx` files in `src/app/` | PASS |
| Node.js check | Run actual parity validator | PASS/WARN |
| Parity ratio | Implemented / Expected | PASS (100%) or WARN |

---

## 4. Stop

```bash
./scripts/dev-runtime.sh down
```

---

## 5. Triage Map

| Failure Symptom | Likely Cause | Fix |
|----------------|-------------|-----|
| Maven build fails with dependency errors | Maven cache not pre-warmed, or the build is using a fragile repo-local cache on a synced folder | For normal local builds use the default `~/.m2/repository`; for offline/runtime prep run `cd services && mvn dependency:go-offline -Dmaven.repo.local=../vendor/m2/repository` on a connected machine |
| Service fails to start (Postgres connection) | Postgres not ready or database not created | Check `scripts/seed/init-databases.sql` includes the service's DB; check `docker compose logs postgres` |
| Service fails with Flyway error | Migration conflict or missing migration | Check `services/<name>/src/main/resources/db/migration/` |
| Envoy returns 503 | Backend service not running | Check `./scripts/dev-runtime.sh status`; service may still be starting |
| OPA returns 403 on non-v1.1 paths | OPA policy misconfigured | Check `tools/ops/gateway/opa/impilo_headers.rego` — non-v1.1 paths should be allowed |
| v1.1 path returns 403 WITH headers | OPA not receiving headers from Envoy | Check `infra/envoy/envoy-runtime.yaml` ext_authz `allowed_headers` patterns |
| Idempotency test returns WARN | Service doesn't implement `IdempotencyFilter` yet | Expected in early waves — the gateway-level OPA check validates the header presence |
| Event-bus proof returns WARN for columns | Outbox table schema doesn't have v1.1 columns yet | Check the latest Flyway migration for the service |
| Kafka broker unreachable | KRaft not initialized | Wait longer (start_period); check `docker compose logs kafka` |
| Experience UI build fails | npm dependencies not cached | Run `cd ui/experience && npm install --legacy-peer-deps` |
| `psql: command not found` in event-bus proof | psql not installed locally | Script falls back to `docker exec` automatically |
| Docker build fails with "no space" | Docker disk full | Run `docker system prune -f` |
| OAuth2/JWT errors in service logs | Keycloak realm not configured | Expected — services have `SPRING_AUTOCONFIGURE_EXCLUDE` to disable OAuth2 in dev |
| Port conflict (address already in use) | Another process on the port | Stop the conflicting process or adjust ports in `.env` |

---

## 6. File Manifest

| File | Purpose |
|------|---------|
| `docker-compose.build.yml` | Build phase — Maven + UI builders with cache mounts |
| `docker-compose.runtime.yml` | Runtime phase — infra + services + UI + edge |
| `infra/envoy/envoy-runtime.yaml` | Envoy config for Docker Compose (service DNS names) |
| `scripts/dev-runtime.sh` | Convenience wrapper (build/up/down/smoke/logs/status) |
| `scripts/smoke/smoke.sh` | Health + v1.1 enforcement + idempotency tests |
| `scripts/smoke/event-bus-proof.sh` | Outbox table verification |
| `scripts/smoke/route-parity.sh` | UI route registry vs filesystem parity |
| `docs/acceptance/dev-runtime-acceptance-pack.md` | This document |

---

## 7. Cache Strategy (Offline/Air-Gapped)

### Maven

- **Default for local connected builds**: Maven's standard user cache (`~/.m2/repository`)
- **Host path**: `./vendor/m2/repository`
- **Container mount**: `/root/.m2/repository`
- **Pre-warm**: `cd services && mvn dependency:go-offline -Dmaven.repo.local=../vendor/m2/repository`
- **No secrets**: Maven settings use default config; no credentials in cache

### npm

- **Host path**: `./vendor/node-cache`
- **Container mount**: `/root/.npm`
- **Pre-warm**: `cd ui/experience && npm install --legacy-peer-deps --cache ../../vendor/node-cache`

### Docker images

Pre-pull on a connected machine:
```bash
docker pull eclipse-temurin:21-jdk-alpine
docker pull eclipse-temurin:21-jre-alpine
docker pull node:20-alpine
docker pull postgres:16-alpine
docker pull redis:7-alpine
docker pull apache/kafka:3.7.1
docker pull quay.io/keycloak/keycloak:25.0
docker pull hapiproject/hapi:v7.4.0
docker pull minio/minio:latest
docker pull openpolicyagent/opa:0.68.0
docker pull envoyproxy/envoy:v1.31-latest
```

Then `docker save` / `docker load` to transfer to the air-gapped machine.

---

## 8. Environment Variables

Copy `.env.example` to `.env`. Key variables:

| Variable | Default | Purpose |
|----------|---------|---------|
| `POSTGRES_USER` | `impilo` | Database user |
| `POSTGRES_PASSWORD` | `changeme` | Database password |
| `KEYCLOAK_REALM` | `impilo` | Keycloak realm name |
| `KEYCLOAK_ADMIN_PASSWORD` | `admin` | Keycloak admin password |
| `MINIO_ACCESS_KEY` | `minioadmin` | MinIO access key |
| `MINIO_SECRET_KEY` | `minioadmin` | MinIO secret key |

**Never commit `.env` to source control.** The `.gitignore` already excludes it.
