#!/usr/bin/env bash
# Run inside tmux session fullboot-import — survives SSH disconnect.
# Attach first if sudo ticket expired: tmux attach -t fullboot-import
set -euo pipefail
REPO="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO"
mkdir -p reports/full-boot
IMPORT_LOG="reports/full-boot/k3s-image-import-preview.log"
VERIFY_LOG="reports/full-boot/k3s-image-verify-preview.log"

echo "=== Full boot k3s import (tmux) ===" | tee "$IMPORT_LOG"
echo "Started at $(date -Is)" | tee -a "$IMPORT_LOG"
echo "Branch: $(git branch --show-current) @ $(git rev-parse --short HEAD)" | tee -a "$IMPORT_LOG"

echo "Requesting sudo (enter password if prompted)..." | tee -a "$IMPORT_LOG"
if ! sudo -v; then
  echo "FAIL: sudo authentication required. Attach tmux and re-run this script." | tee -a "$IMPORT_LOG"
  exit 1
fi

echo "Running image import..." | tee -a "$IMPORT_LOG"
if bash scripts/dev/import-full-vnext-images-k3s.sh preview 2>&1 | tee -a "$IMPORT_LOG"; then
  echo "Import exit: 0" | tee -a "$IMPORT_LOG"
else
  echo "Import exit: $?" | tee -a "$IMPORT_LOG"
  exit 1
fi

echo "Running k3s image verify..." | tee -a "$VERIFY_LOG"
NS=impilo-full-preview bash scripts/dev/verify-full-boot-k3s-images.sh preview 2>&1 | tee "$VERIFY_LOG"
verify_exit=${PIPESTATUS[0]}
echo "Verify exit: $verify_exit" | tee -a "$IMPORT_LOG"
echo "Finished at $(date -Is)" | tee -a "$IMPORT_LOG"
exit "$verify_exit"
