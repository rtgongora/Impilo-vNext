# Ring 0 Wave 1 — Cursor Agent Briefs

## Overview

4 agents running in parallel to close critical Ring 0 gaps identified in the full-stack audit.
No feature work — only wiring up what already exists but isn't connected.

---

## Agent 1: BFF Clients for 6 Disconnected Kernel Services

### Problem
6 Ring 0 services have backend implementations but NO BFF client — their APIs are invisible to the UI.

### Services & Ports
- tshepo-identity-service (8181) — CPID resolution, MOSIP linking, tokens
- tshepo-keys-service (8184) — JWKS, signing, key management, certificates
- tshepo-offline-service (8185) — capability tokens, offline packs, reconciliation
- indawo-service (8150) — site/premises registry, addresses
- zibo-service (8085) — terminology artifacts, mappings, governance, validation
- ubomi-service (8087) — birth/death notifications, verification

### Pattern to Follow
Reference: `services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/client/VarapiServiceClient.java`

Each client: @Component class, constructor takes RestTemplate serviceRestTemplate + ServiceEndpoints, extractData() helper, methods matching the service's controllers.

### Files to Create
1. `services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/client/TshepoIdentityServiceClient.java`
   - Methods: resolveCpid(healthId), generateCpid(request), issueScopedToken(request), linkMosip(request), listReconciliationQueue()
   - Base URL: endpoints.tshepoIdentityBaseUrl()

2. `services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/client/TshepoKeysServiceClient.java`
   - Methods: getJwks(), listKeys(), rotateKey(keyId), signPayload(request), listCertificates()
   - Base URL: endpoints.tshepoKeysBaseUrl()

3. `services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/client/TshepoOfflineServiceClient.java`
   - Methods: issueCapabilityToken(request), getCapabilityToken(id), revokeCapabilityToken(id), generateOfflinePack(request), getOfflinePack(id), submitReconciliation(request)
   - Base URL: endpoints.tshepoOfflineBaseUrl()

4. `services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/client/IndawoServiceClient.java`
   - Methods: createSite(request), getSite(id), searchSites(query), updateSite(id, request)
   - Base URL: endpoints.indawoBaseUrl() (ALREADY EXISTS in config)

5. `services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/client/ZiboServiceClient.java`
   - Methods: listArtifacts(params), getArtifact(id), validateCode(request), getMappings(sourceSystem, code), importArtifact(request)
   - Base URL: endpoints.ziboBaseUrl()

6. `services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/client/UbomiServiceClient.java`
   - Methods: submitBirthNotification(request), submitDeathNotification(request), verifyEvent(id)
   - Base URL: endpoints.ubomiBaseUrl()

### Files to Modify
7. `services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/config/ServiceClientConfig.java`
   - Add to ServiceEndpoints record: tshepoIdentityBaseUrl, tshepoKeysBaseUrl, tshepoOfflineBaseUrl, ziboBaseUrl, ubomiBaseUrl
   - Note: indawoBaseUrl already exists
   - Add defaults in compact constructor: 8181, 8184, 8185, 8085, 8087

8. `services/experience-bff/src/main/resources/application.yml`
   - Add under impilo.services:
     ```
     tshepo-identity-base-url: ${TSHEPO_IDENTITY_BASE_URL:http://localhost:8181}
     tshepo-keys-base-url: ${TSHEPO_KEYS_BASE_URL:http://localhost:8184}
     tshepo-offline-base-url: ${TSHEPO_OFFLINE_BASE_URL:http://localhost:8185}
     zibo-base-url: ${ZIBO_BASE_URL:http://localhost:8085}
     ubomi-base-url: ${UBOMI_BASE_URL:http://localhost:8087}
     ```

### Read First
- Read each service's controllers to discover exact endpoint paths before writing client methods
- Read VarapiServiceClient.java for the exact pattern
- Read ServiceClientConfig.java carefully — it has a complex record with compact constructor

---

## Agent 2: OAuth2 Resource Server for 6 Unprotected Services

### Problem
6 Ring 0 services have SecurityConfig.java but NO OAuth2 resource server configuration — authentication is not enforced on their APIs.

### Services
- butano-fhir (8289)
- fhir-gateway-service (8091)
- schema-registry-service (8371)
- audit-ledger-service (8350)
- developer-portal-service (8370)
- channels-service (8130)

### Pattern to Follow
Reference: any Ring 0 service that already has OAuth2 configured, e.g. `services/vito-service/src/main/resources/application.yml`

