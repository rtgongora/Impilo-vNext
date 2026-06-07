#!/usr/bin/env bash
# Fails when contract operations are partial, missing, or orphan-handler.
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
cd "$REPO_PATH"

COMPLETENESS_DIR="$REPO_PATH/scripts/completeness"
MATRIX_JSON="$REPO_PATH/reports/product/contract-implementation-matrix.json"

if [[ ! -d "$COMPLETENESS_DIR/node_modules" ]]; then
  (cd "$COMPLETENESS_DIR" && npm install --silent) || guard_fail "npm install in scripts/completeness"
fi

guard_pass "generating contract implementation matrix"
(cd "$COMPLETENESS_DIR" && npm run contract-matrix --silent)

if [[ ! -f "$MATRIX_JSON" ]]; then
  guard_fail "missing $MATRIX_JSON after generation"
fi

VIOLATIONS=$(python3 - <<'PY'
import json, sys
with open("reports/product/contract-implementation-matrix.json") as f:
    d = json.load(f)
print(d.get("counts", {}).get("violations", 0))
PY
)

THRESHOLD="${CONTRACT_IMPLEMENTATION_VIOLATION_THRESHOLD:-0}"

if [[ "$VIOLATIONS" -gt "$THRESHOLD" ]]; then
  guard_fail "contract implementation violations=$VIOLATIONS (threshold=$THRESHOLD). See docs/product/CONTRACT_IMPLEMENTATION_MATRIX.md"
fi

guard_pass "contract implementation matrix — violations=$VIOLATIONS (threshold=$THRESHOLD)"
exit 0
