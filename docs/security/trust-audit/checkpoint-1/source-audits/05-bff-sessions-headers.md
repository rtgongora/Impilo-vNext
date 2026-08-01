# Source audit — BFF sessions, trust headers, spoofing

Branch `claude/tshepo-trust-cp1-truth-audit`, commit `f190318e1`.
Source: [Audit BFF, sessions, headers, spoofing](32e803f3-65a1-4fc5-b6bc-f53e660cd5b2).
Classification against **intended production design**.

## Headline

BFF-managed OIDC (authorization code + PKCE, AES-GCM Redis sessions, `__Host-` cookies, double-submit CSRF) is **ENFORCED in preview**. Legacy ROPC/password/passkey/biometric endpoints are `denyAll()` when JWT is present. Browser token persistence is gone.

**Load-bearing caveat:** Traefik routes `/internal` straight to experience-bff; Envoy header-stripping and ext_authz are **off the wire**. On that path, only `X-Actor-ID` is server-authoritative (BFF `ActorContextFilter`). `X-Assurance-Level` and `X-Provider-ID` remain client-supplied and are forwarded downstream. JWT validation covers signature/issuer/expiry but **not audience**. Authenticated-lane Envoy stripping does not remove client identity headers even when Envoy is on-path — it relies on PDP re-injection.

## Classification

| Capability | Classification |
|---|---|
| BFF OIDC auth-code + PKCE + encrypted Redis | **ENFORCED** (preview; disabled-by-default in shipped config) |
| `__Host-` cookies / Secure / HttpOnly / SameSite | **ENFORCED** |
| CSRF double-submit | **ENFORCED** |
| Session inspect + logout | **ENFORCED** |
| No browser OAuth-token persistence | **ENFORCED** |
| Legacy ROPC/password/passkey/biometric | **DISCONNECTED** (denyAll when JwtDecoder present) |
| Public-lane allow-list | **ENFORCED** (as designed) |
| Trust-header contract documentation | **ENFORCED** (documented + used) |
| Public-lane Envoy header stripping | **ENFORCED IN CONFIG / DISCONNECTED** (preview off-path) |
| Authenticated-lane strip of actor/assurance/provider | **PARTIAL / ABSENT** |
| PDP TPL-1 identity re-injection | **SHADOW / DISCONNECTED** in preview |
| BFF server-authoritative `X-Actor-ID` | **ENFORCED** |
| Server-authoritative `X-Assurance-Level` / `X-Provider-ID` | **BYPASSABLE** (preview path) |
| Service JWT signature/issuer/expiry | **ENFORCED** (where OAuth not disabled) |
| JWT audience validation | **ABSENT** |
| East-west / ingress PDP gate | **DISCONNECTED** |
| NodePort/LB direct-backend exposure | **ABSENT** (ClusterIP only) |
| Envoy as mandatory choke point | **BYPASSABLE by design** (Traefik→BFF) |

## Runtime confirmations (2026-08-01)

| Question | Answer |
|---|---|
| Envoy on external path? | **No** — Traefik→BFF/UI/Keycloak |
| Web session enabled on BFF? | **Yes** — `IMPILO_AUTH_WEB_SESSION_ENABLED=true`, cookie secure true |
| Session encryption key present? | **Yes** — secretKeyRef (value not read) |
| ALLOW_ANONYMOUS / AUTH_FALLBACK | **false / false** |
| Audience validation | Still **ABSENT** in source |

