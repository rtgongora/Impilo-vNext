# CP3 Closure — Preview Login Rejection Diagnosis (citizen.moyo)

Date: 2026-08-01 · Branch: `claude/tshepo-trust-cp1-truth-audit` · Estate: `impilo-full-preview`

## Verdict

**Root cause: credential divergence, not an infrastructure or flow defect.**
The live password credential for `citizen.moyo` was last set on **2026-07-26 (11:44 UTC)**
in the pre-migration H2 estate and was **faithfully preserved** by the H2 → PostgreSQL /
Keycloak 26.7 migration (2026-08-01 03:13). It matches **neither** committed realm seed:

| Committed seed | Password value (not reproduced here) | Live match |
|---|---|---|
| `tools/auth/impilo-realm.json` | 14-char test value ("ImpiloTest123!" convention) | **No** — `LOGIN_ERROR invalid_user_credentials` 17:08:41 / 17:09:06 UTC |
| `deploy/helm/impilo-vnext/files/realm-impilo-preview.json` | 14-char test value ("Vashandi@2024!" convention) | **No** — `LOGIN_ERROR invalid_user_credentials` 18:37:23 UTC |

No key in the approved secret store (`impilo-app-secrets`) carries a preview
test-user password (all `keycloak-*` keys are infra/client credentials).

## Evidence chain (all read-only, via governed identities)

Read access used the least-privilege service accounts created by the MFA migration:
`impilo-realm-reconciler` (realm-management read) and `impilo-event-reader`
(`view-events`), authenticated with client credentials from `impilo-app-secrets`.
No secret value was printed; no live user or credential was modified.

1. **User state** — exists, `enabled: true`, `emailVerified: true`, `requiredActions: []`,
   no `federationLink`, identity-anchor attributes present (`actor_id`, `actor_type`,
   `cpid`, `health_id`). Realm user count 42.
2. **Credential presence** — exactly one credential of type `password`,
   `createdDate = 1785066272180` (2026-07-26 11:44:32 UTC) — pre-migration, proving the
   migration preserved the credential rather than corrupting it.
3. **Brute force** — realm `bruteForceProtected: true`, `failureFactor: 5`;
   user not locked. `numFailures` rose 2 → 3 solely from this diagnosis; credential
   probing was stopped with 2 attempts of margin remaining.
4. **Login events** (`impilo-event-reader`, `view-events`):
   - `LOGIN_ERROR error=invalid_user_credentials client=experience-ui user=citizen.moyo`
     at 17:08:41, 17:09:06 (seed #1) and 18:37:23 UTC (seed #2) — Keycloak reached the
     credential check and rejected the value itself.
   - `LOGIN_ERROR error=invalid_client_credentials` / `not_allowed` at 16:47 UTC —
     the legacy BFF ROPC probe: `experience-ui` is now a **confidential** client with
     direct-access grants disabled, so ROPC dies at the client boundary **before** any
     user-credential check (browser-ROPC removal working as designed; the legacy
     `/internal/v1/auth/login` route can no longer authenticate anyone).
5. **Client/flow config** — `experience-ui`: `publicClient: false`,
   `standardFlowEnabled: true`, `directAccessGrantsEnabled: false`,
   `implicitFlowEnabled: false`, `pkce.code.challenge.method: S256`, single exact
   redirect `https://impilo.mohcc.gov.zw/internal/v1/auth/oidc/callback`.
6. **Issuer/hostname consistency** — the authorize redirect lands on
   `https://impilo.mohcc.gov.zw/realms/impilo/...` and the login form round-trips
   normally (`login-actions/authenticate`), confirmed live via Playwright.

Not a lockout, not a missing/disabled user, not a required action, not federation or
realm mismatch, not a client/redirect/PKCE misconfiguration, not an issuer problem.

## Why the live credential diverged

Two committed realm seeds exist with different passwords for the same personas
(`tools/auth/impilo-realm.json` vs `deploy/helm/impilo-vnext/files/realm-impilo-preview.json`),
and the pre-migration H2 estate had personas reset again on 2026-07-26 by an operator
run whose password value was env-supplied (`PERSONA_PASSWORD` in
`scripts/operator/seed-persona-truth-pack.sh`) and never recorded in approved secret
storage. Keycloak only imports realm JSON into an empty database, so neither committed
seed is authoritative for a long-lived estate.

## Exact missing prerequisite

A governed preview test-user credential in approved secret storage — e.g. an
`impilo-app-secrets` key (such as `preview-persona-password`) that the persona seeding
scripts consume and the automated proof reads at runtime — **or** an operator-authorized
re-run of `scripts/operator/reconcile-keycloak-realm-users.sh` to reset seeded personas
to the committed test value. Both are out of scope for this closure run
("do not reset live passwords, do not modify live users").

## Consequence for runtime truth

- `citizen.moyo` remains unrepaired and unused (never reset; never modified).
- Authenticated browser runtime proof: **PREVIEW_ENFORCED** as of 2026-08-02 via the
  governed synthetic identity `preview.test.citizen` — see
  [`AUTHENTICATED_RUNTIME_PROOF.md`](AUTHENTICATED_RUNTIME_PROOF.md) and
  [`PREVIEW_TEST_IDENTITY.md`](PREVIEW_TEST_IDENTITY.md). Playwright 12/12 including
  Set-Cookie attributes, opaque cookie, authenticated session-status, storage
  cleanliness, CSRF enforcement, logout, post-logout rejection, callback replay
  rejection and open-redirect rejection.
- Unauthenticated runtime facets remain passing (PKCE S256 + state + nonce, anonymous
  session fail-closed, open-redirect rejection, no token material).

## Defect found and fixed during diagnosis

A replayed/expired OIDC callback raised `OidcProtocolException`, which fell into the
generic `Exception` handler and surfaced as **500 INTERNAL_ERROR** on the live path.
Rejection was fail-closed (no session minted) but misreported as a server crash.
Fixed in source: both BFF controller-advice classes now map `OidcProtocolException`
to **400** with its safe constant code (e.g. `OIDC_TRANSACTION_EXPIRED`). The deployed
preview still shows 500 until the next authorized deploy; no deploy was performed.
