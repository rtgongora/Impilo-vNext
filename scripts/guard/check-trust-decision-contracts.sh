#!/usr/bin/env bash
# Trust-decision contract conformance gate (Checkpoint 2 closure):
#   1. four-representation semantic parity (Java / TS / JSON Schema / OpenAPI)
#   2. OpenAPI lint + bundle with the repository's established tooling
#   3. shared fixtures against a real JSON Schema validator + OpenAPI models
#   4. duplicate/unversioned re-declaration guard for canonical trust types
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
cd "$REPO_PATH"
FAIL=0

COMPLETENESS_DIR="$REPO_PATH/scripts/completeness"
if [[ ! -d "$COMPLETENESS_DIR/node_modules" ]]; then
  (cd "$COMPLETENESS_DIR" && npm install --silent)
fi

if node "$COMPLETENESS_DIR/check-trust-contract-parity.mjs"; then
  guard_pass "trust contract parity (java/ts/schema/openapi)"
else
  guard_fail "trust contract parity drift" || true
  FAIL=1
fi

if node "$COMPLETENESS_DIR/lint-trust-decision-openapi.mjs"; then
  guard_pass "trust-decision OpenAPI lint + bundle"
else
  guard_fail "trust-decision OpenAPI lint" || true
  FAIL=1
fi

if python3 "$REPO_PATH/scripts/guard/validate-trust-decision-fixtures.py"; then
  guard_pass "trust-decision fixtures (JSON Schema + OpenAPI models)"
else
  guard_fail "trust-decision fixture validation" || true
  FAIL=1
fi

# Consumers must import the canonical versioned contracts, never re-declare them.
# Search TS/TSX sources outside the canonical locations for local re-declarations
# of the canonical type names or a duplicated version constant.
DUPES=$(grep -RnE \
  "(export )?(interface|type|enum) (TrustDecisionInput|TrustChallengeOutcome|TrustChallengeDecision|AsyncExecutionReference|TrustAuditEvent|DelegatedHumanContext|DelegationChain)\b|\"tshepo\.trust\.v1\"" \
  --include='*.ts' --include='*.tsx' \
  ui apps services libs contracts 2>/dev/null \
  | grep -v '^contracts/trust-decision/' \
  | grep -v 'node_modules' \
  | grep -v '\.test\.\|__tests__' \
  || true)
if [[ -n "$DUPES" ]]; then
  guard_fail "unversioned duplicate trust contract declarations found:" || true
  echo "$DUPES"
  FAIL=1
else
  guard_pass "no unversioned duplicate trust contract declarations"
fi

exit "$FAIL"
