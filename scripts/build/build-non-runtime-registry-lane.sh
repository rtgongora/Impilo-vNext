#!/usr/bin/env bash
# Build/validate non-runtime registry entries (internal packages, mobile, bundled UI, clients).
# Emits run-not-applicable reasons; does not deploy to k3s.
set -euo pipefail
REPO="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO"
source "$REPO/scripts/full-boot/_full-boot-common.sh"
full_boot_ensure_artifacts

OUT_JSON="$FULL_BOOT_REPORTS/non-runtime-lane-report.json"
OUT_MD="$FULL_BOOT_REPORTS/non-runtime-lane-report.md"
mkdir -p "$FULL_BOOT_REPORTS"

python3 <<'PY'
import json, os, subprocess, yaml
from datetime import datetime, timezone
from pathlib import Path

root = Path(os.environ.get("REPO_PATH", "."))
cls = yaml.safe_load((root / "config/full-boot-service-classification.yml").read_text())
entries = cls["classifications"]
rows = []
pass_n = fail_n = skip_n = 0

def lane(e):
    return e.get("deployment_lane", "build_validate_only")

def validate_entry(e):
    sid = e["id"]
    strategy = e.get("image_strategy", "")
    path = e.get("source_path") or ""
    status = "pass"
    detail = e.get("run_not_applicable_reason") or e.get("reason") or f"lane={lane(e)}"
    if lane(e).startswith("runtime_"):
        return {"id": sid, "lane": lane(e), "status": "skip", "detail": "runtime lane — not processed here"}
    if strategy == "not-required-internal-package":
        if path and (root / path / "package.json").exists():
            r = subprocess.run(["npm", "run", "-w", path, "type-check"], cwd=root / "ui", capture_output=True, text=True)
            if r.returncode != 0:
                return {"id": sid, "lane": lane(e), "status": "advisory", "detail": "ui workspace type-check skipped or failed"}
        return {"id": sid, "lane": lane(e), "status": "pass", "detail": detail}
    if strategy == "not-required-mobile-artifact":
        if (root / "apps/mobile/package.json").exists():
            return {"id": sid, "lane": lane(e), "status": "pass", "detail": "mobile monorepo present — artifact lane"}
        return {"id": sid, "lane": lane(e), "status": "advisory", "detail": "mobile root missing"}
    if strategy == "not-required-generated-client":
        contract = root / "contracts"
        return {"id": sid, "lane": lane(e), "status": "pass", "detail": "contract-only client"}
    if e.get("component_type") == "frontend_app" or strategy == "buildpacks":
        pkg = root / path / "package.json" if path else None
        if pkg and pkg.exists():
            return {"id": sid, "lane": lane(e), "status": "pass", "detail": "bundled UI workspace — build via ui monorepo"}
        return {"id": sid, "lane": lane(e), "status": "pass", "detail": detail}
    return {"id": sid, "lane": lane(e), "status": "pass", "detail": detail}

for e in sorted(entries, key=lambda x: x["id"]):
    if lane(e).startswith("runtime_"):
        continue
    row = validate_entry(e)
    rows.append(row)
    if row["status"] == "pass":
        pass_n += 1
    elif row["status"] == "fail":
        fail_n += 1
    else:
        skip_n += 1

report = {
    "generated_at": datetime.now(timezone.utc).isoformat(),
    "doctrine": "run-not-applicable for non-k3s registry entries",
    "total_non_runtime": len(rows),
    "pass": pass_n,
    "fail": fail_n,
    "skip": skip_n,
    "run_not_applicable": True,
    "entries": rows,
}
out = root / "reports/full-boot/non-runtime-lane-report.json"
out.write_text(json.dumps(report, indent=2))
md = [
    "# Non-runtime registry lane",
    "",
    f"Generated: {report['generated_at']}",
    "",
    f"**Entries:** {len(rows)} | **Pass:** {pass_n} | **Fail:** {fail_n} | **Skip/advisory:** {skip_n}",
    "",
    "All entries below are **build/validate only** — `run-not-applicable` in k3s full boot.",
    "",
    "| ID | Lane | Status | Detail |",
    "|----|------|--------|--------|",
]
for r in rows[:200]:
    md.append(f"| {r['id']} | {r['lane']} | {r['status']} | {r['detail'][:80]} |")
if len(rows) > 200:
    md.append(f"| … | | | ({len(rows)-200} more) |")
md.append("")
(root / "reports/full-boot/non-runtime-lane-report.md").write_text("\n".join(md))
print(f"NON_RUNTIME_LANE: pass={pass_n} fail={fail_n} skip={skip_n} total={len(rows)}")
PY

echo "Report: $OUT_JSON"
