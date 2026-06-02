#!/usr/bin/env bash
# Remove Impilo narrow k3s image helper and sudoers drop-in.
# Must be run as root: sudo bash scripts/operator/uninstall-k3s-image-helper.sh
set -euo pipefail

if [[ "$(id -u)" -ne 0 ]]; then
  echo "ERROR: run as root: sudo bash $0" >&2
  exit 1
fi

SUDOERS_FILE="/etc/sudoers.d/impilo-k3s-image-helper"
SBIN_IMPORT="/usr/local/sbin/impilo-k3s-import-images"
SBIN_LIST="/usr/local/sbin/impilo-k3s-list-images"
LIB_FILE="/usr/local/libexec/impilo/k3s-image-helper-common.sh"

rm -f "$SUDOERS_FILE"
rm -f "$SBIN_IMPORT" "$SBIN_LIST"
rm -f "$LIB_FILE"
rmdir /usr/local/libexec/impilo 2>/dev/null || true

visudo -c

echo "UNINSTALLED: Impilo k3s image helper and sudoers drop-in removed."
echo "Fall back to interactive: sudo -v && bash scripts/dev/import-full-vnext-images-k3s.sh preview"
