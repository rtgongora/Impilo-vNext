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
    if [[ -z "${SUDO_PASS:-}" ]]; then
      echo "ERROR: k3s image import requires passwordless sudo, SUDO_PASS, or an interactive VM terminal."
      echo "  Run: sudo -v"
      echo "  Then: bash scripts/dev/import-full-vnext-images-k3s.sh preview"
      exit 2
    fi
    SUDO_CMD=(sudo -S)
  else
    SUDO_CMD=(sudo -n)
  fi
else
  SUDO_CMD=()
fi

# Import a saved tar into k3s/containerd (returns 0 on success).
ctr_import_tar() {
  local tar="$1"
  if [[ ${#SUDO_CMD[@]} -gt 0 ]]; then
    if [[ -n "${SUDO_PASS:-}" ]]; then
      if echo "$SUDO_PASS" | "${SUDO_CMD[@]}" k3s ctr images import "$tar" 2>/dev/null \
        || echo "$SUDO_PASS" | "${SUDO_CMD[@]}" ctr -n k8s.io images import "$tar"; then
        return 0
      fi
      return 1
    fi
    if "${SUDO_CMD[@]}" k3s ctr images import "$tar" 2>/dev/null \
      || "${SUDO_CMD[@]}" ctr -n k8s.io images import "$tar"; then
      return 0
    fi
    return 1
  fi
  if command -v k3s >/dev/null 2>&1; then
    k3s ctr images import "$tar"
    return
  fi
  if command -v ctr >/dev/null 2>&1; then
    ctr -n k8s.io images import "$tar"
    return
  fi
  echo "WARN: no k3s/ctr available"
  return 1
}

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
  if ctr_import_tar "$tar"; then
    IMPORTED=$((IMPORTED + 1))
  else
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
