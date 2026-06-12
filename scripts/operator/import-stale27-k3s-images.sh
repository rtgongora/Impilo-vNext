#!/usr/bin/env bash
# Import preview-4917def8 images for the 27 stale full-preview services into k3s containerd.
# Uses the approved impilo-k3s-import-images helper (--runtime-only --only).
# Run as root: sudo bash scripts/operator/import-stale27-k3s-images.sh
set -euo pipefail

REPO="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
TAG="${FULL_BOOT_IMAGE_TAG:-preview-4917def8}"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "ERROR: run as root: sudo bash $0" >&2
  exit 1
fi

ONLY_IDS="ai-model-registry-service,audit-ledger-service,butano-service,community-service,credential-verification-service,document-service,guidance-service,identity-assurance-service,inventory-elmis-adapter,inventory-service,llm-orchestration-service,msika-service,mushe-wallet-service,pharmacy-service,product-registry-service,scheduling-service,search-service,share-slip-service,tshepo-audit-service,tshepo-authz-service,tshepo-consent-service,tshepo-identity-service,tshepo-keys-service,tshepo-offline-service,ubomi-service,varapi-service,zibo-service"

echo "=== stale-27 k3s image import ==="
echo "tag=$TAG repo=$REPO"

bash "$REPO/scripts/operator/install-k3s-image-helper.sh"

/usr/local/sbin/impilo-k3s-import-images "$TAG" "$REPO" --runtime-only --only "$ONLY_IDS" --force

echo "STALE27_IMPORT: complete tag=$TAG"
