# Impilo vNext — Claude Code Project Rules

## Foundational Doctrine

> Impilo is a **Health Operating System**: a trusted, governed, extensible, and
> interoperable national digital environment in which health identities, records, workflows,
> services, transactions, intelligence, assets, and applications operate coherently.

> **Short doctrine line**: One Health Operating System, one experience shell, one person
> anchor, many roles, many IDs, many contexts, one governed runtime.

Full doctrine: [`docs/doctrine/health-os-doctrine.md`](docs/doctrine/health-os-doctrine.md)

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

### Health OS Model
Impilo is not a single application — it is a governed execution environment providing:
1. Common identity and trust services
2. Policy and consent enforcement
3. Shared registries and canonical references
4. Longitudinal record and data services
5. Workflow, messaging, and event orchestration
6. Transaction and fulfilment rails
7. Audit and traceability
8. Standards-based interoperability
9. Extension points for modules, apps, and integrations
10. A coherent unified experience shell

### 6 Planes
Trust & Governance, Registry Spine, Clinical Execution, Finance, Integration/Ops, Experience

### Core Principles
- **Trust-first**: Every request flows through Envoy ext_authz → TSHEPO before reaching any service
- **No PII in SHR**: BUTANO (HAPI FHIR) uses CPID only; PII stays in VITO
- **Outbox pattern**: Every service has an `event_outbox` table for reliable Kafka publishing
- **Person-centered identity**: One Health ID per person, many attached role/context/object identifiers

### Multi-Class Identifier Model
| Class | Purpose | Examples |
|-------|---------|----------|
| **Actor IDs** | Who is involved | Health ID, Provider ID, Staff ID |
| **Context IDs** | Where / under what setting | Facility ID, Department ID, Ward ID, Workspace ID, Programme ID |
| **Object IDs** | What thing / resource | Product ID, Medication ID, Asset ID, Equipment ID, Device ID |
| **Transaction IDs** | Specific action instances | Prescription ID, Order ID, Claim ID, Appointment ID, Payment ID |
| **Record IDs** | Persistent information objects | Observation ID, Consent ID, Document ID, Care Plan ID |
| **Event IDs** | Discrete runtime events | Alert ID, Audit Event ID, Notification ID, Workflow Event ID |

### Identity & Role Activation
- **Sign in as a person** → Health ID establishes who
- **Practice as a provider** → Provider ID activates regulated professional capacity
- Professional execution requires: valid Provider ID + licensure + org affiliation + facility context + purpose of use

### Access Control (10 Dimensions)
Every access decision must evaluate:
1. Person identity (Health ID)
2. Active role
3. Attached role identifier (Provider ID, Staff ID)
4. Organizational affiliation
5. Facility or workspace context
6. Subject relationship
7. Purpose of use
8. Consent or legal basis
9. Assurance level
10. Workflow state

### Header Contract
Trust headers defined in `CompanionHeaders.java` ↔ `api-client.ts` ↔ `envoy.yaml`:
- **Mandatory**: X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID
- **Actor context**: X-Actor-ID, X-Actor-Type, X-Purpose-Of-Use
- **Operational context**: X-Facility-ID, X-Workspace-ID, X-Shift-ID
- **Pending doctrine alignment**: X-Provider-ID, X-Department-ID, X-Ward-ID, X-Programme-ID, X-Subject-ID, X-Assurance-Level

### Unified Experience Shell
- One coherent experience shell, not fragmented portals
- Role-based: adapts visibility/enablement by active role and context
- "Unified" ≠ identical — workspaces may differ but share governed trust model
- Experience UI (`ui/experience/`) is the primary shell; other UIs are being consolidated

## Golden Thread (UI → DB proof path)
1. `ui/experience/src/lib/api-client.ts` — injects trust headers
2. `services/tshepo-service/.../api/AuthorizeController.java` — ext_authz endpoint
3. `services/tshepo-service/.../core/PolicyEngine.java` — RBAC/ABAC + serialized audit chain

## Tech Stack
- Java 21, Spring Boot 3.3.6, PostgreSQL 16, Redis 7, Kafka 3.7.x (KRaft)
- Next.js 14.2.x, TypeScript 5.5, TailwindCSS 3.4, Radix UI, TanStack Query, Zustand
- Keycloak 25.x, Envoy 1.31.x, HAPI FHIR 7.4, Orthanc PACS
- Docker/K8s, Helm, GitHub Actions

## Service Port Map (local dev)
- TSHEPO: 8081, VITO: 8082, VARAPI: 8083, TUSO: 8084, ZIBO: 8085
- Msika: 8086, PCT: 8088, OROS: 8089, HAPI FHIR: 8090
- Landela: 8092, Document Store: 8093, Pharmacy: 8096
- Msika Flow: 8100, Costa: 8101, Mushex: 8102
- Coverage: 8140, Indawo: 8150, Experience BFF: 8160
- Surveillance: 8180, Campaigns: 8190, Notification: 8200
- Data Governance: 8220
- Envoy: 10000 (public), 9901 (admin)
- Keycloak: 8080, MinIO: 9000/9001, Kafka: 9092
- Experience UI: 3020, UI Shell: 3000, Ops Console: 3001, EHR: 3002, Portal: 3003
