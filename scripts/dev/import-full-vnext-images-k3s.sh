#!/usr/bin/env bash
# Import built impilo/* and official full-boot infra images into k3s containerd.
set -euo pipefail
source "$(dirname "$0")/../full-boot/_full-boot-common.sh"

TAG="${1:-preview}"
IMPORT_INFRA="${IMPORT_INFRA:-1}"
IMPORTED=0
FAILED=0

if command -v k3s >/dev/null 2>&1 || command -v ctr >/dev/null 2>&1; then
  if ! sudo -n true 2>/dev/null; then
    echo "ERROR: k3s image import requires passwordless sudo (or run this script in an interactive VM terminal)."
    echo "  Example: bash scripts/dev/import-full-vnext-images-k3s.sh preview-\$(git rev-parse --short HEAD)"
    exit 2
  fi
fi

import_ref() {
  local ref="$1"
  local safe
  safe="$(echo "$ref" | tr '/:@' '___')"
  local tar="/tmp/impilo-image-${safe}.tar"
  echo "Saving $ref -> $tar"
  if ! docker save "$ref" -o "$tar"; then
    FAILED=$((FAILED + 1))
    return 1
  fi
  if command -v k3s >/dev/null 2>&1; then
    if sudo k3s ctr images import "$tar"; then
      IMPORTED=$((IMPORTED + 1))
    else
      FAILED=$((FAILED + 1))
    fi
  elif command -v ctr >/dev/null 2>&1; then
    if sudo ctr -n k8s.io images import "$tar"; then
      IMPORTED=$((IMPORTED + 1))
    else
      FAILED=$((FAILED + 1))
    fi
  else
    echo "WARN: no k3s/ctr — skip import for $ref"
    FAILED=$((FAILED + 1))
  fi
  rm -f "$tar"
}

while read -r ref; do
  [[ -z "$ref" ]] && continue
  import_ref "$ref" || true
done < <(docker images --format '{{.Repository}}:{{.Tag}}' | grep -E '^impilo/.+:('"${TAG}"'|preview-)' || true)

if [[ "$IMPORT_INFRA" == "1" ]]; then
  INFRA_REFS=(
    postgres:16-alpine
    redis:7-alpine
    apache/kafka:3.7.1
    quay.io/keycloak/keycloak:25.0
    minio/minio:latest
    hapiproject/hapi:v7.4.0
    envoyproxy/envoy:v1.31-latest
  )
  for ref in "${INFRA_REFS[@]}"; do
    if docker image inspect "$ref" >/dev/null 2>&1; then
      import_ref "$ref" || true
    else
      echo "WARN: infra image not local, skip: $ref"
      FAILED=$((FAILED + 1))
    fi
  done
fi

echo "Imported: $IMPORTED failed: $FAILED"
[[ $FAILED -eq 0 ]]
