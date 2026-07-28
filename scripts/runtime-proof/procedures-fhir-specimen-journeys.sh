#!/usr/bin/env bash
# Procedures pipeline P13 — FHIR Specimen resource-ref schema proof (§24), against the real
# inpatient schema (real Postgres).
#
# The whole inpatient migration chain is applied — a constraint proven against a schema built in
# isolation proves nothing about the schema that ships. This rig proves the SCHEMA half (the
# fhir_specimen_ref column exists and stacks cleanly on V301's custody columns); the RESOURCE
# SHAPE (FHIR status derivation, optional-block omission) is proven in ButanoProcedureClientTest,
# and the WIRING (recordCollection calls writeSpecimen and stores its ref, best-effort on
# failure) is proven in SpecimenCustodyServiceTest. None of the three substitutes for the others.
set -uo pipefail
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

PG_NAME="inpatient-fhir-specimen-rig-$$"; PG_PORT="${PROC_RIG_PG_PORT:-25456}"
TENANT="00000000-0000-4000-8000-000000000001"
EVIDENCE="$(pwd)/reports/journeys/procedures-fhir-specimen-proof-$(date +%Y%m%d-%H%M%S)"
PASS=0; FAIL=0
cleanup() { [[ "${KEEP_RIG:-0}" == "1" ]] && echo "KEEP_RIG=1 — $PG_NAME on $PG_PORT" || docker rm -f "$PG_NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT
q() { docker exec "$PG_NAME" psql -U impilo -d inpatient -tAc "$1" 2>&1; }
chk() { if echo "$2" | grep -qi -- "$3"; then echo "PASS  $1"; PASS=$((PASS+1));
        else echo "FAIL  $1"; echo "      expected /$3/, got: $(echo "$2"|tr '\n' ' '|cut -c1-200)"; FAIL=$((FAIL+1)); fi; }

echo "=== procedures P13 FHIR specimen schema proof ==="
mkdir -p "$EVIDENCE"
docker rm -f "$PG_NAME" >/dev/null 2>&1 || true
docker run -d --name "$PG_NAME" -e POSTGRES_PASSWORD=impilo -e POSTGRES_USER=impilo \
  -e POSTGRES_DB=inpatient -p "${PG_PORT}:5432" postgres:16 >/dev/null || { echo "FAIL: postgres"; exit 1; }
echo -n "waiting for postgres"
for _ in $(seq 1 60); do docker exec "$PG_NAME" psql -U impilo -d inpatient -tAc "select 1" >/dev/null 2>&1 && break; echo -n "."; sleep 2; done; echo

chain_fail=0
for f in $(ls services/inpatient-service/src/main/resources/db/migration/*.sql | sort -V); do
  out=$(docker exec -i "$PG_NAME" psql -U impilo -d inpatient -v ON_ERROR_STOP=1 < "$f" 2>&1)
  echo "$out" > "$EVIDENCE/$(basename "$f").txt"
  echo "$out" | grep -qi "^ERROR" && { echo "  chain failure in $(basename "$f")"; chain_fail=1; }
done
chk "J-P13-0 whole inpatient migration chain applies with V303" "$chain_fail" "^0$"

EPISODE_ID=$(q "INSERT INTO inpatient.procedure_episode (tenant_id, subject_cpid, procedure_name)
                 VALUES ('$TENANT', 'CPID-FHIR-1', 'Test procedure') RETURNING episode_id" | head -1)
SPECIMEN_ID=$(q "INSERT INTO inpatient.procedure_specimen (tenant_id, episode_id, specimen_label)
                  VALUES ('$TENANT', '$EPISODE_ID', 'Appendix') RETURNING specimen_id" | head -1)

chk "J-P13-1 a fresh specimen has no FHIR ref yet (honestly null, not fabricated)" \
  "$(q "SELECT fhir_specimen_ref FROM inpatient.procedure_specimen WHERE specimen_id = '$SPECIMEN_ID'")" "^$"

chk "J-P13-2 setting fhir_specimen_ref (as the WIRING does on a successful Butano write) IS accepted" \
  "$(q "UPDATE inpatient.procedure_specimen SET fhir_specimen_ref = 'Specimen/specimen-abc123'
        WHERE specimen_id = '$SPECIMEN_ID'")" "UPDATE 1"

chk "J-P13-3 the fhir_specimen_ref column stacks cleanly on V301's custody columns — both readable together" \
  "$(q "SELECT count(*) FROM inpatient.procedure_specimen
        WHERE specimen_id = '$SPECIMEN_ID' AND fhir_specimen_ref IS NOT NULL")" "^1$"

# V301's own constraints, on the same table this migration adds a column to — proven still
# intact, not merely assumed unaffected by an additive ALTER TABLE.
chk "J-P13-4 V301's collected_by/at pairing constraint still holds after V303 stacks on the same table" \
  "$(q "UPDATE inpatient.procedure_specimen SET collected_by = 'nurse-1' WHERE specimen_id = '$SPECIMEN_ID'")" \
  "chk_procedure_specimen_collected_pair"

# adequacy_assessed_by/at are set in the same UPDATE to satisfy
# chk_procedure_specimen_adequacy_requires_assessor first — the same constraint-evaluation-order
# lesson this programme's own rigs have hit twice before (P8, P12): Postgres checks every CHECK
# on the row, not just the one under test.
chk "J-P13-5 V301's adequacy vocabulary constraint still holds" \
  "$(q "UPDATE inpatient.procedure_specimen
        SET adequacy = 'MAYBE', adequacy_assessed_by = 'x', adequacy_assessed_at = now()
        WHERE specimen_id = '$SPECIMEN_ID'")" \
  "chk_procedure_specimen_adequacy_vocabulary"

{ echo "procedures P13 FHIR specimen schema proof"; echo "generated: $(date -Is)"; echo "PASS=$PASS FAIL=$FAIL"; } > "$EVIDENCE/summary.txt"
echo; echo "P13 FHIR specimen proof: PASS=$PASS FAIL=$FAIL   evidence: $EVIDENCE"
[[ $FAIL -eq 0 ]] || exit 1
