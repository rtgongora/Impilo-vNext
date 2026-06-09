# UI Surfacing Maturity Scorecard

> Honest product maturity — not gate pass/fail. Updated: 2026-06-05.

## Executive summary

| Dimension | Score | Notes |
|-----------|-------|-------|
| P0 thin-shell routes | **Live** (cleared) | Last P0 `/marketplace/orders/:id` upgraded to product tables |
| P1 operator routes (all domains) | **Live** | 30 former P1 routes cleared — 0 QRP hotspots in register |
| Registration golden path | **Live (web)** | Readiness probe, assurance BFF wire, Playwright E2E |
| SUPER_ADMIN visibility | **Live** | Role plumbing; per-route maturity still Partial for non-finance P1 |
| Full-boot preview runtime | **Wave-0 default** | Promote via `promote-preview-wave.sh`; wave 8 adds MusheX sandbox |
| GitHub CI | **Infra fallback** | Billing lock on hosted runners; VM gates + `vm-local-gates.yml` self-hosted |
| Mobile citizen onboarding | **Partial+** | SignUp + Assurance screens wired to BFF; browser proof still web-first |
| MusheX live money | **Sandbox-only (by design)** | `MusheXRailSafetyPanel` + preview `MUSHEX_SANDBOX_ENABLED` |

## Batch 4 fixes (this pass)

| Issue | Fix |
|-------|-----|
| P0 `/marketplace/orders/:id` | Tracking + action + order summary tables |
| P1 finance (top 5) | JsonApiDataTable on settlements, refunds, ledger, costa, mushex-platform |
| Mobile parity | `SignUpScreen`, `AssuranceChoiceScreen`, `citizenRegistrationService.ts` |
| Full vNext wave-0 | `promote-preview-wave.sh`, wave-aware k3s image verify, wave 8 MusheX pilot |
| GitHub CI infra | `.github/workflows/vm-local-gates.yml` + `install-vm-self-hosted-runner.sh` |
| MusheX safety | Operator panel on payer-ops; sandbox env in preview values generator |

## Registration chain proof

```
/auth/register
  → GET /internal/v1/auth/register/readiness
  → POST /internal/v1/auth/register
  → /auth/register/assurance
  → POST /internal/v1/identity/assurance/upgrade/request
  → /consent → /auth/register/status → /home
```

Mobile mirror: `SignUpScreen` → `AssuranceChoiceScreen` → main app (same BFF endpoints).

E2E: `ui/one-ui-shell/e2e/citizen-signup-flow.spec.ts`

## Preview runtime honesty

- Public IP serves `impilo-full-preview` (default wave ≤ 0: 13 required microservices, 22 pods).
- Wave promotion: `bash scripts/operator/promote-preview-wave.sh 8` for MusheX sandbox pilot.
- Rollback slice `impilo-preview` (4 pods) preserved.
- GitHub hosted CI may be infra-blocked; VM gates remain canonical until self-hosted runner is registered.

## What remains Partial/Blocked

- Per-route domain richness (typed columns vs generic) — iterate where operators need more fields
- Full 87-service matrix rows — majority Partial until higher waves deploy
- MusheX **live** rails — credentials + step-up auth (not sandbox simulation)
- Core-transaction checklist — missing entries for llm-orchestration, ndila, nhume services
