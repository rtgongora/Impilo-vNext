#!/usr/bin/env bash
# Import only selected full-boot images into k3s (fast path after partial rebuilds).
set -euo pipefail
REPO="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
TAG="${FULL_BOOT_IMAGE_TAG:-preview}"
IDS="${*:-}"
if [[ -z "$IDS" ]]; then
  echo "Usage: bash scripts/operator/import-selected-k3s-images.sh <service-id> [service-id...]"
  echo "Example: bash scripts/operator/import-selected-k3s-images.sh tshepo-authz-service butano-service"
  exit 1
fi
ONLY="$(echo "$IDS" | tr ' ' ',')"
HELPER="/usr/local/sbin/impilo-k3s-import-images"
if [[ ! -x "$HELPER" ]]; then
  echo "ERROR: install helper first: sudo bash scripts/operator/install-k3s-image-helper.sh"
  exit 1
fi
echo "=== Selected k3s import (tag=$TAG) ==="
echo "Services: $ONLY"
if ! sudo -n "$HELPER" "$TAG" "$REPO" --only "$ONLY" 2>&1; then
  echo ""
  echo "If --only is unsupported, reinstall helper:"
  echo "  sudo bash scripts/operator/install-k3s-image-helper.sh"
  exit 1
fi
echo "OK: selected import complete"
