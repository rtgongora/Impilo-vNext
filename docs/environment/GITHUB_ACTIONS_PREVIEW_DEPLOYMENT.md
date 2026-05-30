# GitHub Actions Preview Deployment

## Workflows

| File | Purpose |
|------|---------|
| `.github/workflows/ci.yml` | Lint, test, build on push/PR |
| `.github/workflows/deploy-preview.yml` | Manual deploy to preview VM |

## Required GitHub Secrets

| Secret | Description |
|--------|-------------|
| `PREVIEW_HOST` | `41.57.127.235` |
| `PREVIEW_PORT` | `2276` |
| `PREVIEW_USER` | `robert` |
| `PREVIEW_SSH_KEY` | Private key for SSH deploy (no passwords in repo) |

Optional: `GHCR_TOKEN` if pushing images to GHCR later.

## Manual Deploy

GitHub → Actions → **Deploy Preview** → Run workflow → select branch.

## Post-Deploy Verification

SSH to VM and run:

```bash
cd /opt/impilo/repos/Impilo-vNext
bash scripts/deploy/preview-smoke-test.sh
```

**Do not commit SSH keys, passwords, or tokens to the repository.**
