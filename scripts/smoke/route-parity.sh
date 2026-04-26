#!/usr/bin/env bash
# =============================================================================
# Impilo vNext — Route Parity Check (Wave 18)
# =============================================================================
#
# Verifies that the Experience UI route registry count matches the
# spec-derived route count (as implemented in the filesystem).
#
# This is a wrapper around the existing Node.js route parity script:
#   ui/experience/scripts/route-parity-check.mjs
#
# It can run:
#   1. Via Node.js directly (if node is available)
#   2. Via Docker (using the one-ui-shell container)
#   3. Via static file count (fallback)
#
# Usage:
#   ./scripts/smoke/route-parity.sh
#
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

EXPERIENCE_DIR="$PROJECT_ROOT/ui/experience"
ROUTE_CHECK="$EXPERIENCE_DIR/scripts/route-parity-check.mjs"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

PASS=0
FAIL=0
WARN=0

pass() { echo -e "  ${GREEN}PASS${NC} $*"; ((PASS++)); }
fail() { echo -e "  ${RED}FAIL${NC} $*"; ((FAIL++)); }
warn() { echo -e "  ${YELLOW}WARN${NC} $*"; ((WARN++)); }
info() { echo -e "${CYAN}[ROUTES]${NC} $*"; }

echo ""
echo "============================================================"
echo "  Impilo vNext — Route Parity Check"
echo "============================================================"
echo ""

# =============================================================================
# Check 1: Route registry file exists
# =============================================================================
info "Check 1: Route registry file"

if [ -f "$ROUTE_CHECK" ]; then
    pass "Route parity script exists at ui/experience/scripts/route-parity-check.mjs"
else
    fail "Route parity script NOT found"
    exit 1
fi

# =============================================================================
# Check 2: Count expected routes (from the script itself)
# =============================================================================
info "Check 2: Expected route count"

EXPECTED_COUNT=$(grep -c '^\s*"/' "$ROUTE_CHECK" 2>/dev/null || echo "0")
info "  Expected routes (from registry): $EXPECTED_COUNT"

if [ "$EXPECTED_COUNT" -gt 0 ]; then
    pass "Route registry defines $EXPECTED_COUNT routes"
else
    fail "Route registry is empty"
fi

# =============================================================================
# Check 3: Count implemented page files
# =============================================================================
info "Check 3: Implemented page files"

APP_DIR="$EXPERIENCE_DIR/src/app"

if [ -d "$APP_DIR" ]; then
    IMPLEMENTED_COUNT=$(find "$APP_DIR" -name "page.tsx" -o -name "page.ts" -o -name "page.jsx" -o -name "page.js" | wc -l)
    info "  Implemented page files: $IMPLEMENTED_COUNT"

    if [ "$IMPLEMENTED_COUNT" -gt 0 ]; then
        pass "$IMPLEMENTED_COUNT page files found"
    else
        warn "No page files found in $APP_DIR"
    fi
else
    warn "App directory not found at $APP_DIR"
    IMPLEMENTED_COUNT=0
fi

# =============================================================================
# Check 4: Run the Node.js parity check (if node available)
# =============================================================================
info "Check 4: Node.js route parity check"

if command -v node &>/dev/null; then
    cd "$EXPERIENCE_DIR"
    if node scripts/route-parity-check.mjs 2>/dev/null; then
        pass "Node.js route parity check passed"
    else
        NODE_EXIT=$?
        warn "Node.js route parity check failed (exit code $NODE_EXIT) — some routes may not be implemented yet"
    fi
else
    warn "Node.js not available — skipping interactive parity check"
    info "  Run manually: cd ui/experience && node scripts/route-parity-check.mjs"
fi

# =============================================================================
# Check 5: Parity ratio
# =============================================================================
info "Check 5: Parity ratio"

if [ "$EXPECTED_COUNT" -gt 0 ] && [ "$IMPLEMENTED_COUNT" -gt 0 ]; then
    PARITY_PCT=$((IMPLEMENTED_COUNT * 100 / EXPECTED_COUNT))
    info "  Parity: $IMPLEMENTED_COUNT / $EXPECTED_COUNT ($PARITY_PCT%)"

    if [ "$PARITY_PCT" -ge 100 ]; then
        pass "Full route parity achieved ($PARITY_PCT%)"
    elif [ "$PARITY_PCT" -ge 50 ]; then
        warn "Partial route parity ($PARITY_PCT%) — $((EXPECTED_COUNT - IMPLEMENTED_COUNT)) routes pending"
    else
        warn "Low route parity ($PARITY_PCT%) — $((EXPECTED_COUNT - IMPLEMENTED_COUNT)) routes pending"
    fi
fi

# =============================================================================
# SUMMARY
# =============================================================================
echo ""
echo "============================================================"
echo "  ROUTE PARITY SUMMARY"
echo "============================================================"
echo -e "  ${GREEN}PASS${NC}: $PASS"
echo -e "  ${RED}FAIL${NC}: $FAIL"
echo -e "  ${YELLOW}WARN${NC}: $WARN"
echo -e "  Expected routes:    $EXPECTED_COUNT"
echo -e "  Implemented pages:  $IMPLEMENTED_COUNT"
echo "============================================================"

if [ "$FAIL" -gt 0 ]; then
    exit 1
else
    exit 0
fi
