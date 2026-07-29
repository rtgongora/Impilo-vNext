#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
source "$(dirname "$0")/_parity-common.sh"
cd "$REPO_PATH"
parity_ensure_full_catalog
BASE="$(resolve_base_ref)"
BLOCKING=0

# New user-facing web route without mobile matrix touch
NEW_WEB=$(git diff --diff-filter=A --name-only "$BASE" HEAD -- 'ui/one-ui-shell/src/app/' 2>/dev/null \
  | guard_filter 'page\.tsx$' || true)
if [[ -n "$NEW_WEB" ]]; then
  if ! git diff --name-only "$BASE" HEAD | guard_filter -q 'docs/architecture/MOBILE_PARITY_MATRIX|docs/mobile/|apps/mobile/'; then
    guard_fail "new web route(s) without mobile parity classification update"
    echo "$NEW_WEB" | head -10
    BLOCKING=1
  fi
fi

# New BFF/user capability without mobile classification
NEW_BFF=$(git diff --diff-filter=A --name-only "$BASE" HEAD -- \
  'services/experience-bff/' 'contracts/openapi/' 2>/dev/null | head -20 || true)
if [[ -n "$NEW_BFF" ]]; then
  if ! git diff --name-only "$BASE" HEAD | guard_filter -q 'MOBILE_PARITY_MATRIX|MOBILE_APP_INVENTORY|apps/mobile/'; then
    guard_warn "backend/API changes — verify MOBILE_PARITY_MATRIX.md updated"
  fi
fi

NEW_SCREENS=$(git diff --diff-filter=A --name-only "$BASE" HEAD -- 'apps/mobile/' 2>/dev/null \
  | guard_filter 'Screen\.tsx$' || true)
while IFS= read -r s; do
  [[ -z "$s" || ! -f "$s" ]] && continue
  if ! grep -qE 'Service|api|fetch|useQuery|mobile-api' "$s" 2>/dev/null; then
    parity_is_allowlisted "$s" && continue
    guard_fail "new mobile screen without API integration: $s"
    BLOCKING=1
  fi
done <<< "$NEW_SCREENS"

if [[ "$BLOCKING" -ne 0 ]]; then
  exit 1
fi
guard_pass "mobile API surfacing check"
exit 0
