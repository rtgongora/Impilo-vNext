# Impilo vNext — Server Retry Validation Acceptance Pack

**Date**: 2026-03-18
**Validation Type**: Retry after reported network resolution
**Result**: ❌ BLOCKED

---

## Definition of Done Checklist

| # | Criterion | Status | Notes |
|---|-----------|--------|-------|
| 1 | Reachability checked | ✅ Done | All 6 HTTP endpoints + SSH tested |
| 2 | Deployment attempted | ❌ Blocked | Egress proxy blocks server access |
| 3 | Runtime status checked | ❌ Blocked | Cannot reach server |
| 4 | External URLs checked | ✅ Done | All return 403 from egress proxy |
| 5 | Smoke attempted | ❌ Blocked | Cannot reach server |
| 6 | Evidence docs created | ✅ Done | 4 documents created |
| 7 | Remaining blockers documented | ✅ Done | See below |

---

## Blocker Summary

### BLOCKER-1: Claude Code Egress Proxy Restriction (CRITICAL)

**Status**: ❌ UNRESOLVED (same as 2026-03-17)
**Impact**: Total block on deployment and validation

**Details**: The Claude Code sandbox environment routes all outbound traffic through an Anthropic-managed egress proxy (`21.0.0.77:15004`). This proxy enforces a host allowlist that does not include `197.221.242.150`. All HTTP requests receive `403 host_not_allowed`; SSH connections time out.

**Evidence**:
- 6/6 HTTP endpoints: `403 Forbidden, x-deny-reason: host_not_allowed`
- SSH port 22: Connection timed out
- SSH port 7557: Connection timed out

**Resolution Options**:
1. **Deploy from a direct-access machine** — SSH into server, pull branch, run `./scripts/server-deploy.sh full`
2. **Add IP to egress allowlist** — Request Anthropic add `197.221.242.150` to this workspace's proxy config
3. **GitHub Actions workflow** — Create CI/CD pipeline that can SSH to the server

---

## What the "Network Block Resolved" Likely Means

The engineers likely resolved one of:
- Server-side firewall rules
- ISP-level routing
- Port forwarding on the server's network

What was **not resolved** (and likely was not within their control):
- The egress proxy on the Claude Code container environment

---

## Validation Commands for Manual Execution

From a machine with direct network access:

```bash
# Quick health check of all endpoints
for port in 13020 13021 13022 13023 13024 13025; do
  echo -n "Port $port: "
  curl -sS --connect-timeout 5 -o /dev/null -w "%{http_code}" http://197.221.242.150:$port
  echo
done

# TSHEPO detailed health
curl -sS http://197.221.242.150:13024/actuator/health | python3 -m json.tool

# HAPI FHIR metadata
curl -sS http://197.221.242.150:13023/fhir/metadata | python3 -m json.tool | head -20

# Full deployment (on the server itself)
ssh -p 7557 rgongora@197.221.242.150
cd /path/to/Impilo-vNext
git fetch origin claude/review-project-manifest-jb5O0
git checkout claude/review-project-manifest-jb5O0
./scripts/server-deploy.sh full
```

---

## Documents Created in This Session

| Document | Path | Purpose |
|----------|------|---------|
| Retry Validation Report | `docs/deployment/server-retry-validation-report.md` | Full retry attempt log |
| External Endpoint Validation | `docs/deployment/server-external-endpoint-validation.md` | Endpoint map + results |
| Runtime Evidence | `docs/deployment/server-runtime-evidence.md` | Evidence collection status |
| This Acceptance Pack | `docs/acceptance/server-retry-validation-pack.md` | DoD checklist + blockers |

---

## Conclusion

The deployment retry could not proceed. The blocker is the **Claude Code sandbox egress proxy**, which is outside the scope of what the target server's network engineers can resolve. The recommended path forward is to deploy directly from a machine with network access to `197.221.242.150`, using the scripts and configuration already committed to this branch.