The standard block is:
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${KEYCLOAK_URL:http://localhost:8080}/realms/${KEYCLOAK_REALM:impilo}/protocol/openid-connect/certs
          issuer-uri: ${KEYCLOAK_URL:http://localhost:8080}/realms/${KEYCLOAK_REALM:impilo}
```

### Files to Modify (application.yml for each)
1. `services/butano-fhir/src/main/resources/application.yml`
2. `services/fhir-gateway-service/src/main/resources/application.yml`
3. `services/schema-registry-service/src/main/resources/application.yml`
4. `services/audit-ledger-service/src/main/resources/application.yml`
5. `services/developer-portal-service/src/main/resources/application.yml`
6. `services/channels-service/src/main/resources/application.yml`

### Also Check
- Read each service's SecurityConfig.java to verify it's configured to use OAuth2 resource server
- If SecurityConfig permits all requests (.permitAll()), update it to require authentication on business endpoints while permitting health/probe endpoints
- Standard pattern: health probes at /actuator/** and /internal/v1/health are permitted; all other endpoints require authentication

### Read First
- Read vito-service's application.yml and SecurityConfig.java as the reference implementation
- Read each target service's SecurityConfig.java before modifying

---

## Agent 3: OpenAPI Contracts + Health Endpoints

### Problem A: 5 services missing OpenAPI contracts
- schema-registry-service (8371)
- audit-ledger-service (8350)
- developer-portal-service (8370)
- identity-assurance-service (8201)
- butano-fhir (8289)

### Problem B: 3 services missing health check endpoints
- varapi-service (8083) — has V11ProbeController but audit flagged no health checks
- tuso-service (8084) — has V11ProbeController but audit flagged no health checks
- mushex-service (8102) — has V11ProbeController but audit flagged no health checks

### Task A: Create 5 OpenAPI Contracts
For each service: read ALL controllers, then create a contract at `contracts/openapi/{name}.openapi.yaml`

Follow the EXACT format of `contracts/openapi/varapi.openapi.yaml` (read it first).

1. `contracts/openapi/schema-registry.openapi.yaml` (port 8371)
   - Read controllers in services/schema-registry-service/src/main/java/

2. `contracts/openapi/audit-ledger.openapi.yaml` (port 8350)
   - Read controllers in services/audit-ledger-service/src/main/java/

3. `contracts/openapi/developer-portal.openapi.yaml` (port 8370)
   - Read controllers in services/developer-portal-service/src/main/java/

4. `contracts/openapi/identity-assurance.openapi.yaml` (port 8201)
   - Read controllers in services/identity-assurance-service/src/main/java/

5. `contracts/openapi/butano-fhir.openapi.yaml` (port 8289)
   - Read controllers in services/butano-fhir/src/main/java/

### Task B: Verify Health Endpoints
For varapi, tuso, mushex — verify the V11ProbeController provides /internal/v1/health.
If the actuator health endpoint is also needed, verify spring.management.endpoints config in application.yml. Add if missing:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
```

### Read First
- Read varapi.openapi.yaml for contract format
- Read each service's controllers to discover endpoints before writing contracts

---

## Agent 4: Audit Outbox + Kafka for Primitive Services

### Problem A: tshepo-audit-service has NO outbox entity
It receives audit events via Kafka consumer but cannot publish its own events (e.g., audit chain verification results, export completion).

### Problem B: 4 primitive services have NO Kafka configuration
- schema-registry-service (8371)
- audit-ledger-service (8350)
- developer-portal-service (8370)
- channels-service (8130)

### Task A: Add Outbox to tshepo-audit-service

1. Create `services/tshepo-audit-service/src/main/java/zw/gov/mohcc/impilo/tshepo/audit/persistence/entity/EventOutboxEntity.java`
   - Follow the pattern from any other TSHEPO service, e.g. tshepo-consent-service
   - Read: `services/tshepo-consent-service/src/main/java/zw/gov/mohcc/impilo/tshepo/consent/persistence/EventOutboxEntity.java`

2. Create `services/tshepo-audit-service/src/main/java/zw/gov/mohcc/impilo/tshepo/audit/persistence/repository/EventOutboxRepository.java`

3. Create `services/tshepo-audit-service/src/main/java/zw/gov/mohcc/impilo/tshepo/audit/events/AuditOutboxPublisher.java`
   - Extend CompanionOutboxPublisher from shared-kernel-java
   - Use EventTopicRegistry("tshepo-audit")
   - Wire with @Scheduled polling

4. Create Flyway migration `services/tshepo-audit-service/src/main/resources/db/migration/V002__add_event_outbox.sql`
   - Standard v1.1 outbox table in the tshepo_audit schema

### Task B: Add Kafka Producer Config to 4 Primitive Services

For each service, add to application.yml:
```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: all
      retries: 3
      properties:
        enable.idempotence: true
        max.in.flight.requests.per.connection: 5
```

Files to modify:
1. `services/schema-registry-service/src/main/resources/application.yml`
2. `services/audit-ledger-service/src/main/resources/application.yml`
3. `services/developer-portal-service/src/main/resources/application.yml`
4. `services/channels-service/src/main/resources/application.yml`

Also verify each service's pom.xml includes spring-kafka dependency. If missing, add:
```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

### Read First
- Read tshepo-consent-service's EventOutboxEntity + ConsentOutboxPublisher for the pattern
- Read CompanionOutboxPublisher.java in libs/shared-kernel-java for the base class
- Read each target service's application.yml and pom.xml before modifying
