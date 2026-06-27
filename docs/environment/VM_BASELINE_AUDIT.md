# VM Baseline Audit

**Date:** 2026-05-29 (Web Preview VM) · **Updated:** 2026-06-27 (dual-VM model)  
**Host:** `41.57.127.235:2276` — Web Preview / Engineering Control  
**Purpose:** Record pre-setup state before Impilo remote dev workspace + dev preview sandbox installation.

> **Dual-VM model (2026-06-27):** `41.57.127.235` remains the canonical **Web Preview / Engineering Control** VM (this document).  
> `41.57.127.218` has been **reactivated/provisioned** as the **MOHCC Maestro Android Mobile Automation Sandbox** — a separate VM for Android emulator and Maestro runtime validation. It is part of the active vNext dev-test pipeline and consumes the preview API at `http://41.57.127.235`.  
> See [`DUAL_VM_OPERATING_MODEL.md`](./DUAL_VM_OPERATING_MODEL.md) and [`docs/mobile/MOBILE_ANDROID_SANDBOX.md`](../mobile/MOBILE_ANDROID_SANDBOX.md).
>
> **Supersedes (2026-05-30 note):** An earlier note stated `41.57.127.218` was retired after IP reassignment to `41.57.127.235`. That applied to a **prior** topology. The two IPs now denote **different active VMs** with distinct roles.

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
