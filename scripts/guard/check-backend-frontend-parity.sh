#!/usr/bin/env bash
# Backend-to-frontend parity: docs in sync + no new OPEN gaps without matrix update.
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
cd "$REPO_PATH"
BASE="$(resolve_base_ref)"
FAIL=0

MATRIX="docs/frontend/BACKEND_CAPABILITY_TO_FRONTEND_SURFACING_MATRIX.md"
STATUS="docs/frontend/FRONTEND_IMPLEMENTATION_STATUS.md"
INVENTORY="docs/architecture/BACKEND_CAPABILITY_INVENTORY.md"
PARITY_DOC="docs/architecture/FRONTEND_BACKEND_PARITY_MATRIX.md"

for f in "$MATRIX" "$STATUS"; do
  if [[ ! -f "$f" ]]; then
    guard_fail "missing parity doc: $f"
    FAIL=1
  fi
done

# Regenerate and allow date-line-only drift
node scripts/frontend/generate-parity-docs.mjs >/dev/null 2>&1 || {
  guard_fail "generate-parity-docs.mjs failed"
  exit 1
}

CONTENT_DRIFT=$(git diff -U0 -- "$MATRIX" "$STATUS" 2>/dev/null \
  | grep -E '^[+-]' | grep -v '^[+-][+-][+-]' | grep -v 'Generated:' | grep -v 'Updated:' || true)

if [[ -n "$CONTENT_DRIFT" ]]; then
  guard_fail "parity docs out of sync — run: node scripts/frontend/generate-parity-docs.mjs"
  echo "$CONTENT_DRIFT" | head -30
  FAIL=1
else
  guard_pass "frontend parity docs in sync with capability registry"
fi

# New BFF controllers without parity doc touch
NEW_BFF=$(git diff --diff-filter=A --name-only "$BASE"...HEAD -- \
  'services/experience-bff/src/main/java' 2>/dev/null | guard_filter 'Controller\.java$' || true)
if [[ -n "$NEW_BFF" ]]; then
  if git diff --name-only "$BASE"...HEAD | guard_filter -q 'docs/frontend/BACKEND_CAPABILITY|docs/architecture/FRONTEND_BACKEND_PARITY|docs/architecture/BACKEND_CAPABILITY'; then
    guard_pass "BFF changes include parity documentation update"
  else
    guard_fail "new BFF controllers without BACKEND_CAPABILITY / parity matrix update"
    echo "$NEW_BFF"
    FAIL=1
  fi
fi

# Block obvious placeholder pages in new routes
NEW_PAGES=$(git diff --diff-filter=A --name-only "$BASE"...HEAD -- 'ui/one-ui-shell/src/app/' 2>/dev/null \
  | guard_filter 'page\.tsx$' || true)
while IFS= read -r page; do
  [[ -z "$page" || ! -f "$page" ]] && continue
  if grep -qiE 'coming soon|under construction|lorem ipsum|TODO: implement' "$page" 2>/dev/null; then
    guard_fail "placeholder copy in new page: $page"
    FAIL=1
  fi
  if grep -q 'JSON.stringify' "$page" 2>/dev/null && ! grep -qE 'test|spec|story' <<<"$page"; then
    guard_warn "JSON.stringify dump suspected in $page — verify not a debug stub"
  fi
done <<< "$NEW_PAGES"

bash scripts/guard/check-frontend-mocks-and-stubs.sh || FAIL=1
bash scripts/guard/check-api-client-surfacing.sh || true

if [[ "$FAIL" -ne 0 ]]; then
  exit 1
fi
guard_pass "backend-frontend parity gate"
exit 0
