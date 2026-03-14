#!/usr/bin/env bash
set -euo pipefail

###############################################################################
# Full-Platform Compliance Verifier — Impilo vNext
#
# Scans all services under /services and asserts the presence of required
# compliance markers. Fails non-zero if a real service is missing mandatory
# items. Distinguishes STUB services from failing real services.
#
# Usage:
#   ./scripts/compliance/full-platform-compliance-check.sh
#
# CI-friendly: returns 0 if all real services pass, non-zero otherwise.
###############################################################################

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SERVICES_DIR="$REPO_ROOT/services"

# Counters
TOTAL=0
PASS=0
FAIL=0
STUB=0
ADAPTER=0

# ANSI colors (CI-safe)
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# STUB services (no meaningful runtime code)
STUB_SERVICES="butano-fhir fhir-gateway-service inpatient-service jobs-service offline-sync-service pacs-adapter-service product-registry-service"

# Headless adapter services (no API surface — exempt from /internal/v1 requirement)
ADAPTER_SERVICES="inventory-elmis-adapter pharmacy-elmis-adapter"

# Services with domain-specific event storage (not standard outbox pattern)
CUSTOM_OUTBOX_SERVICES="tshepo-audit-service audit-ledger-service"

# Shared library (not a deployable service)
LIBRARY_SERVICES="shared-core"

printf "\n${CYAN}╔══════════════════════════════════════════════════════════════════╗${NC}\n"
printf "${CYAN}║  Impilo vNext — Full-Platform Compliance Verifier              ║${NC}\n"
printf "${CYAN}╚══════════════════════════════════════════════════════════════════╝${NC}\n\n"

printf "%-40s %-8s %-8s %-8s %-8s %-8s %-10s\n" \
  "SERVICE" "TC_DEP" "INT_V1" "OUTBOX" "GOLDEN" "HEALTH" "STATUS"
printf "%-40s %-8s %-8s %-8s %-8s %-8s %-10s\n" \
  "-------" "------" "------" "------" "------" "------" "------"

