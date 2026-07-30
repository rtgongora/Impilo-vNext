#!/usr/bin/env bash
# Surgical demonstration 3 — individual decision or board decision? (V012) — runtime proof
# against real Postgres.
#
# Every guarantee V012 makes is a CHECK constraint, and H2 with ddl-auto enforces none of them.
# The important ones are the contradictions: an MDT decision that names no board, an individual
# decision that carries one, a reference with no source (PCT has TWO MDT systems of record, so a
# bare id says nothing about which table it lives in), and a verification of a reference that was
# never made.
#
# Usage:  bash scripts/runtime-proof/surgery-decision-forum-journeys.sh
#         KEEP_RIG=1 ... to leave the container up.
set -uo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

PG_NAME="surgery-forum-rig-$$"
PG_PORT="${SURGERY_FORUM_RIG_PG_PORT:-25488}"
TENANT="00000000-0000-4000-8000-000000000001"
BOARD="22222222-2222-4222-8222-222222222222"
MIG="services/surgery-service/src/main/resources/db/migration"
EVIDENCE="reports/journeys/surgery-decision-forum-proof-$(date +%Y%m%d-%H%M%S)"

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
  else echo "FAIL  $name"; echo "      expected /$expect/, got: $(echo "$out"|tr '\n' ' '|cut -c1-220)"; FAIL=$((FAIL+1)); fi
}

echo "=== surgery-service V012 decision forum / MDT reference proof ==="
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

apply() {
  out=$(docker exec -i "$PG_NAME" psql -U impilo -d surgery -v ON_ERROR_STOP=1 < "$MIG/$1.sql" 2>&1)
  echo "$out" > "$EVIDENCE/$1.txt"
  chk "J-SB3F-0 $1 applies" "$(echo "$out" | grep -ci error || true)" "^0$"
}

for f in V001__init V002__surgical_episode V003__surgical_assessment V004__surgical_decision \
         V005__complication_pathway_and_prehab V006__longitudinal_objects_followup_specialty \
         V007__specialty_content_seed V008__analytics_indicator_catalogue \
         V010__surgical_episode_reoperation V011__surgical_episode_specialty; do
  apply "$f"
done

EP=$(q "INSERT INTO surgery.surgical_episode (tenant_id, subject_cpid, journey_id, operative_indication, status)
        VALUES ('$TENANT', 'CPID-FORUM-1', 'journey-forum', 'Rectal adenocarcinoma, low anterior resection', 'ASSESSMENT')
        RETURNING id" | grep -Eio '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' | head -1)

# A decision recorded BEFORE V012 — the migration has to leave it meaning what it meant.
q "INSERT INTO surgery.surgical_decision (tenant_id, surgical_episode_id, expected_benefit,
      final_decision, decided_by, decided_at)
   VALUES ('$TENANT', '$EP', 'Curative resection', 'PROCEED', 'actor-surgeon', now())" >/dev/null

apply V012__surgical_decision_mdt_forum

# ── The pre-V012 row ──────────────────────────────────────────────────────────────────
chk "J-SB3F-1 a decision recorded before V012 becomes INDIVIDUAL, which is what it was" \
  "$(q "SELECT decision_forum FROM surgery.surgical_decision WHERE surgical_episode_id='$EP'")" \
  "^INDIVIDUAL$"

chk "J-SB3F-2 and it carries no invented board reference" \
  "$(q "SELECT count(*) FROM surgery.surgical_decision
        WHERE surgical_episode_id='$EP' AND (mdt_decision_ref IS NOT NULL OR mdt_decision_source IS NOT NULL)")" \
  "^0$"

# ── A board decision ──────────────────────────────────────────────────────────────────
chk "J-SB3F-3 a board decision records the forum, the reference and the source table" \
  "$(q "UPDATE surgery.surgical_decision
        SET decision_forum='MDT', mdt_decision_ref='$BOARD', mdt_decision_source='PCT_MDT_DECISION',
            mdt_decision_verified_at=now()
        WHERE surgical_episode_id='$EP';
        SELECT decision_forum || '/' || mdt_decision_source
        FROM surgery.surgical_decision WHERE surgical_episode_id='$EP'")" \
  "^MDT/PCT_MDT_DECISION$"

chk "J-SB3F-4 the telemedicine board lane is an accepted source too — PCT has two" \
  "$(q "UPDATE surgery.surgical_decision SET mdt_decision_source='PCT_MDT_CASE_ITEM'
        WHERE surgical_episode_id='$EP';
        SELECT mdt_decision_source FROM surgery.surgical_decision WHERE surgical_episode_id='$EP'")" \
  "^PCT_MDT_CASE_ITEM$"

# ── The contradictions ────────────────────────────────────────────────────────────────
chk "J-SB3F-5 NEGATIVE an MDT decision that names no board is refused" \
  "$(q "UPDATE surgery.surgical_decision
        SET mdt_decision_ref=NULL, mdt_decision_source=NULL, mdt_decision_verified_at=NULL
        WHERE surgical_episode_id='$EP'")" \
  "chk_surgical_decision_mdt_forum_ref"

