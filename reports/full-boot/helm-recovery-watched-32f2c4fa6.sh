#!/usr/bin/env bash
set -euo pipefail
cd /opt/impilo/repos/Impilo-vNext
LOG=/tmp/fullboot-helm-watched-32f2c4fa6.log
exec > >(tee -a "$LOG") 2>&1
milestone() { echo ""; echo "==== MILESTONE: $* ===="; echo "==== $(date -u +%Y-%m-%dT%H:%M:%SZ) ===="; }

milestone "START watched recovery HEAD=$(git rev-parse --short HEAD)"
echo "NOTE: bypasses fullboot.sh deploy (avoids NS wipe). Uses FULL_BOOT_HELM_NO_WAIT=1."

export NAMESPACE=impilo-full-preview
export FULL_BOOT_NAMESPACE=impilo-full-preview
export FULL_BOOT_IMAGE_TAG=preview IMAGE_TAG=preview
export FULL_BOOT_SKIP_BUILD=1
export FULLBOOT_SKIP_GATES=1
export FULLBOOT_DEPLOY_AUTHORIZED=1
export FULL_BOOT_HELM_NO_WAIT=1
export FULL_BOOT_HELM_WAIT_TIMEOUT=30m
export FULL_BOOT_ROLLOUT_TIMEOUT=20m
unset FULL_BOOT_SKIP_IMPORT FULL_BOOT_SKIP_PUSH FULL_BOOT_FORCE_IMPORT_RUNTIME || true

kubectl create namespace "$NAMESPACE" 2>/dev/null || true

milestone "Rebuild zibo-service only (Flyway fix)"
IMPILO_UI_BUNDLE_HASH_MODE=strict bash scripts/build/build-full-vnext-images.sh --only zibo-service

milestone "Retag UI/BFF to preview from preview-32f2c4fa6"
docker tag impilo/one-ui-shell:preview-32f2c4fa6 impilo/one-ui-shell:preview
docker tag impilo/one-ui-shell:preview-32f2c4fa6 impilo/one-ui-shell:preview-32f2c4fa6  # keep
# also tag SHA tag for registry
if docker image inspect impilo/experience-bff:preview-32f2c4fa6 >/dev/null 2>&1; then
  docker tag impilo/experience-bff:preview-32f2c4fa6 impilo/experience-bff:preview
fi
docker tag impilo/zibo-service:preview "impilo/zibo-service:preview-32f2c4fa6" 2>/dev/null || true

milestone "Bootstrap secrets (incl livekit from CM)"
NAMESPACE="$NAMESPACE" bash scripts/secrets/bootstrap-secrets.sh

milestone "Direct full-boot-preview-deploy (no NS wipe, no helm --wait)"
printf '%s\n' 'AUTHORIZE FULL BOOT PREVIEW DEPLOY' | bash scripts/deploy/full-boot-preview-deploy.sh
rc=${PIPESTATUS[0]}
echo "DEPLOY_EXIT:$rc"

milestone "Post-helm livekit secret refresh"
NAMESPACE="$NAMESPACE" bash scripts/secrets/bootstrap-secrets.sh || true

# Restart livekit-related pods if keys were just added
kubectl rollout restart deploy/livekit deploy/livekit-egress deploy/rtc-gateway-service -n "$NAMESPACE" 2>/dev/null || true

milestone "Health probe"
curl -sS -o /tmp/hv.json -w "health_http=%{http_code}\n" --connect-timeout 5 http://127.0.0.1/health/version || echo health_fail
head -c 300 /tmp/hv.json; echo

milestone "Digest truth"
kubectl get deploy one-ui-shell -n "$NAMESPACE" -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}' || true
rg -n 'oneUiShell:|experienceBff:' -A1 deploy/helm/impilo-vnext/values-full-preview-digests.generated.yaml | head -20

milestone "Pod summary"
kubectl get pods -n "$NAMESPACE" --no-headers | awk '{print $3}' | sort | uniq -c | sort -rn

milestone "END exit=$rc"
exit "$rc"
