#!/usr/bin/env bash
# Procedures pipeline P1 — canonical catalogue runtime proof (real Postgres).
#
# The catalogue is the keystone: §5 appropriateness, §7 competence, §8 readiness, §9 safety
# pauses and §17 aftercare are all functions of it. This proves the properties those waves
# will depend on, and proves them where they are actually enforced — Postgres. The module
# tests run on H2, which does not honour the partial unique index, does not enforce the CHECK
# constraints, and mishandles JSON; anything asserted only there is asserted nowhere.
#
# Usage:  bash scripts/runtime-proof/procedures-catalogue-journeys.sh
#         KEEP_RIG=1 ... to leave the container up.
set -uo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

PG_NAME="proc-cat-rig-$$"
PG_PORT="${PROC_RIG_PG_PORT:-25441}"
TENANT="00000000-0000-4000-8000-000000000001"
MIG="services/procedures-service/src/main/resources/db/migration"
EVIDENCE="reports/journeys/procedures-catalogue-proof-$(date +%Y%m%d-%H%M%S)"

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

echo "=== procedures-service P1 catalogue proof ==="
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

for f in V001__init V002__procedure_catalogue V003__procedure_catalogue_seed; do
  out=$(docker exec -i "$PG_NAME" psql -U impilo -d procedures -v ON_ERROR_STOP=1 < "$MIG/$f.sql" 2>&1)
  echo "$out" > "$EVIDENCE/$f.txt"
  chk "J-P1-0 $f applies" "$(echo "$out" | grep -ci error || true)" "^0$"
done

# Threshold asserted in SQL rather than by regex alternation: chk() greps with a basic
# regular expression, so "^(9|1[0-9])$" matches literally and never fires. Every threshold in
# this rig is therefore expressed as a CASE returning a word.
chk "J-P1-1 catalogue seeded across at least nine procedure classes" \
  "$(q "SELECT CASE WHEN count(DISTINCT category) >= 9 THEN 'ENOUGH' ELSE 'TOO_FEW' END
        FROM procedures.procedure_definition")" "ENOUGH"

chk "J-P1-2 every definition declares an owning specialty and a purpose" \
  "$(q "SELECT count(*) FROM procedures.procedure_definition WHERE owning_specialty IS NULL OR purpose IS NULL")" "^0$"

# The keystone property: requirements are rows, so the readiness engine iterates rather than
# knowing names. If this collapses to a handful of kinds, the model has degenerated back to
# columns wearing a table's clothes.
chk "J-P1-3 requirements are rows spanning many kinds, not a fixed set of columns" \
  "$(q "SELECT CASE WHEN count(DISTINCT requirement_kind) >= 15 THEN 'ENOUGH' ELSE 'TOO_FEW' END FROM procedures.procedure_requirement")" \
  "ENOUGH"

# §8: every unresolved item must have an owner. A blocker nobody owns is never cleared.
chk "J-P1-4 every requirement names an owner" \
  "$(q "SELECT count(*) FROM procedures.procedure_requirement WHERE owner_role IS NULL OR btrim(owner_role)=''")" "^0$"

# ── The site-and-side guarantee. This is the finding the audit rated most serious. ──
chk "J-P1-5 every lateralised or multi-site procedure requires site and side verification" \
  "$(q "SELECT count(*) FROM procedures.procedure_definition d
        WHERE d.laterality_applicability IN ('LATERALISED','MULTI_SITE')
          AND NOT EXISTS (SELECT 1 FROM procedures.procedure_requirement r
                          WHERE r.definition_id=d.id AND r.requirement_kind='SITE_SIDE_VERIFICATION')")" \
  "^0$"

chk "J-P1-6 no site-and-side requirement is overridable in an emergency (seeded state)" \
  "$(q "SELECT count(*) FROM procedures.procedure_requirement
        WHERE requirement_kind='SITE_SIDE_VERIFICATION' AND overridable_in_emergency")" "^0$"

# …and the constraint refuses to let one become overridable. The seeded state above could be
# corrected by hand; this cannot. The emergency lane's lateralised procedures — chest drain,
# thoracostomy, central line, thoracotomy, escharotomy, burr hole — are done at speed by a
# lone clinician with no second checker, which is exactly where wrong-side harm happens.
chk "J-P1-7 making site-and-side overridable is REFUSED by the schema" \
  "$(q "UPDATE procedures.procedure_requirement SET overridable_in_emergency=true
        WHERE requirement_kind='SITE_SIDE_VERIFICATION'")" \
  "chk_procedure_requirement_site_side_never_overridable"

