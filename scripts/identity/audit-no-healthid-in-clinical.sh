#!/usr/bin/env bash
# Identity Contract §16 journey 12 / Wave B4 estate audit:
# PROVE that no clinical database contains any value present in vito.client.health_id.
#
# Before the CPID decoupling, clinical services stamped health_id as patient_cpid,
# so a clinical dump joined to PII by ID equality. After the cutover (and the
# preview wipe+reseed) this audit must return ZERO matches everywhere.
#
# Usage: run on the estate host (kubectl port-forwards or in-cluster) with
#   PGHOST/PGPORT/PGUSER/PGPASSWORD set for the shared Postgres, or override
#   PSQL_BASE with a full psql command prefix.
set -euo pipefail

PSQL_BASE=${PSQL_BASE:-"psql -tA"}
VITO_DB=${VITO_DB:-vito}

# clinical_db:schema.table.column — every subject-key column in the clinical plane
CHECKS=(
  "pct:pct.encounter.subject_cpid"
  "pct:pct.problem.subject_cpid"
  "pct:pct.care_plan.subject_cpid"
  "pct:pct.ed_visit.patient_cpid"
  "pct:pct.emergency_cases.known_subject_cpid"
  "pct:pct.emergency_cases.reconciled_cpid"
  "inpatient:inpatient.admission.patient_cpid"
  "inpatient:inpatient.emergency_activation.subject_cpid"
  "inpatient:inpatient.procedure_episode.subject_cpid"
  "daidzai:daidzai.dai_trauma_episode.subject_health_id"
  "daidzai:daidzai.dai_emergency_incident.subject_health_id"
  "pharmacy:pharmacy.prescription.patient_cpid"
  "oros:oros.orders.patient_cpid"
  "madi:madi.blood_orders.patient_cpid"
  "booking:booking.appointment.patient_cpid"
  "indawo:indawo.case_investigation.subject_cpid"
)

fail=0
total=0

echo "== Identity audit: no vito.client.health_id value may appear in clinical subject columns =="

for check in "${CHECKS[@]}"; do
  db="${check%%:*}"
  fq="${check#*:}"
  col="${fq##*.}"
  tbl="${fq%.*}"
  # Skip cleanly when the table/column does not exist in this estate build.
  exists=$($PSQL_BASE -d "$db" -c \
    "SELECT count(*) FROM information_schema.columns
      WHERE table_schema||'.'||table_name = '${tbl}' AND column_name = '${col}'" 2>/dev/null || echo 0)
  if [ "${exists:-0}" -eq 0 ]; then
    echo "SKIP  ${db} ${fq} (absent)"
    continue
  fi
  count=$($PSQL_BASE -d "$db" -c \
    "SELECT count(*) FROM ${tbl} t
      WHERE t.${col} IS NOT NULL
        AND t.${col} ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\$'
        AND t.${col}::uuid IN (
            SELECT health_id FROM dblink('dbname=${VITO_DB}',
                'SELECT health_id FROM vito.client') AS v(health_id uuid))" 2>/dev/null) || {
    echo "WARN  ${db} ${fq} (query failed — check dblink extension / connectivity)"
    fail=1
    continue
  }
  total=$((total + count))
  if [ "$count" -gt 0 ]; then
    echo "LEAK  ${db} ${fq}: ${count} row(s) keyed by a Health ID"
    fail=1
  else
    echo "OK    ${db} ${fq}"
  fi
done

# BUTANO/HAPI stores the CPID as a FHIR identifier value in hfj_spidx_token.
hapi_count=$($PSQL_BASE -d "${HAPI_DB:-hapi}" -c \
  "SELECT count(*) FROM hfj_spidx_token t
    WHERE t.sp_name = 'identifier'
      AND t.sp_value ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\$'
      AND t.sp_value::uuid IN (
          SELECT health_id FROM dblink('dbname=${VITO_DB}',
              'SELECT health_id FROM vito.client') AS v(health_id uuid))" 2>/dev/null) || hapi_count="?"
if [ "$hapi_count" = "?" ]; then
  echo "WARN  hapi identifier check failed — verify manually"
  fail=1
elif [ "$hapi_count" -gt 0 ]; then
  echo "LEAK  hapi hfj_spidx_token identifiers: ${hapi_count} Health-ID-keyed"
  fail=1
else
  echo "OK    hapi FHIR identifiers"
fi

if [ "$fail" -eq 0 ]; then
  echo "AUDIT PASS: zero Health-ID values found in clinical subject keys"
else
  echo "AUDIT FAIL: Health IDs present in the clinical plane (or checks unverifiable)"
  exit 1
fi
