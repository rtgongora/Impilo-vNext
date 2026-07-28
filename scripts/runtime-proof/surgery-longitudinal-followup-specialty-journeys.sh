#!/usr/bin/env bash
# Surgery pipeline SB-2 — longitudinal objects (§17), discharge/follow-up (§18), waiting-list
# revalidation (§9 slice), and the specialty dimension (§6), against REAL Postgres.
#
# Implants stay federated to inventory-service (P8) — this rig proves NOTHING about implants
# exists here beyond an opaque reference column. The specialty vocabulary is hardened to the
# fifteen ZIBO-coded values (zibo-service V300, same batch); the two content tables
# (surgical_specialty_indication / surgical_operative_template) are proven EMPTY-but-ready —
# content lands via a separate integration step, never fabricated by this migration.
#
# Usage:  bash scripts/runtime-proof/surgery-longitudinal-followup-specialty-journeys.sh
#         KEEP_RIG=1 ... to leave the container up.
set -uo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

PG_NAME="surgery-sb2-rig-$$"
PG_PORT="${SURGERY_RIG_PG_PORT:-25465}"
TENANT="00000000-0000-4000-8000-000000000001"
MIG="services/surgery-service/src/main/resources/db/migration"
EVIDENCE="reports/journeys/surgery-sb2-proof-$(date +%Y%m%d-%H%M%S)"

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

echo "=== surgery-service SB-2 longitudinal+followup+specialty proof ==="
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
         V007__specialty_content_seed; do
  out=$(docker exec -i "$PG_NAME" psql -U impilo -d surgery -v ON_ERROR_STOP=1 < "$MIG/$f.sql" 2>&1)
  echo "$out" > "$EVIDENCE/$f.txt"
  chk "J-SB2-0 $f applies" "$(echo "$out" | grep -ci error || true)" "^0$"
done

