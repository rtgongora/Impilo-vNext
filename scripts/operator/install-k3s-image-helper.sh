#!/usr/bin/env bash
# One-time VM install: narrow NOPASSWD sudo for Impilo k3s image import/list helpers only.
# Must be run as root: sudo bash scripts/operator/install-k3s-image-helper.sh
set -euo pipefail

if [[ "$(id -u)" -ne 0 ]]; then
  echo "ERROR: run as root: sudo bash $0" >&2
  exit 1
fi

REPO="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
SBIN_IMPORT="/usr/local/sbin/impilo-k3s-import-images"
SBIN_LIST="/usr/local/sbin/impilo-k3s-list-images"
LIB_DIR="/usr/local/libexec/impilo"
LIB_FILE="$LIB_DIR/k3s-image-helper-common.sh"
SUDOERS_FILE="/etc/sudoers.d/impilo-k3s-image-helper"
LOG_FILE="/var/log/impilo-k3s-image-helper.log"

for src in \
  "$REPO/scripts/operator/impilo-k3s-import-images" \
  "$REPO/scripts/operator/impilo-k3s-list-images" \
  "$REPO/scripts/operator/k3s-image-helper-common.sh"; do
  if [[ ! -f "$src" ]]; then
    echo "ERROR: missing $src" >&2
    exit 1
  fi
done

install -d -m 0755 /usr/local/sbin "$LIB_DIR"
install -m 0755 "$REPO/scripts/operator/impilo-k3s-import-images" "$SBIN_IMPORT"
install -m 0755 "$REPO/scripts/operator/impilo-k3s-list-images" "$SBIN_LIST"
install -m 0644 "$REPO/scripts/operator/k3s-image-helper-common.sh" "$LIB_FILE"
chown root:root "$SBIN_IMPORT" "$SBIN_LIST" "$LIB_FILE"

touch "$LOG_FILE"
chown root:root "$LOG_FILE"
chmod 0640 "$LOG_FILE"

cat >"$SUDOERS_FILE" <<'EOF'
# Impilo full-boot: passwordless sudo ONLY for root-owned k3s image helpers.
# Install: sudo bash scripts/operator/install-k3s-image-helper.sh
# Rollback: sudo bash scripts/operator/uninstall-k3s-image-helper.sh
Defaults!/usr/local/sbin/impilo-k3s-import-images !env_keep
Defaults!/usr/local/sbin/impilo-k3s-list-images !env_keep
robert ALL=(root) NOPASSWD: /usr/local/sbin/impilo-k3s-import-images, /usr/local/sbin/impilo-k3s-list-images
EOF
chown root:root "$SUDOERS_FILE"
chmod 0440 "$SUDOERS_FILE"

visudo -cf "$SUDOERS_FILE"
visudo -c

echo ""
echo "INSTALLED: Impilo k3s image helper"
echo "  $SBIN_IMPORT"
echo "  $SBIN_LIST"
echo "  $LIB_FILE"
echo "  $SUDOERS_FILE"
echo "  $LOG_FILE"
echo ""
echo "Robert may run (no password):"
echo "  sudo -n /usr/local/sbin/impilo-k3s-import-images preview"
echo "  sudo -n /usr/local/sbin/impilo-k3s-list-images preview"
echo ""
echo "Product-owner workflow:"
echo "  bash scripts/operator/fullboot.sh import-images"
echo "  bash scripts/operator/fullboot.sh verify-images"
echo ""
echo "Self-test (as robert):"
echo "  bash scripts/operator/test-k3s-image-helper.sh"
echo ""
echo "Rollback:"
echo "  sudo bash scripts/operator/uninstall-k3s-image-helper.sh"
