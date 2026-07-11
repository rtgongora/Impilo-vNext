#!/usr/bin/env bash
# Bulk default operationalization of registry-absorbed facilities (F1).
#
# Drives tuso's governed POST /v1/internal/facilities/operationalize-defaults in
# paced batches until no candidate (zero-workspace) facility remains. Resumable
# by construction: candidacy is re-computed server-side each batch, so re-running
# after an interruption simply continues where it stopped.
#
# Pacing: tuso's RateLimitFilter 429s bursts (200 req/min). One batch = ONE
# request that provisions up to BATCH_SIZE facilities server-side, so the limit
# is nowhere near — SLEEP_SECONDS exists to keep outbox drain and PCT queue
# reconciliation comfortably ahead of the writer.
#
# Usage:
#   operationalize-facilities.sh [--province "Manicaland"] [--batch-size 100]
#                                [--sleep 5] [--dry-run] [--max-batches N]
# Env:
#   TUSO_BASE_URL (default http://localhost:8084), X_TENANT_ID (tenant UUID),
#   IMPILO_AUTH_TOKEN (optional bearer)

set -euo pipefail

TUSO_BASE="${TUSO_BASE_URL:-http://localhost:8084}"
TENANT_ID="${X_TENANT_ID:-00000000-0000-4000-8000-000000000001}" # tenant UUID — a slug parses to null and 500s audit rows
ACTOR_ID="${X_ACTOR_ID:-operator.facility-operationalization}"
POD_ID="${X_POD_ID:-national-spine}"
PURPOSE="${X_PURPOSE_OF_USE:-OPERATIONS}"
AUTH_TOKEN="${IMPILO_AUTH_TOKEN:-}"

PROVINCE=""
BATCH_SIZE=100
SLEEP_SECONDS=5
DRY_RUN=false
MAX_BATCHES=100

fail() { echo "ERROR: $*" >&2; exit 1; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --province)    PROVINCE="${2:-}"; shift 2 ;;
    --batch-size)  BATCH_SIZE="${2:-100}"; shift 2 ;;
    --sleep)       SLEEP_SECONDS="${2:-5}"; shift 2 ;;
    --dry-run)     DRY_RUN=true; shift ;;
    --max-batches) MAX_BATCHES="${2:-100}"; shift 2 ;;
    -h|--help)     grep '^#' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *)             fail "unknown argument: $1" ;;
  esac
done

command -v jq >/dev/null || fail "jq is required"

url="${TUSO_BASE}/v1/internal/facilities/operationalize-defaults"
total_processed=0
total_failed=0
batch=0

while (( batch < MAX_BATCHES )); do
  batch=$((batch + 1))
  request_id="$(cat /proc/sys/kernel/random/uuid)"
  correlation_id="$(cat /proc/sys/kernel/random/uuid)"
  payload="$(jq -n --arg province "$PROVINCE" --argjson batchSize "$BATCH_SIZE" --argjson dryRun "$DRY_RUN" \
      '{batchSize: $batchSize, dryRun: $dryRun} + (if $province == "" then {} else {province: $province} end)')"

  headers=(
    -H "Content-Type: application/json"
    -H "X-Tenant-ID: ${TENANT_ID}"
    -H "X-Pod-ID: ${POD_ID}"
    -H "X-Request-ID: ${request_id}"
    -H "X-Correlation-ID: ${correlation_id}"
    -H "X-Actor-ID: ${ACTOR_ID}"
    -H "X-Actor-Type: OPERATOR"
    -H "X-Purpose-Of-Use: ${PURPOSE}"
  )
  [[ -n "$AUTH_TOKEN" ]] && headers+=( -H "Authorization: Bearer ${AUTH_TOKEN}" )

  resp="$(curl -sS -f -X POST "${headers[@]}" -d "$payload" "$url")" \
    || fail "batch ${batch} request failed (see tuso logs, correlation ${correlation_id})"

  processed="$(jq -r '.data.processed' <<<"$resp")"
  failed="$(jq -r '.data.failed' <<<"$resp")"
  remaining="$(jq -r '.data.remaining' <<<"$resp")"
  echo "batch=${batch} processed=${processed} failed=${failed} remaining=${remaining}" \
       "workspaces=$(jq -r '.data.workspacesCreated' <<<"$resp")" \
       "servicePoints=$(jq -r '.data.servicePointsCreated' <<<"$resp")" \
       "capabilities=$(jq -r '.data.capabilitiesCreated' <<<"$resp")" \
       "readiness=$(jq -r '.data.readinessCreated' <<<"$resp")"
  failed_ids="$(jq -r '.data.failedFacilityIds | join(",")' <<<"$resp")"
  [[ -n "$failed_ids" ]] && echo "  failed facility ids: ${failed_ids}" >&2

  total_processed=$((total_processed + processed))
  total_failed=$((total_failed + failed))

  if [[ "$DRY_RUN" == true ]]; then
    echo "dry-run: ${remaining} candidate facilities; no changes made"
    exit 0
  fi
  if (( remaining <= 0 )); then
    break
  fi
  # A batch that only fails would loop forever — stop and let the operator inspect.
  if (( processed == 0 && failed > 0 )); then
    fail "batch ${batch} made no progress (${failed} failures) — aborting"
  fi
  sleep "$SLEEP_SECONDS"
done

echo "DONE: processed=${total_processed} failed=${total_failed} batches=${batch}"
(( total_failed == 0 )) || exit 2
