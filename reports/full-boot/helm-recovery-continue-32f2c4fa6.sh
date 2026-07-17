#!/usr/bin/env bash
# Recovery continue after Helm deadline + missing impilo-app-secrets.
# Durable: run under tmux. Does not touch impilo-preview slice.
set -euo pipefail
cd /opt/impilo/repos/Impilo-vNext
LOG=/tmp/fullboot-helm-recovery-32f2c4fa6.log
exec > >(tee -a "$LOG") 2>&1

echo "==== HELM RECOVERY START $(date -u +%Y-%m-%dT%H:%M:%SZ) ===="
echo "HEAD=$(git rev-parse HEAD)"
echo "BRANCH=$(git branch --show-current)"

export NAMESPACE=impilo-full-preview
export FULL_BOOT_NAMESPACE=impilo-full-preview
export FULL_BOOT_IMAGE_TAG=preview
export IMAGE_TAG=preview
export FULL_BOOT_SKIP_BUILD=1
export FULLBOOT_SKIP_GATES=1
export FULLBOOT_DEPLOY_AUTHORIZED=1
export FULL_BOOT_HELM_WAIT_TIMEOUT="${FULL_BOOT_HELM_WAIT_TIMEOUT:-90m}"
export FULL_BOOT_ROLLOUT_TIMEOUT="${FULL_BOOT_ROLLOUT_TIMEOUT:-60m}"
unset FULL_BOOT_SKIP_IMPORT || true
unset FULL_BOOT_SKIP_PUSH || true
unset FULL_BOOT_FORCE_IMPORT_RUNTIME || true

echo "--- Bootstrap out-of-band app secrets (idempotent) ---"
NAMESPACE=impilo-full-preview bash scripts/secrets/bootstrap-secrets.sh

echo "--- Resume authorized fullboot deploy (skip rebuild; push+digest+helm) ---"
printf '%s\n' 'AUTHORIZE FULL BOOT PREVIEW DEPLOY' | bash scripts/operator/fullboot.sh deploy
echo "EXIT:${PIPESTATUS[0]}"
echo "==== HELM RECOVERY END $(date -u +%Y-%m-%dT%H:%M:%SZ) ===="
