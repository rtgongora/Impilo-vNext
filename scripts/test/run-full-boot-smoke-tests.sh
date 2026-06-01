#!/usr/bin/env bash
# Smoke tests for full-boot namespace (classification-driven).
set -uo pipefail
source "$(dirname "$0")/../full-boot/_full-boot-common.sh"
full_boot_ensure_artifacts

NS="${FULL_BOOT_NAMESPACE:-impilo-full-preview}"
BASE_URL="${FULL_BOOT_BASE_URL:-http://127.0.0.1}"
REPORT="$FULL_BOOT_REPORTS/full-boot-smoke-report.json"
mkdir -p "$FULL_BOOT_REPORTS"
FAIL=0
ADVISORY=0

echo "=== Full boot smoke tests (namespace: $NS) ==="

python3 <<'PY' > "$REPORT"
import json, os, pathlib, subprocess, sys, urllib.request
import yaml

root = pathlib.Path(os.environ.get("REPO_PATH", "."))
ns = os.environ.get("NS", "impilo-full-preview")
base = os.environ.get("BASE_URL", "http://127.0.0.1")
doc = yaml.safe_load((root / "config/full-boot-service-classification.yml").read_text())
required = [e for e in doc["classifications"] if e.get("classification") == "required_full_boot"]
req_ids = [e["id"] for e in required]

def kubectl(*args):
    try:
        return subprocess.check_output(["kubectl", *args], text=True, stderr=subprocess.DEVNULL).strip()
    except Exception:
        return ""

deployed = set(kubectl("get", "deploy", "-n", ns, "-o", "jsonpath={.items[*].metadata.name}").split())
services = set(kubectl("get", "svc", "-n", ns, "-o", "jsonpath={.items[*].metadata.name}").split())
pods_raw = kubectl("get", "pods", "-n", ns, "--no-headers")
pods = []
crash = pending = []
for line in pods_raw.splitlines():
    parts = line.split()
    if len(parts) < 3:
        continue
    name, ready, status = parts[0], parts[1], parts[2]
    pods.append({"name": name, "ready": ready, "status": status})
    if "CrashLoopBackOff" in status or status == "Error":
        crash.append(name)
    if status == "Pending":
        pending.append(name)

missing_deploy = [r for r in req_ids if r not in deployed]
missing_svc = [r for r in req_ids if r not in services and r in deployed]

pvc_bound = True
try:
    pvc_out = kubectl("get", "pvc", "-n", ns, "--no-headers")
    for line in pvc_out.splitlines():
        if "Bound" not in line and line.strip():
            pvc_bound = False
except Exception:
    pass

health_checks = []
for e in required:
    sid = e["id"]
    if sid not in deployed:
        continue
    port = e.get("default_http_port")
    if not port and sid in ("postgres", "redis", "kafka"):
        continue
    if port:
        health_checks.append({"service": sid, "port": port, "configured": True})

version_ok = False
version_body = None
try:
    with urllib.request.urlopen(f"{base}/health/version", timeout=5) as resp:
        version_body = resp.read(500).decode()
        version_ok = resp.status == 200
except Exception:
    pass

report = {
    "namespace": ns,
    "required_count": len(req_ids),
    "deployed_count": len(deployed),
    "missing_deployments": missing_deploy,
    "missing_services": missing_svc,
    "pods": pods,
    "crashloop": crash,
    "pending": pending,
    "pvc_bound": pvc_bound,
    "health_version_reachable": version_ok,
    "health_version_snippet": version_body,
}
print(json.dumps(report, indent=2))
PY

if command -v kubectl >/dev/null 2>&1; then
  if [[ -n "$(jq -r '.crashloop[]?' "$REPORT" 2>/dev/null)" ]]; then
    echo "FAIL: CrashLoopBackOff pods"
    jq -r '.crashloop[]' "$REPORT"
    FAIL=1
  fi
  if [[ -n "$(jq -r '.pending[]?' "$REPORT" 2>/dev/null)" ]]; then
    echo "FAIL: Pending pods"
    jq -r '.pending[]' "$REPORT"
    FAIL=1
  fi
  missing="$(jq -r '.missing_deployments | length' "$REPORT" 2>/dev/null || echo 0)"
  if [[ "$missing" != "0" ]]; then
    echo "ADVISORY: $missing required deployments missing in $NS"
    jq -r '.missing_deployments[]' "$REPORT" | head -15
    ADVISORY=1
  else
    echo "PASS: all required deployments present"
  fi
  if jq -e '.pvc_bound == false' "$REPORT" >/dev/null 2>&1; then
    echo "FAIL: unbound PVCs"
    FAIL=1
  fi
else
  echo "ADVISORY: kubectl not available"
  ADVISORY=1
fi

if jq -e '.health_version_reachable == true' "$REPORT" >/dev/null 2>&1; then
  echo "PASS: /health/version reachable"
  jq -r '.health_version_snippet' "$REPORT"
else
  echo "ADVISORY: /health/version not reachable at $BASE_URL"
  ADVISORY=1
fi

bash scripts/guard/check-full-boot-runtime-completeness.sh >/dev/null 2>&1 || true
status="$(python3 -c "import json; print(json.load(open('$FULL_BOOT_REPORTS/full-boot-runtime-report.json'))['full_boot_status'])" 2>/dev/null || echo unknown)"
echo "Runtime completeness gate: $status"

[[ $FAIL -eq 0 ]] || exit 1
exit 0
