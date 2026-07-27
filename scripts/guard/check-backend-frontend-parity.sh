#!/usr/bin/env bash
# Backend-to-frontend parity gate — orchestrates inventory, mocks, API surfacing.
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
source "$(dirname "$0")/_parity-common.sh"
cd "$REPO_PATH"
parity_ensure_full_catalog
BASE="$(resolve_base_ref)"
FAIL=0
PARITY_BLOCKING=0
PARITY_ADVISORY=0
: >"$PARITY_SUMMARY_FILE"

echo "=== Backend-to-frontend parity gate ==="
echo "BASE: $BASE"

MATRIX="docs/frontend/BACKEND_CAPABILITY_TO_FRONTEND_SURFACING_MATRIX.md"
for f in "$MATRIX" docs/architecture/BACKEND_CAPABILITY_INVENTORY.md docs/architecture/FRONTEND_BACKEND_PARITY_MATRIX.md; do
  [[ -f "$f" ]] || node scripts/architecture/generate-parity-inventories.mjs 2>/dev/null || true
done

node scripts/frontend/generate-parity-docs.mjs >/dev/null 2>&1 || {
  guard_fail "generate-parity-docs.mjs failed"
  exit 1
}

CONTENT_DRIFT=$(git diff -U0 -- "$MATRIX" docs/frontend/FRONTEND_IMPLEMENTATION_STATUS.md 2>/dev/null \
  | grep -E '^[+-]' | grep -v '^[+-][+-][+-]' | grep -v 'Generated:' | grep -v 'Updated:' || true)
if [[ -n "$CONTENT_DRIFT" ]]; then
  guard_fail "parity docs out of sync — run: node scripts/frontend/generate-parity-docs.mjs"
  echo "$CONTENT_DRIFT" | head -20
  FAIL=1
  PARITY_BLOCKING=1
else
  guard_pass "frontend parity docs in sync"
fi

bash scripts/guard/check-frontend-mocks-and-stubs.sh || { FAIL=1; PARITY_BLOCKING=1; }
bash scripts/guard/check-api-client-surfacing.sh || { FAIL=1; PARITY_BLOCKING=1; }

# Weakened page replacement detection
for page in $(git diff --diff-filter=M --name-only "$BASE" HEAD -- 'ui/one-ui-shell/src/app/' 2>/dev/null | guard_filter 'page\.tsx$' || true); do
  [[ -f "$page" ]] || continue
  before=$(git show "$BASE:$page" 2>/dev/null | wc -l || echo 0)
  after=$(wc -l <"$page" || echo 0)
  if [[ "$before" -gt 80 && "$after" -lt 30 ]]; then
    guard_warn "page drastically simplified: $page ($before -> $after lines)"
    PARITY_ADVISORY=1
  fi
done

echo ""
echo "=== Parity classification (from matrix) ==="
python3 <<'PY' 2>/dev/null || true
import re, pathlib
p = pathlib.Path("docs/frontend/BACKEND_CAPABILITY_TO_FRONTEND_SURFACING_MATRIX.md")
if not p.exists():
    exit()
text = p.read_text()
for label in ["Live", "Partial", "Not Wired", "Fixture", "Blocked"]:
    n = len(re.findall(rf"\| [^|]* \| [^|]* \| [^|]* \| [^|]* \| [^|]* \| [^|]* \| [^|]* \| [^|]* \| [^|]* \| [^|]* \| {label} \|", text))
    if n:
        print(f"  {label}: {n}")
PY

parity_print_verdict "Backend-frontend parity" || FAIL=1
[[ "$FAIL" -eq 0 ]] && guard_pass "backend-frontend parity gate"
exit "$FAIL"
