#!/usr/bin/env bash
set -Eeuo pipefail

# Guarded Keycloak 25 H2 -> PostgreSQL preparation. This script deliberately stops
# before the 26.7 Helm rollout. It never deletes the legacy PVC or namespace.

PHASE="${1:-preflight}"
NAMESPACE="${NAMESPACE:-impilo-full-preview}"
EXPECTED_USERS="${EXPECTED_USERS:-42}"
BACKUP_PVC="${KEYCLOAK_BACKUP_PVC:-keycloak-migration-backup}"
LEGACY_PVC="${KEYCLOAK_LEGACY_PVC:-keycloak-data}"
KC25_IMAGE="${KEYCLOAK_25_IMAGE:-quay.io/keycloak/keycloak:25.0}"
STAMP="${MIGRATION_STAMP:-$(date -u +%Y%m%dT%H%M%SZ)}"
ARTIFACT_ROOT="${KEYCLOAK_MIGRATION_ARTIFACT_ROOT:-$HOME/impilo-backups/keycloak}"

command -v kubectl >/dev/null || { echo "kubectl is required" >&2; exit 69; }

preflight() {
  kubectl -n "$NAMESPACE" get pvc "$LEGACY_PVC" >/dev/null
  kubectl -n "$NAMESPACE" get secret impilo-app-secrets >/dev/null
  kubectl -n "$NAMESPACE" get secret postgres-credentials >/dev/null
  users="$(kubectl -n "$NAMESPACE" exec deploy/keycloak -- sh -ec '
    /opt/keycloak/bin/kcadm.sh config credentials --server http://127.0.0.1:8080 --realm master \
      --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null 2>&1 ||
    /opt/keycloak/bin/kcadm.sh config credentials --server http://127.0.0.1:8080 --realm master \
      --user "$KEYCLOAK_ADMIN" --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null 2>&1
    /opt/keycloak/bin/kcadm.sh get users/count -r impilo --format csv --noquotes
  ' | tr -d '\r')"
  [[ "$users" == "$EXPECTED_USERS" ]] || { echo "expected $EXPECTED_USERS users, found $users" >&2; exit 65; }
  echo "PRECHECK_OK namespace=$NAMESPACE users=$users legacy_pvc=$LEGACY_PVC"
}

case "$PHASE" in
  preflight)
    preflight
    ;;
  prepare)
    [[ "${MFA_MIGRATION_ACK:-}" == "PRESERVE_${EXPECTED_USERS}_USERS" ]] || {
      echo "set MFA_MIGRATION_ACK=PRESERVE_${EXPECTED_USERS}_USERS" >&2; exit 64;
    }
    preflight
    kubectl -n "$NAMESPACE" get pvc "$BACKUP_PVC" >/dev/null 2>&1 || kubectl -n "$NAMESPACE" create -f - <<YAML
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: $BACKUP_PVC
  annotations:
    helm.sh/resource-policy: keep
spec:
  accessModes: [ReadWriteOnce]
  resources:
    requests:
      storage: 5Gi
YAML
    original_replicas="$(kubectl -n "$NAMESPACE" get deploy keycloak -o jsonpath='{.spec.replicas}')"
    restore_keycloak() {
      if [[ -n "${reader:-}" ]]; then
        kubectl -n "$NAMESPACE" delete pod "$reader" --ignore-not-found --wait=false >/dev/null
      fi
      kubectl -n "$NAMESPACE" scale deploy/keycloak --replicas="$original_replicas" >/dev/null
      kubectl -n "$NAMESPACE" rollout status deploy/keycloak --timeout=300s >/dev/null
      echo "KEYCLOAK_RESTORED replicas=$original_replicas"
    }
    trap restore_keycloak EXIT
    kubectl -n "$NAMESPACE" scale deploy/keycloak --replicas=0
    kubectl -n "$NAMESPACE" wait --for=delete pod -l app=keycloak --timeout=180s || true
    kubectl -n "$NAMESPACE" delete job "keycloak-h2-snapshot-$STAMP" --ignore-not-found
    kubectl -n "$NAMESPACE" create -f - <<YAML
apiVersion: batch/v1
kind: Job
metadata:
  name: keycloak-h2-snapshot-$STAMP
spec:
  backoffLimit: 0
  template:
    spec:
      restartPolicy: Never
      containers:
        - name: snapshot
          image: busybox:1.36
          command: ["sh", "-ec"]
          args:
            - mkdir -p /backup/h2-$STAMP; cp -a /legacy/. /backup/h2-$STAMP/; find /backup/h2-$STAMP -type f -exec sha256sum {} + | sort > /backup/h2-$STAMP.sha256
          volumeMounts:
            - {name: legacy, mountPath: /legacy, readOnly: true}
            - {name: backup, mountPath: /backup}
      volumes:
        - name: legacy
          persistentVolumeClaim: {claimName: $LEGACY_PVC}
        - name: backup
          persistentVolumeClaim: {claimName: $BACKUP_PVC}
YAML
    kubectl -n "$NAMESPACE" wait --for=condition=complete "job/keycloak-h2-snapshot-$STAMP" --timeout=600s
    kubectl -n "$NAMESPACE" delete job "keycloak-h2-export-$STAMP" --ignore-not-found
    kubectl -n "$NAMESPACE" create -f - <<YAML
apiVersion: batch/v1
kind: Job
metadata:
  name: keycloak-h2-export-$STAMP
