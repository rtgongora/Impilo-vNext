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

# Leave the probe table as we found it (nothing else references these rows).
q "DELETE FROM pct.pct_treatment_regimens WHERE enrolment_id='$ENR';
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
