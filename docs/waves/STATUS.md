# Impilo vNext — Wave Tracking Status

> Last updated: 2026-03-11

## Wave Definitions

| Wave | Name | Scope |
|------|------|-------|
| 0 | Foundation | Parent POM, shared-core, infra docker-compose, CI skeleton |
| 1 | Trust Plane | TSHEPO service, Keycloak integration, Envoy ext_authz, policy engine |
| 2 | Registry Spine | VITO (MPI), VARAPI (provider/facility), TUSO (terminology), ZIBO (billing) |
| 3 | Clinical Core | MSIKA (clinical engine), UBOMI (care plans), FHIR Gateway, BUTANO (SHR) |
| 4 | Finance & Ops | PCT (pricing), OROS (claims), Pharmacy, Inpatient, Inventory, MuSHeX |
| 5 | Platform Services | Notification, Jobs, Integration Hub, Rules, Offline Sync, Document, PACS, Search, Forms |
| 6 | Tech Companion & v1.1 Enforcement | Tech Companion library, golden harness, federation connector, TSHEPO decomposition, Data Platform (Ring-2), Supply & IoT, Security & Observability stubs, Channels, Coverage, INDAWO |
| 7 | Eventing Standardization | EventEnvelope schema_version + partition_key normalization, outbox relay standardization, Kafka topic governance, dead-letter queue patterns |
| 8 | Experience Platform | Experience UI (Next.js), Experience BFF (Spring Boot), prototype-to-platform migration |

## Wave Status

| Wave | Code Complete | Build Verified | Runtime Verified | Notes |
|------|:---:|:---:|:---:|-------|
| 0 | ✅ | ✅ | ✅ | Parent POM, shared-core, docker-compose |
| 1 | ✅ | ✅ | ✅ | TSHEPO + Envoy + Keycloak wired |
| 2 | ✅ | ✅ | ✅ | VITO, VARAPI, TUSO, ZIBO all operational |
| 3 | ✅ | ✅ | ✅ | Clinical core services running |
| 4 | ✅ | ✅ | ✅ | Finance, pharmacy, inventory, inpatient |
| 5 | ✅ | ✅ | ✅ | All platform services operational |
| 6 | ✅ | ⚠️ | ⚠️ | Code complete; build/runtime requires online env (Docker + Maven). Verification pack committed. |
| 7 | ❌ | ❌ | ❌ | Not started. Next objective. |
| 8 | ✅ (stage 1) | ⚠️ | ⚠️ | Experience BFF + UI skeleton committed. Reactor wiring fixed. Online verification pack ready. |

### Legend
- ✅ = Confirmed
- ⚠️ = Partial — code exists but verification requires online environment
- ❌ = Not started

## Current Stage Detail: Wave 8 — Experience Platform (Middle Ground)

### What is committed:
- **Experience BFF** (`services/experience-bff/`): Spring Boot service with v1.1 header enforcement, idempotency, outbox pattern, 3 real endpoints (Workspace, Facility, ReportJob)
- **Experience UI** (`ui/experience/`): Next.js 14 app with 96 page.tsx files covering 98 registry routes, 3-zone navigation, route guards, API client with trust header injection
- **Tech Companion** (`libs/tech-companion/`): v1.1 filters including TimeoutEnforcementFilter with pre-expired deadline check
- **Golden Harness** (`libs/tech-companion-harness/`): Contract tests for header enforcement, error envelope, idempotency, timeout enforcement, and federation authority
- **Reactor wiring**: `shared-kernel-java` and `experience-bff` both registered in `services/pom.xml`
- **Docker Compose**: Dev runtime definition for BFF + PostgreSQL
- **Verification pack**: `scripts/experience/verify-online.sh` + smoke tests + docs

### What needs online verification:
- Maven `clean test` for experience-bff (requires dependency resolution)
- Docker Compose up + healthcheck
- BFF smoke tests via curl
- Outbox proof query
- UI route parity check (already passes locally as file-based check)

## Next Wave Objective

**Wave 7: Eventing Standardization**

Scope:
1. Normalize `EventEnvelope` across all services to include `schema_version` and `partition_key`
2. Standardize outbox relay mechanism (currently per-service; move to shared library or sidecar)
3. Define Kafka topic naming convention and governance (e.g., `impilo.<domain>.<event>.v1`)
4. Implement dead-letter queue pattern for failed event processing
5. Add event schema registry integration (optional, if infrastructure permits)

**Do NOT start Wave 7 in this session.** It will be the next engineering objective.
