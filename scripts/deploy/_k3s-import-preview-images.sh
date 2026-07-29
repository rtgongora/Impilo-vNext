#!/usr/bin/env bash
# Run k3s-import-preview-images.sh as root (NOPASSWD helper, cached sudo, or interactive).
set -euo pipefail

run_k3s_import_preview_images() {
  local repo="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
  local installed="/usr/local/sbin/k3s-import-preview-images.sh"
  local repo_script="$repo/scripts/deploy/k3s-import-preview-images.sh"

  if [[ ! -x "$repo_script" ]]; then
    echo "ERROR: missing $repo_script" >&2
    return 1
  fi

  if [[ -x "$installed" ]] && sudo -n "$installed" 2>/dev/null; then
    return 0
  fi

  if sudo -n "$repo_script" 2>/dev/null; then
    return 0
  fi

  if [[ -n "${SUDO_PASS:-}" ]]; then
    echo "$SUDO_PASS" | sudo -S "$repo_script"
    return 0
  fi

  echo "Importing preview images (sudo required)..."
  sudo "$repo_script"
}
