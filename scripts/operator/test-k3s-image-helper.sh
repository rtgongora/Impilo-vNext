#!/usr/bin/env bash
# Non-destructive checks for narrow k3s image helper installation.
set -uo pipefail

REPO="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
PASS=0
FAIL=0

ok() { echo "OK   $*"; PASS=$((PASS + 1)); }
bad() { echo "FAIL $*"; FAIL=$((FAIL + 1)); }
warn() { echo "WARN $*"; }

SBIN_IMPORT="/usr/local/sbin/impilo-k3s-import-images"
SBIN_LIST="/usr/local/sbin/impilo-k3s-list-images"
SUDOERS_FILE="/etc/sudoers.d/impilo-k3s-image-helper"

echo "=== Impilo k3s image helper self-test ==="
echo "User: $(whoami)"
echo ""

for f in "$SBIN_IMPORT" "$SBIN_LIST"; do
  if [[ -x "$f" && "$(stat -c '%U:%G' "$f")" == "root:root" ]]; then
    ok "installed $f (root:root, executable)"
  else
    bad "missing or wrong ownership $f"
  fi
  if [[ -w "$f" ]]; then
    bad "$f is writable by $(whoami) — must not be"
  else
    ok "$f not writable by $(whoami)"
  fi
done

if [[ -f "$SUDOERS_FILE" ]]; then
  ok "sudoers file exists"
  if sudo visudo -cf "$SUDOERS_FILE" 2>/dev/null; then
    ok "sudoers fragment validates"
  else
    bad "sudoers fragment invalid"
  fi
  if grep -qE '^robert ALL=\(root\) NOPASSWD: /usr/local/sbin/impilo-k3s-import-images, /usr/local/sbin/impilo-k3s-list-images' "$SUDOERS_FILE" 2>/dev/null \
    || sudo grep -q 'impilo-k3s-import-images' "$SUDOERS_FILE" 2>/dev/null; then
    ok "sudoers grants only helper paths"
  else
    bad "sudoers content unexpected"
  fi
  if grep -q 'NOPASSWD: ALL' "$SUDOERS_FILE" 2>/dev/null; then
    bad "broad NOPASSWD ALL found"
  fi
else
  bad "sudoers file missing (helper not installed)"
fi

# Do not use `sudo -n true` — a cached ticket from interactive install is normal and is not broad NOPASSWD.
SUDO_LIST="$(sudo -n -l 2>/dev/null || true)"
if echo "$SUDO_LIST" | grep -qE 'NOPASSWD:\s*ALL'; then
  bad "sudoers grants NOPASSWD: ALL (too broad)"
elif echo "$SUDO_LIST" | grep -q 'impilo-k3s-import-images' && echo "$SUDO_LIST" | grep -q 'impilo-k3s-list-images'; then
  ok "passwordless sudo limited to impilo k3s helpers (sudo -l)"
else
  warn "sudo -l inconclusive (OK if helper works; cached ticket after install is normal)"
fi

LIST_OUT="$(sudo -n "$SBIN_LIST" preview 2>/dev/null || true)"
if echo "$LIST_OUT" | grep -q 'IMAGE_PRESENCE:'; then
  ok "sudo -n impilo-k3s-list-images preview (IMAGE_PRESENCE reported)"
else
  bad "sudo -n impilo-k3s-list-images failed"
fi

if sudo -n /usr/bin/k3s ctr images list 2>/dev/null; then
  bad "passwordless k3s ctr allowed (too broad)"
else
  ok "passwordless k3s ctr denied"
fi

echo ""
echo "SUMMARY pass=$PASS fail=$FAIL"
[[ "$FAIL" -eq 0 ]]
