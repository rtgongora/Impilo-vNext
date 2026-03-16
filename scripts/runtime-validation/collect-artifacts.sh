#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; BOLD='\033[1m'; NC='\033[0m'

TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
OUTPUT_DIR="$ROOT_DIR/artifacts/runtime-validation-${TIMESTAMP}"

echo -e "${BOLD}=== Collect Artifacts ===${NC}"
echo "Output directory: $OUTPUT_DIR"

mkdir -p "$OUTPUT_DIR"

COLLECTED=0

# 1. Copy runtime-validation scripts
echo ""
echo -e "${BOLD}--- Copying runtime-validation scripts ---${NC}"
if [[ -d "$SCRIPT_DIR" ]]; then
  mkdir -p "$OUTPUT_DIR/scripts/runtime-validation"
  cp -v "$SCRIPT_DIR"/*.sh "$OUTPUT_DIR/scripts/runtime-validation/" 2>/dev/null || true
  COLLECTED=$((COLLECTED + 1))
  echo -e "${GREEN}[OK]${NC} Runtime-validation scripts copied"
else
  echo -e "${YELLOW}[SKIP]${NC} No runtime-validation scripts found"
fi

# 2. Copy integration test scripts
echo ""
echo -e "${BOLD}--- Copying integration test scripts ---${NC}"
TEST_DIR="$ROOT_DIR/test/integration"
if [[ -d "$TEST_DIR" ]]; then
  mkdir -p "$OUTPUT_DIR/test/integration"
  cp -v "$TEST_DIR"/*.sh "$OUTPUT_DIR/test/integration/" 2>/dev/null || true
  COLLECTED=$((COLLECTED + 1))
  echo -e "${GREEN}[OK]${NC} Integration test scripts copied"
else
  echo -e "${YELLOW}[SKIP]${NC} No integration test scripts found"
fi

# 3. Copy runtime-validation docs
echo ""
echo -e "${BOLD}--- Copying runtime-validation docs ---${NC}"
DOCS_DIR="$ROOT_DIR/docs/runtime-validation"
if [[ -d "$DOCS_DIR" ]]; then
  mkdir -p "$OUTPUT_DIR/docs/runtime-validation"
  cp -v "$DOCS_DIR"/* "$OUTPUT_DIR/docs/runtime-validation/" 2>/dev/null || true
  COLLECTED=$((COLLECTED + 1))
  echo -e "${GREEN}[OK]${NC} Runtime-validation docs copied"
else
  echo -e "${YELLOW}[SKIP]${NC} No runtime-validation docs found"
fi

# 4. Capture Docker state if running
echo ""
echo -e "${BOLD}--- Capturing Docker state ---${NC}"
if docker info &>/dev/null; then
  mkdir -p "$OUTPUT_DIR/docker"

  # Capture docker compose ps
  echo "Capturing docker compose ps..."
  (cd "$ROOT_DIR" && docker compose -f docker-compose.runtime.yml ps) \
    > "$OUTPUT_DIR/docker/compose-ps.txt" 2>&1 || true
  echo -e "${GREEN}[OK]${NC} docker compose ps captured"

  # Capture docker compose logs (last 50 lines)
  echo "Capturing docker compose logs (tail=50)..."
  (cd "$ROOT_DIR" && docker compose -f docker-compose.runtime.yml logs --tail=50) \
    > "$OUTPUT_DIR/docker/compose-logs.txt" 2>&1 || true
  echo -e "${GREEN}[OK]${NC} docker compose logs captured"

  COLLECTED=$((COLLECTED + 1))
else
  echo -e "${YELLOW}[SKIP]${NC} Docker daemon not running — skipping container state capture"
fi

# Summary
echo ""
echo "========================================="
echo -e "${BOLD}Artifact Collection Summary${NC}"
echo "========================================="
echo "Output directory: $OUTPUT_DIR"
echo "Sections collected: $COLLECTED"
echo ""
echo "Contents:"
find "$OUTPUT_DIR" -type f | sort | while read -r f; do
  SIZE=$(stat --printf="%s" "$f" 2>/dev/null || stat -f "%z" "$f" 2>/dev/null || echo "?")
  REL="${f#$OUTPUT_DIR/}"
  printf "  %-60s %s bytes\n" "$REL" "$SIZE"
done

echo ""
echo -e "${GREEN}Artifacts collected successfully.${NC}"
exit 0
