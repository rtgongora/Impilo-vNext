#!/usr/bin/env bash
# Procedures pipeline P8 — specimen chain of custody (§13, inpatient V301) and implant
# patient-facing info + removal/revision lifecycle (§14, inventory V300), against the real
# schemas those tables live in.
#
# Two services, two containers — the whole migration chain is applied to each (40-odd for
# inpatient, 14 for inventory) rather than a schema built in isolation, the same reasoning
# procedures-site-side-journeys.sh gives: a constraint proven against an isolated schema proves
# nothing about the schema that ships.
set -uo pipefail
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

INPATIENT_PG="p8-inpatient-rig-$$"; INPATIENT_PORT="${PROC_RIG_INPATIENT_PORT:-25444}"
INVENTORY_PG="p8-inventory-rig-$$"; INVENTORY_PORT="${PROC_RIG_INVENTORY_PORT:-25445}"
TENANT="00000000-0000-4000-8000-000000000001"
EVIDENCE="reports/journeys/procedures-p8-specimen-device-proof-$(date +%Y%m%d-%H%M%S)"
PASS=0; FAIL=0
cleanup() {
  if [[ "${KEEP_RIG:-0}" == "1" ]]; then echo "KEEP_RIG=1 — $INPATIENT_PG on $INPATIENT_PORT, $INVENTORY_PG on $INVENTORY_PORT"
  else docker rm -f "$INPATIENT_PG" "$INVENTORY_PG" >/dev/null 2>&1 || true; fi
}
trap cleanup EXIT
qi() { docker exec "$INPATIENT_PG" psql -U impilo -d inpatient -tAc "$1" 2>&1; }
qv() { docker exec "$INVENTORY_PG" psql -U impilo -d inventory -tAc "$1" 2>&1; }
chk() { if echo "$2" | grep -qi -- "$3"; then echo "PASS  $1"; PASS=$((PASS+1));
        else echo "FAIL  $1"; echo "      expected /$3/, got: $(echo "$2"|tr '\n' ' '|cut -c1-200)"; FAIL=$((FAIL+1)); fi; }

echo "=== procedures P8 specimen custody + implant lifecycle proof ==="
mkdir -p "$EVIDENCE"
docker rm -f "$INPATIENT_PG" "$INVENTORY_PG" >/dev/null 2>&1 || true
docker run -d --name "$INPATIENT_PG" -e POSTGRES_PASSWORD=impilo -e POSTGRES_USER=impilo \
  -e POSTGRES_DB=inpatient -p "${INPATIENT_PORT}:5432" postgres:16 >/dev/null || { echo "FAIL: inpatient postgres"; exit 1; }
docker run -d --name "$INVENTORY_PG" -e POSTGRES_PASSWORD=impilo -e POSTGRES_USER=impilo \
  -e POSTGRES_DB=inventory -p "${INVENTORY_PORT}:5432" postgres:16 >/dev/null || { echo "FAIL: inventory postgres"; exit 1; }
echo -n "waiting for postgres (both)"
for _ in $(seq 1 60); do
  docker exec "$INPATIENT_PG" psql -U impilo -d inpatient -tAc "select 1" >/dev/null 2>&1 \
    && docker exec "$INVENTORY_PG" psql -U impilo -d inventory -tAc "select 1" >/dev/null 2>&1 && break
  echo -n "."; sleep 2
done; echo

