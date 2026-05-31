#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
BASE="$(resolve_base_ref)"
FAIL=0

# Large new feature dirs without inventory touch
NEW_FEATURES=$(git diff --diff-filter=A --name-only "$BASE"...HEAD 2>/dev/null \
  | guard_filter '^ui/one-ui-shell/src/(app|features)/' | cut -d/ -f1-5 | sort -u | head -20 || true)

if [[ -z "$NEW_FEATURES" ]]; then
  guard_pass "no major new frontend feature paths"
  exit 0
fi

if git diff --name-only "$BASE"...HEAD | guard_filter -q 'docs/architecture/FEATURE_INVENTORY.md|docs/frontend/'; then
  guard_pass "feature inventory or frontend docs touched for new surfaces"
else
  guard_warn "new frontend surfaces without FEATURE_INVENTORY.md update"
fi

exit "$FAIL"
