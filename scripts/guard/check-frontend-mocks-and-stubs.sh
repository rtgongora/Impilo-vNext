#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
source "$(dirname "$0")/_parity-common.sh"
cd "$REPO_PATH"
BASE="$(resolve_base_ref)"
PARITY_BLOCKING=0
PARITY_ADVISORY=0

# Canonical no-stub guard (blocking)
if [[ ! -d ui/node_modules ]]; then
  (cd ui && npm ci --ignore-scripts -w one-ui-shell) >/dev/null 2>&1 || true
fi
if ! (cd ui/one-ui-shell && npm run test:no-stubs); then
  guard_fail "no-stub guard failed (GAP_CLOSURE_RULES)"
  parity_record_blocking
  exit 1
fi
guard_pass "no-stub guard (production path scan)"

# Heuristic scan on NEW/changed production files only
NEW_FILES=$(git diff --name-only "$BASE"...HEAD -- 'ui/one-ui-shell/src/' 2>/dev/null \
  | guard_filter '\.(tsx|ts)$' || true)

while IFS= read -r f; do
  [[ -z "$f" || ! -f "$f" ]] && continue
  parity_is_test_path "$f" && continue
  parity_is_allowlisted "$f" && continue
  if grep -qiE 'coming soon|under construction|lorem ipsum|placeholder page|TODO: implement' "$f" 2>/dev/null; then
    guard_fail "new placeholder copy: $f"
    parity_record_blocking
  fi
  if grep -q 'JSON.stringify' "$f" 2>/dev/null; then
    guard_warn "JSON.stringify in changed file: $f"
    parity_record_advisory
  fi
  if grep -qiE 'mockData|fakeResponse|demoPatient|sampleData|hardcoded.*\[\]' "$f" 2>/dev/null; then
    guard_fail "suspected mock data in production file: $f"
    parity_record_blocking
  fi
done <<< "$NEW_FILES"

# Advisory: legacy patterns in full tree (not blocking)
if parity_scan_grep "advisory-legacy" "ui/one-ui-shell/src/app" 'coming soon'; then
  guard_warn "legacy 'coming soon' in app routes — document in parity matrix/allowlist"
  parity_record_advisory
fi

if [[ "$PARITY_BLOCKING" -ne 0 ]]; then
  exit 1
fi
guard_pass "frontend mocks/stubs check"
exit 0
