#!/usr/bin/env bash
##
## Impilo vNext — Build All (Experience Platform Stage-1)
##
## Builds experience-bff (Maven) and one-ui-shell (npm).
## Accepts MAVEN_MIRROR_URL environment variable for air-gapped/cached builds.
##
## Usage:
##   ./tools/dev/build-all.sh
##   MAVEN_MIRROR_URL=http://nexus:8081/repository/maven-public/ ./tools/dev/build-all.sh
##
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "========================================"
echo "  Impilo vNext — Build All (Stage-1)"
echo "========================================"

# ── Maven settings ────────────────────────────────────────────
MAVEN_OPTS_EXTRA=""
if [ -n "${MAVEN_MIRROR_URL:-}" ]; then
  echo "[INFO] Using Maven mirror: $MAVEN_MIRROR_URL"
  SETTINGS_FILE="$ROOT_DIR/tools/dev/maven-settings.xml"
  if [ -f "$SETTINGS_FILE" ]; then
    MAVEN_OPTS_EXTRA="-s $SETTINGS_FILE"
  fi
elif [ -n "${MAVEN_SETTINGS_FILE:-}" ]; then
  echo "[INFO] Using custom Maven settings: $MAVEN_SETTINGS_FILE"
  MAVEN_OPTS_EXTRA="-s $MAVEN_SETTINGS_FILE"
fi

# ── Build shared libs + experience-bff ────────────────────────
echo ""
echo "── Building experience-bff (Maven) ──"
cd "$ROOT_DIR/services"

mvn -pl ../libs/shared-kernel-java,../libs/tech-companion,../libs/tech-companion-harness,experience-bff \
    -am -DskipTests package -q $MAVEN_OPTS_EXTRA

echo "[OK] experience-bff built successfully"

# ── Build one-ui-shell ───────────────────────────────────────
echo ""
echo "── Building one-ui-shell (npm) ──"
cd "$ROOT_DIR/ui"

if [ ! -d "node_modules" ]; then
  npm install --legacy-peer-deps
fi
npm -w one-ui-shell run build

echo "[OK] one-ui-shell built successfully"

echo ""
echo "========================================"
echo "  Build complete!"
echo "========================================"
