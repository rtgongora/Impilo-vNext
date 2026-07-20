#!/usr/bin/env bash
#
# sync-mohcc-gov-tls.sh — push the host-side Let's Encrypt cert for
# impilo.mohcc.gov.zw into the Kubernetes TLS secret that Traefik serves AND
# that the embedded LiveKit TURN server terminates TURN-TLS with.
#
# Used as a certbot --deploy-hook so that every renewal (and the initial
# issuance) refreshes the k8s secret; without this, Traefik would keep serving
# the stale cert after certbot renews on the host.
#
# Traefik hot-reloads the updated Secret automatically (no restart). LiveKit,
# however, reads the TURN cert only at process start, so a renewed cert does not
# take effect until the pod restarts. This script therefore rolls
# deployment/livekit — but ONLY when the certificate actually changed, so an
# unchanged renewal run (or a re-run) never needlessly bounces live calls.
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
LIVEKIT_DEPLOY="${LIVEKIT_DEPLOY:-livekit}"
ROLLOUT_TIMEOUT="${ROLLOUT_TIMEOUT:-180s}"
export KUBECONFIG="${KUBECONFIG:-/etc/rancher/k3s/k3s.yaml}"

if [[ ! -r "${CERT_DIR}/fullchain.pem" || ! -r "${CERT_DIR}/privkey.pem" ]]; then
  echo "sync-mohcc-gov-tls: cert files not found under ${CERT_DIR}" >&2
  exit 1
fi

# Fingerprint the new leaf cert and the one currently in the Secret (metadata
# only — never the private key) to decide whether anything actually changed.
new_fp="$(openssl x509 -in "${CERT_DIR}/fullchain.pem" -noout -fingerprint -sha256 2>/dev/null | cut -d= -f2)"
old_fp="$(kubectl get secret "${SECRET_NAME}" -n "${NAMESPACE}" \
  -o jsonpath='{.data.tls\.crt}' 2>/dev/null | base64 -d 2>/dev/null \
  | openssl x509 -noout -fingerprint -sha256 2>/dev/null | cut -d= -f2 || true)"

# Apply is declarative and idempotent; safe to run every time. This is the
# Traefik cert-sync behaviour, retained unchanged.
kubectl create secret tls "${SECRET_NAME}" \
  --cert="${CERT_DIR}/fullchain.pem" \
  --key="${CERT_DIR}/privkey.pem" \
  -n "${NAMESPACE}" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "sync-mohcc-gov-tls: secret ${NAMESPACE}/${SECRET_NAME} updated from ${CERT_DIR}"

if [[ -n "${new_fp}" && "${new_fp}" == "${old_fp}" ]]; then
  echo "sync-mohcc-gov-tls: certificate unchanged (sha256 ${new_fp}) — skipping LiveKit restart"
  exit 0
fi

if ! kubectl get deployment "${LIVEKIT_DEPLOY}" -n "${NAMESPACE}" >/dev/null 2>&1; then
  echo "sync-mohcc-gov-tls: deployment/${LIVEKIT_DEPLOY} not found in ${NAMESPACE} — secret updated, no restart needed"
  exit 0
fi

echo "sync-mohcc-gov-tls: certificate changed (was '${old_fp:-none}', now '${new_fp}') — restarting LiveKit to load the new TURN-TLS cert"
kubectl rollout restart "deployment/${LIVEKIT_DEPLOY}" -n "${NAMESPACE}"
if ! kubectl rollout status "deployment/${LIVEKIT_DEPLOY}" -n "${NAMESPACE}" --timeout="${ROLLOUT_TIMEOUT}"; then
  echo "sync-mohcc-gov-tls: ERROR — deployment/${LIVEKIT_DEPLOY} did not become healthy within ${ROLLOUT_TIMEOUT}" >&2
  exit 1
fi
echo "sync-mohcc-gov-tls: deployment/${LIVEKIT_DEPLOY} rolled out with the renewed TURN-TLS certificate"
