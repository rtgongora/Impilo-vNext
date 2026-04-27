# Impilo vNext — First Build Guide

> Branch: `claude/staging-ux-orchestration-remediation-Yypyl`
>
> Last verified: 2026-04-13

---

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Java | 21 (Eclipse Temurin or GraalVM) | Backend services |
| Maven | 3.9+ | Java build system |
| Node.js | >= 20 LTS | Frontend tooling |
| pnpm | >= 9.0 | Package manager (all JS/TS projects) |
| Docker | 24+ | Infrastructure containers |
| Docker Compose | v2+ | Multi-container orchestration |

### Install pnpm (if not present)

```bash
corepack enable
corepack prepare pnpm@latest --activate
```

---

## 1. Clone and Checkout

```bash
git clone https://github.com/rtgongora/Impilo-vNext.git
cd Impilo-vNext
git checkout claude/staging-ux-orchestration-remediation-Yypyl
```

---

## 2. Start Infrastructure

The platform depends on PostgreSQL, Redis, Kafka (KRaft mode), and Keycloak. Start them first.

```bash
docker compose -f docker-compose.runtime.yml up -d
```

Wait for all services to become healthy:

```bash
docker compose -f docker-compose.runtime.yml ps
```

| Container | Port | Purpose |
|-----------|------|---------|
| PostgreSQL | 5432 | Relational store for all services |
| Redis | 6379 | Caching, session, rate-limiting |
| Kafka | 9092 | Event backbone (KRaft mode, no ZK) |
| Keycloak | 8080 | Identity provider / OAuth2 |

### Seed the database

```bash
# Create databases for all services
psql -h localhost -U postgres < scripts/seed/init-databases.sql

# Create Kafka topics
bash scripts/bootstrap/bootstrap-topics.sh
```

---

## 3. Build Java Services

All Java services share a parent POM. Building from root resolves shared libraries (`tech-companion`, `shared-kernel-java`, `tshepo-contracts`) automatically.

### Full build (all services)

```bash
mvn clean package -DskipTests
```

### Build only the Experience BFF

```bash
mvn clean package -pl services/experience-bff -am -DskipTests
```

`-am` (also-make) builds the parent POM and shared library dependencies.

### Run the Experience BFF

```bash
cd services/experience-bff
mvn spring-boot:run
```

The BFF starts on **port 8160**. It proxies to sovereign services — if any downstream service is unavailable, the BFF will return graceful fallbacks (empty arrays) thanks to resilience4j circuit breakers. For **patient summary** including **Mvumo** `consentSummary`, run **mvumo-service** (`8195`) and set **`MVUMO_BASE_URL`** on the BFF (see `docs/architecture/patient-care-consent-surface.md`).

### Environment overrides

All service URLs default to `localhost` with ports from `docs/runbooks/port-allocation.md`. Override via environment variables:

```bash
# Example: point BFF at remote PCT service
PCT_BASE_URL=http://pct-host:8088 mvn spring-boot:run
```

Key environment variables (see `application.yml` for full list):

| Variable | Default | Service |
|----------|---------|---------|
| `PCT_BASE_URL` | `http://localhost:8088` | Patient Care Tracker |
| `VITO_BASE_URL` | `http://localhost:8082` | Identity registry |
| `PHARMACY_BASE_URL` | `http://localhost:8096` | Pharmacy |
| `KEYCLOAK_URL` | `http://localhost:8080` | Keycloak |
| `KEYCLOAK_REALM` | `impilo` | OAuth2 realm |
| `REDIS_HOST` | `localhost` | Redis cache |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka |

---

## 4. Build the Impilo web experience (orchestration layer)

The **only** canonical user-facing web layer is the Impilo **web experience** — one orchestration surface (zones, routes, BFF-backed flows). It is developed in **`ui/one-ui-shell`** and shipped as the Compose/Kubernetes service **`one-ui-shell`**; that folder name is packaging, not a second product beside “Experience.” Stack: Next.js 14, TailwindCSS, TanStack Query, Zustand.

```bash
cd ui/one-ui-shell
npm install
```

### Type-check (verify compilation)

```bash
npm run type-check
```

### Development server

```bash
NEXT_PUBLIC_BFF_URL=http://localhost:8160 npm run dev
```

Opens on **port 3000** — visit `http://localhost:3000`.

### Production build

```bash
NEXT_PUBLIC_BFF_URL=http://localhost:8160 npm run build
npm start
```

### Run tests

```bash
npm test              # Vitest unit tests
npm run test:coverage     # With coverage report
npm run e2e               # Playwright E2E (requires dev server running)
```

---

## 5. Build the Mobile Apps

Both mobile apps use Expo 52 / React Native 0.76 and share workspace packages.

### Install all dependencies

```bash
cd apps/mobile
pnpm install
```

This resolves the 7 shared `workspace:*` packages:

