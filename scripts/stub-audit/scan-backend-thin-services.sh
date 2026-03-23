#!/usr/bin/env bash
# scan-backend-thin-services.sh — Measure backend service implementation depth
set -euo pipefail

echo "=== Stub Density Audit: Backend Service Depth Scan ==="
echo "Date: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
echo ""

printf "%-40s %5s %5s %5s %5s %5s  %s\n" "SERVICE" "JAVA" "CTRL" "MIG" "TEST" "REPO" "STATUS"
printf "%-40s %5s %5s %5s %5s %5s  %s\n" "-------" "----" "----" "---" "----" "----" "------"

for svc in services/*/; do
  name=$(basename "$svc")
  [ "$name" = "pom.xml" ] && continue

  java=$(find "$svc" -name "*.java" 2>/dev/null | wc -l)
  ctrl=$(find "$svc" -name "*Controller.java" 2>/dev/null | wc -l)
  mig=$(find "$svc" -name "V*.sql" 2>/dev/null | wc -l)
  test=$(find "$svc" -name "*Test*.java" -o -name "*IT.java" 2>/dev/null | wc -l)
  repo=$(find "$svc" -name "*Repository.java" 2>/dev/null | wc -l)

  if [ "$java" -eq 0 ]; then
    status="SHELL-ONLY"
  elif [ "$ctrl" -eq 0 ] && [ "$name" != "shared-core" ]; then
    status="LIBRARY"
  elif [ "$java" -lt 15 ]; then
    status="THIN"
  elif [ "$test" -le 1 ]; then
    status="ADEQUATE-LOW-TEST"
  elif [ "$java" -ge 30 ] && [ "$ctrl" -ge 3 ] && [ "$test" -ge 3 ]; then
    status="REAL"
  else
    status="ADEQUATE"
  fi

  printf "%-40s %5d %5d %5d %5d %5d  %s\n" "$name" "$java" "$ctrl" "$mig" "$test" "$repo" "$status"
done | sort -t' ' -k7

echo ""
echo "=== Scan complete ==="
