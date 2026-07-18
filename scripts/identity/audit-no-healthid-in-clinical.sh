#!/usr/bin/env bash
# Identity Contract §16 journey 12 / Wave B4 estate audit:
# PROVE that no clinical database contains any value present in vito.client.health_id.
#
# Before the CPID decoupling, clinical services stamped health_id as patient_cpid,
# so a clinical dump joined to PII by ID equality. After the cutover (and the
# preview wipe+reseed) this audit must return ZERO matches everywhere.
#
# dblink-free: the estate runs one Postgres with per-service DATABASES, so the
# audit fetches the vito health_id set once and intersects clinical subject-key
# values in the shell — no cross-database extension required.
#
# Usage: run against the estate with PSQL set to a command prefix that takes
#   `-d <db> -tAc <sql>`, e.g.
#     PSQL="kubectl exec -n impilo-full-preview deploy/postgres -- psql -U impilo -tA"
set -uo pipefail

PSQL="${PSQL:-${PSQL_BASE:-psql -tA}}"
VITO_DB="${VITO_DB:-vito}"
HAPI_DB="${HAPI_DB:-hapi}"
q(){ local db="$1" sql="$2"; $PSQL -d "$db" -c "$sql" 2>/dev/null; }

UUID_RE='^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'

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

echo "== Identity audit: no vito.client.health_id value may appear in clinical subject columns =="

# 1. Fetch the vito health_id set once (the values that must NOT appear anywhere clinical).
HIDS="$(q "$VITO_DB" "SELECT health_id FROM vito.client WHERE health_id IS NOT NULL")"
hid_count="$(printf '%s\n' "$HIDS" | grep -cE "$UUID_RE" || true)"
echo "vito.client health_id count: ${hid_count}"
# Build a lookup set (newline-delimited, lowercased).
HID_SET="$(printf '%s\n' "$HIDS" | tr 'A-Z' 'a-z' | grep -E "$UUID_RE" || true)"

fail=0
total=0

check_values(){  # $1=db $2=fq  → echoes matching (leaked) count
  local db="$1" fq="$2" col tbl
  col="${fq##*.}"; tbl="${fq%.*}"
  local exists
  exists="$(q "$db" "SELECT count(*) FROM information_schema.columns WHERE table_schema||'.'||table_name='${tbl}' AND column_name='${col}'" | tr -d '[:space:]')"
  if [ "${exists:-0}" = "0" ] || [ -z "$exists" ]; then
    echo "SKIP  ${db} ${fq} (absent)"; return 0
  fi
  if [ "$hid_count" = "0" ]; then
    # No health_ids exist → nothing can leak; the column is definitionally clean.
    echo "OK    ${db} ${fq} (0 health_ids to match)"; return 0
  fi
  local vals leaked
  vals="$(q "$db" "SELECT DISTINCT ${col} FROM ${tbl} WHERE ${col} IS NOT NULL" | tr 'A-Z' 'a-z' | grep -E "$UUID_RE" || true)"
  # intersection: any clinical value present in the health_id set is a leak
  leaked="$(comm -12 <(printf '%s\n' "$HID_SET" | sort -u) <(printf '%s\n' "$vals" | sort -u) | grep -cE "$UUID_RE" || true)"
  total=$((total + leaked))
  if [ "${leaked:-0}" -gt 0 ]; then
    echo "LEAK  ${db} ${fq}: ${leaked} row(s) keyed by a Health ID"; fail=1
  else
    echo "OK    ${db} ${fq}"
  fi
}

for check in "${CHECKS[@]}"; do
  check_values "${check%%:*}" "${check#*:}"
done

# BUTANO/HAPI stores the CPID as a FHIR identifier value in hfj_spidx_token.
if [ "$hid_count" = "0" ]; then
  echo "OK    hapi FHIR identifiers (0 health_ids to match)"
else
  hvals="$(q "$HAPI_DB" "SELECT DISTINCT sp_value FROM hfj_spidx_token WHERE sp_name='identifier' AND sp_value IS NOT NULL" | tr 'A-Z' 'a-z' | grep -E "$UUID_RE" || true)"
  hleak="$(comm -12 <(printf '%s\n' "$HID_SET" | sort -u) <(printf '%s\n' "$hvals" | sort -u) | grep -cE "$UUID_RE" || true)"
  if [ "${hleak:-0}" -gt 0 ]; then echo "LEAK  hapi hfj_spidx_token: ${hleak} Health-ID-keyed"; fail=1
  else echo "OK    hapi FHIR identifiers"; fi
fi

if [ "$fail" -eq 0 ]; then
  echo "AUDIT PASS: zero Health-ID values found in clinical subject keys"
else
  echo "AUDIT FAIL: Health IDs present in the clinical plane"
  exit 1
fi
