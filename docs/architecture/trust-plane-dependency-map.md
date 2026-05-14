# Trust Plane Dependency Map

Date: 2026-05-14

## Service-to-Service Trust Dependencies

| Service | Depends on | Dependency type | Purpose |
|---|---|---|---|
| `mvumo-service` | `tshepo-consent-service` | REST (`/v1/consent/*`) | consent directive create/evaluate/revoke |
| `tshepo-authz-service` | `tshepo-consent-service` | REST | consent-aware policy decisions |
| `experience-bff` | `tshepo-authz-service`, `tshepo-audit-service`, `mvumo-service` | REST + gateway policy | admin/security/trust workflow orchestration |
| `tshepo-service` (legacy) | canonical TSHEPO split services (migration target) | compatibility overlap | constrained legacy consumer continuity |
| `vito-service`, `varapi-service`, `msika-flow-service` | `tshepo-authz-service` compatibility policy routes | REST compatibility proxy | migrated policy consumer entry-point away from direct `tshepo-service` dependency |
| `identity-assurance-service` | trust headers + JWT context, downstream trust consumers | trust context contract | risk/attestation data for trust decisions |
| `audit-ledger-service` | trust-plane producers (`tshepo-*`, `mvumo-service`) | event/query integration | immutable audit evidence store |
| `security-hardening-service` | trust-plane operators/services | security governance API/events | control baselines and security operations |

## Data Stores and Persistence

| Service | Persistence | Notes |
|---|---|---|
| `mvumo-service` | PostgreSQL (`mvumo` schema), Redis token registry, Kafka outbox publish | consent requests/templates/sessions/events/outbox |
| `tshepo-authz-service` | service DB + outbox | policy, break-glass, step-up/device controls |
| `tshepo-consent-service` | service DB + outbox | consent directive SoR |
| `tshepo-identity-service` | service DB | identity resolution/tokenization mappings |
| `tshepo-audit-service` | service DB + chain artifacts | immutable/verified trust audit operations |
| `tshepo-keys-service` | service DB + key material operations | JWKS/signing/rotation/certificates |
| `tshepo-offline-service` | service DB + outbox | offline trust pack/capability/reconcile |
| `audit-ledger-service` | immutable ledger DB | forensic verification and chain checks |
| `security-hardening-service` | service DB + outbox | policy packs/scans/control lifecycle |

## Platform Trust Integrations

| Integration | Current use in trust plane |
|---|---|
| Keycloak (`spring.security.oauth2.resourceserver.jwt.issuer-uri`) | JWT resource-server auth across trust services |
| Envoy `ext_authz` | policy decision checks delegated to TSHEPO authz |
| OPA | architecture placeholder; no authoritative production trust path evidence in this pass |
| Trust headers (`x-tenant-id`, `x-actor-id`, `x-purpose-of-use`, `x-device-fingerprint`, `x-correlation-id`, facility/workspace/shift) | extracted via `TrustContextFilter` and consumed by trust services |
| Correlation IDs | propagated through trust headers and audit payloads/events |

## BFF/Frontend/Admin Trust Wiring

| Surface | BFF/Gateway | Backend trust services |
|---|---|---|
| Communication preferences & consent capture | `/internal/v1/mvumo/*` via BFF/mobile routes | `mvumo-service` + `tshepo-consent-service` |
| Policy enforcement (all channels) | Envoy `ext_authz` | `tshepo-authz-service` |
| Trust audit and forensic admin | `/internal/v1/admin/audit*` | `tshepo-audit-service` + `audit-ledger-service` |
| Identity assurance admin/security workflows | internal trust/security routes | `identity-assurance-service` (+ authz dependency) |
| Legacy compatibility consumers | constrained `/v1/*` compatibility routes | `tshepo-service` (retirement path active) |

## Security Profiles and Secrets

- Trust services use OAuth2 resource-server JWT configuration via Keycloak issuer properties.
- `mvumo-service` TSHEPO integration supports OAuth2 client credentials and externalized secret values.
- `tshepo-service` no longer defaults to permissive `anyRequest().permitAll()`; non-public routes require authentication.
- `tshepo-service` legacy `/v1/*` compatibility traffic now emits deprecation telemetry and is tracked via `/internal/v1/legacy/route-usage`.
- Test-only permissive security remains isolated in explicit test profile/test classes.

## Observability and Operations

- Health/info/prometheus endpoints are exposed for trust services.
- Micrometer/actuator present across audited trust services.
- MVUMO and TSHEPO event/outbox patterns provide operational reconciliation hooks.

## Remaining Dependency Blockers

1. CI-grade end-to-end trust scenario coverage still needs full multi-service runtime orchestration (`mvumo` + `authz` + `consent` + `audit` + ledger + BFF path).
2. Canonical route harmonization (`/internal/v1`) across TSHEPO split services still incomplete.
3. Legacy monolith retirement execution still requires zero-usage window and full compatibility proxy decommission plan.
