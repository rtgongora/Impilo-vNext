# Remote Development Workspace Usage

**All Impilo vNext development happens on the VM via Cursor Remote SSH.** The local laptop
clone must not be used for normal development — the laptop is only the Cursor client and the
browser-testing machine.

| Item | Value |
|------|-------|
| Remote SSH target | `robert@41.57.127.235:2276` |
| Repo path | `/opt/impilo/repos/Impilo-vNext` |
| Active working branch | `claude/staging-ux-orchestration-remediation-Yypyl` (unless explicitly changed) |
| Preview URL | `http://41.57.127.235` |
| Source of truth | **GitHub** `rtgongora/Impilo-vNext` |

## Cursor Startup Checklist

1. **Open Cursor** on your laptop.
2. **Connect using Remote SSH** (Command Palette → "Remote-SSH: Connect to Host…").
3. **SSH target:** `robert@41.57.127.235:2276`.
4. **Open folder:** `/opt/impilo/repos/Impilo-vNext`.
5. **Confirm branch** is `claude/staging-ux-orchestration-remediation-Yypyl` (unless explicitly changed):

   ```bash
   cd /opt/impilo/repos/Impilo-vNext
   git status
   git branch --show-current
   git rev-parse --short HEAD
   git remote -v
   ```

6. **Pull latest:**

   ```bash
   git pull origin claude/staging-ux-orchestration-remediation-Yypyl
   ```

7. **Start work** (create a feature/fix branch where appropriate).
8. **Run tests/builds on the VM:**

   ```bash
   bash scripts/dev/run-tests.sh
   bash scripts/dev/build-all.sh
   ```

9. **Deploy the preview from the VM:**

   ```bash
   bash scripts/deploy/preview-build-images.sh
   bash scripts/deploy/preview-deploy.sh
   bash scripts/deploy/preview-status.sh
   bash scripts/deploy/preview-smoke-test.sh
   ```

10. **Test using the browser** at `http://41.57.127.235` (on the laptop).
11. **Commit and push to GitHub** from the VM:

    ```bash
    git add -p && git commit -m "feat: ..."
    git push -u origin <your-branch>
    ```

## SSH Access (terminal-only, optional)

```bash
ssh robert@41.57.127.235 -p 2276
```

## Verify the workspace

```bash
cd /opt/impilo/repos/Impilo-vNext
bash scripts/dev/verify-remote-cursor-workspace.sh
```

## Daily Commands

```bash
cd /opt/impilo/repos/Impilo-vNext

# Dependencies
bash scripts/dev/install-dependencies.sh

# Tests
bash scripts/dev/run-tests.sh

# Build
bash scripts/dev/build-all.sh

# Preview deploy
bash scripts/deploy/preview-build-images.sh
bash scripts/deploy/preview-deploy.sh
bash scripts/deploy/preview-status.sh
bash scripts/deploy/preview-smoke-test.sh

# Logs
bash scripts/deploy/preview-logs.sh experience-bff
bash scripts/deploy/preview-logs.sh one-ui-shell

# Deployed commit
curl -s http://41.57.127.235/health/version | jq .
git rev-parse HEAD
```

## Why Not the Laptop?

The laptop is a low-resource client machine; running Docker/Kubernetes and full builds locally
overwhelms it (and freezes when the repo sits inside cloud-synced folders). All heavy
build/test/deploy work runs on the VM. The laptop is used only as the Cursor Remote SSH client
and to open `http://41.57.127.235` in a browser for preview testing.

## Git Workflow

GitHub is the source of truth. Commit and push from the VM workspace:

```bash
git checkout -b feat/my-change
# ... edit, test, build ...
git add -p && git commit -m "feat: ..."
git push -u origin feat/my-change
```
