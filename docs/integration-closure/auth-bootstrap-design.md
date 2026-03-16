# Auth Bootstrap Design

## Overview

The Impilo vNext platform uses Keycloak 25.x as its identity provider. All backend services use Spring Security OAuth2 Resource Server with JWT validation against the `impilo` realm. In the current development configuration, all services disable OAuth2 via `SPRING_AUTOCONFIGURE_EXCLUDE`, meaning JWT validation is bypassed entirely. This design introduces a deterministic Keycloak realm import that provisions a known set of clients, roles, and test users so that integration tests can exercise real authentication and authorization paths without manual setup.

## Current Auth Posture

- `docker-compose.runtime.yml` includes a Keycloak container launched with the `start-dev` command.
- No realm import exists — Keycloak starts with an empty configuration (only the `master` realm).
- All services bypass JWT validation in dev mode by excluding the Spring Security OAuth2 Resource Server auto-configuration.
- The Experience BFF exposes a Stage-1 mock auth endpoint at `/internal/v1/auth/login` that returns a synthetic token for UI development.
- Trust headers (`X-Tenant-ID`, `X-Pod-ID`, `X-Request-ID`, `X-Correlation-ID`) are enforced independently of OAuth2 by the shared-core `TrustContextFilter`. These headers flow regardless of whether JWT validation is active.

## Bootstrap Artifacts

### Realm Import (`tools/auth/impilo-realm.json`)

A self-contained Keycloak realm export file that declaratively defines:

- **Realm name**: `impilo`
- **5 clients**: `experience-ui`, `one-ui-shell`, `citizen-portal`, `impilo-backend`, `integration-test` (see Test Clients section)
- **8 roles**: `SYSTEM_ADMIN`, `FACILITY_ADMIN`, `CLINICIAN`, `NURSE`, `CITIZEN`, `SUPPORT_AGENT`, `SERVICE_ACCOUNT`, `INTEGRATION_TEST`
- **6 test users**: Pre-provisioned identities with deterministic passwords and role assignments (see Test Identities section)
- **Custom protocol mappers**: Token mappers that project user attributes (`facility_id`, `provider_number`, `cpid`) and role assignments into JWT claims, matching the claim structure expected by Spring Security resource servers and the trust header contract.

### Bootstrap Script (`scripts/integration-closure/bootstrap-auth.sh`)

A shell script that automates Keycloak provisioning for integration test environments:

- Waits for the Keycloak container to become healthy (polls the `/health/ready` endpoint).
- Copies the realm JSON into the Keycloak container and triggers an import via the Keycloak Admin CLI, or relies on the `--import-realm` startup flag if the volume mount is configured.
- Verifies that the `impilo` realm, all clients, and all test users exist after import.
- **Idempotent behavior**: The script checks whether the `impilo` realm already exists before attempting import. If the realm is present, it skips the import and logs a message. This allows the script to be run multiple times safely (e.g., in CI pipelines or after container restarts with persistent volumes).

Usage:

```bash
./scripts/integration-closure/bootstrap-auth.sh
```

## Test Identities

| Username | Role | Email | Attributes | Use Case |
|---|---|---|---|---|
| admin.central | SYSTEM_ADMIN | admin@mohcc.gov.zw | - | Steel Thread A: Admin flows |
| admin.harare | FACILITY_ADMIN | harare.admin@mohcc.gov.zw | - | Facility-scoped admin flows |
| dr.mapfumo | CLINICIAN | mapfumo@mohcc.gov.zw | facility_id, provider_number | Steel Thread A: Provider auth |
| nurse.chienda | NURSE | chienda@mohcc.gov.zw | facility_id | Clinical flows |
| citizen.moyo | CITIZEN | tatenda.moyo@example.com | cpid=CPID-ZW-00001 | Steel Thread B: Citizen flow |
| support.agent1 | SUPPORT_AGENT | support@mohcc.gov.zw | - | Steel Thread C: Support escalation |

All test users share a deterministic password suitable for automated test execution. Passwords must never be used outside of local development and CI environments.

## Test Clients

| Client ID | Type | Purpose | Secret |
|---|---|---|---|
| experience-ui | public | Experience UI OIDC | - |
| one-ui-shell | public | One UI Shell | - |
| citizen-portal | public | Citizen portal | - |
| impilo-backend | confidential | Service-to-service | impilo-backend-secret |
| integration-test | confidential | Integration test harness | integration-test-secret |

Public clients use PKCE for authorization code flow. Confidential clients use client credentials grant for service-to-service communication and direct access grant for integration test token acquisition.

## Integration with Docker Compose

Mount the realm JSON file into the Keycloak container via a volume bind and pass the `--import-realm` flag so that the realm is provisioned automatically on first startup.

Updated Keycloak service configuration:

```yaml
keycloak:
  image: quay.io/keycloak/keycloak:25.0
  command: start-dev --import-realm
  environment:
    KEYCLOAK_ADMIN: admin
    KEYCLOAK_ADMIN_PASSWORD: admin
  volumes:
    - ./tools/auth/impilo-realm.json:/opt/keycloak/data/import/impilo-realm.json:ro
  ports:
    - "8080:8080"
```

With this configuration, Keycloak imports the `impilo` realm on first boot. The `--import-realm` flag is a no-op if the realm already exists in the database (i.e., when using a persistent volume), preserving idempotent behavior.

## Integration with Services

When OAuth2 is enabled (i.e., `SPRING_AUTOCONFIGURE_EXCLUDE` is removed or overridden), each Spring Boot service validates incoming JWTs against the Keycloak issuer URI:

```
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://keycloak:8080/realms/impilo
```

Trust headers are a separate concern. The shared-core `TrustContextFilter` extracts and validates `X-Tenant-ID`, `X-Pod-ID`, `X-Request-ID`, and `X-Correlation-ID` regardless of whether OAuth2 is active. In production, Envoy ext_authz populates these headers via TSHEPO; in integration tests, the test harness supplies both a valid Bearer token and the required trust headers on every request.

To obtain a token in integration tests, use the OAuth2 direct access grant (Resource Owner Password Credentials) against the Keycloak token endpoint:

```
POST http://keycloak:8080/realms/impilo/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password&client_id=integration-test&client_secret=integration-test-secret&username=dr.mapfumo&password=<test-password>
```

The returned access token is then included as a `Bearer` token in the `Authorization` header alongside the trust headers for each service call.

## SPEC CONFLICT

The Experience BFF Stage-1 auth endpoint (`/internal/v1/auth/login`) returns a mock token that is not a real Keycloak JWT. This creates a divergence between how the UI layer authenticates and how backend services validate tokens.

For full integration, two resolution paths exist:

- **(a)** Route the UI through the standard Keycloak OIDC authorization code flow, eliminating the mock endpoint entirely.
- **(b)** The BFF validates real Keycloak tokens and translates them into a server-side session, acting as a Backend-for-Frontend OAuth2 client.

**Current approach for integration testing**: Integration tests targeting backend services (TSHEPO, VITO, VARAPI, etc.) obtain real Keycloak tokens via the direct access grant and include them as Bearer tokens. Integration tests targeting UI-layer flows continue to use the BFF mock endpoint at `/internal/v1/auth/login`. This split allows backend auth validation to be tested end-to-end while the BFF auth strategy is finalized.
