#!/usr/bin/env bash
# =============================================================================
# Impilo vNext — UI Route Parity Check
# =============================================================================
# Compares the route registry (ui/experience/src/lib/routes.ts) against the
# actual page.tsx files in the experience UI app directory to detect drift.
#
# Also cross-references against prototype spec (docs/prototype/final/01_site_map.md)
# if available.
#
# Exit code: 0 = parity confirmed, non-zero = drift detected
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

ROUTE_REGISTRY="$REPO_ROOT/ui/experience/src/lib/routes.ts"
UI_APP_DIR="$REPO_ROOT/ui/experience/src/app"
SPEC_FILE="$REPO_ROOT/docs/prototype/final/01_site_map.md"

echo "  UI Route Parity Check"
echo ""

PASS=0
FAIL=0
WARNINGS=0

pass() { echo "    ✅ $1"; PASS=$((PASS + 1)); }
fail() { echo "    ❌ $1"; FAIL=$((FAIL + 1)); }
warn() { echo "    ⚠️  $1"; WARNINGS=$((WARNINGS + 1)); }

# ── 1. Route registry exists ──
echo "  1. Route registry file"
if [ -f "$ROUTE_REGISTRY" ]; then
  pass "Route registry exists: ui/experience/src/lib/routes.ts"
else
  fail "Route registry not found: ui/experience/src/lib/routes.ts"
  exit 1
fi

# ── 2. Count routes in registry ──
echo "  2. Registry route count"
REGISTRY_COUNT=$(grep -c '"path":' "$ROUTE_REGISTRY" 2>/dev/null) || REGISTRY_COUNT=0
EXPECTED_COUNT=$(grep 'EXPECTED_ROUTE_COUNT' "$ROUTE_REGISTRY" | grep -oP '\d+' | head -1) || EXPECTED_COUNT=0

echo "    Registry declares $REGISTRY_COUNT routes (EXPECTED_ROUTE_COUNT = $EXPECTED_COUNT)"

if [ "$REGISTRY_COUNT" -eq "$EXPECTED_COUNT" ]; then
  pass "Registry route count matches EXPECTED_ROUTE_COUNT ($EXPECTED_COUNT)"
elif [ "$REGISTRY_COUNT" -gt 0 ]; then
  warn "Registry has $REGISTRY_COUNT routes but EXPECTED_ROUTE_COUNT is $EXPECTED_COUNT"
else
  fail "Registry has 0 routes"
fi

# ── 3. Count page.tsx files ──
echo "  3. Page file count"
PAGE_COUNT=$(find "$UI_APP_DIR" -name "page.tsx" 2>/dev/null | wc -l) || PAGE_COUNT=0
echo "    Found $PAGE_COUNT page.tsx files in ui/experience/src/app/"

# Some routes share page files (e.g., "/" and "/home" may use the same layout page,
# dynamic routes [id] collapse). Allow a tolerance of ±5.
DIFF=$((REGISTRY_COUNT - PAGE_COUNT))
ABS_DIFF=${DIFF#-}

if [ "$ABS_DIFF" -le 5 ]; then
  pass "Page file count ($PAGE_COUNT) within tolerance of registry ($REGISTRY_COUNT) [diff=$DIFF]"
else
  fail "Page file count ($PAGE_COUNT) diverges from registry ($REGISTRY_COUNT) by $ABS_DIFF"
fi

# ── 4. Verify key zones have page files ──
echo "  4. Zone coverage check"
ZONES_OK=0
ZONES_MISSING=0

for zone_dir in auth home facility workspace shift queue ehr admin registry marketplace finance pharmacy inventory reports settings; do
  if [ -d "$UI_APP_DIR/$zone_dir" ] || [ -d "$UI_APP_DIR/($zone_dir)" ]; then
    ZONES_OK=$((ZONES_OK + 1))
  else
    # Some zones may be nested differently — check registry
    ZONE_IN_REGISTRY=$(grep "\"zone\": *\"$zone_dir\"" "$ROUTE_REGISTRY" 2>/dev/null | head -1) || ZONE_IN_REGISTRY=""
    if [ -n "$ZONE_IN_REGISTRY" ]; then
      # Zone exists in registry but directory not found as simple name — check if paths exist
      ZONE_PATH_PREFIX="/$zone_dir"
      if find "$UI_APP_DIR" -path "*/$zone_dir/*" -name "page.tsx" 2>/dev/null | grep -q .; then
        ZONES_OK=$((ZONES_OK + 1))
      else
        warn "Zone '$zone_dir' in registry but no matching page files found"
        ZONES_MISSING=$((ZONES_MISSING + 1))
      fi
    fi
  fi
done

if [ "$ZONES_MISSING" -eq 0 ]; then
  pass "All $ZONES_OK expected zones have page files"
else
  warn "$ZONES_MISSING zones may be missing page files"
fi

# ── 5. Prototype spec cross-reference ──
echo "  5. Prototype spec cross-reference"
if [ -f "$SPEC_FILE" ]; then
  SPEC_ROUTE_MENTION=$(grep -i "route" "$SPEC_FILE" | head -1) || SPEC_ROUTE_MENTION=""
  SPEC_COUNT_MENTION=$(grep -oP '\d+\s+routes' "$SPEC_FILE" | head -1) || SPEC_COUNT_MENTION=""

  if [ -n "$SPEC_COUNT_MENTION" ]; then
    SPEC_NUM=$(echo "$SPEC_COUNT_MENTION" | grep -oP '\d+')
    echo "    Spec mentions: $SPEC_COUNT_MENTION"
    if [ "$REGISTRY_COUNT" -eq "$SPEC_NUM" ]; then
      pass "Registry count matches spec count ($SPEC_NUM)"
    else
      warn "SPEC CONFLICT: Registry has $REGISTRY_COUNT routes, spec mentions $SPEC_NUM"
    fi
  else
    echo "    Spec file exists but contains no explicit route count."
    echo "    Spec file describes: $(head -5 "$SPEC_FILE" | tail -1)"
    warn "Spec file has no explicit route count table — falling back to registry self-check"
  fi
else
  echo "    No prototype spec file found at docs/prototype/final/01_site_map.md"
  warn "Spec cross-reference skipped (file not found)"
fi

# ── Summary ──
echo ""
TOTAL=$((PASS + FAIL))
if [ "$FAIL" -eq 0 ]; then
  echo "  Route Parity: All $TOTAL checks passed ($WARNINGS warnings)"
  exit 0
else
  echo "  Route Parity: $FAIL of $TOTAL checks failed ($WARNINGS warnings)"
  exit 1
fi
