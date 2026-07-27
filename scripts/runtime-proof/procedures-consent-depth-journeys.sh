#!/usr/bin/env bash
# Procedures pipeline P5 — informed consent is the record of a conversation (§6).
# Whole mvumo migration chain against real Postgres.
set -uo pipefail
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"
PG_NAME="mvumo-consent-rig-$$"; PG_PORT="${PROC_RIG_PG_PORT:-25444}"
T="00000000-0000-4000-8000-000000000001"
EVIDENCE="reports/journeys/procedures-consent-depth-proof-$(date +%Y%m%d-%H%M%S)"
PASS=0; FAIL=0
cleanup() { [[ "${KEEP_RIG:-0}" == "1" ]] && echo "KEEP_RIG=1" || docker rm -f "$PG_NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT
q() { docker exec "$PG_NAME" psql -U impilo -d mvumo -tAc "$1" 2>&1; }
chk() { if echo "$2" | grep -qi -- "$3"; then echo "PASS  $1"; PASS=$((PASS+1));
        else echo "FAIL  $1"; echo "      expected /$3/, got: $(echo "$2"|tr '\n' ' '|cut -c1-180)"; FAIL=$((FAIL+1)); fi; }

echo "=== procedures P5 consent-depth proof ==="; mkdir -p "$EVIDENCE"
docker rm -f "$PG_NAME" >/dev/null 2>&1 || true
docker run -d --name "$PG_NAME" -e POSTGRES_PASSWORD=impilo -e POSTGRES_USER=impilo \
  -e POSTGRES_DB=mvumo -p "${PG_PORT}:5432" postgres:16 >/dev/null || { echo "FAIL: postgres"; exit 1; }
echo -n "waiting"; for _ in $(seq 1 60); do q "select 1" >/dev/null 2>&1 && break; echo -n "."; sleep 2; done; echo

cf=0
for f in $(ls services/mvumo-service/src/main/resources/db/migration/*.sql | sort -V); do
  out=$(docker exec -i "$PG_NAME" psql -U impilo -d mvumo -v ON_ERROR_STOP=1 < "$f" 2>&1)
  echo "$out" > "$EVIDENCE/$(basename "$f").txt"
  echo "$out" | grep -qi "^ERROR" && { echo "  chain failure in $(basename "$f")"; cf=1; }
done
chk "J-P5-0 whole mvumo chain applies with V300" "$cf" "^0$"

ins() { q "INSERT INTO mvumo.consent_request (tenant_id, state, subject_patient_ref, consent_type, required_assurance $2)
           VALUES ('$T','GRANTED','CPID-$1','PROCEDURE','L2' $3)"; }

chk "J-P5-1 a bare grant is still accepted — history is not retrofitted" "$(ins 1 '' '')" "INSERT 0 1"

chk "J-P5-2 a guardian consent with no recorded basis is REFUSED" \
  "$(ins 2 ', decision_maker_type, decision_maker_ref' ", 'GUARDIAN', 'rel-1'")" "chk_consent_substitute_needs_basis"
chk "J-P5-3 a guardian consent WITH a basis is accepted" \
  "$(ins 3 ', decision_maker_type, decision_maker_ref, decision_maker_basis' ", 'GUARDIAN', 'rel-1', 'Mother, parental responsibility'")" "INSERT 0 1"
chk "J-P5-4 an emergency exception needs no substitute basis — it is its own basis" \
  "$(ins 4 ', decision_maker_type' ", 'EMERGENCY_EXCEPTION'")" "INSERT 0 1"

chk "J-P5-5 an interpreter nobody can identify is REFUSED" \
  "$(ins 5 ', interpreter_used' ", true")" "chk_consent_interpreter_identified"
chk "J-P5-6 an identified interpreter is accepted" \
  "$(ins 6 ', interpreter_used, interpreter_identity' ", true, 'S. Moyo, Shona'")" "INSERT 0 1"

chk "J-P5-7 a withdrawal timestamp with no actor is REFUSED" \
  "$(ins 7 ', withdrawn_at' ", now()")" "chk_consent_withdrawal_pair"
chk "J-P5-8 an invented capacity outcome is REFUSED" \
  "$(ins 8 ', capacity_outcome' ", 'PROBABLY_FINE'")" "chk_consent_capacity_outcome"
chk "J-P5-9 a refused child assent is representable alongside a granted guardian consent" \
  "$(ins 9 ', decision_maker_type, decision_maker_ref, decision_maker_basis, assent_sought, assent_outcome' ", 'GUARDIAN','rel-2','Father, parental responsibility', true, 'REFUSED'")" "INSERT 0 1"

# The §6 invariant: a signature is not informed consent, and the view says which is which.
chk "J-P5-10 a grant with proof but no discussion is classified SIGNATURE_ONLY" \
  "$(q "INSERT INTO mvumo.consent_request (tenant_id,state,subject_patient_ref,consent_type,required_assurance,proof_ref)
        VALUES ('$T','GRANTED','CPID-10','PROCEDURE','L2','s3://sig.png');
        SELECT consent_depth FROM mvumo.v_consent_completeness WHERE tenant_id='$T' AND id=(
          SELECT id FROM mvumo.consent_request WHERE subject_patient_ref='CPID-10')")" "SIGNATURE_ONLY"

chk "J-P5-11 a fully recorded conversation is classified INFORMED" \
  "$(q "INSERT INTO mvumo.consent_request (tenant_id,state,subject_patient_ref,consent_type,required_assurance,
          purpose_explained,benefits_explained,material_risks_explained,alternatives_explained,
          consequences_of_declining,obtained_by_ref,obtained_at,language_code)
        VALUES ('$T','GRANTED','CPID-11','PROCEDURE','L2','Remove the gallbladder','Resolve the pain',
                'Bleeding, infection, bile duct injury','Medical management, watchful waiting',
                'Recurrent attacks, risk of perforation','dr-b',now(),'sn');
        SELECT consent_depth FROM mvumo.v_consent_completeness WHERE subject_patient_ref='CPID-11'")" "INFORMED"

chk "J-P5-12 a bare grant with no proof at all is INCOMPLETE, not INFORMED" \
  "$(q "SELECT consent_depth FROM mvumo.v_consent_completeness WHERE subject_patient_ref='CPID-1'")" "INCOMPLETE"

# Completeness is computed, never stored — a stored flag drifts from the columns it summarises.
chk "J-P5-13 completeness is a view, not a stored column" \
  "$(q "SELECT count(*) FROM information_schema.columns WHERE table_name='consent_request'
        AND column_name IN ('is_complete','consent_depth','completeness')")" "^0$"

{ echo "P5 consent-depth proof"; echo "generated: $(date -Is)"; echo "PASS=$PASS FAIL=$FAIL"; } > "$EVIDENCE/summary.txt"
echo; echo "P5 consent-depth proof: PASS=$PASS FAIL=$FAIL   evidence: $EVIDENCE"
[[ $FAIL -eq 0 ]] || exit 1
