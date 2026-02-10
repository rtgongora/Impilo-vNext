#!/usr/bin/env bash
# =============================================================================
# mvn-local.sh — Run Maven using a repo-local vendored repository
# =============================================================================
# Usage:  ./tools/maven/mvn-local.sh [any maven args...]
# Example: ./tools/maven/mvn-local.sh -pl services/vito-service -am test-compile -o
# =============================================================================
set -euo pipefail

# Resolve the repository root (parent of tools/)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

LOCAL_REPO="$REPO_ROOT/vendor/m2/repository"

# Ensure the local repo directory exists
mkdir -p "$LOCAL_REPO"

# Verify mvn is installed
if ! command -v mvn &>/dev/null; then
    echo "ERROR: mvn is not installed or not on PATH." >&2
    echo "Install Maven 3.9+ and ensure it is available." >&2
    exit 1
fi

echo "========================================="
echo " mvn-local.sh"
echo " Repo root:  $REPO_ROOT"
echo " Local repo: $LOCAL_REPO"
echo "========================================="

exec mvn \
    -Dmaven.repo.local="$LOCAL_REPO" \
    -f "$REPO_ROOT/pom.xml" \
    "$@"
