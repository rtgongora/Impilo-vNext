# Repository Guidelines

## Project Overview

Impilo vNext is a **Health Operating System (HOS)** — a sovereign-grade, security-first national digital health platform. It is a governed execution environment, not a single application. The platform enforces person-centered identity, consent, audit, and RBAC/ABAC policy across every transaction.

> **Doctrine**: One Health OS, one experience shell, one person anchor, many roles, many contexts, one governed runtime. Full doctrine: [`docs/doctrine/health-os-doctrine.md`](docs/doctrine/health-os-doctrine.md)

### Experience orchestration layer (canonical)

There is **one** actor-facing web orchestration layer: zones, routes, trust-header-aware
API usage, and BFF-backed flows that operators and citizens see as a single Impilo
experience. The workspace and Docker artifact **`one-ui-shell`** is how that layer is built
and shipped; it is **not** a second product alongside “Experience.” Deprecated paths (for
example `ui/experience/`) and multiple Keycloak **client** names are continuity and wiring
only — they MUST NOT imply a second default web entry or a parallel UX stack. See doctrine
[§2.0](docs/doctrine/health-os-doctrine.md).

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

**Canonical seven architectural planes** (source of truth: `docs/architecture/planes/00-production-plane-doctrine.md`):
- **trust** — Trust, Identity Assurance & Governance Plane
- **registry** — Registry & Sovereign Identity Spine
- **clinical** — Clinical Execution & Shared Health Record Plane
- **data** — Data, Intelligence & Public Health Plane
- **integration** — Integration, Interoperability & Edge Plane
- **experience** — Experience, Workflow & Orchestration Plane
- **enterprise** — Enterprise Resource & Market Operations Plane

### Architecture guardrails (mandatory)

- Do not invent new plane names.
- Do not create duplicate system-of-record functionality.
- Do not move service folders by plane unless explicitly approved.
- Do not treat `secondary_planes` as ownership.
- Do not add mocks/stubs to production execution paths.
- Before adding a feature, check `docs/registry/services-registry.yaml` and `docs/registry/system-of-record-map.md`.
- Before creating a new service, prove no existing service already owns the capability.
- Backend work is incomplete until wired through BFF/API contracts and surfaced in the relevant experience layer.
- Frontend work is incomplete until backed by real APIs and real service logic.
- All production routes must have authz, audit, error handling, observability, and tests.

### Core Transaction Doctrine Compliance

- Every feature must map to a `CoreTransactionType` and lifecycle stage.
- Every feature must map to Person, Provider, and/or Platform journey stages where applicable.
- Orphan features are not allowed; each capability must show journey placement.
- Do not create duplicate truth for patient/provider/facility/service/terminology/payment/consent/clinical data.
- Backend-only work is incomplete until wired through user journey surfaces where applicable.
- Frontend-only work is incomplete unless connected to domain truth or explicitly marked as prototype.
- Experience composes and orchestrates; it does not own sovereign domain truth.
- Experience BFF composes sovereign truths; it must not become source-of-truth for clinical/registry/trust/finance.
- Meaningful actions must carry state transition, event emission, permission meaning, and audit trace.
- Implementations must consider failure and offline/federated behavior where relevant.
- Relevant user-facing flows must include Nompilo guidance, accessibility, and feedback capture consideration.
- Nompilo must not override provider judgement or become an unaudited decision channel.
- Preserve existing services and avoid deleting or breaking unrelated work.
- Use `docs/templates/CORE_TRANSACTION_FEATURE_ALIGNMENT_CHECKLIST.md` before calling a feature done.

### Implementation integrity checklist (required)

- For every user-facing feature, prove the chain: **web route/mobile screen -> hook/client -> BFF endpoint -> service -> contract -> tests**.
- Never mark fixture-backed UX as live; use explicit maturity labels in internal/dev-facing surfaces.
- Avoid accidental web/mobile drift: if parity is intentionally not in scope, document it in `docs/audits/*`.
- Do not leave empty handlers (`onClick={() => {}}`, disabled flows without reason) in high-value production surfaces.

### Social Timeline / Communities / Pages

- **Bounded context**: lives in `community-service` under the
  `zw.gov.mohcc.impilo.community.social` package. Do **not** create a
  separate `social-service` — extend the existing context.
- **Contract**: `contracts/openapi/social.openapi.yaml`. Mirror DTO
  shapes in `services/community-service/.../social/api/dto/SocialDtos.java`
  and the OpenAPI file together.
- **DB**: tables prefixed `social_` in the `community` schema (`V002__social.sql`).
- **BFF surface**: `/internal/v1/social/**` (shared web/mobile),
  `/internal/v1/mobile/citizen/social/**`,
  `/internal/v1/mobile/provider/social/**`, and the legacy citizen feed
  wrapper at `/internal/v1/mobile/citizen/feed`.
- **Nompilo composer assist**: BFF endpoint
  `/internal/v1/social/composer/assist` proxies the LLM Orchestration
  Service with deterministic offline fallbacks. Preserve the fallback
  behaviour when changing.
- **Runbook**: [`docs/architecture/SOCIAL_TIMELINE.md`](docs/architecture/SOCIAL_TIMELINE.md).

Full port allocation: [`docs/runbooks/port-allocation.md`](docs/runbooks/port-allocation.md)

**Non-obvious architectural constraints**:
- Every request flows **Envoy → TSHEPO ext_authz** before reaching any service. Never bypass this.
- **No PII in BUTANO (SHR)**. BUTANO stores FHIR resources keyed by CPID only; PII lives in VITO.
- Every service must have an **`event_outbox` table** for reliable Kafka publishing (outbox pattern).
- DB migrations use **Flyway** with `V001__init.sql`, `V002__*.sql` naming in `src/main/resources/db/migration/`.
- Java package root: `zw.gov.mohcc.impilo.<service>`

**Trust header contract** (all outbound requests must carry these — see [`ui/one-ui-shell/src/lib/api-client.ts`](ui/one-ui-shell/src/lib/api-client.ts)):
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
cd ui && npm run dev:shell          # Impilo web experience (orchestration layer) → port 3000
cd ui/one-ui-shell && npm run dev   # Same layer (direct workspace)
cd ui/ehr && npm run dev            # EHR UI → port 3002

# Tests (same orchestration layer codebase)
cd ui/one-ui-shell && npm test                  # Vitest unit tests
cd ui/one-ui-shell && npm run test:coverage     # With coverage
cd ui/one-ui-shell && npm run e2e               # Playwright E2E
cd ui/one-ui-shell && npm run test:routes       # Route parity check

# Type check
cd ui/one-ui-shell && npm run type-check
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
