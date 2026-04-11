# Runtime Orchestration Cleanup Report

## Overview
This document records all runtime orchestration issues identified and resolved during the Cross-Service Integration Closure Wave.

## UI Port Conflicts — RESOLVED

### Conflict 1: Port 3006
- **butano-web** (3006) vs **support-console** (3006)
- **Resolution**: support-console moved to port 3019
- **File changed**: `ui/support-console/package.json`

### Conflict 2: Port 3007
- **developer-console** (3007) vs **pct-web** (3007)
- **Resolution**: pct-web moved to port 3021
- **File changed**: `ui/pct-web/package.json`

## Canonical UI Port Map

| App | Port | Status |
|-----|------|--------|
| one-ui-shell | 3000 | Active — Trust layer shell |
| ops-console | 3001 | Active — VITO ops dashboard |
| ehr | 3002 | **DEPRECATED** — superseded by experience |
| portal | 3003 | Active — Citizen portal |
| ops-docs | 3004 | Active — Ops documentation |
| self-service | 3005 | Active — Self-service (MINIMAL) |
| butano-web | 3006 | Active — FHIR/SHR management |
| developer-console | 3007 | Active — Developer portal |
| zibo-web | 3008 | Active — Terminology management |
| oros-web | 3009 | Active — Orders & Results |
| pharmacy-web | 3010 | Active — Pharmacy dispensing |
| inventory-web | 3011 | Active — Inventory management |
| msika-web | 3012 | Active — Product registry |
| msika-flow-vendor | 3013 | Active — Vendor portal |
| msika-flow-ops | 3014 | Active — Procurement ops |
| costa-console | 3015 | Active — Costing console |
| mushex-payer-portal | 3016 | Active — Payer portal |
| mushex-finance-console | 3017 | Active — MUSheX finance |
| mushex-ops-console | 3018 | Active — MUSheX ops |
| support-console | 3019 | Active — Support/helpdesk (moved from 3006) |
| experience | 3020 | Active — Main clinical UI |
| pct-web | 3021 | Active — Patient Care Tracker (moved from 3007) |

## ui/ehr Disposition — DEPRECATED

- **Classification**: FRAGILE (0 source files, package.json only with 4 skeleton components)
- **Superseded by**: `ui/experience` — 125 source files, 80+ pages across 17 zones
- **Action taken**: Added `ui/ehr/DEPRECATED.md` with explicit deprecation notice
- **Port 3002**: Reserved/unassigned
- **Rationale**: The completeness audit explicitly states "Empty — package.json only. Superseded by experience app"

## Service Port Conflicts — DOCUMENTED

Not all services run simultaneously. The docker-compose.runtime.yml includes only the integration-critical subset.

**Port conflicts (resolved):** Phase A0 assigned unique `localhost` defaults per service and aligned the Experience BFF. See **[`docs/runbooks/port-allocation.md`](../runbooks/port-allocation.md)**.

**Mitigation:** Continue to use `${SERVER_PORT:…}` overrides for any host-specific deployment; compose can map non-default host ports when needed.

## Canonical Runtime Path

### docker-compose.runtime.yml (updated)
The canonical integration runtime includes:

**Infrastructure**: PostgreSQL 16, Redis 7, Kafka 3.7 (KRaft), Keycloak 25 (with realm import), MinIO, HAPI FHIR R4

**Edge**: Envoy 1.31, OPA 0.68

**Backend Services**: TSHEPO (8081), VITO (8082), VARAPI (8083), TUSO (8084), ZIBO (8085), PCT (8088), OROS (8089), Experience BFF (8160)

**UI**: Experience UI (3020)

### How to run
```bash
# Build JARs first
docker compose -f docker-compose.build.yml build

# Start the runtime
docker compose -f docker-compose.runtime.yml up -d

# Or use the dev runtime script
./scripts/dev-runtime.sh up
```

### Changes in this wave
1. Keycloak now imports the `impilo` realm automatically via `--import-realm`
2. Realm JSON mounted from `tools/auth/impilo-realm.json`
3. UI port conflicts resolved (support-console → 3019, pct-web → 3021)
4. ui/ehr formally deprecated

## Remaining Gaps
- Service port conflicts for non-runtime-compose services are documented but not changed (they use SERVER_PORT env override pattern)
- For a full-stack integration test requiring support-service or notification-service, they would need to be added to docker-compose.runtime.yml with their correct ports
