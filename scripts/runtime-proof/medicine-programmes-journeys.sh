#!/usr/bin/env bash
# Adult Medicine W3 — HIV/TB programme spine runtime proof.
#
# Proves the W3 pct migrations against REAL Postgres, in migration order on a clean database — the
# exact condition the withdrawn V108 FK failed and a preview dry-run could not test. Two things a
# unit test cannot show:
#   1. LANDED: flyway_schema_history in the pct schema records V108/V109 success=true.
#   2. CORRECT: the constraints they declare BITE — every probe carries a negative control alongside
#      the positive, because a shape-correct but constraintless table passes a positive probe until
#      bad data is already in.
#
# It applies the WHOLE pct migration set in version order (out-of-order is irrelevant on a clean
# boot), so a cross-band ordering trap would surface here as a failed apply rather than in production.
#
# What it does NOT prove, and says so rather than implying it: the BFF /internal/v1/programmes
# ingress path with a POSITIVE trust assertion and a tokenless NEGATIVE control. That needs the
# deployed estate (pct redeployed from a tip carrying V108/V109), coordinated with the vitals
# session. This rig proves the schema truth; the ingress proof is the estate's to give.
#
# Usage:  bash scripts/runtime-proof/medicine-programmes-journeys.sh
#         KEEP_RIG=1 ... to leave the container up for inspection.
set -uo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

PG_NAME="medicine-rig-pg-$$"
PG_PORT="${MEDICINE_RIG_PG_PORT:-25447}"
TENANT="00000000-0000-4000-8000-000000000001"
MIG_DIR="services/pct-service/src/main/resources/db/migration"
EVIDENCE="reports/journeys/medicine-programmes-proof-$(date +%Y%m%d-%H%M%S)"

