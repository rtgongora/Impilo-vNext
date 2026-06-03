# Deployment sudo and k3s image import hardening

## Problem

Full boot loads Docker-built images into **k3s containerd** with:

```bash
docker save <image> -o /tmp/impilo-image-….tar
k3s ctr images import <tar>
```

The containerd socket is **root-only**. Cursor’s agent shell is **non-interactive** and cannot reliably prompt for sudo. The product owner should not re-enter passwords for every full boot attempt.

## Rejected approaches

| Approach | Why rejected |
|----------|----------------|
| `robert ALL=(ALL) NOPASSWD: ALL` | Too broad |
| `SUDO_PASS` in repo, docs, or shell history | Secret leakage |
| Cursor running arbitrary `sudo k3s …` / `sudo docker …` | No input validation or audit boundary |
| Writable helper scripts under `/opt/impilo/...` | Tampering risk |

## Chosen model (narrow helper)

| Property | Implementation |
|----------|----------------|
| Root-owned helpers | `/usr/local/sbin/impilo-k3s-import-images`, `/usr/local/sbin/impilo-k3s-list-images` |
| Shared library | `/usr/local/libexec/impilo/k3s-image-helper-common.sh` (`0644`, root only) |
| Sudoers | `/etc/sudoers.d/impilo-k3s-image-helper` — **only** the two sbin paths |
| Install | `sudo bash scripts/operator/install-k3s-image-helper.sh` |
| Uninstall | `sudo bash scripts/operator/uninstall-k3s-image-helper.sh` |
| Audit log | `/var/log/impilo-k3s-image-helper.log` |
| Repo mirror log | `reports/full-boot/k3s-image-import-helper.log` |

### Exact sudoers rule

```sudoers
Defaults!/usr/local/sbin/impilo-k3s-import-images !env_keep
Defaults!/usr/local/sbin/impilo-k3s-list-images !env_keep
robert ALL=(root) NOPASSWD: /usr/local/sbin/impilo-k3s-import-images, /usr/local/sbin/impilo-k3s-list-images
```

Validate before relying on it:

```bash
sudo visudo -cf /etc/sudoers.d/impilo-k3s-image-helper
sudo visudo -c
```

**Do not** add NOPASSWD for `/usr/bin/k3s`, `/usr/bin/docker`, `/usr/bin/ctr`, `/usr/bin/kubectl`, or `/bin/bash`.

### Helper input validation (v2)

- **Tag:** `preview` or `preview-[a-f0-9]{7,40}` only
- **Repo:** `/opt/impilo/repos/Impilo-vNext` only (must contain classification YAML)
- **Tar import:** only `/tmp/impilo-image-*` or `reports/full-boot/*.tar`
- **Default set:** `required_full_boot` + infra, **missing-only** (skip if ref already in containerd)
- **Lock:** `flock` on `/tmp/impilo-k3s-import.lock` — one import at a time
- **`--only id1,id2`:** import only matching required refs, then exit (no infra sweep, no all-tag sweep)
- **`--all-local-preview`:** only flag that sweeps all local `impilo/*:preview*` tags
- **`--force`:** bypass missing-only skip

### Local registry (reduces tar import load)

| Script | Role |
|--------|------|
| `scripts/operator/registry-up.sh` | Start `registry:2` on `127.0.0.1:5000` (no sudo) |
| `scripts/build/push-images-to-local-registry.sh` | Parallel push, missing-only by digest |
| Checkpoint `configure_k3s_local_registry` | `/etc/rancher/k3s/registries.yaml` + k3s restart |

Helm: `global.imageRegistry` + `imagePullPolicy: IfNotPresent` in `values-full-preview.yaml`.

### Rollback

```bash
sudo bash scripts/operator/uninstall-k3s-image-helper.sh
sudo -n -l   # as robert — must NOT list impilo helpers
```

## Repo integration

| Script | Behavior |
|--------|----------|
| `scripts/dev/import-full-vnext-images-k3s.sh` | Prefer `sudo -n /usr/local/sbin/impilo-k3s-import-images` |
| `scripts/dev/verify-full-boot-k3s-images.sh` | Prefer `sudo -n /usr/local/sbin/impilo-k3s-list-images` |
| `scripts/operator/fullboot.sh` | Product-owner wrapper |
| `scripts/deploy/full-boot-preview-deploy.sh` | Calls `fullboot.sh import-images` / `verify-images` unless `FULL_BOOT_SKIP_IMPORT=1` |

## Legacy slice preview (two images)

`scripts/deploy/k3s-import-preview-images.sh` remains for **slice** `impilo-preview` (BFF + shell only). Full boot uses the **new** helpers and full classification (22 images).

## Human sudo consent checkpoint (full boot helper)

When passwordless helpers are not available, `scripts/operator/fullboot.sh` must:

1. Complete all non-sudo preparation first.
2. Write `reports/full-boot/sudo-checkpoint.json` and `.md`.
3. Print **one** product-owner command block (SSH + `sudo-checkpoint-run`).
4. Stop until the product owner runs the checkpoint and tells Cursor `sudo checkpoint completed`.
5. Resume with `bash scripts/operator/fullboot.sh continue`.

`sudo-checkpoint-run` reads the checkpoint JSON, runs **only** the named allowed action, writes `sudo-checkpoint-result.json`, and refuses arbitrary commands.

**Sudo consent ≠ deploy authorization.** Helm deploy still requires `AUTHORIZE FULL BOOT PREVIEW DEPLOY`.

Before creating a checkpoint, `fullboot.sh` checks whether `verify-images` already reports `IMAGE_PRESENCE: PASS` and `SUMMARY ok=22 fail=0` — if so, skip import and do not ask for sudo.

**Runaway imports:** If `pgrep` shows active `impilo-k3s-import-images`, deploy/import are blocked. Cursor runs `bash scripts/operator/fullboot.sh cleanup-imports` → checkpoint `cleanup_duplicate_k3s_import_processes` (product owner runs **one** `sudo-checkpoint-run` block).

## Cursor vs interactive terminal

Interactive `sudo -v` in your SSH session does **not** apply to the agent’s separate shell. Install the narrow helper once on the VM so both can use `sudo -n` on the approved paths only.

## References

- [FULL_BOOT_OPERATOR_MODE.md](./FULL_BOOT_OPERATOR_MODE.md)
- [DEV_PREVIEW_OPERATIONS.md](./DEV_PREVIEW_OPERATIONS.md)
- `scripts/operator/install-k3s-image-helper.sh`
- `scripts/operator/test-k3s-image-helper.sh`
