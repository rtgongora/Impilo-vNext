#!/usr/bin/env bash
# Full-boot runtime completeness gate — images, Helm deployability, infra, deployment, health.
set -uo pipefail
source "$(dirname "$0")/_guard-common.sh"
source "$(dirname "$0")/../full-boot/_full-boot-common.sh"
cd "$REPO_PATH"
full_boot_ensure_artifacts

# Helm audit needs fullBootServices from generated overlay (not values-full-preview.yaml alone).
if [[ ! -f "$REPO_PATH/deploy/helm/impilo-vnext/values-full-preview-runtime.generated.yaml" ]]; then
  node "$REPO_PATH/scripts/full-boot/generate-full-preview-runtime-values.mjs" >/dev/null 2>&1 || true
fi

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
            name = parts[0]
            if name.startswith("v-"):
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


def deploy_ready_names(ns):
    try:
        data = json.loads(
            subprocess.check_output(["kubectl", "get", "deploy", "-n", ns, "-o", "json"], text=True, stderr=subprocess.DEVNULL)
        )
    except Exception:
        return set(), set()
    all_names = set()
    ready_names = set()
    for item in data.get("items", []):
        name = item["metadata"]["name"]
        all_names.add(name)
        spec_r = item.get("spec", {}).get("replicas", 1) or 1
        ready = item.get("status", {}).get("readyReplicas") or 0
        avail = item.get("status", {}).get("availableReplicas") or 0
        if ready >= spec_r and avail >= spec_r:
            ready_names.add(name)
    return all_names, ready_names

deployed_full, ready_deploys = deploy_ready_names(full_ns)
deployed_slice = ns_deployments(slice_ns)
pod_total, pod_ready, pod_crash, pod_pending = ns_ready_pods(full_ns)

# Full RUNTIME ESTATE = all runtime_k8s_microservice + dedicated bff/shell. One estate means
# all deployable vNext services, not just the required spine. Waves are sequencing, not
# optionality: a subset that is healthy is PARTIAL_WAVE_PASS, never FULL_ESTATE_PASS.
runtime_estate = sorted({
    e["id"] for e in entries if e.get("deployment_lane") == "runtime_k8s_microservice"
} | {"experience-bff", "one-ui-shell"})
estate_total = len(runtime_estate)
estate_ready = len(set(runtime_estate) & ready_deploys)
estate_deployed = len(set(runtime_estate) & deployed_full)
estate_missing = sorted(set(runtime_estate) - deployed_full)
estate_fully_ready = estate_ready >= estate_total and not estate_missing

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
required_ids = {e["id"] for e in required}
healthy_required = len(ready_deploys & required_ids)
deployments_ready = healthy_required >= req_total and len(required_ids - deployed_full) == 0
runtime_healthy = (
    namespace_deployed
    and deployments_ready
    and pod_crash == 0
    and pod_pending == 0
)

reason = "deployment not attempted"
status = "FULL_BOOT_NOT_ATTEMPTED"

if images_ready or img_sum.exists() or build_sum.exists():
    if not namespace_deployed:
        status = "FULL_BOOT_PARTIAL"
        if not images_ready or blocking_failures > 0 or len(failing_builds) > 0:
            reason = "deployment not attempted (image/build prep incomplete)"
        else:
            reason = "deployment not attempted"
    elif not images_ready or blocking_failures > 0 or len(failing_builds) > 0:
        status = "FULL_BOOT_FAIL"
        reason = "required images or strategy blocking"
    elif not runtime_healthy:
        status = "FULL_BOOT_PARTIAL"
        reason = "deployed but not all required deployments ready"
    elif not helm_deployability_ready:
        status = "FULL_BOOT_PARTIAL"
        reason = "helm deployability partial"
    else:
        status = "FULL_BOOT_PASS"
        reason = "images, helm, and runtime healthy"

# Estate status (canonical). FULL_BOOT_* retained as warned backward-compat aliases.
# A deployment is not complete until the FULL estate is running, aligned, healthy, current.
estate_status = "FAIL"
estate_reason = reason
if status == "FULL_BOOT_NOT_ATTEMPTED":
    estate_status = "FULL_BOOT_NOT_ATTEMPTED"
    estate_reason = "deployment not attempted"
