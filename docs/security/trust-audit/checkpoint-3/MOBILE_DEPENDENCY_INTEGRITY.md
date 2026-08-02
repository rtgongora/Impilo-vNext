# Checkpoint 3 — Mobile Dependency Integrity (`jose`) and Redroid Disposition

Date: 2026-08-01 · Branch: `claude/tshepo-trust-cp1-truth-audit`

## Why `jose` was absent

Before commit `537b7fe00` (`feat(mobile): persist and verify PKCE transactions`,
2026-07-31), `jose@6.2.3` existed in `apps/mobile/pnpm-lock.yaml` **only as a
transitive dependency** of `@livekit/components-react` (via `livekit-client`). It was
therefore hoisted into `node_modules/.pnpm` without `@impilo/mobile-auth` declaring it:
any import would resolve on a machine with a full install but was an undeclared
(phantom) dependency — invisible to the manifest, fragile under pnpm's strict linking,
and silently pinned to whatever version LiveKit chose.

`537b7fe00` introduced the `jose` import (`createRemoteJWKSet`, `jwtVerify`) for
JWKS-backed ID-token verification and, in the same commit, declared it properly.

## Current declaration state (verified 2026-08-01)

| Check | Result |
|---|---|
| Manifest | `apps/mobile/packages/mobile-auth/package.json` → `"jose": "6.2.3"` (exact pin) |
| Lockfile importer entry | `pnpm-lock.yaml` `packages/mobile-auth` → `jose 6.2.3` |
| Version drift | None — exactly one `jose` version (`6.2.3`) in the entire lockfile; the direct pin equals the LiveKit transitive resolution |
| `pnpm install --frozen-lockfile` | Succeeds (lockfile consistent with manifests) |
| Usage | `src/keycloakClient.ts` — `jwtVerify` with `issuer`, `audience`, `requiredClaims: [sub, iat, exp, nonce]` and explicit nonce equality check |

## Security / licence checks

The repository has **no repo-native npm dependency-audit or licence gate**
(`scripts/pipeline/run-local-quality-gates.sh` contains none); the standard package
manager audit was run instead.

- `pnpm audit --prod` (workspace root): **40 findings** (2 critical, 24 high, 11
  moderate, 3 low) — **all in the pre-existing Expo/React Native toolchain**
  (`tar`, `shell-quote`, `postcss`, `ws`, `undici`, `@babel/*`, …). None are new in
  this checkpoint and none are reachable through the auth path.
- `jose` specifically: **0 advisories**, licence **MIT**.
- Remediating the Expo toolchain advisories is an SDK-upgrade programme, out of
  Checkpoint 3 scope; recorded here as a known pre-existing condition.

## Mobile auth test evidence

`cd apps/mobile/packages/mobile-auth && pnpm test` → **5 files, 28/28 pass**, including:

- `authTransaction.test.ts` — process-death restoration from secure storage with
  **exactly-once consumption** (replay rejection), forged-`state` non-consumption,
  expired-transaction rejection and removal;
- `keycloak.test.ts` — PKCE S256 challenge derivation and authorization-URL
  parameters (`state`, `nonce`), logout URL, actor-ID claim mapping.

Issuer/audience/nonce/expiry enforcement is structural in `keycloakClient.ts`
(`jwtVerify` options), not test-only.

## Authenticated Redroid smoke — PROVEN (2026-08-02)

Product Owner authorized a dedicated synthetic preview CITIZEN identity. Credential
stored in `impilo-full-preview/impilo-preview-test-identity` (see
[`PREVIEW_TEST_IDENTITY.md`](PREVIEW_TEST_IDENTITY.md)). Authenticated proof:

```bash
bash scripts/mobile/redroid-authenticated-proof.sh
```

**OVERALL PASS** — mobile-auth unit suite 31/31; Maestro IdP login on redroid
(`Continue securely`); Keycloak `LOGIN` + `CODE_TO_TOKEN` for
`impilo-mobile-citizen` / `preview.test.citizen` with redirect
`impilo-citizen://auth/callback`. Full record:
[`AUTHENTICATED_RUNTIME_PROOF.md`](AUTHENTICATED_RUNTIME_PROOF.md).
