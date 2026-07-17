#!/usr/bin/env bash
#
# restore-public-edge.sh — re-create the public TLS/ingress objects that live in
# the impilo-full-preview namespace but are NOT part of the Helm chart, and so are
# destroyed by the fullboot namespace wipe (helm uninstall + kubectl delete ns).
#
# Without this, after a fullboot the public host impilo.mohcc.gov.zw has no
# IngressRoute → Traefik serves its self-signed default cert
# (net::ERR_CERT_AUTHORITY_INVALID, HSTS blocks bypass) and the site is down.
#
# All steps here are non-root and idempotent. The ONE piece this script CANNOT do
# is recreate the TLS secret itself (it reads the root-only Let's Encrypt private
# key); it prints the exact sudo command for that instead.
#
# Run standalone any time, or wired into the fullboot deploy tail.
set -uo pipefail

REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
NS="${NAMESPACE:-impilo-full-preview}"
TLS_DIR="$REPO_PATH/deploy/tls/mohcc-gov"
SECRET_NAME="impilo-mohcc-gov-zw-tls"
HOST="impilo.mohcc.gov.zw"
ACME_PORT="${ACME_HOST_NGINX_PORT:-8089}"

echo "--- Restore public edge (TLS/ingress) for $HOST in $NS ---"

# 1) IngressRoutes + ACME ingress + livekit + public-website (all versioned manifests)
for f in ingressroutes.yaml acme-ingress.yaml livekit-ingressroute.yaml public-website.yaml; do
  if [[ -f "$TLS_DIR/$f" ]]; then
    kubectl apply -f "$TLS_DIR/$f" >/dev/null 2>&1 && echo "  applied $f" \
      || echo "  WARN: apply $f failed (deps may be missing — non-fatal)"
  fi
done

# 2) acme-host-nginx Service + manual Endpoints (host nginx :$ACME_PORT on the node).
#    Not a versioned manifest historically — recreate here. Node IP derived, not hard-coded.
NODE_IP="$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}' 2>/dev/null)"
if [[ -n "$NODE_IP" ]]; then
  kubectl apply -f - >/dev/null 2>&1 <<EOF && echo "  acme-host-nginx svc+endpoints -> ${NODE_IP}:${ACME_PORT}"
apiVersion: v1
kind: Service
metadata: { name: acme-host-nginx, namespace: ${NS} }
spec:
  ports: [{ name: http, port: 80, targetPort: ${ACME_PORT}, protocol: TCP }]
---
apiVersion: v1
kind: Endpoints
metadata: { name: acme-host-nginx, namespace: ${NS} }
subsets:
  - addresses: [{ ip: ${NODE_IP} }]
    ports: [{ name: http, port: ${ACME_PORT}, protocol: TCP }]
EOF
else
  echo "  WARN: could not derive node InternalIP — acme-host-nginx endpoints not restored"
fi

# 3) TLS secret — the ONE root-only step. Detect and instruct; never fail the deploy on it.
if kubectl get secret "$SECRET_NAME" -n "$NS" >/dev/null 2>&1; then
  echo "  TLS secret $SECRET_NAME present."
else
  cat <<EOF

  ================================================================
  ACTION REQUIRED (root): the public TLS secret was wiped and must
  be recreated from the host Let's Encrypt cert. Traefik is serving
  its self-signed default cert until you run:

      sudo /usr/local/bin/sync-mohcc-gov-tls.sh

  (No pod restart needed — Traefik hot-loads the secret.)
  Verify:  echo | openssl s_client -connect 127.0.0.1:443 \\
             -servername $HOST 2>/dev/null | openssl x509 -noout -issuer
  ================================================================
EOF
fi

echo "--- Restore public edge: done ---"
