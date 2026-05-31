# Preview deployment — sudo and k3s image import hardening

## Current issue

Preview images are built with **Docker** (user `robert`, `docker` group) but must be loaded into **k3s containerd** with:

```bash
docker save <image> | sudo k3s ctr images import -
```

The k3s containerd socket (`/run/k3s/containerd/containerd.sock`) is **root-only**. Any import step requires elevated privileges.

## Why the first authorized deploy failed in the Cursor agent shell

1. `scripts/dev/build-images.sh` (via `manual-authorized-preview-deploy.sh`) reached **“Importing images into k3s containerd…”**.
2. The agent subshell is **non-interactive** (`sudo` cannot prompt for a password).
3. `sudo -n` failed (no cached credential in that session).
4. `SUDO_PASS` was **not** set (and must not be committed or documented).
5. Docker builds **completed**, but k3s never received the new images until import was run in an **interactive VM terminal** where `sudo` succeeded.

**Important:** Interactive `sudo` in your SSH or Cursor **terminal** does **not** carry over to the agent’s separate non-TTY shell.

## Current manual workaround (safe fallback — option D)

Use the VM terminal (Remote SSH) after VM quality gates and explicit deploy authorization:

```bash
cd /opt/impilo/repos/Impilo-vNext
export DEPLOY_BRANCH="claude/staging-ux-orchestration-remediation-Yypyl"
export DEPLOY_COMMIT_SHA="$(git rev-parse HEAD)"

# If images already built (tags :preview exist):
bash scripts/dev/import-images-k3s.sh

# Full authorized path (rebuilds if needed):
printf 'AUTHORIZE DEPLOY WITH VM GATES\n' | bash scripts/deploy/manual-authorized-preview-deploy.sh
```

Verify:

```bash
curl -s http://41.57.127.235/health/version
kubectl get pods -n impilo-preview
bash scripts/deploy/preview-smoke-test.sh
```

## Options evaluated

| Option | Summary | Verdict |
|--------|---------|---------|
| **A. Limited NOPASSWD sudo** | Allow `robert` to run **one** root-owned import script, no args | **Recommended** for automation |
| **B. Root-owned deploy helper** | Same as A if script is installed under `/usr/local/sbin/` and owned `root:root` | **Recommended** (implementation of A) |
| **C. Local registry** | Push to registry; k3s pulls without `ctr import` | More moving parts; needs `registries.yaml`, pull secrets, host reachability; **not** default for single-node preview |
| **D. Interactive sudo only** | Documented fallback | **Valid today**; keep for break-glass |

**Rejected:**

- Broad `NOPASSWD: ALL` for `robert`
- Storing `SUDO_PASS` in repo, docs, or shell history
- Committing sudo passwords or `.env` secrets

## Recommended long-term solution (A + B)

Use the repo script **`scripts/deploy/k3s-import-preview-images.sh`**, installed on the VM as a **root-owned** executable, with **passwordless sudo only for that path**.

The script:

- Runs only as **root** (`id -u` check)
- Imports **only** `impilo/experience-bff:preview` and `impilo/one-ui-shell:preview`
- Verifies images exist in Docker before import
- Does not accept extra arguments (reduces injection surface)

Call path from repo scripts: `scripts/deploy/_k3s-import-preview-images.sh` → tries `sudo -n` on the installed helper, then interactive `sudo`, then optional `SUDO_PASS` (CI/bootstrap only, never in git).

### Install on the VM (one-time, operator)

```bash
cd /opt/impilo/repos/Impilo-vNext
sudo cp scripts/deploy/k3s-import-preview-images.sh /usr/local/sbin/k3s-import-preview-images.sh
sudo chown root:root /usr/local/sbin/k3s-import-preview-images.sh
sudo chmod 755 /usr/local/sbin/k3s-import-preview-images.sh
```

### Exact sudoers rule (recommended)

Create **`/etc/sudoers.d/impilo-k3s-import`** with mode `0440`:

```sudoers
# Impilo preview: import only pre-built :preview images into k3s containerd.
Defaults!/usr/local/sbin/k3s-import-preview-images.sh !env_keep
robert ALL=(root) NOPASSWD: /usr/local/sbin/k3s-import-preview-images.sh
```

Validate before saving:

```bash
sudo visudo -cf /etc/sudoers.d/impilo-k3s-import
```

**Do not** add `NOPASSWD` for `/usr/bin/k3s`, `docker`, or the repo copy under `/opt/impilo/...` (writable by deploy user). Only the **installed** root-owned path above.

### Risks

| Risk | Mitigation |
|------|------------|
| Script tampering before `cp` to `/usr/local/sbin` | Install from known-good commit; `chown root:root`; review script in PRs |
| User replaces `/usr/local/sbin` binary | Root-only write on `/usr/local/sbin`; audit with `rpm -V` / checksum |
| Import of wrong images | Hard-coded image list; tags fixed to `:preview` |
| Passwordless sudo expansion | Single entry in `sudoers.d`; no wildcards |

### Rollback plan

1. Remove sudoers drop-in: `sudo rm /etc/sudoers.d/impilo-k3s-import`
2. Remove helper: `sudo rm /usr/local/sbin/k3s-import-preview-images.sh`
3. Confirm: `sudo -n -l` as `robert` should **not** list the import command
4. Fall back to **interactive sudo** (option D) for imports
5. Preview cluster unchanged; only the import **mechanism** reverts

## Branch metadata fix (related)

`/health/version` showed `"branch": ""` because `preview-deploy.sh` used `git branch --show-current` while the repo was in **detached HEAD** after `git checkout $DEPLOY_COMMIT_SHA`.

**Fix:** `scripts/deploy/_preview-deploy-metadata.sh` resolves branch from `DEPLOY_BRANCH`, then detached-HEAD fallbacks (`origin/*` at HEAD). `github-actions-remote-preview-deploy.sh` exports `DEPLOY_BRANCH` / `DEPLOY_COMMIT_SHA` before Helm.

**Live preview:** Still shows empty branch until the next Helm upgrade (metadata-only). No redeploy was performed for this doc change. To patch live metadata only (requires approval):

```bash
helm upgrade impilo-preview deploy/helm/impilo-vnext -n impilo-preview \
  -f deploy/helm/impilo-vnext/values-preview.yaml \
  --set global.gitBranch="claude/staging-ux-orchestration-remediation-Yypyl" \
  --set global.gitCommit="5a58424d8c2621abbc589ca70e8f5f61c87527f2" \
  --reuse-values
kubectl rollout restart deployment/experience-bff -n impilo-preview
```

## References

- [DEV_PREVIEW_OPERATIONS.md](./DEV_PREVIEW_OPERATIONS.md)
- [HUMAN_AUTHORIZED_PREVIEW_DEPLOYMENT.md](./HUMAN_AUTHORIZED_PREVIEW_DEPLOYMENT.md)
- `scripts/deploy/k3s-import-preview-images.sh`
- `scripts/deploy/_k3s-import-preview-images.sh`
