# Checkpoint 1 — Documentation Mislabel Corrections

Audit scope: phrases in `docs/` that present `tshepo-service`, Keycloak, or OPA as the whole of Tshepo.
Corrections are proposed for follow-up; only `CLAUDE.md`, `AGENTS.md`, and `docs/doctrine/README.md` were
edited in this checkpoint.

| # | File:line | Proposed correction |
|---|-----------|---------------------|
| 1 | `docs/architecture/SERVICE_ARCHITECTURE_REGISTER.md:220` | Relabel row from single `Tshepo` backend service to **Tshepo trust plane** (decomposed services); point artifact column at `tshepo-authz-service` + siblings, not `tshepo-service` alone. |
| 2 | `docs/production-readiness/wave20/REGISTRY_PARITY_MATRIX.md:155` | Split the single **Tshepo → tshepo-service** row into plane-level entry listing decomposed trust services; mark `tshepo-service` as legacy/deprecated. |
| 3 | `docs/registry/system-of-record-map.md:91` | Change SoR label from **Tshepo canonical records** to **Tshepo legacy monolith (deprecated)**; trust-plane SoR is distributed across decomposed TSHEPO services per trust-plane doctrine. |
| 4 | `docs/registry/service-ownership-matrix.md:83` | Same as #3 — `tshepo-service` owns legacy compatibility records only, not the whole trust plane. |
| 5 | `docs/registry/services-index.md:115` | Clarify **TSHEPO, legacy monolith** is not plane SoR; link to `tshepo-trust-plane-doctrine.md` for canonical trust ownership. |
| 6 | `docs/architecture/planes/01-trust-identity-assurance-governance.md:17` | Remove or downgrade `tshepo-service` from plane inventory as canonical; list decomposed trust services as the plane components. |
| 7 | `docs/waves/STATUS.md:10` | Replace **TSHEPO service** with **Tshepo trust plane** (Keycloak + decomposed TSHEPO services + Envoy ext_authz + OPA parity path). |
| 8 | `docs/waves/STATUS.md:41` | Expand **TSHEPO + Envoy + Keycloak wired** to name trust-plane components, not a single service. |
| 9 | `docs/product/VNEXT_ACTOR_MODEL.md:8` | Replace **Keycloak session + TSHEPO** auth column with **Keycloak authentication within Tshepo trust plane; tshepo-authz authorization**. |
| 10 | `docs/product/VNEXT_ACTOR_MODEL.md:40` | Same as #9 for the actor detail **Authentication / trust** field. |
| 11 | `docs/mobile/app-interoperability-architecture.md:44-45` | Rename diagram box **TSHEPO Service (Policy Engine)** to **tshepo-authz-service (ext_authz decision point)** within the Tshepo trust plane. |
| 12 | `docs/mobile/shared-foundation-scope.md:49` | Replace **Depends on: Keycloak, tshepo-service** with **Depends on: Keycloak (authentication), tshepo-authz-service (authorization), tshepo-offline-service**. |
| 13 | `docs/runtime-validation/steel-thread-results.md:12` | Change **Keycloak → TSHEPO → VARAPI** to **Keycloak → tshepo-authz (Tshepo plane) → VARAPI** to show authn vs authz split. |
| 14 | `docs/doctrine/identity-access-trust-governance.md:10` | Replace **Tshepo/OPA** with **tshepo-authz (PolicyEngine) with OPA parity evaluation** — OPA is not Tshepo, and Tshepo is not OPA alone. |
| 15 | `docs/doctrine/identity-access-trust-governance.md:324` | Same as #14 for the permissions bullet. |
| 16 | `docs/clinical/encounter-structured-forms/audit.md:76-77` | Reframe **Tshepo: PolicyEngine + OPA shadow** as **tshepo-authz-service within the Tshepo trust plane**; OPA is parity/shadow, not co-equal naming for the plane. |
| 17 | `docs/production-readiness/runbooks/dependency-failure.md:109` | Clarify **TSHEPO: Token validation falls back to Keycloak introspection** — token validation is Keycloak's authentication lane; tshepo-authz handles authorization decisions separately. |
| 18 | `docs/production-readiness/production-readiness-report.md:135` | Replace **primary TSHEPO service** with **legacy tshepo-service monolith**; decomposed trust-plane services are the canonical production path. |
| 19 | `docs/architecture/SERVICE_ARCHITECTURE_REGISTER.md:757` | Reframe **TSHEPO Service remains a legacy monolith** as explicit retirement status under plane doctrine, not as the plane's primary identity. |
| 20 | `docs/acceptance/data-centre-enforcement-gates.md:13` | Replace **Envoy -> TSHEPO/OPA -> service** with **Envoy -> tshepo-authz (Tshepo plane) -> service; OPA parity path separate** to avoid equating Tshepo with OPA. |
