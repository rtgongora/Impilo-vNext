#!/usr/bin/env bash
# Procedures pipeline P1 — canonical catalogue runtime proof (real Postgres). Extended for
# P7-P11 (all of which write into or read the catalogue's own tables).
#
# The catalogue is the keystone: §5 appropriateness, §7 competence, §8 readiness, §9 safety
# pauses and §17 aftercare are all functions of it. This proves the properties those waves
# will depend on, and proves them where they are actually enforced — Postgres. The module
# tests run on H2, which does not honour the partial unique index, does not enforce the CHECK
# constraints, and mishandles JSON; anything asserted only there is asserted nowhere.
#
# P11 ADDENDUM: closes §21 (IPC + sterile processing) as CONTENT — real procedure_requirement
# rows (requirement_kind IPC/STERILE_PROCESSING, both valid since V002) rather than a new
# reference table, because the standard's own revisitCondition already named this mechanism:
# "infection-prevention readiness as catalogue requirements with named owners per unresolved
# item." No new Java/schema; this rig belongs here rather than a separate P11 rig file because
# the content lives entirely in the catalogue's own procedure_requirement table.
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

for f in V001__init V002__procedure_catalogue V003__procedure_catalogue_seed V004__appropriateness_rules \
         V005__safety_pause_and_sedation V006__recovery_and_aftercare V007__aftercare_template_specific_codes \
         V008__complication_profile V009__ipc_requirement_depth; do
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

# ── P3 appropriateness inputs (§5). Content, not engine: a duplication window that changes
# because a committee decided so must be a content release, not a deployment.
chk "J-P3-1 repeat windows declared only where a repeat is a real harm or waste" \
  "$(q "SELECT CASE WHEN count(*) BETWEEN 8 AND 30 THEN 'SOME' ELSE 'SUSPICIOUS' END
        FROM procedures.procedure_definition WHERE duplicate_lookback_days IS NOT NULL")" "SOME"

# A defaulted window would manufacture duplicate warnings on every procedure, and a warning
# nobody believes is worse than no warning. Undeclared must stay undeclared.
chk "J-P3-2 most procedures have NO declared window and are honestly undeclared" \
  "$(q "SELECT CASE WHEN count(*) > 30 THEN 'UNDECLARED' ELSE 'OVER_DEFAULTED' END
        FROM procedures.procedure_definition WHERE duplicate_lookback_days IS NULL")" "UNDECLARED"

chk "J-P3-3 conflicts are symmetric where declared" \
  "$(q "SELECT count(*) FROM procedures.procedure_definition a
        WHERE jsonb_array_length(a.conflicts_with) > 0
          AND EXISTS (SELECT 1 FROM jsonb_array_elements_text(a.conflicts_with) c
                      WHERE NOT EXISTS (SELECT 1 FROM procedures.procedure_definition b
                                        WHERE b.definition_code = c
                                          AND b.conflicts_with ? a.definition_code))")" "^0$"

chk "J-P3-4 every declared prerequisite names a procedure that exists" \
  "$(q "SELECT count(*) FROM procedures.procedure_definition d,
             jsonb_array_elements_text(d.prerequisite_codes) p
        WHERE NOT EXISTS (SELECT 1 FROM procedures.procedure_definition x WHERE x.definition_code = p)")" "^0$"

# ── P11 §21 — IPC requirement depth. The four standards this domain's own
# coverage-exclusions.json committed to P11 at Wave 0.4, closed as real catalogue requirements
# rather than a generic sterile checkbox. ──
chk "J-P11-1 hand hygiene is a real requirement on every one of the 66 published procedures" \
  "$(q "SELECT count(*) FROM procedures.procedure_requirement WHERE requirement_code='IPC-HAND_HYGIENE'")" "^66$"

chk "J-P11-2 aseptic technique now covers the whole catalogue, not just V003's original two procedures" \
  "$(q "SELECT count(*) FROM procedures.procedure_requirement WHERE requirement_code='IPC-ASEPTIC'")" "^66$"

chk "J-P11-3 injection safety (single-use, exposure incident, waste) is declared on every procedure" \
  "$(q "SELECT count(*) FROM procedures.procedure_requirement WHERE requirement_code='IPC-INJECTION_SAFETY'")" "^66$"

chk "J-P11-4 PPE and skin preparation are both real requirements, not folded into one vague row" \
  "$(q "SELECT CASE WHEN count(DISTINCT requirement_code) = 2 THEN 'BOTH' ELSE 'MISSING' END
        FROM procedures.procedure_requirement WHERE requirement_code IN ('IPC-PPE','IPC-SKIN_PREP')")" "BOTH"

chk "J-P11-5 sterile-reprocessing limits are scoped to THEATRE/ENDOSCOPY, not applied to every bedside procedure" \
  "$(q "SELECT count(*) FROM procedures.procedure_requirement r
        JOIN procedures.procedure_definition d ON d.id = r.definition_id
        WHERE r.requirement_code = 'CSSD-REPROCESSING_LIMITS'
          AND NOT (d.permitted_settings ? 'THEATRE' OR d.permitted_settings ? 'ENDOSCOPY')")" "^0$"

chk "J-P11-6 V003's original two IPC-ASEPTIC rows were left untouched, not duplicated" \
  "$(q "SELECT count(*) FROM procedures.procedure_requirement r
        JOIN procedures.procedure_definition d ON d.id = r.definition_id
        WHERE r.requirement_code = 'IPC-ASEPTIC' AND d.definition_code IN ('PROC-LUMBAR-PUNCTURE','PROC-CHEST-DRAIN')
        GROUP BY d.definition_code HAVING count(*) > 1")" "^$"

chk "J-P11-7 every new IPC/STERILE_PROCESSING row names an owner, same discipline as every other requirement" \
  "$(q "SELECT count(*) FROM procedures.procedure_requirement
        WHERE requirement_code IN ('IPC-HAND_HYGIENE','IPC-PPE','IPC-SKIN_PREP','IPC-INJECTION_SAFETY','CSSD-REPROCESSING_LIMITS')
          AND (owner_role IS NULL OR btrim(owner_role)='')")" "^0$"

{ echo "procedures-service P1 catalogue proof"; echo "generated: $(date -Is)"; echo "PASS=$PASS FAIL=$FAIL"; } > "$EVIDENCE/summary.txt"
echo
echo "P1 catalogue proof: PASS=$PASS FAIL=$FAIL   evidence: $EVIDENCE"
[[ $FAIL -eq 0 ]] || exit 1
