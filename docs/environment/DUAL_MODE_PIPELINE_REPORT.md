# Dual-mode pipeline implementation report

**Date:** 2026-05-31  
**Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`

## Why GitHub Actions alone is insufficient

Run [26703129031](https://github.com/rtgongora/Impilo-vNext/actions/runs/26703129031) failed with **22 jobs, 0 steps each** (~2s) due to **GitHub billing/runner lock**. No application tests executed. Treating that as code failure or success would both be wrong.

## VM local pipeline

**Master command:** `bash scripts/pipeline/run-local-quality-gates.sh`

Phases: workspace → tools → security → static → frontend → backend → backend/frontend parity → API contracts → integration → regression → change-safety → mobile (advisory) → E2E (advisory).

Writes machine-readable and Markdown reports under `reports/pipeline/`.

## GitHub Actions alignment

- `ci.yml` header documents canonical script path.
- `frontend-lint` job calls `run-local-quality-gates.sh` with `PIPELINE_ONLY=security,static,frontend,parity`.
- `preview-pipeline-gates` calls full script with frontend/static skipped (covered by other jobs).
- `run-preview-gates.sh` delegates to the same master script.

## CI infrastructure detection

`scripts/ci/collect-ci-feedback.sh` sets:

- `ci_infra_failure: yes` when all jobs have 0 steps
- `code_test_result: unknown` for infra failures
- `fallback_available` + recommends VM local pipeline
- `deploy_recommended: conditional` when infra fails but VM report passed for HEAD

## Cursor feedback

`scripts/pipeline/cursor-local-feedback.sh` merges local report + CI feedback + decision options.

## Deploy authorization

`manual-authorized-preview-deploy.sh` accepts:

| Evidence | Prompt |
|----------|--------|
| GitHub CI pass | `AUTHORIZE DEPLOY` |
| Infra failure + VM gates pass | `AUTHORIZE DEPLOY WITH VM GATES` |
| Override | `BYPASS_CI=1` + `AUTHORIZE DEPLOY` |

## Backend–frontend parity

| Item | Status |
|------|--------|
| `check-backend-frontend-parity.sh` | Implemented (blocking) |
| `check-frontend-mocks-and-stubs.sh` | Implemented (`test:no-stubs`) |
| `check-api-client-surfacing.sh` | Implemented (warn on orphan hooks) |
| Docs | `BACKEND_FRONTEND_PARITY_GATE.md`, architecture inventories |

## Mocks/stubs

Blocked in production paths via `npm run test:no-stubs` (GAP_CLOSURE_RULES). Not weakened.

## Commands run (verification)

| Command | Result |
|---------|--------|
| `run-security-checks.sh` | PASS |
| `run-change-safety-gates.sh` | PASS |
| `run-static-checks.sh` | PASS |
| `run-frontend-checks.sh` | PASS |
| `run-backend-checks.sh` | PASS |
| `check-backend-frontend-parity.sh` | (run at verify time) |
| Full `run-local-quality-gates.sh` | Long — run before deploy |

## Remaining gaps

- GitHub billing must be fixed for remote CI parity
- `gh` not installed on VM — `sudo apt install -y gh && gh auth login`
- Full E2E/mobile remain advisory
- Not all 60+ backend services run in local backend gate (core subset)

## Recommended next steps

1. Run `bash scripts/pipeline/run-local-quality-gates.sh` before each push.
2. Fix GitHub Actions billing; re-run workflow.
3. Install `gh` on VM for richer CI logs.
4. Do not deploy preview until user authorizes after reviewing `cursor-local-feedback.sh`.

## Preview deploy recommendation

**Remain blocked** until user explicitly authorizes after fresh VM local pipeline report for current HEAD (and GitHub CI pass or documented VM-gates path).
