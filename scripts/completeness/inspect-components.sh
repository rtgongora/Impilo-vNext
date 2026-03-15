#!/usr/bin/env bash
# inspect-components.sh — Enumerate and classify every component in the Impilo vNext monorepo.
# Exit codes: 0 = all components found, 1 = missing components detected.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
EXIT_CODE=0
MISSING=()

echo "═══════════════════════════════════════════════════════════════"
echo " Impilo vNext — Component Inventory Inspector"
echo " Run: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "═══════════════════════════════════════════════════════════════"

# ── 1. Shared Libraries ───────────────────────────────────────────
echo ""
echo "▶ SHARED LIBRARIES"
echo "─────────────────────────────────────────────────────────────"
EXPECTED_LIBS=(
  tech-companion tech-companion-harness tech-companion-mock
  shared-kernel shared-kernel-java
  tshepo-contracts tshepo-sdk
  security-baseline ops-instrumentation federation-connector
  contract-tests offline-sdk
)
for lib in "${EXPECTED_LIBS[@]}"; do
  dir="$REPO_ROOT/libs/$lib"
  if [ -d "$dir" ]; then
    src_count=$(find "$dir" -name "*.java" -o -name "*.ts" | grep -c "" || true)
    test_count=$(find "$dir" -name "*Test.java" -o -name "*.test.ts" -o -name "*.test.js" | grep -c "" || true)
    build="none"
    [ -f "$dir/pom.xml" ] && build="maven"
    [ -f "$dir/package.json" ] && build="npm"
    printf "  ✅ %-30s  src=%-4s test=%-3s build=%s\n" "$lib" "$src_count" "$test_count" "$build"
  else
    printf "  ❌ %-30s  MISSING\n" "$lib"
    MISSING+=("libs/$lib")
    EXIT_CODE=1
  fi
done

# ── 2. Backend Services ──────────────────────────────────────────
echo ""
echo "▶ BACKEND SERVICES"
echo "─────────────────────────────────────────────────────────────"
EXPECTED_SERVICES=(
  tshepo-service tshepo-authz-service tshepo-identity-service
  tshepo-consent-service tshepo-audit-service tshepo-keys-service
  tshepo-offline-service
  vito-service varapi-service tuso-service zibo-service
  msika-service msika-flow-service product-registry-service
  butano-service butano-fhir mushex-service ubomi-service indawo-service
  pct-service oros-service pharmacy-service inpatient-service
  costing-engine-service inventory-service
  document-service landela-adapter-service
  jobs-service offline-sync-service offline-edge-service
  pacs-adapter-service fhir-gateway-service connector-fhir-adapter
  experience-bff card-print-agent share-slip-service
  pharmacy-elmis-adapter inventory-elmis-adapter
  coverage-service credential-verification-service
  integration-hub rules-service notification-service
  forms-service search-service workflow-service
  data-ingestion-service data-governance-service data-access-governance-service
  data-warehouse-service data-pipeline-service surveillance-service
  asset-registry-service dispatch-service iot-ingestion-service
  observability-service security-hardening-service
  support-service developer-portal-service schema-registry-service
  audit-ledger-service reporting-service national-data-repository-service
  identity-assurance-service campaigns-service channels-service ndr-service
  mushex-service
)
# Deduplicate
EXPECTED_SERVICES=($(printf '%s\n' "${EXPECTED_SERVICES[@]}" | sort -u))

for svc in "${EXPECTED_SERVICES[@]}"; do
  dir="$REPO_ROOT/services/$svc"
  if [ -d "$dir" ]; then
    pom=$([ -f "$dir/pom.xml" ] && echo "Y" || echo "N")
    app=$(find "$dir" -name "*Application.java" 2>/dev/null | head -1)
    app_flag=$([ -n "$app" ] && echo "Y" || echo "N")
    src_count=$(find "$dir/src/main" -name "*.java" 2>/dev/null | grep -c "" || true)
    test_count=$(find "$dir/src/test" -name "*.java" 2>/dev/null | grep -c "" || true)
    mig_count=$(find "$dir" -path "*/db/migration/V*.sql" 2>/dev/null | grep -c "" || true)
    helm_flag=$([ -d "$dir/helm" ] && echo "Y" || echo "N")
    printf "  ✅ %-42s pom=%s app=%s src=%-4s test=%-3s mig=%-2s helm=%s\n" \
      "$svc" "$pom" "$app_flag" "$src_count" "$test_count" "$mig_count" "$helm_flag"
  else
    printf "  ❌ %-42s MISSING\n" "$svc"
    MISSING+=("services/$svc")
    EXIT_CODE=1
  fi
done

