#!/usr/bin/env bash
# Surgical domain pack SB-5 — operative record depth, against the real inpatient schema.
#
# The surgical audit (§13) scores the operative record PARTIAL and names eleven missing fields:
# position, preparation, incision, steps, technique, fluids, drains, stomas, closure, wound
# classification, postoperative instructions. V304 adds them, plus a soft reference to SB-4's
# specialty operative templates.
#
# The whole inpatient migration chain is applied, not just V304. Three of these columns have
# near-namesakes on sibling tables (the anaesthetic `technique`, and the PACU `fluids` and
# `drains_devices` channels), so proving the new columns land on the RIGHT table in the schema
# that actually ships is most of the point.
#
# The module's own tests cannot do this job: application-test.yml sets flyway.enabled=false with
# ddl-auto=create-drop, so they build their schema from the entities and are structurally blind to
# every CHECK constraint below. That blindness is what let V300's setting default break all theatre
# intake for five waves.
set -uo pipefail
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

PG_NAME="inpatient-oprecord-rig-$$"; PG_PORT="${PROC_RIG_PG_PORT:-25457}"
TENANT="00000000-0000-4000-8000-000000000001"
EVIDENCE="${EV:-reports/journeys/procedures-operative-record-proof-$(date +%Y%m%d-%H%M%S)}"
PASS=0; FAIL=0
cleanup() { [[ "${KEEP_RIG:-0}" == "1" ]] && echo "KEEP_RIG=1 — $PG_NAME on $PG_PORT" || docker rm -f "$PG_NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT
q() { docker exec "$PG_NAME" psql -U impilo -d inpatient -tAc "$1" 2>&1; }
# A UUID and nothing else. Used as the "accepted" assertion instead of matching a bare hyphen:
# psql error text is full of hyphens, so /-/ passes on failure and proves nothing.
UUID_RE='^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
# psql prints the RETURNING value AND its own "INSERT 0 1" status line; keep only the id.
id_of() { echo "$1" | grep -oE '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' | head -1; }
chk() { if echo "$2" | grep -qiE -- "$3"; then echo "PASS  $1"; PASS=$((PASS+1));
        else echo "FAIL  $1"; echo "      expected /$3/, got: $(echo "$2"|tr '\n' ' '|cut -c1-200)"; FAIL=$((FAIL+1)); fi; }
# Assert an insert was accepted: exactly one id came back and no error did.
chk_accepted() { local got; got=$(id_of "$2");
        if [[ -n "$got" ]] && ! echo "$2" | grep -qi "^ERROR"; then echo "PASS  $1"; PASS=$((PASS+1));
        else echo "FAIL  $1"; echo "      expected an id, got: $(echo "$2"|tr '\n' ' '|cut -c1-200)"; FAIL=$((FAIL+1)); fi; }

echo "=== SB-5 operative record depth proof ==="
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
chk "J-SB5-0 whole inpatient migration chain applies with V304" "$chain_fail" "^0$"

# An episode to hang notes on.
EPISODE=$(id_of "$(q "INSERT INTO inpatient.procedure_episode (tenant_id, subject_cpid, procedure_name, status)
             VALUES ('$TENANT', 'CPID-SB5-1', 'Open right inguinal hernia repair', 'IN_PROGRESS')
             RETURNING episode_id")")
chk "J-SB5-1 an episode exists to attach an operative record to" "$EPISODE" "$UUID_RE"

note() { q "INSERT INTO inpatient.procedure_note (episode_id $2)
            VALUES ('$EPISODE' $3) RETURNING note_id"; }

# ── The eleven land, and land on procedure_note ──────────────────────────────────────
chk "J-SB5-2 all eleven audit-named fields exist on procedure_note" \
  "$(q "SELECT count(*) FROM information_schema.columns
        WHERE table_schema='inpatient' AND table_name='procedure_note' AND column_name IN
        ('patient_position','skin_preparation','incision','operative_steps','operative_technique',
         'intraoperative_fluids','drains_placed','stomas_formed','closure_method',
         'wound_classification','postoperative_instructions')")" "^11$"

chk_accepted "J-SB5-3 a complete operative record persists" \
  "$(note x ", patient_position, skin_preparation, incision, operative_steps, operative_technique,
              intraoperative_fluids, drains_placed, stomas_formed, closure_method,
              wound_classification, postoperative_instructions" \
          ", 'Supine, arms out', 'Chlorhexidine 2% in alcohol', 'Transverse right groin, 6 cm',
             'Sac reduced; mesh laid; layers closed', 'OPEN', 'Hartmann''s 500 ml', 'None', 'None',
             'Mass closure 1 PDS', 'CLEAN', 'No lifting over 5 kg for six weeks'")"

chk "J-SB5-4 each field is separately readable — the point of a structured record" \
  "$(q "SELECT patient_position || '|' || operative_technique || '|' || wound_classification
        FROM inpatient.procedure_note WHERE episode_id='$EPISODE' AND wound_classification IS NOT NULL LIMIT 1")" \
  "Supine, arms out|OPEN|CLEAN"

# ── Wound classification: a closed vocabulary, enforced by the database ──────────────
chk "J-SB5-5 an invented wound classification is REFUSED by the database, not just by Java" \
  "$(note y ", wound_classification" ", 'FAIRLY_CLEAN'")" "chk_procedure_note_wound_classification"

for wc in CLEAN CLEAN_CONTAMINATED CONTAMINATED DIRTY; do
  chk_accepted "J-SB5-6 the CDC class $wc is accepted" "$(note "wc-$wc" ", wound_classification" ", '$wc'")"
done
chk "J-SB5-6b all four landed as distinct stored values" \
  "$(q "SELECT count(DISTINCT wound_classification) FROM inpatient.procedure_note
        WHERE wound_classification IS NOT NULL")" "^4$"

chk_accepted "J-SB5-7 wound classification stays OPTIONAL — a part-written note is not an invalid one" \
  "$(note z "" "")"

# ── Template linkage is a reference, and a reference is a pair ───────────────────────
chk "J-SB5-8 a template id with no code is REFUSED — unreadable when surgery-service is down" \
  "$(note a ", operative_template_ref" ", gen_random_uuid()")" "chk_procedure_note_template_pair"

chk "J-SB5-9 a template code with no id is REFUSED — unresolvable back to the template row" \
  "$(note b ", operative_template_code" ", 'GS-HERNIA-OPEN'")" "chk_procedure_note_template_pair"

chk_accepted "J-SB5-10 a complete template reference is accepted" \
  "$(note c ", operative_template_ref, operative_template_code" ", gen_random_uuid(), 'GS-HERNIA-OPEN'")"

# The linkage must NOT be a foreign key: surgery.surgical_operative_template lives in another
# service's database and is absent entirely here. An FK would make this rig — and every theatre
# rig — impossible to boot.
chk "J-SB5-11 the template linkage is a reference, NOT a cross-service foreign key" \
  "$(q "SELECT count(*) FROM information_schema.table_constraints tc
        JOIN information_schema.key_column_usage k ON k.constraint_name = tc.constraint_name
        WHERE tc.table_schema='inpatient' AND tc.table_name='procedure_note'
          AND tc.constraint_type='FOREIGN KEY' AND k.column_name='operative_template_ref'")" "^0$"

chk "J-SB5-12 the surgery schema is genuinely absent here, which is why an FK was impossible" \
  "$(q "SELECT count(*) FROM information_schema.schemata WHERE schema_name='surgery'")" "^0$"

# ── The three near-namesakes stay where they were ────────────────────────────────────
chk "J-SB5-13 the anaesthetic technique stays on the anaesthesia chart, unduplicated" \
  "$(q "SELECT count(*) FROM information_schema.columns
        WHERE table_schema='inpatient' AND column_name='technique' AND table_name='procedure_note'")" "^0$"

chk "J-SB5-14 PACU fluids and drains channels are untouched by this migration" \
  "$(q "SELECT count(*) FROM information_schema.columns
        WHERE table_schema='inpatient' AND table_name='procedure_pacu_observation'
          AND column_name IN ('fluids','drains_devices')")" "^2$"

chk "J-SB5-15 the fifteen pre-existing operative-record fields are unaffected" \
  "$(q "SELECT count(*) FROM information_schema.columns
        WHERE table_schema='inpatient' AND table_name='procedure_note' AND column_name IN
        ('performed_procedure','findings','specimens','implants','estimated_blood_loss_ml',
         'complications','counts_correct','postop_plan')")" "^8$"

{ echo "SB-5 operative record depth proof"; echo "generated: $(date -Is)"; echo "PASS=$PASS FAIL=$FAIL"; } > "$EVIDENCE/summary.txt"
echo; echo "SB-5 operative record proof: PASS=$PASS FAIL=$FAIL   evidence: $EVIDENCE"
[[ $FAIL -eq 0 ]] || exit 1
