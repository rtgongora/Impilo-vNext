#!/usr/bin/env bash
# Cross-service cohesion gate — advisory by default; blocks when COHESION_GATE_BLOCKING=1.
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
cd "$REPO_PATH"

COMPLETENESS_DIR="$REPO_PATH/scripts/completeness"
PRODUCT_TRUTH_JSON="$REPO_PATH/reports/product/product-truth.json"

if [[ ! -d "$COMPLETENESS_DIR/node_modules" ]]; then
  (cd "$COMPLETENESS_DIR" && npm install --silent) || guard_fail "npm install in scripts/completeness"
fi

guard_pass "generating product truth dataset (cohesion evaluation)"
(cd "$COMPLETENESS_DIR" && npm run product-truth --silent)

if [[ ! -f "$PRODUCT_TRUTH_JSON" ]]; then
  guard_fail "missing $PRODUCT_TRUTH_JSON after generation"
fi

read -r PASS TOTAL NEEDS MISSING <<<"$(python3 - <<'PY'
import json
with open("reports/product/product-truth.json") as f:
    d = json.load(f)
s = d.get("summary", {}).get("crossServiceCohesion", {})
print(s.get("pass", 0), s.get("total", 0), s.get("needsWork", 0), s.get("missingTest", 0))
PY
)"

BLOCKING="${COHESION_GATE_BLOCKING:-0}"
MIN_PASS="${COHESION_MIN_PASS:-10}"

if [[ "$NEEDS" -gt 0 ]]; then
  if [[ "$BLOCKING" == "1" ]]; then
    guard_fail "cross-service cohesion needs-work=$NEEDS (pass=$PASS/$TOTAL)"
  else
    guard_warn "cross-service cohesion needs-work=$NEEDS (pass=$PASS/$TOTAL)"
  fi
elif [[ "$MISSING" -gt 0 ]]; then
  guard_warn "cross-service cohesion missing-test=$MISSING (pass=$PASS/$TOTAL)"
elif [[ "$PASS" -lt "$MIN_PASS" ]]; then
  guard_warn "cross-service cohesion pass=$PASS below minimum=$MIN_PASS"
else
  guard_pass "cross-service cohesion — pass=$PASS/$TOTAL"
fi

exit 0
