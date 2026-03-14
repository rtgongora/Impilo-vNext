# Impilo vNext — Wave Tracking Status

> Last updated: 2026-03-14

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
| 9 | Contract Testing & CI Gates | Contract testing gate, schema validation in CI, GoldenContractSuite enforcement across all services |
| 10 | Data Platform & Analytics | Analytics pipeline, surveillance service, data governance, delta-snapshot framework |
| 11 | Supply & Scheduling | Scheduling service, inpatient enhancements, supply planning module |
| 12 | Developer Experience | Developer portal, API documentation, sandbox environments |
| 13 | Production Readiness Tooling | ops-instrumentation library, structured logging, golden signal metrics, health indicators, OTel propagation |
| 14 | Security Hardening | security-baseline library, input sanitization, rate limiting, admin audit, secrets management |
| 15 | Offline & Edge MVP | offline-sdk, offline-edge-service, JWT entitlements, capture/sync/replay, conflict resolution |
| 16 | Consistency & Decision Evidence | Consistency class enforcement, decision evidence pipeline, staleness headers |
| 17 | Federation Control | Federation control module in TSHEPO, pod authority enforcement, mTLS pod handshake |
| 18 | Chaos & Resilience | Chaos resilience framework, fault injection, circuit breaker validation |
| 19 | Production Readiness Gate | SLOs/SLIs per Ring 0 service, error budgets, alerting rules, load/perf baselines, security posture checks |
| 20 | Disaster Recovery & Continuity | Backup/restore automation, restore drills, RPO/RTO measurement, failover playbooks |
| 21 | Federation Pilot & Pod Readiness | Pod registration handshake, mTLS + aud=federation JWT, authority violations, revocation channel |
| 22 | Offline Pilot at the Edge | Entitlement issuance + device binding, offline capture + audit, post-sync reconciliation, break-glass |
| 23 | Dual-Mode Ecosystem Enablement | Developer portal live, partner onboarding contract tests, versioning/deprecation, sandbox + certification |
| 24 | National Rollout Program | Site readiness, training & support ops, release trains, change control/CAB processes |
| 25 | Continuous Improvement Loop | Schema governance cycles, observability-driven backlog, security patch pipeline, cost/capacity planning |

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
| 9 | ❌ | ❌ | ❌ | Not started. Depends on Wave 7. |
| 10 | ❌ | ❌ | ❌ | Not started. Depends on Wave 7. |
| 11 | ❌ | ❌ | ❌ | Not started. Depends on Wave 9. |
| 12 | ❌ | ❌ | ❌ | Not started. Depends on Wave 9. |
| 13 | ✅ | ⚠️ | ⚠️ | ops-instrumentation library code complete. Online verification pending. |
| 14 | ✅ | ⚠️ | ⚠️ | security-baseline library code complete. Online verification pending. |
| 15 | ✅ | ⚠️ | ⚠️ | offline-edge-service code complete. Online verification pending. |
| 16 | ❌ | ❌ | ❌ | Not started. Depends on Wave 7. |
| 17 | ❌ | ❌ | ❌ | Not started. Depends on Wave 16. |
| 18 | ❌ | ❌ | ❌ | Not started. Depends on Wave 13. |
| 19 | ❌ | ❌ | ❌ | Not started. Depends on Waves 13, 14, 18. |
| 20 | ❌ | ❌ | ❌ | Not started. Depends on Wave 19. |
| 21 | ❌ | ❌ | ❌ | Not started. Depends on Wave 17. |
| 22 | ❌ | ❌ | ❌ | Not started. Depends on Waves 15, 21. |
| 23 | ❌ | ❌ | ❌ | Not started. Depends on Waves 9, 12, 21. |
| 24 | ❌ | ❌ | ❌ | Not started. Depends on Waves 19, 20, 21, 22, 23. Non-code heavy. |
| 25 | ❌ | ❌ | ❌ | Ongoing. Begins after Wave 24. |

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

## Wave Detail Documents

| Wave | Document |
|------|----------|
| 7 | [`docs/eventing/wave7-standard.md`](../eventing/wave7-standard.md) |
| 8 | [`docs/consistency/wave8-consistency.md`](../consistency/wave8-consistency.md), [`docs/federation/wave8-federation.md`](../federation/wave8-federation.md), [`docs/offline/wave8-offline-vitals.md`](../offline/wave8-offline-vitals.md) |
| 13 | [`docs/ops/wave13-production-readiness.md`](../ops/wave13-production-readiness.md) |
| 14 | [`docs/security/wave14-security-hardening.md`](../security/wave14-security-hardening.md) |
| 15 | [`docs/offline/wave15-offline-edge.md`](../offline/wave15-offline-edge.md) |
| 19 | [`docs/ops/wave19-production-readiness-gate.md`](../ops/wave19-production-readiness-gate.md) |
| 20 | [`docs/ops/wave20-disaster-recovery.md`](../ops/wave20-disaster-recovery.md) |
| 21 | [`docs/federation/wave21-federation-pilot.md`](../federation/wave21-federation-pilot.md) |
| 22 | [`docs/offline/wave22-offline-pilot.md`](../offline/wave22-offline-pilot.md) |
| 23 | [`docs/integration/wave23-dual-mode-ecosystem.md`](../integration/wave23-dual-mode-ecosystem.md) |
| 24 | [`docs/ops/wave24-national-rollout.md`](../ops/wave24-national-rollout.md) |
| 25 | [`docs/ops/wave25-continuous-improvement.md`](../ops/wave25-continuous-improvement.md) |
