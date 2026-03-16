# Impilo vNext — Bootstrap Guide

## What Bootstrap Does

Bootstrap initializes the platform's runtime dependencies that cannot be configured through Docker Compose alone. It runs **after** infrastructure is up but **before** you start using the platform.

## Bootstrap Scripts

| Script | Purpose | Depends On |
|--------|---------|-----------|
| `bootstrap-app-config.sh` | Verifies .env, vendor dirs, config files | Nothing (runs first) |
| `bootstrap-auth.sh` | Imports Keycloak realm, verifies users/clients | Keycloak running |
| `bootstrap-topics.sh` | Creates 34 Kafka topics | Kafka running |
| `bootstrap-schemas.sh` | Validates databases exist, FHIR schema | Postgres, HAPI FHIR running |
| `bootstrap-seed-data.sh` | Seeds test facilities, patients, providers | Services running |

## Running Bootstrap

### All at once (recommended):
```bash
./scripts/runtime/platformctl.sh bootstrap
```

### Individual scripts:
```bash
./scripts/bootstrap/bootstrap-app-config.sh
./scripts/bootstrap/bootstrap-auth.sh
./scripts/bootstrap/bootstrap-topics.sh
./scripts/bootstrap/bootstrap-schemas.sh
./scripts/bootstrap/bootstrap-seed-data.sh
```

## Idempotency

All bootstrap scripts are idempotent — **safe to run multiple times**:

- `bootstrap-auth.sh`: Checks if realm exists before importing. Skips if already present.
- `bootstrap-topics.sh`: Checks each topic before creating. Skips existing topics.
- `bootstrap-schemas.sh`: Reads database state, never writes.
- `bootstrap-seed-data.sh`: Checks for existing data before seeding.
- `bootstrap-app-config.sh`: Creates files only if missing.

## Rerun Behavior

| Scenario | What Happens |
|----------|-------------|
| First run | Everything gets created |
| Second run | Everything gets skipped (already exists) |
| After `docker volume rm` | Everything gets re-created |
| After Keycloak restart | Realm persists (in postgres) — skips import |
| After Kafka restart | Topics may need re-creation (in-memory for dev) |

## Auth Bootstrap Details

The auth bootstrap:
1. Waits for Keycloak to be healthy (up to 120s)
2. Obtains an admin token from the master realm
3. Checks if the `impilo` realm exists
4. If not, imports from `tools/auth/impilo-realm.json`
5. Verifies test user `dr.mapfumo` exists
6. Verifies test client `integration-test` exists
7. Obtains and prints a test JWT token

### Realm contents:
- **Roles**: SYSTEM_ADMIN, FACILITY_ADMIN, CLINICIAN, NURSE, PHARMACIST, etc.
- **Test users**: dr.mapfumo (CLINICIAN)
- **Clients**: integration-test, impilo-ui

## Kafka Topic Bootstrap Details

Creates topics organized by domain:

| Domain | Topics |
|--------|--------|
| Trust & Governance | platform.audit.events, tshepo.audit.events, platform.identity.events, platform.consent.events, platform.keys.events, platform.offline.events |
| Core Registries | vito.patient.events, varapi.provider.events, tuso.facility.events, zibo.terminology.events |
| Clinical | pct.encounter.events, oros.order.events, oros.result.events, ubomi.visit.events |
| Supply Chain | pharmacy.*, inventory.*, elmis.*, msika.* |
| Finance | mushex.claim.events, costing.allocation.events |
| Platform | platform.outbox.relay, platform.dlq |

Default: 3 partitions, replication factor 1 (single-node dev).

## Troubleshooting

### "Keycloak not ready within 120s"
Keycloak is slow to start, especially on first run.
```bash
docker logs impilo-keycloak --tail 50
# Wait and retry
./scripts/bootstrap/bootstrap-auth.sh
```

### "Failed to obtain admin token"
Keycloak admin credentials may have changed.
```bash
# Check actual credentials
grep KEYCLOAK_ADMIN .env
# Verify via UI: http://localhost:8080
```

### "Kafka not ready within 60s"
```bash
docker logs impilo-kafka --tail 30
# Verify Kafka is running
docker ps | grep kafka
```

### "Database 'X' not accessible"
The init SQL runs only on first postgres startup. If you created the volume before the SQL was added:
```bash
docker volume rm impilo-vnext_pg_data
./scripts/runtime/platformctl.sh up lite
```

## Environment Variables

Bootstrap scripts read from the environment or use defaults:

| Variable | Default | Used By |
|----------|---------|---------|
| KEYCLOAK_URL | http://localhost:8080 | bootstrap-auth |
| KEYCLOAK_ADMIN | admin | bootstrap-auth |
| KEYCLOAK_ADMIN_PASSWORD | admin | bootstrap-auth |
| KAFKA_BOOTSTRAP | localhost:9092 | bootstrap-topics |
| KAFKA_CONTAINER | impilo-kafka | bootstrap-topics |
| USE_DOCKER | true | bootstrap-topics |
| POSTGRES_HOST | localhost | bootstrap-schemas |
| POSTGRES_PORT | 5432 | bootstrap-schemas |
| POSTGRES_USER | impilo | bootstrap-schemas |
| POSTGRES_PASSWORD | changeme | bootstrap-schemas |
