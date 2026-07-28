#!/usr/bin/env bash
# Procedures pipeline P10 — Clavien-Dindo severity grades and complication profiles (§18)
# runtime proof (real Postgres).
#
# procedure_definition.complication_profile has existed since V002 with no seed value and no
# resolving table — one step earlier in the same "code with nothing to resolve to" state V005
# found for safety_pause_template (fixed by P7) and V007 found and fixed for aftercare_template
# (Wave P-R2, after P9 had missed it). This resolves complication_profile FROM THE START against
# the column that already exists, applying that lesson directly rather than needing a fourth
# fix-forward migration.
#
# H2 (module tests) does not enforce the CHECK constraints or the published-authority governance
# rule; this proves what only Postgres can enforce.
#
# Usage:  bash scripts/runtime-proof/procedures-complications-journeys.sh
#         KEEP_RIG=1 ... to leave the container up.
set -uo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

PG_NAME="proc-complications-rig-$$"
PG_PORT="${PROC_RIG_PG_PORT:-25452}"
TENANT="00000000-0000-4000-8000-000000000001"
MIG="services/procedures-service/src/main/resources/db/migration"
EVIDENCE="reports/journeys/procedures-complications-proof-$(date +%Y%m%d-%H%M%S)"

PASS=0; FAIL=0
cleanup() {
  if [[ "${KEEP_RIG:-0}" == "1" ]]; then echo "KEEP_RIG=1 — $PG_NAME left on $PG_PORT"
  else docker rm -f "$PG_NAME" >/dev/null 2>&1 || true; fi
}
trap cleanup EXIT

q() { docker exec "$PG_NAME" psql -U impilo -d procedures -tAc "$1" 2>&1; }
chk() {
  local name="$1" out="$2" expect="$3"
  if echo "$out" | grep -qi -- "$expect"; then echo "PASS  $name"; PASS=$((PASS+1))
  else echo "FAIL  $name"; echo "      expected /$expect/, got: $(echo "$out"|tr '\n' ' '|cut -c1-200)"; FAIL=$((FAIL+1)); fi
}

echo "=== procedures-service P10 complications proof ==="
mkdir -p "$EVIDENCE"
docker rm -f "$PG_NAME" >/dev/null 2>&1 || true
docker run -d --name "$PG_NAME" -e POSTGRES_PASSWORD=impilo -e POSTGRES_USER=impilo \
  -e POSTGRES_DB=procedures -p "${PG_PORT}:5432" postgres:16 >/dev/null || { echo "FAIL: postgres"; exit 1; }
echo -n "waiting for postgres"
for _ in $(seq 1 60); do
  docker exec "$PG_NAME" psql -U impilo -d procedures -tAc "select 1" >/dev/null 2>&1 && break
  echo -n "."; sleep 2
done; echo
docker exec "$PG_NAME" psql -U impilo -d procedures -tAc "select 1" >/dev/null 2>&1 \
  || { echo "FAIL: postgres never reachable"; exit 1; }

for f in V001__init V002__procedure_catalogue V003__procedure_catalogue_seed \
         V004__appropriateness_rules V005__safety_pause_and_sedation V006__recovery_and_aftercare \
         V007__aftercare_template_specific_codes V008__complication_profile; do
  out=$(docker exec -i "$PG_NAME" psql -U impilo -d procedures -v ON_ERROR_STOP=1 < "$MIG/$f.sql" 2>&1)
  echo "$out" > "$EVIDENCE/$f.txt"
  chk "J-P10-0 $f applies" "$(echo "$out" | grep -ci error || true)" "^0$"
done

# ── Clavien-Dindo grades — a real cited standard, not engineering-seed ──
chk "J-P10-1 all seven Clavien-Dindo grades are seeded" \
  "$(q "SELECT count(*) FROM procedures.clavien_dindo_grade")" "^7$"

chk "J-P10-2 an invented grade code is REFUSED" \
  "$(q "INSERT INTO procedures.clavien_dindo_grade
          (tenant_id, grade_code, grade_label, description, display_order, source_citation)
        VALUES ('$TENANT','VI','x','y',8,'z')")" \
  "chk_clavien_dindo_grade_code"

chk "J-P10-3 every grade cites the real standard, not a fabricated one" \
  "$(q "SELECT count(*) FROM procedures.clavien_dindo_grade WHERE source_citation NOT ILIKE '%Dindo%'")" "^0$"

# ── complication profiles ──
chk "J-P10-4 all five complication profiles are seeded and PUBLISHED" \
  "$(q "SELECT count(*) FROM procedures.complication_profile WHERE status='PUBLISHED'")" "^5$"

chk "J-P10-5 every published profile names an approving authority" \
  "$(q "SELECT count(*) FROM procedures.complication_profile
        WHERE status='PUBLISHED' AND (approving_authority IS NULL OR btrim(approving_authority)='')")" "^0$"

