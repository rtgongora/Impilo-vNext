# Impilo vNext — Quickstart

Get the platform running in 3 commands.

## Prerequisites

- Docker Desktop 24+ with 8 GB+ RAM allocated
- `docker compose version` returns v2.x

## Start

```bash
# 1. Start the platform (dev-lite: core services + UI)
./scripts/runtime/platformctl.sh up lite

# 2. Bootstrap auth, topics, schemas
./scripts/runtime/platformctl.sh bootstrap

# 3. Run smoke tests
./scripts/runtime/platformctl.sh smoke
```

## Verify

Open http://localhost:3020 — you should see the Experience UI.

Check health:
```bash
./scripts/runtime/platformctl.sh status
```

Expected: all services show "healthy" or "Up".

## Stop

```bash
./scripts/runtime/platformctl.sh down
```

## Other Profiles

```bash
./scripts/runtime/platformctl.sh up full          # All services
./scripts/runtime/platformctl.sh up integration   # All + observability
```

## Key URLs

| Service | URL |
|---------|-----|
| Experience UI | http://localhost:3020 |
| Keycloak | http://localhost:8080 (admin/admin) |
| Envoy Gateway | http://localhost:10000 |
| TSHEPO | http://localhost:8081/actuator/health |

## Troubleshooting

```bash
./scripts/runtime/platformctl.sh logs              # All logs
./scripts/runtime/platformctl.sh logs tshepo       # Specific service
./scripts/runtime/platformctl.sh verify            # Readiness checks
```
