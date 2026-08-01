# Checkpoint 3 — MFA runtime truth

Captured 2026-08-01 on Web Preview / Engineering Control (`impilo.mohcc.gov.zw` via in-cluster Traefik `10.50.1.67`). No credentials, tokens, cookies, recovery codes or personal data are recorded below.

## Classification legend

Each facet is classified independently as one of: `SOURCE_IMPLEMENTED`, `TEST_PROVEN`, `PREVIEW_DEPLOYED`, `PREVIEW_ENFORCED`, `PARTIAL`, `INSUFFICIENT_EVIDENCE`, `UNKNOWN`.

## 1. Keycloak version / image / provenance

| Fact | Value | Classification |
|---|---|---|
| Deployed image | `127.0.0.1:5000/impilo/keycloak@sha256:70f0af3d5a9352c1d62cf6ea059430faaa10ed772bb63bea690c99cd2a4836bc` | `PREVIEW_DEPLOYED` |
| OCI version label | `26.7.0` | `PREVIEW_DEPLOYED` |
| Source revision label | `304152be61a790c2e92f40f36b1db2b4e6ff11c6` | `PREVIEW_DEPLOYED` |
| Impilo source tree | `52e64b981d3ffd45cf4a0dee6d795d17c77ec12a` (branch `codex/mfa-production`) | `PREVIEW_DEPLOYED` |
| Start args | `start --optimized --http-enabled=true --http-port=8080` | `PREVIEW_DEPLOYED` |

## 2. PostgreSQL backing and persistence

| Fact | Value | Classification |
|---|---|---|
| `KC_DB` | `postgres` | `PREVIEW_DEPLOYED` |
| Host / port / database | `postgres:5432` / `keycloak` | `PREVIEW_DEPLOYED` |
| Postgres Deployment | `postgres` Ready 1/1 | `PREVIEW_DEPLOYED` |
| Persistence PVCs | `postgres-data` 50Gi Bound; `postgres-backups` 30Gi Bound | `PREVIEW_DEPLOYED` |
| Keycloak H2 PVC retained | `keycloak-data` 2Gi Bound (rollback source) | `PREVIEW_DEPLOYED` |
| Migration snapshot PVC | `keycloak-migration-backup` 5Gi Bound | `PREVIEW_DEPLOYED` |

## 3. Realm configuration / reconciler

| Fact | Value | Classification |
|---|---|---|
| Issuer (live discovery) | `https://impilo.mohcc.gov.zw/realms/impilo` | `PREVIEW_ENFORCED` |
| Desired/live realm hash (release evidence) | `9c903e22f394ec812ff412331fedcd18a3bae49a4bb8c17e6b4c70d4bb5209d8` | `PREVIEW_DEPLOYED` (cited from `docs/security/evidence/mfa-preview-release-evidence-20260801.md`) |
| Reconciler Jobs completed | `keycloak-create-reconciler-mfa`, bootstrap/admin/event-reader jobs all `COMPLETE=1` | `PREVIEW_DEPLOYED` |
| CronJob reconciler | none scheduled | `PREVIEW_DEPLOYED` (idempotent job, not continuous cron) |
| Realm ConfigMap | `keycloak-realm-import` present | `PREVIEW_DEPLOYED` |

## 4. Browser client flow / PKCE / redirects / grants

| Fact | Value | Classification |
|---|---|---|
| BFF authorize → IdP | 302 to Keycloak with `response_type=code`, `code_challenge_method=S256`, `state`, `nonce`, `redirect_uri=https://impilo.mohcc.gov.zw/internal/v1/auth/oidc/callback` | `PREVIEW_ENFORCED` (curl + Playwright) |
| PKCE S256 | present on live authorize URL | `PREVIEW_ENFORCED` |
| Implicit grant for `experience-ui` | `response_type=token` → `error=unauthorized_client` | `PREVIEW_ENFORCED` |
| ROPC for `experience-ui` | `grant_type=password` → `unauthorized_client` | `PREVIEW_ENFORCED` |
| Realm discovery still lists `password`/`implicit` | yes (realm-level catalogue); client denies them | `PARTIAL` (client-enforced, not realm-stripped) |

## 5. BFF routes, token boundary, cookies, CSRF

