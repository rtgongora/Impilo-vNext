#!/usr/bin/env bash
set -euo pipefail
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

echo "=== Build experience-bff + dependency chain ==="
cd "$REPO_PATH/services"
mvn -q package -pl experience-bff -am -DskipTests

echo "=== Frontend deps + build (optional local build) ==="
cd "$REPO_PATH/ui"
npm ci --legacy-peer-deps || npm install --legacy-peer-deps
npm -w one-ui-shell run build || echo "WARN: local UI build failed — Docker image build may still succeed"

echo "=== Build complete ==="
