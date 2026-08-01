# Checkpoint 3 — MFA migration readiness, authenticated runtime truth, constrained recovery

Branch: `claude/tshepo-trust-cp1-truth-audit`
Hygiene commit: `227c4a764` (`chore(gitignore): narrow contract-bundle ignore to exact generated path`)

## Scope

1. Exact MFA runtime truth (source + preview).
2. Keycloak migration/restore evidence status (no recreation of missing proof).
3. Constrained recovery semantics (source correction; not activated workforce MFA).
4. Browser and mobile security verification.
5. Workforce MFA activation readiness matrix (readiness only).

## Explicit non-goals

- No merge.
- No preview/full-boot deploy.
- No workforce MFA activation.
- No OAuth / Envoy / OPA enforcement expansion.
- No live credential rotation and no live user modification.

## Artifacts in this directory

| File | Purpose |
|---|---|
| [`MFA_RUNTIME_TRUTH.md`](MFA_RUNTIME_TRUTH.md) | Deployed Keycloak/PG/BFF/mobile facet classifications |
| [`MIGRATION_EVIDENCE_STATUS.md`](MIGRATION_EVIDENCE_STATUS.md) | H2→PG→26.7 evidence matrix |
| [`CONSTRAINED_RECOVERY.md`](CONSTRAINED_RECOVERY.md) | Recovery semantics, allowed/denied routes, audit |
| [`BROWSER_MOBILE_RESULTS.md`](BROWSER_MOBILE_RESULTS.md) | Playwright + mobile-auth + Redroid status |
| [`WORKFORCE_MFA_READINESS.md`](WORKFORCE_MFA_READINESS.md) | Activation readiness matrix (not activated) |

## Status marker

See the final report at the end of this checkpoint execution.
