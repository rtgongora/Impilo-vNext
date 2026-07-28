#!/usr/bin/env bash
# Surgery pipeline S1 — the surgical episode, against REAL Postgres.
#
# S1 extends PCT's pct_problems/pct_care_plans rather than duplicating them (the 2026-07-26 CC-2
# amendment, ADR §5a). This rig proves the schema-level half of that: the PCT-anchor requirement
# (CC-5), the honest-nullability of pct_problem_ref (populated only once a real contribution
# succeeds — this rig cannot exercise the actual HTTP call to pct-service, which is proven by
# PctProblemContributionClientTest instead), and that no CC-2-forbidden registry column
# (condition/diagnosis/staging/certainty) exists on THIS table either.
#
# Usage:  bash scripts/runtime-proof/surgery-episode-journeys.sh
#         KEEP_RIG=1 ... to leave the container up.
set -uo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

PG_NAME="surgery-episode-rig-$$"
PG_PORT="${SURGERY_RIG_PG_PORT:-25461}"
TENANT="00000000-0000-4000-8000-000000000001"
MIG="services/surgery-service/src/main/resources/db/migration"
EVIDENCE="reports/journeys/surgery-episode-proof-$(date +%Y%m%d-%H%M%S)"

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

echo "=== surgery-service S1 surgical episode proof ==="
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

for f in V001__init V002__surgical_episode; do
  out=$(docker exec -i "$PG_NAME" psql -U impilo -d surgery -v ON_ERROR_STOP=1 < "$MIG/$f.sql" 2>&1)
  echo "$out" > "$EVIDENCE/$f.txt"
  chk "J-S1-0 $f applies" "$(echo "$out" | grep -ci error || true)" "^0$"
done

# ── CC-5 PCT anchor: an episode with neither journey_id nor encounter_id is unrepresentable ──
chk "J-S1-1 an episode with NO pct anchor at all is REFUSED" \
  "$(q "INSERT INTO surgery.surgical_episode (tenant_id, subject_cpid, operative_indication)
        VALUES ('$TENANT','CPID-1','Symptomatic gallstones')")" \
  "chk_surgical_episode_pct_anchor"

chk "J-S1-2 an episode anchored by journey_id alone is accepted" \
  "$(q "INSERT INTO surgery.surgical_episode (tenant_id, subject_cpid, journey_id, operative_indication)
        VALUES ('$TENANT','CPID-1','journey-1','Symptomatic gallstones')")" \
  "INSERT 0 1"

chk "J-S1-3 an episode anchored by encounter_id alone is accepted" \
  "$(q "INSERT INTO surgery.surgical_episode (tenant_id, subject_cpid, encounter_id, operative_indication)
        VALUES ('$TENANT','CPID-2', gen_random_uuid(),'Acute appendicitis')")" \
  "INSERT 0 1"

# ── The surgical reasoning is required content, not an afterthought ──
chk "J-S1-4 a blank operative indication is REFUSED" \
  "$(q "INSERT INTO surgery.surgical_episode (tenant_id, subject_cpid, journey_id, operative_indication)
        VALUES ('$TENANT','CPID-3','journey-3','   ')")" \
  "chk_surgical_episode_indication"

# ── Lifecycle vocabulary is closed ──
chk "J-S1-5 an invented status is REFUSED" \
  "$(q "INSERT INTO surgery.surgical_episode (tenant_id, subject_cpid, journey_id, operative_indication, status)
        VALUES ('$TENANT','CPID-4','journey-4','Indication','PROBABLY_FINE')")" \
  "chk_surgical_episode_status"

# ── The contribution is a reference, never a copy: pct_problem_ref starts NULL and the schema
#    carries no column that would let anyone mistake "not yet contributed" for "no condition" ──
chk "J-S1-6 pct_problem_ref defaults to NULL — no contribution is assumed" \
  "$(q "SELECT pct_problem_ref FROM surgery.surgical_episode WHERE subject_cpid='CPID-1'")" \
  "^$"

chk "J-S1-7 the uncontributed-episode reconciliation index exists" \
  "$(q "SELECT count(*) FROM pg_indexes WHERE schemaname='surgery' AND indexname='idx_surgical_episode_uncontributed'")" \
  "^1$"

# ── CC-2 regression guard, repeated at the table level now that a domain table exists ──
chk "J-S1-8 surgical_episode itself carries no condition/diagnosis/staging/certainty column (CC-2)" \
  "$(q "SELECT count(*) FROM information_schema.columns
        WHERE table_schema='surgery' AND table_name='surgical_episode' AND (
          column_name LIKE '%condition%' OR column_name LIKE '%diagnos%' OR
          column_name LIKE '%staging%' OR column_name LIKE '%certainty%' OR
          column_name LIKE '%severity%')")" \
  "^0$"

echo
echo "=== S1 surgical episode proof: $PASS passed, $FAIL failed ==="
[[ $FAIL -eq 0 ]] || exit 1
