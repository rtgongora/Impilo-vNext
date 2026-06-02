# Dev Preview Operations

Single-node **k3s** preview on VM `41.57.127.235`, namespace `impilo-preview`, public URL **http://41.57.127.235**.

Test data only — not production or formal staging.

## Current deployment snapshot (2026-05-31)

| Field | Value |
|-------|--------|
| Preview URL | http://41.57.127.235 |
| Deployed commit | `5a58424d8c2621abbc589ca70e8f5f61c87527f2` (`5a58424d`) |
| Intended branch | `claude/staging-ux-orchestration-remediation-Yypyl` |
| Environment | `preview` |
| Authorization | VM local quality gates + manual `AUTHORIZE DEPLOY WITH VM GATES` |
| Helm revision | `3` (deployed ~2026-05-31 18:00 +0200) |
| Post-deploy smoke | **PASS** (root, BFF health, `/health/version`, pods) |
| Pods | `experience-bff`, `one-ui-shell`, `postgres`, `redis` — all Running |

**Note:** `/health/version` **commit** matches `5a58424d`. The **branch** field was empty on that rollout (detached HEAD during Helm — fixed in repo scripts; see [DEPLOYMENT_SUDO_AND_IMAGE_IMPORT_HARDENING.md](./DEPLOYMENT_SUDO_AND_IMAGE_IMPORT_HARDENING.md)). A small Helm metadata upgrade (no image rebuild) can refresh branch on live preview — **requires explicit approval**.

## Safe manual deploy (authorized)

Prerequisites:

1. VM on branch `claude/staging-ux-orchestration-remediation-Yypyl` at intended SHA.
2. `bash scripts/pipeline/run-local-quality-gates.sh` — **PASS**.
3. Explicit user authorization (not auto-deploy on push).

### Path A — Full authorized deploy

```bash
cd /opt/impilo/repos/Impilo-vNext
git pull origin claude/staging-ux-orchestration-remediation-Yypyl

export DEPLOY_BRANCH="claude/staging-ux-orchestration-remediation-Yypyl"
export DEPLOY_COMMIT_SHA="$(git rev-parse HEAD)"

bash scripts/deploy/manual-authorized-preview-deploy.sh
```

When prompted for VM gates (GitHub CI infra-blocked):

```text
AUTHORIZE DEPLOY WITH VM GATES
```

### Path B — Images already built (skip Docker rebuild)

```bash
cd /opt/impilo/repos/Impilo-vNext
export DEPLOY_BRANCH="claude/staging-ux-orchestration-remediation-Yypyl"
export DEPLOY_COMMIT_SHA="$(git rev-parse HEAD)"

# Interactive sudo in VM terminal, or install limited NOPASSWD helper (see hardening doc)
bash scripts/dev/import-images-k3s.sh
```

Then run post-deploy checks (below). Use Path A if you need the full CI/VM gate wrapper and smoke + `/health/version` commit/branch checks.

### Post-deploy verification (required)

```bash
curl -s http://41.57.127.235/health/version
kubectl get pods -n impilo-preview
bash scripts/deploy/preview-smoke-test.sh
```

Expect `/health/version`:

- `environment`: `preview`
- `commit`: full SHA of deployed commit
- `branch`: `claude/staging-ux-orchestration-remediation-Yypyl` (after metadata fix is deployed)
- `buildDate`: UTC timestamp from Helm deploy
- `status`: `ok`

### Sudo / image import

Cursor agent shells cannot use interactive `sudo`. For **full boot** (22 images):

1. **Preferred:** one-time `sudo bash scripts/operator/install-k3s-image-helper.sh` — then Cursor runs `bash scripts/operator/fullboot.sh deploy` / `continue` without passwords.
2. **Fallback:** human sudo checkpoint — Cursor completes non-sudo work, writes `reports/full-boot/sudo-checkpoint.*`, product owner runs **one** SSH block + `sudo-checkpoint-run`, then tells Cursor `sudo checkpoint completed`; Cursor runs `bash scripts/operator/fullboot.sh continue`.

```bash
bash scripts/operator/fullboot.sh status
bash scripts/operator/fullboot.sh verify-images
bash scripts/operator/fullboot.sh sudo-checkpoint-status
```

Do **not** ask the product owner to manually orchestrate kubectl, helm, tmux, or full import loops.

See [FULL_BOOT_OPERATOR_MODE.md](./FULL_BOOT_OPERATOR_MODE.md) and [DEPLOYMENT_SUDO_AND_IMAGE_IMPORT_HARDENING.md](./DEPLOYMENT_SUDO_AND_IMAGE_IMPORT_HARDENING.md).

**Do not** store `SUDO_PASS` in the repo or shell history.

## Cluster health

```bash
kubectl get nodes
kubectl get pods -A
sudo systemctl status k3s
```

## App health

```bash
bash scripts/deploy/preview-status.sh
curl -s http://41.57.127.235/actuator/health
curl -s http://41.57.127.235/health/version | jq .
```

## Logs

```bash
bash scripts/deploy/preview-logs.sh experience-bff
bash scripts/deploy/preview-logs.sh one-ui-shell
kubectl logs -n impilo-preview -l app=experience-bff --tail=100
```

## Redeploy (images + Helm)

```bash
bash scripts/deploy/preview-build-images.sh
bash scripts/deploy/preview-deploy.sh
```

Ensure `DEPLOY_BRANCH` is exported when deploying a specific commit (detached HEAD).

## Rollback

```bash
bash scripts/deploy/preview-rollback.sh
```

## Deployed commit check

```bash
cd /opt/impilo/repos/Impilo-vNext && git rev-parse HEAD
curl -s http://41.57.127.235/health/version
```

## Resource checks

```bash
free -h
df -h /
kubectl top nodes 2>/dev/null || true
```

## Restart k3s

```bash
sudo systemctl restart k3s
```

## Failed pods

```bash
kubectl get pods -n impilo-preview
kubectl describe pod -n impilo-preview <name>
```

## Related docs

- [DEPLOYMENT_SUDO_AND_IMAGE_IMPORT_HARDENING.md](./DEPLOYMENT_SUDO_AND_IMAGE_IMPORT_HARDENING.md)
- [HUMAN_AUTHORIZED_PREVIEW_DEPLOYMENT.md](./HUMAN_AUTHORIZED_PREVIEW_DEPLOYMENT.md)
- [DUAL_MODE_TEST_PIPELINE.md](./DUAL_MODE_TEST_PIPELINE.md)
