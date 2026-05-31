# GitHub Actions Preview Deployment

## Pipeline (current)

```text
commit → push → CI (ci.yml) — NO automatic preview deploy
  → user authorizes → Deploy Preview workflow OR manual-authorized-preview-deploy.sh
  → VM pull/build/helm → preview-smoke-test.sh → /health/version
```

## Workflows

| File | Trigger | Purpose |
|------|---------|---------|
| `.github/workflows/ci.yml` | `push` to `main`, `develop`, `claude/**`; PRs to `main`/`develop` | Lint, test, change-safety, preview-pipeline-gates |
| `.github/workflows/deploy-preview.yml` | **`workflow_dispatch` only** | Manual authorized deploy to preview VM |
| `.github/workflows/deploy.yml` | `workflow_dispatch` | Staging/production GHCR + Helmfile (not preview VM) |
| `.github/workflows/deprecated-surface-guard.yml` | push/PR on retired UI paths | Block `ui/experience` / `ui/ehr` regressions |

### Automatic preview deploy

**Disabled.** The `deploy-preview-sandbox` job was removed from `ci.yml`. Preview updates only after explicit user authorization.

### CI jobs that gate quality (not deploy)

On push to `claude/staging-ux-orchestration-remediation-Yypyl`, `preview-pipeline-gates` runs
`scripts/test/run-preview-gates.sh` after `backend-test`, `frontend-lint`, `frontend-test`,
and `change-safety-gates`.

## Required GitHub Secrets (manual deploy workflow)

| Secret | Description |
|--------|-------------|
| `PREVIEW_HOST` | `41.57.127.235` |
| `PREVIEW_PORT` | SSH port (e.g. `2276`) |
| `PREVIEW_USER` | `robert` |
| `PREVIEW_SSH_KEY` | Private key for VM deploy |

## VM manual deploy

```bash
bash scripts/ci/collect-ci-feedback.sh
bash scripts/deploy/manual-authorized-preview-deploy.sh
```

See [HUMAN_AUTHORIZED_PREVIEW_DEPLOYMENT.md](./HUMAN_AUTHORIZED_PREVIEW_DEPLOYMENT.md).