chk "J-P10-6 publishing a profile without an approving authority is REFUSED" \
  "$(q "INSERT INTO procedures.complication_profile (tenant_id, profile_code, profile_name, status)
        VALUES ('$TENANT','COMPLICATIONS-BOGUS','x','PUBLISHED')")" \
  "chk_complication_profile_published_authority"

chk "J-P10-7 every seeded profile is honestly flagged ENGINEERING_SEED (the taxonomy has no sourced standard)" \
  "$(q "SELECT count(*) FROM procedures.complication_profile WHERE content_maturity <> 'ENGINEERING_SEED'")" "^0$"

chk "J-P10-8 an invented content_maturity value is REFUSED" \
  "$(q "UPDATE procedures.complication_profile SET content_maturity='PROBABLY_FINE' WHERE profile_code='COMPLICATIONS-LAPAROTOMY'")" \
  "chk_complication_profile_content_maturity"

# ── complication classes ──
chk "J-P10-9 an invented complication type is REFUSED" \
  "$(q "INSERT INTO procedures.complication_class
          (profile_id, complication_type, monitoring_required, escalation_action)
        SELECT id, 'ALIEN_ABDUCTION', 'x', 'y' FROM procedures.complication_profile LIMIT 1")" \
  "chk_complication_class_type"

chk "J-P10-10 an invented typical_grade_code value is REFUSED" \
  "$(q "INSERT INTO procedures.complication_class
          (profile_id, complication_type, typical_grade_code, monitoring_required, escalation_action)
        SELECT id, 'BLEEDING', 'GRADE_X', 'x', 'y' FROM procedures.complication_profile LIMIT 1")" \
  "chk_complication_class_grade_vocabulary"

chk "J-P10-11 a NULL typical_grade_code IS permitted (a never-event has no 'typical' grade to default to)" \
  "$(q "INSERT INTO procedures.complication_class
          (profile_id, complication_type, typical_grade_code, monitoring_required, escalation_action, is_never_event)
        SELECT id, 'WRONG_SITE_PROCEDURE', NULL, 'x', 'y', true FROM procedures.complication_profile
        WHERE profile_code='COMPLICATIONS-BEDSIDE-LINE'")" "INSERT 0 1"

chk "J-P10-12 every seeded typical_grade_code actually resolves against a real Clavien-Dindo grade" \
  "$(q "SELECT count(*) FROM procedures.complication_class c
        WHERE c.typical_grade_code IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM procedures.clavien_dindo_grade g WHERE g.grade_code = c.typical_grade_code)")" \
  "^0$"

chk "J-P10-13 at least one never-event class is seeded, and it names no 'typical' grade to normalise around" \
  "$(q "SELECT count(*) FROM procedures.complication_class WHERE is_never_event AND complication_type='RETAINED_FOREIGN_OBJECT'")" "^[1-9]"

# ── linkage to the catalogue ──
chk "J-P10-14 at least ten catalogue entries declare a complication profile" \
  "$(q "SELECT CASE WHEN count(*) >= 10 THEN 'ENOUGH' ELSE 'TOO_FEW' END
        FROM procedures.procedure_definition WHERE complication_profile IS NOT NULL")" "ENOUGH"

chk "J-P10-15 every declared complication_profile resolves to a real PUBLISHED profile" \
  "$(q "SELECT count(*) FROM procedures.procedure_definition d
        WHERE d.complication_profile IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM procedures.complication_profile p
                          WHERE p.tenant_id = d.tenant_id AND p.profile_code = d.complication_profile
                            AND p.status = 'PUBLISHED')")" "^0$"

# ── invariants that must survive a new migration ──
chk "J-P10-16 engine-not-store still holds — no complication OCCURRENCE/EVENT/PATHWAY table added here" \
  "$(q "SELECT coalesce(string_agg(tablename,','),'none') FROM pg_tables
        WHERE schemaname='procedures' AND (tablename LIKE '%occurrence%' OR tablename LIKE '%pathway%'
          OR (tablename LIKE '%event%' AND tablename NOT LIKE '%outbox%'))")" "^none$"

chk "J-P10-17 P1's site-and-side-never-overridable constraint still holds" \
  "$(q "UPDATE procedures.procedure_requirement SET overridable_in_emergency=true
        WHERE requirement_kind='SITE_SIDE_VERIFICATION'")" \
  "chk_procedure_requirement_site_side_never_overridable"

{ echo "procedures-service P10 complications proof"; echo "generated: $(date -Is)"; echo "PASS=$PASS FAIL=$FAIL"; } > "$EVIDENCE/summary.txt"
echo
echo "P10 complications proof: PASS=$PASS FAIL=$FAIL   evidence: $EVIDENCE"
[[ $FAIL -eq 0 ]] || exit 1
