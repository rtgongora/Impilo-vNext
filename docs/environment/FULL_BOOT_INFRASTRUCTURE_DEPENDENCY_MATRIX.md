# Full Boot Infrastructure Dependency Matrix

Generated: 2026-07-15T23:39:14.831943+00:00

Target namespace: **impilo-full-preview**

| Component | Official image | Chart template | Values key | Port | Health | Order | Status |
|-----------|----------------|----------------|------------|------|--------|-------|--------|
| postgres | postgres:16-alpine | templates/postgres.yaml | values-full-preview.yaml#postgres | 5432 | pg_isready | 1 | helm_ready |
| redis | redis:7-alpine | templates/redis.yaml | values-full-preview.yaml#redis | 6379 | redis ping | 2 | helm_ready |
| kafka | apache/kafka:3.7.1 | templates/kafka.yaml | values-full-preview.yaml#kafka | 9092 | tcp:9092 | 3 | helm_ready |
| keycloak | quay.io/keycloak/keycloak:25.0 | templates/keycloak.yaml | values-full-preview.yaml#keycloak | 8080 | /health/ready | 4 | helm_ready |
| minio | minio/minio:latest | templates/minio.yaml | values-full-preview.yaml#minio | 9000 | /minio/health/ready | 5 | helm_ready |
| hapi-fhir | hapiproject/hapi:v7.4.0 | templates/hapi-fhir.yaml | values-full-preview.yaml#hapiFhir | 8090 | /fhir/metadata | 6 | helm_ready |
| envoy | envoyproxy/envoy:v1.31-latest | templates/envoy.yaml | values-full-preview.yaml#envoy | 10000 | admin /ready | 7 | helm_ready |
