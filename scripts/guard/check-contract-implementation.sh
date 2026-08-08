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

# Baseline-ratchet, replacing a threshold that defaulted to 0. That default meant the
# gate ALWAYS failed, while run-api-contract-checks.sh assigned 999999 inside a
# `bash -c` without exporting it — so the guard never saw the override and every run
# reported "threshold=0", after which `|| gate_warn` swallowed the failure.
# Permanently red AND permanently non-blocking is not a gate; it is noise people learn
# to scroll past. This ledger records measured truth and blocks REGRESSIONS above it.
# Ratchet the baseline DOWN as contracts and handlers are reconciled.
read -r VIOLATIONS MISSING ORPHANS BASELINE <<EOF
$(python3 - <<'PY'
import json
with open("reports/product/contract-implementation-matrix.json") as f:
    c = json.load(f).get("counts", {})
base = 0
try:
    with open("reports/product/contract-implementation-baseline.json") as f:
        base = int(json.load(f).get("violationsBaseline", 0))
except FileNotFoundError:
    pass
print(c.get("violations", 0), c.get("missing", 0), c.get("orphanHandlers", 0), base)
PY
)
EOF

THRESHOLD="${CONTRACT_IMPLEMENTATION_VIOLATION_THRESHOLD:-$BASELINE}"

if [[ "$VIOLATIONS" -gt "$THRESHOLD" ]]; then
  guard_fail "contract implementation REGRESSION: violations=$VIOLATIONS > baseline=$THRESHOLD (missing=$MISSING, orphanHandlers=$ORPHANS). Do not raise the baseline — add the OpenAPI operation, or wire the handler. See docs/product/CONTRACT_IMPLEMENTATION_MATRIX.md and reports/product/contract-implementation-baseline.json"
fi

guard_pass "contract implementation — violations=$VIOLATIONS at/below baseline=$THRESHOLD (missing=$MISSING, orphanHandlers=$ORPHANS). TRUE count is reported, not zero; ratchet the baseline down as fixes land."
exit 0
