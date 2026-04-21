# Repository Guidelines

## Project Overview

Impilo vNext is a **Health Operating System (HOS)** — a sovereign-grade, security-first national digital health platform. It is a governed execution environment, not a single application. The platform enforces person-centered identity, consent, audit, and RBAC/ABAC policy across every transaction.

> **Doctrine**: One Health OS, one experience shell, one person anchor, many roles, many contexts, one governed runtime. Full doctrine: [`docs/doctrine/health-os-doctrine.md`](docs/doctrine/health-os-doctrine.md)

---

## Project Structure & Module Organization

```
contracts/      OpenAPI, AsyncAPI, TypeScript type contracts (source of truth for APIs)
services/       60+ Java Spring Boot microservices (one per bounded context)
ui/             25+ Next.js frontend workspaces (managed via npm workspaces + Turborepo)
apps/mobile/    Citizen and provider mobile apps (pnpm workspace)
infra/          Envoy proxy config, Kubernetes manifests, observability
compose/        Docker Compose stacks (infra, experience, runtime)
docs/           Architecture docs, runbooks, ADRs, acceptance packs
tests/          Integration tests
```

**Seven architectural planes** (see `README.md`):
- **Trust & Governance**: TSHEPO (PDP, port 8081), Envoy ext_authz (PEP), Audit, Consent
- **Registry Spine**: VITO (clients, 8082), VARAPI (providers, 8083), TUSO (facilities, 8084), ZIBO (terminology, 8085)
- **Clinical Execution**: BUTANO/HAPI FHIR (SHR, 8090), PCT (8088), OROS (8089), Pharmacy (8096), Inpatient (8121)
- **Finance**: MUSheX (8102), Costing Engine
- **Integration/Ops**: Integration Hub, Offline Sync (8095), Document Service (8093), Notification (8200), Jobs (8109)
- **Experience**: One UI Shell (3000), Experience UI (3020), EHR (3002), Portal (3003), Ops Console (3001)
- **Enterprise Resource**: General Ledger, HR & Payroll, Procurement

Full port allocation: [`docs/runbooks/port-allocation.md`](docs/runbooks/port-allocation.md)

**Non-obvious architectural constraints**:
- Every request flows **Envoy → TSHEPO ext_authz** before reaching any service. Never bypass this.
- **No PII in BUTANO (SHR)**. BUTANO stores FHIR resources keyed by CPID only; PII lives in VITO.
- Every service must have an **`event_outbox` table** for reliable Kafka publishing (outbox pattern).
- DB migrations use **Flyway** with `V001__init.sql`, `V002__*.sql` naming in `src/main/resources/db/migration/`.
- Java package root: `zw.gov.mohcc.impilo.<service>`

**Trust header contract** (all outbound requests must carry these — see [`ui/experience/src/lib/api-client.ts`](ui/experience/src/lib/api-client.ts)):
- Mandatory: `X-Tenant-ID`, `X-Pod-ID`, `X-Request-ID`, `X-Correlation-ID`
- Actor: `X-Actor-ID`, `X-Actor-Type`, `X-Provider-ID`
- Context: `X-Facility-ID`, `X-Department-ID`, `X-Ward-ID`, `X-Workspace-ID`, `X-Programme-ID`, `X-Shift-ID`
- Governance: `X-Purpose-Of-Use`, `X-Assurance-Level`, `X-Access-Mode`

---

## Build, Test, and Development Commands

### Backend (Java / Maven)

```bash
# Build all services
cd services && ./mvnw clean install -DskipTests

# Run a single service
cd services/tshepo-service && ./mvnw spring-boot:run

# Run tests for a single service
cd services/tshepo-service && ./mvnw test

# Run a single test class
cd services/tshepo-service && ./mvnw test -Dtest=TshepoGoldenContractIT
```

### Frontend (Next.js / npm workspaces + Turborepo)

```bash
# From ui/ root — build, lint, typecheck all workspaces
cd ui && npm run build
cd ui && npm run lint
cd ui && npm run type-check

# Dev server for a specific workspace
cd ui && npm run dev:shell          # One UI Shell → port 3000
cd ui/experience && npm run dev     # Experience UI → port 3020
cd ui/ehr && npm run dev            # EHR UI → port 3002

# Tests (Experience UI)
cd ui/experience && npm test                  # Vitest unit tests
cd ui/experience && npm run test:coverage     # With coverage
cd ui/experience && npm run e2e               # Playwright E2E
cd ui/experience && npm run test:routes       # Route parity check

# Type check (Experience UI)
cd ui/experience && npm run type-check
```

### Infrastructure

```bash
cp .env.example .env
docker compose up -d                          # Full stack (infra + services)
docker compose -f compose/experience/docker-compose.yml up -d   # Experience stack only
bash compose/experience/smoke-test.sh         # Smoke test after compose up
```

---

## Coding Style & Naming Conventions

### TypeScript / Frontend

- **TypeScript strict mode** is enabled (`"strict": true` in `tsconfig.json`)
- Path alias `@/*` maps to `./src/*`
- ESLint extends `next/core-web-vitals` + `next/typescript`
- Enforced rules: `prefer-const: error`, `no-console` (warn, allow `warn`/`error`)
- Warned rules: `no-unused-vars`, `@typescript-eslint/no-explicit-any`, `react-hooks/exhaustive-deps`
- EHR/clinical components (`src/components/ehr/**`, `src/components/clinical/**`, `src/engines/**`) have relaxed unused-var and explicit-any rules
- Prefix unused variables/params with `_` to suppress warnings

### Java / Backend

- Package: `zw.gov.mohcc.impilo.<service-name>`
- Java 21, Spring Boot 3.3.6
- Every service's Kafka publishing goes through an outbox table — never publish directly to Kafka in service code
- Trust header constants live in `TrustHeaders.java` (TSHEPO service); reference those, don't hardcode strings

---

## Testing Guidelines

### Frontend (Experience UI)

- Framework: **Vitest** with jsdom environment
- Test files: `src/**/*.test.{ts,tsx}`
- Setup: `src/test/setup.ts`
- E2E: **Playwright** (`playwright.config.ts` at `ui/experience/`)
- Run unit: `npm test` | Run E2E: `npm run e2e`

### Backend

- Framework: **JUnit 5** with Spring Boot Test (`@SpringBootTest`)
- Integration tests suffixed `IT` (e.g., `TshepoGoldenContractIT.java`)
- Unit tests suffixed `Test`

---

## Commit & Pull Request Guidelines

Use **Conventional Commits** — observed from git history:

```
feat:      new feature or capability
fix:       bug fix
refactor:  code restructuring without behavior change
chore:     build, config, tooling changes
docs:      documentation only
test:      adding or fixing tests
```

**Workflow rules** (from `CLAUDE.md`):
1. **One commit per logical change** — never bundle unrelated changes
2. **Push after every commit** — `git push` immediately after `git commit`
3. After completing a milestone wave: commit → `git pull --rebase` → push
4. Never leave a finished wave uncommitted or unpushed

---

## Architecture Decision Records

ADRs live in [`docs/adr/`](docs/adr/). Before introducing structural changes (new service, new DB schema, new cross-service pattern), check if an ADR covers it. Significant decisions should be documented as a new ADR.

## Access Control

Every access decision evaluates 10 dimensions: person identity (Health ID), active role, role identifier (Provider ID / Staff ID), org affiliation, facility context, subject relationship, purpose of use, consent/legal basis, assurance level, and workflow state. Do not shortcut policy checks — all enforcement goes through TSHEPO `PolicyEngine.java`.
