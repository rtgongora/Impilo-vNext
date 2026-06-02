#!/usr/bin/env bash
# Full boot runtime smoke — requires deployments Ready, not merely present.
set -uo pipefail
source "$(dirname "$0")/../full-boot/_full-boot-common.sh"
full_boot_ensure_artifacts

NS="${FULL_BOOT_NAMESPACE:-impilo-full-preview}"
# Use port-forward target only; do not hit public slice URL unless explicitly set.
BASE_URL="${FULL_BOOT_BASE_URL:-}"
REPORT="$FULL_BOOT_REPORTS/full-boot-smoke-report.json"
mkdir -p "$FULL_BOOT_REPORTS"
FAIL=0

echo "=== Full boot runtime smoke tests (namespace: $NS) ==="
echo "NOTE: Checks deployment readiness — not image-verify v-* pods."

export NS BASE_URL REPO_PATH
python3 <<'PY' > "$REPORT"
import json
import os
import pathlib
import subprocess
import sys

import yaml

root = pathlib.Path(os.environ.get("REPO_PATH", "."))
ns = os.environ.get("NS", "impilo-full-preview")
base = os.environ.get("BASE_URL", "").strip()
doc = yaml.safe_load((root / "config/full-boot-service-classification.yml").read_text())
required = [e for e in doc["classifications"] if e.get("classification") == "required_full_boot"]
req_ids = [e["id"] for e in required]


def kubectl_json(args):
    try:
        out = subprocess.check_output(["kubectl", *args], text=True, stderr=subprocess.DEVNULL)
        return json.loads(out)
    except Exception:
        return None


def deploy_health():
    data = kubectl_json(["get", "deploy", "-n", ns, "-o", "json"]) or {"items": []}
    by_name = {}
    for item in data.get("items", []):
        name = item["metadata"]["name"]
        spec_replicas = item.get("spec", {}).get("replicas", 1) or 1
        status = item.get("status", {})
        ready = status.get("readyReplicas") or 0
        available = status.get("availableReplicas") or 0
        by_name[name] = {
            "replicas": spec_replicas,
            "ready": ready,
            "available": available,
            "healthy": ready >= spec_replicas and available >= spec_replicas,
        }
    return by_name


deploys = deploy_health()
pods_raw = subprocess.check_output(
    ["kubectl", "get", "pods", "-n", ns, "--no-headers"], text=True, stderr=subprocess.DEVNULL
).strip()

runtime_pods = []
verify_pods = []
crash = []
pending = []
not_ready = []

for line in pods_raw.splitlines():
    parts = line.split()
    if len(parts) < 3:
        continue
    name, ready_col, status = parts[0], parts[1], parts[2]
    bucket = verify_pods if name.startswith("v-") else runtime_pods
    bucket.append({"name": name, "ready": ready_col, "status": status})
    if name.startswith("v-"):
        continue
    if "CrashLoopBackOff" in status or status == "Error":
        crash.append(name)
    elif status == "Pending":
        pending.append(name)
    elif not (ready_col.endswith("/1") and ready_col.split("/")[0] == ready_col.split("/")[1]):
        if status not in ("Completed", "Succeeded"):
            not_ready.append(name)

missing_deploy = [r for r in req_ids if r not in deploys]
unhealthy_deploy = []
for rid in req_ids:
    d = deploys.get(rid)
    if not d:
        continue
    if not d["healthy"]:
        unhealthy_deploy.append(
            {"id": rid, "ready": d["ready"], "available": d["available"], "replicas": d["replicas"]}
        )

# Jobs/hooks
jobs = kubectl_json(["get", "jobs", "-n", ns, "-o", "json"]) or {"items": []}
job_status = []
for j in jobs.get("items", []):
    name = j["metadata"]["name"]
    succeeded = j.get("status", {}).get("succeeded", 0) or 0
    failed = j.get("status", {}).get("failed", 0) or 0
    active = j.get("status", {}).get("active", 0) or 0
    job_status.append(
        {"name": name, "succeeded": succeeded, "failed": failed, "active": active, "ok": succeeded >= 1}
    )

version_ok = False
version_body = None
version_note = "skipped — set FULL_BOOT_BASE_URL to port-forward BFF (e.g. http://127.0.0.1:18160)"
if base:
    import urllib.request

    try:
        with urllib.request.urlopen(f"{base.rstrip('/')}/health/version", timeout=5) as resp:
            version_body = resp.read(500).decode()
            version_ok = resp.status == 200
            version_note = "checked via FULL_BOOT_BASE_URL"
    except Exception as ex:
        version_note = f"fail: {ex}"

report = {
    "namespace": ns,
    "required_count": len(req_ids),
    "deployments": deploys,
    "missing_deployments": missing_deploy,
    "unhealthy_deployments": unhealthy_deploy,
    "runtime_pods_count": len(runtime_pods),
    "verify_pods_ignored": len(verify_pods),
    "crashloop": crash,
    "pending": pending,
    "not_ready_pods": not_ready,
    "jobs": job_status,
    "health_version_reachable": version_ok,
    "health_version_snippet": version_body,
    "health_version_note": version_note,
    "pass": len(missing_deploy) == 0 and len(unhealthy_deploy) == 0 and len(crash) == 0 and len(pending) == 0,
}
print(json.dumps(report, indent=2))
sys.exit(0 if report["pass"] else 1)
PY
PY_EXIT=$?

python3 -c "
import json,sys
r=json.load(open('$REPORT'))
print(f\"Deployments missing: {len(r['missing_deployments'])}\")
print(f\"Deployments not ready: {len(r['unhealthy_deployments'])}\")
for u in r['unhealthy_deployments'][:12]:
    print(f\"  FAIL deploy {u['id']} ready={u['ready']}/{u['replicas']}\")
if r['crashloop']:
    print('CrashLoopBackOff:', ', '.join(r['crashloop'][:12]))
if r['pending']:
    print('Pending:', ', '.join(r['pending'][:12]))
print('health/version:', r['health_version_note'])
print('SMOKE_PASS' if r['pass'] else 'SMOKE_FAIL')
"

if [[ $PY_EXIT -ne 0 ]]; then
  FAIL=1
fi

bash scripts/guard/check-full-boot-runtime-completeness.sh >/dev/null 2>&1 || true
status="$(python3 -c "import json; print(json.load(open('$FULL_BOOT_REPORTS/full-boot-runtime-report.json'))['full_boot_status'])" 2>/dev/null || echo unknown)"
echo "Runtime completeness gate: $status"

[[ $FAIL -eq 0 ]] || exit 1
exit 0
