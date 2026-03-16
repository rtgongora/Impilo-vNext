# Open Blockers — Cross-Service Integration Closure

## Overview
This document lists all remaining blockers and gaps that could not be fully resolved during the Cross-Service Integration Closure Wave. Each entry includes the exact residual issue, impact, and recommended next action.

## Blocker 1: OAuth2 Disabled in Dev Mode

**Status**: KNOWN LIMITATION
**Impact**: Medium — integration tests can run with trust headers only, but JWT validation is bypassed
**Details**:
- All services in `docker-compose.runtime.yml` set `SPRING_AUTOCONFIGURE_EXCLUDE=...OAuth2ResourceServerAutoConfiguration`
- This means Bearer tokens from Keycloak are accepted but not validated by Spring Security
- Trust headers (X-Tenant-ID, X-Pod-ID, etc.) are still enforced by shared-core TrustContextFilter
**Next Action**: Remove the `SPRING_AUTOCONFIGURE_EXCLUDE` override and set `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` to `http://keycloak:8080/realms/impilo` once the realm import is confirmed stable. This requires testing each service's SecurityConfig with real JWT validation.
**File**: `docker-compose.runtime.yml` (lines 188-189, 222-223, etc.)

## Blocker 2: Experience BFF Stage-1 Mock Auth

**Status**: SPEC CONFLICT
**Impact**: Low — BFF provides a mock session endpoint, not real OIDC
**Details**:
- `services/experience-bff/src/main/java/.../AuthSessionController.java` returns a UUID session token, not a Keycloak JWT
- For full E2E browser flow, the UI would need to redirect through Keycloak OIDC
- For API-level integration tests, direct Keycloak token + trust headers is sufficient
**Next Action**: Implement OIDC-aware session management in BFF that validates Keycloak tokens and translates to BFF sessions. This is an experience-layer enhancement, not a blocker for backend integration testing.
**File**: `services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/AuthSessionController.java`

## Blocker 3: Service Port Conflicts (15+ pairs)

**Status**: DOCUMENTED — NOT BLOCKING
**Impact**: Low — only matters when running conflicting services simultaneously outside docker-compose
**Details**:
All affected services use `${SERVER_PORT:default}` pattern. Docker-compose assigns unique ports. Conflicts exist only in application.yml defaults.
**Key conflicts**:
- tshepo-service (8081) vs tshepo-authz-service (8081)
- reporting-service (8160) vs experience-bff (8160)
- See full list in `docs/integration-closure/runtime-orchestration-cleanup.md`
**Next Action**: For any future full-stack compose file that includes all services, assign unique host ports. No code change needed — env variable overrides are sufficient.

## Blocker 4: Support and Notification Services Not in Runtime Compose

**Status**: DOCUMENTED
**Impact**: Medium — Steel threads C (Support) and D (Messaging) cannot execute against the canonical runtime without adding these services
**Details**:
- `docker-compose.runtime.yml` includes 8 backend services but not support-service (8340) or notification-service (8111)
- The steel thread tests for these threads assume the services are running at their default ports
**Next Action**: Add support-service and notification-service to docker-compose.runtime.yml or create a separate docker-compose.integration.yml that extends it.

## Blocker 5: Kafka Consumer Verification

**Status**: PARTIAL
**Impact**: Low — outbox rows can be verified, but end-to-end consumer processing requires running consumers
**Details**:
- Steel Thread E verifies outbox table rows and Kafka topic existence
- Full consumer verification (e.g., TSHEPO audit consumer processing an event from VITO) requires all consumer services running
- Consumer wiring exists in code (EventEnvelope, Kafka config) but live consumer tests are not in scope for this wave
**Next Action**: Create a consumer integration test that starts a minimal consumer and verifies it processes an event from the outbox-published topic.

## Blocker 6: MINIMAL Components

**Status**: DOCUMENTED
**Impact**: Low — neither MINIMAL component is on any steel thread critical path
**Details**:
- `libs/tech-companion-harness` (MINIMAL) — has GoldenContractSuite but no self-tests. Functions correctly as a test base class.
- `ui/self-service` (MINIMAL) — basic self-service portal. Not involved in any integration flow.
- `ui/ehr` (FRAGILE → DEPRECATED) — formally deprecated in this wave.
**Next Action**:
- tech-companion-harness: Add self-tests if code coverage requirements demand it. Not blocking integration.
- self-service: Flesh out when self-service flows are specified. Not blocking integration.

## Blocker 7: Mobile App Integration Tests

**Status**: OUT OF SCOPE
**Impact**: None for this wave — mobile apps (citizen-app, provider-app) are tested separately
**Details**:
- Mobile apps are MOBILE-READY (React Native) with their own test suites
- They would use the same Keycloak realm and trust header contract
- Cross-service integration from mobile perspective requires a mobile testing environment (emulators, etc.)
**Next Action**: Mobile integration testing is a separate effort that can leverage the same auth bootstrap artifacts.

## Summary

| # | Blocker | Severity | Fixable Now | Next Action |
|---|---------|----------|-------------|-------------|
| 1 | OAuth2 disabled in dev | Medium | Partially | Enable JWT validation in compose env |
| 2 | BFF mock auth | Low | No | OIDC session management enhancement |
| 3 | Service port conflicts | Low | Documented | Env variable overrides |
| 4 | Missing services in compose | Medium | Yes | Add to compose or create integration profile |
| 5 | Kafka consumer verification | Low | Partially | Consumer integration test |
| 6 | MINIMAL components | Low | No | Not on critical path |
| 7 | Mobile integration | N/A | N/A | Separate mobile testing effort |
