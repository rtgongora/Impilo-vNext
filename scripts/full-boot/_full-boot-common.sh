#!/usr/bin/env bash
# Shared paths for full vNext boot readiness scripts.
set -euo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

FULL_BOOT_REPORTS="${FULL_BOOT_REPORTS:-$REPO_PATH/reports/full-boot}"
FULL_BOOT_CLASSIFICATION="${FULL_BOOT_CLASSIFICATION:-$REPO_PATH/config/full-boot-service-classification.yml}"
FULL_BOOT_CATALOG="${FULL_BOOT_CATALOG:-$REPO_PATH/docs/architecture/FULL_VNEXT_SERVICE_CATALOG.md}"
SLICE_NAMESPACE="${SLICE_NAMESPACE:-impilo-preview}"
FULL_BOOT_NAMESPACE="${FULL_BOOT_NAMESPACE:-impilo-full-preview}"

full_boot_ensure_artifacts() {
  if [[ ! -f "$FULL_BOOT_CLASSIFICATION" ]]; then
    echo "Generating full-boot artifacts..."
    node scripts/full-boot/generate-full-boot-artifacts.mjs
  fi
}

full_boot_short_sha() {
  git rev-parse --short HEAD
}

full_boot_image_tag() {
  echo "preview-$(full_boot_short_sha)"
}
