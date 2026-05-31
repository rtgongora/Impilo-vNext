# Full Boot Infrastructure Plan

> Namespace for full boot: **`impilo-full-preview`** (never replace `impilo-preview` slice).
> No production secrets in this document.

## Infrastructure components

| Component | Plane / group | Deploy method | Namespace | Image/chart | Persistence | Health | Dependents |
|-----------|---------------|---------------|-----------|-------------|-------------|--------|------------|
| PostgreSQL | infrastructure | Helm subchart / impilo-vnext | impilo-full-preview | postgres:16-alpine | PVC optional | `pg_isready` | All JDBC services |
| Redis | infrastructure | Helm subchart | impilo-full-preview | redis:7-alpine | none | `redis-cli ping` | BFF, sessions, cache |
| Kafka (KRaft) | event_backbone | docker-compose ref / future chart | impilo-full-preview | apache/kafka:3.7.1 | PVC | broker API | Outbox publishers |
| Keycloak | identity_trust_policy | values keycloak.enabled | impilo-full-preview | keycloak:25.0 | JDBC to postgres | `/health` | TSHEPO, BFF OIDC |
| Envoy | identity_trust_policy | infra/envoy | impilo-full-preview | envoyproxy/envoy | none | admin :9901 | ext_authz → TSHEPO |
| MinIO | infrastructure | docker-compose | impilo-full-preview | minio/minio | PVC | `/minio/health/live` | Document store |
| OPA (optional) | identity_trust_policy | infra policy | impilo-full-preview | openpolicyagent/opa | none | `/health` | Policy sidecar |
| HAPI FHIR | clinical / data | butano chart | impilo-full-preview | hapiproject/hapi | PVC | FHIR metadata | BUTANO services |
| Ingress (Traefik) | infrastructure | k3s default | impilo-full-preview | traefik | n/a | HTTP routes | Shell, BFF |

## Current slice readiness

| Component | impilo-preview | Full boot target |
|-----------|----------------|------------------|
| postgres | deployed | required |
| redis | deployed | required |
| kafka | missing | required |
| keycloak | disabled in values | required |
| envoy | missing | required |
| domain services | missing | required per classification |

## Secrets (preview only)

Use `.env.preview.example` and Kubernetes secrets created at deploy time — never commit credentials.

## Next steps

1. Extend `deploy/helm/impilo-vnext` with optional infrastructure toggles (Kafka, Keycloak, Envoy).
2. Add subcharts or `helm/` charts per `FULL_HELM_DEPLOYABILITY_MATRIX.md`.
3. Deploy only after `AUTHORIZE FULL BOOT PREVIEW DEPLOY`.
