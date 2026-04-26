#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; BOLD='\033[1m'; NC='\033[0m'

TIMEOUT="${BOOT_TIMEOUT:-120}"

# Check Docker daemon
echo -e "${BOLD}=== Boot Runtime: Docker Check ===${NC}"
if ! docker info &>/dev/null; then
  echo -e "${RED}[FAIL] Docker daemon is not running. Aborting.${NC}"
  exit 1
fi
echo -e "${GREEN}[OK]${NC} Docker daemon is running"

# Start services
echo ""
echo -e "${BOLD}=== Starting docker compose ===${NC}"
(cd "$ROOT_DIR" && docker compose -f docker-compose.runtime.yml up -d)
echo -e "${GREEN}[OK]${NC} docker compose up -d issued"

# Service health check definitions
# Format: name|port|path (empty path = TCP-only check)
SERVICES=(
  "postgres|5432|"
  "redis|6379|"
  "kafka|9092|"
  "keycloak|8080|/health/ready"
  "tshepo|8081|/actuator/health"
  "vito|8082|/actuator/health"
  "varapi|8083|/actuator/health"
  "tuso|8084|/actuator/health"
  "zibo|8085|/actuator/health"
  "pct|8088|/actuator/health"
  "oros|8089|/actuator/health"
  "hapi-fhir|8090|/fhir/metadata"
  "experience-bff|8160|/actuator/health"
  "envoy|9901|/ready"
  "opa|8181|/health"
  "one-ui-shell|3000|"
)

check_tcp() {
  local port="$1"
  (echo >/dev/tcp/localhost/"$port") 2>/dev/null
}

check_http() {
  local port="$1" path="$2"
  local status
  status=$(curl -sf -o /dev/null -w "%{http_code}" "http://localhost:${port}${path}" 2>/dev/null) || return 1
  [[ "$status" -ge 200 && "$status" -lt 400 ]]
}

check_service() {
  local name="$1" port="$2" path="$3"
  if [[ -z "$path" ]]; then
    check_tcp "$port"
  else
    check_http "$port" "$path"
  fi
}

# Wait for all services
echo ""
echo -e "${BOLD}=== Waiting for services (timeout: ${TIMEOUT}s) ===${NC}"

declare -A SVC_STATUS
for svc in "${SERVICES[@]}"; do
  IFS='|' read -r name _ _ <<< "$svc"
  SVC_STATUS["$name"]="pending"
done

ELAPSED=0
INTERVAL=5
ALL_HEALTHY=0

while [[ "$ELAPSED" -lt "$TIMEOUT" ]]; do
  ALL_HEALTHY=1
  for svc in "${SERVICES[@]}"; do
    IFS='|' read -r name port path <<< "$svc"
    if [[ "${SVC_STATUS[$name]}" == "healthy" ]]; then
      continue
    fi
    if check_service "$name" "$port" "$path"; then
      SVC_STATUS["$name"]="healthy"
    else
      ALL_HEALTHY=0
    fi
  done

  if [[ "$ALL_HEALTHY" -eq 1 ]]; then
    break
  fi

  sleep "$INTERVAL"
  ELAPSED=$((ELAPSED + INTERVAL))
  echo "  ... ${ELAPSED}s elapsed, waiting for remaining services"
done

# Boot matrix
echo ""
echo -e "${BOLD}=== Boot Matrix ===${NC}"
printf "%-20s %-8s %s\n" "SERVICE" "PORT" "STATUS"
printf "%-20s %-8s %s\n" "-------" "----" "------"

FAIL_COUNT=0
for svc in "${SERVICES[@]}"; do
  IFS='|' read -r name port path <<< "$svc"
  if [[ "${SVC_STATUS[$name]}" == "healthy" ]]; then
    printf "%-20s %-8s ${GREEN}%s${NC}\n" "$name" "$port" "HEALTHY"
  else
    printf "%-20s %-8s ${RED}%s${NC}\n" "$name" "$port" "UNHEALTHY"
    FAIL_COUNT=$((FAIL_COUNT + 1))
  fi
done

echo ""
if [[ "$FAIL_COUNT" -gt 0 ]]; then
  echo -e "${RED}${FAIL_COUNT} service(s) did not become healthy within ${TIMEOUT}s.${NC}"
  exit 1
fi

echo -e "${GREEN}All services healthy.${NC}"
exit 0
