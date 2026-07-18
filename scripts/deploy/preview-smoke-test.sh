#!/usr/bin/env bash
set -euo pipefail
# Runs ON the VM, where the PUBLIC host hairpin-NATs and is unreachable. Default to the node-local
# ingress; override PREVIEW_HOST for an external smoke test from off-VM.
HOST="${PREVIEW_HOST:-127.0.0.1}"
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

# Identity Contract conformance (the AMBER->GREEN bar, Identity Contract §16).
# Opt-in — set RUN_IDENTITY_PACK=1 after the CPID cutover. A RED result
# (exit 1) fails the smoke test; AMBER (exit 2, journeys skipped because a
# service was unreachable) is surfaced but does not fail liveness.
if [[ "${RUN_IDENTITY_PACK:-0}" == "1" ]]; then
  echo "=== Identity Contract 12-journey pack ==="
  REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
  if bash "$REPO_ROOT/tests/identity-contract/identity-contract-journeys.sh"; then
    echo "PASS identity-contract pack GREEN"
  else
    rc=$?
    if [[ "$rc" == "1" ]]; then echo "FAIL identity-contract pack RED"; FAIL=1
    else echo "WARN identity-contract pack AMBER (some journeys skipped)"; fi
  fi
fi

exit "$FAIL"