chk "J-P1-8 a conditional requirement without a condition is REFUSED" \
  "$(q "INSERT INTO procedures.procedure_requirement
          (definition_id,requirement_kind,requirement_code,requirement_label,obligation,owner_role)
        SELECT id,'LABORATORY','LAB-X','x','CONDITIONAL','OWNER' FROM procedures.procedure_definition LIMIT 1")" \
  "chk_procedure_requirement_condition"

# Governance: publishing without a named authority is refused, so unratified content cannot
# acquire the appearance of ratification by leaving the field empty.
chk "J-P1-9 publishing without an approving authority is REFUSED" \
  "$(q "INSERT INTO procedures.procedure_definition
          (tenant_id,definition_code,version,clinical_name,category,owning_specialty,purpose,status)
        VALUES ('$TENANT','PROC-NO-AUTHORITY',1,'x','THEATRE','SURGERY','THERAPEUTIC','PUBLISHED')")" \
  "chk_procedure_definition_published_authority"

chk "J-P1-10 all seeded content declares itself unratified" \
  "$(q "SELECT count(*) FROM procedures.procedure_definition
        WHERE status='PUBLISHED' AND approving_authority <> 'PENDING_MOHCC_RATIFICATION'")" "^0$"

# Two current versions would mean two answers to "what does this procedure require".
chk "J-P1-11 a second PUBLISHED version of the same code is REFUSED" \
  "$(q "INSERT INTO procedures.procedure_definition
          (tenant_id,definition_code,version,clinical_name,category,owning_specialty,purpose,
           status,approving_authority,approved_at)
        VALUES ('$TENANT','PROC-LAPAROTOMY',2,'x','THEATRE','SURGERY','THERAPEUTIC',
                'PUBLISHED','PENDING_MOHCC_RATIFICATION',now())")" \
  "uq_procedure_definition_one_published"

chk "J-P1-12 a DRAFT second version IS permitted — amendment is a new version, not an overwrite" \
  "$(q "INSERT INTO procedures.procedure_definition
          (tenant_id,definition_code,version,clinical_name,category,owning_specialty,purpose,status)
        VALUES ('$TENANT','PROC-LAPAROTOMY',2,'Exploratory laparotomy v2','THEATRE','SURGERY',
                'THERAPEUTIC','DRAFT')")" \
  "INSERT 0 1"

# JSON round-trip on real Postgres — the thing H2 cannot prove. A jsonb column bound as
# varchar fails here with SQLSTATE 42804, which is how this defect reached production before.
chk "J-P1-13 jsonb columns hold real arrays, not quoted strings" \
  "$(q "SELECT jsonb_array_length(permitted_settings) FROM procedures.procedure_definition
        WHERE definition_code='PROC-LUMBAR-PUNCTURE'")" "^4$"

# Honesty about coding: no fabricated SNOMED codes. Sixty unverified concept ids that look
# authoritative are worse than sixty nulls — a null is a gap somebody fills.
chk "J-P1-14 no unverified SNOMED codes were shipped" \
  "$(q "SELECT count(*) FROM procedures.procedure_definition WHERE snomed_ct_code IS NOT NULL")" "^0$"

chk "J-P1-15 ICD-9-CM codes present only where zibo V004 already publishes them" \
  "$(q "SELECT count(*) FROM procedures.procedure_definition
        WHERE icd9cm_procedure_code IS NOT NULL
          AND icd9cm_procedure_code NOT IN ('47.0','47.01','51.23','53.00','53.10','54.11','68.49','74.1','79.36','85.41')")" \
  "^0$"

# Engine-not-store still holds after the catalogue landed.
chk "J-P1-16 engine-not-store — still no readiness or checklist table in this schema" \
  "$(q "SELECT coalesce(string_agg(tablename,','),'none') FROM pg_tables
        WHERE schemaname='procedures' AND (tablename LIKE '%readiness%' OR tablename LIKE '%checklist%')")" \
  "^none$"

{ echo "procedures-service P1 catalogue proof"; echo "generated: $(date -Is)"; echo "PASS=$PASS FAIL=$FAIL"; } > "$EVIDENCE/summary.txt"
echo
echo "P1 catalogue proof: PASS=$PASS FAIL=$FAIL   evidence: $EVIDENCE"
[[ $FAIL -eq 0 ]] || exit 1
