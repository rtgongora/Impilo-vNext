#!/usr/bin/env bash
# Surgical demonstration 9 — a reoperation joins the SAME surgical episode (V010) — runtime
# proof against real Postgres.
#
# Everything V010 asserts is a CHECK constraint or a foreign key, and the module tests run on H2
# with ddl-auto, where none of them exist. A constraint that only a mock has ever seen is not a
# constraint anyone has tested — so the widened status vocabulary, the audit-triple rule, the
# self-reference ban and the predecessor key are all proved here, on the migration itself.
#
# Negative assertions carry the weight: the point is not that REOPENED can be written, it is that
# an UNAUDITED reopen cannot be.
#
# Usage:  bash scripts/runtime-proof/surgery-reoperation-journeys.sh
#         KEEP_RIG=1 ... to leave the container up.
set -uo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

PG_NAME="surgery-reop-rig-$$"
PG_PORT="${SURGERY_REOP_RIG_PG_PORT:-25486}"
TENANT="00000000-0000-4000-8000-000000000001"
MIG="services/surgery-service/src/main/resources/db/migration"
EVIDENCE="reports/journeys/surgery-reoperation-proof-$(date +%Y%m%d-%H%M%S)"

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
# The sibling rigs' chk() greps with BRE, where {8} is eight literal braces-and-digits rather
# than a quantifier. Anything with a real quantifier — every UUID assertion here — needs ERE.
chk_re() {
  local name="$1" out="$2" expect="$3"
  if echo "$out" | grep -qiE -- "$expect"; then echo "PASS  $name"; PASS=$((PASS+1))
  else echo "FAIL  $name"; echo "      expected /$expect/, got: $(echo "$out"|tr '\n' ' '|cut -c1-220)"; FAIL=$((FAIL+1)); fi
}

echo "=== surgery-service V010 reoperation / REOPENED proof ==="
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
         V005__complication_pathway_and_prehab V006__longitudinal_objects_followup_specialty \
         V007__specialty_content_seed V008__analytics_indicator_catalogue \
         V010__surgical_episode_reoperation; do
  out=$(docker exec -i "$PG_NAME" psql -U impilo -d surgery -v ON_ERROR_STOP=1 < "$MIG/$f.sql" 2>&1)
  echo "$out" > "$EVIDENCE/$f.txt"
  chk "J-SB9-0 $f applies" "$(echo "$out" | grep -ci error || true)" "^0$"
done

UUID_RE='^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
new_episode() {
  # $1 = status. Returns the id, or leaves an error in the output for chk to catch.
  q "INSERT INTO surgery.surgical_episode (tenant_id, subject_cpid, journey_id, operative_indication, status)
     VALUES ('$TENANT', '$1', 'journey-$1', 'Symptomatic gallstones', '${2:-ASSESSMENT}')
     RETURNING id" | grep -Eio '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' | head -1
}

# ── The widened vocabulary ────────────────────────────────────────────────────────────
E1=$(new_episode CPID-REOP-1 OPERATED)
chk_re "J-SB9-1 an operated episode exists to reopen" "$E1" "$UUID_RE"

chk "J-SB9-2 REOPENED is an accepted status (it was not before V010)" \
  "$(q "UPDATE surgery.surgical_episode
        SET status='REOPENED', reopened_at=now(), reopened_by='actor-surgeon',
            reopen_reason='Postoperative haemorrhage — return to theatre'
        WHERE id='$E1'; SELECT status FROM surgery.surgical_episode WHERE id='$E1'")" \
  "REOPENED"

# Widening a CHECK by dropping and recreating it can silently lose the values it already had.
for s in ASSESSMENT LISTED_FOR_SURGERY OPERATED CLOSED ABANDONED; do
  q "UPDATE surgery.surgical_episode SET status='$s' WHERE id='$E1'" >/dev/null
  chk "J-SB9-3 status $s still accepted" "$(q "SELECT status FROM surgery.surgical_episode WHERE id='$E1'")" "^$s$"
done

chk "J-SB9-4 NEGATIVE an invented status is still refused by the CHECK" \
  "$(q "UPDATE surgery.surgical_episode SET status='PROBABLY_FINE' WHERE id='$E1'")" \
  "chk_surgical_episode_status"

# ── A reopen is audited or it did not happen ──────────────────────────────────────────
E2=$(new_episode CPID-REOP-2 OPERATED)

