# Impilo vNext — Platform Orchestration Spec Conflicts

## Port Conflicts

The following port conflicts exist in service `application.yml` defaults. These are resolved in Docker Compose via container networking (each service gets its own network namespace), but are relevant for bare-metal development.

| Port | Services | Resolution |
|------|----------|------------|
| 8081 | tshepo-service, tshepo-authz-service | Use monolith tshepo OR decomposed authz, not both simultaneously |
| 8181 | tshepo-identity-service, OPA | Override `SERVER_PORT` for tshepo-identity when running with OPA |
| 8087 | ubomi-service, mushex-service | Override `SERVER_PORT` for mushex → 8102 in compose |
| 8093 | document-service, inpatient-service | Override `SERVER_PORT` for one |
| 8094 | jobs-service, credential-verification-service | Override `SERVER_PORT` for one |
| 8095 | share-slip-service, offline-sync-service | Override `SERVER_PORT` for one |
| 8098 | inventory-service, inventory-elmis-adapter | Override `SERVER_PORT` for one |
| 8140 | coverage-service, data-pipeline-service, workflow-service | Override `SERVER_PORT` for two of three |
| 8090 | butano-service, HAPI FHIR (host mapping) | Container networking resolves; for bare-metal override butano |
| 8150 | indawo-service, national-data-repository-service, connector-fhir-adapter | Override `SERVER_PORT` for two of three |

## TSHEPO Monolith vs Decomposition

The repository contains both:
- `services/tshepo-service` — monolith TSHEPO (used in current compose)
- `services/tshepo-authz-service`, `tshepo-identity-service`, `tshepo-consent-service`, `tshepo-audit-service`, `tshepo-keys-service`, `tshepo-offline-service` — decomposed microservices

**Current state**: The orchestration uses monolith `tshepo-service` for dev-lite profile. The decomposed services are available in dev-full profile but should not run simultaneously with the monolith on the same port.

**Recommendation**: Use monolith for development. Switch to decomposed services when migration is complete.

## Services Without Dockerfiles

The following services exist in the repo but do not have Dockerfiles. They are included in the manifest but cannot be started as containers without additional work:

| Service | Path | Notes |
|---------|------|-------|
| integration-hub | services/integration-hub | No Dockerfile |
| notification-service | services/notification-service | No Dockerfile — has helm chart |
| workflow-service | services/workflow-service | No Dockerfile |
| channels-service | services/channels-service | No Dockerfile |
| forms-service | services/forms-service | No Dockerfile |
| search-service | services/search-service | No Dockerfile |
| rules-service | services/rules-service | No Dockerfile |
| dispatch-service | services/dispatch-service | No Dockerfile |
| support-service | services/support-service | No Dockerfile |
| developer-portal-service | services/developer-portal-service | No Dockerfile |
| coverage-service | services/coverage-service | No Dockerfile |
| indawo-service | services/indawo-service | No Dockerfile |
| data-warehouse-service | services/data-warehouse-service | No Dockerfile |
| data-governance-service | services/data-governance-service | No Dockerfile |
| surveillance-service | services/surveillance-service | No Dockerfile |
| campaigns-service | services/campaigns-service | No Dockerfile |
| audit-ledger-service | services/audit-ledger-service | No Dockerfile |

**Resolution**: These services need Dockerfiles to be added. The compose operations file references them by build context; Docker will fail gracefully if Dockerfile is missing.

## UI Apps Without Dockerfiles

Most UI apps under `ui/` (except `ui/experience`) do not have Dockerfiles or build configurations for containerized deployment. They are listed in the manifest for completeness.

## Kafka Topic Naming Ambiguity

Some services use explicit topic names while others use prefix-based patterns:
- `tuso-service`: `kafka-topic-prefix: tuso` (generates: `tuso.facility.events`, etc.)
- `varapi-service`: `kafka-topic-prefix: varapi`
- Others: explicit topic names

The bootstrap creates topics with explicit names. Services using prefix patterns may create additional topics at runtime.
