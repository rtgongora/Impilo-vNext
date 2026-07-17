# Full boot — human sudo checkpoint

**Checkpoint ID:** cp-20260716114608-32f2c4fa6  
**Time:** 2026-07-16T11:46:08+02:00  
**Branch:** claude/staging-ux-orchestration-remediation-Yypyl @ 32f2c4fa6  

## What needs your consent

Privileged action (sudo password): **cleanup_duplicate_k3s_import_processes**

- **Affected namespace:** `impilo-full-preview` (full boot only)
- **Protected (untouched):** `impilo-preview` (slice at https://impilo.mohcc.gov.zw)

## What to run (one sequence)

Open PowerShell:

```powershell
ssh -p 2276 robert@impilo.mohcc.gov.zw
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
