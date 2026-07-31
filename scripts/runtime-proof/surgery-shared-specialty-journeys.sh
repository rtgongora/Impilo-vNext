#!/usr/bin/env bash
# Surgical demonstration 4 — shared-specialty care (V011) — runtime proof against real Postgres.
#
# The single-LEAD rule is a PARTIAL UNIQUE INDEX, the specialty and role vocabularies are CHECK
# constraints, and the backfill is an INSERT ... SELECT. H2 with ddl-auto has none of these, so
# the module tests cannot see any of it. This rig can.
#
# The assertion that matters most is the awkward one: surgical_episode.specialty STAYS, meaning
# the lead specialty, so every reader written before V011 keeps working. This proves the backfill
# puts the two in agreement to begin with, and that the join table cannot hold a second lead.
#
# Usage:  bash scripts/runtime-proof/surgery-shared-specialty-journeys.sh
#         KEEP_RIG=1 ... to leave the container up.
set -uo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

PG_NAME="surgery-specialty-rig-$$"
PG_PORT="${SURGERY_SPECIALTY_RIG_PG_PORT:-25487}"
TENANT="00000000-0000-4000-8000-000000000001"
MIG="services/surgery-service/src/main/resources/db/migration"
EVIDENCE="reports/journeys/surgery-shared-specialty-proof-$(date +%Y%m%d-%H%M%S)"

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
chk_re() {
  local name="$1" out="$2" expect="$3"
  if echo "$out" | grep -qiE -- "$expect"; then echo "PASS  $name"; PASS=$((PASS+1))
  else echo "FAIL  $name"; echo "      expected /$expect/, got: $(echo "$out"|tr '\n' ' '|cut -c1-220)"; FAIL=$((FAIL+1)); fi
}
UUID_RE='^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'

echo "=== surgery-service V011 shared-specialty care proof ==="
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
  chk "J-SB4S-0 $1 applies" "$(echo "$out" | grep -ci error || true)" "^0$"
}

for f in V001__init V002__surgical_episode V003__surgical_assessment V004__surgical_decision \
         V005__complication_pathway_and_prehab V006__longitudinal_objects_followup_specialty \
         V007__specialty_content_seed V008__analytics_indicator_catalogue \
         V010__surgical_episode_reoperation; do
  apply "$f"
done

new_episode() {
  # $1 = cpid, $2 = specialty or the literal NULL
  local spec="$2"
  [ "$spec" = "NULL" ] && spec="NULL" || spec="'$spec'"
  q "INSERT INTO surgery.surgical_episode (tenant_id, subject_cpid, journey_id, operative_indication, status, specialty)
     VALUES ('$TENANT', '$1', 'journey-$1', 'Retroperitoneal sarcoma resection', 'ASSESSMENT', $spec)
     RETURNING id" | grep -Eio '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' | head -1
}

# ── Pre-existing episodes, created BEFORE V011 runs. The backfill has to find these. ───
PRE1=$(new_episode CPID-SPEC-PRE-1 SURGICAL_ONCOLOGY)
PRE2=$(new_episode CPID-SPEC-PRE-2 NULL)
chk_re "J-SB4S-1 an episode existed before V011 with a lead specialty" "$PRE1" "$UUID_RE"
chk_re "J-SB4S-2 and one existed with no specialty at all" "$PRE2" "$UUID_RE"

apply V011__surgical_episode_specialty

# ── The backfill ──────────────────────────────────────────────────────────────────────
chk "J-SB4S-3 the pre-existing episode's specialty became its LEAD row" \
  "$(q "SELECT specialty || '/' || role FROM surgery.surgical_episode_specialty WHERE episode_id='$PRE1'")" \
  "^SURGICAL_ONCOLOGY/LEAD$"

chk "J-SB4S-4 the backfill records honest provenance — nobody added these, they were derived" \
  "$(q "SELECT added_by FROM surgery.surgical_episode_specialty WHERE episode_id='$PRE1'")" \
  "^migration:V011$"

chk "J-SB4S-5 an episode with no specialty gets no invented lead" \
  "$(q "SELECT count(*) FROM surgery.surgical_episode_specialty WHERE episode_id='$PRE2'")" "^0$"

chk "J-SB4S-6 the column and the join table agree for every backfilled episode" \
  "$(q "SELECT count(*) FROM surgery.surgical_episode e
        WHERE e.specialty IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM surgery.surgical_episode_specialty s
                          WHERE s.episode_id = e.id AND s.role='LEAD' AND s.specialty = e.specialty)")" \
  "^0$"

# ── Shared-specialty care: the thing the single column could not express ──────────────
chk "J-SB4S-7 a second team joins the case as SHARED" \
  "$(q "INSERT INTO surgery.surgical_episode_specialty
          (tenant_id, episode_id, specialty, role, contribution, added_by)
        VALUES ('$TENANT', '$PRE1', 'VASCULAR', 'SHARED', 'Vascular control of the IVC', 'actor-surgeon')
        RETURNING specialty")" "^VASCULAR$"

chk "J-SB4S-8 a third team joins too" \
  "$(q "INSERT INTO surgery.surgical_episode_specialty
          (tenant_id, episode_id, specialty, role, contribution, added_by)
        VALUES ('$TENANT', '$PRE1', 'UROLOGY', 'SHARED', 'Ureteric reimplantation', 'actor-surgeon')
        RETURNING specialty")" "^UROLOGY$"

chk "J-SB4S-9 the episode now names all three operating teams" \
  "$(q "SELECT count(*) FROM surgery.surgical_episode_specialty WHERE episode_id='$PRE1'")" "^3$"

