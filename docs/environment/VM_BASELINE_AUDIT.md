# VM Baseline Audit

**Date:** 2026-05-29  
**Host:** `41.57.127.235:2276`  
**Purpose:** Record pre-setup state before Impilo remote dev workspace + dev preview sandbox installation.

> **Note (2026-05-30):** The public IP was reassigned from the original `41.57.127.218` to `41.57.127.235` (the same VM). `41.57.127.218` is retired; use `41.57.127.235` everywhere.

## Server Snapshot

| Item | Value |
|------|--------|
| Hostname | `user-HVM-domU` |
| OS | Ubuntu 24.04.4 LTS (Noble Numbat) |
| Kernel | Linux 6.17.0-29-generic |
| CPU | Intel Xeon Gold 6230R @ 2.10GHz, 32 vCPU |
| RAM | 125 GiB total, ~123 GiB available |
| Swap | 8 GiB (unused at audit) |
| Root disk | `/dev/xvda2` 2.0T (~1% used) |
| SSH user | `robert` |
| SSH port (client) | `2276` |
| sshd listen (local) | port `22` (2276 likely NAT/forwarded) |

## Tooling State (Before Setup)

| Tool | Status |
|------|--------|
| git | missing |
| java | missing |
| maven | missing |
| node/npm | missing |
| docker | missing |
| kubectl | missing |
| helm | missing |
| k3s | missing |
| Running containers | none |

## Security / Network (Before Setup)

| Check | Finding |
|-------|---------|
| UFW | not configured initially |
| fail2ban | not active initially |
| Listening ports | SSH on 22, minimal other services |

## Commands Run

```bash
ssh robert@41.57.127.235 -p 2276
hostname && cat /etc/os-release
lscpu | grep -E 'Model name|CPU\\(s\\)'
free -h && swapon --show
df -h /
ss -tulpn | head -40
```

## Findings

1. Large fresh Ubuntu VM suitable for remote development and single-node k3s preview.
2. No prior Impilo deployment artifacts detected.
3. SSH on port 2276 works from automation host (paramiko/OpenSSH).
4. Initial connection attempts failed intermittently (connection reset before banner); later attempts succeeded — possible transient firewall/rate-limit.

## Post-Setup Verification

After bootstrap, run on the VM:

```bash
/opt/impilo/repos/Impilo-vNext/scripts/dev/check-tools.sh
/opt/impilo/repos/Impilo-vNext/scripts/dev/verify-remote-cursor-workspace.sh
```

See `DEV_WORKSPACE_AND_PREVIEW_SETUP_REPORT.md` for post-install results.
