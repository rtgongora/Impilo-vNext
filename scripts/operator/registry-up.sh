#!/usr/bin/env bash
# Start local OCI registry for full-boot image push/pull (no sudo).
set -euo pipefail
REPO="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
REGISTRY_NAME="${IMPILO_LOCAL_REGISTRY_NAME:-impilo-local-registry}"
REGISTRY_PORT="${IMPILO_LOCAL_REGISTRY_PORT:-5000}"
REGISTRY_HOST="${IMPILO_LOCAL_REGISTRY_HOST:-127.0.0.1}"
DATA_DIR="${IMPILO_REGISTRY_DATA:-$REPO/reports/full-boot/local-registry-data}"

mkdir -p "$DATA_DIR"
if docker ps --format '{{.Names}}' | grep -qxF "$REGISTRY_NAME"; then
  echo "OK: registry container $REGISTRY_NAME already running"
  exit 0
fi
if docker ps -a --format '{{.Names}}' | grep -qxF "$REGISTRY_NAME"; then
  docker start "$REGISTRY_NAME" >/dev/null
  echo "OK: started existing $REGISTRY_NAME"
  exit 0
fi

docker run -d \
  --restart unless-stopped \
  --name "$REGISTRY_NAME" \
  -p "${REGISTRY_HOST}:${REGISTRY_PORT}:5000" \
  -v "${DATA_DIR}:/var/lib/registry" \
  registry:2

echo "OK: local registry http://${REGISTRY_HOST}:${REGISTRY_PORT}"
echo "Data: $DATA_DIR"
