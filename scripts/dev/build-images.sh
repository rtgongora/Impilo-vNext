#!/usr/bin/env bash
set -euo pipefail
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
TAG="${TAG:-preview-$(git -C "$REPO_PATH" rev-parse --short HEAD 2>/dev/null || echo local)}"
cd "$REPO_PATH"

echo "Building experience-bff image: impilo/experience-bff:${TAG}"
docker build -f services/experience-bff/Dockerfile \
  -t "impilo/experience-bff:${TAG}" \
  -t impilo/experience-bff:preview \
  services/experience-bff

echo "Building one-ui-shell image: impilo/one-ui-shell:${TAG}"
# Same-origin by default: empty NEXT_PUBLIC_* so the browser bundle uses relative paths
# routed by Traefik to the BFF. This keeps the image independent of the public IP.
docker build -f ui/one-ui-shell/Dockerfile \
  --build-arg NEXT_PUBLIC_BFF_URL="${NEXT_PUBLIC_BFF_URL:-}" \
  --build-arg NEXT_PUBLIC_API_GATEWAY_URL="${NEXT_PUBLIC_API_GATEWAY_URL:-}" \
  -t "impilo/one-ui-shell:${TAG}" \
  -t impilo/one-ui-shell:preview \
  .

echo "Importing images into k3s containerd..."
# shellcheck source=scripts/deploy/_k3s-import-preview-images.sh
source "$REPO_PATH/scripts/deploy/_k3s-import-preview-images.sh"
run_k3s_import_preview_images

echo "Imported images into k3s containerd."
docker images | grep impilo || true
