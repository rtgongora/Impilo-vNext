#!/usr/bin/env bash
# Verify every helm-enabled microservice has a BFF downstream URL or documented exclusion.
set -euo pipefail

REPO="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO"

echo "=== check-bff-downstream-mappings ==="

# Regenerate and validate (generator exits 1 on gaps)
node scripts/full-boot/generate-full-preview-bff-downstream-env.mjs >/dev/null

OUT="deploy/helm/impilo-vnext/values-full-preview-bff-env.generated.yaml"
if grep -q 'localhost' "$OUT" 2>/dev/null; then
  echo "FAIL: generated BFF env contains localhost — preview pods would break"
  grep 'localhost' "$OUT" | head -5
  exit 1
fi

# Ensure generator source documents exclusions
if ! grep -q 'BFF_DOWNSTREAM_EXCLUDED' scripts/full-boot/generate-full-preview-bff-downstream-env.mjs; then
  echo "FAIL: BFF_DOWNSTREAM_EXCLUDED block missing from generator"
  exit 1
fi

echo "PASS: BFF downstream mappings complete; no localhost in generated preview env"