| Package | Purpose |
|---------|---------|
| `@impilo/mobile-api-client` | HTTP client with trust headers |
| `@impilo/mobile-auth` | Authentication and token management |
| `@impilo/mobile-design-system` | UI primitives, tokens, clinical components |
| `@impilo/mobile-messaging` | Real-time messaging |
| `@impilo/mobile-offline` | Offline-first sync |
| `@impilo/mobile-timeline` | Health timeline |
| `@impilo/mobile-trust` | Trust header and session utilities |

### Citizen App

```bash
cd citizen-app
pnpm type-check        # TypeScript validation
pnpm start             # Expo dev server
pnpm android           # Run on Android emulator
pnpm ios               # Run on iOS simulator
pnpm test              # Vitest unit tests
```

### Provider App

```bash
cd ../provider-app
pnpm type-check
pnpm start
pnpm android
pnpm ios
pnpm test
```

### EAS Build (production binaries)

```bash
# Requires Expo account and eas-cli
npx eas-cli build --platform android
npx eas-cli build --platform ios
npx eas-cli build --platform all
```

---

## 6. Full Local Stack (Experience Slice)

To run the complete Experience slice locally:

```bash
# Terminal 1 — Infrastructure
docker compose -f docker-compose.runtime.yml up -d

# Terminal 2 — BFF
cd services/experience-bff
mvn spring-boot:run

# Terminal 3 — UI (web experience orchestration layer)
cd ui/one-ui-shell
NEXT_PUBLIC_BFF_URL=http://localhost:8160 npm run dev
```

Then open `http://localhost:3000` in your browser.

For the experience-specific Docker Compose (BFF + UI containerised):

```bash
docker compose -f compose/experience/docker-compose.yml up -d
```

---

## 7. Port Allocation Reference

Full authoritative table: `docs/runbooks/port-allocation.md`

### Most-used ports

| Service | Port |
|---------|------|
| Web experience (`one-ui-shell`) | 3000 |
| Experience BFF | 8160 |
| Keycloak | 8080 |
| TSHEPO Authz | 8081 |
| VITO (Identity) | 8082 |
| VARAPI | 8083 |
| TUSO | 8084 |
| ZIBO | 8085 |
| MSIKA | 8086 |
| PCT | 8088 |
| OROS (Lab) | 8089 |
| BUTANO (FHIR) | 8090 |
| Pharmacy | 8096 |
| Notification | 8200 |
| Search | 8230 |
| Workflow | 8250 |
| Wellness | 8161 |
| Envoy (public) | 10000 |

---

## 8. Build Verification Checklist

Run through these after your first build to confirm everything is working:

- [ ] `docker compose -f docker-compose.runtime.yml ps` — all containers healthy
- [ ] `mvn clean package -pl services/experience-bff -am -DskipTests` — BUILD SUCCESS
- [ ] BFF health check: `curl http://localhost:8160/actuator/health` returns `{"status":"UP"}`
- [ ] `cd ui/one-ui-shell && npm run type-check` — zero errors
- [ ] `cd ui/one-ui-shell && npm run build` — completes without errors
- [ ] `http://localhost:3000` — Impilo logo (green shield) appears in browser tab
- [ ] `http://localhost:3000/auth/login` — Login page renders with green gradient branding
- [ ] `cd apps/mobile/citizen-app && pnpm type-check` — zero errors
- [ ] `cd apps/mobile/provider-app && pnpm type-check` — zero errors

---

## 9. Troubleshooting

### BFF fails to start — "Address already in use"

Another process is on port 8160. Kill it or override:

```bash
SERVER_PORT=8161 mvn spring-boot:run
```

### UI build fails — "Module not found"

Ensure `pnpm install` completed. If `@tanstack/react-query` is missing:

```bash
pnpm install --force
```

### Mobile — "Unable to resolve @impilo/mobile-design-system"

Run install from the workspace root, not from the individual app:

```bash
cd apps/mobile
pnpm install
```

### Kafka connection refused

Kafka needs 10-15 seconds to start in KRaft mode. Check:

```bash
docker compose -f docker-compose.runtime.yml logs kafka
```

### Keycloak realm not found

Import the Impilo realm on first run:

```bash
# Keycloak admin: http://localhost:8080/admin (admin/admin)
# Import realm from: infra/keycloak/impilo-realm.json (if available)
```

---

## 10. What Was Built (Session Summary)

This branch integrates work from 4 feature branches:

| Feature | What it adds |
|---------|-------------|
| **Impilo Branding** | Logo (shield + cross), green palette (#1F7A3A), Inter font, favicon, 240+ files migrated from blue to brand green |
| **Workflow Fixes** | Care-team, goals, care-plans forms wired to real API mutations; inventory stub replaced; ID services email delivery |
| **BFF Service Proxies** | 6 new service clients + controllers: Notification, Workflow, Support, Channels, Dispatch, Wellness |
| **Mobile Parity** | Citizen: finance, wellness challenges/programs, marketplace cart. Provider: facility admin, reports, finance, inventory |
| **Healthcare Coding Standards** | Shared terminology library, Flyway migrations, OpenAPI coding system URIs |
| **Privacy-by-Design** | PII masking, InactivityLock, PrivacyWatermark, consent flows, V39 privacy DB, account deletion |
