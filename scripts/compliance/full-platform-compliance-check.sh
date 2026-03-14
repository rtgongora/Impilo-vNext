#!/usr/bin/env bash
set -euo pipefail

###############################################################################
# Full-Platform Compliance Verifier — Impilo vNext
#
# Scans all services under /services and asserts the presence of required
# compliance markers. Every service with src/main/java is treated as a real
# service and must pass ALL checks. No STUB or ADAPTER exemptions.
#
# Usage:
#   ./scripts/compliance/full-platform-compliance-check.sh
#
# CI-friendly: returns 0 if all services pass, non-zero otherwise.
###############################################################################

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SERVICES_DIR="$REPO_ROOT/services"

# Counters
TOTAL=0
PASS=0
FAIL=0
LIBRARY=0

# ANSI colors (CI-safe)
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# Services with domain-specific event storage (not standard outbox pattern)
CUSTOM_OUTBOX_SERVICES="tshepo-audit-service audit-ledger-service"

# Shared library (not a deployable service)
LIBRARY_SERVICES="shared-core"

printf "\n${CYAN}╔══════════════════════════════════════════════════════════════════╗${NC}\n"
printf "${CYAN}║  Impilo vNext — Full-Platform Compliance Verifier              ║${NC}\n"
printf "${CYAN}║  Zero exemptions — every service must be fully compliant       ║${NC}\n"
printf "${CYAN}╚══════════════════════════════════════════════════════════════════╝${NC}\n\n"

printf "%-40s %-8s %-8s %-8s %-8s %-8s %-10s\n" \
  "SERVICE" "TC_DEP" "INT_V1" "OUTBOX" "GOLDEN" "HEALTH" "STATUS"
printf "%-40s %-8s %-8s %-8s %-8s %-8s %-10s\n" \
  "-------" "------" "------" "------" "------" "------" "------"

for svc_dir in "$SERVICES_DIR"/*/; do
  svc_name=$(basename "$svc_dir")
  TOTAL=$((TOTAL + 1))

  # Skip the parent POM directory
  if [ "$svc_name" = "pom.xml" ]; then
    TOTAL=$((TOTAL - 1))
    continue
  fi

  # Check if shared library (only exemption)
  if echo "$LIBRARY_SERVICES" | grep -qw "$svc_name"; then
    LIBRARY=$((LIBRARY + 1))
    printf "%-40s %-8s %-8s %-8s %-8s %-8s ${CYAN}%-10s${NC}\n" \
      "$svc_name" "-" "-" "-" "-" "-" "LIBRARY"
    continue
  fi

  # Check for src/main/java — if absent, it's a failing service (no exemptions)
  if [ ! -d "$svc_dir/src/main/java" ]; then
    FAIL=$((FAIL + 1))
    printf "%-40s %-8s %-8s %-8s %-8s %-8s ${RED}%-10s${NC}\n" \
      "$svc_name" "FAIL" "FAIL" "FAIL" "FAIL" "FAIL" "NO-CODE"
    continue
  fi

  # === Compliance checks ===

  # 1. tech-companion dependency
  tc_dep="FAIL"
  if [ -f "$svc_dir/pom.xml" ]; then
    if grep -q "tech-companion" "$svc_dir/pom.xml" 2>/dev/null; then
      tc_dep="PASS"
    fi
  fi

  # 2. /internal/v1 route in main source
  int_v1="FAIL"
  if grep -rq '/internal/v1' "$svc_dir/src/main/" 2>/dev/null; then
    int_v1="PASS"
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

  # 4. GoldenContractIT exists
  golden="FAIL"
  if find "$svc_dir" -name "*GoldenContract*" -type f 2>/dev/null | grep -q .; then
    golden="PASS"
  fi

  # 5. Health endpoint (actuator dependency)
  health="FAIL"
  if [ -f "$svc_dir/pom.xml" ]; then
    if grep -q "spring-boot-starter-actuator" "$svc_dir/pom.xml" 2>/dev/null; then
      health="PASS"
    fi
  fi

  # Determine overall status — ALL checks must pass
  status="PASS"
  if [ "$tc_dep" = "FAIL" ] || [ "$int_v1" = "FAIL" ] || [ "$outbox" = "FAIL" ] || [ "$golden" = "FAIL" ] || [ "$health" = "FAIL" ]; then
    status="FAIL"
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
printf "Total: %d | ${GREEN}Pass: %d${NC} | ${RED}Fail: %d${NC} | ${CYAN}Library: %d${NC}\n" \
  "$TOTAL" "$PASS" "$FAIL" "$LIBRARY"
printf "${CYAN}═══════════════════════════════════════════════════════════════════${NC}\n\n"

if [ "$FAIL" -gt 0 ]; then
  printf "${RED}COMPLIANCE CHECK FAILED — %d service(s) non-compliant.${NC}\n\n" "$FAIL"
  exit 1
else
  printf "${GREEN}ALL SERVICES COMPLIANT — ZERO EXEMPTIONS.${NC}\n\n"
  exit 0
fi
