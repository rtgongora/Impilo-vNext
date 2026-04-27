# Experience Platform — Stage-1

## What is Stage-1?

Stage-1 delivers a **runnable, end-to-end Experience Platform** that:

1. **Replicates the Lovable prototype's UX/IA/routing** — 96 route pages across 15 zones, 4 layout variants, 3-zone sidebar navigation, hierarchical auth guards
2. **Replaces Supabase with the Impilo stack** — experience-bff (Spring Boot) with PostgreSQL, Flyway migrations, outbox pattern, v1.1 header enforcement
3. **Runs via Docker Compose** — Postgres (5433), Redis, Kafka, wellness-service, BFF, UI with healthchecks and smoke tests (mirrors root infra wiring for cache/event bus)

**Sovereign downstreams (full stack):** For chart **patient summary** with **Mvumo** `consentSummary`, run **mvumo-service** (`:8195`) and **pct-service** (`:8088`), and set **`MVUMO_BASE_URL`** (and `PCT_BASE_URL`) on **experience-bff** when not using defaults. See [`docs/architecture/patient-care-consent-surface.md`](../../docs/architecture/patient-care-consent-surface.md).

### Stage-1 Scope

| Area | Included |
|------|----------|
| Routes | All 96 page files (98 spec routes including redirects) |
| Golden Paths | A (Email Login), B (Provider ID), C (Queue→Encounter→Close), D (Admin), E (Marketplace), F (Registry) |
| BFF Endpoints | 25+ endpoints across auth, facilities, patients, queue, shifts, encounters, admin, pharmacy, inventory, marketplace, registry, reports |
| Persistence | PostgreSQL with 15 tables, Flyway migrations V1-V4 |
| v1.1 Compliance | Header enforcement, idempotency, outbox, error envelopes |
| Tests | GoldenContractIT, GoldenPathIntegrationTest, ExperienceBffIntegrationTest (30+ tests) |
| Verification | Route parity check, smoke tests, healthcheck validator |

### What is NOT included in Stage-1

| Area | Reason |
|------|--------|
| Kafka outbox publisher | Outbox writes to DB; Kafka publish deferred to Stage-2 |
| Upstream service proxying | BFF owns its own data; proxying to VITO/VARAPI/TUSO deferred |
| Full Keycloak OIDC | Auth endpoint returns session token; full OIDC deferred |
| EHR clinical data (vitals, orders, results) | Page stubs exist; data binding deferred to Stage-2 |
| Redis caching | BFF connects to Redis in compose for parity with full stack; many golden paths still work if Redis is empty |
| Envoy gateway | BFF exposed directly in dev compose |

## Running Locally

### Prerequisites

- Docker + Docker Compose
- Java 21 + Maven 3.9+ (for building from source)
- Node.js 20+ (for UI development)

### Quick Start (Docker Compose)

The Experience compose file starts **Postgres (5433), Redis (6379), Kafka (9092), wellness-service, experience-bff, experience-ui** — the same Redis/Kafka **roles** as the repo root [`docker-compose.yml`](../../docker-compose.yml), wired so Java services use `redis` / `kafka` on the Docker network (not `localhost` inside the container). Do not run root `redis` + `kafka` on the same host ports at the same time.

```bash
# Start everything (Maven JARs + compose)
./tools/dev/up.sh

# Or with image rebuild
./tools/dev/up.sh --build

# Optional: also bring up root Postgres + Keycloak (see ../../docker-compose.yml).
# Experience compose already binds 6379/9092 for Redis/Kafka — `./tools/dev/up.sh --infra` does not start root redis/kafka.
./tools/dev/up.sh --infra
```

**Windows (PowerShell):** Docker images for **wellness-service** and **experience-bff** expect **pre-built JARs** under `services/wellness-service/target/` and `services/experience-bff/target/`. Use:

```powershell
.\tools\dev\up.ps1              # Maven package, then compose up
.\tools\dev\up.ps1 -Build        # same + rebuild images
.\tools\dev\up.ps1 -SkipMaven    # compose only (JARs must exist)
```