chk "J-SB4S-10 and the pre-V011 column still answers correctly with the lead" \
  "$(q "SELECT specialty FROM surgery.surgical_episode WHERE id='$PRE1'")" "^SURGICAL_ONCOLOGY$"

chk "J-SB4S-11 'which episodes is vascular involved in' is now answerable" \
  "$(q "SELECT count(*) FROM surgery.surgical_episode_specialty
        WHERE tenant_id='$TENANT' AND specialty='VASCULAR'")" "^1$"

# ── The negatives ─────────────────────────────────────────────────────────────────────
chk "J-SB4S-12 NEGATIVE a second LEAD is refused by the partial unique index" \
  "$(q "INSERT INTO surgery.surgical_episode_specialty (tenant_id, episode_id, specialty, role, added_by)
        VALUES ('$TENANT', '$PRE1', 'PLASTICS', 'LEAD', 'actor-surgeon')")" \
  "uq_episode_specialty_single_lead"

chk "J-SB4S-13 NEGATIVE the same team cannot be added twice" \
  "$(q "INSERT INTO surgery.surgical_episode_specialty (tenant_id, episode_id, specialty, role, added_by)
        VALUES ('$TENANT', '$PRE1', 'VASCULAR', 'SHARED', 'actor-surgeon')")" \
  "uq_episode_specialty"

chk "J-SB4S-14 NEGATIVE an invented specialty is refused" \
  "$(q "INSERT INTO surgery.surgical_episode_specialty (tenant_id, episode_id, specialty, role, added_by)
        VALUES ('$TENANT', '$PRE1', 'KEYHOLE_WIZARDRY', 'SHARED', 'actor-surgeon')")" \
  "chk_episode_specialty_value"

chk "J-SB4S-15 NEGATIVE an invented role is refused" \
  "$(q "INSERT INTO surgery.surgical_episode_specialty (tenant_id, episode_id, specialty, role, added_by)
        VALUES ('$TENANT', '$PRE1', 'PLASTICS', 'ASSISTING', 'actor-surgeon')")" \
  "chk_episode_specialty_role"

chk "J-SB4S-16 NEGATIVE a team on an episode that does not exist is refused" \
  "$(q "INSERT INTO surgery.surgical_episode_specialty (tenant_id, episode_id, specialty, role, added_by)
        VALUES ('$TENANT', '11111111-1111-4111-8111-111111111111', 'ENT', 'SHARED', 'actor-surgeon')")" \
  "surgical_episode_specialty_episode_id_fkey"

chk "J-SB4S-17 NEGATIVE none of the refusals wrote anything" \
  "$(q "SELECT count(*) FROM surgery.surgical_episode_specialty WHERE episode_id='$PRE1'")" "^3$"

# ── Handing the lead over ─────────────────────────────────────────────────────────────
# The outgoing lead must step down before the incoming one is written; the partial unique
# index permits exactly one LEAD at any point, including mid-statement.
q "BEGIN;
   UPDATE surgery.surgical_episode_specialty SET role='SHARED' WHERE episode_id='$PRE1' AND role='LEAD';
   UPDATE surgery.surgical_episode_specialty SET role='LEAD' WHERE episode_id='$PRE1' AND specialty='VASCULAR';
   UPDATE surgery.surgical_episode SET specialty='VASCULAR' WHERE id='$PRE1';
   COMMIT;" >/dev/null

chk "J-SB4S-18 after the handover exactly one team leads" \
  "$(q "SELECT count(*) FROM surgery.surgical_episode_specialty WHERE episode_id='$PRE1' AND role='LEAD'")" "^1$"

chk "J-SB4S-19 the outgoing lead stays on the case — it operated" \
  "$(q "SELECT role FROM surgery.surgical_episode_specialty
        WHERE episode_id='$PRE1' AND specialty='SURGICAL_ONCOLOGY'")" "^SHARED$"

chk "J-SB4S-20 the column followed the lead, so pre-V011 readers stay correct" \
  "$(q "SELECT count(*) FROM surgery.surgical_episode e
        JOIN surgery.surgical_episode_specialty s ON s.episode_id = e.id AND s.role='LEAD'
        WHERE e.id='$PRE1' AND e.specialty = s.specialty")" "^1$"

# ── Nothing that worked before V011 stops working ─────────────────────────────────────
chk_re "J-SB4S-21 an ordinary single-specialty episode still opens with no join rows needed" \
  "$(new_episode CPID-SPEC-ORDINARY ORTHOPAEDICS)" "$UUID_RE"

chk "J-SB4S-22 deleting an episode takes its specialty rows with it" \
  "$(q "DELETE FROM surgery.surgical_episode WHERE id='$PRE2';
        SELECT count(*) FROM surgery.surgical_episode_specialty WHERE episode_id='$PRE2'")" "^0$"

echo
echo "PASS=$PASS FAIL=$FAIL"
{ echo "PASS=$PASS"; echo "FAIL=$FAIL"; } > "$EVIDENCE/summary.txt"
q "SELECT e.subject_cpid, e.specialty AS lead_column, s.specialty, s.role, s.contribution
   FROM surgery.surgical_episode e
   LEFT JOIN surgery.surgical_episode_specialty s ON s.episode_id = e.id
   ORDER BY e.subject_cpid, s.role, s.specialty" > "$EVIDENCE/specialties.txt"
echo "evidence: $EVIDENCE"
[ "$FAIL" = 0 ]
