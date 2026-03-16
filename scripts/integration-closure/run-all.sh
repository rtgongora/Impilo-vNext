#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "============================================================"
echo "  Impilo vNext — Full Integration Closure Suite"
echo "============================================================"
echo ""

echo "=== Phase 1: Auth Bootstrap ==="
bash "$SCRIPT_DIR/bootstrap-auth.sh"
echo ""

echo "=== Phase 2: Runtime Health Checks ==="
bash "$SCRIPT_DIR/run-runtime-checks.sh"
echo ""

echo "=== Phase 3: Cross-Service Integration Tests ==="
bash "$SCRIPT_DIR/run-cross-service-tests.sh"
echo ""

echo "============================================================"
echo "  All integration closure checks completed successfully."
echo "============================================================"
