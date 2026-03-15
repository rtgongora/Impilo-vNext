#!/usr/bin/env bash
set -euo pipefail

###############################################################################
# Impilo vNext — Fleet Build Check
#
# Attempts to build the entire platform as a fleet:
#   Phase 1: Shared libraries (libs/)
#   Phase 2: All backend services (services/ Maven reactor)
#   Phase 3: UI workspace (ui/ Turbo monorepo)
#   Phase 4: Mobile apps (apps/mobile/)
#
# Usage:
#   ./scripts/reality-check/build-fleet.sh
#
# Environment:
#   Requires: Java 21, Maven 3.9+, Node 20+, npm
#   Optional: Docker (for compose-based build)
#
# Exit: 0 if all phases pass, 1 if any phase fails.
###############################################################################

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
REPORT_FILE="$REPO_ROOT/docs/reality-check/fleet-build-matrix.md"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

TOTAL=0
PASS=0
FAIL=0
SKIP=0

RESULTS=()

log_phase() { echo -e "\n${CYAN}═══ $* ═══${NC}\n"; }
log_pass()  { echo -e "  ${GREEN}PASS${NC} $*"; ((PASS++)); ((TOTAL++)); RESULTS+=("PASS|$*"); }
log_fail()  { echo -e "  ${RED}FAIL${NC} $*"; ((FAIL++)); ((TOTAL++)); RESULTS+=("FAIL|$*"); }
log_skip()  { echo -e "  ${YELLOW}SKIP${NC} $*"; ((SKIP++)); ((TOTAL++)); RESULTS+=("SKIP|$*"); }

check_tool() {
    local tool="$1"
    if command -v "$tool" &>/dev/null; then
        log_pass "$tool available: $(command -v "$tool")"
        return 0
    else
        log_fail "$tool not found in PATH"
        return 1
    fi
}

# =============================================================================
# PHASE 0: Toolchain Verification
# =============================================================================
log_phase "Phase 0: Toolchain Verification"

JAVA_OK=false
MAVEN_OK=false
NODE_OK=false
NPM_OK=false

if check_tool java; then
    JAVA_VERSION=$(java -version 2>&1 | head -1)
    echo "    Version: $JAVA_VERSION"
    JAVA_OK=true
fi

if check_tool mvn; then
    MVN_VERSION=$(mvn --version 2>&1 | head -1)
    echo "    Version: $MVN_VERSION"
    MAVEN_OK=true
fi

if check_tool node; then
    NODE_VERSION=$(node --version 2>&1)
    echo "    Version: $NODE_VERSION"
    NODE_OK=true
fi

if check_tool npm; then
    NPM_VERSION=$(npm --version 2>&1)
    echo "    Version: $NPM_VERSION"
    NPM_OK=true
fi

# =============================================================================
# PHASE 1: Shared Libraries Build
# =============================================================================
log_phase "Phase 1: Shared Libraries (libs/)"

LIBS_DIR="$REPO_ROOT/libs"
LIB_BUILD_ORDER=(
    "shared-kernel-java"
    "tshepo-contracts"
    "tshepo-sdk"
    "contract-tests"
    "tech-companion"
    "tech-companion-harness"
    "tech-companion-mock"
    "federation-connector"
    "ops-instrumentation"
    "security-baseline"
    "offline-sdk"
)

if [ "$MAVEN_OK" = true ] && [ "$JAVA_OK" = true ]; then
    for lib in "${LIB_BUILD_ORDER[@]}"; do
        lib_dir="$LIBS_DIR/$lib"
        if [ -f "$lib_dir/pom.xml" ]; then
            echo -e "  Building: $lib ..."
            if (cd "$lib_dir" && mvn -B clean install -DskipTests -q 2>&1 | tail -3); then
                log_pass "lib/$lib"
            else
                log_fail "lib/$lib"
            fi
        else
            log_skip "lib/$lib (no pom.xml)"
        fi
    done

    # JS shared kernel
    if [ "$NPM_OK" = true ] && [ -f "$LIBS_DIR/shared-kernel/package.json" ]; then
        echo -e "  Building: shared-kernel (JS) ..."
        if (cd "$LIBS_DIR/shared-kernel" && npm install --legacy-peer-deps 2>&1 | tail -3); then
            log_pass "lib/shared-kernel (JS)"
        else
            log_fail "lib/shared-kernel (JS)"
        fi
    else
        log_skip "lib/shared-kernel (JS) — npm not available or no package.json"
    fi
else
    for lib in "${LIB_BUILD_ORDER[@]}"; do
        log_skip "lib/$lib — Java/Maven not available"
    done
fi

# =============================================================================
# PHASE 2: Backend Services Fleet Build (Maven Reactor)
# =============================================================================
log_phase "Phase 2: Backend Services (services/ Maven Reactor)"

SERVICES_DIR="$REPO_ROOT/services"