chk "J-SB3F-6 NEGATIVE a reference with no source table is refused — a bare id is ambiguous" \
  "$(q "UPDATE surgery.surgical_decision SET mdt_decision_source=NULL WHERE surgical_episode_id='$EP'")" \
  "chk_surgical_decision_mdt_ref_pair"

chk "J-SB3F-7 NEGATIVE an invented source table is refused" \
  "$(q "UPDATE surgery.surgical_decision SET mdt_decision_source='SURGERY_OWN_MDT'
        WHERE surgical_episode_id='$EP'")" \
  "chk_surgical_decision_mdt_source"

chk "J-SB3F-8 NEGATIVE an invented forum is refused" \
  "$(q "UPDATE surgery.surgical_decision SET decision_forum='CORRIDOR_CHAT' WHERE surgical_episode_id='$EP'")" \
  "chk_surgical_decision_forum"

chk "J-SB3F-9 NEGATIVE an INDIVIDUAL decision carrying a board reference is refused" \
  "$(q "UPDATE surgery.surgical_decision SET decision_forum='INDIVIDUAL' WHERE surgical_episode_id='$EP'")" \
  "chk_surgical_decision_mdt_forum_ref"

chk "J-SB3F-10 NEGATIVE nothing can be verified that was never referenced" \
  "$(q "INSERT INTO surgery.surgical_decision (tenant_id, surgical_episode_id, mdt_decision_verified_at)
        VALUES ('$TENANT', '$EP', now())")" \
  "chk_surgical_decision_mdt_verified"

chk "J-SB3F-11 NEGATIVE none of the refusals changed the row" \
  "$(q "SELECT decision_forum || '/' || mdt_decision_source
        FROM surgery.surgical_decision WHERE surgical_episode_id='$EP'")" \
  "^MDT/PCT_MDT_CASE_ITEM$"

# ── Reverting to an individual decision, done properly ────────────────────────────────
chk "J-SB3F-12 reverting to INDIVIDUAL works when the reference is cleared with it" \
  "$(q "UPDATE surgery.surgical_decision
        SET decision_forum='INDIVIDUAL', mdt_decision_ref=NULL, mdt_decision_source=NULL,
            mdt_decision_verified_at=NULL
        WHERE surgical_episode_id='$EP';
        SELECT decision_forum FROM surgery.surgical_decision WHERE surgical_episode_id='$EP'")" \
  "^INDIVIDUAL$"

# ── Unverified is not invalid ─────────────────────────────────────────────────────────
EP2=$(q "INSERT INTO surgery.surgical_episode (tenant_id, subject_cpid, journey_id, operative_indication, status)
         VALUES ('$TENANT', 'CPID-FORUM-2', 'journey-forum-2', 'Oesophagectomy', 'ASSESSMENT')
         RETURNING id" | grep -Eio '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' | head -1)

chk "J-SB3F-13 a board decision recorded while PCT was unreachable is stored unverified" \
  "$(q "INSERT INTO surgery.surgical_decision
          (tenant_id, surgical_episode_id, decision_forum, mdt_decision_ref, mdt_decision_source)
        VALUES ('$TENANT', '$EP2', 'MDT', '$BOARD', 'PCT_MDT_DECISION')
        RETURNING decision_forum")" \
  "^MDT$"

chk "J-SB3F-14 the reconciliation index finds exactly the unverified board decisions" \
  "$(q "SELECT count(*) FROM surgery.surgical_decision
        WHERE decision_forum='MDT' AND mdt_decision_verified_at IS NULL")" "^1$"

chk "J-SB3F-15 the unverified-reconciliation index exists and is partial" \
  "$(q "SELECT indexdef FROM pg_indexes
        WHERE schemaname='surgery' AND indexname='idx_surgical_decision_mdt_unverified'")" \
  "mdt_decision_verified_at IS NULL"

# ── V004's own guarantees still hold ──────────────────────────────────────────────────
chk "J-SB3F-16 the pre-existing final-decision triple is still enforced" \
  "$(q "UPDATE surgery.surgical_decision SET final_decision='PROCEED' WHERE surgical_episode_id='$EP2'")" \
  "chk_surgical_decision_pair"

chk "J-SB3F-17 one decision row per episode is still enforced" \
  "$(q "INSERT INTO surgery.surgical_decision (tenant_id, surgical_episode_id)
        VALUES ('$TENANT', '$EP2')")" \
  "uq_surgical_decision_episode"

echo
echo "PASS=$PASS FAIL=$FAIL"
{ echo "PASS=$PASS"; echo "FAIL=$FAIL"; } > "$EVIDENCE/summary.txt"
q "SELECT surgical_episode_id, decision_forum, mdt_decision_source, mdt_decision_ref, mdt_decision_verified_at
   FROM surgery.surgical_decision" > "$EVIDENCE/decisions.txt"
echo "evidence: $EVIDENCE"
[ "$FAIL" = 0 ]