spec:
  backoffLimit: 0
  template:
    spec:
      restartPolicy: Never
      containers:
        - name: export
          image: $KC25_IMAGE
          args: ["export", "--dir", "/backup/export-$STAMP", "--realm", "impilo", "--users", "realm_file"]
          volumeMounts:
            - {name: backup, mountPath: /opt/keycloak/data, subPath: h2-$STAMP}
            - {name: backup, mountPath: /backup}
      volumes:
        - name: backup
          persistentVolumeClaim: {claimName: $BACKUP_PVC}
YAML
    kubectl -n "$NAMESPACE" wait --for=condition=complete "job/keycloak-h2-export-$STAMP" --timeout=900s
    kubectl -n "$NAMESPACE" logs "job/keycloak-h2-export-$STAMP"
    reader="keycloak-backup-reader-$STAMP"
    kubectl -n "$NAMESPACE" delete pod "$reader" --ignore-not-found
    kubectl -n "$NAMESPACE" create -f - <<YAML
apiVersion: v1
kind: Pod
metadata:
  name: $reader
spec:
  restartPolicy: Never
  containers:
    - name: reader
      image: busybox:1.36
      command: ["sh", "-c", "sleep 600"]
      volumeMounts:
        - {name: backup, mountPath: /backup, readOnly: true}
  volumes:
    - name: backup
      persistentVolumeClaim: {claimName: $BACKUP_PVC}
YAML
    kubectl -n "$NAMESPACE" wait --for=condition=Ready "pod/$reader" --timeout=120s
    mkdir -p "$ARTIFACT_ROOT"
    [[ ! -e "$ARTIFACT_ROOT/export-$STAMP" ]] || {
      echo "artifact path already exists: $ARTIFACT_ROOT/export-$STAMP" >&2; exit 65;
    }
    kubectl -n "$NAMESPACE" cp "$reader:/backup/export-$STAMP" "$ARTIFACT_ROOT/export-$STAMP"
    kubectl -n "$NAMESPACE" delete pod "$reader" --wait=true
    reader=""
    test -n "$(find "$ARTIFACT_ROOT/export-$STAMP" -type f -name '*.json' -print -quit)" || {
      echo "export contains no realm JSON" >&2; exit 70;
    }
    restore_keycloak
    trap - EXIT
    actual_users="$(jq -s '[.[].users[]? | select(.serviceAccountClientId == null)] | length' "$ARTIFACT_ROOT/export-$STAMP"/*.json)"
    [[ "$actual_users" == "$EXPECTED_USERS" ]] || {
      echo "export expected $EXPECTED_USERS users, found $actual_users" >&2; exit 70;
    }
    echo "H2_EXPORT_OK stamp=$STAMP backup_pvc=$BACKUP_PVC artifact=$ARTIFACT_ROOT/export-$STAMP users=$actual_users"
    echo "Next: scripts/operator/keycloak-migration-rehearsal.sh $ARTIFACT_ROOT/export-$STAMP"
    ;;
  collect)
    command -v jq >/dev/null || { echo "jq is required" >&2; exit 69; }
    kubectl -n "$NAMESPACE" get pvc "$BACKUP_PVC" >/dev/null
    reader="keycloak-backup-reader-$STAMP"
    trap 'kubectl -n "$NAMESPACE" delete pod "$reader" --ignore-not-found --wait=false >/dev/null' EXIT
    kubectl -n "$NAMESPACE" delete pod "$reader" --ignore-not-found
    kubectl -n "$NAMESPACE" create -f - <<YAML
apiVersion: v1
kind: Pod
metadata:
  name: $reader
spec:
  restartPolicy: Never
  containers:
    - name: reader
      image: busybox:1.36
      command: ["sh", "-c", "sleep 600"]
      volumeMounts:
        - {name: backup, mountPath: /backup, readOnly: true}
  volumes:
    - name: backup
      persistentVolumeClaim: {claimName: $BACKUP_PVC}
YAML
    kubectl -n "$NAMESPACE" wait --for=condition=Ready "pod/$reader" --timeout=120s
    mkdir -p "$ARTIFACT_ROOT"
    [[ ! -e "$ARTIFACT_ROOT/export-$STAMP" ]] || {
      echo "artifact path already exists: $ARTIFACT_ROOT/export-$STAMP" >&2; exit 65;
    }
    kubectl -n "$NAMESPACE" cp "$reader:/backup/export-$STAMP" "$ARTIFACT_ROOT/export-$STAMP"
    actual_users="$(jq -s '[.[].users[]? | select(.serviceAccountClientId == null)] | length' "$ARTIFACT_ROOT/export-$STAMP"/*.json)"
    [[ "$actual_users" == "$EXPECTED_USERS" ]] || {
      echo "export expected $EXPECTED_USERS users, found $actual_users" >&2; exit 70;
    }
    kubectl -n "$NAMESPACE" delete pod "$reader" --wait=true
    trap - EXIT
    echo "H2_EXPORT_COLLECTED stamp=$STAMP artifact=$ARTIFACT_ROOT/export-$STAMP users=$actual_users"
    ;;
  status)
    kubectl -n "$NAMESPACE" get pvc "$LEGACY_PVC" "$BACKUP_PVC"
    kubectl -n "$NAMESPACE" get jobs -l job-name --sort-by=.metadata.creationTimestamp 2>/dev/null || true
    kubectl -n "$NAMESPACE" get deploy keycloak -o custom-columns=NAME:.metadata.name,REPLICAS:.spec.replicas,READY:.status.readyReplicas,IMAGE:.spec.template.spec.containers[0].image
    ;;
  *)
    echo "usage: $0 preflight|prepare|collect|status" >&2
    exit 64
    ;;
esac
