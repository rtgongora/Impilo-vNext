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

gate_regen() {
  node scripts/frontend/generate-parity-docs.mjs >/dev/null 2>&1 || true
  if git diff --quiet -- docs/frontend/FRONTEND_IMPLEMENTATION_STATUS.md 2>/dev/null; then
    guard_pass "frontend parity docs in sync"
  else
    guard_warn "frontend parity docs drift — run node scripts/frontend/generate-parity-docs.mjs"
  fi
}
gate_regen
exit "$FAIL"
