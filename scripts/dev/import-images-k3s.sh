#!/usr/bin/env bash
set -euo pipefail
SUDO_PASS="${SUDO_PASS:-}"
if [[ -n "$SUDO_PASS" ]]; then echo "$SUDO_PASS" | sudo -S -v; fi

for img in impilo/experience-bff:preview impilo/one-ui-shell:preview; do
  echo "Importing $img into k3s..."
  docker save "$img" | sudo k3s ctr images import -
done

sudo k3s ctr images ls | grep impilo || true
cd /opt/impilo/repos/Impilo-vNext
bash scripts/deploy/preview-deploy.sh || true
kubectl rollout restart deployment/experience-bff deployment/one-ui-shell -n impilo-preview || true
sleep 25
kubectl get pods -n impilo-preview
curl -s -o /dev/null -w "http_root=%{http_code}\n" http://127.0.0.1/ || true
curl -s http://127.0.0.1/health/version 2>/dev/null | head -c 300 || true
