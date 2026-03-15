#!/usr/bin/env bash
# check-service-minimums.sh — Verify every backend service meets minimum completeness criteria.
# Checks: pom.xml, Application.java, migration, test, security config, application.yml, outbox.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SERVICES_DIR="$REPO_ROOT/services"
FAILURES=0
WARNINGS=0
SERVICES_CHECKED=0

echo "═══════════════════════════════════════════════════════════════"
echo " Impilo vNext — Service Minimum Completeness Check"
echo " Run: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "═══════════════════════════════════════════════════════════════"

for svc_dir in "$SERVICES_DIR"/*/; do
  svc_name=$(basename "$svc_dir")
  [ "$svc_name" = "shared-core" ] && continue  # shared-core is a library, not a service

  SERVICES_CHECKED=$((SERVICES_CHECKED + 1))
  svc_issues=()
  svc_warns=()

  # 1. pom.xml
  [ ! -f "$svc_dir/pom.xml" ] && svc_issues+=("missing pom.xml")

  # 2. Application entrypoint
  app_file=$(find "$svc_dir" -name "*Application.java" 2>/dev/null | head -1)
  [ -z "$app_file" ] && svc_issues+=("missing Application.java")

  # 3. At least one migration
  mig_count=$(find "$svc_dir" -path "*/db/migration/V*.sql" 2>/dev/null | grep -c "" || true)
  [ "$mig_count" -eq 0 ] && svc_issues+=("no Flyway migrations")

  # 4. At least one test file
  test_count=$(find "$svc_dir/src/test" -name "*.java" 2>/dev/null | grep -c "" || true)
  [ "$test_count" -eq 0 ] && svc_issues+=("no tests")
  [ "$test_count" -eq 1 ] && svc_warns+=("only 1 test file (likely GoldenContractIT only)")

  # 5. SecurityConfig
  sec_config=$(find "$svc_dir" -name "SecurityConfig.java" -o -name "SecurityBaselineConfig.java" 2>/dev/null | head -1)
  [ -z "$sec_config" ] && svc_warns+=("no SecurityConfig.java")

  # 6. application.yml or application.properties
  app_cfg=$(find "$svc_dir" -name "application.yml" -o -name "application.properties" 2>/dev/null | head -1)
  [ -z "$app_cfg" ] && svc_issues+=("no application.yml/properties")

  # 7. Source file count (at least 5 for a real service)
  src_count=$(find "$svc_dir/src/main" -name "*.java" 2>/dev/null | grep -c "" || true)
  [ "$src_count" -lt 5 ] && svc_warns+=("very few source files ($src_count)")

  # 8. Outbox entity (most services should have one)
  outbox=$(find "$svc_dir" -name "*Outbox*" -o -name "*outbox*" 2>/dev/null | head -1)
  [ -z "$outbox" ] && svc_warns+=("no outbox pattern found")

  # Report
  if [ ${#svc_issues[@]} -gt 0 ]; then
    printf "  ❌ %-42s FAIL\n" "$svc_name"
    for issue in "${svc_issues[@]}"; do
      echo "       ├─ $issue"
      FAILURES=$((FAILURES + 1))
    done
    for warn in "${svc_warns[@]}"; do
      echo "       ├─ ⚠ $warn"
      WARNINGS=$((WARNINGS + 1))
    done
  elif [ ${#svc_warns[@]} -gt 0 ]; then
    printf "  ⚠  %-42s WARN\n" "$svc_name"
    for warn in "${svc_warns[@]}"; do
      echo "       ├─ $warn"
      WARNINGS=$((WARNINGS + 1))
    done
  else
    printf "  ✅ %-42s PASS\n" "$svc_name"
  fi
done

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo " Services checked: $SERVICES_CHECKED"
echo " Failures:         $FAILURES"
echo " Warnings:         $WARNINGS"
if [ "$FAILURES" -gt 0 ]; then
  echo " Result:           ❌ FAIL"
else
  echo " Result:           ✅ PASS (with $WARNINGS warnings)"
fi
echo "═══════════════════════════════════════════════════════════════"

[ "$FAILURES" -gt 0 ] && exit 1
exit 0
