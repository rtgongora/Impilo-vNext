# MFA foundation truth — split facets (Checkpoint 1 closure)

**Prior overstatement corrected:**  
“BFF/mobile MFA foundation ENFORCED; workforce enforcement not activated.”

That sentence collapsed independent truths. Split below. Layers: SOURCE / TEST / PREVIEW_DEPLOYED / PREVIEW_ENFORCED.

## Facet matrix

| Facet | SOURCE | TEST | PREVIEW_DEPLOYED | PREVIEW_ENFORCED | Evidence |
|---|---|---|---|---|---|
| Browser auth-code + PKCE via BFF | YES | PARTIAL | YES | YES | `OidcSessionService`, `OidcSessionController`; live `IMPILO_AUTH_WEB_SESSION_ENABLED=true` on `experience-bff@sha256:1948d8d3…` (commit `486b3a4f…`, branch `codex/mfa-production`) |
| Encrypted Redis session + `__Host-impilo_session` + CSRF | YES | PARTIAL | YES | YES | `WebAuthSessionStore`, `SessionCsrfFilter`; `IMPILO_AUTH_WEB_SESSION_COOKIE_SECURE=true` |
| Legacy browser ROPC | YES (residue) | denyAll tested | YES (code) | NO | Legacy controller denyAll in `SecurityConfig` |
| Mobile PKCE + SecureStore | YES | PARTIAL | UNKNOWN | UNKNOWN | `apps/mobile/packages/mobile-auth`; no Redroid proof in this closure window |
| Mobile residual password grant | YES | NO | UNKNOWN | NO | Sign-up residual |
| Keycloak version | YES (26.7 image build) | N/A | YES | N/A (infra) | Live pod: **Keycloak 26.7.0** (`/opt/keycloak/version.txt`); image `keycloak@sha256:70f0af3d…` commit `304152be…` |
| Keycloak database | YES (postgres config) | N/A | YES | N/A (infra) | Live env: `KC_DB=postgres`, `KC_DB_URL_HOST=postgres`, `KC_DB_URL_DATABASE=keycloak` — **PostgreSQL is deployed** |
| Enrollment (TOTP/passkey/recovery required actions) | PARTIAL | PARTIAL | PARTIAL | UNKNOWN | MFA release evidence claims workforce required actions; not re-proven as PREVIEW_ENFORCED here |
| Token AAL (ACR → AuthenticationAssurance) | YES | PARTIAL | YES | PARTIAL | Mapping in `KeycloakAdapter`; only when token reaches tshepo-authz (off ingress path) |
| Recovery-code constrained state | NO (defect) | NO | N/A | NO | Grants ordinary AAL2 in source interpretation — `RECOVERY_CODE_PROOF.md` |
| Workforce MFA enforcement activation | PARTIAL | NO | PARTIAL | NO | `IMPILO_BOOTSTRAP_REQUIRE_MFA=false` on BFF; programme gate not opened |
| New BFF session flow deployed | YES | PARTIAL | YES | YES | Explicitly **is** deployed in preview (`IMPILO_AUTH_WEB_SESSION_ENABLED=true`) |

## Explicit corrections

1. **Keycloak 26.7 / PostgreSQL IS deployed** in `impilo-full-preview` as of this closure capture. Do not claim undeployed.
2. **New BFF session flow IS deployed and enabled** in preview. Do not claim undeployed.
3. **Mobile** remains SOURCE_IMPLEMENTED / TEST_PARTIAL; **PREVIEW_ENFORCED is UNKNOWN** without a Redroid/Maestro capture in this window.
4. **Workforce enforcement is not activated** — still correct.
5. Preview digests map to `codex/mfa-production` builds, not to commits on `claude/tshepo-trust-cp1-truth-audit`.

## Safe summary statement (replacement)

> Browser OIDC session foundation is PREVIEW_ENFORCED on experience-bff against Keycloak 26.7/PostgreSQL. Mobile PKCE is SOURCE_IMPLEMENTED but not PREVIEW_ENFORCED in this evidence pack. Recovery-code policy remains a SOURCE_CONFIRMED defect (ordinary AAL2). Workforce MFA enforcement is not activated.
