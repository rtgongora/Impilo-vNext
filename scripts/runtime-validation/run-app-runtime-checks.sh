#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; BOLD='\033[1m'; NC='\033[0m'

PASS_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0

report_pass() { echo -e "${GREEN}[PASS]${NC} $1"; PASS_COUNT=$((PASS_COUNT + 1)); }
report_fail() { echo -e "${RED}[FAIL]${NC} $1"; FAIL_COUNT=$((FAIL_COUNT + 1)); }
report_skip() { echo -e "${YELLOW}[SKIP]${NC} $1"; SKIP_COUNT=$((SKIP_COUNT + 1)); }

echo -e "${BOLD}=== App Runtime Checks ===${NC}"

# 1. Experience UI
echo ""
echo -e "${BOLD}--- Experience UI ---${NC}"
HTTP_STATUS=$(curl -sf -o /dev/null -w "%{http_code}" "http://localhost:3000" 2>/dev/null) || HTTP_STATUS="000"
if [[ "$HTTP_STATUS" -ge 200 && "$HTTP_STATUS" -lt 400 ]]; then
  report_pass "Experience UI reachable (HTTP $HTTP_STATUS)"
else
  report_fail "Experience UI not reachable (HTTP $HTTP_STATUS)"
fi

# 2. Support Console type-check
echo ""
echo -e "${BOLD}--- Support Console ---${NC}"
SUPPORT_DIR="$ROOT_DIR/ui/support-console"
if [[ -d "$SUPPORT_DIR" && -f "$SUPPORT_DIR/package.json" ]]; then
  RC=0
  (cd "$SUPPORT_DIR" && npm run type-check 2>&1) || RC=$?
  if [[ "$RC" -eq 0 ]]; then
    report_pass "Support Console type-check"
  else
    report_fail "Support Console type-check"
  fi
else
  report_skip "Support Console (directory not found)"
fi

# 3. Developer Console type-check
echo ""
echo -e "${BOLD}--- Developer Console ---${NC}"
DEV_DIR="$ROOT_DIR/ui/developer-console"
if [[ -d "$DEV_DIR" && -f "$DEV_DIR/package.json" ]]; then
  RC=0
  (cd "$DEV_DIR" && npm run type-check 2>&1) || RC=$?
  if [[ "$RC" -eq 0 ]]; then
    report_pass "Developer Console type-check"
  else
    report_fail "Developer Console type-check"
  fi
else
  report_skip "Developer Console (directory not found)"
fi

# 4. Provider App
echo ""
echo -e "${BOLD}--- Provider App ---${NC}"
PROVIDER_DIR="$ROOT_DIR/ui/provider-app"
if [[ -d "$PROVIDER_DIR" ]]; then
  # Check eas.json
  if [[ -f "$PROVIDER_DIR/eas.json" ]]; then
    report_pass "Provider App: eas.json exists"
  else
    report_fail "Provider App: eas.json missing"
  fi

  # Check app.config.ts
  if [[ -f "$PROVIDER_DIR/app.config.ts" ]]; then
    report_pass "Provider App: app.config.ts exists"
  else
    report_fail "Provider App: app.config.ts missing"
  fi

  # Type-check
  if [[ -f "$PROVIDER_DIR/package.json" ]]; then
    RC=0
    (cd "$PROVIDER_DIR" && npm run type-check 2>&1) || RC=$?
    if [[ "$RC" -eq 0 ]]; then
      report_pass "Provider App: type-check"
    else
      report_fail "Provider App: type-check"
    fi
  fi
else
  report_skip "Provider App (directory not found)"
fi

# 5. Citizen App
echo ""
echo -e "${BOLD}--- Citizen App ---${NC}"
CITIZEN_DIR="$ROOT_DIR/ui/citizen-app"
if [[ -d "$CITIZEN_DIR" ]]; then
  # Check eas.json
  if [[ -f "$CITIZEN_DIR/eas.json" ]]; then
    report_pass "Citizen App: eas.json exists"
  else
    report_fail "Citizen App: eas.json missing"
  fi

  # Check app.config.ts
  if [[ -f "$CITIZEN_DIR/app.config.ts" ]]; then
    report_pass "Citizen App: app.config.ts exists"
  else
    report_fail "Citizen App: app.config.ts missing"
  fi

  # Type-check
  if [[ -f "$CITIZEN_DIR/package.json" ]]; then
    RC=0
    (cd "$CITIZEN_DIR" && npm run type-check 2>&1) || RC=$?
    if [[ "$RC" -eq 0 ]]; then
      report_pass "Citizen App: type-check"
    else
      report_fail "Citizen App: type-check"
    fi
  fi
else
  report_skip "Citizen App (directory not found)"
fi

# Summary
echo ""
echo "========================================="
echo -e "App Runtime: ${GREEN}${PASS_COUNT} passed${NC}, ${RED}${FAIL_COUNT} failed${NC}, ${YELLOW}${SKIP_COUNT} skipped${NC}"
echo "========================================="

if [[ "$FAIL_COUNT" -gt 0 ]]; then
  exit 1
fi
exit 0
