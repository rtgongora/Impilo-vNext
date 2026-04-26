#!/usr/bin/env bash
##
## Impilo vNext — Experience Platform Healthcheck Validator
##
## Validates that all services in the compose stack are healthy.
##
## Usage:
##   ./tools/dev/healthcheck.sh
##
set -euo pipefail

BFF_URL="${BFF_URL:-http://localhost:8160}"
UI_URL="${UI_URL:-http://localhost:3000}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5433}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

HEALTHY=0
UNHEALTHY=0

check() {
  local name="$1"
  local cmd="$2"
  if eval "$cmd" > /dev/null 2>&1; then
    echo -e "  ${GREEN}HEALTHY${NC}: $name"
    HEALTHY=$((HEALTHY + 1))
  else
    echo -e "  ${RED}UNHEALTHY${NC}: $name"
    UNHEALTHY=$((UNHEALTHY + 1))
  fi
}

echo "=============================================="
echo "  Impilo vNext — Healthcheck Validator"
echo "=============================================="
echo ""

check "Experience DB (PostgreSQL)" "pg_isready -h $DB_HOST -p $DB_PORT -U impilo -d experience_bff 2>/dev/null || curl -sf http://$DB_HOST:$DB_PORT 2>/dev/null"
check "Experience BFF (/health)" "curl -sf $BFF_URL/health"
check "Experience BFF (v1.1 endpoint)" "curl -sf -o /dev/null -w '%{http_code}' $BFF_URL/internal/v1/facilities 2>/dev/null | grep -q '400'"
check "Experience UI (root)" "curl -sf -o /dev/null $UI_URL"

echo ""
echo "=============================================="
echo -e "  Results: ${GREEN}$HEALTHY healthy${NC}, ${RED}$UNHEALTHY unhealthy${NC}"
echo "=============================================="

if [ "$UNHEALTHY" -gt 0 ]; then
  exit 1
fi
