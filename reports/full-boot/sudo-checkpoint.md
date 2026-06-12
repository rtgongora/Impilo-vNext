# Full boot — human sudo checkpoint

**Checkpoint ID:** cp-20260611062832-4917def8  
**Time:** 2026-06-11T06:28:32+02:00  
**Branch:** claude/staging-ux-orchestration-remediation-Yypyl @ 4917def8  

## What needs your consent

Privileged action (sudo password): **refresh_stale27_containerd_refs**

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

This performs **only**: refresh_stale27_containerd_refs  
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

`STALE27_CONTAINERD_REF_REFRESH: complete`

## Log

`/opt/impilo/repos/Impilo-vNext/reports/full-boot/sudo-checkpoint-run.log`
