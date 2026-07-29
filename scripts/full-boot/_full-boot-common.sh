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

# generate-full-boot-artifacts.mjs imports js-yaml, which scripts/full-boot/package.json declares but
# nothing ever installed — so the generator died with ERR_MODULE_NOT_FOUND at every call site, and
# every caller swallowed it with `|| true`. Registry-driven artifacts silently went stale instead,
# which is the exact failure 4ae10ccfd set out to stop. Provision the way check-product-truth.sh
# already does for scripts/completeness, and let the generator's own exit code through.
full_boot_generate_artifacts() {
  if [[ ! -d "$REPO_PATH/scripts/full-boot/node_modules" ]]; then
    (cd "$REPO_PATH/scripts/full-boot" && npm install --silent --no-audit --no-fund) || {
      echo "full-boot: npm install failed in scripts/full-boot (environment, not registry)" >&2
      return 1
    }
  fi
  node "$REPO_PATH/scripts/full-boot/generate-full-boot-artifacts.mjs"
}

full_boot_ensure_artifacts() {
  if [[ ! -f "$FULL_BOOT_CLASSIFICATION" ]]; then
    echo "Generating full-boot artifacts..."
    full_boot_generate_artifacts
  fi
}

full_boot_short_sha() {
  git rev-parse --short HEAD
}

full_boot_image_tag() {
  echo "preview-$(full_boot_short_sha)"
}
