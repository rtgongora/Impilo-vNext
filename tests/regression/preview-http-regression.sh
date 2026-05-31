#!/usr/bin/env bash
# Baseline HTTP regression checks (no browser). Set PREVIEW_BASE_URL for live preview.
set -euo pipefail
BASE="${PREVIEW_BASE_URL:-http://127.0.0.1}"
FAIL=0

check_route() {
  local name="$1" path="$2"
  local code
  code="$(curl -s -o /dev/null -w '%{http_code}' "${BASE}${path}" 2>/dev/null || echo 000)"
  if [[ "$code" != "000" && "$code" != "502" && "$code" != "503" ]]; then
    echo "PASS $name ($code) ${BASE}${path}"
  else
    echo "FAIL $name (got $code) ${BASE}${path}"
    FAIL=1
  fi
}

echo "=== Preview HTTP regression (${BASE}) ==="
check_route "root" "/"
check_route "login" "/auth/login"
check_route "home" "/home"
check_route "registry" "/registry"
check_route "clinical" "/clinical"
check_route "enterprise" "/enterprise"
check_route "data-intelligence" "/data"
check_route "ask-nompilo" "/ask"
check_route "fundo-learning" "/learning"
check_route "ndila" "/ndila"
check_route "nhume-dispatch" "/dispatch"
check_route "mushex-finance" "/finance/mushex-platform"
check_route "bff-health" "/actuator/health"
check_route "bff-version" "/health/version"

exit "$FAIL"
