#!/usr/bin/env bash
# Static runtime-truth heuristics — decorative handlers, fixture dashboards, mock clients.
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
cd "$REPO_PATH"

BLOCKING="${RUNTIME_TRUTH_GATE_BLOCKING:-1}"
VIOLATIONS=0

check_pattern() {
  local label="$1"
  local pattern="$2"
  local glob="$3"
  local hits
  hits="$(rg -n "$pattern" $glob \
    --glob '!**/*.test.*' \
    --glob '!**/*.spec.*' \
    --glob '!**/e2e/**' \
    2>/dev/null | rg -v 'test\.skip|TODO|FIXME|demo fixture|DemoFixture|fixture-backed' || true)"
  if [[ -n "$hits" ]]; then
    guard_warn "$label violations (sample):"
    echo "$hits" | head -15
    VIOLATIONS=$((VIOLATIONS + $(echo "$hits" | wc -l)))
  else
    guard_pass "$label"
  fi
}

echo "=== Runtime truth heuristics (static) ==="
check_pattern "decorative onClick" 'onClick=\{\(\) => \{\}\}' 'ui/one-ui-shell/src'
check_pattern "hardcoded dashboard arrays in pages" 'const (DATA|data|rows|items|stats) = \[' 'ui/one-ui-shell/src/app'
check_pattern "mock/stub api clients in lib" 'from ["'\''].*/(mock|stub|fixture)["'\'']' 'ui/one-ui-shell/src/lib'

if [[ "$VIOLATIONS" -gt 0 ]]; then
  if [[ "$BLOCKING" == "1" ]]; then
    guard_fail "runtime truth heuristic violations=$VIOLATIONS (set RUNTIME_TRUTH_GATE_BLOCKING=0 to advise only)"
  else
    guard_warn "runtime truth heuristic violations=$VIOLATIONS (advisory)"
  fi
fi

guard_pass "runtime truth heuristics complete (violations=$VIOLATIONS)"
