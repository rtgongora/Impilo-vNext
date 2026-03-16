# Impilo vNext — Component Start Order

## Startup Order (Layer by Layer)

### Layer 0 — Infrastructure (parallel start)
| # | Component | Port | Readiness Check | Timeout |
|---|-----------|------|----------------|---------|
| 1 | postgres | 5432 | `pg_isready` | 30s |
| 2 | redis | 6379 | `redis-cli ping` | 15s |
| 3 | kafka | 9092 | `kafka-broker-api-versions.sh` | 60s |
| 4 | minio | 9000, 9001 | HTTP `/minio/health/live` | 20s |

**Wait**: All Layer 0 services must be healthy before proceeding.

### Layer 1 — Shared Services
| # | Component | Port | Readiness Check | Timeout | Depends On |
|---|-----------|------|----------------|---------|-----------|
| 5 | keycloak | 8080 | HTTP `/health/ready` | 120s | postgres |
| 6 | hapi-fhir | 8090 | HTTP `/fhir/metadata` | 90s | postgres |
| 7 | orthanc | 8042 | HTTP `/system` | 30s | — |

**Wait**: keycloak and hapi-fhir must be healthy.

### Layer 2 — Edge
| # | Component | Port | Readiness Check | Timeout | Depends On |
|---|-----------|------|----------------|---------|-----------|
| 8 | opa | 8181 | HTTP `/health` | 15s | — |
| 9 | envoy | 10000, 9901 | HTTP `/ready` (9901) | 30s | opa |

**Wait**: envoy must be healthy.

### Layer 3 — Ring 0: Trust & Governance
| # | Component | Port | Readiness Check | Timeout | Depends On |
|---|-----------|------|----------------|---------|-----------|
| 10 | tshepo | 8081 | HTTP `/actuator/health` | 120s | postgres, redis, kafka, keycloak |

**Wait**: tshepo must be healthy.

### Layer 4 — Ring 1: Core Registries (parallel start)
| # | Component | Port | Readiness Check | Timeout | Depends On |
|---|-----------|------|----------------|---------|-----------|
| 11 | vito | 8082 | HTTP `/actuator/health` | 120s | postgres, redis, kafka, keycloak |
| 12 | varapi | 8083 | HTTP `/actuator/health` | 120s | postgres, redis, kafka, keycloak |
| 13 | tuso | 8084 | HTTP `/actuator/health` | 120s | postgres, redis, kafka, keycloak |
| 14 | zibo | 8085 | HTTP `/actuator/health` | 120s | postgres, redis, kafka, keycloak |

**Wait**: All registries must be healthy.

### Layer 5 — Ring 2: Clinical & Supply Chain (parallel start)
| # | Component | Port | Readiness Check | Timeout |
|---|-----------|------|----------------|---------|
| 15 | pct | 8088 | HTTP `/actuator/health` | 120s |
| 16 | oros | 8089 | HTTP `/actuator/health` | 120s |
| 17 | ubomi | 8087 | HTTP `/actuator/health` | 120s |
| 18 | pharmacy | 8097 | HTTP `/actuator/health` | 90s |
| 19 | inventory | 8098 | HTTP `/actuator/health` | 90s |
| 20 | msika | 8086 | HTTP `/actuator/health` | 90s |
| ... | (additional services) | ... | ... | ... |

### Layer 6 — Operations (parallel start)
| # | Component | Port | Readiness Check | Timeout |
|---|-----------|------|----------------|---------|
| 25 | integration-hub | 8110 | HTTP `/actuator/health` | 90s |
| 26 | notification | 8111 | HTTP `/actuator/health` | 90s |
| 27 | experience-bff | 8160 | HTTP `/actuator/health` | 90s |
| ... | (additional services) | ... | ... | ... |

### Layer 7 — Applications
| # | Component | Port | Readiness Check | Timeout | Depends On |
|---|-----------|------|----------------|---------|-----------|
| 35 | experience-ui | 3020 | HTTP `/` | 60s | experience-bff |

### Layer 8 — Observability (optional, parallel start)
| # | Component | Port | Readiness Check | Timeout |
|---|-----------|------|----------------|---------|
| 36 | prometheus | 9090 | HTTP `/-/healthy` | 30s |
| 37 | otel-collector | 4317, 4318 | HTTP `:13133/` | 20s |
| 38 | grafana | 3100 | HTTP `/api/health` | 30s |
| 39 | jaeger | 16686 | HTTP `:14269/` | 20s |

## Shutdown Order (Reverse)

| Phase | Layer | Services |
|-------|-------|----------|
| 1 | 8 | prometheus, grafana, otel-collector, jaeger |
| 2 | 7 | experience-ui |
| 3 | 6 | experience-bff, integration-hub, notification, jobs, ... |
| 4 | 5 | pct, oros, ubomi, pharmacy, inventory, msika, ... |
| 5 | 4 | vito, varapi, tuso, zibo |
| 6 | 3 | tshepo |
| 7 | 2 | envoy, opa |
| 8 | 1 | keycloak, hapi-fhir, orthanc |
| 9 | 0 | kafka, redis, minio, postgres (last) |

**postgres stops last** to ensure all services have cleanly disconnected.

## Profile-Specific Start Lists

### dev-lite
Layers 0, 1 (no orthanc), 2, 3, 4, 5 (pct+oros only), 7 (bff+ui only)

### dev-full
All layers 0–7

### integration
All layers 0–8

### pilot
All layers 0–8 (same as integration, different env config)
