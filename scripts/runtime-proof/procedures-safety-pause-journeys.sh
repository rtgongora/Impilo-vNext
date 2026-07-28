#!/usr/bin/env bash
# Procedures pipeline P7 — safety-pause templates (§9) and sedation continuum (§10) runtime
# proof (real Postgres).
#
# Formalizes the 13 mutation-proof assertions run by hand against a fresh V001-V005 chain
# before the Java layer was written, so they are repeatable rather than a one-off session
# transcript. Same reason as procedures-catalogue-journeys.sh: H2 (module tests) does not
# enforce CHECK constraints, composite foreign keys, or partial unique indexes — anything
# asserted only there is asserted nowhere. SafetyPauseAndSedationServiceTest (H2) proves the
# service's read/resolve logic; this proves what only Postgres can enforce.
#
# Usage:  bash scripts/runtime-proof/procedures-safety-pause-journeys.sh
#         KEEP_RIG=1 ... to leave the container up.
set -uo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

PG_NAME="proc-sp-rig-$$"
PG_PORT="${PROC_RIG_PG_PORT:-25442}"
TENANT="00000000-0000-4000-8000-000000000001"
MIG="services/procedures-service/src/main/resources/db/migration"
EVIDENCE="reports/journeys/procedures-safety-pause-proof-$(date +%Y%m%d-%H%M%S)"

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

echo "=== procedures-service P7 safety-pause + sedation proof ==="
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
         V004__appropriateness_rules V005__safety_pause_and_sedation; do
  out=$(docker exec -i "$PG_NAME" psql -U impilo -d procedures -v ON_ERROR_STOP=1 < "$MIG/$f.sql" 2>&1)
  echo "$out" > "$EVIDENCE/$f.txt"
  chk "J-P7-0 $f applies" "$(echo "$out" | grep -ci error || true)" "^0$"
done

# ── §9 safety-pause templates ──
chk "J-P7-1 all ten class-specific templates are seeded and PUBLISHED" \
  "$(q "SELECT count(*) FROM procedures.safety_pause_template WHERE status='PUBLISHED'")" "^10$"

chk "J-P7-2 every published template names an approving authority" \
  "$(q "SELECT count(*) FROM procedures.safety_pause_template
        WHERE status='PUBLISHED' AND (approving_authority IS NULL OR btrim(approving_authority)='')")" "^0$"

# The irreducible minimum stated in the migration's own seed comment.
chk "J-P7-3 every template carries PATIENT, PROCEDURE and CONSENT confirmation items" \
  "$(q "SELECT CASE WHEN count(*) = 10 THEN 'ALL' ELSE 'MISSING' END FROM (
        SELECT t.id FROM procedures.safety_pause_template t
        WHERE (SELECT count(DISTINCT confirmation_item) FROM procedures.safety_pause_confirmation_item i
               WHERE i.template_id = t.id AND i.confirmation_item IN ('PATIENT','PROCEDURE','CONSENT')) = 3
      ) x")" "ALL"

chk "J-P7-4 an invented confirmation item is REFUSED" \
  "$(q "INSERT INTO procedures.safety_pause_confirmation_item (template_id, confirmation_item, prompt_text, display_order)
        SELECT id, 'NOT_A_REAL_ITEM', 'x', 999 FROM procedures.safety_pause_template LIMIT 1")" \
  "chk_safety_pause_confirmation_item"

chk "J-P7-5 publishing a template without an approving authority is REFUSED" \
  "$(q "INSERT INTO procedures.safety_pause_template
          (tenant_id, template_code, template_name, status)
        VALUES ('$TENANT','SAFETY-PAUSE-BOGUS','x','PUBLISHED')")" \
  "chk_safety_pause_template_published_authority"

# ── §10 sedation continuum ──
chk "J-P7-6 all eight sedation levels are seeded" \
  "$(q "SELECT count(*) FROM procedures.sedation_level")" "^8$"

chk "J-P7-7 an invented sedation level code is REFUSED" \
  "$(q "INSERT INTO procedures.sedation_level
          (tenant_id, level_code, level_label, depth_rank, monitoring_required, provider_competence_required)
        VALUES ('$TENANT','TWILIGHT_SEDATION','x',3,'m','c')")" \
  "chk_sedation_level_code"

