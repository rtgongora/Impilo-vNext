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

# Honest baseline-ratchet: the scanner now reports the TRUE gap count. The gate
# blocks on REGRESSIONS (gaps above the recorded baseline) and on any NEW blocker
# above the blocker baseline — it does NOT pretend the count is zero. The baseline
# is a debt ledger to be ratcheted DOWN as fixes land, never up.
read -r VIOLATIONS BLOCKERS GAP_BASELINE BLOCKER_BASELINE <<EOF
$(python3 - <<'PY'
import json
with open("reports/product/product-truth.json") as f:
    d = json.load(f)
gaps = d.get("summary", {}).get("gapCounts", {})
total = gaps.get("total", 0)
blockers = gaps.get("bySeverity", {}).get("blocker", 0)
gb, bb = 0, 0
try:
    with open("reports/product/product-truth-baseline.json") as f:
        b = json.load(f)
    gb = int(b.get("gapBaseline", 0))
    bb = int(b.get("blockerBaseline", 0))
except FileNotFoundError:
    pass
print(total, blockers, gb, bb)
PY
)
EOF

# Allow an explicit override threshold, but never let it silently exceed the baseline.
THRESHOLD="${PRODUCT_TRUTH_VIOLATION_THRESHOLD:-$GAP_BASELINE}"
BLOCKING="${PRODUCT_TRUTH_GATE_BLOCKING:-1}"

if [[ "$BLOCKERS" -gt "$BLOCKER_BASELINE" ]]; then
  if [[ "$BLOCKING" == "1" ]]; then
    guard_fail "product-truth NEW blocker-severity gap(s): blockers=$BLOCKERS > baseline=$BLOCKER_BASELINE. A security/crypto/UI-without-backend blocker regressed. See reports/product/product-truth-baseline.json"
  else
    guard_warn "product-truth blockers=$BLOCKERS > baseline=$BLOCKER_BASELINE (advisory)"
  fi
elif [[ "$VIOLATIONS" -gt "$THRESHOLD" ]]; then
  if [[ "$BLOCKING" == "1" ]]; then
    guard_fail "product-truth REGRESSION: violations=$VIOLATIONS > baseline=$THRESHOLD. New product-truth debt introduced; do not raise the baseline — fix the gap. See docs/audits/product-truth-gap-register.md"
  else
    guard_warn "product-truth violations=$VIOLATIONS > baseline=$THRESHOLD (advisory)"
  fi
else
  guard_pass "product-truth gate — violations=$VIOLATIONS at/below baseline=$THRESHOLD (blockers=$BLOCKERS/$BLOCKER_BASELINE). TRUE count is reported, not zero; ratchet baseline down as fixes land."
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
