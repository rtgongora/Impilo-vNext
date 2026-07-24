#!/usr/bin/env bash
# Create and prove a fresh PostgreSQL backup before an existing preview estate changes.
#
# A brand-new namespace has no postgres/CronJob yet, so the first installation skips.
# Any existing postgres deployment must have the backup CronJob and a successful,
# freshly-created Job; inability to create or complete it blocks deployment.
set -euo pipefail

NS="${NAMESPACE:-impilo-full-preview}"
TIMEOUT="${FULL_BOOT_PREDEPLOY_BACKUP_TIMEOUT:-15m}"
REPORT_DIR="${REPORT_DIR:-reports/full-boot/predeploy-evidence}"
mkdir -p "$REPORT_DIR"

if ! kubectl -n "$NS" get deployment postgres >/dev/null 2>&1; then
  echo "SKIP: no existing postgres deployment in $NS (first installation)"
  exit 0
fi

if ! kubectl -n "$NS" get cronjob postgres-backup >/dev/null 2>&1; then
  echo "FAIL: existing postgres has no postgres-backup CronJob" >&2
  exit 1
fi

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
job="postgres-predeploy-${stamp,,}"
report="$REPORT_DIR/postgres-predeploy-${stamp}.txt"

{
  echo "namespace=$NS"
  echo "job=$job"
  echo "started_at=$stamp"
  kubectl -n "$NS" create job --from=cronjob/postgres-backup "$job"
  kubectl -n "$NS" wait --for=condition=complete "job/$job" --timeout="$TIMEOUT"
  succeeded="$(kubectl -n "$NS" get job "$job" -o jsonpath='{.status.succeeded}')"
  if [[ "$succeeded" != "1" ]]; then
    echo "FAIL: backup job succeeded=$succeeded (expected 1)" >&2
    exit 1
  fi
  echo "completed_at=$(date -u +%Y%m%dT%H%M%SZ)"
  echo "succeeded=$succeeded"
  echo "--- job ---"
  kubectl -n "$NS" get job "$job" -o wide
  echo "--- logs ---"
  kubectl -n "$NS" logs "job/$job" --all-containers
} 2>&1 | tee "$report"

if ! grep -q '^succeeded=1$' "$report"; then
  echo "FAIL: backup evidence did not record succeeded=1: $report" >&2
  exit 1
fi

echo "PASS: fresh pre-deploy PostgreSQL backup completed"
echo "evidence=$report"
