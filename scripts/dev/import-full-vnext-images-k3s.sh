#!/usr/bin/env bash
# Import built impilo/* images into k3s containerd (VM preview hosts).
set -euo pipefail
source "$(dirname "$0")/../full-boot/_full-boot-common.sh"

TAG="${1:-preview}"
IMPORTED=0
FAILED=0

while read -r ref; do
  [[ -z "$ref" ]] && continue
  name="${ref#impilo/}"
  name="${name%:preview}"
  tar="/tmp/impilo-image-${name}.tar"
  echo "Saving $ref -> $tar"
  docker save "$ref" -o "$tar" || { FAILED=$((FAILED + 1)); continue; }
  if command -v k3s >/dev/null 2>&1; then
    sudo k3s ctr images import "$tar" || FAILED=$((FAILED + 1))
  elif command -v ctr >/dev/null 2>&1; then
    sudo ctr -n k8s.io images import "$tar" || FAILED=$((FAILED + 1))
  else
    echo "WARN: no k3s/ctr — skip import for $ref"
    FAILED=$((FAILED + 1))
  fi
  rm -f "$tar"
  IMPORTED=$((IMPORTED + 1))
done < <(docker images --format '{{.Repository}}:{{.Tag}}' | grep -E '^impilo/.+:('"${TAG}"'|preview-)' || true)

echo "Imported: $IMPORTED failed: $FAILED"
[[ $FAILED -eq 0 ]]
