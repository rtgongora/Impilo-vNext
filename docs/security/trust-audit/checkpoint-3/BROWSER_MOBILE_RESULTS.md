# Checkpoint 3 — Browser / mobile security results

## Browser (Playwright against live preview)

Config: `ui/one-ui-shell/playwright.preview.config.ts`  
Spec: `ui/one-ui-shell/e2e/browser-preview-security.spec.ts`  
Resolver: Chromium maps `impilo.mohcc.gov.zw` → Traefik `10.50.1.67`.

```text
npx playwright test --config=playwright.preview.config.ts
  4 passed (1.6s)
```

| Case | Result |
|---|---|
| Auth code + PKCE S256 + state + nonce on authorize | PASS |
| Anonymous session-status `NO_ACTIVE_SESSION` | PASS |
| Open-redirect rejection (attacker host never navigated) | PASS |
| No token material in local/session storage or JS cookies after authorize | PASS |
| Authenticated session cookie attributes / body / logout revocation | **INSUFFICIENT_EVIDENCE** (live seeded password rejected; no deploy-secret login available to runner) |
| Recovery-session route restrictions (browser) | Covered by BFF unit tests (`RecoverySessionFilterTest` 4/4); live browser recovery login not available |
| Recovery continuation single use | Covered by BFF unit tests (`OidcRecoverySessionTest`) |

## Mobile (`@impilo/mobile-auth`)

```text
pnpm test  (apps/mobile/packages/mobile-auth)
  Test Files  5 passed (5)
  Tests       28 passed (28)
```

| Case | Result |
|---|---|
| Auth URL builds with S256 + state + nonce | PASS (`keycloak.test.ts`) |
| Process-death restore of state/nonce/verifier/createdAt; single consume | PASS (`authTransaction.test.ts`) |
| Callback replay → `CALLBACK_REPLAYED` | PASS |
| Forged state → `STATE_MISMATCH` (transaction retained) | PASS |
| Expired transaction → `TRANSACTION_EXPIRED` | PASS |
| Preview callback schemes | `impilo-citizen://auth/callback`, `impilo-provider://auth/callback` (source) |

## Redroid / Maestro

- This checkpoint did **not** re-run authenticated Redroid on `41.57.127.218`.
- Prior release evidence (`docs/security/evidence/mfa-preview-release-evidence-20260801.md`) reports APK install + Maestro citizen/provider smokes at commit `b8d29a653`.
- Exact blocker for a fresh authenticated Redroid run in this session: **not attempted** (out of scope to deploy/sign new APKs; no live user modification). Treat current Redroid authenticated proof as **PRIOR_RELEASE_EVIDENCE / PARTIAL** for Checkpoint 3 freshness.
