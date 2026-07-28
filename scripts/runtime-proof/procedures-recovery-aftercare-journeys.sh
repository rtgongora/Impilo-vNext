#!/usr/bin/env bash
# Procedures pipeline P9 — recovery settings (§15) and aftercare templates (§17) runtime proof
# (real Postgres). Extended for V007 (Wave P-R2).
#
# H2 (module tests) does not enforce CHECK constraints or the published-authority governance
# rule; this proves what only Postgres can enforce. RecoveryAndAftercareServiceTest proves the
# service's read/resolve logic on hand-inserted fixtures.
#
# V007 ADDENDUM: while wiring P9's content into the catalogue UI (Wave P-R2), found that
# procedure_definition.aftercare_template (V002/V003, 34 distinct specific per-procedure codes)
# already existed and had never been resolved — P9's own aftercare_template table used a
# SEPARATE, coarser six-value taxonomy instead of resolving the pre-existing specific one, the
# exact mistake P7 correctly avoided for safety_pause_template. V007 fixes this forward (V006
# was already pushed) by seeding five specific templates for the same demonstration procedures
# already sedation/recovery-linked. J-P9-17 below proves the fix at the row level, not just that
# the migration applies.
#
# Usage:  bash scripts/runtime-proof/procedures-recovery-aftercare-journeys.sh
#         KEEP_RIG=1 ... to leave the container up.
set -uo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

PG_NAME="proc-recovery-rig-$$"
PG_PORT="${PROC_RIG_PG_PORT:-25449}"
TENANT="00000000-0000-4000-8000-000000000001"
MIG="services/procedures-service/src/main/resources/db/migration"
EVIDENCE="reports/journeys/procedures-recovery-aftercare-proof-$(date +%Y%m%d-%H%M%S)"

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

echo "=== procedures-service P9 recovery + aftercare proof ==="
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
         V007__aftercare_template_specific_codes; do
  out=$(docker exec -i "$PG_NAME" psql -U impilo -d procedures -v ON_ERROR_STOP=1 < "$MIG/$f.sql" 2>&1)
  echo "$out" > "$EVIDENCE/$f.txt"
  chk "J-P9-0 $f applies" "$(echo "$out" | grep -ci error || true)" "^0$"
done

# ── §15 recovery settings ──
chk "J-P9-1 all five recovery settings are seeded" \
  "$(q "SELECT count(*) FROM procedures.recovery_setting")" "^5$"

chk "J-P9-2 an invented recovery setting code is REFUSED" \
  "$(q "INSERT INTO procedures.recovery_setting
          (tenant_id, setting_code, setting_label, discharge_readiness_criteria, monitoring_required)
        VALUES ('$TENANT','SEMI_CONSCIOUS','x','c','m')")" \
  "chk_recovery_setting_code"

chk "J-P9-3 PACU cites the anaesthesia monitoring standard, not the generic one" \
  "$(q "SELECT source_citation FROM procedures.recovery_setting WHERE setting_code='PACU'")" \
  "ANAESTHESIA.WFSA_WHO.MONITORING"

# ── §17 aftercare templates ──
# Expressed relative to the total row count (P9's 6 setting-class + V007's 5 specific) rather
# than a literal number that would need editing every time a future wave adds a template.
chk "J-P9-4 every seeded aftercare template is PUBLISHED (none silently left DRAFT)" \
  "$(q "SELECT CASE WHEN count(*) FILTER (WHERE status='PUBLISHED') = count(*) THEN 'ALL_PUBLISHED' ELSE 'GAP' END
        FROM procedures.aftercare_template")" "ALL_PUBLISHED"

chk "J-P9-4b at least eleven aftercare templates exist (6 setting-class + 5 V007 specific)" \
  "$(q "SELECT CASE WHEN count(*) >= 11 THEN 'ENOUGH' ELSE 'TOO_FEW' END FROM procedures.aftercare_template")" \
  "ENOUGH"

chk "J-P9-5 every published template names an approving authority" \
  "$(q "SELECT count(*) FROM procedures.aftercare_template
        WHERE status='PUBLISHED' AND (approving_authority IS NULL OR btrim(approving_authority)='')")" "^0$"

chk "J-P9-6 publishing a template without an approving authority is REFUSED" \
  "$(q "INSERT INTO procedures.aftercare_template (tenant_id, template_code, template_name, status)
        VALUES ('$TENANT','AFTERCARE-BOGUS','x','PUBLISHED')")" \
  "chk_aftercare_template_published_authority"

chk "J-P9-7 every seeded template is honestly flagged ENGINEERING_SEED, not claimed RATIFIED" \
  "$(q "SELECT count(*) FROM procedures.aftercare_template WHERE content_maturity <> 'ENGINEERING_SEED'")" "^0$"

chk "J-P9-8 an invented content_maturity value is REFUSED" \
  "$(q "UPDATE procedures.aftercare_template SET content_maturity='PROBABLY_FINE' WHERE template_code='AFTERCARE-THEATRE'")" \
  "chk_aftercare_template_content_maturity"

