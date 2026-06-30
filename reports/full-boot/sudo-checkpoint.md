# Full boot — human sudo checkpoint

**Checkpoint ID:** cp-20260630225914-63c2d792b  
**Time:** 2026-06-30T22:59:14+02:00  
**Branch:** claude/crazy-merkle-3ad1a1 @ 63c2d792b  

## What needs your consent

Privileged action (sudo password): **import_full_boot_images_to_k3s**

- **Affected namespace:** `impilo-full-preview` (full boot only)
- **Protected (untouched):** `impilo-preview` (slice at http://41.57.127.235)

## What to run (one sequence)

Open PowerShell:

```powershell
ssh -p 2276 robert@41.57.127.235
```

Inside the VM:

```bash
cd /opt/impilo/repos/Impilo-vNext/.claude/worktrees/crazy-merkle-3ad1a1
git pull
sudo -v
bash scripts/operator/fullboot.sh sudo-checkpoint-run
```

This performs **only**: import_full_boot_images_to_k3s  
It will **not** deploy unless you separately authorize deploy later.

## After it succeeds

Tell Cursor:

```text
sudo checkpoint completed
```

Cursor will run:

```bash
bash scripts/operator/fullboot.sh continue
```

## Success marker

`IMAGE_PRESENCE: PASS and SUMMARY ok=22 fail=0`

## Log

`/opt/impilo/repos/Impilo-vNext/.claude/worktrees/crazy-merkle-3ad1a1/reports/full-boot/sudo-checkpoint-run.log`
