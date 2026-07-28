#!/usr/bin/env bash
# Blocks fraudulent core-transaction completion metric flips.
# Authority: docs/frontend/GAP_CLOSURE_RULES.md
set -euo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"
source "$REPO_PATH/scripts/guard/_guard-common.sh"

GENERATOR="scripts/product/generate-core-transaction-maps.mjs"
FAIL=0

echo "=== Core transaction completion evidence gate ==="
echo "Authority: docs/frontend/GAP_CLOSURE_RULES.md"

if ! node "$GENERATOR" --check-only; then
  guard_fail "completion evidence validation failed"
  FAIL=1
else
  guard_pass "completion evidence registry matches transaction-complete journeys"
fi

# Detect blanket classification flips in generator diffs (regex mass-replace pattern).
BASE="$(resolve_base_ref)"
if git rev-parse --verify "${BASE}^{commit}" >/dev/null 2>&1; then
  if git diff "${BASE}" -- "$GENERATOR" | grep -E '^\+.*completionClassification: "transaction-complete"' | wc -l | grep -qv '^0$'; then
    ADDED_COMPLETE="$(git diff "${BASE}" -- "$GENERATOR" | grep -c '^\+.*completionClassification: "transaction-complete"' || true)"
    ADDED_EVIDENCE="$(git diff "${BASE}" -- "$GENERATOR" | grep -E '^\+.*(bffEndpoints|COMPLETION_EVIDENCE|"tests":)' | wc -l | tr -d ' ')"
    if [[ "${ADDED_COMPLETE:-0}" -gt 3 ]] && [[ "${ADDED_EVIDENCE:-0}" -eq 0 ]]; then
      guard_fail "suspected blanket completionClassification flip (+${ADDED_COMPLETE} without evidence registry changes)"
      FAIL=1
    fi
  fi
fi

# Source registry size must match runtime transaction-complete count from --check-only.
REGISTRY_SIZE="$(node -e "
const fs=require('fs');
const src=fs.readFileSync('$GENERATOR','utf8');
const m=src.match(/const COMPLETION_EVIDENCE = \\{([\\s\\S]*?)\\};/);
if(!m) { console.log(0); process.exit(0); }
const keys=[...m[0].matchAll(/\"([a-z0-9-]+)\":\\s*\\{/g)].map(x=>x[1]);
console.log(keys.length);
")"

if ! node "$GENERATOR" --check-only >/tmp/core-tx-check-only.out 2>&1; then
  cat /tmp/core-tx-check-only.out
  guard_fail "completion evidence --check-only failed"
  FAIL=1
else
  COMPLETE_RUNTIME="$(grep -oP 'Transaction-complete: \K[0-9]+' /tmp/core-tx-check-only.out || echo 0)"
  if [[ "$REGISTRY_SIZE" != "$COMPLETE_RUNTIME" ]]; then
    guard_fail "evidence registry (${REGISTRY_SIZE}) != runtime transaction-complete (${COMPLETE_RUNTIME})"
    FAIL=1
  else
    guard_pass "evidence registry matches runtime transaction-complete count (${REGISTRY_SIZE})"
  fi
fi

if [[ "$FAIL" -ne 0 ]]; then
  echo ""
  echo "CORE-TRANSACTION-EVIDENCE: BLOCKED"
  exit 1
fi

echo ""
echo "CORE-TRANSACTION-EVIDENCE: PASSED"
exit 0
