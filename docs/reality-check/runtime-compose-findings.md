# Runtime / Compose Wiring Findings — Impilo vNext

> Generated: 2026-03-15 | Branch: claude/review-project-manifest-jb5O0
> Risk Class: E — Compose/runtime wiring is incomplete

## Executive Summary

The runtime composition is **partially complete**. Infrastructure services (Postgres, Redis, Kafka, Keycloak, MinIO, HAPI FHIR) are fully wired with healthchecks. Edge services (Envoy, OPA) are configured. However, only **9 out of 67 backend services** are wired in the runtime compose, and **38 services lack Dockerfiles entirely**. The dev-runtime wrapper script and smoke tests exist but target only the 9 wired services.

## Compose File Inventory

| File | Purpose | Status |
|---|---|---|
| `docker-compose.yml` | Base infrastructure (dev) | COMPLETE — 7 infra services |
| `docker-compose.build.yml` | Build phase (Maven + UI) | COMPLETE — builds JARs + Experience UI |
| `docker-compose.runtime.yml` | Full runtime (infra + services + UI + edge) | PARTIAL — only 9 backend services |

## Infrastructure Coverage (docker-compose.runtime.yml)

| Service | Image | Port | Healthcheck | Status |
|---|---|---|---|---|
| postgres | postgres:16-alpine | 5432 | `pg_isready` | COMPLETE |
| redis | redis:7-alpine | 6379 | `redis-cli ping` | COMPLETE |
| kafka | apache/kafka:3.7.1 | 9092 | broker API versions | COMPLETE |
| keycloak | keycloak:25.0 | 8080 | HTTP /health/ready | COMPLETE |
| minio | minio/minio:latest | 9000/9001 | — | MISSING HEALTHCHECK |
| hapi-fhir | hapiproject/hapi:v7.4.0 | 8090 | /fhir/metadata | COMPLETE |
| opa | openpolicyagent/opa:0.68.0 | 8181 | /health | COMPLETE |
| envoy | envoyproxy/envoy:v1.31 | 10000/9901 | /ready | COMPLETE |
| orthanc | — | — | — | IN BASE COMPOSE ONLY |

## Backend Service Coverage

### In Runtime Compose (9/67 = 13%)

| Service | Port | Healthcheck | Dependencies |
|---|---|---|---|
| tshepo | 8081 | /actuator/health | postgres, redis, kafka |
| vito | 8082 | /actuator/health | postgres, redis, kafka |
| varapi | 8083 | /actuator/health | postgres, redis, kafka |
| tuso | 8084 | /actuator/health | postgres, redis, kafka |
| zibo | 8085 | /actuator/health | postgres, redis, kafka |
| pct | 8088 | /actuator/health | postgres, redis, kafka |
| oros | 8089 | /actuator/health | postgres, redis, kafka |
| experience-bff | 8160 | /actuator/health | postgres |
| experience-ui | 3020 | HTTP / | experience-bff |

### NOT in Runtime Compose (58/67)

All remaining services. This includes critical services like:
- **msika-service** (marketplace)
- **mushex-service** (finance/claims)
- **pharmacy-service** (dispensation)
- **notification-service** (alerts)
- **integration-hub** (message routing)
- **inventory-service** (stock management)
- All data platform services
- All TSHEPO sub-services (audit, authz, consent, identity, keys, offline)

## Dockerfile Coverage

| Metric | Count | Percentage |
|---|---|---|
| Services WITH Dockerfile | 30 | 44% |
| Services WITHOUT Dockerfile | 38 | 56% |
| Total services (excl. shared-core) | 68 | 100% |

## Runtime Scripts

| Script | Purpose | Status |
|---|---|---|
| `scripts/dev-runtime.sh` | Dev convenience wrapper (build/up/down/smoke/logs/status) | EXISTS |
| `scripts/smoke/smoke.sh` | Smoke tests (health, headers, idempotency) | EXISTS |
| `scripts/smoke/event-bus-proof.sh` | Outbox event verification | EXISTS |
| `scripts/smoke/route-parity.sh` | Route parity checks | EXISTS |
| `.env.example` | Environment template | Referenced but existence unverified |

## Envoy Configuration

- **Config path**: `infra/envoy/envoy-runtime.yaml` (referenced in compose)
- **Ports**: 10000 (public gateway), 9901 (admin)
- **OPA integration**: OPA sidecar for policy decisions

## Gap Analysis

### Critical Gaps

1. **Only 13% of backend services in compose**: The remaining 58 services cannot be started in the runtime environment without additional compose entries.

2. **38 services lack Dockerfiles**: Even if added to compose, these services can't be containerized without Dockerfiles. They would need to run as bare JARs.

3. **No compose profiles**: There's no way to selectively start subsets of services (e.g., only Registry Spine, or only Clinical Execution).

### Design Decisions (Not Bugs)

1. **Build phase is separate**: `docker-compose.build.yml` builds JARs, runtime phase runs them. This is intentional and correct for air-gapped/offline builds.

2. **Only core services in compose**: The 9 services in compose represent the Registry Spine (TSHEPO, VITO, VARAPI, TUSO, ZIBO) + Clinical Core (PCT, OROS) + Experience layer. This is likely the minimum viable dev stack.

## Mitigation Applied

- Created `scripts/reality-check/run-compose-checks.sh` with structural validation
- Supports `--live` flag for runtime health checking
- Documents exact expected run path

## Verdict

**COMPOSE/RUNTIME: STRUCTURALLY VALID BUT INCOMPLETE**

The runtime composition correctly wires infrastructure and core services. The gap is breadth — 87% of backend services are not in compose, and 56% lack Dockerfiles. For full-fleet runtime, significant additional compose/Dockerfile work is needed.
