# Source audit — East-west (service-to-service) trust

Branch `claude/tshepo-trust-cp1-truth-audit`, commit `f190318e1`.
Source: [Audit service-to-service trust flows](1c61aa6a-c8ff-42d3-a854-b17fd5d970d8).
Classification against **intended production design** (not preview posture).

## Headline

A real three-tier outbound credential model exists in code, but the **runtime east-west enforcement floor is absent**. Envoy → TSHEPO ext_authz is not on the path; Kafka is PLAINTEXT; all workloads share the `default` ServiceAccount; NetworkPolicy/cert-manager manifests target namespaces that are not deployed; there is no inter-service mTLS.

Outbound patterns:

1. **Inbound-token forwarding + minted client_credentials fallback** — experience-bff (`ServiceClientConfig`).
2. **Minted client_credentials (service-originated, optional)** — pct-service, mvumo-service.
3. **Inbound-token-only propagation** — most domain clients; background/no-context calls carry **no credential**.

## Classification (vs intended production design)

| # | Capability | Classification | Evidence |
|---|---|---|---|
| 1 | Envoy → TSHEPO ext_authz universal gate | **DISCONNECTED** | Deployed ConfigMap has 0 `ext_authz`; Traefik routes to BFF/UI/Keycloak; `envoy.extAuthz.enabled: false` |
| 2 | Per-service inbound OAuth (compiled default) | **PARTIAL** | Enforce-by-default in SecurityConfig; runtime disable on 96 services |
| 3 | Empty-issuer domain services (oros/pct/product-registry) | **BYPASSABLE** | Empty `KEYCLOAK_ISSUER` / JWT issuer URI in full-preview runtime values |
| 4 | Unconditional permitAll (jobs/offline-sync/offline-edge/support) | **ABSENT** | SecurityConfig `anyRequest().permitAll()` with no OAuth branch |
| 5 | Explicit permit-all flags (llm/ai-model-registry/iot) | **BYPASSABLE** | `*_ALLOW_INSECURE_PERMIT_ALL` / `IMPILO_SECURITY_MODE=permit-all` |
| 6 | BFF minted client_credentials | **ACTIVE_NOT_ENFORCED** | Real minting code; token optional; callees rarely require it |
| 7 | pct/mvumo minted CC | **ACTIVE_NOT_ENFORCED** | Token optional; degrades to unauthenticated |
| 8 | Domain inbound-token-only | **BYPASSABLE** | No credential in scheduled/background context |
| 9 | Trust headers as authentication | **SHADOW** | Present everywhere; documented as context not auth; forgeable |
| 10 | Kafka auth + ACLs | **ABSENT** | PLAINTEXT listeners, no SASL/ACL |
| 11 | Dedicated K8s ServiceAccounts | **ABSENT** | 114/116 workloads use `default` |
| 12 | NetworkPolicy segmentation | **DISCONNECTED** | Manifests for wrong namespaces; 0 NetPols in live ns |
| 13 | Inter-service mTLS | **ABSENT** | No `server.ssl`, no mesh |
| 14 | Edge TLS (Traefik) | **ENFORCED** (north-south only) | Let's Encrypt at Traefik |
| 15 | Keycloak admin edge | **ENFORCED** | Dedicated admin token, un-intercepted RestTemplate |

## Credential inventory (names only)

- Shared Keycloak clients: `experience-ui`, `impilo-backend`, `impilo-user-admin`, `impilo-event-reader`.
- Secrets: `keycloak-backend-secret`, `keycloak-client-secret-bff`, `keycloak-user-admin-secret`, `web-session-encryption-key`, `postgres-credentials`, SMS/SMTP (preview = log mode).
- Bootstrap: `IMPILO_BOOTSTRAP_TOKEN_HASH` (verify single-use at runtime — still open).

## Runtime confirmations (2026-08-01)

See `../runtime-evidence/OPEN_QUESTION_ANSWERS.md` and `../runtime-evidence/BYPASS_INVENTORY.md`.

- Envoy deployed config: **0** `ext_authz` references — confirmed.
- Workloads share `default` SA — confirmed (114/116).
- `KEYCLOAK_BACKEND_SECRET` is present as `secretKeyRef` on experience-bff — confirmed (value not read).
- Kafka PLAINTEXT — confirmed.
