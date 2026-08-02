# Checkpoint 3 — Authenticated Runtime Proof (closure)

Date: 2026-08-02 · Branch: `claude/tshepo-trust-cp1-truth-audit`  
Identity: governed synthetic `preview.test.citizen` (see [`PREVIEW_TEST_IDENTITY.md`](PREVIEW_TEST_IDENTITY.md))  
Credential source: Kubernetes Secret `impilo-full-preview/impilo-preview-test-identity` (never printed, never committed)

## Updated truth classifications

| Capability | Prior (bc9d2a4ba) | Now |
|---|---|---|
| Authenticated browser BFF session | INSUFFICIENT_EVIDENCE (no credential) | **PREVIEW_ENFORCED** (Playwright 12/12) |
| Authenticated Redroid / mobile PKCE | INSUFFICIENT_EVIDENCE | **PREVIEW_ENFORCED** at IdP + code exchange; journey home optional (no clinical person by design) |
| Rollback retention governance | OUTSTANDING / indefinite | **ADOPTED** (activation + 30 stable days; dual-owner deletion) |
| Constrained recovery allowlist | Source-proven | Unchanged (still enforced in source/tests) |
| Reconciler safety unit suite | 39/39 | Unchanged |

## Playwright authenticated preview (`playwright.preview.config.ts`)

Command (credential loaded from Secret into env; values not logged):

```bash
export PREVIEW_TEST_USERNAME="$(kubectl get secret -n impilo-full-preview impilo-preview-test-identity -o jsonpath='{.data.username}' | base64 -d)"
export PREVIEW_TEST_PASSWORD="$(kubectl get secret -n impilo-full-preview impilo-preview-test-identity -o jsonpath='{.data.password}' | base64 -d)"
cd ui/one-ui-shell && npx playwright test --config playwright.preview.config.ts
```

**Result: 12 passed (0 failed, 0 skipped)** on 2026-08-02.

| # | Proof | Result |
|---|---|---|
| 1 | PKCE login → BFF callback (302, `state`+`code`) | PASS |
| 2 | Set-Cookie `__Host-impilo_session` Secure/HttpOnly/Path=/ / SameSite / no Domain | PASS |
| 3 | Session cookie opaque (not JWT) | PASS |
| 4 | Authenticated session-status principal, no token material | PASS |
| 5 | No tokens in localStorage/sessionStorage/document.cookie | PASS |
| 6 | CSRF enforcement (missing/wrong `X-CSRF-Token` → 403 `CSRF_REJECTED`) | PASS |
| 7/8 | Logout with CSRF → 200; post-logout session-status 401 `NO_ACTIVE_SESSION` | PASS |
| 9 | Callback replay does not re-establish session | PASS |
| — | Open-redirect `returnTo` rejected (security suite) | PASS |
| — | Authorize carries PKCE S256 + state + nonce | PASS |

Keycloak events corroborate: `LOGIN` + `CODE_TO_TOKEN` for `experience-ui` with username `preview.test.citizen`.

## Authenticated Redroid proof

Command: `bash scripts/mobile/redroid-authenticated-proof.sh`

**Result: OVERALL PASS** on 2026-08-02 (device `127.0.0.1:15555`, package `zw.gov.impilo.citizen.dev`).

| Concern | Evidence |
|---|---|
| Preview callback scheme | Authorize URL used `redirect_uri=impilo-citizen://auth/callback` with PKCE S256 |
| Issuer / audience / state / nonce | Unit: `validateIdToken` + authorize URL builders (31/31 mobile-auth tests); runtime authorize URL carried `state` + `code_challenge_method=S256` |
| Process-death restoration | Unit: `authTransaction.test.ts` restores from secure storage |
| Exactly-once callback / replay rejection | Unit: `CALLBACK_REPLAYED` / `STATE_MISMATCH` |
| Secure token storage | Unit: `TokenManager` persists only via SecureStorage adapter |
| Authenticated session | Maestro completed Impilo-themed Keycloak login (`Continue securely`); Keycloak `LOGIN` + `CODE_TO_TOKEN` for `impilo-mobile-citizen` / `preview.test.citizen` with no error |
| Logout | Unit: `clearTokens` removes access/refresh from secure storage |
| Journey continuation | Not asserted on redroid: synthetic identity has **no clinical person record by design**; tabs/home may be absent after auth. IdP + code exchange is the governed auth proof. |

Maestro hierarchy/screenshots are scrubbed after each run (password fields appear as EditText text in accessibility trees).

## Remaining blockers (exact)

1. **Journey home on redroid** — not claimed; synthetic identity intentionally has no VITO/clinical person. Separate from auth-session proof.
2. **No deploy / merge / enforcement activation** — still held (this closure does not deploy).
3. **Retention clock** — policy adopted; 30-day window starts only at final trust activation (not started).