**Linux/macOS:** `./tools/dev/up.sh` runs the same Maven slice then compose; use `--skip-maven` if you already built.

Or build once from `services/`: `mvn -B -pl wellness-service,experience-bff -am -DskipTests package`, then `docker compose -f compose/experience/docker-compose.yml up -d`.

### Building from Source

```bash
# Build all (BFF + UI)
./tools/dev/build-all.sh

# With Maven mirror
MAVEN_MIRROR_URL=http://nexus:8081/repository/maven-public/ ./tools/dev/build-all.sh

# With custom Maven settings
MAVEN_SETTINGS_FILE=/path/to/settings.xml ./tools/dev/build-all.sh
```

### Running Tests

```bash
# BFF integration tests (requires Docker for Testcontainers)
cd services && mvn -pl experience-bff test

# Route parity check (no Docker needed)
cd ui/experience && npm run test:routes
```

### Verification Scripts

```bash
# Smoke test (requires running services)
./tools/dev/smoke.sh

# Golden path smoke test
./tools/dev/golden-path-smoke.sh

# Healthcheck validator
./tools/dev/healthcheck.sh
```

## Service Ports

| Service | Port | URL |
|---------|------|-----|
| One UI Shell (unified Experience) | 3000 | http://localhost:3000 |
| Experience BFF | 8160 | http://localhost:8160 |
| Experience DB | 5433 | localhost:5433 |

## Architecture

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│ Experience   │────▶│ Experience  │────▶│ PostgreSQL  │
│ UI (Next.js) │     │ BFF (Spring)│     │ 16-alpine   │
│ :3000        │     │ :8160       │     │ :5433       │
└─────────────┘     └─────────────┘     └─────────────┘
                         │
                         ▼
                    ┌─────────────┐
                    │ event_outbox│
                    │ (write-only)│
                    └─────────────┘
```

## API Endpoints

### Auth
- `POST /internal/v1/auth/login` — Login (email or provider_id)
- `POST /internal/v1/auth/logout` — Logout
- `GET /internal/v1/auth/session` — Get session status

### Facilities
- `GET /internal/v1/facilities` — List facilities (paginated, filterable)

### Patients
- `GET /internal/v1/patients` — List patients (searchable)
- `GET /internal/v1/patients/{id}` — Get patient

### Queue
- `GET /internal/v1/queue/entries` — List queue entries
- `POST /internal/v1/queue/entries` — Create queue entry
- `POST /internal/v1/queue/entries/{id}/call` — Call patient
- `POST /internal/v1/queue/entries/{id}/complete` — Complete entry

### Shifts
- `GET /internal/v1/shifts/current` — Get current shift
- `POST /internal/v1/shifts/start` — Start shift
- `POST /internal/v1/shifts/{id}/end` — End shift

### Encounters
- `GET /internal/v1/encounters` — List encounters
- `GET /internal/v1/encounters/{id}` — Get encounter
- `POST /internal/v1/encounters` — Create encounter
- `POST /internal/v1/encounters/{id}/close` — Close encounter

### Admin
- `GET /internal/v1/admin/users` — List admin users
- `GET /internal/v1/admin/users/{id}` — Get admin user
- `GET /internal/v1/admin/audit` — List audit log
- `GET /internal/v1/admin/audit/{id}` — Get audit entry

### Pharmacy
- `GET /internal/v1/pharmacy/prescriptions` — List prescriptions
- `POST /internal/v1/pharmacy/dispense` — Dispense prescription

### Inventory
- `GET /internal/v1/inventory/items` — List inventory items

### Marketplace
- `GET /internal/v1/marketplace/orders` — List orders
- `GET /internal/v1/marketplace/orders/{id}` — Get order
- `POST /internal/v1/marketplace/orders` — Create order

### Registry
- `GET /internal/v1/registry/providers` — List providers
- `GET /internal/v1/registry/providers/{id}` — Get provider
- `GET /internal/v1/registry/facilities` — List facilities

### Reports
- `POST /internal/v1/reports/generate` — Generate report
