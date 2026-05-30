#!/usr/bin/env bash
set -euo pipefail
HOST="${PREVIEW_HOST:-41.57.127.235}"
PORT="${PREVIEW_HTTP_PORT:-80}"
BASE="http://${HOST}:${PORT}"
FAIL=0

check() {
  local name="$1" url="$2" code="${3:-200}"
  local got
  got="$(curl -s -o /dev/null -w '%{http_code}' "$url" || echo 000)"
  if [[ "$got" == "$code" ]] || [[ "$code" == "any" && "$got" != "000" ]]; then
    echo "PASS $name ($got) $url"
  else
    echo "FAIL $name (got $got, want $code) $url"
    FAIL=1
  fi
}

echo "=== Impilo preview smoke tests ==="
check "frontend-root" "$BASE/" "any"
check "bff-health" "$BASE/actuator/health" "any"
check "bff-version" "$BASE/health/version" "any"

if command -v kubectl >/dev/null 2>&1; then
  crashing="$(kubectl get pods -n impilo-preview --no-headers 2>/dev/null | awk '$3 ~ /CrashLoop|Error/ {print}' || true)"
  if [[ -z "$crashing" ]]; then
    echo "PASS no crashing pods in impilo-preview"
  else
    echo "FAIL crashing pods:"
    echo "$crashing"
    FAIL=1
  fi
fi

exit "$FAIL"
