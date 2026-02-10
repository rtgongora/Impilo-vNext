#!/usr/bin/env bash
# =============================================================================
# mvn-nexus.sh — Run Maven through local Nexus mirror + vendored local repo
# =============================================================================
# Prerequisite: docker compose -f tools/maven/nexus/docker-compose.yml up -d
# Usage:  ./tools/maven/mvn-nexus.sh [any maven args...]
# Example: ./tools/maven/mvn-nexus.sh -pl services/vito-service -am test-compile
# =============================================================================
set -euo pipefail

# Resolve the repository root (parent of tools/)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

LOCAL_REPO="$REPO_ROOT/vendor/m2/repository"
SETTINGS_FILE="$REPO_ROOT/tools/maven/settings-nexus.xml"

# Ensure the local repo directory exists
mkdir -p "$LOCAL_REPO"

# Verify mvn is installed
if ! command -v mvn &>/dev/null; then
    echo "ERROR: mvn is not installed or not on PATH." >&2
    echo "Install Maven 3.9+ and ensure it is available." >&2
    exit 1
fi

# Verify settings file exists
if [[ ! -f "$SETTINGS_FILE" ]]; then
    echo "ERROR: Settings file not found: $SETTINGS_FILE" >&2
    exit 1
fi

echo "========================================="
echo " mvn-nexus.sh"
echo " Repo root:   $REPO_ROOT"
echo " Local repo:  $LOCAL_REPO"
echo " Settings:    $SETTINGS_FILE"
echo " Nexus URL:   http://localhost:8081"
echo "========================================="

exec mvn \
    -s "$SETTINGS_FILE" \
    -Dmaven.repo.local="$LOCAL_REPO" \
    -f "$REPO_ROOT/pom.xml" \
    "$@"