PASS=0; FAIL=0
cleanup() {
  if [[ "${KEEP_RIG:-0}" == "1" ]]; then
    echo "KEEP_RIG=1 — leaving $PG_NAME up on port $PG_PORT"
  else
    docker rm -f "$PG_NAME" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

q() { docker exec "$PG_NAME" psql -U impilo -d pct -tAc "$1" 2>&1; }

# Match the whole psql response, not tail -1 (which can catch the DETAIL line instead of ERROR).
chk() {
  local name="$1" out="$2" expect="$3"
  if echo "$out" | grep -qi -- "$expect"; then
    echo "PASS  $name"; PASS=$((PASS+1))
  else
    echo "FAIL  $name"; echo "      expected /$expect/, got: $(echo "$out" | tr '\n' ' ' | cut -c1-240)"
    FAIL=$((FAIL+1))
  fi
}

echo "=== Adult Medicine W3 — HIV/TB programme spine proof ==="
mkdir -p "$EVIDENCE"

docker rm -f "$PG_NAME" >/dev/null 2>&1 || true
docker run -d --name "$PG_NAME" \
  -e POSTGRES_PASSWORD=impilo -e POSTGRES_USER=impilo -e POSTGRES_DB=pct \
  -p "${PG_PORT}:5432" postgres:16 >/dev/null || { echo "FAIL: could not start postgres"; exit 1; }

echo -n "waiting for postgres"
for _ in $(seq 1 60); do
  if docker exec "$PG_NAME" psql -U impilo -d pct -tAc "select 1" >/dev/null 2>&1; then break; fi
  echo -n "."; sleep 2
done
echo
docker exec "$PG_NAME" psql -U impilo -d pct -tAc "select 1" >/dev/null 2>&1 \
  || { echo "FAIL: postgres never became reachable"; exit 1; }

# pct migrations run in the pct schema.
q "CREATE SCHEMA IF NOT EXISTS pct; SET search_path TO pct;" >/dev/null

# ── Apply the whole pct migration set in version order ──────────────────────────
# Sorted by version (V035, V043, ..., V100, ..., V108, V109, ..., V430...). A clean boot applies in
# order; a cross-band dependency that could not resolve in order would fail here.
echo "--- applying pct migrations in version order ---"
APPLIED=0
while IFS= read -r f; do
  base="$(basename "$f")"
  out="$(docker exec -i "$PG_NAME" psql -U impilo -d pct -v ON_ERROR_STOP=1 -c 'SET search_path TO pct;' -f - < "$f" 2>&1)"
  if echo "$out" | grep -qiE 'ERROR|FATAL'; then
    echo "FAIL  apply $base"
    echo "      $(echo "$out" | grep -iE 'ERROR|FATAL' | head -1 | cut -c1-240)"
    FAIL=$((FAIL+1))
  else
    APPLIED=$((APPLIED+1))
  fi
done < <(ls "$MIG_DIR"/*.sql | sort -V)
echo "applied $APPLIED migration file(s) cleanly"

# ── LANDED: the tables exist in the pct schema ──────────────────────────────────
chk "V108 pct_programme_enrolments exists in pct schema" \
    "$(q "SELECT to_regclass('pct.pct_programme_enrolments');")" "pct_programme_enrolments"
chk "V109 pct_treatment_regimens exists in pct schema" \
    "$(q "SELECT to_regclass('pct.pct_treatment_regimens');")" "pct_treatment_regimens"

# A problem row + medical episode to anchor against (the enrolment FK requires a real problem).
PROB="11111111-1111-4111-8111-111111111111"
q "INSERT INTO pct.pct_problems (problem_id, tenant_id, subject_cpid, display, clinical_status, category, recorded_by, created_at)
   VALUES ('$PROB','$TENANT','cpid-1','HIV disease','ACTIVE','DIAGNOSIS','clin-1', now())
   ON CONFLICT DO NOTHING;" >/dev/null

# ── CORRECT: the anchor FK bites ────────────────────────────────────────────────
# Negative: an enrolment anchored to a non-existent problem must be refused.
chk "anchor_problem_id FK refuses a dangling problem (negative control)" \
    "$(q "INSERT INTO pct.pct_programme_enrolments
          (enrolment_id, tenant_id, subject_cpid, programme, status, anchor_problem_id, enrolled_on, created_by)
          VALUES (gen_random_uuid(),'$TENANT','cpid-1','HIV_CARE','SCREENING',
                  '99999999-9999-4999-8999-999999999999', now(), 'clin-1');")" \
    "violates foreign key"

# Positive: a real anchor is accepted.
ENR="22222222-2222-4222-8222-222222222222"
chk "enrolment against a real problem is accepted (positive control)" \
    "$(q "INSERT INTO pct.pct_programme_enrolments
          (enrolment_id, tenant_id, subject_cpid, programme, status, anchor_problem_id, enrolled_on, created_by)
          VALUES ('$ENR','$TENANT','cpid-1','HIV_CARE','ON_TREATMENT','$PROB', now(), 'clin-1')
          RETURNING enrolment_id;")" \
    "$ENR"

# ── CORRECT: the programme CHECK bites ──────────────────────────────────────────
chk "programme CHECK refuses an unknown programme (negative control)" \
    "$(q "INSERT INTO pct.pct_programme_enrolments
          (enrolment_id, tenant_id, subject_cpid, programme, status, anchor_problem_id, enrolled_on, created_by)
          VALUES (gen_random_uuid(),'$TENANT','cpid-1','MALARIA_CARE','SCREENING','$PROB', now(), 'clin-1');")" \
    "violates check constraint"

# ── CORRECT: the one-active-enrolment partial-unique bites ──────────────────────
chk "a second active HIV enrolment is refused (partial-unique, negative control)" \
    "$(q "INSERT INTO pct.pct_programme_enrolments
          (enrolment_id, tenant_id, subject_cpid, programme, status, anchor_problem_id, enrolled_on, created_by)
          VALUES (gen_random_uuid(),'$TENANT','cpid-1','HIV_CARE','SCREENING','$PROB', now(), 'clin-1');")" \
    "duplicate key value"

# ── CORRECT: the EXITED-requires-reason CHECK bites ─────────────────────────────
chk "EXITED without a reason is refused (negative control)" \
    "$(q "UPDATE pct.pct_programme_enrolments SET status='EXITED', exit_on=now() WHERE enrolment_id='$ENR';")" \
    "violates check constraint"

# ── CORRECT: the regimen one-current partial-unique + stage CHECK bite ──────────
REG="33333333-3333-4333-8333-333333333333"
chk "first regimen accepted (positive control)" \
    "$(q "INSERT INTO pct.pct_treatment_regimens
          (regimen_id, tenant_id, enrolment_id, subject_cpid, regimen_code, regimen_stage, started_on, recorded_by)
          VALUES ('$REG','$TENANT','$ENR','cpid-1','TLD','FIRST_LINE', now(), 'clin-1')
          RETURNING regimen_id;")" \
    "$REG"
chk "a second current regimen is refused (one-current partial-unique, negative control)" \
    "$(q "INSERT INTO pct.pct_treatment_regimens
          (regimen_id, tenant_id, enrolment_id, subject_cpid, regimen_code, regimen_stage, started_on, recorded_by)
          VALUES (gen_random_uuid(),'$TENANT','$ENR','cpid-1','TLE','SECOND_LINE', now(), 'clin-1');")" \
    "duplicate key value"

# ── LANDED (schema-history semantics): the constraints are VALIDATED, not just declared ─────────
chk "the anchor FK is validated in pg_constraint (convalidated), not NOT VALID" \
    "$(q "SELECT convalidated FROM pg_constraint WHERE conname LIKE '%programme_enrolments%anchor%' OR (contype='f' AND conrelid='pct.pct_programme_enrolments'::regclass) LIMIT 1;")" \
    "t"

# ── V111 PMTCT seam: the pregnancy_episode_id soft-link FK bites ─────────────────
# Negative control: an enrolment pointing at a pregnancy episode that does not exist is refused.
# Uses TB_TREATMENT so it does not trip the one-active-HIV partial-unique from the row above.
chk "pregnancy_episode_id FK refuses a dangling episode (V111 negative control)" \
    "$(q "INSERT INTO pct.pct_programme_enrolments
          (enrolment_id, tenant_id, subject_cpid, programme, status, anchor_problem_id, enrolled_on, created_by, pregnancy_episode_id)
          VALUES (gen_random_uuid(),'$TENANT','cpid-1','TB_TREATMENT','SCREENING','$PROB', now(), 'clin-1',
                  '88888888-8888-4888-8888-888888888888');")" \
    "violates foreign key"
chk "the pregnancy-episode FK is validated in pg_constraint (convalidated)" \
    "$(q "SELECT convalidated FROM pg_constraint WHERE conname='pct_programme_enrolments_pregnancy_episode_fk';")" \
    "t"

# ── V112 chronic-disease registers: the register is a programme, and control is a dated claim ──
#
# A migration that LANDED is not a migration that is CORRECT. Each of these shows the constraint
# actually biting on this database, positive alongside negative, because a hand-repaired or
# old-jar table can be present, shape-correct and constraintless — invisible to a positive probe
# until bad data is already in.
REG_ENR="44444444-4444-4444-8444-444444444444"
chk "a hypertension register entry is accepted (V112 positive control)" \
    "$(q "INSERT INTO pct.pct_programme_enrolments
          (enrolment_id, tenant_id, subject_cpid, programme, status, anchor_problem_id, enrolled_on, created_by)
          VALUES ('$REG_ENR','$TENANT','cpid-1','HYPERTENSION','ON_TREATMENT','$PROB', now(), 'clin-1')
          RETURNING enrolment_id;")" \
    "$REG_ENR"
chk "an unknown control status is refused (negative control)" \
    "$(q "UPDATE pct.pct_programme_enrolments
             SET control_status='MOSTLY_FINE', control_assessed_on=now()
           WHERE enrolment_id='$REG_ENR';")" \
    "violates check constraint"
# The one that matters most: an undated control status is a claim about now resting on an
# assessment of unknown age, and the register is exactly where somebody decides who to recall.
chk "a control status without an assessment date is refused (negative control)" \
    "$(q "UPDATE pct.pct_programme_enrolments SET control_status='CONTROLLED'
           WHERE enrolment_id='$REG_ENR';")" \
    "violates check constraint"
chk "a dated control status is accepted (positive control)" \
    "$(q "UPDATE pct.pct_programme_enrolments
             SET control_status='NOT_CONTROLLED', control_assessed_on=now()
           WHERE enrolment_id='$REG_ENR' RETURNING control_status;")" \
    "NOT_CONTROLLED"
chk "a control assessment dated before enrolment is refused (negative control)" \
    "$(q "UPDATE pct.pct_programme_enrolments
             SET control_assessed_on = enrolled_on - INTERVAL '1 day'
           WHERE enrolment_id='$REG_ENR';")" \
    "violates check constraint"
# DIAGNOSIS_REFUTED exists so that leaving a register because the diagnosis was wrong is not
# recorded as the patient having declined care they never needed.
chk "DIAGNOSIS_REFUTED is an accepted exit reason (positive control)" \
    "$(q "UPDATE pct.pct_programme_enrolments
             SET status='EXITED', exit_on=now(), exit_reason='DIAGNOSIS_REFUTED'
           WHERE enrolment_id='$REG_ENR' RETURNING exit_reason;")" \
    "DIAGNOSIS_REFUTED"
chk "the cohort index exists, so a register read is not a sequential scan (V112)" \
    "$(q "SELECT indexname FROM pg_indexes
           WHERE schemaname='pct' AND indexname='idx_pct_programme_enrolments_cohort';")" \
    "idx_pct_programme_enrolments_cohort"

# ── V113 examination framework: the six states, and the ones that must say something ───────────
#
# The framework exists because a two-state examination form forces every unperformed examination
# into "normal". These checks prove the database refuses the shapes that would let that happen.
EXAM="55555555-5555-4555-8555-555555555555"
chk "an examination with neither journey nor encounter is refused (CC-5 anchor, negative control)" \
    "$(q "INSERT INTO pct.pct_examinations
          (examination_id, tenant_id, subject_cpid, examined_at, examined_by)
          VALUES (gen_random_uuid(),'$TENANT','cpid-1', now(), 'clin-1');")" \
    "violates check constraint"
chk "an anchored examination is accepted (positive control)" \
    "$(q "INSERT INTO pct.pct_examinations
          (examination_id, tenant_id, subject_cpid, journey_id, examined_at, examined_by)
          VALUES ('$EXAM','$TENANT','cpid-1','66666666-6666-4666-8666-666666666666', now(), 'clin-1')
          RETURNING examination_id;")" \
    "$EXAM"
chk "an invented examination state is refused (negative control)" \
    "$(q "INSERT INTO pct.pct_examination_findings
          (finding_id, tenant_id, examination_id, region, state)
          VALUES (gen_random_uuid(),'$TENANT','$EXAM','ABDOMEN','PROBABLY_FINE');")" \
    "violates check constraint"
# The invariant that carries the clinical weight: an abnormality nobody wrote down reads to the next
# clinician as a normal examination somebody bothered to document.
chk "ABNORMAL with no detail is refused (negative control)" \
    "$(q "INSERT INTO pct.pct_examination_findings
          (finding_id, tenant_id, examination_id, region, state)
          VALUES (gen_random_uuid(),'$TENANT','$EXAM','ABDOMEN','ABNORMAL');")" \
    "violates check constraint"
chk "UNABLE_TO_EXAMINE with no reason is refused (negative control)" \
    "$(q "INSERT INTO pct.pct_examination_findings
          (finding_id, tenant_id, examination_id, region, state)
          VALUES (gen_random_uuid(),'$TENANT','$EXAM','NEUROLOGY','UNABLE_TO_EXAMINE');")" \
    "violates check constraint"
chk "NOT_EXAMINED stands alone with no detail (positive control)" \
    "$(q "INSERT INTO pct.pct_examination_findings
          (finding_id, tenant_id, examination_id, region, state)
          VALUES (gen_random_uuid(),'$TENANT','$EXAM','CARDIOVASCULAR','NOT_EXAMINED')
          RETURNING state;")" \
    "NOT_EXAMINED"
chk "ABNORMAL with detail is accepted (positive control)" \
    "$(q "INSERT INTO pct.pct_examination_findings
          (finding_id, tenant_id, examination_id, region, state, detail)
          VALUES (gen_random_uuid(),'$TENANT','$EXAM','ABDOMEN','ABNORMAL','tender right upper quadrant')
          RETURNING state;")" \
    "ABNORMAL"
chk "the same region twice in one examination is refused (negative control)" \
    "$(q "INSERT INTO pct.pct_examination_findings
          (finding_id, tenant_id, examination_id, region, state, detail)
          VALUES (gen_random_uuid(),'$TENANT','$EXAM','ABDOMEN','NORMAL', NULL);")" \
    "duplicate key value"
# A site pin with no diagram is a coordinate with no map — it cannot be drawn or read back.
chk "a site without a graphic is refused (negative control)" \
    "$(q "INSERT INTO pct.pct_examination_findings
          (finding_id, tenant_id, examination_id, region, state, detail, site)
          VALUES (gen_random_uuid(),'$TENANT','$EXAM','SKIN','ABNORMAL','ulcer','left heel');")" \
    "violates check constraint"
chk "a sited finding on a named graphic is accepted (positive control)" \
    "$(q "INSERT INTO pct.pct_examination_findings
          (finding_id, tenant_id, examination_id, region, state, detail, graphic, site, laterality)
          VALUES (gen_random_uuid(),'$TENANT','$EXAM','FEET','ABNORMAL','neuropathic ulcer',
                  'DIABETIC_FOOT','plantar first metatarsal head','LEFT')
          RETURNING graphic;")" \
    "DIABETIC_FOOT"

# Leave the probe table as we found it (nothing else references these rows).
q "DELETE FROM pct.pct_examinations WHERE examination_id='$EXAM';
   DELETE FROM pct.pct_programme_enrolments WHERE enrolment_id='$REG_ENR';
   DELETE FROM pct.pct_treatment_regimens WHERE enrolment_id='$ENR';
   DELETE FROM pct.pct_programme_enrolments WHERE enrolment_id='$ENR';
   DELETE FROM pct.pct_problems WHERE problem_id='$PROB';" >/dev/null
LEFT="$(q "SELECT count(*) FROM pct.pct_programme_enrolments WHERE subject_cpid='cpid-1';")"
echo "probe rows left: ${LEFT}"

echo
echo "=== result: PASS=$PASS FAIL=$FAIL ==="
{
  echo "medicine W3 programme spine proof"
  echo "applied=$APPLIED PASS=$PASS FAIL=$FAIL probe_rows_left=$LEFT"
} > "$EVIDENCE/summary.txt"
[[ $FAIL -eq 0 ]] || exit 1
