#!/usr/bin/env bash
# Surgery pipeline S3 — the surgical decision-making record, against REAL Postgres.
#
# S3 closes the surgical domain pack's own §8 (18 elements, all ABSENT per its audit).
# Diagnosis/certainty are deliberately NOT repeated here — surgical_episode.pct_problem_ref
# (S1) already resolves to the pct_problems row this service contributed. This rig proves the
# schema half of the remaining 16 elements: the closed final_decision vocabulary, and the
# three-way decided_by/decided_at/final_decision pairing — a decision with no author or no
# timestamp is not a decision anyone can stand behind.
#
# Usage:  bash scripts/runtime-proof/surgery-decision-journeys.sh
#         KEEP_RIG=1 ... to leave the container up.
set -uo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

PG_NAME="surgery-decision-rig-$$"
PG_PORT="${SURGERY_RIG_PG_PORT:-25463}"
TENANT="00000000-0000-4000-8000-000000000001"
MIG="services/surgery-service/src/main/resources/db/migration"
EVIDENCE="reports/journeys/surgery-decision-proof-$(date +%Y%m%d-%H%M%S)"

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

echo "=== surgery-service S3 surgical decision proof ==="
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

for f in V001__init V002__surgical_episode V003__surgical_assessment V004__surgical_decision; do
  out=$(docker exec -i "$PG_NAME" psql -U impilo -d surgery -v ON_ERROR_STOP=1 < "$MIG/$f.sql" 2>&1)
  echo "$out" > "$EVIDENCE/$f.txt"
  chk "J-S3-0 $f applies" "$(echo "$out" | grep -ci error || true)" "^0$"
done

EPISODE=$(q "INSERT INTO surgery.surgical_episode (tenant_id, subject_cpid, journey_id, operative_indication)
             VALUES ('$TENANT','CPID-1','journey-1','Symptomatic gallstones') RETURNING id" | head -1)

chk "J-S3-1 reasoning fields with no final decision yet are accepted" \
  "$(q "INSERT INTO surgery.surgical_decision (tenant_id, surgical_episode_id, natural_history, expected_benefit)
        VALUES ('$TENANT','$EPISODE','Risk of perforation if untreated','Resolves recurrent colic')")" \
  "INSERT 0 1"

chk "J-S3-2 a second decision row for the SAME episode is REFUSED — refine, never duplicate" \
  "$(q "INSERT INTO surgery.surgical_decision (tenant_id, surgical_episode_id, natural_history)
        VALUES ('$TENANT','$EPISODE','duplicate row')")" \
  "uq_surgical_decision_episode"

# ── Final decision vocabulary is closed ──
chk "J-S3-3 an invented final_decision value is REFUSED" \
  "$(q "UPDATE surgery.surgical_decision SET final_decision = 'MAYBE_LATER', decided_by = 'x', decided_at = now()
        WHERE surgical_episode_id='$EPISODE'")" \
  "chk_surgical_decision_final"

# ── The three-way pairing: a decision with no author, or no timestamp, is refused ──
chk "J-S3-4 a final_decision with no decided_by is REFUSED" \
  "$(q "UPDATE surgery.surgical_decision SET final_decision = 'PROCEED', decided_at = now()
        WHERE surgical_episode_id='$EPISODE'")" \
  "chk_surgical_decision_pair"

chk "J-S3-5 a final_decision with no decided_at is REFUSED" \
  "$(q "UPDATE surgery.surgical_decision SET final_decision = 'PROCEED', decided_by = 'dr-x'
        WHERE surgical_episode_id='$EPISODE'")" \
  "chk_surgical_decision_pair"

chk "J-S3-6 a fully paired final decision is accepted" \
  "$(q "UPDATE surgery.surgical_decision
        SET final_decision = 'PROCEED', decided_by = 'dr-x', decided_at = now()
        WHERE surgical_episode_id='$EPISODE'")" \
  "UPDATE 1"

chk "J-S3-7 an assessment for a nonexistent episode is REFUSED by the FK" \
  "$(q "INSERT INTO surgery.surgical_decision (tenant_id, surgical_episode_id, natural_history)
        VALUES ('$TENANT', gen_random_uuid(), 'orphan')")" \
  "violates foreign key constraint"

# ── CC-2 regression guard: diagnosis/certainty are NOT repeated here ──
chk "J-S3-8 surgical_decision carries no diagnosis/certainty column of its own (CC-2)" \
  "$(q "SELECT count(*) FROM information_schema.columns
        WHERE table_schema='surgery' AND table_name='surgical_decision' AND (
          column_name LIKE '%diagnos%' OR column_name LIKE '%certainty%')")" \
  "^0$"

echo
echo "=== S3 surgical decision proof: $PASS passed, $FAIL failed ==="
[[ $FAIL -eq 0 ]] || exit 1
