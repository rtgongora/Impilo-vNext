#!/usr/bin/env bash
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
SSH_PORT="${SSH_PORT:-2276}"
FAIL=0

check() {
  local name="$1" cmd="$2"
  if command -v "$cmd" >/dev/null 2>&1; then
    echo "OK  $name ($cmd)"
  else
    echo "MISSING  $name ($cmd)"
    FAIL=1
  fi
}

echo "=== Impilo remote workspace tool check ==="
check git git
check java java
check maven mvn
check node node
check npm npm
check docker docker
check kubectl kubectl
check helm helm
check k3s k3s

if [[ -d "$REPO_PATH/.git" ]]; then
  echo "OK  repo at $REPO_PATH"
  echo "    branch: $(git -C "$REPO_PATH" branch --show-current)"
  echo "    remote: $(git -C "$REPO_PATH" remote get-url origin 2>/dev/null || echo unknown)"
else
  echo "MISSING  repo at $REPO_PATH"
  FAIL=1
fi

if command -v kubectl >/dev/null 2>&1; then
  kubectl get nodes 2>/dev/null || { echo "WARN  kubectl cannot reach cluster"; FAIL=1; }
  kubectl get ns impilo-preview >/dev/null 2>&1 && echo "OK  namespace impilo-preview" || echo "MISSING  namespace impilo-preview"
fi

if ss -tlnp 2>/dev/null | grep -q ":${SSH_PORT}"; then
  echo "OK  SSH listening on port ${SSH_PORT}"
else
  echo "WARN  SSH may not be listening on port ${SSH_PORT} (check NAT/forwarding)"
fi

exit "$FAIL"
