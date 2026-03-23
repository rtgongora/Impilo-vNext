#!/usr/bin/env bash
# run-all.sh — Master runner for stub density audit scripts
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUTPUT_DIR="${SCRIPT_DIR}/../../docs/stub-audit/scan-output"
mkdir -p "$OUTPUT_DIR"

echo "========================================"
echo "  Impilo vNext Stub Density Audit"
echo "  $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
echo "========================================"
echo ""

echo "[1/3] Running placeholder indicator scan..."
bash "$SCRIPT_DIR/scan-placeholder-indicators.sh" | tee "$OUTPUT_DIR/placeholder-indicators.txt"
echo ""

echo "[2/3] Running UI thin page scan..."
bash "$SCRIPT_DIR/scan-ui-thin-pages.sh" | tee "$OUTPUT_DIR/ui-thin-pages.txt"
echo ""

echo "[3/3] Running backend service depth scan..."
bash "$SCRIPT_DIR/scan-backend-thin-services.sh" | tee "$OUTPUT_DIR/backend-service-depth.txt"
echo ""

echo "========================================"
echo "  All scans complete."
echo "  Output saved to: $OUTPUT_DIR/"
echo "========================================"
