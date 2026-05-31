# GitHub Actions Preview Deployment

## Pipeline (intended)

```text
commit → push → CI (ci.yml) → deploy-preview-sandbox job → VM pull/build/helm → preview-smoke-test.sh → http://41.57.127.235
```

Manual override: **Actions → Deploy Preview → Run workflow** (same remote script, any branch).

## Workflows

| File | Trigger | Purpose |
|------|---------|---------|
| `.github/workflows/ci.yml` | `push` to `main`, `develop`, `claude/**`; PRs to `main`/`develop` | Lint, test, build |
| `.github/workflows/deploy-preview.yml` | `workflow_dispatch` only | Manual redeploy to preview VM |
| `.github/workflows/deploy.yml` | `workflow_dispatch` | Staging/production GHCR + Helmfile (not preview VM) |
| `.github/workflows/deprecated-surface-guard.yml` | `push`/`pull_request` on retired UI paths | Block `ui/experience` / `ui/ehr` regressions |

### Automatic preview deploy

On push to `claude/staging-ux-orchestration-remediation-Yypyl`, after these CI jobs succeed:

- `backend-test`
- `frontend-lint`
- `frontend-test`
- `e2e-test`

the `deploy-preview-sandbox` job SSHs to the VM and runs
`scripts/deploy/github-actions-remote-preview-deploy.sh` (pull → build → images → helm → smoke).

Long-running CI jobs (trust/registry fullstack, mobile Maestro, sovereign smoke, etc.) do **not** gate preview deploy.

## Required GitHub Secrets

Configure under **GitHub → rtgongora/Impilo-vNext → Settings → Secrets and variables → Actions → Repository secrets**.

| Secret | Example / description |
|--------|------------------------|
| `PREVIEW_HOST` | `41.57.127.235` |
| `PREVIEW_PORT` | `2276` |
| `PREVIEW_USER` | `robert` |
| `PREVIEW_SSH_KEY` | Private key for `robert@41.57.127.235` (PEM). **Never commit.** |

Optional later: `GHCR_TOKEN` if preview images are pulled from GHCR instead of built on-VM.

### Verify secrets are configured

From a machine with GitHub CLI and repo access:

```bash
gh secret list -R rtgongora/Impilo-vNext
```

Expect `PREVIEW_HOST`, `PREVIEW_PORT`, `PREVIEW_USER`, `PREVIEW_SSH_KEY`. Values are not readable after set; a failed deploy job with "secret not found" or SSH auth errors means a secret is missing or wrong.

## VM-side deploy (same as Actions)

```bash
cd /opt/impilo/repos/Impilo-vNext
export DEPLOY_BRANCH=claude/staging-ux-orchestration-remediation-Yypyl
bash scripts/deploy/github-actions-remote-preview-deploy.sh
```

## Post-deploy verification

```bash
curl -s http://41.57.127.235/health/version | jq .
bash scripts/deploy/preview-smoke-test.sh
kubectl get pods -n impilo-preview
```

`/health/version` returns JSON: `branch`, `commit`, `buildDate`, `environment` (from BFF `IMPILO_GIT_*` set by Helm in `preview-deploy.sh`).

Helm also sets `NEXT_PUBLIC_GIT_*` on `one-ui-shell`; the shell does not yet render a build badge in the UI—use the version endpoint for proof.

**Do not commit SSH keys, passwords, or tokens to the repository.**
