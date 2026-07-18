#!/usr/bin/env bash
# =============================================================================
# Patch the PersistentVolumes behind the estate's data PVCs to reclaimPolicy=Retain.
#
# k3s local-path provisions PVs with reclaimPolicy=Delete: deleting a PVC (or the
# namespace) would also erase the data on disk. Retain means even a PVC delete
# leaves the directory under /var/lib/rancher/k3s/storage for manual recovery.
# Idempotent; run from the full-boot deploy tail after the helm upgrade.
#
# Usage: NAMESPACE=impilo-full-preview bash scripts/full-boot/retain-data-volumes.sh
# =============================================================================
set -uo pipefail
NS="${NAMESPACE:-impilo-full-preview}"
PVCS="postgres-data keycloak-data minio-data kafka-data orthanc-data postgres-backups ndila-martin-data"

rc=0
for pvc in $PVCS; do
  pv="$(kubectl -n "$NS" get pvc "$pvc" -o jsonpath='{.spec.volumeName}' 2>/dev/null || true)"
  if [ -z "$pv" ]; then
    echo "SKIP  $pvc (not found / not bound yet)"
    continue
  fi
  cur="$(kubectl get pv "$pv" -o jsonpath='{.spec.persistentVolumeReclaimPolicy}' 2>/dev/null || true)"
  if [ "$cur" = "Retain" ]; then
    echo "OK    $pvc -> $pv already Retain"
    continue
  fi
  if kubectl patch pv "$pv" -p '{"spec":{"persistentVolumeReclaimPolicy":"Retain"}}' >/dev/null 2>&1; then
    echo "OK    $pvc -> $pv patched Delete->Retain"
  else
    echo "FAIL  $pvc -> $pv could not patch reclaim policy"
    rc=1
  fi
done
exit "$rc"