chk "J-SB9-5 NEGATIVE REOPENED with no audit at all is refused" \
  "$(q "UPDATE surgery.surgical_episode SET status='REOPENED' WHERE id='$E2'")" \
  "chk_surgical_episode_reopened_is_audited"

chk "J-SB9-6 NEGATIVE a half-recorded reopen (when, but not who or why) is refused" \
  "$(q "UPDATE surgery.surgical_episode SET reopened_at=now() WHERE id='$E2'")" \
  "chk_surgical_episode_reopen_audit_triple"

chk "J-SB9-7 NEGATIVE a reopen with a blank reason is refused" \
  "$(q "UPDATE surgery.surgical_episode
        SET reopened_at=now(), reopened_by='actor-surgeon', reopen_reason='   ' WHERE id='$E2'")" \
  "chk_surgical_episode_reopen_audit_triple"

chk "J-SB9-8 a complete reopen is accepted" \
  "$(q "UPDATE surgery.surgical_episode
        SET status='REOPENED', reopened_at=now(), reopened_by='actor-surgeon',
            reopen_reason='Anastomotic leak diagnosed on day 5'
        WHERE id='$E2'; SELECT reopen_reason FROM surgery.surgical_episode WHERE id='$E2'")" \
  "Anastomotic leak diagnosed on day 5"

# The continuity the demonstration is actually about: ONE episode across both operations.
chk "J-SB9-9 the reoperation is the SAME episode, not a second one" \
  "$(q "SELECT count(*) FROM surgery.surgical_episode WHERE subject_cpid='CPID-REOP-2'")" "^1$"

chk "J-SB9-10 moving on from REOPENED keeps the reopen on the record" \
  "$(q "UPDATE surgery.surgical_episode SET status='OPERATED' WHERE id='$E2';
        SELECT status || '/' || CASE WHEN reopened_at IS NOT NULL AND reopened_by = 'actor-surgeon'
                                     THEN 'AUDIT_KEPT' ELSE 'AUDIT_LOST' END
        FROM surgery.surgical_episode WHERE id='$E2'")" \
  "OPERATED/AUDIT_KEPT"

# ── Predecessor linkage, for the reoperation given its own episode ────────────────────
E3=$(new_episode CPID-REOP-3 ASSESSMENT)

chk "J-SB9-11 NEGATIVE an episode cannot be a reoperation of itself" \
  "$(q "UPDATE surgery.surgical_episode SET reoperation_of_episode_id='$E3' WHERE id='$E3'")" \
  "chk_surgical_episode_reoperation_not_self"

chk "J-SB9-12 NEGATIVE a predecessor that does not exist is refused by the foreign key" \
  "$(q "UPDATE surgery.surgical_episode
        SET reoperation_of_episode_id='11111111-1111-4111-8111-111111111111' WHERE id='$E3'")" \
  "fk_surgical_episode_reoperation_of"

chk "J-SB9-13 a real predecessor is linked" \
  "$(q "UPDATE surgery.surgical_episode SET reoperation_of_episode_id='$E2' WHERE id='$E3';
        SELECT reoperation_of_episode_id FROM surgery.surgical_episode WHERE id='$E3'")" \
  "$E2"

chk "J-SB9-14 the predecessor chain reads back in one hop" \
  "$(q "SELECT p.subject_cpid FROM surgery.surgical_episode c
        JOIN surgery.surgical_episode p ON p.id = c.reoperation_of_episode_id
        WHERE c.id='$E3'")" "^CPID-REOP-2$"

# ── Backward compatibility: nothing that worked before V010 stops working ─────────────
chk_re "J-SB9-15 an ordinary episode still needs no reoperation columns at all" \
  "$(new_episode CPID-REOP-ORDINARY ASSESSMENT)" "$UUID_RE"

chk "J-SB9-16 the partial predecessor index exists and excludes the ordinary case" \
  "$(q "SELECT indexdef FROM pg_indexes
        WHERE schemaname='surgery' AND indexname='idx_surgical_episode_reoperation_of'")" \
  "reoperation_of_episode_id IS NOT NULL"

echo
echo "PASS=$PASS FAIL=$FAIL"
{ echo "PASS=$PASS"; echo "FAIL=$FAIL"; } > "$EVIDENCE/summary.txt"
q "SELECT id, subject_cpid, status, reopened_by, reopen_reason, reoperation_of_episode_id
   FROM surgery.surgical_episode ORDER BY subject_cpid" > "$EVIDENCE/episodes.txt"
echo "evidence: $EVIDENCE"
[ "$FAIL" = 0 ]
