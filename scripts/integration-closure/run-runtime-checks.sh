#!/usr/bin/env bash
set -euo pipefail

echo "============================================="
echo "  Impilo vNext — Runtime Health Checks"
echo "============================================="

SERVICES=(
  "PostgreSQL|localhost:5432|pg_isready"
  "Redis|localhost:6379|redis-cli ping"
  "Kafka|localhost:9092|kafka"
  "Keycloak|localhost:8080/health/ready|http"
  "TSHEPO|localhost:8081/actuator/health|http"
  "VITO|localhost:8082/actuator/health|http"
  "VARAPI|localhost:8083/actuator/health|http"
  "TUSO|localhost:8084/actuator/health|http"
  "ZIBO|localhost:8085/actuator/health|http"
  "PCT|localhost:8088/actuator/health|http"
  "OROS|localhost:8089/actuator/health|http"
  "HAPI FHIR|localhost:8090/fhir/metadata|http"
  "Experience BFF|localhost:8160/actuator/health|http"
  "Envoy|localhost:9901/ready|http"
  "OPA|localhost:8181/health|http"
  "Experience UI|localhost:3020|http"
)

PASS=0
FAIL=0

for svc in "${SERVICES[@]}"; do
  IFS='|' read -r name endpoint check_type <<< "$svc"
  if [[ "$check_type" == "http" ]]; then
    if curl -sf "http://$endpoint" > /dev/null 2>&1; then
      echo -e "\033[0;32m[OK]\033[0m $name ($endpoint)"
      PASS=$((PASS + 1))
    else
      echo -e "\033[0;31m[DOWN]\033[0m $name ($endpoint)"
      FAIL=$((FAIL + 1))
    fi
  else
    # Non-HTTP checks — just try to connect
    host=$(echo "$endpoint" | cut -d: -f1)
    port=$(echo "$endpoint" | cut -d: -f2 | cut -d/ -f1)
    if (echo > /dev/tcp/$host/$port) 2>/dev/null; then
      echo -e "\033[0;32m[OK]\033[0m $name ($endpoint)"
      PASS=$((PASS + 1))
    else
      echo -e "\033[0;31m[DOWN]\033[0m $name ($endpoint)"
      FAIL=$((FAIL + 1))
    fi
  fi
done

echo ""
echo "Results: $PASS healthy, $FAIL down out of $((PASS + FAIL))"
[[ $FAIL -eq 0 ]] && exit 0 || exit 1
