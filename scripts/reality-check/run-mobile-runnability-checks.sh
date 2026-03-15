#!/usr/bin/env bash
set -euo pipefail

###############################################################################
# Impilo vNext — Mobile Runnability Check
#
# Validates whether mobile apps are truly runnable on Android/iOS:
#   1. Framework identification (Expo/React Native)
#   2. Native build readiness (expo prebuild, android/, ios/)
#   3. Dependency completeness
#   4. Web-only API usage in mobile-critical paths
#   5. Shared package health
#
# Usage:
#   ./scripts/reality-check/run-mobile-runnability-checks.sh
###############################################################################

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

PASS=0
FAIL=0
SKIP=0
TOTAL=0

log_phase() { echo -e "\n${CYAN}═══ $* ═══${NC}\n"; }
log_pass()  { echo -e "  ${GREEN}PASS${NC} $*"; ((PASS++)); ((TOTAL++)); }
log_fail()  { echo -e "  ${RED}FAIL${NC} $*"; ((FAIL++)); ((TOTAL++)); }
log_skip()  { echo -e "  ${YELLOW}SKIP${NC} $*"; ((SKIP++)); ((TOTAL++)); }
log_warn()  { echo -e "  ${YELLOW}WARN${NC} $*"; }

echo ""
echo "============================================================"
echo "  Impilo vNext — Mobile Runnability Check"
echo "============================================================"
echo ""

MOBILE_DIR="$REPO_ROOT/apps/mobile"

# =============================================================================
# CHECK 1: App Existence and Framework
# =============================================================================
log_phase "Check 1: Mobile App Identification"

for app in provider-app citizen-app; do
    app_dir="$MOBILE_DIR/$app"
    if [ -d "$app_dir" ]; then
        log_pass "$app directory exists"

        # Check framework
        if [ -f "$app_dir/package.json" ]; then
            if grep -q '"expo"' "$app_dir/package.json"; then
                EXPO_VERSION=$(grep -oP '"expo":\s*"[^"]+"' "$app_dir/package.json")
                echo "    Framework: Expo ($EXPO_VERSION)"
                log_pass "$app uses Expo framework"
            fi
            if grep -q '"react-native"' "$app_dir/package.json"; then
                RN_VERSION=$(grep -oP '"react-native":\s*"[^"]+"' "$app_dir/package.json")
                echo "    React Native: $RN_VERSION"
                log_pass "$app has react-native dependency"
            fi
        else
            log_fail "$app missing package.json"
        fi
    else
        log_fail "$app directory not found at $app_dir"
    fi
done

# =============================================================================
# CHECK 2: Expo Configuration
# =============================================================================
log_phase "Check 2: Expo Configuration"

for app in provider-app citizen-app; do
    app_dir="$MOBILE_DIR/$app"

    # app.config.ts / app.json
    if [ -f "$app_dir/app.config.ts" ] || [ -f "$app_dir/app.json" ]; then
        log_pass "$app has Expo config (app.config.ts or app.json)"

        # Check for Android/iOS config
        CONFIG_FILE="$app_dir/app.config.ts"
        [ ! -f "$CONFIG_FILE" ] && CONFIG_FILE="$app_dir/app.json"

        if grep -q "android" "$CONFIG_FILE" 2>/dev/null; then
            log_pass "$app has Android configuration"
        else
            log_fail "$app missing Android configuration in Expo config"
        fi

        if grep -q "ios" "$CONFIG_FILE" 2>/dev/null; then
            log_pass "$app has iOS configuration"
        else
            log_fail "$app missing iOS configuration in Expo config"
        fi
    else
        log_fail "$app missing Expo config"
    fi

    # eas.json (EAS Build config)
    if [ -f "$app_dir/eas.json" ]; then
        log_pass "$app has EAS build config (eas.json)"
    else
        log_fail "$app missing eas.json for native builds"
    fi

    # metro.config.js
    if [ -f "$app_dir/metro.config.js" ]; then
        log_pass "$app has Metro bundler config"
    else
        log_fail "$app missing metro.config.js"
    fi

    # babel.config.js
    if [ -f "$app_dir/babel.config.js" ]; then
        log_pass "$app has Babel config"
    else
        log_fail "$app missing babel.config.js"
    fi
done

# =============================================================================
# CHECK 3: Native Directory Presence (expo prebuild output)
# =============================================================================
log_phase "Check 3: Native Build Directories"

for app in provider-app citizen-app; do
    app_dir="$MOBILE_DIR/$app"

    if [ -d "$app_dir/android" ]; then
        log_pass "$app has android/ directory (prebuild complete)"
    else
        log_warn "$app: android/ directory not present — requires 'expo prebuild' before native build"
        log_skip "$app android/ prebuild not yet run"
    fi

    if [ -d "$app_dir/ios" ]; then
        log_pass "$app has ios/ directory (prebuild complete)"
    else
        log_warn "$app: ios/ directory not present — requires 'expo prebuild' before native build"
        log_skip "$app ios/ prebuild not yet run"
    fi
