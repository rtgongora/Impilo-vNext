# Full boot — human sudo checkpoint

**Checkpoint ID:** cp-20260603115849-6acbb3cd  
**Time:** 2026-06-03T11:58:49+02:00  
**Branch:** claude/staging-ux-orchestration-remediation-Yypyl @ 6acbb3cd  

## What needs your consent

Privileged action (sudo password): **cleanup_duplicate_k3s_import_processes**

- **Affected namespace:** `impilo-full-preview` (full boot only)
- **Protected (untouched):** `impilo-preview` (slice at http://41.57.127.235)

## What to run (one sequence)

Open PowerShell:

```powershell
ssh -p 2276 robert@41.57.127.235
```

Inside the VM:

```bash
cd /opt/impilo/repos/Impilo-vNext
git pull
sudo -v
bash scripts/operator/fullboot.sh sudo-checkpoint-run
```

This performs **only**: cleanup_duplicate_k3s_import_processes  
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

`CHECKPOINT_CLEANUP: no impilo-k3s-import processes and helper version matches repo`

## Log

`/opt/impilo/repos/Impilo-vNext/reports/full-boot/sudo-checkpoint-run.log`
