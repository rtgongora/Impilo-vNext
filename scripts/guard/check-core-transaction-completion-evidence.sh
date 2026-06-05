#!/usr/bin/env bash
# Blocks fraudulent core-transaction completion metric flips.
# Authority: docs/frontend/GAP_CLOSURE_RULES.md
set -euo pipefail

REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
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
    ADDED_EVIDENCE="$(git diff "${BASE}" -- "$GENERATOR" | grep -c '^\+.*"' | grep -c 'bffEndpoints\|COMPLETION_EVIDENCE' || true)"
    if [[ "${ADDED_COMPLETE:-0}" -gt 3 ]] && [[ "${ADDED_EVIDENCE:-0}" -eq 0 ]]; then
      guard_fail "suspected blanket completionClassification flip (+${ADDED_COMPLETE} without evidence registry changes)"
      FAIL=1
    fi
  fi
fi

# Source registry size must match runtime count of transaction-complete literals.
REGISTRY_SIZE="$(node -e "
const fs=require('fs');
const src=fs.readFileSync('$GENERATOR','utf8');
const m=src.match(/const COMPLETION_EVIDENCE = \\{([\\s\\S]*?)\\};/);
if(!m) { console.log(0); process.exit(0); }
const keys=[...m[0].matchAll(/\"([a-z0-9-]+)\":\\s*\\{/g)].map(x=>x[1]);
console.log(keys.length);
")"
COMPLETE_LITERALS="$(grep -c 'completionClassification: "transaction-complete"' "$GENERATOR" || true)"

if [[ "$REGISTRY_SIZE" != "$COMPLETE_LITERALS" ]]; then
  guard_fail "generator has ${COMPLETE_LITERALS} transaction-complete journeys but ${REGISTRY_SIZE} evidence entries"
  FAIL=1
else
  guard_pass "evidence registry size matches transaction-complete journey count (${REGISTRY_SIZE})"
fi

if [[ "$FAIL" -ne 0 ]]; then
  echo ""
  echo "CORE-TRANSACTION-EVIDENCE: BLOCKED"
  exit 1
fi

echo ""
echo "CORE-TRANSACTION-EVIDENCE: PASSED"
exit 0
