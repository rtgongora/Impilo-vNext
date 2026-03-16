# Impilo vNext — Environment Profiles

This directory contains environment profiles that control which platform services are started and how they are configured.

## Available Profiles

| Profile | File | Services | Use Case |
|---------|------|----------|----------|
| **dev-lite** | `dev-lite.env` | Infra + Keycloak + Edge + TSHEPO + core registries + PCT/OROS + Experience UI | Day-to-day development on a single feature area |
| **dev-full** | `dev-full.env` | All services layers 0–7 (no observability) | Full-platform local development |
| **integration** | `integration.env` | All services layers 0–8 (includes observability) | Cross-service integration testing, steel threads |
| **pilot** | `pilot.env` | All services, production-like settings | Pilot deployment, acceptance testing |

## Profile Selection

Profiles are selected via the `platformctl.sh` command:

```bash
# Start with dev-lite (default)
./scripts/runtime/platformctl.sh up lite

# Start with full dev
./scripts/runtime/platformctl.sh up full

# Start integration (includes observability)
./scripts/runtime/platformctl.sh up integration

# Start pilot
./scripts/runtime/platformctl.sh up pilot
```

## What Each Profile Includes

### dev-lite (fastest startup, ~8 services)
- PostgreSQL, Redis, Kafka, MinIO
- Keycloak (identity), HAPI FHIR
- OPA + Envoy (gateway)
- TSHEPO (trust)
- VITO, VARAPI, TUSO, ZIBO (registries)
- PCT, OROS (clinical core)
- Experience BFF + UI

### dev-full (all functional services, ~40+ services)
- Everything in dev-lite
- Orthanc PACS
- TSHEPO decomposition services
- All clinical services (ubomi, pharmacy, inpatient, etc.)
- All supply chain services (msika, inventory, etc.)
- All operations services (integration-hub, notifications, jobs, etc.)
- All UIs and consoles

### integration (dev-full + observability)
- Everything in dev-full
- Prometheus, Grafana, OTel Collector, Jaeger
- OAuth2 JWT validation enabled

### pilot (production-like)
- Everything in integration
- Production-like credential requirements
- All security settings enabled

## Customizing

1. Copy the closest profile to `.env` in the project root:
   ```bash
   cp ops/runtime/environments/dev-lite.env .env
   ```
2. Edit `.env` to override specific values
3. Run `platformctl.sh` — it reads `.env` automatically

## Security Notes

- **Never commit `.env` files** with real credentials
- The `pilot.env` contains `CHANGE_ME_FOR_PILOT` placeholders — replace before use
- Dev profiles use default passwords suitable only for local development
