#!/usr/bin/env bash
# Grant impilo-backend service account permission to create users (registration).
# Keycloak realm import does not always apply service-account client roles in KC 25.
set -euo pipefail

NAMESPACE="${IMPILO_PREVIEW_NAMESPACE:-impilo-full-preview}"
REALM="${KEYCLOAK_REALM:-impilo}"
BACKEND_CLIENT="${KEYCLOAK_BACKEND_CLIENT_ID:-impilo-backend}"
KC_URL="${KEYCLOAK_URL:-http://keycloak.${NAMESPACE}.svc.cluster.local:8080}"

if ! command -v kubectl >/dev/null 2>&1; then
  echo "ERROR: kubectl required" >&2
  exit 1
fi

ADMIN_USER="$(kubectl get secret keycloak-preview-credentials -n "$NAMESPACE" -o jsonpath='{.data.KEYCLOAK_ADMIN}' 2>/dev/null | base64 -d || true)"
ADMIN_PASS="$(kubectl get secret keycloak-preview-credentials -n "$NAMESPACE" -o jsonpath='{.data.KEYCLOAK_ADMIN_PASSWORD}' 2>/dev/null | base64 -d || true)"

if [[ -z "$ADMIN_USER" || -z "$ADMIN_PASS" ]]; then
  echo "ERROR: keycloak-preview-credentials secret missing in namespace $NAMESPACE" >&2
  exit 1
fi

echo "Configuring kcadm in keycloak pod (namespace=$NAMESPACE, realm=$REALM)..."
kubectl exec -n "$NAMESPACE" deploy/keycloak -- /opt/keycloak/bin/kcadm.sh config credentials \
  --server http://localhost:8080 \
  --realm master \
  --user "$ADMIN_USER" \
  --password "$ADMIN_PASS" >/dev/null

echo "Assigning realm-management roles to service account for client $BACKEND_CLIENT..."
kubectl exec -n "$NAMESPACE" deploy/keycloak -- /opt/keycloak/bin/kcadm.sh add-roles \
  -r "$REALM" \
  --uusername "service-account-${BACKEND_CLIENT}" \
  --cclientid realm-management \
  --rolename manage-users \
  --rolename view-users \
  --rolename query-users \
  --rolename manage-realm

echo "PASS: backend service account can manage users in realm $REALM"
