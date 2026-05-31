#!/usr/bin/env bash
# Regenerate architecture inventory markdown from canonical repo sources.
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO_PATH"

python3 <<'PY'
import pathlib, re, json
root = pathlib.Path("/opt/impilo/repos/Impilo-vNext")
routes = (root / "ui/one-ui-shell/src/lib/routes.ts").read_text()
paths = sorted(set(re.findall(r'path:\s*"([^"]+)"', routes)))
svc_md = ["# Service inventory", "", "> Canonical YAML: `docs/registry/services-registry.yaml`. Regenerate: `bash scripts/architecture/sync-pipeline-inventories.sh`", ""]
svc_md.append("| Service module | Plane | Port | Status |")
svc_md.append("|---|---|---|---|")
import yaml
reg = yaml.safe_load((root / "docs/registry/services-registry.yaml").read_text())
for s in sorted(reg.get("services", []), key=lambda x: x.get("maven_module","")):
    svc_md.append(f"| {s.get('maven_module','?')} | {s.get('primary_plane','?')} | {s.get('default_http_port') or '—'} | {s.get('production_status','?')} |")
(root / "docs/architecture/SERVICE_INVENTORY.md").write_text("\n".join(svc_md) + "\n")

route_md = ["# Frontend route inventory", "", f"> Generated from `ui/one-ui-shell/src/lib/routes.ts` ({len(paths)} routes)", ""]
for p in paths[:200]:
    route_md.append(f"- `{p}`")
if len(paths) > 200:
    route_md.append(f"- … and {len(paths)-200} more")
(root / "docs/architecture/FRONTEND_ROUTE_INVENTORY.md").write_text("\n".join(route_md) + "\n")

api_md = ["# API endpoint inventory", "", "> OpenAPI under `contracts/openapi/`. Extend per contract.", ""]
for f in sorted((root / "contracts/openapi").glob("*.yaml"))[:80]:
    api_md.append(f"- `{f.name}`")
(root / "docs/architecture/API_ENDPOINT_INVENTORY.md").write_text("\n".join(api_md) + "\n")

feat = ["# Feature inventory", "", "> High-level surfaces; see `docs/frontend/FRONTEND_IMPLEMENTATION_STATUS.md` for maturity.", ""]
for zone in ["registry","clinical","enterprise","data","trust","experience"]:
    feat.append(f"- **{zone} plane** — routes under `/{zone}` and related BFF `/internal/v1/` namespaces")
(root / "docs/architecture/FEATURE_INVENTORY.md").write_text("\n".join(feat) + "\n")

mob = ["# Mobile app inventory", "", "| App | Path | Stack |", "|---|---|---|",
       "| Citizen | `apps/mobile/citizen-app` | Expo / React Native |",
       "| Provider | `apps/mobile/provider-app` | Expo / React Native |",
       "| Shared packages | `apps/mobile/packages/*` | pnpm workspace |"]
(root / "docs/architecture/MOBILE_APP_INVENTORY.md").write_text("\n".join(mob) + "\n")
print("Inventories updated.")
PY
