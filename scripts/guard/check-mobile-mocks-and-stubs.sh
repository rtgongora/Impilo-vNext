#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
source "$(dirname "$0")/_parity-common.sh"
cd "$REPO_PATH"
parity_ensure_full_catalog
BASE="$(resolve_base_ref)"
PARITY_BLOCKING=0

NEW_FILES=$(git diff --name-only "$BASE"...HEAD -- 'apps/mobile/' 2>/dev/null \
  | guard_filter '\.(tsx|ts)$' || true)

while IFS= read -r f; do
  [[ -z "$f" || ! -f "$f" ]] && continue
  parity_is_test_path "$f" && continue
  parity_is_allowlisted "$f" && continue
  if grep -qiE 'coming soon|under construction|placeholder|TODO: implement|stub screen' "$f" 2>/dev/null; then
    guard_fail "new mobile placeholder: $f"
    parity_record_blocking
  fi
  if grep -qiE 'mockData|fakeResponse|demoPatient|sampleData|lorem ipsum' "$f" 2>/dev/null; then
    if ! grep -q 'demoJourney' <<<"$f"; then
      guard_fail "suspected mobile mock data: $f"
      parity_record_blocking
    fi
  fi
  if grep -qiE 'from.*fixtures/|from.*mocks/' "$f" 2>/dev/null; then
    guard_fail "production mobile imports fixture/mock: $f"
    parity_record_blocking
  fi
done <<< "$NEW_FILES"

# Shell-only screens (very small file, no API import)
while IFS= read -r f; do
  [[ -z "$f" || ! -f "$f" ]] && continue
  [[ "$f" == *Screen.tsx ]] || continue
  parity_is_allowlisted "$f" && continue
  if ! grep -qE 'Service|api|fetch|useQuery|mobile-api' "$f" 2>/dev/null; then
    if grep -qiE 'Text.*>|placeholder|coming soon' "$f" 2>/dev/null; then
      guard_warn "mobile screen may be shell-only: $f"
      parity_record_advisory
    fi
  fi
done <<< "$NEW_FILES"

if [[ "$PARITY_BLOCKING" -ne 0 ]]; then
  exit 1
fi
guard_pass "mobile mocks/stubs check"
exit 0
