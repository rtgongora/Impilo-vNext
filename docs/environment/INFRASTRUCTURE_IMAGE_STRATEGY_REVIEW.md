# Infrastructure Image Strategy Review

> Identity, policy, database, cache, broker, object storage, ingress — **official images/charts** unless custom implementation required.

| Component | Official image | Helm chart | Version strategy | Helm release | Local build | Reason | Notes |
|---|---|---|---|---|---|---|---|
| envoy | envoyproxy/envoy:v1.31-latest | infra/envoy | pin in values/compose | Helm subchart or impilo-vnext values | no | Infrastructure uses upstream image/chart | Use pinned versions in deploy; no custom Dockerfile unless fork required |
| hapi-fhir | hapiproject/hapi:v7.4.0 | helm/butano | pin in values/compose | Helm subchart or impilo-vnext values | no | Infrastructure uses upstream image/chart | Use pinned versions in deploy; no custom Dockerfile unless fork required |
| kafka | apache/kafka:3.7.1 | strimzi or compose | pin in values/compose | Helm subchart or impilo-vnext values | no | Infrastructure uses upstream image/chart | Use pinned versions in deploy; no custom Dockerfile unless fork required |
| keycloak | quay.io/keycloak/keycloak:25.0 | codecentric/keycloak or values.keycloak | pin in values/compose | Helm subchart or impilo-vnext values | no | Infrastructure uses upstream image/chart | Use pinned versions in deploy; no custom Dockerfile unless fork required |
| minio | minio/minio:latest | minio/minio | pin in values/compose | Helm subchart or impilo-vnext values | no | Infrastructure uses upstream image/chart | Use pinned versions in deploy; no custom Dockerfile unless fork required |
| opa | openpolicyagent/opa:0.68.0 | none — sidecar in compose | pin in values/compose | Helm subchart or impilo-vnext values | no | Infrastructure uses upstream image/chart | Use pinned versions in deploy; no custom Dockerfile unless fork required |
| postgres | postgres:16-alpine | bitnami/postgresql or deploy/helm/impilo-vnext | pin in values/compose | Helm subchart or impilo-vnext values | no | Infrastructure uses upstream image/chart | Use pinned versions in deploy; no custom Dockerfile unless fork required |
| redis | redis:7-alpine | bitnami/redis or subchart | pin in values/compose | Helm subchart or impilo-vnext values | no | Infrastructure uses upstream image/chart | Use pinned versions in deploy; no custom Dockerfile unless fork required |

See also: `compose/`, `deploy/helm/impilo-vnext/values.yaml`, `docs/environment/FULL_BOOT_INFRASTRUCTURE_PLAN.md`.