for svc_dir in "$SERVICES_DIR"/*/; do
  svc_name=$(basename "$svc_dir")
  TOTAL=$((TOTAL + 1))

  # Skip the parent POM
  if [ "$svc_name" = "pom.xml" ]; then
    TOTAL=$((TOTAL - 1))
    continue
  fi

  # Check if STUB
  if echo "$STUB_SERVICES" | grep -qw "$svc_name"; then
    STUB=$((STUB + 1))
    printf "%-40s %-8s %-8s %-8s %-8s %-8s ${YELLOW}%-10s${NC}\n" \
      "$svc_name" "-" "-" "-" "-" "-" "STUB"
    continue
  fi

  # Check if shared library
  if echo "$LIBRARY_SERVICES" | grep -qw "$svc_name"; then
    STUB=$((STUB + 1))
    printf "%-40s %-8s %-8s %-8s %-8s %-8s ${CYAN}%-10s${NC}\n" \
      "$svc_name" "-" "-" "-" "-" "-" "LIBRARY"
    continue
  fi

  # Check if headless adapter
  is_adapter=false
  if echo "$ADAPTER_SERVICES" | grep -qw "$svc_name"; then
    is_adapter=true
  fi

  # Check for src/main/java
  if [ ! -d "$svc_dir/src/main/java" ]; then
    STUB=$((STUB + 1))
    printf "%-40s %-8s %-8s %-8s %-8s %-8s ${YELLOW}%-10s${NC}\n" \
      "$svc_name" "-" "-" "-" "-" "-" "NO-CODE"
    continue
  fi

  # === Compliance checks ===

  # 1. tech-companion dependency (adapters exempt — no API surface)
  tc_dep="FAIL"
  if [ -f "$svc_dir/pom.xml" ]; then
    if grep -q "tech-companion" "$svc_dir/pom.xml" 2>/dev/null; then
      tc_dep="PASS"
    fi
  fi
  if $is_adapter; then
    tc_dep="N/A"
  fi

  # 2. /internal/v1 route in main source
  int_v1="FAIL"
  if grep -rq '/internal/v1' "$svc_dir/src/main/" 2>/dev/null; then
    int_v1="PASS"
  fi
  # Adapters are exempt
  if $is_adapter; then
    int_v1="N/A"
  fi

  # 3. Outbox table present (or domain-specific event storage)
  outbox="FAIL"
  if grep -rq 'event_outbox' "$svc_dir/src/" 2>/dev/null; then
    outbox="PASS"
  elif grep -rq 'event_outbox\|EventOutbox\|outbox' "$svc_dir/src/main/resources/db/" 2>/dev/null; then
    outbox="PASS"
  elif echo "$CUSTOM_OUTBOX_SERVICES" | grep -qw "$svc_name"; then
    outbox="PASS"
  fi
  if $is_adapter; then
    outbox="N/A"
  fi

  # 4. GoldenContractIT exists
  golden="FAIL"
  if find "$svc_dir" -name "*GoldenContract*" -type f 2>/dev/null | grep -q .; then
    golden="PASS"
  fi
  if $is_adapter; then
    golden="N/A"
  fi

  # 5. Health endpoint (actuator dependency)
  health="FAIL"
  if [ -f "$svc_dir/pom.xml" ]; then
    if grep -q "spring-boot-starter-actuator" "$svc_dir/pom.xml" 2>/dev/null; then
      health="PASS"
    fi
  fi

  # Determine overall status
  status="PASS"
  if $is_adapter; then
    if [ "$health" = "FAIL" ]; then
      status="FAIL"
    fi
  else
    if [ "$tc_dep" = "FAIL" ] || [ "$int_v1" = "FAIL" ] || [ "$outbox" = "FAIL" ] || [ "$golden" = "FAIL" ] || [ "$health" = "FAIL" ]; then
      status="FAIL"
    fi
  fi

  if $is_adapter; then
    ADAPTER=$((ADAPTER + 1))
    if [ "$status" = "PASS" ]; then
      printf "%-40s %-8s %-8s %-8s %-8s %-8s ${YELLOW}%-10s${NC}\n" \
        "$svc_name" "$tc_dep" "$int_v1" "$outbox" "$golden" "$health" "ADAPTER"
    else
      FAIL=$((FAIL + 1))
      printf "%-40s %-8s %-8s %-8s %-8s %-8s ${RED}%-10s${NC}\n" \
        "$svc_name" "$tc_dep" "$int_v1" "$outbox" "$golden" "$health" "FAIL"
    fi
    continue
  fi

  if [ "$status" = "PASS" ]; then
    PASS=$((PASS + 1))
    printf "%-40s %-8s %-8s %-8s %-8s %-8s ${GREEN}%-10s${NC}\n" \
      "$svc_name" "$tc_dep" "$int_v1" "$outbox" "$golden" "$health" "PASS"
  else
    FAIL=$((FAIL + 1))
    printf "%-40s %-8s %-8s %-8s %-8s %-8s ${RED}%-10s${NC}\n" \
      "$svc_name" "$tc_dep" "$int_v1" "$outbox" "$golden" "$health" "FAIL"
  fi
done

printf "\n${CYAN}═══════════════════════════════════════════════════════════════════${NC}\n"
printf "Total: %d | ${GREEN}Pass: %d${NC} | ${RED}Fail: %d${NC} | ${YELLOW}Stub/Library: %d${NC} | Adapter: %d\n" \
  "$TOTAL" "$PASS" "$FAIL" "$STUB" "$ADAPTER"
printf "${CYAN}═══════════════════════════════════════════════════════════════════${NC}\n\n"

if [ "$FAIL" -gt 0 ]; then
  printf "${RED}COMPLIANCE CHECK FAILED — %d service(s) non-compliant.${NC}\n\n" "$FAIL"
  exit 1
else
  printf "${GREEN}ALL REAL SERVICES COMPLIANT.${NC}\n\n"
  exit 0
fi
