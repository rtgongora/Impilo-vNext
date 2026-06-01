#!/usr/bin/env bash
# Full-boot runtime completeness gate — runtime image strategy doctrine.
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
cls = yaml.load((root / "config/full-boot-service-classification.yml").read_text(), Loader=yaml.SafeLoader)
entries = cls.get("classifications", [])
by_class = {}
by_plane = {}
by_strategy = {}
for e in entries:
    c = e.get("full_boot_classification", e.get("classification", "unknown"))
    by_class[c] = by_class.get(c, 0) + 1
    p = e.get("plane", "unknown")
    by_plane[p] = by_plane.get(p, 0) + 1
    s = e.get("image_strategy", "unknown")
    by_strategy[s] = by_strategy.get(s, 0) + 1

required = [e for e in entries if e.get("full_boot_classification") == "required_full_boot" or e.get("classification") == "required_full_boot"]
runtime_required = [e for e in entries if e.get("image_required") or e.get("runtime_image_required")]
valid_strategy = [
    e for e in required
    if e.get("image_strategy_status") == "valid"
    and e.get("image_strategy") != "missing-required-image-strategy"
]
missing_strategy = [e for e in required if e.get("image_strategy") == "missing-required-image-strategy" or e.get("image_strategy_status") == "missing"]
unknown_review = [e for e in entries if e.get("image_strategy") == "unknown-needs-review"]
not_required_ok = [e for e in entries if str(e.get("image_strategy", "")).startswith("not-required")]

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
strategy_sum = root / "reports/full-boot/image-strategy-summary.json"
build_pass = build_fail = 0
img_pass = img_fail = img_missing_strategy = blocking_failures = 0
strategy_counts = {}
if build_sum.exists():
    b = json.loads(build_sum.read_text())
    build_pass = b.get("pass", 0)
    build_fail = b.get("required_fail", 0) + b.get("optional_fail", 0)
for p in (img_sum, strategy_sum):
    if p.exists():
        i = json.loads(p.read_text())
        img_pass = i.get("image_build_pass_count", i.get("pass", 0))
        img_fail = i.get("image_build_fail_count", i.get("fail", 0))
        img_missing_strategy = i.get("missing_required_image_strategy_count", i.get("missing_required_image_strategy", 0))
        blocking_failures = i.get("blocking_failure_count", i.get("required_fail", 0))
        strategy_counts = {k: i.get(k) for k in i if k.endswith("_count")}

failing_builds = []
if (root / "reports/full-boot/image-logs").exists():
    for log in (root / "reports/full-boot/image-logs").glob("*.log"):
        text = log.read_text(errors="ignore")
        ent = next((x for x in required if x["id"] == log.stem), None)
        if not ent:
            continue
        if "FAIL impilo" in text or text.strip().endswith("(dockerfile)") and "FAIL" in text.splitlines()[-1]:
            failing_builds.append(log.stem)
        elif text.strip().splitlines()[-1].startswith("FAIL") if text.strip() else False:
            failing_builds.append(log.stem)

healthy = len(deployed & {e["id"] for e in required})
req_total = len(required)
status = "FULL_BOOT_NOT_ATTEMPTED"
blocking = len(missing_strategy) > 0 or img_missing_strategy > 0 or blocking_failures > 0 or len(failing_builds) > 0
if build_sum.exists() or img_sum.exists():
    if blocking:
        status = "FULL_BOOT_FAIL"
    elif build_fail > 0:
        status = "FULL_BOOT_FAIL"
    elif healthy < req_total:
        status = "FULL_BOOT_PARTIAL"
    else:
        status = "FULL_BOOT_PARTIAL"

report = {
    "total_discovered": len(entries),
    "by_plane": by_plane,
    "by_classification": by_class,
    "by_image_strategy": by_strategy,
    "required_full_boot": req_total,
    "required_with_valid_image_strategy": len(valid_strategy),
    "runtime_image_required_count": len(runtime_required),
    "missing_required_image_strategy": [e["id"] for e in missing_strategy],
    "missing_required_image_strategy_count": len(missing_strategy),
    "failing_required_image_builds": failing_builds,
    "unknown_needs_review": [e["id"] for e in unknown_review],
    "not_required_components_count": len(not_required_ok),
    "official_image_chart_defined": sum(
        1 for e in entries if e.get("official_image") or e.get("official_chart")
    ),
    "deployed_in_slice": sorted(deployed),
    "healthy_required_estimate": healthy,
    "build_pass": build_pass,
    "build_fail": build_fail,
    "image_pass": img_pass,
    "image_fail": img_fail,
    "blocking_failure_count": blocking_failures,
    "image_strategy_summary": strategy_counts,
    "full_boot_status": status,
}
(root / "reports/full-boot/full-boot-runtime-report.json").write_text(json.dumps(report, indent=2))
md = [
    "# Full Boot Runtime Completeness Report",
    "",
    f"**Status:** `{status}`",
    "",
    "> Checks **runtime image strategy**, not Dockerfile presence alone.",
    "",
    "| Metric | Value |",
    "|--------|-------|",
    f"| Total discovered | {len(entries)} |",
    f"| Required full boot | {req_total} |",
    f"| Required with valid image strategy | {len(valid_strategy)} |",
    f"| Runtime image required | {len(runtime_required)} |",
    f"| Missing required strategy | {len(missing_strategy)} |",
    f"| Failing required image builds | {len(failing_builds)} |",
    f"| Unknown needs review | {len(unknown_review)} |",
    f"| Not-required classified | {len(not_required_ok)} |",
    f"| Official image/chart defined | {report['official_image_chart_defined']} |",
    f"| Image pass / fail | {img_pass} / {img_fail} |",
    f"| Blocking failures | {blocking_failures} |",
    "",
]
if missing_strategy:
    md.append("## Missing required image strategy")
    for e in missing_strategy:
        md.append(f"- `{e['id']}` ({e.get('plane')})")
    md.append("")
if failing_builds:
    md.append("## Failing required image builds")
    for s in failing_builds:
        md.append(f"- `{s}`")
    md.append("")
md.append("## Image strategies")
for s, n in sorted(by_strategy.items()):
    md.append(f"- **{s}**: {n}")
(root / "reports/full-boot/full-boot-runtime-report.md").write_text("\n".join(md) + "\n")
print(f"FULL_BOOT_STATUS={status}")
print(f"valid_strategy={len(valid_strategy)}/{req_total} missing={len(missing_strategy)} failing_builds={len(failing_builds)}")
PY

STATUS="$(python3 -c "import json; print(json.load(open('$REPORT_JSON'))['full_boot_status'])")"
echo "=== Full boot runtime completeness: $STATUS ==="
case "$STATUS" in
  FULL_BOOT_PASS) guard_pass "full boot runtime completeness" ;;
  FULL_BOOT_PARTIAL) guard_warn "full boot partial — see $REPORT_MD"; exit 0 ;;
  FULL_BOOT_NOT_ATTEMPTED) guard_warn "full boot not attempted (run build scripts)"; exit 0 ;;
  *) guard_fail "full boot runtime completeness: $STATUS"; exit 1 ;;
esac
