#!/usr/bin/env bash
# Surgery pipeline SB-1 — complication pathway instances (§15) + prehab/optimisation items
# (§10), against REAL Postgres.
#
# §15's own point (R8): a complication is a MANAGED pathway, not a logged event — this rig
# proves the pathway cannot CLOSE until it has been graded, owned, disclosed, and given an
# outcome. §10's items share the estate's rows-not-columns idiom (one row per episode+domain,
# refined not duplicated) and a CC-2-shaped guard: no column here holds the underlying clinical
# fact an item optimises (only a status + an opaque registry reference).
#
# Usage:  bash scripts/runtime-proof/surgery-complication-prehab-journeys.sh
#         KEEP_RIG=1 ... to leave the container up.
set -uo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

PG_NAME="surgery-sb1-rig-$$"
PG_PORT="${SURGERY_RIG_PG_PORT:-25464}"
TENANT="00000000-0000-4000-8000-000000000001"
MIG="services/surgery-service/src/main/resources/db/migration"
EVIDENCE="reports/journeys/surgery-sb1-proof-$(date +%Y%m%d-%H%M%S)"

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

echo "=== surgery-service SB-1 complication+prehab proof ==="
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

for f in V001__init V002__surgical_episode V003__surgical_assessment V004__surgical_decision \
         V005__complication_pathway_and_prehab; do
  out=$(docker exec -i "$PG_NAME" psql -U impilo -d surgery -v ON_ERROR_STOP=1 < "$MIG/$f.sql" 2>&1)
  echo "$out" > "$EVIDENCE/$f.txt"
  chk "J-SB1-0 $f applies" "$(echo "$out" | grep -ci error || true)" "^0$"
done

EPISODE=$(q "INSERT INTO surgery.surgical_episode (tenant_id, subject_cpid, journey_id, operative_indication)
             VALUES ('$TENANT','CPID-1','journey-1','Symptomatic gallstones') RETURNING id" | head -1)

# ── §15 complication pathway ──
chk "J-SB1-1 an invented complication_type is REFUSED" \
  "$(q "INSERT INTO surgery.surgical_complication_pathway (tenant_id, surgical_episode_id, complication_type, recognised_by)
        VALUES ('$TENANT','$EPISODE','ALIEN_ABDUCTION','dr-x')")" \
  "chk_complication_pathway_type"

PATHWAY=$(q "INSERT INTO surgery.surgical_complication_pathway
               (tenant_id, surgical_episode_id, complication_type, recognised_by)
             VALUES ('$TENANT','$EPISODE','ANASTOMOTIC_LEAK','dr-x') RETURNING id" | head -1)

chk "J-SB1-2 closing an ungraded pathway is REFUSED" \
  "$(q "UPDATE surgery.surgical_complication_pathway SET status='CLOSED', outcome='Resolved', closed_at=now(), closed_by='dr-x'
        WHERE id='$PATHWAY'")" \
  "chk_complication_pathway_closure"

chk "J-SB1-3 grading with an invented code is REFUSED" \
  "$(q "UPDATE surgery.surgical_complication_pathway SET grade_code='GRADE_X', graded_by='dr-x', graded_at=now()
        WHERE id='$PATHWAY'")" \
  "chk_complication_pathway_grade"

chk "J-SB1-4 a grade with no grader (an incomplete pair) is REFUSED" \
  "$(q "UPDATE surgery.surgical_complication_pathway SET grade_code='IIIB' WHERE id='$PATHWAY'")" \
  "chk_complication_pathway_grade_pair"

q "UPDATE surgery.surgical_complication_pathway SET grade_code='IIIB', graded_by='dr-x', graded_at=now() WHERE id='$PATHWAY'" >/dev/null

chk "J-SB1-5 closing a graded-but-not-owned/disclosed pathway is still REFUSED" \
  "$(q "UPDATE surgery.surgical_complication_pathway SET status='CLOSED', outcome='Resolved', closed_at=now(), closed_by='dr-x'
        WHERE id='$PATHWAY'")" \
  "chk_complication_pathway_closure"

q "UPDATE surgery.surgical_complication_pathway
   SET owned_by='General Surgery Team B', disclosed_at=now(), disclosed_by='dr-x'
   WHERE id='$PATHWAY'" >/dev/null

chk "J-SB1-6 closing a fully-managed pathway (graded, owned, disclosed, outcome) is accepted" \
  "$(q "UPDATE surgery.surgical_complication_pathway SET status='CLOSED', outcome='Resolved with reoperation', closed_at=now(), closed_by='dr-x'
        WHERE id='$PATHWAY'")" \
  "UPDATE 1"

chk "J-SB1-7 disclosed_at with no disclosed_by is REFUSED (a second pathway, isolated)" \
  "$(q "INSERT INTO surgery.surgical_complication_pathway
          (tenant_id, surgical_episode_id, complication_type, recognised_by, disclosed_at)
        VALUES ('$TENANT','$EPISODE','BLEEDING','dr-y', now())")" \
  "chk_complication_pathway_disclosure_pair"

# ── §10 prehab items ──
chk "J-SB1-8 an invented prehab domain is REFUSED" \
  "$(q "INSERT INTO surgery.surgical_prehab_item (tenant_id, surgical_episode_id, domain)
        VALUES ('$TENANT','$EPISODE','ASTROLOGY')")" \
  "chk_prehab_item_domain"

chk "J-SB1-9 a valid prehab item is accepted" \
  "$(q "INSERT INTO surgery.surgical_prehab_item (tenant_id, surgical_episode_id, domain, status, planned_by)
        VALUES ('$TENANT','$EPISODE','SMOKING_CESSATION','IN_PROGRESS','nurse-1')")" \
  "INSERT 0 1"

chk "J-SB1-10 a second item for the SAME domain on the SAME episode is REFUSED — refine, never duplicate" \
  "$(q "INSERT INTO surgery.surgical_prehab_item (tenant_id, surgical_episode_id, domain)
        VALUES ('$TENANT','$EPISODE','SMOKING_CESSATION')")" \
  "uq_prehab_item_domain"

chk "J-SB1-11 an invented prehab status is REFUSED" \
  "$(q "UPDATE surgery.surgical_prehab_item SET status='VIBES_GOOD'
        WHERE surgical_episode_id='$EPISODE' AND domain='SMOKING_CESSATION'")" \
  "chk_prehab_item_status"

# ── CC-2 regression guard: no column holds the underlying clinical fact an item optimises ──
chk "J-SB1-12 no column on surgical_prehab_item stores a clinical VALUE directly" \
  "$(q "SELECT coalesce(string_agg(column_name,','),'none') FROM information_schema.columns
        WHERE table_schema='surgery' AND table_name='surgical_prehab_item' AND (
          column_name LIKE '%smoking%' OR column_name LIKE '%alcohol%' OR
          column_name LIKE '%haemoglobin%' OR column_name LIKE '%weight_kg%')")" \
  "^none$"

echo
echo "=== SB-1 proof: $PASS passed, $FAIL failed ==="
[[ $FAIL -eq 0 ]] || exit 1
