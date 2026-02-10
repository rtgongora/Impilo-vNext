#!/usr/bin/env bash
# =============================================================================
# verify-offline.sh — Validate vendored Maven repo and attempt offline compile
# =============================================================================
# Usage:  ./tools/maven/verify-offline.sh
#
# Checks:
#   1. vendor/m2/repository/ exists
#   2. Minimum required Spring Boot 3.3.6 artifacts present
#   3. Attempts offline test-compile for VITO service
# =============================================================================
set -euo pipefail

# Resolve the repository root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

LOCAL_REPO="$REPO_ROOT/vendor/m2/repository"
MVN_LOCAL="$REPO_ROOT/tools/maven/mvn-local.sh"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "========================================="
echo " Impilo vNext — Offline Readiness Verifier"
echo " Repo root:  $REPO_ROOT"
echo " Local repo: $LOCAL_REPO"
echo "========================================="
echo ""

FAILED=0

# ---------------------------------------------------------------------------
# CHECK 1: Vendor repo directory exists
# ---------------------------------------------------------------------------
echo "--- Check 1: Vendor repository directory ---"
if [[ -d "$LOCAL_REPO" ]]; then
    echo -e "${GREEN}PASS${NC}: $LOCAL_REPO exists"
else
    echo -e "${RED}FAIL${NC}: $LOCAL_REPO does not exist"
    echo "  Create it with: mkdir -p vendor/m2/repository"
    FAILED=1
fi
echo ""

# ---------------------------------------------------------------------------
# CHECK 2: Minimum required artifacts for Spring Boot 3.3.6
# ---------------------------------------------------------------------------
echo "--- Check 2: Required Spring Boot 3.3.6 artifacts ---"

REQUIRED_PATHS=(
    "org/springframework/boot/spring-boot-starter-parent/3.3.6"
    "org/springframework/boot/spring-boot-dependencies/3.3.6"
)

MISSING_PATHS=()
for REL_PATH in "${REQUIRED_PATHS[@]}"; do
    FULL_PATH="$LOCAL_REPO/$REL_PATH"
    if [[ -d "$FULL_PATH" ]]; then
        echo -e "  ${GREEN}FOUND${NC}: $REL_PATH"
    else
        echo -e "  ${RED}MISSING${NC}: $REL_PATH"
        MISSING_PATHS+=("$REL_PATH")
    fi
done
echo ""

if [[ ${#MISSING_PATHS[@]} -gt 0 ]]; then
    echo -e "${YELLOW}WARNING: ${#MISSING_PATHS[@]} required artifact path(s) missing.${NC}"
    echo ""
    echo "Missing paths:"
    for P in "${MISSING_PATHS[@]}"; do
        echo "  vendor/m2/repository/$P"
    done
    echo ""
    echo "Remediation options:"
    echo ""
    echo "  Option 1 — Copy from a connected machine:"
    echo "    On a machine with internet access, run:"
    echo "      ./tools/maven/mvn-local.sh -pl services/vito-service -am test-compile"
    echo "    Then copy the populated vendor/m2/repository/ into this repo:"
    echo "      rsync -a vendor/m2/repository/ <offline-machine>:<repo>/vendor/m2/repository/"
    echo ""
    echo "  Option 2 — Use local Nexus to warm the cache:"
    echo "    docker compose -f tools/maven/nexus/docker-compose.yml up -d"
    echo "    # Wait for Nexus to start (~60s), then:"
    echo "    ./tools/maven/mvn-nexus.sh -pl services/vito-service -am test-compile"
    echo "    # This populates vendor/m2/repository/ via Nexus proxy."
    echo ""
    echo -e "${RED}Cannot proceed with offline compile — vendor repo incomplete.${NC}"
    exit 1
fi

echo -e "${GREEN}All required base artifacts present.${NC}"
echo ""

# ---------------------------------------------------------------------------
# CHECK 3: Attempt offline test-compile for VITO
# ---------------------------------------------------------------------------
echo "--- Check 3: Offline test-compile for VITO ---"
echo "Running: $MVN_LOCAL -pl services/vito-service -am -DskipTests test-compile -o"
echo ""

COMPILE_LOG=$(mktemp)
trap "rm -f '$COMPILE_LOG'" EXIT

if "$MVN_LOCAL" -pl services/vito-service -am -DskipTests test-compile -o >"$COMPILE_LOG" 2>&1; then
    echo ""
    echo "========================================="
    echo -e "${GREEN} SUCCESS${NC}"
    echo " VITO offline test-compile completed."
    echo " Vendor repo is sufficient for offline builds."
    echo "========================================="
    exit 0
else
    EXIT_CODE=$?
    echo ""
    echo -e "${RED}FAIL: Offline test-compile exited with code $EXIT_CODE${NC}"
    echo ""
    echo "--- First 40 lines of failure output ---"
    head -n 40 "$COMPILE_LOG"
    echo ""
    echo "--- Full log available at: $COMPILE_LOG ---"
    # Don't let trap remove it if we failed
    trap - EXIT
    echo -e "${RED}Offline compile failed. Review the output above for missing artifacts.${NC}"
    exit 1
fi
