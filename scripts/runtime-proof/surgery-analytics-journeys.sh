#!/usr/bin/env bash
# Surgical domain pack SB-6 — surgical-pack analytics indicator catalogue (§23) runtime proof
# (real Postgres).
#
# Governed CONTENT, not an execution engine — same shape as procedures-service's own P14/V010.
# Real numbers, where they exist, are computed by reporting-service's already-proven
# TheatreReportingConsumer projection (V002__theatre_report_catalog.sql); this rig proves the
# CATALOGUE's own governance rules and the three-way count reconciliation V008's own header
# names (audit.md "2 of 20" undercounts; dak-baseline.md's "four" gets the COUNT right but the
# SET wrong — it tags emergency-vs-elective, which has no real column anywhere in the projection,
# instead of unplanned-return-to-theatre, which does).
#
# H2 (module tests) does not enforce the CHECK constraints; this proves what only Postgres can.
#
# Usage:  bash scripts/runtime-proof/surgery-analytics-journeys.sh
#         KEEP_RIG=1 ... to leave the container up.
set -uo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

PG_NAME="surgery-analytics-rig-$$"
PG_PORT="${SURGERY_RIG_PG_PORT:-25482}"
TENANT="00000000-0000-4000-8000-000000000001"
MIG="services/surgery-service/src/main/resources/db/migration"
EVIDENCE="reports/journeys/surgery-analytics-proof-$(date +%Y%m%d-%H%M%S)"

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

echo "=== surgery-service SB-6 analytics indicator catalogue proof ==="
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
         V007__specialty_content_seed V008__analytics_indicator_catalogue; do
  out=$(docker exec -i "$PG_NAME" psql -U impilo -d surgery -v ON_ERROR_STOP=1 < "$MIG/$f.sql" 2>&1)
  echo "$out" > "$EVIDENCE/$f.txt"
  chk "J-SB6A-0 $f applies" "$(echo "$out" | grep -ci error || true)" "^0$"
done

# ── Catalogue completeness ──
chk "J-SB6A-1 all twenty named indicators are seeded" \
  "$(q "SELECT count(*) FROM surgery.analytics_indicator_definition WHERE tenant_id='$TENANT'")" "^20$"

chk "J-SB6A-2 every indicator code is unique per tenant" \
  "$(q "SELECT count(*) FROM (SELECT indicator_code FROM surgery.analytics_indicator_definition
        WHERE tenant_id='$TENANT' GROUP BY indicator_code HAVING count(*) > 1) d")" "^0$"

# ── Honesty governance ──
chk "J-SB6A-3 every PARTIAL/NOT_YET_INSTRUMENTED row carries a gap_reason" \
  "$(q "SELECT count(*) FROM surgery.analytics_indicator_definition
        WHERE tenant_id='$TENANT' AND computation_status <> 'COMPUTED'
          AND (gap_reason IS NULL OR btrim(gap_reason) = '')")" "^0$"

chk "J-SB6A-4 a PARTIAL row without a gap_reason is REFUSED by the CHECK" \
  "$(q "INSERT INTO surgery.analytics_indicator_definition
          (tenant_id, indicator_code, indicator_name, numerator_description, computation_status)
        VALUES ('$TENANT', 'BOGUS_PARTIAL', 'x', 'y', 'PARTIAL')")" \
  "chk_surgery_analytics_indicator_gap_reason"

chk "J-SB6A-5 every COMPUTED row carries an executable_via reference" \
  "$(q "SELECT count(*) FROM surgery.analytics_indicator_definition
        WHERE tenant_id='$TENANT' AND computation_status = 'COMPUTED'
          AND (executable_via IS NULL OR btrim(executable_via) = '')")" "^0$"

chk "J-SB6A-6 a COMPUTED row without an executable_via is REFUSED by the CHECK" \
  "$(q "INSERT INTO surgery.analytics_indicator_definition
          (tenant_id, indicator_code, indicator_name, numerator_description, computation_status)
        VALUES ('$TENANT', 'BOGUS_COMPUTED', 'x', 'y', 'COMPUTED')")" \
  "chk_surgery_analytics_indicator_executable_via"

chk "J-SB6A-7 an invented computation_status value is REFUSED" \
  "$(q "UPDATE surgery.analytics_indicator_definition
        SET computation_status='PROBABLY_FINE' WHERE indicator_code='MORTALITY'")" \
  "chk_surgery_analytics_indicator_status"

# ── The count reconciliation itself — the specific finding this wave exists to prove ──
chk "J-SB6A-8 exactly four indicators are honestly COMPUTED (not five, not two)" \
  "$(q "SELECT count(*) FROM surgery.analytics_indicator_definition
        WHERE tenant_id='$TENANT' AND computation_status='COMPUTED'")" "^4$"

chk "J-SB6A-9 EMERGENCY_VS_ELECTIVE — dak-baseline's own Y tag — is honestly NOT_YET_INSTRUMENTED, not COMPUTED" \
  "$(q "SELECT computation_status FROM surgery.analytics_indicator_definition
        WHERE tenant_id='$TENANT' AND indicator_code='EMERGENCY_VS_ELECTIVE'")" "^NOT_YET_INSTRUMENTED$"

chk "J-SB6A-10 UNPLANNED_RETURN_TO_THEATRE — untagged in dak-baseline.md — is honestly COMPUTED" \
  "$(q "SELECT computation_status FROM surgery.analytics_indicator_definition
        WHERE tenant_id='$TENANT' AND indicator_code='UNPLANNED_RETURN_TO_THEATRE'")" "^COMPUTED$"

chk "J-SB6A-11 every COMPUTED indicator resolves to reporting-service's real projection" \
  "$(q "SELECT count(*) FROM surgery.analytics_indicator_definition
        WHERE tenant_id='$TENANT' AND computation_status='COMPUTED'
          AND executable_via NOT LIKE 'reporting-service:%'")" "^0$"

# ── Delegation is distinct from an unclaimed gap ──
chk "J-SB6A-12 caesarean-integration and financial-protection are flagged delegated_out_of_scope" \
  "$(q "SELECT count(*) FROM surgery.analytics_indicator_definition
        WHERE tenant_id='$TENANT' AND indicator_code IN ('CAESAREAN_INTEGRATION','FINANCIAL_PROTECTION')
          AND delegated_out_of_scope = TRUE")" "^2$"

chk "J-SB6A-13 no COMPUTED indicator is mis-flagged delegated_out_of_scope" \
  "$(q "SELECT count(*) FROM surgery.analytics_indicator_definition
        WHERE tenant_id='$TENANT' AND computation_status = 'COMPUTED'
          AND delegated_out_of_scope = TRUE")" "^0$"

# ── Every seeded row is honestly flagged ENGINEERING_SEED (same rider as V007) ──
chk "J-SB6A-14 every seeded indicator is honestly flagged ENGINEERING_SEED" \
  "$(q "SELECT count(*) FROM surgery.analytics_indicator_definition
        WHERE tenant_id='$TENANT' AND content_maturity <> 'ENGINEERING_SEED'")" "^0$"

# ── Tenant isolation ──
chk "J-SB6A-15 a second tenant sees no indicators" \
  "$(q "SELECT count(*) FROM surgery.analytics_indicator_definition
        WHERE tenant_id='00000000-0000-4000-8000-000000000002'")" "^0$"

echo
echo "=== SB-6 surgery analytics proof: $PASS passed, $FAIL failed ==="
[[ $FAIL -eq 0 ]] || exit 1
