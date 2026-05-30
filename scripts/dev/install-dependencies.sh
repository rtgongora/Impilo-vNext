#!/usr/bin/env bash
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO_PATH"

echo "=== Backend: Maven (experience-bff + dependencies) ==="
cd "$REPO_PATH/services"
mvn -q install -pl experience-bff -am -DskipTests

echo "=== Frontend: npm install (ui workspace) ==="
cd "$REPO_PATH/ui"
npm ci --legacy-peer-deps || npm install --legacy-peer-deps

echo "=== Done ==="