EPISODE=$(q "INSERT INTO surgery.surgical_episode (tenant_id, subject_cpid, journey_id, operative_indication, specialty)
             VALUES ('$TENANT','CPID-1','journey-1','Symptomatic gallstones','GENERAL_SURGERY') RETURNING id" | head -1)

# ── §6 specialty vocabulary ──
chk "J-SB2-1 an invented specialty on the episode is REFUSED" \
  "$(q "INSERT INTO surgery.surgical_episode (tenant_id, subject_cpid, journey_id, operative_indication, specialty)
        VALUES ('$TENANT','CPID-2','journey-2','x','ASTROLOGY_SURGERY')")" \
  "chk_surgical_episode_specialty"

chk "J-SB2-2 every one of the fifteen surgical specialties is genuinely INSERTABLE" \
  "$(q "WITH s(specialty) AS (VALUES
          ('GENERAL_SURGERY'),('ORTHOPAEDICS'),('UROLOGY'),('ENT'),('OPHTHALMOLOGY'),
          ('COLORECTAL'),('UPPER_GI_HPB'),('BREAST_ENDOCRINE'),('VASCULAR'),('NEUROSURGERY'),
          ('CARDIOTHORACIC'),('MAXILLOFACIAL'),('PLASTICS'),('PAEDIATRIC_SURGERY'),('SURGICAL_ONCOLOGY')
        )
        INSERT INTO surgery.surgical_episode (tenant_id, subject_cpid, journey_id, operative_indication, specialty)
        SELECT '$TENANT', 'CPID-SPEC-' || row_number() OVER (), 'journey-spec-' || row_number() OVER (),
               'Specialty vocabulary probe', specialty
        FROM s")" \
  "INSERT 0 15"

# ── §17 longitudinal objects ──
chk "J-SB2-3 an invented longitudinal object kind is REFUSED" \
  "$(q "INSERT INTO surgery.surgical_longitudinal_object (tenant_id, surgical_episode_id, kind, site, operator, indication)
        VALUES ('$TENANT','$EPISODE','PACEMAKER','x','dr-x','y')")" \
  "chk_longitudinal_object_kind"

DRAIN=$(q "INSERT INTO surgery.surgical_longitudinal_object
             (tenant_id, surgical_episode_id, kind, site, operator, indication)
           VALUES ('$TENANT','$EPISODE','DRAIN','Subhepatic','dr-x','Bile leak risk') RETURNING id" | head -1)

chk "J-SB2-4 status REMOVED with no removed_at/by is REFUSED (status-consistency)" \
  "$(q "UPDATE surgery.surgical_longitudinal_object SET status='REMOVED' WHERE id='$DRAIN'")" \
  "chk_longitudinal_object_status_consistency"

chk "J-SB2-5 removed_at with no removed_by (an incomplete pair) is REFUSED" \
  "$(q "UPDATE surgery.surgical_longitudinal_object SET removed_at=now() WHERE id='$DRAIN'")" \
  "chk_longitudinal_object_removal_pair"

chk "J-SB2-6 status REVISED with no revised_to_object_id is REFUSED" \
  "$(q "UPDATE surgery.surgical_longitudinal_object
        SET status='REVISED', removed_at=now(), removed_by='dr-x' WHERE id='$DRAIN'")" \
  "chk_longitudinal_object_revision_needs_target"

chk "J-SB2-7 a complete removal (status+removed_at+removed_by all together) is accepted" \
  "$(q "UPDATE surgery.surgical_longitudinal_object
        SET status='REMOVED', removed_at=now(), removed_by='dr-x', removal_reason='Output negligible'
        WHERE id='$DRAIN'")" \
  "UPDATE 1"

# ── §18 follow-up ──
FOLLOWUP_EP=$(q "INSERT INTO surgery.surgical_episode (tenant_id, subject_cpid, journey_id, operative_indication)
                 VALUES ('$TENANT','CPID-3','journey-3','Inguinal hernia repair') RETURNING id" | head -1)

chk "J-SB2-8 a follow-up record with a fit note but no issuer is REFUSED" \
  "$(q "INSERT INTO surgery.surgical_followup (tenant_id, surgical_episode_id, fit_note_notes)
        VALUES ('$TENANT','$FOLLOWUP_EP','Unfit for manual work 4 weeks')")" \
  "chk_surgical_followup_fitnote_pair"

chk "J-SB2-9 a complete follow-up record is accepted" \
  "$(q "INSERT INTO surgery.surgical_followup
          (tenant_id, surgical_episode_id, restrictions, fit_note_notes, fit_note_issued_at, fit_note_issued_by)
        VALUES ('$TENANT','$FOLLOWUP_EP','No heavy lifting 6 weeks','Unfit for manual work 4 weeks', now(), 'dr-x')")" \
  "INSERT 0 1"

chk "J-SB2-10 a second follow-up row for the SAME episode is REFUSED — refine, never duplicate" \
  "$(q "INSERT INTO surgery.surgical_followup (tenant_id, surgical_episode_id, restrictions)
        VALUES ('$TENANT','$FOLLOWUP_EP','duplicate row')")" \
  "uq_surgical_followup_episode"

# ── §9 waiting-list revalidation (append-only) ──
chk "J-SB2-11 a waitlist revalidation is accepted" \
  "$(q "INSERT INTO surgery.surgical_waitlist_revalidation
          (tenant_id, surgical_episode_id, revalidated_by, still_clinically_appropriate)
        VALUES ('$TENANT','$EPISODE','dr-x', true)")" \
  "INSERT 0 1"

chk "J-SB2-12 a second revalidation on the SAME episode is a NEW row (append-only, not refused)" \
  "$(q "INSERT INTO surgery.surgical_waitlist_revalidation
          (tenant_id, surgical_episode_id, revalidated_by, still_clinically_appropriate)
        VALUES ('$TENANT','$EPISODE','dr-y', false)")" \
  "INSERT 0 1"

chk "J-SB2-13 both revalidation rows for the episode are visible" \
  "$(q "SELECT count(*) FROM surgery.surgical_waitlist_revalidation WHERE surgical_episode_id='$EPISODE'")" \
  "^2$"

# ── §6 specialty content tables: empty but ready ──
chk "J-SB2-14 an invented specialty on an indication row is REFUSED" \
  "$(q "INSERT INTO surgery.surgical_specialty_indication
          (tenant_id, specialty, indication_code, condition_display)
        VALUES ('$TENANT','ASTROLOGY_SURGERY','IND-X-01','x')")" \
  "chk_specialty_indication_specialty"

chk "J-SB2-15 an invalid wound_classification on a template row is REFUSED" \
  "$(q "INSERT INTO surgery.surgical_operative_template
          (tenant_id, specialty, template_code, template_name, wound_classification)
        VALUES ('$TENANT','GENERAL_SURGERY','TPL-X-01','x','SUPER_DIRTY')")" \
  "chk_operative_template_wound_class"

# SB-4's content fan-out landed as V007 in this same migration chain: 118 indications + 75
# operative templates across all fifteen specialties (see V007's own header for the content
# rationale and the one honest gap it names — CKP rule integration, deferred).
chk "J-SB2-16 all fifteen specialties have at least one seeded indication" \
  "$(q "SELECT count(DISTINCT specialty) FROM surgery.surgical_specialty_indication")" \
  "^15$"

chk "J-SB2-16b every seeded indication and template is honestly flagged ENGINEERING_SEED" \
  "$(q "SELECT (SELECT count(*) FROM surgery.surgical_specialty_indication WHERE content_maturity <> 'ENGINEERING_SEED')
             + (SELECT count(*) FROM surgery.surgical_operative_template WHERE content_maturity <> 'ENGINEERING_SEED')")" \
  "^0$"

# ── CC-2 regression guard: implants stay federated, never copied here ──
chk "J-SB2-17 no column on surgical_longitudinal_object stores implant device data directly" \
  "$(q "SELECT coalesce(string_agg(column_name,','),'none') FROM information_schema.columns
        WHERE table_schema='surgery' AND table_name='surgical_longitudinal_object' AND (
          column_name LIKE '%udi%' OR column_name LIKE '%lot_number%' OR column_name LIKE '%manufacturer%')")" \
  "^none$"

echo
echo "=== SB-2 proof: $PASS passed, $FAIL failed ==="
[[ $FAIL -eq 0 ]] || exit 1