# ── §13: inpatient — whole migration chain, then procedure_specimen custody ──
chain_fail=0
for f in $(ls services/inpatient-service/src/main/resources/db/migration/*.sql | sort -V); do
  out=$(docker exec -i "$INPATIENT_PG" psql -U impilo -d inpatient -v ON_ERROR_STOP=1 < "$f" 2>&1)
  echo "$out" > "$EVIDENCE/inpatient-$(basename "$f").txt"
  echo "$out" | grep -qi "^ERROR" && { echo "  chain failure in $(basename "$f")"; chain_fail=1; }
done
chk "J-P8-0 whole inpatient migration chain applies with V301" "$chain_fail" "^0$"

# A real episode + specimen row to hang custody assertions on.
# NOTE: psql -tAc prints the RETURNING value AND the "INSERT 0 1" command tag on separate
# lines even in tuples-only mode — `head -1` isolates the id. Every other chk() in this rig
# (and in procedures-site-side-journeys.sh) sidesteps this by matching "-" rather than
# capturing the id; this rig needs the real id to chain a specimen off an episode.
EPISODE_ID=$(qi "INSERT INTO inpatient.procedure_episode (tenant_id, subject_cpid, procedure_name)
                 VALUES ('$TENANT', 'CPID-SPEC-1', 'Appendicectomy') RETURNING episode_id" | head -1)
SPECIMEN_ID=$(qi "INSERT INTO inpatient.procedure_specimen (tenant_id, episode_id, specimen_label)
                   VALUES ('$TENANT', '$EPISODE_ID', 'Appendix') RETURNING specimen_id" | head -1)

spec_update() { qi "UPDATE inpatient.procedure_specimen SET $1 WHERE specimen_id = '$SPECIMEN_ID'"; }

chk "J-P8-1 collected_by without collected_at is REFUSED" \
  "$(spec_update "collected_by = 'nurse-1'")" "chk_procedure_specimen_collected_pair"

chk "J-P8-2 collected_at without collected_by is REFUSED" \
  "$(spec_update "collected_at = now()")" "chk_procedure_specimen_collected_pair"

chk "J-P8-3 a paired collection IS accepted" \
  "$(spec_update "collected_by = 'nurse-1', collected_at = now(), container_type = 'formalin jar', fixative = '10% formalin'")" \
  "UPDATE 1"

chk "J-P8-4 label_confirmed_by without label_confirmed_at is REFUSED (label-mismatch prevention pair)" \
  "$(spec_update "label_confirmed_by = 'nurse-1'")" "chk_procedure_specimen_label_confirmed_pair"

chk "J-P8-5 a paired label confirmation IS accepted" \
  "$(spec_update "label_confirmed_by = 'nurse-1', label_confirmed_at = now()")" "UPDATE 1"

chk "J-P8-6 received_by without received_at is REFUSED" \
  "$(spec_update "received_by = 'lab-clerk-1'")" "chk_procedure_specimen_received_pair"

# Postgres evaluates every CHECK on the row, not just the one under test — pairing the assessor
# fields in the same UPDATE satisfies chk_..._adequacy_requires_assessor so the vocabulary check
# is the one left to fail alone.
chk "J-P8-7 adequacy accepts only the closed vocabulary" \
  "$(spec_update "adequacy = 'SORT_OF', adequacy_assessed_by = 'x', adequacy_assessed_at = now()")" \
  "chk_procedure_specimen_adequacy_vocabulary"

chk "J-P8-8 an adequacy verdict other than the default requires an assessor" \
  "$(qi "UPDATE inpatient.procedure_specimen SET adequacy = 'ADEQUATE' WHERE specimen_id = '$SPECIMEN_ID'")" \
  "chk_procedure_specimen_adequacy_requires_assessor"

chk "J-P8-9 a fully paired adequacy assessment IS accepted" \
  "$(spec_update "adequacy = 'ADEQUATE', adequacy_assessed_by = 'pathologist-1', adequacy_assessed_at = now()")" \
  "UPDATE 1"

# ── §14: inventory — whole migration chain, then inv_patient_implant lifecycle ──
chain_fail=0
for f in $(ls services/inventory-service/src/main/resources/db/migration/*.sql | sort -V); do
  out=$(docker exec -i "$INVENTORY_PG" psql -U impilo -d inventory -v ON_ERROR_STOP=1 < "$f" 2>&1)
  echo "$out" > "$EVIDENCE/inventory-$(basename "$f").txt"
  echo "$out" | grep -qi "^ERROR" && { echo "  chain failure in $(basename "$f")"; chain_fail=1; }
done
chk "J-P8-10 whole inventory migration chain applies with V300" "$chain_fail" "^0$"

UNIT_ID=$(qv "INSERT INTO inv_implant_registry (tenant_id, udi, device_type) VALUES ('$TENANT', 'UDI-RIG-1', 'hip prosthesis') RETURNING implant_unit_id" | head -1)
LINK_ID=$(qv "INSERT INTO inv_patient_implant (tenant_id, implant_unit_id, patient_cpid, body_site, laterality)
              VALUES ('$TENANT', '$UNIT_ID', 'CPID-IMPLANT-1', 'hip', 'LEFT') RETURNING patient_implant_id" | head -1)

# Same evaluation-order note as J-P8-7: pairing removed_at/removed_by in the same UPDATE
# satisfies chk_..._removal_matches_status (any non-IMPLANTED status needs removed_at set) so
# the vocabulary check is the one left to fail alone.
chk "J-P8-11 an invented status is REFUSED" \
  "$(qv "UPDATE inv_patient_implant SET status = 'MISPLACED', removed_at = now(), removed_by = 'x'
         WHERE patient_implant_id = '$LINK_ID'")" \
  "chk_patient_implant_status"

# Isolating the pairing check the other direction: move status to REMOVED (satisfying
# removal_matches_status, which only cares that removed_at is set) while leaving removed_by null.
chk "J-P8-12 removed_at without removed_by is REFUSED" \
  "$(qv "UPDATE inv_patient_implant SET status = 'REMOVED', removed_at = now() WHERE patient_implant_id = '$LINK_ID'")" \
  "chk_patient_implant_removed_pair"

chk "J-P8-13 setting status=REMOVED without removed_at is REFUSED (status must match removal)" \
  "$(qv "UPDATE inv_patient_implant SET status = 'REMOVED' WHERE patient_implant_id = '$LINK_ID'")" \
  "chk_patient_implant_removal_matches_status"

chk "J-P8-14 a properly paired removal IS accepted" \
  "$(qv "UPDATE inv_patient_implant SET status = 'REMOVED', removed_at = now(), removed_by = 'surgeon-1', removal_reason = 'Infection'
         WHERE patient_implant_id = '$LINK_ID'")" "UPDATE 1"

chk "J-P8-15 status IMPLANTED with a removal already recorded is REFUSED — can't un-remove by flipping the flag" \
  "$(qv "UPDATE inv_patient_implant SET status = 'IMPLANTED' WHERE patient_implant_id = '$LINK_ID'")" \
  "chk_patient_implant_removal_matches_status"

chk "J-P8-16 revision_of_patient_implant_id pointing at a real row IS accepted" \
  "$(qv "INSERT INTO inv_patient_implant
           (tenant_id, implant_unit_id, patient_cpid, status, revision_of_patient_implant_id)
         VALUES ('$TENANT', '$UNIT_ID', 'CPID-IMPLANT-1', 'IMPLANTED', '$LINK_ID')")" \
  "INSERT 0 1"

chk "J-P8-17 revision_of_patient_implant_id pointing at a nonexistent row is REFUSED" \
  "$(qv "INSERT INTO inv_patient_implant
           (tenant_id, implant_unit_id, patient_cpid, status, revision_of_patient_implant_id)
         VALUES ('$TENANT', '$UNIT_ID', 'CPID-IMPLANT-1', 'IMPLANTED', gen_random_uuid())")" \
  "violates foreign key constraint"

chk "J-P8-18 an existing pre-P8 row (IMPLANTED, no removal columns) still satisfies the new CHECKs" \
  "$(qv "SELECT count(*) FROM inv_patient_implant WHERE status = 'IMPLANTED' AND removed_at IS NULL")" \
  "^[1-9]"

{ echo "procedures P8 specimen custody + implant lifecycle proof"; echo "generated: $(date -Is)"; echo "PASS=$PASS FAIL=$FAIL"; } > "$EVIDENCE/summary.txt"
echo
echo "P8 proof: PASS=$PASS FAIL=$FAIL   evidence: $EVIDENCE"
[[ $FAIL -eq 0 ]] || exit 1