chk "J-P9-9 an invented instruction kind is REFUSED" \
  "$(q "INSERT INTO procedures.aftercare_instruction (template_id, instruction_kind, instruction_text)
        SELECT id, 'NOT_A_REAL_KIND', 'x' FROM procedures.aftercare_template LIMIT 1")" \
  "chk_aftercare_instruction_kind"

chk "J-P9-10 an invented delivery channel is REFUSED" \
  "$(q "INSERT INTO procedures.aftercare_template_channel (template_id, delivery_channel)
        SELECT id, 'CARRIER_PIGEON' FROM procedures.aftercare_template LIMIT 1")" \
  "chk_aftercare_delivery_channel"

# Every template reaches at least the clinical record — the same discipline §16 already applies
# to results (nothing is optional to the chart itself).
chk "J-P9-11 every template is deliverable through at least CLINICAL_SUMMARY" \
  "$(q "SELECT CASE WHEN count(*) FILTER (WHERE EXISTS (
                SELECT 1 FROM procedures.aftercare_template_channel c
                WHERE c.template_id = t.id AND c.delivery_channel = 'CLINICAL_SUMMARY')) = count(*)
             THEN 'ALL' ELSE 'MISSING' END
        FROM procedures.aftercare_template t")" "ALL"

# ── linkage to the catalogue (P7's own linkage pattern, reused) ──
chk "J-P9-12 at least eleven catalogue entries declare both a recovery setting and an aftercare template" \
  "$(q "SELECT CASE WHEN count(*) >= 11 THEN 'ENOUGH' ELSE 'TOO_FEW' END FROM procedures.procedure_definition
        WHERE default_recovery_setting_code IS NOT NULL AND default_aftercare_template_code IS NOT NULL")" \
  "ENOUGH"

chk "J-P9-13 every declared default_recovery_setting_code resolves to a real seeded setting" \
  "$(q "SELECT count(*) FROM procedures.procedure_definition d
        WHERE d.default_recovery_setting_code IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM procedures.recovery_setting r
                          WHERE r.tenant_id = d.tenant_id AND r.setting_code = d.default_recovery_setting_code)")" \
  "^0$"

chk "J-P9-14 every declared default_aftercare_template_code resolves to a real PUBLISHED template" \
  "$(q "SELECT count(*) FROM procedures.procedure_definition d
        WHERE d.default_aftercare_template_code IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM procedures.aftercare_template t
                          WHERE t.tenant_id = d.tenant_id AND t.template_code = d.default_aftercare_template_code
                            AND t.status = 'PUBLISHED')")" \
  "^0$"

# ── invariants that must survive a new migration ──
chk "J-P9-15 engine-not-store still holds — no recovery/aftercare COMPLETION or DELIVERY table added" \
  "$(q "SELECT coalesce(string_agg(tablename,','),'none') FROM pg_tables
        WHERE schemaname='procedures' AND (tablename LIKE '%completion%' OR tablename LIKE '%delivery_log%' OR tablename LIKE '%delivered%')")" \
  "^none$"

chk "J-P9-16 P1's site-and-side-never-overridable constraint still holds" \
  "$(q "UPDATE procedures.procedure_requirement SET overridable_in_emergency=true
        WHERE requirement_kind='SITE_SIDE_VERIFICATION'")" \
  "chk_procedure_requirement_site_side_never_overridable"

# ── V007: the fix, proven at the row level — the pre-existing SPECIFIC aftercare_template
# codes on the demonstration procedures now actually resolve, not just the coarse fallback. ──
chk "J-P9-17 all ten demonstration procedures with a declared specific aftercare_template resolve to a real row" \
  "$(q "SELECT CASE WHEN count(*) = 10 THEN 'ALL' ELSE 'MISSING' END FROM procedures.procedure_definition d
        WHERE d.definition_code IN ('PROC-LAPAROTOMY','PROC-CAESAREAN-SECTION','PROC-TOTAL-HIP-REPLACEMENT',
          'PROC-OGD','PROC-COLONOSCOPY','PROC-BRONCHOSCOPY','PROC-HAEMODIALYSIS','PROC-PERITONEAL-DIALYSIS',
          'PROC-CENTRAL-LINE','PROC-LUMBAR-PUNCTURE')
          AND d.aftercare_template IS NOT NULL
          AND EXISTS (SELECT 1 FROM procedures.aftercare_template t
                      WHERE t.tenant_id = d.tenant_id AND t.template_code = d.aftercare_template)")" \
  "ALL"

# The honest debt, not silently closed: PROC-ARTERIAL-LINE never had an aftercare_template value
# seeded at all (V003), and V007 does not fabricate one for it.
chk "J-P9-18 PROC-ARTERIAL-LINE's aftercare_template is still honestly undeclared, not backfilled" \
  "$(q "SELECT aftercare_template FROM procedures.procedure_definition WHERE definition_code='PROC-ARTERIAL-LINE'")" \
  "^$"

{ echo "procedures-service P9 recovery + aftercare proof"; echo "generated: $(date -Is)"; echo "PASS=$PASS FAIL=$FAIL"; } > "$EVIDENCE/summary.txt"
echo
echo "P9 recovery + aftercare proof: PASS=$PASS FAIL=$FAIL   evidence: $EVIDENCE"
[[ $FAIL -eq 0 ]] || exit 1