# ── 3. UI Applications ───────────────────────────────────────────
echo ""
echo "▶ UI APPLICATIONS"
echo "─────────────────────────────────────────────────────────────"
EXPECTED_UIS=(
  one-ui-shell experience portal self-service ehr
  ops-console mushex-ops-console mushex-finance-console mushex-payer-portal
  msika-web msika-flow-portal msika-flow-ops msika-flow-vendor
  costa-console inventory-web pharmacy-web zibo-web pct-web oros-web butano-web
  support-console developer-console ops-docs shared-ui
)
for ui_app in "${EXPECTED_UIS[@]}"; do
  dir="$REPO_ROOT/ui/$ui_app"
  if [ -d "$dir" ]; then
    pkg=$([ -f "$dir/package.json" ] && echo "Y" || echo "N")
    src_count=$(find "$dir/src" -name "*.tsx" -o -name "*.ts" 2>/dev/null | grep -c "" || true)
    test_count=$(find "$dir" -name "*.test.*" -o -name "*.spec.*" 2>/dev/null | grep -c "" || true)
    printf "  ✅ %-30s pkg=%s src=%-4s test=%-3s\n" "$ui_app" "$pkg" "$src_count" "$test_count"
  else
    printf "  ❌ %-30s MISSING\n" "$ui_app"
    MISSING+=("ui/$ui_app")
    EXIT_CODE=1
  fi
done

# ── 4. Mobile Applications ───────────────────────────────────────
echo ""
echo "▶ MOBILE APPLICATIONS"
echo "─────────────────────────────────────────────────────────────"
EXPECTED_MOBILE=(citizen-app provider-app)
for app in "${EXPECTED_MOBILE[@]}"; do
  dir="$REPO_ROOT/apps/mobile/$app"
  if [ -d "$dir" ]; then
    src_count=$(find "$dir/src" -name "*.tsx" -o -name "*.ts" 2>/dev/null | grep -c "" || true)
    test_count=$(find "$dir" -name "*.test.*" -o -name "*.spec.*" 2>/dev/null | grep -c "" || true)
    printf "  ✅ %-30s src=%-4s test=%-3s\n" "$app" "$src_count" "$test_count"
  else
    printf "  ❌ %-30s MISSING\n" "$app"
    MISSING+=("apps/mobile/$app")
    EXIT_CODE=1
  fi
done

EXPECTED_MOBILE_PKGS=(
  mobile-api-client mobile-auth mobile-design-system
  mobile-messaging mobile-offline mobile-timeline mobile-trust
)
for pkg in "${EXPECTED_MOBILE_PKGS[@]}"; do
  dir="$REPO_ROOT/apps/mobile/packages/$pkg"
  if [ -d "$dir" ]; then
    src_count=$(find "$dir/src" -name "*.ts" -o -name "*.tsx" 2>/dev/null | grep -c "" || true)
    printf "  ✅ %-30s src=%-4s\n" "$pkg" "$src_count"
  else
    printf "  ❌ %-30s MISSING\n" "$pkg"
    MISSING+=("apps/mobile/packages/$pkg")
    EXIT_CODE=1
  fi
done

# ── 5. Infrastructure ────────────────────────────────────────────
echo ""
echo "▶ INFRASTRUCTURE"
echo "─────────────────────────────────────────────────────────────"
INFRA_FILES=(
  docker-compose.yml docker-compose.build.yml docker-compose.runtime.yml
  .env.example .gitignore CLAUDE.md README.md
)
for f in "${INFRA_FILES[@]}"; do
  if [ -f "$REPO_ROOT/$f" ]; then
    printf "  ✅ %s\n" "$f"
  else
    printf "  ❌ %s  MISSING\n" "$f"
    MISSING+=("$f")
    EXIT_CODE=1
  fi
done

INFRA_DIRS=(contracts compose infra helm tools scripts vendor)
for d in "${INFRA_DIRS[@]}"; do
  if [ -d "$REPO_ROOT/$d" ]; then
    count=$(find "$REPO_ROOT/$d" -type f | grep -c "" || true)
    printf "  ✅ %-20s (%s files)\n" "$d/" "$count"
  else
    printf "  ❌ %-20s MISSING\n" "$d/"
    MISSING+=("$d/")
    EXIT_CODE=1
  fi
done

# ── Summary ──────────────────────────────────────────────────────
echo ""
echo "═══════════════════════════════════════════════════════════════"
if [ ${#MISSING[@]} -eq 0 ]; then
  echo " ✅ ALL COMPONENTS PRESENT"
else
  echo " ❌ MISSING COMPONENTS (${#MISSING[@]}):"
  for m in "${MISSING[@]}"; do
    echo "    - $m"
  done
fi
echo "═══════════════════════════════════════════════════════════════"

exit $EXIT_CODE
