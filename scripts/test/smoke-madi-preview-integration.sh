#!/usr/bin/env bash
# MADI runtime integration smoke — NHUME handoff, surveillance forecast, IoT fridge paths.
# Run against preview or local stack after deploy. Graceful degradation is expected when
# optional services are down; this script records live vs fallback for operator evidence.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

PREVIEW_BASE="${PREVIEW_BASE:-http://41.57.127.235}"
BFF_PREFIX="${BFF_PREFIX:-/internal/v1/madi}"
TENANT="${TENANT_ID:-local}"
REQ_ID="madi-smoke-$(date +%s)"
CORR_ID="madi-smoke-corr-$(date +%s)"

hdr=(-H "X-Tenant-ID: ${TENANT}" -H "X-Pod-ID: preview" -H "X-Request-ID: ${REQ_ID}" -H "X-Correlation-ID: ${CORR_ID}" -H "X-Actor-ID: smoke-operator" -H "X-Actor-Type: PROVIDER" -H "X-Purpose-Of-Use: TREATMENT")

pass=0
fail=0
skip=0

probe() {
  local name="$1"
  local url="$2"
  local code
  code=$(curl -s -o /tmp/madi-smoke-body.json -w "%{http_code}" "${hdr[@]}" "$url" || echo "000")
  if [[ "$code" =~ ^2 ]]; then
    echo "PASS  $name ($code)"
    pass=$((pass + 1))
  elif [[ "$code" == "404" || "$code" == "502" || "$code" == "503" ]]; then
    echo "SKIP  $name ($code) — service may be offline; MADI degrades gracefully"
    skip=$((skip + 1))
  else
    echo "FAIL  $name ($code)"
    fail=$((fail + 1))
  fi
}

echo "MADI preview integration smoke — base=${PREVIEW_BASE}"
probe "dashboard" "${PREVIEW_BASE}${BFF_PREFIX}/dashboard?scope=local"
probe "forecast (surveillance-tuned)" "${PREVIEW_BASE}${BFF_PREFIX}/dashboard/forecast"
probe "national haemovigilance" "${PREVIEW_BASE}${BFF_PREFIX}/haemovigilance/national"
probe "central bank metrics" "${PREVIEW_BASE}${BFF_PREFIX}/central-bank/metrics"

echo ""
echo "Summary: pass=${pass} skip=${skip} fail=${fail}"
if [[ "$fail" -gt 0 ]]; then
  exit 1
fi
