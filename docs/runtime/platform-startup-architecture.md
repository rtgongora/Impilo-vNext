# Impilo vNext — Platform Startup Architecture

## Overview

The Impilo vNext platform uses a **layered startup model** where components are organized into dependency-ordered layers. Each layer must be healthy before the next layer starts. This ensures deterministic, reproducible platform startups.

## Layered Startup Model

```
Layer 0: Infrastructure     ─── postgres, redis, kafka, minio
    │
Layer 1: Shared Services    ─── keycloak, hapi-fhir, orthanc
    │
Layer 2: Edge               ─── opa, envoy
    │
Layer 3: Ring 0 (Trust)     ─── tshepo (trust & governance)
    │
Layer 4: Ring 1 (Registries)─── vito, varapi, tuso, zibo
    │
Layer 5: Ring 2 (Clinical)  ─── pct, oros, ubomi, pharmacy, inventory, ...
    │
Layer 6: Operations         ─── integration-hub, notifications, jobs, ...
    │
Layer 7: Applications       ─── experience-bff, experience-ui, consoles
    │
Layer 8: Observability      ─── prometheus, grafana, otel-collector, jaeger
```

## Dependency Graph

```
postgres ──┬── keycloak ──┬── tshepo ──┬── vito ──── pct ──── experience-bff ──── experience-ui
           │              │            │── varapi ── oros
           │              │            │── tuso
           ├── hapi-fhir  │            └── zibo
           │              │
redis ─────┘              └── envoy ─── (all v1.1 traffic)
           │
kafka ─────┘     opa ─────┘
           │
minio ─────┘
```

### Key dependencies:
- **All Java services** depend on: postgres, redis, kafka
- **Keycloak** depends on: postgres (for its database)
- **HAPI FHIR** depends on: postgres (butano database)
- **Envoy** depends on: OPA (ext_authz policy engine)
- **Experience BFF** depends on: postgres
- **Experience UI** depends on: experience-bff

## Profiles

| Profile | Layers | Services | Startup Time | Use Case |
|---------|--------|----------|-------------|----------|
| dev-lite | 0-5,7 (subset) | ~15 | ~2-3 min | Day-to-day dev |
| dev-full | 0-7 | ~40+ | ~5-8 min | Full platform dev |
| integration | 0-8 | ~45+ | ~6-10 min | Integration testing |
| pilot | 0-8 | ~45+ | ~6-10 min | Acceptance testing |

## Manifest

The canonical list of all components, their layers, ports, dependencies, and healthchecks is in:

```
ops/runtime/platform-manifest.yaml
```

## Compose File Organization

Each layer has its own compose file:

| File | Layer | Contents |
|------|-------|----------|
| `docker-compose.infra.yml` | 0 | postgres, redis, kafka, minio |
| `docker-compose.shared.yml` | 1 | keycloak, hapi-fhir, orthanc |
| `docker-compose.edge.yml` | 2 | opa, envoy |
| `docker-compose.kernel.yml` | 3-4 | tshepo, vito, varapi, tuso, zibo |
| `docker-compose.operations.yml` | 5-6 | pct, oros, clinical, bff, ops services |
| `docker-compose.apps.yml` | 7 | experience-ui |
| `docker-compose.observability.yml` | 8 | prometheus, grafana, otel, jaeger |

All files are in `ops/runtime/` and share the `impilo-network` Docker network.

## Readiness Model

The platform uses **active health polling** (not sleep-based waiting):

1. **Infrastructure**: TCP port checks (pg_isready, redis-cli ping, kafka-broker-api-versions)
2. **Shared services**: HTTP health endpoints (Keycloak `/health/ready`, HAPI FHIR `/fhir/metadata`)
3. **Java services**: Spring Boot Actuator (`/actuator/health` → `{"status":"UP"}`)
4. **Edge**: Envoy admin (`/ready`), OPA (`/health`)
5. **UI**: HTTP root page check

Each service has:
- `healthcheck` in Docker Compose (container-level)
- `wait-for-readiness.sh` polling (orchestration-level)
- Configurable timeout per service type

## Shutdown Model

Shutdown proceeds in **reverse layer order** (Layer 8 → Layer 0):

1. Stop observability (non-critical)
2. Stop UIs and BFF
3. Stop operation services
4. Stop clinical services
5. Stop kernel (registries, trust)
6. Stop edge (envoy, opa)
7. Stop shared (keycloak, fhir)
8. Stop infrastructure (postgres, redis, kafka, minio)

This ensures:
- No service processes requests against a stopped dependency
- Graceful connection draining
- Data integrity (postgres stops last)

## Bootstrap Model

After infrastructure is running, bootstrap scripts initialize:

1. **App config** (`bootstrap-app-config.sh`): .env, vendor caches, config files
2. **Auth** (`bootstrap-auth.sh`): Keycloak realm, test users, test clients
3. **Topics** (`bootstrap-topics.sh`): 34 Kafka topics
4. **Schemas** (`bootstrap-schemas.sh`): Database existence, FHIR schema
5. **Seed data** (`bootstrap-seed-data.sh`): Test facilities, patients, providers

All bootstrap scripts are **idempotent** — safe to run multiple times.

## Trust-First Architecture

Every request flows through the trust pipeline:

```
Client → Envoy (10000) → OPA ext_authz → Backend Service
```

The 14 trust headers (defined in `TrustHeaders.java` / `contracts.ts`) are:
- Enforced by OPA policies at the gateway
- Required for all `/internal/v1/` and `/external/v1/` paths
- Validated by each service's `V11HeaderFilter`
