# Impilo vNext — Claude Code Project Rules

## Foundational Doctrine

> Impilo is a **Health Operating System**: a trusted, governed, extensible, interoperable,
> and person-centered national digital environment in which health identities, records,
> workflows, services, transactions, intelligence, assets, devices, communities, wellness
> experiences, dietary and sleep-related participation, marketplace functions, and
> applications operate coherently.

> **Short doctrine line**: One Health Operating System, one experience shell, one person
> anchor, many roles, many IDs, many contexts, many experience modes, many connected
> entities, one governed runtime.

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
- Experience UI (`ui/experience/`) is the primary shell; all 22 sidecars absorbed
- **Intelligent**: searchable, conversational, context-aware, proactive guidance
- **Consumer-grade wellness**: diet, sleep, fitness, clubs, coaching — genuine product pillars
- **Graduated friction**: MINIMAL (wellness/search) → MAXIMUM (prescribing/claims)

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

Authoritative table (defaults, no collisions): [`docs/runbooks/port-allocation.md`](docs/runbooks/port-allocation.md).

Summary:

- TSHEPO Authz: **8081** (gRPC **9090**), TSHEPO legacy monolith: **8079**, VITO: **8082**, VARAPI: **8083**, TUSO: **8084**, ZIBO: **8085**
- MSIKA: **8086**, UBOMI: **8087**, PCT: **8088**, OROS: **8089**, BUTANO (HAPI): **8090**, FHIR Gateway: **8091**, BUTANO FHIR layer: **8289**
- Landela: **8092**, Document Store: **8093**, Inpatient: **8121**, Pharmacy: **8096**, PACS adapter: **8113**
- Msika Flow: **8100**, Costa: **8101**, MUSheX: **8102**
- Share slip: **8104**, Offline sync: **8095**, Jobs: **8109**, Credential verification: **8094**
- Connector FHIR adapter: **8151**, Indawo: **8150**, National data repository: **8152**
- Coverage: **8140**, Data pipeline: **8215**, Workflow: **8250**
- Experience BFF: **8160**, Reporting: **8176**, Search: **8230**, Forms: **8240**, Rules: **8241**, Guidance: **8260**, Clinical Knowledge Platform: **8270**
- NDR: **8232**, Data warehouse: **8233**, Data governance: **8220**, Security hardening: **8221**, Observability: **8211**, Data ingestion: **8210**
- Surveillance: **8180**, Campaigns: **8190**, Notification: **8200**, Identity assurance: **8201**
- Card print agent: **8291**, Product registry: **8097**, Inventory eLMIS adapter: **8108**
- Envoy: 10000 (public), 9901 (admin)
- Keycloak: 8080, MinIO: 9000/9001, Kafka: 9092
- Experience UI: 3020, UI Shell: 3000, Ops Console: 3001, EHR: 3002, Portal: 3003
