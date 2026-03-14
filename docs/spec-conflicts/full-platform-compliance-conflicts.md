# Spec Conflict Log — Full-Platform Compliance Closure

> Generated: 2026-03-14 | Branch: claude/review-project-manifest-jb5O0

## 1. ConsistencyClass / ActionRegistry Adoption

| Field | Value |
|---|---|
| **Service / Area** | All services |
| **What the standards say** | Tech Companion Spec 2.0 defines ConsistencyClass (STRONG, BOUNDED, EVENTUAL) and ActionRegistry for per-endpoint consistency guarantees. The `ConsistencyClassFilter` exists in `libs/tech-companion`. |
| **What the repo has** | No service has declared an `ActionRegistry` bean or annotated endpoints with `@ConsistencyClass`. The filter is registered conditionally (`@ConditionalOnBean(ActionRegistry.class)`) so it never activates. |
| **Why ambiguous** | The spec describes consistency classes as a SHOULD requirement, not a MUST. No production service has needed PDP-backed consistency enforcement yet. Implementing without PDP infrastructure would be a placeholder. |
| **Minimal question** | Should consistency classes be declared for all endpoints now (static annotation only) or deferred until PDP infrastructure is deployed? |

## 2. Federation Authority — Scope of National-Only Operations

| Field | Value |
|---|---|
| **Service / Area** | All registry services (VITO, TUSO, VARAPI, ZIBO, MSIKA, INDAWO) |
| **What the standards say** | vNext V3 states that "national authoritative only" operations (e.g., patient merge, master data mutations) MUST reject non-national pods with 403 FEDERATION_AUTHORITY_VIOLATION. |
| **What the repo has** | Only `vito-service` implements `FederationAuthorityGuard` on merge operations. Other registries (TUSO, VARAPI, ZIBO, MSIKA, INDAWO) do not have federation guards on any write endpoints. |
| **Why ambiguous** | The spec does not enumerate which specific operations per service are "national authoritative only." For TUSO (service types), VARAPI (lab test definitions), ZIBO (billing codes), MSIKA (marketplace), INDAWO (sites) — it is unclear whether writes to these registries should be restricted to the national pod or allowed at facility level. |
| **Minimal question** | Which specific write operations in TUSO, VARAPI, ZIBO, MSIKA, and INDAWO should be classified as "national authoritative only"? |

## 3. Snapshot Endpoint Coverage for Non-Registry Services

| Field | Value |
|---|---|
| **Service / Area** | Non-registry services with event emission |
| **What the standards say** | vNext V3 recommends snapshot endpoints for "registry-like" services to support bootstrapping and catch-up scenarios. |
| **What the repo has** | Snapshot endpoints exist for: VITO, TUSO, VARAPI, ZIBO, MSIKA, INDAWO, channels, data-governance, dispatch, support, workflow. They do NOT exist for: PCT, OROS, pharmacy, mushex, butano, ubomi, and many platform services. |
| **Why ambiguous** | The spec says "registry-like" but does not define whether clinical execution services (PCT, OROS, pharmacy) need snapshot endpoints. Their data is transactional/encounter-based, not reference data. |
| **Minimal question** | Are snapshot endpoints required for transactional/clinical services (PCT, OROS, pharmacy, etc.) or only for reference-data registries? |

## 4. Adapter Services — Compliance Scope

| Field | Value |
|---|---|
| **Service / Area** | inventory-elmis-adapter, pharmacy-elmis-adapter |
| **What the standards say** | "Every service that exposes business APIs" must enforce request-path compliance. |
| **What the repo has** | ELMIS adapters are headless Kafka/scheduled-job integrations with no HTTP API surface. They have no controllers, no REST endpoints, and no tech-companion dependency. |
| **Why ambiguous** | The spec requirement is conditional on "exposes business APIs." These adapters don't, but they are deployable services. |
| **Minimal question** | Are headless adapter services explicitly exempt from request-path compliance? (Recommendation: YES — they have no request boundary.) |

## 5. card-print-agent — HTTP vs Job Agent

| Field | Value |
|---|---|
| **Service / Area** | card-print-agent |
| **What the standards say** | Request-path compliance for all services with business APIs. |
| **What the repo has** | card-print-agent has minimal HTTP endpoints (`/v1/internal/print-jobs`) alongside its primary Kafka-consumer job processing. We added a v1.1 probe controller and tech-companion dependency for compliance. |
| **Why ambiguous** | The agent's primary operation is Kafka-consumer-driven, not HTTP-request-driven. Whether the `/v1/internal/print-jobs` endpoint is the primary API or a monitoring endpoint affects compliance scope. |
| **Minimal question** | Should card-print-agent's HTTP endpoints be considered the primary API (full compliance) or operational/monitoring endpoints (reduced compliance)? |

## 6. Event Naming Convention — Canonical vs Legacy

| Field | Value |
|---|---|
| **Service / Area** | All services with legacy event emission |
| **What the standards say** | Event types must follow: `impilo.<service>.<domain>.<entity>.<action>.v1` |
| **What the repo has** | Ring 0/Ring 1 services (VITO, TUSO, VARAPI, ZIBO, MSIKA) use the canonical naming. Older services (TSHEPO sub-services, pharmacy, oros, pct) may emit events with legacy naming patterns that predate the convention. |
| **Why ambiguous** | Changing legacy event names would break existing Kafka consumers. The spec doesn't address backwards-compatible migration of event type names. |
| **Minimal question** | Should legacy event type names be aliased (dual-emit) or renamed (breaking change) to match the canonical pattern? |
