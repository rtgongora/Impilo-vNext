# Full Helm Deployability Matrix

Generated: 2026-06-04T16:02:43.667173+00:00

**Required services:** 22

## Summary

| Status | Count |
|--------|-------|
| config_missing | 13 |
| helm_ready | 9 |

## Required services

| Service | Plane | Port | Deploy group | Image | Helm status | Notes |
|---------|-------|------|--------------|-------|-------------|-------|
| butano-service | clinical | 8090 | domain_services | impilo/butano-service | config_missing | not in fullBootServices |
| envoy | trust | 10000 | infrastructure | envoyproxy/envoy:v1.31-latest | helm_ready | envoy.yaml |
| experience-bff | experience | 8160 | experience_layer | impilo/experience-bff | helm_ready | experience-bff.yaml |
| fhir-gateway-service | clinical | 8091 | domain_services | impilo/fhir-gateway-service | config_missing | not in fullBootServices |
| hapi-fhir | clinical | 8090 | infrastructure | hapiproject/hapi:v7.4.0 | helm_ready | hapi-fhir.yaml |
| kafka | integration | 9092 | infrastructure | apache/kafka:3.7.1 | helm_ready | kafka.yaml |
| keycloak | trust | 8080 | infrastructure | quay.io/keycloak/keycloak:25.0 | helm_ready | keycloak.yaml |
| minio | integration | 9000 | infrastructure | minio/minio:latest | helm_ready | minio.yaml |
| one-ui-shell | experience | — | experience_layer | impilo/one-ui-shell | helm_ready | one-ui-shell.yaml |
| pct-service | clinical | 8088 | domain_services | impilo/pct-service | config_missing | not in fullBootServices |
| postgres | integration | 5432 | infrastructure | postgres:16-alpine | helm_ready | postgres.yaml |
| redis | integration | 6379 | infrastructure | redis:7-alpine | helm_ready | redis.yaml |
| tshepo-audit-service | trust | 8183 | identity_trust_policy | impilo/tshepo-audit-service | config_missing | not in fullBootServices |
| tshepo-authz-service | trust | 8081 | identity_trust_policy | impilo/tshepo-authz-service | config_missing | not in fullBootServices |
| tshepo-consent-service | trust | 8182 | identity_trust_policy | impilo/tshepo-consent-service | config_missing | not in fullBootServices |
| tshepo-identity-service | trust | 8181 | identity_trust_policy | impilo/tshepo-identity-service | config_missing | not in fullBootServices |
| tshepo-keys-service | trust | 8184 | identity_trust_policy | impilo/tshepo-keys-service | config_missing | not in fullBootServices |
| tuso-service | registry | 8084 | registries | impilo/tuso-service | config_missing | not in fullBootServices |
| ubomi-service | registry | 8087 | registries | impilo/ubomi-service | config_missing | not in fullBootServices |
| varapi-service | registry | 8083 | registries | impilo/varapi-service | config_missing | not in fullBootServices |
| vito-service | registry | 8082 | registries | impilo/vito-service | config_missing | not in fullBootServices |
| zibo-service | registry | 8085 | registries | impilo/zibo-service | config_missing | not in fullBootServices |
