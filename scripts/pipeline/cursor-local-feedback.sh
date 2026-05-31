#!/usr/bin/env bash
# Cursor-friendly summary: VM local pipeline + GitHub CI (if available).
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO_PATH"

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║           Impilo — local + CI quality feedback               ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
echo "Branch: $(git branch --show-current)"
echo "Commit: $(git rev-parse --short HEAD) ($(git rev-parse HEAD))"
echo ""

REPORT="$REPO_PATH/reports/pipeline/latest-summary.md"
JSON="$REPO_PATH/reports/pipeline/latest-summary.json"
FAILURES="$REPO_PATH/reports/pipeline/latest-failures.md"

if [[ -f "$REPORT" ]]; then
  echo "── VM local pipeline (latest report) ──"
  head -n 40 "$REPORT"
  echo ""
  [[ -f "$FAILURES" ]] && [[ -s "$FAILURES" ]] && { echo "── Failures detail ──"; head -n 30 "$FAILURES"; echo ""; }
else
  echo "VM local pipeline: NO REPORT"
  echo "  Run: bash scripts/pipeline/run-local-quality-gates.sh"
  echo ""
fi

echo "── Parity gates (latest run) ──"
for f in /tmp/impilo-parity-summary.txt; do
  [[ -f "$f" ]] && cat "$f" 2>/dev/null || echo "(run parity guards to populate)"
done
if [[ -f config/parity-allowlist.yml ]]; then
  echo "Allowlist: config/parity-allowlist.yml ($(grep -c '^  - path:' config/parity-allowlist.yml 2>/dev/null || echo 0) exceptions)"
fi
echo "Matrices: docs/architecture/FRONTEND_BACKEND_PARITY_MATRIX.md, MOBILE_PARITY_MATRIX.md"
echo ""

echo "── GitHub Actions CI ──"
FB="$(mktemp)"
bash scripts/ci/collect-ci-feedback.sh 2>&1 | tee "$FB"
echo ""

echo "── Decision options ──"
echo "  1. Fix failures first"
echo "  2. Authorize VM preview deploy based on GitHub CI (type AUTHORIZE DEPLOY)"
echo "  3. Authorize VM preview deploy based on VM local gates (GitHub CI unavailable)"
echo "     Type: AUTHORIZE DEPLOY WITH VM GATES"
echo "  4. Authorize deploy with known risks (BYPASS_CI=1 + reason)"
echo "  5. Stop"
echo ""
rm -f "$FB"
