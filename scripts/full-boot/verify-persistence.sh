#!/usr/bin/env bash
# =============================================================================
# MANDATORY post-deploy check — estate data is on durable volumes.
#
# 2026-07-18: postgres ran on emptyDir and a pod restart erased every database
# (MinIO/Kafka/Keycloak/Orthanc had no volumes at all). This gate fails the boot
# loudly if any stateful workload regresses to ephemeral storage, a data PVC is
# not Bound, or the backup CronJob is missing. Companion mutating step:
# scripts/full-boot/retain-data-volumes.sh (PV reclaimPolicy=Retain).
#
# Usage: NAMESPACE=impilo-full-preview bash scripts/full-boot/verify-persistence.sh
# =============================================================================
set -uo pipefail
NS="${NAMESPACE:-impilo-full-preview}"
fail=0

# workload -> volume-name that must be PVC-backed
check_deploy_pvc() {
  local deploy="$1" volname="$2" expect_claim="$3" claim
  claim="$(kubectl -n "$NS" get deploy "$deploy" \
    -o jsonpath="{.spec.template.spec.volumes[?(@.name=='$volname')].persistentVolumeClaim.claimName}" 2>/dev/null || true)"
  if [ "$claim" = "$expect_claim" ]; then
    printf 'OK    %-10s volume %-16s -> PVC %s\n' "$deploy" "$volname" "$claim"
  else
    printf 'FAIL  %-10s volume %-16s is NOT PVC-backed (claim=%s, expected %s) — emptyDir regression?\n' \
      "$deploy" "$volname" "${claim:-none}" "$expect_claim"
    fail=1
  fi
}

check_pvc_bound() {
  local pvc="$1" phase
  phase="$(kubectl -n "$NS" get pvc "$pvc" -o jsonpath='{.status.phase}' 2>/dev/null || true)"
  if [ "$phase" = "Bound" ]; then
    printf 'OK    PVC %-18s Bound\n' "$pvc"
  else
    printf 'FAIL  PVC %-18s phase=%s (expected Bound)\n' "$pvc" "${phase:-missing}"
    fail=1
  fi
}

echo "=== estate persistence post-deploy check (ns=$NS) ==="
check_deploy_pvc postgres data            postgres-data
check_deploy_pvc keycloak data            keycloak-data
check_deploy_pvc minio    data            minio-data
check_deploy_pvc kafka    data            kafka-data
check_deploy_pvc orthanc  orthanc-storage orthanc-data

for pvc in postgres-data keycloak-data minio-data kafka-data orthanc-data postgres-backups; do
  check_pvc_bound "$pvc"
done

if kubectl -n "$NS" get cronjob postgres-backup >/dev/null 2>&1; then
  echo "OK    CronJob postgres-backup present"
else
  echo "FAIL  CronJob postgres-backup missing — no scheduled backup"
  fail=1
fi

if [ "$fail" -ne 0 ]; then
  echo ""
  echo "MANDATORY CHECK FAILED: estate data is not fully durable — a stateful workload is on"
  echo "ephemeral storage or a data PVC/backup is missing. A pod restart would lose data."
  echo "See deploy/helm/impilo-vnext/templates/{postgres,keycloak,minio,kafka,orthanc,postgres-backup}.yaml"
  exit 1
fi
echo "PASS: all stateful workloads on durable PVCs; backup scheduled."
