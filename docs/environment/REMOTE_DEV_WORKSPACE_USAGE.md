# Remote Development Workspace Usage

## SSH Access

```bash
ssh robert@41.57.127.235 -p 2276
```

## Using Cursor with this Remote Development Workspace

- **Do not** open the local laptop repo for normal development.
- Open Cursor → **Remote SSH** → `robert@41.57.127.235:2276`.
- Open folder: `/opt/impilo/repos/Impilo-vNext`.
- Confirm branch: `git branch --show-current`.
- Pull latest: `git pull origin <branch>`.
- Create feature branches on the VM.
- Run builds/tests on the VM.
- Deploy preview from the VM.
- Commit and push to GitHub from the VM.
- Use the **laptop browser only** to open `http://41.57.127.235/` for preview testing.

## Daily Commands

```bash
cd /opt/impilo/repos/Impilo-vNext

# Verify tools + repo
bash scripts/dev/verify-remote-cursor-workspace.sh

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

The laptop has 16GB RAM — Docker/Kubernetes workloads crash. All heavy build/test/deploy work runs on the 125GB VM.

## Git Workflow

GitHub is the source of truth:

```bash
git checkout -b feat/my-change
# ... edit, test, build ...
git add -p && git commit -m "feat: ..."
git push -u origin feat/my-change
```
