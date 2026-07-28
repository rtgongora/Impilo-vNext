#!/usr/bin/env bash
# Surgery pipeline S2 — the general surgical assessment, against REAL Postgres.
#
# S2 adds structured surgical assessment content — the surgical domain pack's own §5, ~27
# elements, all absent before this wave. Before writing this schema, each element was checked
# against the rest of the estate for a canonical home (allergies -> pct_allergies, medications ->
# OROS, tobacco/alcohol -> pct_social_history, functional status -> pct_functional_assessments,
# pregnancy -> pct_pregnancy_episodes, previous surgery -> pct_past_procedures, imaging -> pacs,
# pathology -> OROS histopath). This rig proves the schema half of that split: a reference id
# cannot exist without its reviewed flag, and no column here holds the underlying registry VALUE
# itself (only a flag + a short note + an opaque reference id).
#
# Usage:  bash scripts/runtime-proof/surgery-assessment-journeys.sh
#         KEEP_RIG=1 ... to leave the container up.
set -uo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

PG_NAME="surgery-assessment-rig-$$"
PG_PORT="${SURGERY_RIG_PG_PORT:-25462}"
TENANT="00000000-0000-4000-8000-000000000001"
MIG="services/surgery-service/src/main/resources/db/migration"
EVIDENCE="reports/journeys/surgery-assessment-proof-$(date +%Y%m%d-%H%M%S)"

PASS=0; FAIL=0
cleanup() {
  if [[ "${KEEP_RIG:-0}" == "1" ]]; then echo "KEEP_RIG=1 — $PG_NAME left on $PG_PORT"
  else docker rm -f "$PG_NAME" >/dev/null 2>&1 || true; fi
}
trap cleanup EXIT

q() { docker exec "$PG_NAME" psql -U impilo -d surgery -tAc "$1" 2>&1; }
chk() {
  local name="$1" out="$2" expect="$3"
  if echo "$out" | grep -qi -- "$expect"; then echo "PASS  $name"; PASS=$((PASS+1))
  else echo "FAIL  $name"; echo "      expected /$expect/, got: $(echo "$out"|tr '\n' ' '|cut -c1-200)"; FAIL=$((FAIL+1)); fi
}

echo "=== surgery-service S2 surgical assessment proof ==="
mkdir -p "$EVIDENCE"
docker rm -f "$PG_NAME" >/dev/null 2>&1 || true
docker run -d --name "$PG_NAME" -e POSTGRES_PASSWORD=impilo -e POSTGRES_USER=impilo \
  -e POSTGRES_DB=surgery -p "${PG_PORT}:5432" postgres:16 >/dev/null || { echo "FAIL: postgres"; exit 1; }
echo -n "waiting for postgres"
for _ in $(seq 1 60); do
  docker exec "$PG_NAME" psql -U impilo -d surgery -tAc "select 1" >/dev/null 2>&1 && break
  echo -n "."; sleep 2
done; echo
docker exec "$PG_NAME" psql -U impilo -d surgery -tAc "select 1" >/dev/null 2>&1 \
  || { echo "FAIL: postgres never reachable"; exit 1; }

for f in V001__init V002__surgical_episode V003__surgical_assessment; do
  out=$(docker exec -i "$PG_NAME" psql -U impilo -d surgery -v ON_ERROR_STOP=1 < "$MIG/$f.sql" 2>&1)
  echo "$out" > "$EVIDENCE/$f.txt"
  chk "J-S2-0 $f applies" "$(echo "$out" | grep -ci error || true)" "^0$"
done

EPISODE=$(q "INSERT INTO surgery.surgical_episode (tenant_id, subject_cpid, journey_id, operative_indication)
             VALUES ('$TENANT','CPID-1','journey-1','Symptomatic gallstones') RETURNING id" | head -1)

chk "J-S2-1 an assessment with only new-content fields is accepted" \
  "$(q "INSERT INTO surgery.surgical_assessment (tenant_id, surgical_episode_id, presenting_problem)
        VALUES ('$TENANT','$EPISODE','RUQ pain after fatty meals')")" \
  "INSERT 0 1"

chk "J-S2-2 a second assessment row for the SAME episode is REFUSED — refine, never duplicate" \
  "$(q "INSERT INTO surgery.surgical_assessment (tenant_id, surgical_episode_id, presenting_problem)
        VALUES ('$TENANT','$EPISODE','duplicate row')")" \
  "uq_surgical_assessment_episode"

# ── Reference-without-review is refused for every referenced registry ──
chk "J-S2-3 a functional_assessment_ref with reviewed still false is REFUSED" \
  "$(q "UPDATE surgery.surgical_assessment SET functional_assessment_ref = gen_random_uuid()
        WHERE surgical_episode_id='$EPISODE'")" \
  "chk_surgical_assessment_functional_ref"

chk "J-S2-4 a functional_assessment_ref WITH reviewed=true is accepted" \
  "$(q "UPDATE surgery.surgical_assessment
        SET functional_status_reviewed = TRUE, functional_assessment_ref = gen_random_uuid()
        WHERE surgical_episode_id='$EPISODE'")" \
  "UPDATE 1"

chk "J-S2-5 a pregnancy_episode_ref with confirmed still false is REFUSED" \
  "$(q "UPDATE surgery.surgical_assessment SET pregnancy_episode_ref = gen_random_uuid()
        WHERE surgical_episode_id='$EPISODE'")" \
  "chk_surgical_assessment_pregnancy_ref"

chk "J-S2-6 an imaging_ref with reviewed still false is REFUSED" \
  "$(q "UPDATE surgery.surgical_assessment SET imaging_ref = 'pacs-study-1'
        WHERE surgical_episode_id='$EPISODE'")" \
  "chk_surgical_assessment_imaging_ref"

chk "J-S2-7 a pathology_ref with reviewed still false is REFUSED" \
  "$(q "UPDATE surgery.surgical_assessment SET pathology_ref = 'oros-histo-1'
        WHERE surgical_episode_id='$EPISODE'")" \
  "chk_surgical_assessment_pathology_ref"

# ── Referencing an episode that does not exist is refused by the FK ──
chk "J-S2-8 an assessment for a nonexistent episode is REFUSED" \
  "$(q "INSERT INTO surgery.surgical_assessment (tenant_id, surgical_episode_id, presenting_problem)
        VALUES ('$TENANT', gen_random_uuid(), 'orphan')")" \
  "violates foreign key constraint"

# ── CC-2 regression guard: no column here holds a registry VALUE, only flags/notes/refs ──
chk "J-S2-9 no column stores allergy/medication/functional-status/pregnancy VALUES directly" \
  "$(q "SELECT coalesce(string_agg(column_name,','),'none') FROM information_schema.columns
        WHERE table_schema='surgery' AND table_name='surgical_assessment' AND (
          column_name = 'allergies' OR column_name = 'medications' OR
          column_name = 'functional_status' OR column_name = 'pregnancy_status' OR
          column_name LIKE '%tobacco%' OR column_name LIKE '%alcohol%')")" \
  "^none$"

echo
echo "=== S2 surgical assessment proof: $PASS passed, $FAIL failed ==="
[[ $FAIL -eq 0 ]] || exit 1
