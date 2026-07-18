#!/usr/bin/env bash
# =============================================================================
# MANDATORY post-deploy check — the facility / geo reference estate is present.
#
# The 1,773 Master Health Facilities, their coordinates (Ndila), type-baseline
# capabilities, and derived referral pathways are durable Flyway SEED migrations
# (tuso V027/V028/V029, ndila V003) generated from the cleaned MFL CSV by
# scripts/seed/generate-facility-estate-seed.mjs. They were previously loaded at
# RUNTIME (import-facility-master-pack.sh + a Tuso->Ndila sync) and so were wiped
# by every FULL_BOOT_CLEAN_REBUILD. This check fails the boot loudly if a count is
# below threshold after deploy — i.e. the seed migrations did not apply and
# find-care is starved (facilities/coords/capabilities/pathways missing).
#
# Usage: NAMESPACE=impilo-full-preview bash scripts/full-boot/verify-facility-estate.sh
# =============================================================================
set -uo pipefail
NS="${NAMESPACE:-impilo-full-preview}"

q() { kubectl exec -n "$NS" deploy/postgres -- psql -U impilo -d "$1" -tAc "$2" 2>/dev/null | tr -d '[:space:]'; }

fail=0
check() {
  local label="$1" db="$2" sql="$3" min="$4" n
  n="$(q "$db" "$sql")"; n="${n:-0}"
  case "$n" in (*[!0-9]*) n=0 ;; esac
  if [ "$n" -ge "$min" ]; then
    printf 'OK    %-28s = %-6s (>= %s)\n' "$label" "$n" "$min"
  else
    printf 'FAIL  %-28s = %-6s (< %s)\n' "$label" "$n" "$min"
    fail=1
  fi
}

echo "=== facility / geo estate post-deploy check (ns=$NS) ==="
check "tuso facilities"          tuso  "SELECT count(*) FROM tuso.facility"                                                1700
check "tuso facility coordinates" tuso "SELECT count(*) FROM tuso.facility WHERE latitude IS NOT NULL"                     1600
check "tuso capabilities"        tuso  "SELECT count(*) FROM tuso.facility_capability"                                     15000
check "tuso referral edges"      tuso  "SELECT count(*) FROM tuso.facility_relationship WHERE relationship='REFERS_TO'"    1500
check "ndila facility locations" ndila "SELECT count(*) FROM ndila_locations WHERE owner_service='tuso-service'"           1600

if [ "$fail" -ne 0 ]; then
  echo ""
  echo "MANDATORY CHECK FAILED: the facility/geo estate is incomplete — the Flyway seed"
  echo "migrations (tuso V027/V028/V029, ndila V003) did not apply. find-care will be starved."
  echo "Regenerate/inspect: scripts/seed/generate-facility-estate-seed.mjs; confirm the migrations"
  echo "are on the tuso/ndila images and Flyway ran them (…flyway_schema_history)."
  exit 1
fi
echo "PASS: facility/geo estate present and durable."
