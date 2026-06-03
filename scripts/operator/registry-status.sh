#!/usr/bin/env bash
# Status of local OCI registry for full-boot.
set -euo pipefail
REGISTRY_NAME="${IMPILO_LOCAL_REGISTRY_NAME:-impilo-local-registry}"
REGISTRY_PORT="${IMPILO_LOCAL_REGISTRY_PORT:-5000}"
REGISTRY_HOST="${IMPILO_LOCAL_REGISTRY_HOST:-127.0.0.1}"

echo "=== Local registry status ==="
if docker ps --format '{{.Names}} {{.Status}} {{.Ports}}' | grep -F "$REGISTRY_NAME" || true; then
  :
else
  echo "Registry container not running."
fi
if curl -sf "http://${REGISTRY_HOST}:${REGISTRY_PORT}/v2/" >/dev/null 2>&1; then
  echo "API: OK http://${REGISTRY_HOST}:${REGISTRY_PORT}/v2/"
  curl -sf "http://${REGISTRY_HOST}:${REGISTRY_PORT}/v2/_catalog" 2>/dev/null | head -c 500 || true
  echo ""
else
  echo "API: unreachable at http://${REGISTRY_HOST}:${REGISTRY_PORT}/v2/"
  exit 1
fi
