#!/usr/bin/env bash
# In-place Helm recovery: do NOT call fullboot.sh deploy (it deletes the NS).
# Calls full-boot-preview-deploy.sh directly after retag/push + secrets.
set -euo pipefail
cd /opt/impilo/repos/Impilo-vNext
LOG=/tmp/fullboot-helm-inplace-32f2c4fa6.log
exec > >(tee -a "$LOG") 2>&1

echo "==== IN-PLACE HELM RECOVERY START $(date -u +%Y-%m-%dT%H:%M:%SZ) ===="
echo "HEAD=$(git rev-parse HEAD)"
echo "NOTE: bypassing fullboot.sh deploy to avoid fb_cleanup_full_boot_namespace"

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

# Keep existing NS / failed release — upgrade in place.
kubectl create namespace "$NAMESPACE" 2>/dev/null || true
# Wait if a prior delete left it Terminating
for i in $(seq 1 60); do
  phase=$(kubectl get ns "$NAMESPACE" -o jsonpath='{.status.phase}' 2>/dev/null || echo Missing)
  [[ "$phase" == "Active" ]] && break
  echo "Waiting for namespace Active (now=$phase)..."
  sleep 5
done

# Point mutable :preview tags at the rebuilt SHA images before push/digest resolve.
if docker image inspect "impilo/one-ui-shell:preview-32f2c4fa6" >/dev/null 2>&1; then
  docker tag "impilo/one-ui-shell:preview-32f2c4fa6" "impilo/one-ui-shell:preview"
  echo "OK: retagged one-ui-shell:preview <- preview-32f2c4fa6"
else
  echo "WARN: local one-ui-shell:preview-32f2c4fa6 missing"
fi
if docker image inspect "impilo/experience-bff:preview-32f2c4fa6" >/dev/null 2>&1; then
  docker tag "impilo/experience-bff:preview-32f2c4fa6" "impilo/experience-bff:preview"
  echo "OK: retagged experience-bff:preview <- preview-32f2c4fa6"
fi

echo "--- Pre-seed secrets (also re-run inside deploy script after ns ensure) ---"
NAMESPACE="$NAMESPACE" bash scripts/secrets/bootstrap-secrets.sh

echo "--- Direct full-boot-preview-deploy (no NS wipe) ---"
printf '%s\n' 'AUTHORIZE FULL BOOT PREVIEW DEPLOY' | bash scripts/deploy/full-boot-preview-deploy.sh
rc=${PIPESTATUS[0]}
echo "DEPLOY_EXIT:$rc"

echo "--- Post-helm secrets refresh (livekit keys once CM exists) ---"
NAMESPACE="$NAMESPACE" bash scripts/secrets/bootstrap-secrets.sh || true

echo "==== IN-PLACE HELM RECOVERY END $(date -u +%Y-%m-%dT%H:%M:%SZ) exit=$rc ===="
exit "$rc"
