#!/usr/bin/env bash
# Mobile parity gate — blocking for new gaps; advisory for documented legacy gaps.
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

echo "=== Mobile parity gate ==="
echo "BASE: $BASE"

# Inventories
for f in docs/architecture/MOBILE_APP_INVENTORY.md docs/architecture/MOBILE_PARITY_MATRIX.md; do
  if [[ ! -f "$f" ]]; then
    node scripts/architecture/generate-parity-inventories.mjs 2>/dev/null || true
  fi
  if [[ ! -f "$f" ]]; then
    guard_fail "missing $f — run node scripts/architecture/generate-parity-inventories.mjs"
    FAIL=1
  fi
done

if [[ -f config/parity-allowlist.yml ]]; then
  guard_pass "parity allowlist present"
else
  guard_fail "missing config/parity-allowlist.yml"
  FAIL=1
fi

bash scripts/guard/check-mobile-mocks-and-stubs.sh || { FAIL=1; PARITY_BLOCKING=1; }
bash scripts/guard/check-mobile-api-surfacing.sh || { FAIL=1; PARITY_BLOCKING=1; }

# Mobile dependency sanity when mobile tree changed
if git diff --name-only "$BASE"...HEAD -- 'apps/mobile/' 2>/dev/null | head -1 | grep -q .; then
  if command -v pnpm >/dev/null 2>&1; then
    if (cd apps/mobile && pnpm install --frozen-lockfile 2>/dev/null || pnpm install); then
      guard_pass "mobile workspace install"
    else
      guard_fail "mobile pnpm install failed (mobile files changed)"
      FAIL=1
      PARITY_BLOCKING=1
    fi
  else
    guard_warn "pnpm missing — mobile install check skipped"
    PARITY_ADVISORY=1
  fi
fi

# Preview backend config (advisory unless mobile release files changed)
for cfg in apps/mobile/citizen-app/app.json apps/mobile/provider-app/app.json; do
  if [[ -f "$cfg" ]]; then
    if grep -q '41.57.127.235\|impilo-preview\|EXPO_PUBLIC.*BFF\|API_URL' "$cfg" 2>/dev/null \
      || grep -rq 'extra.*api\|bff' apps/mobile/citizen-app 2>/dev/null; then
      guard_pass "mobile preview API config traceable in $cfg or env docs"
    else
      guard_warn "verify mobile preview points to preview BFF (see MOBILE_PREVIEW_TESTING.md)"
      PARITY_ADVISORY=1
    fi
  fi
done

# Matrix drift on mobile column changes in registry
if git diff --name-only "$BASE"...HEAD | guard_filter -q 'scripts/frontend/generate-parity-docs.mjs'; then
  if ! git diff --name-only "$BASE"...HEAD | guard_filter -q 'MOBILE_PARITY_MATRIX|generate-parity-inventories'; then
    guard_fail "parity registry changed without MOBILE_PARITY_MATRIX regeneration"
    FAIL=1
  fi
fi

# Advisory legacy
guard_warn "full Android APK / iOS TestFlight builds remain advisory (see MOBILE_PARITY_GATE.md)"
PARITY_ADVISORY=1

echo ""
echo "=== Mobile parity summary ==="
grep -E '^(complete|partial|missing)' docs/architecture/MOBILE_PARITY_MATRIX.md 2>/dev/null | head -5 || true
echo "See docs/architecture/MOBILE_PARITY_MATRIX.md for full classification"

parity_print_verdict "Mobile parity" || FAIL=1
exit "$FAIL"
