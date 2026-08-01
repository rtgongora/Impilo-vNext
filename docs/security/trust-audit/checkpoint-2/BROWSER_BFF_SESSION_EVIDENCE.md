# Browser BFF session — fresh runtime evidence (Checkpoint 2 closure)

**Captured:** 2026-08-01T14:14:34Z  
**Capture host:** engineering VM (`impilo.mohcc.gov.zw` / cluster-local ingress `10.50.1.67`)  
**Branch under audit (contracts work):** `claude/tshepo-trust-cp1-truth-audit`  
**Deployed preview branch (running images):** `codex/mfa-production`  
**No deploy / no enforcement change / no credential rotation in this checkpoint.**

Layers: `SOURCE_IMPLEMENTED` / `TEST_PROVEN` / `PREVIEW_DEPLOYED` / `PREVIEW_ENFORCED`.

---

## 1. Deployed digest and mapped commit

| Item | Value | Layer |
|---|---|---|
| Live Deployment image | `127.0.0.1:5000/impilo/experience-bff@sha256:1948d8d355b5a3456ed0bbdf1feb195143ff4f45b348b8dcb85b1d41b3ea763b` | PREVIEW_DEPLOYED |
| Ready replicas | `2/2` (`impilo-full-preview`) | PREVIEW_DEPLOYED |
| OCI label `org.opencontainers.image.revision` | `486b3a4ff93e6e4b2cfb9eb8ea1aa7503649b565` | MAPPED |
| OCI label `zw.gov.mohcc.impilo.source.branch` | `codex/mfa-production` | MAPPED |
| Live `/health/version` `commit` / `bffCommit` | `486b3a4ff93e6e4b2cfb9eb8ea1aa7503649b565` | PREVIEW_DEPLOYED |
| Live `/health/version` `imageDigests.experience-bff` | `sha256:57c6952eb604d8e547acf86377e9c442e599920a2da9ff1042567fde72808e53` | **PARTIAL** — does **not** match the live Deployment digest above; treat the Deployment digest + OCI labels as authoritative for this capture |

**Verdict:** running BFF digest → commit `486b3a4f…` is **MAPPED**. The `/health/version` digest map field is **PARTIAL** (stale/mismatched) and must not be used alone.

---

## 2. Live routes

Probed via Traefik ingress IP `10.50.1.67` with `Host: impilo.mohcc.gov.zw` (public DNS from this host returned HTTP 000 / no connect).

| Route | HTTP | Notes |
|---|---|---|
| `GET /health/version` | **200** | Returns BFF identity JSON (see §1) |
| `GET /internal/v1/auth/oidc/session` | **401** | Unauthenticated — session gate closed |
| `GET /internal/v1/auth/oidc/callback` | **400** | Route present; rejects unbound callback |
| `GET /internal/v1/auth/oidc/login` | **401** | Present, gated |
| `GET /internal/v1/auth/oidc/start` | **401** | Present, gated |
| `GET /realms/impilo/.well-known/openid-configuration` | **200** | Keycloak discovery |

UI client entry: `ui/one-ui-shell/src/lib/auth/web-session.ts` → `beginOidcLogin` navigates to `/internal/v1/auth/oidc/authorize`.

---

## 3. BFF session cookie behavior

| Evidence | Result | Layer |
|---|---|---|
| Deploy env `IMPILO_AUTH_WEB_SESSION_ENABLED` | `true` | PREVIEW_DEPLOYED |
| Deploy env `IMPILO_AUTH_WEB_SESSION_COOKIE_SECURE` | `true` | PREVIEW_DEPLOYED |
| Cookie name (source) | `__Host-impilo_session` in `WebAuthSessionStore.SESSION_COOKIE` | SOURCE_IMPLEMENTED |
| Unauthenticated `GET …/oidc/session` Set-Cookie | **none** (only HTTP/2 401) | PREVIEW_ENFORCED for *absence of session mint without auth* |
| Successful login Set-Cookie attributes (`Secure` / `HttpOnly` / `__Host-` prefix on the wire) | **Not freshly captured** in this window (no interactive login performed) | **PARTIAL** (config + source proven; wire attributes not re-proven) |

Legacy `AuthSessionController` still references `exp_refresh_token`. That is a retained compatibility surface; this capture does **not** prove that cookie is absent from every login path — classify wire exclusivity of `__Host-impilo_session` as **PARTIAL**.

---

## 4. Absence of browser token persistence