| Fact | Value | Classification |
|---|---|---|
| Routes | `/internal/v1/auth/oidc/{authorize,callback,session,step-up,action,logout}` | `SOURCE_IMPLEMENTED` + `PREVIEW_DEPLOYED` |
| Token storage | access/refresh/id tokens encrypted in Redis (`experience:auth:session:*`); never returned in session-status body | `SOURCE_IMPLEMENTED` + `TEST_PROVEN` |
| Cookie names | `__Host-impilo_session` (HttpOnly), `__Host-impilo_csrf` (readable) | `SOURCE_IMPLEMENTED` |
| Cookie attributes (source) | `Secure` (env `IMPILO_AUTH_WEB_SESSION_COOKIE_SECURE=true`), `SameSite=Lax`, `Path=/`, no Domain (required by `__Host-`) | `SOURCE_IMPLEMENTED` + `PREVIEW_DEPLOYED` (flag) / `INSUFFICIENT_EVIDENCE` for observed Set-Cookie from authenticated callback (login credential not usable from runner) |
| CSRF binding | `SessionCsrfFilter` requires `X-CSRF-Token` matching session for non-safe methods | `SOURCE_IMPLEMENTED` + `TEST_PROVEN` |
| Issuer / audience / nonce validation | `OidcSessionService.validateIdToken` | `SOURCE_IMPLEMENTED` + `TEST_PROVEN` |
| Callback replay | transaction `getAndDelete` → `OIDC_TRANSACTION_EXPIRED` | `SOURCE_IMPLEMENTED` + `TEST_PROVEN` |
| Anonymous session-status | HTTP 401 `NO_ACTIVE_SESSION` | `PREVIEW_ENFORCED` |
| Authenticated session-status body | not freshly captured (seeded password rejected by live Keycloak) | `INSUFFICIENT_EVIDENCE` (browser) / `TEST_PROVEN` (unit) |
| Browser storage after login | no JWT/token material after authorize hop (Playwright) | `PREVIEW_ENFORCED` (pre-auth hop); authenticated post-login storage `INSUFFICIENT_EVIDENCE` |
| Logout / invalidation | local Redis delete + Keycloak revoke best-effort; cookies cleared | `SOURCE_IMPLEMENTED` + `TEST_PROVEN`; live authenticated logout `INSUFFICIENT_EVIDENCE` |

## 6. Mobile client flow / preview build

| Fact | Value | Classification |
|---|---|---|
| Flow | authorization code + PKCE via `@impilo/mobile-auth` (`keycloakClient` + secure-storage transaction) | `SOURCE_IMPLEMENTED` + `TEST_PROVEN` (28/28 after deps) |
| Process-death restore | `consumeAuthTransaction` restores state/nonce/verifier/createdAt; replay → `CALLBACK_REPLAYED` | `TEST_PROVEN` |
| Preview callback schemes | citizen `impilo-citizen://auth/callback`; provider `impilo-provider://auth/callback` (via `Linking.createURL("auth/callback")`) | `SOURCE_IMPLEMENTED` |
| Current preview APK on 218 | release evidence cites APKs at `b8d29a653`; this checkpoint did not re-install/re-smoke on Redroid | `PARTIAL` (prior release evidence) / Redroid authenticated smoke not re-run here |
| Redroid blocker (if any) | not re-attempted this checkpoint; prior release evidence reports Maestro citizen/provider smokes succeeded | see [`BROWSER_MOBILE_RESULTS.md`](BROWSER_MOBILE_RESULTS.md) |

## 7. Workforce MFA activation flag

| Fact | Value | Classification |
|---|---|---|
| BFF `IMPILO_BOOTSTRAP_REQUIRE_MFA` | `false` | `PREVIEW_DEPLOYED` |
| Workforce MFA activation | **not activated** (explicit Checkpoint 3 non-goal) | `PREVIEW_DEPLOYED` (flag false) |
| Release evidence | all 38 workforce accounts have native MFA/recovery required actions in Keycloak, but enforcement activation remains gated | `PREVIEW_DEPLOYED` (actions) / activation `ABSENT` |

## 8. `/health/version` digest mismatch

| Fact | Value | Classification |
|---|---|---|
| Live BFF image | `sha256:1948d8d355b5a3456ed0bbdf1feb195143ff4f45b348b8dcb85b1d41b3ea763b` | `PREVIEW_DEPLOYED` |
| `/health/version` `imageDigests.experience-bff` | `sha256:57c6952eb604d8e547acf86377e9c442e599920a2da9ff1042567fde72808e53` | stale vs runtime |
| Cause | Helm-release digest map (`IMPILO_IMAGE_DIGESTS_JSON`) certified for estate commit `fe0ba72d…` at `2026-07-25T03:38:44Z`; later targeted image update set BFF to `486b3a4ff` without regenerating the map | **source defect in truthfulness of presentation**, not a wrong image |
| Source fix (this checkpoint) | `PreviewVersionController` now emits `imageDigestsAuthoritativeForThisRuntime`, `imageDigestsCertifiedForCommit`, `imageDigestsCertifiedAt` so a stale map cannot be read as current runtime truth | `SOURCE_IMPLEMENTED` + `TEST_PROVEN` (unit); **not deployed** (no deploy authorized) |

## 9. Controlled preview identity attempt

- Seeded username `citizen.moyo` was used once against the live login form.
- Live Keycloak returned **Invalid username or password**.
- No password, cookie, or token values were retained in evidence.
- Authenticated cookie-attribute / session-body / logout facets therefore remain `INSUFFICIENT_EVIDENCE` for browser runtime (unit/source proof stands).
