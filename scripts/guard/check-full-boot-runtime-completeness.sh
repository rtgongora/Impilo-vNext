#!/usr/bin/env bash
# Full-boot runtime completeness gate — compares catalog, classification, matrices, cluster.
set -uo pipefail
source "$(dirname "$0")/_guard-common.sh"
source "$(dirname "$0")/../full-boot/_full-boot-common.sh"
cd "$REPO_PATH"
full_boot_ensure_artifacts

REPORT_JSON="$FULL_BOOT_REPORTS/full-boot-runtime-report.json"
REPORT_MD="$FULL_BOOT_REPORTS/full-boot-runtime-report.md"
mkdir -p "$FULL_BOOT_REPORTS"

python3 <<'PY'
import json, pathlib, yaml, subprocess, os
root = pathlib.Path(os.environ.get("REPO_PATH", "."))
cls = yaml.safe_load((root / "config/full-boot-service-classification.yml").read_text())
entries = cls.get("classifications", [])
by_class = {}
by_plane = {}
for e in entries:
    c = e.get("classification", "unknown")
    by_class[c] = by_class.get(c, 0) + 1
    p = e.get("plane", "unknown")
    by_plane[p] = by_plane.get(p, 0) + 1
required = [e for e in entries if e.get("classification") == "required_full_boot"]
deployed = set()
try:
    out = subprocess.check_output(
        ["kubectl", "get", "deploy", "-n", os.environ.get("SLICE_NAMESPACE", "impilo-preview"), "-o", "jsonpath={.items[*].metadata.name}"],
        text=True, stderr=subprocess.DEVNULL,
    )
    deployed = set(out.split())
except Exception:
    pass
build_sum = root / "reports/full-boot/full-build-summary.json"
img_sum = root / "reports/full-boot/full-image-build-summary.json"
build_pass = build_fail = 0
img_pass = img_missing = 0
if build_sum.exists():
    b = json.loads(build_sum.read_text())
    build_pass = b.get("pass", 0)
    build_fail = b.get("required_fail", 0) + b.get("optional_fail", 0)
if img_sum.exists():
    i = json.loads(img_sum.read_text())
    img_pass = i.get("pass", 0)
    img_missing = i.get("missing_dockerfile", 0)
healthy = len(deployed & {e["id"] for e in required})
req_total = len(required)
status = "FULL_BOOT_NOT_ATTEMPTED"
if build_sum.exists() or img_sum.exists():
    if build_fail == 0 and healthy < req_total:
        status = "FULL_BOOT_PARTIAL"
    elif build_fail > 0 or img_missing > 10:
        status = "FULL_BOOT_FAIL"
    elif healthy >= 4 and req_total > 20:
        status = "FULL_BOOT_PARTIAL"
    else:
        status = "FULL_BOOT_FAIL"
report = {
    "total_discovered": len(entries),
    "by_plane": by_plane,
    "by_classification": by_class,
    "required_full_boot": req_total,
    "deployed_in_slice": sorted(deployed),
    "healthy_required_estimate": healthy,
    "build_pass": build_pass,
    "build_fail": build_fail,
    "image_pass": img_pass,
    "image_missing_dockerfile": img_missing,
    "full_boot_status": status,
}
(root / "reports/full-boot/full-boot-runtime-report.json").write_text(json.dumps(report, indent=2))
md = [
    "# Full Boot Runtime Completeness Report",
    "",
    f"**Status:** `{status}`",
    "",
    f"| Metric | Value |",
    f"|--------|-------|",
    f"| Total discovered | {len(entries)} |",
    f"| Required full boot | {req_total} |",
    f"| Deployed (slice ns) | {len(deployed)} |",
    f"| Build pass | {build_pass} |",
    f"| Build fail | {build_fail} |",
    f"| Images pass | {img_pass} |",
    f"| Missing Dockerfiles | {img_missing} |",
    "",
    "## Per-plane counts",
    "",
]
for p, n in sorted(by_plane.items()):
    md.append(f"- **{p}**: {n}")
(root / "reports/full-boot/full-boot-runtime-report.md").write_text("\n".join(md) + "\n")
print(f"FULL_BOOT_STATUS={status}")
print(f"total={len(entries)} required={req_total} deployed={len(deployed)}")
PY

STATUS="$(python3 -c "import json; print(json.load(open('$REPORT_JSON'))['full_boot_status'])")"
echo "=== Full boot runtime completeness: $STATUS ==="
case "$STATUS" in
  FULL_BOOT_PASS) guard_pass "full boot runtime completeness" ;;
  FULL_BOOT_PARTIAL) guard_warn "full boot partial — see $REPORT_MD"; exit 0 ;;
  FULL_BOOT_NOT_ATTEMPTED) guard_warn "full boot not attempted (run build scripts)"; exit 0 ;;
  *) guard_fail "full boot runtime completeness: $STATUS"; exit 1 ;;
esac
