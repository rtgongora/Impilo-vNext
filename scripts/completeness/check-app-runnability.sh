#!/usr/bin/env bash
# check-app-runnability.sh — Verify every UI/mobile app has minimum runnability prerequisites.
# Checks: package.json, source files, next.config, tsconfig, dependencies.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FAILURES=0
WARNINGS=0
APPS_CHECKED=0

echo "═══════════════════════════════════════════════════════════════"
echo " Impilo vNext — App Runnability Check"
echo " Run: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "═══════════════════════════════════════════════════════════════"

check_web_app() {
  local app_dir="$1"
  local app_name="$2"
  local category="$3"
  APPS_CHECKED=$((APPS_CHECKED + 1))
  local issues=()
  local warns=()

  # package.json
  [ ! -f "$app_dir/package.json" ] && issues+=("missing package.json")

  # Source files
  local src_count
  src_count=$(find "$app_dir/src" -name "*.tsx" -o -name "*.ts" 2>/dev/null | grep -c "" || true)
  [ "$src_count" -eq 0 ] && issues+=("no source files in src/")
  [ "$src_count" -gt 0 ] && [ "$src_count" -lt 5 ] && warns+=("very few source files ($src_count)")

  # next.config (for Next.js apps)
  if [ "$app_name" != "shared-ui" ]; then
    local next_cfg
    next_cfg=$(find "$app_dir" -maxdepth 1 -name "next.config.*" 2>/dev/null | head -1)
    [ -z "$next_cfg" ] && warns+=("no next.config.* found")
  fi

  # tsconfig.json
  [ ! -f "$app_dir/tsconfig.json" ] && warns+=("no tsconfig.json")

  # Tests
  local test_count
  test_count=$(find "$app_dir" -name "*.test.*" -o -name "*.spec.*" 2>/dev/null | grep -c "" || true)
  [ "$test_count" -eq 0 ] && warns+=("no tests")

  # Report
  if [ ${#issues[@]} -gt 0 ]; then
    printf "  ❌ [%s] %-30s FAIL\n" "$category" "$app_name"
    for issue in "${issues[@]}"; do
      echo "       ├─ $issue"
      FAILURES=$((FAILURES + 1))
    done
    for warn in "${warns[@]}"; do
      echo "       ├─ ⚠ $warn"
      WARNINGS=$((WARNINGS + 1))
    done
  elif [ ${#warns[@]} -gt 0 ]; then
    printf "  ⚠  [%s] %-30s WARN\n" "$category" "$app_name"
    for warn in "${warns[@]}"; do
      echo "       ├─ $warn"
      WARNINGS=$((WARNINGS + 1))
    done
  else
    printf "  ✅ [%s] %-30s PASS\n" "$category" "$app_name"
  fi
}

echo ""
echo "▶ WEB UI APPLICATIONS"
echo "─────────────────────────────────────────────────────────────"
for app_dir in "$REPO_ROOT"/ui/*/; do
  app_name=$(basename "$app_dir")
  check_web_app "$app_dir" "$app_name" "web"
done

echo ""
echo "▶ MOBILE APPLICATIONS"
echo "─────────────────────────────────────────────────────────────"
for app_dir in "$REPO_ROOT"/apps/mobile/*/; do
  app_name=$(basename "$app_dir")
  [ "$app_name" = "packages" ] && continue
  APPS_CHECKED=$((APPS_CHECKED + 1))
  issues=()
  warns=()

  [ ! -f "$app_dir/package.json" ] && issues+=("missing package.json")

  src_count=$(find "$app_dir/src" -name "*.tsx" -o -name "*.ts" 2>/dev/null | grep -c "" || true)
  [ "$src_count" -eq 0 ] && issues+=("no source files")

  # React Native check
  if [ -f "$app_dir/package.json" ]; then
    rn_dep=$(grep -c "react-native" "$app_dir/package.json" 2>/dev/null || true)
    [ "$rn_dep" -eq 0 ] && warns+=("no react-native dependency")
  fi

  test_count=$(find "$app_dir" -name "*.test.*" -o -name "*.spec.*" 2>/dev/null | grep -c "" || true)
  [ "$test_count" -eq 0 ] && warns+=("no tests")

  if [ ${#issues[@]} -gt 0 ]; then
    printf "  ❌ [mobile] %-30s FAIL\n" "$app_name"
    for issue in "${issues[@]}"; do echo "       ├─ $issue"; FAILURES=$((FAILURES + 1)); done
    for warn in "${warns[@]}"; do echo "       ├─ ⚠ $warn"; WARNINGS=$((WARNINGS + 1)); done
  elif [ ${#warns[@]} -gt 0 ]; then
    printf "  ⚠  [mobile] %-30s WARN\n" "$app_name"
    for warn in "${warns[@]}"; do echo "       ├─ $warn"; WARNINGS=$((WARNINGS + 1)); done
  else
    printf "  ✅ [mobile] %-30s PASS\n" "$app_name"
  fi
done

echo ""
echo "▶ MOBILE SHARED PACKAGES"
echo "─────────────────────────────────────────────────────────────"
for pkg_dir in "$REPO_ROOT"/apps/mobile/packages/*/; do
  pkg_name=$(basename "$pkg_dir")
  src_count=$(find "$pkg_dir/src" -name "*.ts" -o -name "*.tsx" 2>/dev/null | grep -c "" || true)
  test_count=$(find "$pkg_dir" -name "*.test.*" 2>/dev/null | grep -c "" || true)
  if [ "$src_count" -eq 0 ]; then
    printf "  ❌ [pkg] %-30s FAIL — no source\n" "$pkg_name"
    FAILURES=$((FAILURES + 1))
  else
    status="✅"
    note=""
    [ "$test_count" -eq 0 ] && status="⚠ " && note=" (no tests)" && WARNINGS=$((WARNINGS + 1))
    printf "  %s [pkg] %-30s src=%-3s test=%-3s%s\n" "$status" "$pkg_name" "$src_count" "$test_count" "$note"
  fi
done

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo " Apps checked:  $APPS_CHECKED"
echo " Failures:      $FAILURES"
echo " Warnings:      $WARNINGS"
if [ "$FAILURES" -gt 0 ]; then
  echo " Result:        ❌ FAIL"
else
  echo " Result:        ✅ PASS (with $WARNINGS warnings)"
fi
echo "═══════════════════════════════════════════════════════════════"

[ "$FAILURES" -gt 0 ] && exit 1
exit 0
