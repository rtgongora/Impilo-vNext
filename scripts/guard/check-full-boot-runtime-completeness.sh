#!/usr/bin/env bash
# Full-boot runtime completeness gate — images, Helm deployability, infra, deployment, health.
set -uo pipefail
source "$(dirname "$0")/_guard-common.sh"
source "$(dirname "$0")/../full-boot/_full-boot-common.sh"
cd "$REPO_PATH"
full_boot_ensure_artifacts

REPORT_JSON="$FULL_BOOT_REPORTS/full-boot-runtime-report.json"
REPORT_MD="$FULL_BOOT_REPORTS/full-boot-runtime-report.md"
mkdir -p "$FULL_BOOT_REPORTS"

python3 scripts/full-boot/audit-helm-deployability.py >/dev/null 2>&1 || true

python3 <<'PY'
import json, pathlib, yaml, subprocess, os
root = pathlib.Path(os.environ.get("REPO_PATH", "."))
cls = yaml.safe_load((root / "config/full-boot-service-classification.yml").read_text())
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

full_ns = os.environ.get("FULL_BOOT_NAMESPACE", "impilo-full-preview")
slice_ns = os.environ.get("SLICE_NAMESPACE", "impilo-preview")

def ns_deployments(ns):
    try:
        out = subprocess.check_output(
            ["kubectl", "get", "deploy", "-n", ns, "-o", "jsonpath={.items[*].metadata.name}"],
            text=True, stderr=subprocess.DEVNULL,
        )
        return set(out.split()) if out.strip() else set()
    except Exception:
        return set()

def ns_ready_pods(ns):
    try:
        out = subprocess.check_output(["kubectl", "get", "pods", "-n", ns, "--no-headers"], text=True, stderr=subprocess.DEVNULL)
        ready = total = 0
        crash = pending = 0
        for line in out.splitlines():
            parts = line.split()
            if len(parts) < 3:
                continue
            total += 1
            st = parts[2]
            if "CrashLoopBackOff" in st or "Error" in st:
                crash += 1
            if st == "Pending":
                pending += 1
            if "Running" in st and "/" in parts[1]:
                a, b = parts[1].split("/", 1)
                if a == b:
                    ready += 1
        return total, ready, crash, pending
    except Exception:
        return 0, 0, 0, 0

deployed_full = ns_deployments(full_ns)
deployed_slice = ns_deployments(slice_ns)
pod_total, pod_ready, pod_crash, pod_pending = ns_ready_pods(full_ns)

helm_audit = {}
ha_path = root / "reports/full-boot/helm-deployability-audit.json"
if ha_path.exists():
    helm_audit = json.loads(ha_path.read_text())
helm_counts = helm_audit.get("counts", {})
helm_ready = helm_counts.get("helm_ready", 0)
helm_missing = helm_counts.get("helm_missing", 0) + helm_counts.get("config_missing", 0)
helm_partial = helm_counts.get("helm_partial", 0)
req_total = len(required)
helm_deployability_ready = helm_missing == 0 and helm_ready >= req_total

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

images_ready = (
    len(missing_strategy) == 0
    and img_missing_strategy == 0
    and blocking_failures == 0
    and img_fail == 0
)

failing_builds = []
if (root / "reports/full-boot/image-logs").exists():
    for log in (root / "reports/full-boot/image-logs").glob("*.log"):
        text = log.read_text(errors="ignore")
        ent = next((x for x in required if x["id"] == log.stem), None)
        if not ent:
            continue
        if "FAIL impilo" in text or (text.strip().endswith("(dockerfile)") and "FAIL" in text.splitlines()[-1]):
            failing_builds.append(log.stem)
        elif text.strip() and text.strip().splitlines()[-1].startswith("FAIL"):
            failing_builds.append(log.stem)

if failing_builds:
    images_ready = False

namespace_deployed = len(deployed_full) > 0
healthy_required = len(deployed_full & {e["id"] for e in required})
runtime_healthy = namespace_deployed and pod_crash == 0 and pod_pending == 0 and pod_ready >= healthy_required and healthy_required >= req_total

