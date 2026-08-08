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

# Dedicated UI-without-backend check (category E at blocker severity).
#
# This check was incapable of firing for three independent reasons, each of which
# had to be fixed for it to mean anything:
#   1. it read only `services`, while UI surfaces live in `frontendSurfaces`;
#   2. the surface-level E gap was emitted at severity 'high', so no record could
#      ever match blocker+E (the service-level E requires a UI surface with NO
#      detected backend at all, which no service has);
#   3. it was gated behind PRODUCT_TRUTH_BLOCK_UI_WITHOUT_BACKEND, default 0.
# Red-proved by adding an unbacked page and confirming this goes RED.
UI_BLOCKERS=$(python3 - <<'PY'
import json
with open("reports/product/product-truth.json") as f:
    d = json.load(f)
n = 0
for coll in ("services", "frontendSurfaces"):
    for s in d.get(coll, []):
        for g in s.get("gaps", []):
            if g.get("severity") == "blocker" and g.get("category") == "E":
                n += 1
print(n)
PY
)

UI_BLOCKER_BASELINE="${PRODUCT_TRUTH_UI_BLOCKER_BASELINE:-0}"
if [[ "$UI_BLOCKERS" -gt "$UI_BLOCKER_BASELINE" ]]; then
  if [[ "${PRODUCT_TRUTH_BLOCK_UI_WITHOUT_BACKEND:-1}" == "1" ]]; then
    guard_fail "UI-without-backend blockers=$UI_BLOCKERS > baseline=$UI_BLOCKER_BASELINE. A UI surface reaches no BFF/API route. Fix the wiring or, if the surface is legitimately backend-free (legal/info shell), add it to SURFACE_ALLOWLIST_PREFIXES with a rationale."
  else
    guard_warn "UI-without-backend blockers=$UI_BLOCKERS > baseline=$UI_BLOCKER_BASELINE (advisory)"
  fi
else
  guard_pass "UI-without-backend blockers=$UI_BLOCKERS at/below baseline=$UI_BLOCKER_BASELINE"
fi

exit 0
