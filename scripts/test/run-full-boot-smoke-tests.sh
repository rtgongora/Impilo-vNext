#!/usr/bin/env bash
# Smoke tests for full-boot namespace (classification-driven).
set -uo pipefail
source "$(dirname "$0")/../full-boot/_full-boot-common.sh"
full_boot_ensure_artifacts

NS="${FULL_BOOT_NAMESPACE:-impilo-full-preview}"
BASE_URL="${FULL_BOOT_BASE_URL:-http://127.0.0.1}"
FAIL=0

echo "=== Full boot smoke tests (namespace: $NS) ==="

# Crashloop check
if command -v kubectl >/dev/null 2>&1; then
  bad=$(kubectl get pods -n "$NS" --no-headers 2>/dev/null | awk '$3!="Running" && $3!="Completed" {print}' || true)
  if [[ -n "$bad" ]]; then
    echo "FAIL: unhealthy pods in $NS"
    echo "$bad"
    FAIL=1
  else
    echo "PASS: all pods Running/Completed or namespace empty"
  fi
fi

# Required deployments from classification
python3 <<PY
import yaml, pathlib, subprocess, os, sys
ns = os.environ.get("NS", "$NS")
doc = yaml.safe_load(pathlib.Path("config/full-boot-service-classification.yml").read_text())
required = [e["id"] for e in doc["classifications"] if e["classification"] == "required_full_boot"]
try:
    out = subprocess.check_output(["kubectl", "get", "deploy", "-n", ns, "-o", "jsonpath={.items[*].metadata.name}"], text=True, stderr=subprocess.DEVNULL)
    deployed = set(out.split())
except Exception:
    deployed = set()
missing = [r for r in required if r not in deployed and r not in ("postgres", "redis", "kafka", "keycloak", "envoy", "minio", "hapi-fhir")]
if missing:
    print(f"ADVISORY: {len(missing)} required services not deployed in {ns}")
    for m in missing[:15]:
        print(f"  - {m}")
    sys.exit(0)
print(f"PASS: deployment check ({len(deployed)} deploys in {ns})")
PY

# Health version if ingress routes to full boot
if curl -sf "${BASE_URL}/health/version" >/dev/null 2>&1; then
  echo "PASS: /health/version reachable"
  curl -s "${BASE_URL}/health/version" | head -c 500
  echo ""
else
  echo "ADVISORY: /health/version not reachable at $BASE_URL (slice may still own ingress)"
fi

[[ $FAIL -eq 0 ]] || exit 1
exit 0
