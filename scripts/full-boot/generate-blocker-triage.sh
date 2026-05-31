#!/usr/bin/env bash
# Regenerate FULL_BOOT_BLOCKER_TRIAGE.md from classification + build/image summaries.
set -euo pipefail
source "$(dirname "$0")/_full-boot-common.sh"
full_boot_ensure_artifacts

OUT="$REPO_PATH/docs/environment/FULL_BOOT_BLOCKER_TRIAGE.md"
python3 <<'PY'
import json, pathlib, yaml, datetime

root = pathlib.Path(".")
cls = yaml.safe_load((root / "config/full-boot-service-classification.yml").read_text())
build = {}
img = {}
bp = root / "reports/full-boot/full-build-summary.json"
ip = root / "reports/full-boot/full-image-build-summary.json"
if bp.exists():
    b = json.loads(bp.read_text())
    for r in b.get("results", []):
        if isinstance(r, str):
            continue
        build[r.get("id", "")] = r
if ip.exists():
    img = json.loads(ip.read_text())

lines = [
    "# Full Boot Blocker Triage",
    "",
    f"> Updated: {datetime.datetime.now(datetime.timezone.utc).isoformat()}",
    "",
    "| Plane | Service | Type | Evidence | Log | Fix | Priority | Status |",
    "|-------|---------|------|----------|-----|-----|----------|--------|",
]

def add(plane, svc, typ, ev, log, fix, pri, status):
    lines.append(f"| {plane} | `{svc}` | {typ} | {ev} | `{log}` | {fix} | {pri} | {status} |")

for e in cls.get("classifications", []):
    sid = e["id"]
    plane = e.get("plane", "?")
    if e.get("classification") == "required_full_boot":
        blocker = e.get("blocker") or ""
        if blocker == "not_deployed_in_preview":
            add(plane, sid, "missing_infrastructure", "not in impilo-preview", "—", "deploy in impilo-full-preview", "P0", "open")
        if blocker == "missing_dockerfile":
            add(plane, sid, "missing Dockerfile", "no Dockerfile", f"reports/full-boot/image-logs/{sid}.log", "Add Dockerfile", "P0", "open")
    if e.get("blocker"):
        add(plane, sid, "classification", e["blocker"], "—", e.get("reason", ""), e.get("priority", "P2"), "open")

# Parse build logs for failures
log_dir = root / "reports/full-boot/build-logs"
if log_dir.exists():
    for log in log_dir.glob("*.log"):
        if log.name.startswith("_"):
            continue
        text = log.read_text(errors="ignore")
        if "FAIL" in text and "PASS" not in text.split("\n")[0]:
            svc = log.stem
            ent = next((x for x in cls["classifications"] if x["id"] == svc), {})
            add(ent.get("plane", "?"), svc, "build failure", "mvn/npm failed", str(log.relative_to(root)), "fix compile/test deps", "P0", "open")

out = root / "docs/environment/FULL_BOOT_BLOCKER_TRIAGE.md"
out.write_text("\n".join(lines) + "\n")
print(f"Wrote {out} ({len(lines)-6} table rows)")
PY
