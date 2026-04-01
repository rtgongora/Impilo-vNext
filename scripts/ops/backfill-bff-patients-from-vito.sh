#!/usr/bin/env bash
# =============================================================================
# backfill-bff-patients-from-vito.sh
#
# One-time backfill: copies VITO clients that are missing from the BFF
# patients table.  Safe to re-run — uses ON CONFLICT DO NOTHING.
#
# Root cause this fixes:
#   IdentityService.register() was emitting IDENTITY_CREATED events without
#   tenant_id, so PatientEventConsumer silently skipped them.  That bug is
#   now fixed; this script recovers the clients already in VITO.
#
# Usage:
#   ./scripts/ops/backfill-bff-patients-from-vito.sh
#   ./scripts/ops/backfill-bff-patients-from-vito.sh --dry-run
#
# Requires the postgres container to be running (docker-compose runtime).
# =============================================================================

set -euo pipefail

POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-impilo-postgres-1}"
POSTGRES_USER="${POSTGRES_USER:-impilo}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-changeme}"
DRY_RUN=false

for arg in "$@"; do
  [[ "$arg" == "--dry-run" ]] && DRY_RUN=true
done

psql_vito() {
  docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" "$POSTGRES_CONTAINER" \
    psql -U "$POSTGRES_USER" -d vito -t -A "$@"
}

psql_bff() {
  docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" "$POSTGRES_CONTAINER" \
    psql -U "$POSTGRES_USER" -d experience_bff "$@"
}

echo "=== VITO → BFF patient backfill ==="
echo "    Postgres container : $POSTGRES_CONTAINER"
echo "    Dry-run            : $DRY_RUN"
echo ""

MISSING=$(psql_vito -c "
  SELECT
    c.health_id::text,
    c.tenant_id::text,
    COALESCE(c.given_name,  'Unknown') AS given_name,
    COALESCE(c.family_name, 'Unknown') AS family_name,
    c.date_of_birth,
    c.sex,
    COALESCE(c.status, 'PROVISIONAL') AS status
  FROM vito.client c
  WHERE NOT EXISTS (
    SELECT 1 FROM dblink(
      'dbname=experience_bff user=$POSTGRES_USER password=$POSTGRES_PASSWORD',
      'SELECT cpid FROM patients'
    ) AS bff(cpid text)
    WHERE bff.cpid = c.health_id::text
  )
  ORDER BY c.created_at;
" 2>/dev/null || true)

if [[ -z "$MISSING" ]]; then
  echo "No missing clients detected — checking via two-step approach..."
  MISSING=""
fi

BFF_CPIDS=$(psql_bff -t -A -c "SELECT cpid FROM patients;" 2>/dev/null || echo "")

VITO_ROWS=$(psql_vito -c "
  SELECT
    c.health_id::text     AS health_id,
    c.tenant_id::text     AS tenant_id,
    COALESCE(c.given_name,  'Unknown') AS given_name,
    COALESCE(c.family_name, 'Unknown') AS family_name,
    c.date_of_birth::text AS date_of_birth,
    COALESCE(c.sex, '')   AS sex,
    COALESCE(c.status, 'PROVISIONAL') AS status
  FROM vito.client c
  ORDER BY c.created_at;
")

MISSING_SQL=""
MISSING_COUNT=0

while IFS='|' read -r health_id tenant_id given_name family_name date_of_birth sex status; do
  [[ -z "$health_id" ]] && continue

  if echo "$BFF_CPIDS" | grep -qF "$health_id"; then
    continue
  fi

  MISSING_COUNT=$((MISSING_COUNT + 1))
  echo "  → Missing: health_id=$health_id tenant=$tenant_id name='$given_name $family_name' status=$status"

  DOB_SQL="NULL"
  if [[ -n "$date_of_birth" && "$date_of_birth" != "" ]]; then
    DOB_SQL="'${date_of_birth}'::date"
  fi

  SEX_SQL="'${sex}'"
  [[ -z "$sex" ]] && SEX_SQL="NULL"

  MISSING_SQL+="
INSERT INTO patients (id, tenant_id, cpid, given_name, family_name, date_of_birth, sex, status, created_at, updated_at)
VALUES (
  gen_random_uuid(),
  '${tenant_id}',
  '${health_id}',
  '${given_name//\'/\'\'}',
  '${family_name//\'/\'\'}',
  ${DOB_SQL},
  ${SEX_SQL},
  '${status}',
  NOW(),
  NOW()
)
ON CONFLICT (tenant_id, cpid) DO NOTHING;"

done <<< "$VITO_ROWS"

echo ""
echo "Missing clients found: $MISSING_COUNT"

if [[ $MISSING_COUNT -eq 0 ]]; then
  echo "Nothing to backfill — BFF is already in sync with VITO."
  exit 0
fi

if $DRY_RUN; then
  echo ""
  echo "=== DRY-RUN SQL (not executed) ==="
  echo "$MISSING_SQL"
  exit 0
fi

echo ""
echo "Inserting $MISSING_COUNT clients into experience_bff.patients ..."
psql_bff -c "$MISSING_SQL"
echo "=== Backfill complete ==="
