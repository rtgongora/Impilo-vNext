# Full boot operator mode

Plain-language guide for running **full boot** into `impilo-full-preview` while keeping the VM secure.

**Operating model:** Cursor owns the workflow. The product owner owns authorization (sudo password for a **single** checkpoint when needed; deploy phrase separately).

## Who does what

| Role | Responsibility |
|------|----------------|
| **Cursor agent** | `bash scripts/operator/fullboot.sh deploy` / `continue` / `verify-images` — all non-sudo prep and orchestration |
| **Product owner** | At most **one** SSH block + sudo password when checkpoint is required; then tell Cursor `sudo checkpoint completed` |
| **Product owner (deploy)** | Phrase `AUTHORIZE FULL BOOT PREVIEW DEPLOY` only when ready to Helm deploy — **not** the same as sudo |
| **Technical operator** | One-time: `sudo bash scripts/operator/install-k3s-image-helper.sh` (enables passwordless import/list for Cursor) |

## What this permits

After one-time install, user `robert` may run **without a password**:

```text
sudo -n /usr/local/sbin/impilo-k3s-import-images <tag>
sudo -n /usr/local/sbin/impilo-k3s-list-images <tag>
```

Allowed tags: `preview` or `preview-<git-sha>` (hex only).

Helpers:

- Import only images listed in `config/full-boot-service-classification.yml` (`required_full_boot`) plus approved infra images
- Write logs to `/var/log/impilo-k3s-image-helper.log` and `reports/full-boot/k3s-image-import-helper.log`
- Refuse arbitrary tar paths (only `/tmp/impilo-image-*` and repo `reports/full-boot/*.tar`)
- Refuse repo paths other than `/opt/impilo/repos/Impilo-vNext`

## What this does **not** permit

- Broad `NOPASSWD: ALL`
- Passwordless `k3s`, `docker`, `ctr`, `kubectl`, or `/bin/bash`
- Arbitrary commands as root
- Storing `SUDO_PASS` in git, docs, or shell history
- Modifying helper scripts (installed `root:root`, not writable by `robert`)

## Cursor workflow (preferred)

```bash
cd /opt/impilo/repos/Impilo-vNext
bash scripts/operator/fullboot.sh deploy
```

If images need sudo and passwordless helper is unavailable, Cursor stops and writes:

- `reports/full-boot/sudo-checkpoint.json`
- `reports/full-boot/sudo-checkpoint.md`

Product owner runs **one** sequence (see checkpoint markdown), then tells Cursor:

```text
sudo checkpoint completed
```

Cursor resumes:

```bash
bash scripts/operator/fullboot.sh continue
```

Deploy only after images verify and product owner authorizes:

```bash
export FULLBOOT_DEPLOY_AUTHORIZED=1
printf '%s\n' 'AUTHORIZE FULL BOOT PREVIEW DEPLOY' | bash scripts/operator/fullboot.sh deploy
```

## Commands

| Command | Purpose |
|---------|---------|
| `status` | Slice + full-boot NS + helper + checkpoint state |
| `prepare` | Preflight + dry-run (no deploy) |
| `verify-images` | 22-image presence check |
| `import-images` | Import (passwordless or checkpoint) |
| `deploy` | Orchestrate; stop at checkpoint or deploy auth |
| `continue` | Resume after successful checkpoint |
| `sudo-checkpoint-run` | Product owner: run pending privileged action only |
| `sudo-checkpoint-status` | Pending checkpoint / last result |
| `report` | Log summary |
| `retry` | Re-attempt from workflow state |
| `help` | Usage |

## Human sudo checkpoint (when passwordless helper unavailable)

Allowed privileged actions only:

- `import_full_boot_images_to_k3s`
- `list_full_boot_k3s_images`
- `cleanup_stale_k3s_import_temp_files`

Product owner terminal block (example):

```powershell
ssh -p 2276 robert@41.57.127.235
```

```bash
cd /opt/impilo/repos/Impilo-vNext
git pull
sudo -v
bash scripts/operator/fullboot.sh sudo-checkpoint-run
```

Then: **sudo checkpoint completed** → Cursor runs `bash scripts/operator/fullboot.sh continue`.

**Never** ask the product owner to manually run tmux, helm uninstall, kubectl delete namespace, or full import loops — Cursor orchestrates those without sudo where possible.

## Technical operator — one-time setup

```bash
cd /opt/impilo/repos/Impilo-vNext
sudo bash scripts/operator/install-k3s-image-helper.sh
bash scripts/operator/test-k3s-image-helper.sh
```

Expected: `SUMMARY pass=… fail=0` and `sudo -n /usr/local/sbin/impilo-k3s-list-images preview` prints `IMAGE_PRESENCE: PASS` when images are imported.

## Rollback

```bash
sudo bash scripts/operator/uninstall-k3s-image-helper.sh
```

Fall back to interactive: `sudo -v` then `bash scripts/dev/import-full-vnext-images-k3s.sh preview`

## Cursor agent

After helper install, the agent can run:

```bash
bash scripts/operator/fullboot.sh import-images
bash scripts/operator/fullboot.sh verify-images
```

without interactive sudo. Do **not** set `FULL_BOOT_SKIP_IMPORT=1` unless images were just verified.

## Success criteria

- `IMAGE_PRESENCE: PASS` and `SUMMARY ok=22 fail=0` from verify
- Full boot deploy: **22/22** required deployments Available (not merely present)
- `impilo-preview` slice unchanged and healthy at `http://41.57.127.235/health/version`

See also: [DEPLOYMENT_SUDO_AND_IMAGE_IMPORT_HARDENING.md](./DEPLOYMENT_SUDO_AND_IMAGE_IMPORT_HARDENING.md)
