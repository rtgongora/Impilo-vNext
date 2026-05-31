#!/usr/bin/env bash
# Full boot preview deploy — requires explicit authorization; uses impilo-full-preview namespace.
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO_PATH"
source scripts/full-boot/_full-boot-common.sh

AUTH_PHRASE="AUTHORIZE FULL BOOT PREVIEW DEPLOY"
NAMESPACE="${FULL_BOOT_NAMESPACE:-impilo-full-preview}"

echo "=== Full boot preview deploy ==="
echo "Branch: $(git branch --show-current)"
echo "Commit: $(git rev-parse --short HEAD)"
echo "Target namespace: $NAMESPACE"
echo ""
echo "This will NOT modify namespace: $SLICE_NAMESPACE"
echo ""
read -r -p "Type authorization phrase: " user_auth
if [[ "$user_auth" != "$AUTH_PHRASE" ]]; then
  echo "ABORT: authorization phrase mismatch."
  exit 1
fi

full_boot_ensure_artifacts

if ! bash scripts/build/build-full-vnext.sh; then
  echo "ABORT: full build failed for required targets."
  exit 1
fi

if ! bash scripts/build/build-full-vnext-images.sh; then
  echo "ABORT: required image build failed."
  exit 1
fi

kubectl create namespace "$NAMESPACE" 2>/dev/null || true

helm upgrade --install impilo-full-preview deploy/helm/impilo-vnext \
  -n "$NAMESPACE" \
  -f deploy/helm/impilo-vnext/values-preview.yaml \
  --set global.environment=full-preview \
  --wait --timeout 20m

bash scripts/dev/import-full-vnext-images-k3s.sh "$(full_boot_image_tag)" || true

kubectl rollout status deployment -n "$NAMESPACE" --timeout=600s || true
bash scripts/test/run-full-boot-smoke-tests.sh
bash scripts/guard/check-full-boot-runtime-completeness.sh || true

echo "Full boot deploy finished. Namespace: $NAMESPACE"
