#!/usr/bin/env bash
# Import full-boot images into k3s containerd via narrow helper (default: required+infra, missing-only).
set -euo pipefail
source "$(dirname "$0")/../full-boot/_full-boot-common.sh"

TAG="${1:-preview}"
shift 2>/dev/null || shift 1 2>/dev/null || true
REPO="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
HELPER_IMPORT="/usr/local/sbin/impilo-k3s-import-images"
EXTRA_ARGS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --only)
      EXTRA_ARGS+=("--only" "${2:-}")
      shift 2
      ;;
    --all-local-preview|--force)
      EXTRA_ARGS+=("$1")
      shift
      ;;
    *)
      echo "Unknown arg: $1" >&2
      exit 1
      ;;
  esac
done

if [[ "$(id -u)" -eq 0 && -x "$HELPER_IMPORT" ]]; then
  exec "$HELPER_IMPORT" "$TAG" "$REPO" "${EXTRA_ARGS[@]}"
fi

if [[ -x "$HELPER_IMPORT" ]]; then
  if sudo -n "$HELPER_IMPORT" "$TAG" "$REPO" "${EXTRA_ARGS[@]}"; then
    exit 0
  fi
  echo "ERROR: helper import failed or import already running (single-flight lock)." >&2
  echo "  bash scripts/operator/fullboot.sh cleanup-imports" >&2
  exit 2
fi

echo "ERROR: install narrow helper: sudo bash scripts/operator/install-k3s-image-helper.sh" >&2
echo "  Then: bash scripts/operator/fullboot.sh import-images" >&2
exit 2
