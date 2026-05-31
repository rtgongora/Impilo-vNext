# Human-Authorized Comprehensive Pipeline Report

**Date:** 2026-05-30  
**Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`  
**Workspace:** `/opt/impilo/repos/Impilo-vNext` (VM; `whoami` may be `root` in agent shell)  
**Verdict:** **Comprehensive in design, partially implemented** — blocking gates and manual deploy workflow are in place; GitHub CI reliability and full service coverage remain gaps.

## 1. Overall verdict

The repository now implements the intended **human-authorized** pipeline:

- Push → CI (no auto preview deploy)
- Cursor collects CI feedback → user decides → manual deploy → smoke → `/health/version`

Critical gaps: GitHub Actions may still fail for infrastructure reasons; not all 60+ services run in every CI job; mobile/iOS remain advisory.

## 2. Evidence summary

| Area | Evidence |
|------|----------|
| No auto-deploy | `ci.yml` has no `deploy-preview-sandbox`; grep confirms only `workflow_dispatch` in `deploy-preview.yml` |
| CI gates | Jobs: `backend-test`, `frontend-*`, `e2e-test`, `change-safety-gates`, `preview-pipeline-gates` |
| Test scripts | `scripts/test/run-preview-gates.sh` + 8 sub-scripts |
| Guards | `scripts/guard/run-change-safety-gates.sh` + 8 checks |
| Regression | `tests/regression/preview-http-regression.sh` |
| CI feedback | `scripts/ci/collect-ci-feedback.sh` |
| Manual deploy | `scripts/deploy/manual-authorized-preview-deploy.sh` |
| Inventories | `docs/architecture/*_INVENTORY.md` via `sync-pipeline-inventories.sh` |
| Docs | `docs/environment/PREVIEW_PIPELINE.md`, `PREVIEW_TEST_GATES.md`, etc. |
| Cursor rules | `.cursor/rules/ci-feedback-and-manual-deploy.mdc` |

## 3. What makes it comprehensive

See [COMPREHENSIVE_PIPELINE_CRITERIA.md](./COMPREHENSIVE_PIPELINE_CRITERIA.md) — 13 categories from static checks through human feedback loop.

## 4. GitHub Actions workflows and triggers

| Workflow | Trigger |
|----------|---------|
| `ci.yml` | push `main`, `develop`, `claude/**`; PRs to `main`/`develop` |
| `deploy-preview.yml` | **manual** `workflow_dispatch` |
| `deploy.yml` | manual (non-preview) |
| `deprecated-surface-guard.yml` | path-filtered push/PR |

## 5–6. Blocking vs advisory gates

Documented in [PREVIEW_TEST_GATES.md](./PREVIEW_TEST_GATES.md).

## 7. Regression tests

HTTP baseline + existing Playwright/route tests in `one-ui-shell`. Strategy: [REGRESSION_TEST_STRATEGY.md](./REGRESSION_TEST_STRATEGY.md).

## 8. Change-safety guards

`scripts/guard/*` — deletions, duplicates, inventories, deprecated surfaces.

## 9. Mobile coverage

Two Expo apps + packages; advisory gate. See [MOBILE_APP_AUDIT.md](./MOBILE_APP_AUDIT.md).

## 10–13. Manual authorization, CI feedback, deploy, smoke

[HUMAN_AUTHORIZED_PREVIEW_DEPLOYMENT.md](./HUMAN_AUTHORIZED_PREVIEW_DEPLOYMENT.md)

## 14. `/health/version`

Verified in `manual-authorized-preview-deploy.sh` after `preview-smoke-test.sh`.

## 15. Gap matrix

[PIPELINE_GAP_MATRIX.md](./PIPELINE_GAP_MATRIX.md)

## 16. Commands run (verification)

```bash
pwd / hostname / git status / branch / HEAD
bash scripts/guard/check-deprecated-surfaces.sh  # PASS
bash scripts/test/run-security-checks.sh       # PASS (after self-match fix)
bash scripts/guard/run-change-safety-gates.sh    # PASS
bash scripts/ci/collect-ci-feedback.sh           # deploy_blocked (CI unknown without gh auth)
```

## 17–18. Tests passed / failed

- **Passed locally:** deprecated surface, security gates, change-safety (with grep fallback).
- **Not run end-to-end:** full `run-preview-gates.sh` (long); push/CI cycle (no user-approved push in this session).
- **Known CI:** remote runs may fail; preview may lag until manual deploy.

## 19. Known gaps

- `gh auth login` on VM for rich CI logs
- Full backend matrix for all services
- API response-shape diffing
- iOS build, visual/load/a11y tests
- GitHub Actions runner reliability

## 20. Immediate next actions

1. **Commit and push** this pipeline implementation (user approval required).
2. Run `gh auth login` on VM.
3. After push, `bash scripts/ci/collect-ci-feedback.sh` and review Actions.
4. When CI is green and user approves: `bash scripts/deploy/manual-authorized-preview-deploy.sh`.
5. Install `ripgrep` on VM optional (`guard_filter` uses grep fallback).

## Commands (quick reference)

```bash
# Full blocking gates (VM)
bash scripts/test/run-preview-gates.sh

# CI feedback after push
bash scripts/ci/collect-ci-feedback.sh

# Manual deploy after user says AUTHORIZE DEPLOY
bash scripts/deploy/manual-authorized-preview-deploy.sh
```
