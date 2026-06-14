# Full Helm Deployability Matrix

Generated: 2026-06-14T00:41:10.548770+00:00

**Required services:** 22

## Summary

| Status | Count |
|--------|-------|
| helm_ready | 22 |

## Required services

| Service | Plane | Port | Deploy group | Image | Helm status | Notes |
|---------|-------|------|--------------|-------|-------------|-------|
| butano-service | clinical | 8090 | domain_services | impilo/butano-service | helm_ready | microservice.yaml |
| envoy | trust | 10000 | infrastructure | envoyproxy/envoy:v1.31-latest | helm_ready | envoy.yaml |
| experience-bff | experience | 8160 | experience_layer | impilo/experience-bff | helm_ready | experience-bff.yaml |
| fhir-gateway-service | clinical | 8091 | domain_services | impilo/fhir-gateway-service | helm_ready | microservice.yaml |
| hapi-fhir | clinical | 8090 | infrastructure | hapiproject/hapi:v7.4.0 | helm_ready | hapi-fhir.yaml |
| kafka | integration | 9092 | infrastructure | apache/kafka:3.7.1 | helm_ready | kafka.yaml |
| keycloak | trust | 8080 | infrastructure | quay.io/keycloak/keycloak:25.0 | helm_ready | keycloak.yaml |
| minio | integration | 9000 | infrastructure | minio/minio:latest | helm_ready | minio.yaml |
| one-ui-shell | experience | — | experience_layer | impilo/one-ui-shell | helm_ready | one-ui-shell.yaml |
| pct-service | clinical | 8088 | domain_services | impilo/pct-service | helm_ready | microservice.yaml |
| postgres | integration | 5432 | infrastructure | postgres:16-alpine | helm_ready | postgres.yaml |
| redis | integration | 6379 | infrastructure | redis:7-alpine | helm_ready | redis.yaml |
| tshepo-audit-service | trust | 8183 | identity_trust_policy | impilo/tshepo-audit-service | helm_ready | microservice.yaml |
| tshepo-authz-service | trust | 8081 | identity_trust_policy | impilo/tshepo-authz-service | helm_ready | microservice.yaml |
| tshepo-consent-service | trust | 8182 | identity_trust_policy | impilo/tshepo-consent-service | helm_ready | microservice.yaml |
| tshepo-identity-service | trust | 8181 | identity_trust_policy | impilo/tshepo-identity-service | helm_ready | microservice.yaml |
| tshepo-keys-service | trust | 8184 | identity_trust_policy | impilo/tshepo-keys-service | helm_ready | microservice.yaml |
| tuso-service | registry | 8084 | registries | impilo/tuso-service | helm_ready | microservice.yaml |
| ubomi-service | registry | 8087 | registries | impilo/ubomi-service | helm_ready | microservice.yaml |
| varapi-service | registry | 8083 | registries | impilo/varapi-service | helm_ready | microservice.yaml |
| vito-service | registry | 8082 | registries | impilo/vito-service | helm_ready | microservice.yaml |
| zibo-service | registry | 8085 | registries | impilo/zibo-service | helm_ready | microservice.yaml |
