#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
BASE="$(resolve_base_ref)"
FAIL=0

DELETED_ROUTES=$(git diff --diff-filter=D --name-only "$BASE"...HEAD -- 'ui/one-ui-shell/src/app/' 2>/dev/null \
  | guard_filter 'page\.tsx$' || true)

if [[ -n "$DELETED_ROUTES" ]]; then
  guard_warn "frontend pages removed:"
  echo "$DELETED_ROUTES"
  if ! git diff --name-only "$BASE"...HEAD | guard_filter -q 'ui/one-ui-shell/src/lib/routes.ts|docs/architecture/FRONTEND_ROUTE_INVENTORY.md'; then
    guard_fail "route pages deleted without routes.ts or FRONTEND_ROUTE_INVENTORY.md update"
    FAIL=1
  fi
fi

# --check reports drift without writing. Regenerating in place would dirty the
# tracked docs in every peer session sharing this checkout, and the old form
# then warned about the date-only diff it had just created itself.
gate_regen() {
  local out
  if out="$(node scripts/frontend/generate-parity-docs.mjs --check 2>&1)"; then
    guard_pass "frontend parity docs in sync"
  else
    guard_warn "frontend parity docs drift — run node scripts/frontend/generate-parity-docs.mjs"
    echo "$out" | head -12
  fi
}
gate_regen
exit "$FAIL"