if [ "$MAVEN_OK" = true ] && [ "$JAVA_OK" = true ]; then
    echo "  Attempting reactor build: mvn -B clean compile -DskipTests -T 1C --fail-at-end"
    echo ""

    REACTOR_OUTPUT=$(cd "$SERVICES_DIR" && mvn -B clean compile -DskipTests -T 1C --fail-at-end 2>&1) || true
    REACTOR_EXIT=$?

    # Parse per-module results from Maven output
    while IFS= read -r line; do
        if echo "$line" | grep -qE '^\[INFO\] (Building |--- )'; then
            continue
        fi
        if echo "$line" | grep -qE '\[INFO\].*SUCCESS'; then
            module=$(echo "$line" | sed -E 's/.*\[INFO\] (.*) \.+ SUCCESS.*/\1/' | xargs)
            if [ -n "$module" ] && [ "$module" != "[INFO]" ]; then
                log_pass "service/$module"
            fi
        elif echo "$line" | grep -qE '\[ERROR\].*FAILURE'; then
            module=$(echo "$line" | sed -E 's/.*\[ERROR\] (.*) \.+ FAILURE.*/\1/' | xargs)
            if [ -n "$module" ] && [ "$module" != "[ERROR]" ]; then
                log_fail "service/$module"
            fi
        fi
    done <<< "$(echo "$REACTOR_OUTPUT" | grep -E '(SUCCESS|FAILURE)' | head -80)"

    if [ $REACTOR_EXIT -eq 0 ]; then
        echo -e "\n  ${GREEN}Reactor build: ALL MODULES COMPILED${NC}"
    else
        echo -e "\n  ${RED}Reactor build: SOME MODULES FAILED${NC}"
        echo "$REACTOR_OUTPUT" | grep -E '\[ERROR\]' | tail -20
    fi
else
    log_skip "services/ reactor — Java/Maven not available"
    echo "  To build: cd services && mvn -B clean compile -DskipTests -T 1C --fail-at-end"
fi

# =============================================================================
# PHASE 3: UI Workspace Build
# =============================================================================
log_phase "Phase 3: UI Workspace (ui/)"

UI_DIR="$REPO_ROOT/ui"

if [ "$NODE_OK" = true ] && [ "$NPM_OK" = true ]; then
    if [ -f "$UI_DIR/package.json" ]; then
        echo "  Installing dependencies..."
        if (cd "$UI_DIR" && npm install --legacy-peer-deps 2>&1 | tail -5); then
            log_pass "ui/ npm install"
        else
            log_fail "ui/ npm install"
        fi

        # Check if turbo is available for workspace build
        if [ -f "$UI_DIR/turbo.json" ]; then
            echo "  Attempting turbo type-check..."
            if (cd "$UI_DIR" && npx turbo run type-check 2>&1 | tail -10); then
                log_pass "ui/ turbo type-check"
            else
                log_fail "ui/ turbo type-check"
            fi
        fi
    else
        log_skip "ui/ — no package.json"
    fi
else
    log_skip "ui/ workspace — Node/npm not available"
fi

# =============================================================================
# PHASE 4: Mobile Apps Build
# =============================================================================
log_phase "Phase 4: Mobile Apps (apps/mobile/)"

MOBILE_DIR="$REPO_ROOT/apps/mobile"

for app in provider-app citizen-app; do
    app_dir="$MOBILE_DIR/$app"
    if [ -f "$app_dir/package.json" ]; then
        if [ "$NPM_OK" = true ]; then
            echo "  Installing $app dependencies..."
            if (cd "$app_dir" && npm install --legacy-peer-deps 2>&1 | tail -5); then
                log_pass "mobile/$app npm install"
            else
                log_fail "mobile/$app npm install"
            fi

            # Type check
            echo "  Type-checking $app..."
            if (cd "$app_dir" && npx tsc --noEmit 2>&1 | tail -10); then
                log_pass "mobile/$app type-check"
            else
                log_fail "mobile/$app type-check"
            fi
        else
            log_skip "mobile/$app — npm not available"
        fi
    else
        log_skip "mobile/$app — no package.json"
    fi
done

# =============================================================================
# PHASE 5: Dockerfile Coverage Check
# =============================================================================
log_phase "Phase 5: Dockerfile Coverage"

DOCKERFILE_COUNT=0
NO_DOCKERFILE_COUNT=0
NO_DOCKERFILE_LIST=""

for svc_dir in "$SERVICES_DIR"/*/; do
    svc_name=$(basename "$svc_dir")
    [ "$svc_name" = "shared-core" ] && continue
    [ ! -f "$svc_dir/pom.xml" ] && continue

    if [ -f "$svc_dir/Dockerfile" ]; then
        ((DOCKERFILE_COUNT++))
    else
        ((NO_DOCKERFILE_COUNT++))
        NO_DOCKERFILE_LIST="$NO_DOCKERFILE_LIST  $svc_name\n"
    fi
done

echo -e "  With Dockerfile: $DOCKERFILE_COUNT"
echo -e "  Without Dockerfile: $NO_DOCKERFILE_COUNT"
if [ $NO_DOCKERFILE_COUNT -gt 0 ]; then
    echo -e "\n  Missing Dockerfiles:"
    echo -e "$NO_DOCKERFILE_LIST"
    log_fail "Dockerfile coverage: $DOCKERFILE_COUNT/$(($DOCKERFILE_COUNT + $NO_DOCKERFILE_COUNT))"
else
    log_pass "Dockerfile coverage: $DOCKERFILE_COUNT/$(($DOCKERFILE_COUNT + $NO_DOCKERFILE_COUNT))"
fi

# =============================================================================
# SUMMARY
# =============================================================================
log_phase "Fleet Build Summary"

echo -e "  Total checks: $TOTAL"
echo -e "  ${GREEN}Passed: $PASS${NC}"
echo -e "  ${RED}Failed: $FAIL${NC}"
echo -e "  ${YELLOW}Skipped: $SKIP${NC}"
echo ""

if [ $FAIL -gt 0 ]; then
    echo -e "${RED}FLEET BUILD: ISSUES FOUND${NC}"
    exit 1
elif [ $SKIP -gt 0 ]; then
    echo -e "${YELLOW}FLEET BUILD: PARTIAL (some checks skipped due to missing tools)${NC}"
    exit 0
else
    echo -e "${GREEN}FLEET BUILD: ALL CHECKS PASSED${NC}"
    exit 0
fi
