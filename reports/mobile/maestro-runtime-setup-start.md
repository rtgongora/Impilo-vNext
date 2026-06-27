# Maestro Runtime Setup — Start

**Date:** 2026-06-27  
**Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`  
**Doc alignment commit:** `c9fda06e5`  
**Execution host (235):** `user-HVM-domU` / `41.57.127.235`

## Phase 1 — VM sync status

### Web Preview VM (235)

| Check | Result |
|-------|--------|
| Branch | `claude/staging-ux-orchestration-remediation-Yypyl` |
| HEAD (at start) | `391798028` (≥ `c9fda06e5`) |
| Preview API `curl -I http://41.57.127.235` | Empty/timeout from agent session — verify manually |
| Role | Engineering control — static gates + expo fix executed here |

### Maestro VM (218)

| Check | Result |
|-------|--------|
| Ping `41.57.127.218` | PASS (from 235) |
| SSH port 2027 from 235 | **FAIL** — Network unreachable / timeout |
| SSH port 22 from 235 | Intermittent; **auth failed** (no `facility` key on 235) |
| Agent direct execution on 218 | **BLOCKED** — requires Cursor Remote SSH to 218 or network/firewall fix |

**218 validated readiness (prior activation):** KVM OK, Ubuntu 24.04.4, 20 vCPU, 48 GiB RAM, `facility` user.

## Operational path

Bootstrap scripts prepared for execution **on 218**:

```bash
ssh facility@41.57.127.218 -p 2027
bash /opt/impilo/repos/Impilo-vNext/scripts/mobile/maestro-vm-bootstrap.sh
bash /opt/impilo/repos/Impilo-vNext/scripts/mobile/maestro-vm-runtime-closure.sh
```

Work completed on **235** in this wave: static closure rerun, Expo export fix, reports, scripts.

## Next

Open Cursor Remote SSH session to **218** and run bootstrap, or fix inter-VM SSH routing for port 2027 from 235.