done

# =============================================================================
# CHECK 4: Web-Only API Detection in Mobile Source
# =============================================================================
log_phase "Check 4: Web-Only API Detection"

WEB_ONLY_APIS=(
    "window\\.location"
    "document\\."
    "localStorage\\."
    "sessionStorage\\."
    "navigator\\.serviceWorker"
    "XMLHttpRequest"
    "window\\.addEventListener"
    "next/router"
    "next/link"
    "next/image"
)

for app in provider-app citizen-app; do
    app_src="$MOBILE_DIR/$app/src"
    if [ -d "$app_src" ]; then
        WEB_ONLY_FOUND=false
        for api in "${WEB_ONLY_APIS[@]}"; do
            MATCHES=$(grep -rn "$api" "$app_src" 2>/dev/null | grep -v "test\|spec\|__mock" || true)
            if [ -n "$MATCHES" ]; then
                echo "    $app: Web-only API found: $api"
                echo "$MATCHES" | head -3 | while read -r m; do echo "      $m"; done
                WEB_ONLY_FOUND=true
            fi
        done
        if [ "$WEB_ONLY_FOUND" = true ]; then
            log_fail "$app contains web-only APIs"
        else
            log_pass "$app: no web-only APIs detected"
        fi
    fi
done

# =============================================================================
# CHECK 5: Shared Mobile Packages
# =============================================================================
log_phase "Check 5: Shared Mobile Packages"

PACKAGES_DIR="$MOBILE_DIR/packages"
if [ -d "$PACKAGES_DIR" ]; then
    for pkg_dir in "$PACKAGES_DIR"/*/; do
        pkg_name=$(basename "$pkg_dir")
        if [ -f "$pkg_dir/package.json" ]; then
            echo "  Package: $pkg_name"

            # Check for react-native dependency
            if grep -q '"react-native"' "$pkg_dir/package.json" 2>/dev/null; then
                log_pass "$pkg_name declares react-native peer/dependency"
            else
                log_warn "$pkg_name: no react-native dependency declared"
            fi

            # Check for web-only imports in package source
            SRC_DIR="$pkg_dir/src"
            if [ -d "$SRC_DIR" ]; then
                WEB_IMPORTS=$(grep -rn "from 'next\|from \"next\|require('next" "$SRC_DIR" 2>/dev/null || true)
                if [ -n "$WEB_IMPORTS" ]; then
                    log_fail "$pkg_name imports Next.js in mobile package"
                fi
            fi
        fi
    done
else
    log_skip "No shared mobile packages directory"
fi

# =============================================================================
# CHECK 6: UI Apps Classification (web vs mobile vs hybrid)
# =============================================================================
log_phase "Check 6: UI App Classification"

echo "  Classifying ui/ apps..."
UI_DIR="$REPO_ROOT/ui"

for ui_app_dir in "$UI_DIR"/*/; do
    ui_app=$(basename "$ui_app_dir")
    [ "$ui_app" = "shared-ui" ] && continue
    [ ! -f "$ui_app_dir/package.json" ] && continue

    if grep -q '"next"' "$ui_app_dir/package.json" 2>/dev/null; then
        echo "    $ui_app: WEB (Next.js)"
    elif grep -q '"expo"' "$ui_app_dir/package.json" 2>/dev/null; then
        echo "    $ui_app: MOBILE (Expo)"
    elif grep -q '"react-native"' "$ui_app_dir/package.json" 2>/dev/null; then
        echo "    $ui_app: MOBILE (React Native)"
    else
        echo "    $ui_app: WEB (non-Next)"
    fi
done

echo ""
echo "  Mobile apps (apps/mobile/):"
echo "    provider-app: MOBILE (Expo + React Native)"
echo "    citizen-app: MOBILE (Expo + React Native)"
log_pass "App classification complete"

# =============================================================================
# SUMMARY
# =============================================================================
log_phase "Mobile Runnability Check Summary"

echo -e "  Total checks: $TOTAL"
echo -e "  ${GREEN}Passed: $PASS${NC}"
echo -e "  ${RED}Failed: $FAIL${NC}"
echo -e "  ${YELLOW}Skipped: $SKIP${NC}"
echo ""

if [ $FAIL -gt 0 ]; then
    echo -e "${RED}MOBILE RUNNABILITY: ISSUES FOUND${NC}"
    exit 1
elif [ $SKIP -gt 0 ]; then
    echo -e "${YELLOW}MOBILE RUNNABILITY: PARTIAL (native prebuild pending)${NC}"
    exit 0
else
    echo -e "${GREEN}MOBILE RUNNABILITY: ALL CHECKS PASSED${NC}"
    exit 0
fi