| Evidence | Result | Layer |
|---|---|---|
| `useAuthStore.setAuth` / `hydrateSession` / `setTokens` force `token: null` and `refreshToken: null` | Present in `ui/one-ui-shell/src/hooks/useAuthStore.ts` | SOURCE_IMPLEMENTED |
| Unit tests assert refreshToken stays null | `useAuthStore.test.ts` | TEST_PROVEN |
| Browser localStorage / sessionStorage scan after live login | **Not performed** this window | **UNKNOWN** (runtime) |
| Session JSON body for unauthenticated call | `{"error":…}` only — no token fields | PREVIEW_ENFORCED for unauthenticated response shape |

**Facet verdict:** browser token non-persistence is **SOURCE_IMPLEMENTED + TEST_PROVEN**; live browser storage absence remains **UNKNOWN** without an authenticated browser capture.

---

## 5. Keycloak issuer

| Evidence | Result | Layer |
|---|---|---|
| Discovery `issuer` | `https://impilo.mohcc.gov.zw/realms/impilo` | PREVIEW_ENFORCED (public discovery) |
| BFF env `KEYCLOAK_ISSUER_URI` | `https://impilo.mohcc.gov.zw/realms/impilo` | PREVIEW_DEPLOYED |
| BFF env `KEYCLOAK_INTERNAL_ISSUER` | `http://keycloak:8080/realms/impilo` | PREVIEW_DEPLOYED |
| BFF env `KEYCLOAK_CLIENT_ID` | `experience-ui` | PREVIEW_DEPLOYED |
| Keycloak Deployment image | `127.0.0.1:5000/impilo/keycloak@sha256:70f0af3d5a9352c1d62cf6ea059430faaa10ed772bb63bea690c99cd2a4836bc` | PREVIEW_DEPLOYED |
| Keycloak OCI revision | `304152be61a790c2e92f40f36b1db2b4e6ff11c6` (`codex/mfa-production`) | MAPPED |
| Resource-owner password grant advertised | `false` in discovery `grant_types_supported` | PREVIEW_DEPLOYED |

---

## 6. Callback / session-status proof

| Check | Result | Layer |
|---|---|---|
| Callback route responds | HTTP **400** without OAuth params | PREVIEW_ENFORCED (route live, unbound rejected) |
| Session-status without cookie | HTTP **401**, body keys `["error"]` only | PREVIEW_ENFORCED |
| Authenticated session-status (200 + user/ACR/AMR) | **Not captured** (no login) | **UNKNOWN** |

---

## 7. Enforcement point

| Point | Result | Layer |
|---|---|---|
| Edge | Traefik ingress `acme-host-nginx-gov` → `impilo.mohcc.gov.zw` | PREVIEW_DEPLOYED |
| Application PEP | `experience-bff` OIDC session controllers (`OidcSessionController`, session CSRF / bearer resolvers using `__Host-impilo_session`) | SOURCE_IMPLEMENTED + PREVIEW_DEPLOYED |
| Unauthenticated session denied | HTTP 401 at `/internal/v1/auth/oidc/session` | PREVIEW_ENFORCED |
| Envoy ext_authz on this path | Not required for this facet; Envoy east-west remains as Checkpoint 1 (DISCONNECTED / not on browser ingress) | n/a |

---

## Aggregate facet status

| Facet | Status after this capture |
|---|---|
| BFF session feature deployed & enabled | **PREVIEW_DEPLOYED** |
| Unauthenticated session/callback gating | **PREVIEW_ENFORCED** |
| Digest → commit mapping (Deployment image) | **MAPPED** (`486b3a4f…`) |
| `/health/version` imageDigests accuracy | **PARTIAL** (mismatched digest field) |
| Cookie wire attributes on successful login | **PARTIAL** |
| Authenticated session-status body | **UNKNOWN** |
| Browser storage absence after login | **UNKNOWN** (runtime) / **TEST_PROVEN** (unit) |
| Keycloak issuer discovery | **PREVIEW_ENFORCED** |

**Statement retained with narrowed scope:**

> Browser BFF OIDC session **gating** (enabled flag, unauthenticated 401 on session-status, live callback route, Keycloak issuer) is **PREVIEW_ENFORCED** on `experience-bff@sha256:1948d8d3…` (commit `486b3a4f…`). Successful-login cookie attributes and authenticated session-status body were **not** freshly proven in this window and are **PARTIAL** / **UNKNOWN** respectively — do not over-claim them as PREVIEW_ENFORCED.
