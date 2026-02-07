# Impilo vNext — Claude Code Project Rules

## Workflow Rules

### Small-Commit Workflow
- **Atomic Commits**: One commit per logical change (one component, one API route, one migration). Never bundle unrelated changes.
- **Conventional Commits**: Use standard format for all messages:
  - `feat:` new feature
  - `fix:` bug fix
  - `refactor:` code restructuring
  - `chore:` build/config/tooling
  - `docs:` documentation only
  - `test:` adding or fixing tests
- **Auto-Push**: Push every commit to remote immediately after creation.

### Adaptive Thinking
- Operate at **Effort: Max**.
- Run a **Blocker Check** before writing code for complex tasks:
  1. What files does this change touch?
  2. Are there dependencies that must exist first?
  3. Does this conflict with any existing code?
  4. What is the smallest testable unit of this change?

## Architecture Quick Reference

- **6 planes**: Trust & Governance, Registry Spine, Clinical Execution, Finance, Integration/Ops, Experience
- **Trust-first**: Every request flows through Envoy ext_authz → TSHEPO before reaching any service
- **No PII in SHR**: BUTANO (HAPI FHIR) uses CPID only; PII stays in VITO
- **Outbox pattern**: Every service has an `event_outbox` table for reliable Kafka publishing
- **Header contract**: 14 trust headers defined in `TrustHeaders.java` ↔ `contracts.ts` ↔ `envoy.yaml`

## Golden Thread (UI → DB proof path)
1. `ui/one-ui-shell/src/lib/apiClient.ts` — injects trust headers
2. `services/tshepo-service/.../api/AuthorizeController.java` — ext_authz endpoint
3. `services/tshepo-service/.../core/PolicyEngine.java` — RBAC/ABAC + serialized audit chain

## Tech Stack
- Java 21, Spring Boot 3.3.6, PostgreSQL 16, Redis 7, Kafka 3.7.x (KRaft)
- Next.js 14.2.x, TypeScript 5.5, TailwindCSS 3.4, Radix UI, TanStack Query, Zustand
- Keycloak 25.x, Envoy 1.31.x, HAPI FHIR 7.4, Orthanc PACS
- Docker/K8s, Helm, GitHub Actions

## Service Port Map (local dev)
- TSHEPO: 8081, VITO: 8082, VARAPI: 8083, TUSO: 8084, ZIBO: 8085
- PCT: 8088, OROS: 8089, HAPI FHIR: 8090
- Envoy: 10000 (public), 9901 (admin)
- Keycloak: 8080, MinIO: 9000/9001, Kafka: 9092
- UI Shell: 3000, Ops Console: 3001, EHR: 3002, Portal: 3003
