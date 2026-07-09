#!/usr/bin/env bash
#
# sync-mohcc-gov-tls.sh — push the host-side Let's Encrypt cert for
# impilo.mohcc.gov.zw into the Kubernetes TLS secret that Traefik serves.
#
# Used as a certbot --deploy-hook so that every renewal (and the initial
# issuance) refreshes the k8s secret; without this, Traefik would keep serving
# the stale cert after certbot renews on the host.
#
# Install: sudo install -m750 scripts/tls/sync-mohcc-gov-tls.sh /usr/local/bin/sync-mohcc-gov-tls.sh
# Wire in: certbot ... --deploy-hook /usr/local/bin/sync-mohcc-gov-tls.sh
#
# Runs as root (certbot's context). k3s kubeconfig defaults to the root-readable
# /etc/rancher/k3s/k3s.yaml; override KUBECONFIG if your cluster differs.
set -euo pipefail

NAMESPACE="${NAMESPACE:-impilo-full-preview}"
SECRET_NAME="${SECRET_NAME:-impilo-mohcc-gov-zw-tls}"
CERT_DIR="${CERT_DIR:-/etc/letsencrypt/live/impilo.mohcc.gov.zw}"
export KUBECONFIG="${KUBECONFIG:-/etc/rancher/k3s/k3s.yaml}"

if [[ ! -r "${CERT_DIR}/fullchain.pem" || ! -r "${CERT_DIR}/privkey.pem" ]]; then
  echo "sync-mohcc-gov-tls: cert files not found under ${CERT_DIR}" >&2
  exit 1
fi

kubectl create secret tls "${SECRET_NAME}" \
  --cert="${CERT_DIR}/fullchain.pem" \
  --key="${CERT_DIR}/privkey.pem" \
  -n "${NAMESPACE}" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "sync-mohcc-gov-tls: secret ${NAMESPACE}/${SECRET_NAME} updated from ${CERT_DIR}"
