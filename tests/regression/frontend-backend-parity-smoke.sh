#!/usr/bin/env bash
# Smoke checklist: key web routes exist and are not obvious placeholders.
set -euo pipefail
BASE="${PREVIEW_BASE_URL:-http://127.0.0.1}"
FAIL=0

check() {
  local name="$1" path="$2"
  local code body
  code="$(curl -s -o /tmp/parity-body.html -w '%{http_code}' "${BASE}${path}" 2>/dev/null || echo 000)"
  body="$(cat /tmp/parity-body.html 2>/dev/null || true)"
  if [[ "$code" == "000" || "$code" == "502" || "$code" == "503" ]]; then
    echo "FAIL $name HTTP $code"
    FAIL=1
    return
  fi
  if echo "$body" | grep -qi 'coming soon'; then
    echo "FAIL $name contains 'coming soon'"
    FAIL=1
    return
  fi
  echo "PASS $name ($code)"
}

echo "=== Frontend-backend parity smoke ($BASE) ==="
check "registry" "/registry"
check "clinical" "/clinical"
check "enterprise" "/enterprise"
check "data" "/data"
check "ask-nompilo" "/ask"
check "learning-fundo" "/learning"
check "ndila" "/ndila"
check "dispatch-nhume" "/dispatch"
check "finance-mushex" "/finance/mushex-platform"
check "auth-login" "/auth/login"

exit "$FAIL"
