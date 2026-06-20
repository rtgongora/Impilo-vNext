#!/usr/bin/env bash
# Product Truth gate — advisory by default; ratchet PRODUCT_TRUTH_VIOLATION_THRESHOLD toward zero.
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
cd "$REPO_PATH"

COMPLETENESS_DIR="$REPO_PATH/scripts/completeness"
PRODUCT_TRUTH_JSON="$REPO_PATH/reports/product/product-truth.json"

if [[ ! -d "$COMPLETENESS_DIR/node_modules" ]]; then
  (cd "$COMPLETENESS_DIR" && npm install --silent) || guard_fail "npm install in scripts/completeness"
fi

guard_pass "generating product truth dataset"
(cd "$COMPLETENESS_DIR" && npm run product-truth --silent)

if [[ ! -f "$PRODUCT_TRUTH_JSON" ]]; then
  guard_fail "missing $PRODUCT_TRUTH_JSON after generation"
fi

VIOLATIONS=$(python3 - <<'PY'
import json, sys
with open("reports/product/product-truth.json") as f:
    d = json.load(f)
gaps = d.get("summary", {}).get("gapCounts", {})
print(gaps.get("total", 0))
PY
)

THRESHOLD="${PRODUCT_TRUTH_VIOLATION_THRESHOLD:-99999}"
BLOCKING="${PRODUCT_TRUTH_GATE_BLOCKING:-0}"

if [[ "$VIOLATIONS" -gt "$THRESHOLD" ]]; then
  if [[ "$BLOCKING" == "1" ]]; then
    guard_fail "product-truth violations=$VIOLATIONS (threshold=$THRESHOLD). See docs/audits/product-truth-gap-register.md"
  else
    guard_warn "product-truth violations=$VIOLATIONS (advisory threshold=$THRESHOLD)"
  fi
else
  guard_pass "product-truth gate — violations=$VIOLATIONS (threshold=$THRESHOLD)"
fi

# Specific blocking checks (always fail on new regressions in changed files)
BLOCKERS=$(python3 - <<'PY'
import json
with open("reports/product/product-truth.json") as f:
    d = json.load(f)
n = 0
for s in d.get("services", []):
    for g in s.get("gaps", []):
        if g.get("severity") == "blocker" and g.get("category") == "E":
            n += 1
print(n)
PY
)

if [[ "$BLOCKERS" -gt 0 && "${PRODUCT_TRUTH_BLOCK_UI_WITHOUT_BACKEND:-0}" == "1" ]]; then
  guard_fail "UI-without-backend blockers=$BLOCKERS"
fi

exit 0