# The rescue chain: each level below the top requires the NEXT level's capability, in both
# directions of measurement — checking the actual codes, not just that a code exists.
chk "J-P7-8 the rescue-capability chain resolves minimal to moderate" \
  "$(q "SELECT rescue_capability_level_code FROM procedures.sedation_level WHERE level_code='MINIMAL_SEDATION'")" \
  "^MODERATE_SEDATION$"

chk "J-P7-9 the rescue-capability chain resolves moderate to deep" \
  "$(q "SELECT rescue_capability_level_code FROM procedures.sedation_level WHERE level_code='MODERATE_SEDATION'")" \
  "^DEEP_SEDATION$"

chk "J-P7-10 deep and regional sedation both rescue to general anaesthesia" \
  "$(q "SELECT CASE WHEN count(*)=2 THEN 'BOTH' ELSE 'MISSING' END FROM procedures.sedation_level
        WHERE level_code IN ('DEEP_SEDATION','REGIONAL_ANAESTHESIA') AND rescue_capability_level_code='GENERAL_ANAESTHESIA'")" \
  "BOTH"

chk "J-P7-11 general anaesthesia sits at the top with no rescue target above it" \
  "$(q "SELECT rescue_capability_level_code FROM procedures.sedation_level WHERE level_code='GENERAL_ANAESTHESIA'")" \
  "^$"

chk "J-P7-12 an unknown rescue target is REFUSED by the composite foreign key" \
  "$(q "UPDATE procedures.sedation_level SET rescue_capability_level_code='NOT_A_REAL_LEVEL'
        WHERE level_code='NO_SEDATION'")" \
  "fk_sedation_level_rescue"

# depth_rank is deliberately not unique — parallel techniques, not a strict linear order.
chk "J-P7-13 depth_rank ties are permitted for parallel techniques (no unique constraint fired)" \
  "$(q "SELECT CASE WHEN count(*)=2 THEN 'TIED_OK' ELSE 'UNEXPECTED' END FROM procedures.sedation_level WHERE depth_rank=0")" \
  "TIED_OK"

# ── linkage: sedation_level_code on a requirement row is gated to SEDATION-kind requirements ──
chk "J-P7-14 setting sedation_level_code on a non-SEDATION requirement is REFUSED" \
  "$(q "INSERT INTO procedures.procedure_requirement
          (definition_id, requirement_kind, requirement_code, requirement_label, obligation, owner_role, sedation_level_code)
        SELECT id, 'CONSENT', 'REQ-X', 'x', 'MANDATORY', 'OWNER', 'MODERATE_SEDATION'
        FROM procedures.procedure_definition LIMIT 1")" \
  "chk_procedure_requirement_sedation_level_only_on_sedation_kind"

chk "J-P7-15 setting sedation_level_code on a SEDATION requirement IS permitted" \
  "$(q "INSERT INTO procedures.procedure_requirement
          (definition_id, requirement_kind, requirement_code, requirement_label, obligation, owner_role, sedation_level_code)
        SELECT id, 'SEDATION', 'REQ-SED-TEST', 'x', 'MANDATORY', 'OWNER', 'MODERATE_SEDATION'
        FROM procedures.procedure_definition LIMIT 1")" \
  "INSERT 0 1"

# ── invariants that must survive a new migration ──
chk "J-P7-16 engine-not-store still holds — no completion or encounter table added" \
  "$(q "SELECT coalesce(string_agg(tablename,','),'none') FROM pg_tables
        WHERE schemaname='procedures' AND (tablename LIKE '%completion%' OR tablename LIKE '%encounter%')")" \
  "^none$"

chk "J-P7-17 P1's site-and-side-never-overridable constraint still holds" \
  "$(q "UPDATE procedures.procedure_requirement SET overridable_in_emergency=true
        WHERE requirement_kind='SITE_SIDE_VERIFICATION'")" \
  "chk_procedure_requirement_site_side_never_overridable"

{ echo "procedures-service P7 safety-pause + sedation proof"; echo "generated: $(date -Is)"; echo "PASS=$PASS FAIL=$FAIL"; } > "$EVIDENCE/summary.txt"
echo
echo "P7 safety-pause + sedation proof: PASS=$PASS FAIL=$FAIL   evidence: $EVIDENCE"
[[ $FAIL -eq 0 ]] || exit 1
