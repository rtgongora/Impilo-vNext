#!/usr/bin/env bash
# Import only Impilo preview images from local Docker into k3s containerd.
# Intended for root execution via limited NOPASSWD sudo (see docs/environment/DEPLOYMENT_SUDO_AND_IMAGE_IMPORT_HARDENING.md).
set -euo pipefail

if [[ "$(id -u)" -ne 0 ]]; then
  echo "ERROR: must run as root (e.g. sudo /usr/local/sbin/k3s-import-preview-images.sh)" >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1 || ! command -v k3s >/dev/null 2>&1; then
  echo "ERROR: docker and k3s are required" >&2
  exit 1
fi

ALLOWED_IMAGES=(
  "impilo/experience-bff:preview"
  "impilo/one-ui-shell:preview"
)

for img in "${ALLOWED_IMAGES[@]}"; do
  if ! docker image inspect "$img" >/dev/null 2>&1; then
    echo "ERROR: missing local image $img — build with scripts/dev/build-images.sh first" >&2
    exit 1
  fi
  echo "Importing $img into k3s containerd..."
  docker save "$img" | k3s ctr images import -
done

echo "Imported preview images:"
k3s ctr images ls | grep -E 'impilo/(experience-bff|one-ui-shell)' || true
