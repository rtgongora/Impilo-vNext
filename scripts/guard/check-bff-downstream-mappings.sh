#!/usr/bin/env bash
# Verify every helm-enabled microservice has a BFF downstream URL or documented exclusion.
set -euo pipefail

# Script-relative, never a hardcoded checkout: this default sent the guard into
# /opt/impilo/repos/Impilo-vNext and audited that tree instead of the one under review, so a
# worktree run reported on somebody else's working copy and called it a pass.
REPO="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
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