reason = "deployment not attempted"
status = "FULL_BOOT_NOT_ATTEMPTED"

if images_ready or img_sum.exists() or build_sum.exists():
    if not images_ready or blocking_failures > 0 or len(failing_builds) > 0:
        status = "FULL_BOOT_FAIL"
        reason = "required images or strategy blocking"
    elif not namespace_deployed:
        status = "FULL_BOOT_PARTIAL"
        reason = "deployment not attempted"
    elif not runtime_healthy:
        status = "FULL_BOOT_PARTIAL"
        reason = "deployed but not all required pods healthy"
    elif not helm_deployability_ready:
        status = "FULL_BOOT_PARTIAL"
        reason = "helm deployability partial"
    else:
        status = "FULL_BOOT_PASS"
        reason = "images, helm, and runtime healthy"

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
    "official_image_chart_defined": sum(1 for e in entries if e.get("official_image") or e.get("official_chart")),
    "images_ready": images_ready,
    "helm_deployability_ready": helm_deployability_ready,
    "helm_ready_count": helm_ready,
    "helm_missing_count": helm_missing,
    "helm_partial_count": helm_partial,
    "infrastructure_helm_ready": helm_ready >= 7,
    "namespace_deployed": namespace_deployed,
    "full_boot_namespace": full_ns,
    "deployed_in_full_boot": sorted(deployed_full),
    "deployed_in_slice": sorted(deployed_slice),
    "full_boot_pods_total": pod_total,
    "full_boot_pods_ready": pod_ready,
    "full_boot_pods_crashloop": pod_crash,
    "full_boot_pods_pending": pod_pending,
    "healthy_required_estimate": healthy_required,
    "runtime_healthy": runtime_healthy,
    "build_pass": build_pass,
    "build_fail": build_fail,
    "image_pass": img_pass,
    "image_fail": img_fail,
    "blocking_failure_count": blocking_failures,
    "image_strategy_summary": strategy_counts,
    "full_boot_status": status,
    "full_boot_reason": reason,
}
(root / "reports/full-boot/full-boot-runtime-report.json").write_text(json.dumps(report, indent=2))
md = [
    "# Full Boot Runtime Completeness Report",
    "",
    f"**Status:** `{status}`",
    f"**Reason:** {reason}",
    "",
    "| Phase | State |",
    "|-------|-------|",
    f"| Images ready | {images_ready} |",
    f"| Helm deployability ready | {helm_deployability_ready} ({helm_ready}/{req_total}) |",
    f"| Namespace deployed | {namespace_deployed} ({full_ns}) |",
    f"| Runtime healthy | {runtime_healthy} |",
    "",
    "| Metric | Value |",
    "|--------|-------|",
    f"| Total discovered | {len(entries)} |",
    f"| Required full boot | {req_total} |",
    f"| Image pass / fail | {img_pass} / {img_fail} |",
    f"| Helm ready / missing / partial | {helm_ready} / {helm_missing} / {helm_partial} |",
    f"| Deployed in full boot | {len(deployed_full)} |",
    f"| Pods ready / total | {pod_ready} / {pod_total} |",
    "",
]
(root / "reports/full-boot/full-boot-runtime-report.md").write_text("\n".join(md) + "\n")
print(f"FULL_BOOT_STATUS={status}")
print(f"reason={reason} images_ready={images_ready} helm_ready={helm_ready}/{req_total} deployed={namespace_deployed}")
PY

STATUS="$(python3 -c "import json; print(json.load(open('$REPORT_JSON'))['full_boot_status'])")"
echo "=== Full boot runtime completeness: $STATUS ==="
case "$STATUS" in
  FULL_BOOT_PASS) guard_pass "full boot runtime completeness" ;;
  FULL_BOOT_PARTIAL) guard_warn "full boot partial — see $REPORT_MD"; exit 0 ;;
  FULL_BOOT_NOT_ATTEMPTED) guard_warn "full boot not attempted"; exit 0 ;;
  *) guard_fail "full boot runtime completeness: $STATUS"; exit 1 ;;
esac
