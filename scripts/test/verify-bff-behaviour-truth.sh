#!/usr/bin/env bash
# verify-bff-behaviour-truth.sh
#
# All of vNext is accountable. Deployment truth is the running estate, not the deployment story.
# For experience-bff, /health/version alone is NOT deployment truth: a new commit in metadata
# can coexist with old endpoint behaviour if the running image is stale. This verifies CHANGED
# endpoint BEHAVIOUR, not just version metadata.
#
# READ-ONLY (HTTP GET + read-only kubectl/guard). No cluster mutation.
#
# Probe table (extend per commit): each row is "METHOD PATH EXPECTED_STATUS DESCRIPTION".
# Default probe = the settings/display endpoint added in commit 74478954 (was 500, now 200).
#
# Usage:
#   scripts/test/verify-bff-behaviour-truth.sh [--url URL] [--ns NS] [--skip-downstream]
#
# Exit codes: 0 pass | 1 fail | 2 usage
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO_PATH" 2>/dev/null || true

URL="${PREVIEW_URL:-http://41.57.127.235}"
NS="${FULL_BOOT_NAMESPACE:-impilo-full-preview}"
SKIP_DOWNSTREAM=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --url) URL="${2:-}"; shift 2 ;;
    --ns) NS="${2:-}"; shift 2 ;;
    --skip-downstream) SKIP_DOWNSTREAM=1; shift ;;
    -h|--help) grep -E '^#( |$)' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

command -v curl >/dev/null 2>&1 || { echo "BFF_BEHAVIOUR_TRUTH: UNKNOWN (curl not available)"; exit 0; }

echo "== BFF behaviour truth: $URL =="
fail=0

# 1) /health/version: capture commit + assert environment.
hv="$(curl -fsSL --max-time 15 "$URL/health/version" 2>/dev/null || true)"
if [[ -z "$hv" ]]; then
  echo "BFF_BEHAVIOUR_TRUTH: FAIL (/health/version unreachable)"
  exit 1
fi
hv_commit="$(printf '%s' "$hv" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("commit",""))' 2>/dev/null || true)"
hv_env="$(printf '%s' "$hv" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("environment",""))' 2>/dev/null || true)"
echo "health/version: env=$hv_env commit=${hv_commit:0:12}"
if [[ -n "$hv_env" && "$hv_env" != "full-preview" ]]; then
  echo "  WARN environment is '$hv_env' (expected full-preview)"
fi

# 2) Running BFF image digest (read-only) for the truth record.
if command -v kubectl >/dev/null 2>&1; then
  bff_img="$(kubectl get pod -n "$NS" -l app=experience-bff -o jsonpath='{.items[0].status.containerStatuses[0].imageID}' 2>/dev/null || true)"
  [[ -n "$bff_img" ]] && echo "experience-bff pod imageID: ${bff_img##*@}"
fi

# 3) Downstream mapping guard (no localhost downstream).
if [[ "$SKIP_DOWNSTREAM" != "1" && -x scripts/guard/check-bff-downstream-mappings.sh ]]; then
  if bash scripts/guard/check-bff-downstream-mappings.sh >/tmp/bff-downstream-$$.log 2>&1; then
    echo "downstream mappings: PASS"
  else
    echo "downstream mappings: FAIL (see output)"; tail -5 /tmp/bff-downstream-$$.log | sed 's/^/    /'
    fail=1
  fi
  rm -f /tmp/bff-downstream-$$.log
fi

# 4) Changed-endpoint behaviour probes. Trust headers required by the BFF.
hdr=(-H "X-Tenant-ID: default" -H "X-Pod-ID: preview"
     -H "X-Request-ID: bff-truth-$$" -H "X-Correlation-ID: bff-truth-$$"
     -H "X-Actor-ID: actor-truth")

# probe row format: METHOD|PATH|EXPECTED_STATUS|MUST_CONTAIN|DESCRIPTION
PROBES=(
  "GET|/internal/v1/settings/display|200|display|settings/display added in 74478954 (was 500)"
)

for row in "${PROBES[@]}"; do
  IFS='|' read -r method path expected must desc <<<"$row"
  body="$(curl -fsS --max-time 15 -X "$method" "${hdr[@]}" "$URL$path" 2>/dev/null || true)"
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 -X "$method" "${hdr[@]}" "$URL$path" 2>/dev/null || echo 000)"
  ok=1
  [[ "$code" == "$expected" ]] || ok=0
  if [[ -n "$must" ]] && ! printf '%s' "$body" | grep -qiF "$must"; then ok=0; fi
  if [[ "$ok" == "1" ]]; then
    echo "  PROBE OK   $method $path -> $code ($desc)"
  else
    echo "  PROBE FAIL $method $path -> $code expected $expected containing '$must' ($desc)"
    echo "             /health/version reports commit ${hv_commit:0:12} but endpoint behaviour is stale/old."
    fail=1
  fi
done

if [[ "$fail" == "1" ]]; then
  echo "BFF_BEHAVIOUR_TRUTH: FAIL (metadata may be new but behaviour is stale or downstream broken)"
  exit 1
fi
echo "BFF_BEHAVIOUR_TRUTH: PASS (changed endpoint behaviour matches deployed commit)"
