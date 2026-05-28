#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; BOLD='\033[1m'; NC='\033[0m'

PASS_COUNT=0
FAIL_COUNT=0
CRITICAL_FAIL=0

report() {
  local label="$1" rc="$2"
  if [[ "$rc" -eq 0 ]]; then
    echo -e "${GREEN}[PASS]${NC} $label"
    PASS_COUNT=$((PASS_COUNT + 1))
  else
    echo -e "${RED}[FAIL]${NC} $label"
    FAIL_COUNT=$((FAIL_COUNT + 1))
  fi
}

echo -e "${BOLD}=== Build-All: Prerequisite Check ===${NC}"

MISSING=0
for cmd in mvn node npm; do
  if command -v "$cmd" &>/dev/null; then
    echo -e "${GREEN}[OK]${NC} $cmd found: $(command -v "$cmd")"
  else
    echo -e "${RED}[MISSING]${NC} $cmd not found"
    MISSING=1
  fi
done

if [[ "$MISSING" -eq 1 ]]; then
  echo -e "${RED}Prerequisites missing. Aborting.${NC}"
  exit 1
fi

# Step 1: Build Java services
echo ""
echo -e "${BOLD}=== Step 1: Build Java libs + services ===${NC}"
RC=0
(cd "$ROOT_DIR/services" && mvn -B clean package -DskipTests -T 1C --fail-at-end) || RC=$?
report "Java libs + services build" "$RC"
if [[ "$RC" -ne 0 ]]; then
  CRITICAL_FAIL=1
fi

# Step 2: Build Experience UI
echo ""
echo -e "${BOLD}=== Step 2: Build Experience UI ===${NC}"
RC=0
(cd "$ROOT_DIR/ui/one-ui-shell" && npm install --legacy-peer-deps && npm run build) || RC=$?
report "Experience UI build" "$RC"
if [[ "$RC" -ne 0 ]]; then
  CRITICAL_FAIL=1
fi

# Step 3: Type-check shared-kernel
echo ""
echo -e "${BOLD}=== Step 3: Type-check shared-kernel ===${NC}"
RC=0
(cd "$ROOT_DIR/libs/shared-kernel" && npm install && npx tsc --noEmit) || RC=$?
report "shared-kernel type-check" "$RC"
if [[ "$RC" -ne 0 ]]; then
  CRITICAL_FAIL=1
fi

# Summary
echo ""
echo "========================================="
echo -e "Build Results: ${GREEN}${PASS_COUNT} passed${NC}, ${RED}${FAIL_COUNT} failed${NC}"
echo "========================================="

if [[ "$CRITICAL_FAIL" -eq 1 ]]; then
  echo -e "${RED}One or more critical builds failed.${NC}"
  exit 1
fi

echo -e "${GREEN}All builds passed.${NC}"
exit 0
