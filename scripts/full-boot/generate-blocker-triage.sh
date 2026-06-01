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
entries = cls["classifications"]

lines = [
    "# Full Boot Blocker Triage",
    "",
    f"> Updated: {datetime.datetime.now(datetime.timezone.utc).isoformat()}",
    "",
    "> Image doctrine: missing Dockerfile ≠ failure. See `RUNTIME_IMAGE_STRATEGY_DOCTRINE.md`.",
    "",
    "## Former missing-Dockerfile reclassification (UI / optional)",
    "",
    "| Service | Plane | Reclass | Strategy | Runtime required |",
    "|---------|-------|---------|----------|------------------|",
]

ui_optional = [
    e for e in entries
    if e.get("component_type") == "frontend_app"
    and e.get("image_strategy_reclass") in (
        "buildpack-candidate", "buildpack_candidate", "not-required-mobile-artifact", "shared-template-candidate"
    )
]
for e in sorted(ui_optional, key=lambda x: x["id"]):
    lines.append(
        f"| `{e['id']}` | {e.get('plane','?')} | {e.get('image_strategy_reclass')} | {e.get('image_strategy')} | {e.get('runtime_image_required')} |"
    )

lines += [
    "",
    "## Active blockers",
    "",
    "| Plane | Service | Type | Evidence | Log | Fix | Priority | Status |",
    "|-------|---------|------|----------|-----|-----|----------|--------|",
]

def add(plane, svc, typ, ev, log, fix, pri, status):
    lines.append(f"| {plane} | `{svc}` | {typ} | {ev} | `{log}` | {fix} | {pri} | {status} |")

for e in entries:
    sid = e["id"]
    plane = e.get("plane", "?")
    if e.get("image_strategy_status") == "missing_required_image_strategy":
        add(plane, sid, "missing_required_image_strategy", e.get("image_strategy_reclass", ""), "—",
            "Assign valid runtime image strategy", "P0", "open")
    elif e.get("blocker") == "not_deployed_in_preview" and e.get("classification") == "required_full_boot":
        add(plane, sid, "not_deployed", "not in impilo-preview slice", "—",
            "Deploy in impilo-full-preview after authorization", "P0", "open")

build_summary = root / "reports/full-boot/full-build-summary.json"
if build_summary.exists():
    bs = json.loads(build_summary.read_text())
    for r in bs.get("results", []):
        if r.get("classification") != "required_full_boot" or r.get("status") != "fail":
            continue
        svc = r.get("id")
        ent = next((x for x in entries if x["id"] == svc), {})
        add(ent.get("plane", "?"), svc, "build failure", "mvn/npm failed", f"reports/full-boot/build-logs/{svc}.log",
            "fix compile deps", "P0", "open")

img_dir = root / "reports/full-boot/image-logs"
if img_dir.exists():
    for log in sorted(img_dir.glob("*.log")):
        text = log.read_text(errors="ignore")
        if "FAIL impilo" in text or (text.startswith("FAIL")):
            svc = log.stem
            ent = next((x for x in entries if x["id"] == svc), {})
            if ent.get("runtime_image_required") and ent.get("classification") == "required_full_boot":
                add(ent.get("plane", "?"), svc, "image build failure", ent.get("image_strategy", ""),
                    str(log.relative_to(root)), "shared_template or fix Dockerfile COPY jar", "P0", "open")

out = root / "docs/environment/FULL_BOOT_BLOCKER_TRIAGE.md"
out.write_text("\n".join(lines) + "\n")
print(f"Wrote {out}")
PY