elif status == "FULL_BOOT_FAIL":
    estate_status = "FAIL"
elif status in ("FULL_BOOT_PARTIAL",):
    estate_status = "PARTIAL_WAVE_PASS"
    estate_reason = f"{reason}; estate {estate_ready}/{estate_total} ready"
elif status == "FULL_BOOT_PASS":
    if estate_fully_ready:
        estate_status = "FULL_ESTATE_PASS"
        estate_reason = "full estate deployed, ready, and healthy"
    else:
        estate_status = "PARTIAL_WAVE_PASS"
        estate_reason = (
            f"required spine healthy but estate only {estate_ready}/{estate_total} ready "
            f"(missing/not-ready: {len(estate_missing)}) - waves are sequencing, not full estate"
        )

# Consume runtime image truth artifact if present: stale non-exempt services block FULL_ESTATE_PASS.
truth_path = root / "reports/full-boot/runtime-image-truth.json"
estate_stale_services = []
if truth_path.exists():
    try:
        estate_stale_services = json.loads(truth_path.read_text()).get("stale_non_exempt", [])
    except Exception:
        estate_stale_services = []
    if estate_stale_services and estate_status == "FULL_ESTATE_PASS":
        estate_status = "FAIL"
        estate_reason = f"runtime image truth: {len(estate_stale_services)} stale non-exempt service(s)"

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
    "deployments_ready_count": healthy_required,
    "deployments_ready": deployments_ready,
    "runtime_healthy": runtime_healthy,
    "build_pass": build_pass,
    "build_fail": build_fail,
    "image_pass": img_pass,
    "image_fail": img_fail,
    "blocking_failure_count": blocking_failures,
    "image_strategy_summary": strategy_counts,
    "full_boot_status": status,
    "full_boot_reason": reason,
    "estate_status": estate_status,
    "estate_reason": estate_reason,
    "runtime_estate_total": estate_total,
    "runtime_estate_ready": estate_ready,
    "runtime_estate_deployed": estate_deployed,
    "runtime_estate_missing": estate_missing,
    "runtime_estate_missing_count": len(estate_missing),
    "estate_stale_services": estate_stale_services,
}
(root / "reports/full-boot/full-boot-runtime-report.json").write_text(json.dumps(report, indent=2))
md = [
    "# Full Boot Runtime Completeness Report",
    "",
    "> All of vNext is accountable. One estate means all deployable vNext services. Waves are sequencing, not optionality.",
    "",
    f"**Estate status:** `{estate_status}`",
    f"**Estate reason:** {estate_reason}",
    f"**Runtime estate ready:** {estate_ready}/{estate_total} (missing/not-ready: {len(estate_missing)})",
    f"**Legacy full-boot status (alias):** `{status}` — {reason}",
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
print(f"ESTATE_STATUS={estate_status}")
print(f"FULL_BOOT_STATUS={status}  (legacy alias)")
print(f"estate_ready={estate_ready}/{estate_total} reason={estate_reason}")
print(f"reason={reason} images_ready={images_ready} helm_ready={helm_ready}/{req_total} deployed={namespace_deployed}")
PY

ESTATE_STATUS="$(python3 -c "import json; print(json.load(open('$REPORT_JSON'))['estate_status'])")"
STATUS="$(python3 -c "import json; print(json.load(open('$REPORT_JSON'))['full_boot_status'])")"
echo "=== vNext runtime estate completeness: $ESTATE_STATUS (legacy: $STATUS) ==="
case "$ESTATE_STATUS" in
  FULL_ESTATE_PASS) guard_pass "vNext full runtime estate complete" ;;
  PARTIAL_WAVE_PASS) guard_warn "partial wave (not full estate) — see $REPORT_MD"; exit 0 ;;
  DEBUG_SLICE_PASS) guard_warn "debug slice only (not full estate) — see $REPORT_MD"; exit 0 ;;
  FULL_BOOT_NOT_ATTEMPTED) guard_warn "full estate not attempted"; exit 0 ;;
  *) guard_fail "vNext runtime estate completeness: $ESTATE_STATUS"; exit 1 ;;
esac
