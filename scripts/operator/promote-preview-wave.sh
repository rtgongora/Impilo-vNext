#!/usr/bin/env bash
# Promote impilo-full-preview to a higher full-boot wave (build + deploy).
# Usage:
#   bash scripts/operator/promote-preview-wave.sh [WAVE_N]
#   FULL_BOOT_MAX_WAVE=8 bash scripts/operator/promote-preview-wave.sh
#
# Requires AUTHORIZE FULL BOOT PREVIEW DEPLOY for deploy phase.
set -euo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

WAVE_N="${1:-${FULL_BOOT_MAX_WAVE:-1}}"
if ! [[ "$WAVE_N" =~ ^[0-9]+$ ]]; then
  echo "ERROR: wave must be a non-negative integer (got: $WAVE_N)" >&2
  exit 1
fi

echo "=== Promote preview to wave $WAVE_N ==="
echo "Repo: $REPO_PATH"
echo "Branch: $(git branch --show-current)"
echo "Commit: $(git rev-parse --short HEAD)"

export FULL_BOOT_MAX_WAVE="$WAVE_N"

node scripts/full-boot/generate-full-preview-runtime-values.mjs --max-wave "$WAVE_N" >/dev/null
ENABLED_COUNT="$(python3 -c "
import yaml
waves = yaml.safe_load(open('config/full-boot-waves.yml'))
enabled = set()
for w in waves['waves']:
    if w['id'] > $WAVE_N: break
    enabled.update(w.get('services', []))
print(len(enabled))
")"
echo "Cumulative microservices through wave $WAVE_N: $ENABLED_COUNT"

echo ""
echo "Step 1/2: wave-build $WAVE_N"
bash scripts/operator/fullboot.sh wave-build "$WAVE_N"

echo ""
echo "Step 2/2: wave-deploy $WAVE_N (requires deploy authorization)"
echo "When prompted, type: AUTHORIZE FULL BOOT PREVIEW DEPLOY"
bash scripts/operator/fullboot.sh wave-deploy "$WAVE_N"

echo ""
echo "Done. Verify: bash scripts/operator/report-preview-generation.sh"
