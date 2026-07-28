#!/usr/bin/env bash
# Procedures pipeline P14 — pipeline analytics indicator catalogue (§26) runtime proof
# (real Postgres).
#
# This is governed CONTENT, not an execution engine: procedures-service declares what each of
# the twenty-two named indicators (dak-baseline.md §7 enumerates twenty-two despite both that
# doc and audit.md asserting "twenty-four" — a pre-existing inconsistency this wave does not
# paper over by inventing two more) IS, and its real computation status today. Real numbers, where
# they exist, are computed by reporting-service's already-proven TheatreReportingConsumer
# projection (V002__theatre_report_catalog.sql) — this rig proves the CATALOGUE's own governance
# rules (every non-COMPUTED row must carry a gap_reason; every non-NOT_YET_INSTRUMENTED row must
# carry an executable_via), not a second copy of reporting-service's numbers.
#
# H2 (module tests) does not enforce the CHECK constraints; this proves what only Postgres can.
#
# Usage:  bash scripts/runtime-proof/procedures-analytics-journeys.sh
#         KEEP_RIG=1 ... to leave the container up.
set -uo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

PG_NAME="proc-analytics-rig-$$"
PG_PORT="${PROC_RIG_PG_PORT:-25453}"
TENANT="00000000-0000-4000-8000-000000000001"
MIG="services/procedures-service/src/main/resources/db/migration"
EVIDENCE="reports/journeys/procedures-analytics-proof-$(date +%Y%m%d-%H%M%S)"

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

echo "=== procedures-service P14 analytics indicator catalogue proof ==="
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
         V007__aftercare_template_specific_codes V008__complication_profile V009__ipc_requirement_depth \
         V010__analytics_indicator_definitions; do
  out=$(docker exec -i "$PG_NAME" psql -U impilo -d procedures -v ON_ERROR_STOP=1 < "$MIG/$f.sql" 2>&1)
  echo "$out" > "$EVIDENCE/$f.txt"
  chk "J-P14-0 $f applies" "$(echo "$out" | grep -ci error || true)" "^0$"
done

# ── Catalogue completeness ──
chk "J-P14-1 all twenty-two named indicators are seeded" \
  "$(q "SELECT count(*) FROM procedures.analytics_indicator_definition WHERE tenant_id='$TENANT'")" "^22$"

chk "J-P14-2 every indicator code is unique per tenant (no accidental duplicate seed)" \
  "$(q "SELECT count(*) FROM (SELECT indicator_code FROM procedures.analytics_indicator_definition
        WHERE tenant_id='$TENANT' GROUP BY indicator_code HAVING count(*) > 1) d")" "^0$"

# ── Honesty governance: non-COMPUTED rows must carry a reason ──
chk "J-P14-3 every PARTIAL/NOT_YET_INSTRUMENTED row carries a gap_reason" \
  "$(q "SELECT count(*) FROM procedures.analytics_indicator_definition
        WHERE tenant_id='$TENANT' AND computation_status <> 'COMPUTED'
          AND (gap_reason IS NULL OR btrim(gap_reason) = '')")" "^0$"

chk "J-P14-4 a COMPUTED/PARTIAL row without a gap_reason is REFUSED by the CHECK" \
  "$(q "INSERT INTO procedures.analytics_indicator_definition
          (tenant_id, indicator_code, indicator_name, numerator_description, computation_status)
        VALUES ('$TENANT', 'BOGUS_PARTIAL', 'x', 'y', 'PARTIAL')")" \
  "chk_analytics_indicator_gap_reason"

chk "J-P14-5 every COMPUTED row carries an executable_via reference" \
  "$(q "SELECT count(*) FROM procedures.analytics_indicator_definition
        WHERE tenant_id='$TENANT' AND computation_status = 'COMPUTED'
          AND (executable_via IS NULL OR btrim(executable_via) = '')")" "^0$"

chk "J-P14-6 a COMPUTED row without an executable_via is REFUSED by the CHECK" \
  "$(q "INSERT INTO procedures.analytics_indicator_definition
          (tenant_id, indicator_code, indicator_name, numerator_description, computation_status)
        VALUES ('$TENANT', 'BOGUS_COMPUTED', 'x', 'y', 'COMPUTED')")" \
  "chk_analytics_indicator_executable_via"

# Target INDICATION (NOT_YET_INSTRUMENTED, already carries a gap_reason and no executable_via) so
# only the status CHECK can fire — the same constraint-evaluation-order lesson P8/P12/P13 each
# hit: an UPDATE touching one field can trip a DIFFERENT, already-violated CHECK first unless the
# row already satisfies every other constraint.
chk "J-P14-7 an invented computation_status value is REFUSED" \
  "$(q "UPDATE procedures.analytics_indicator_definition
        SET computation_status='PROBABLY_FINE' WHERE indicator_code='INDICATION'")" \
  "chk_analytics_indicator_status"

# ── Real status distribution: proves the wave neither over- nor under-claims coverage ──
chk "J-P14-8 exactly five indicators are honestly COMPUTED today (via reporting-service's real projection)" \
  "$(q "SELECT count(*) FROM procedures.analytics_indicator_definition
        WHERE tenant_id='$TENANT' AND computation_status='COMPUTED'")" "^5$"

chk "J-P14-9 every COMPUTED indicator resolves to reporting-service's real projection, not an invented source" \
  "$(q "SELECT count(*) FROM procedures.analytics_indicator_definition
        WHERE tenant_id='$TENANT' AND computation_status='COMPUTED'
          AND executable_via NOT LIKE 'reporting-service:%'")" "^0$"

# ── Delegation is distinct from an unclaimed gap ──
chk "J-P14-10 cost and stockout are flagged delegated_out_of_scope, not owed as debt to this programme" \
  "$(q "SELECT count(*) FROM procedures.analytics_indicator_definition
        WHERE tenant_id='$TENANT' AND indicator_code IN ('COST','STOCKOUT_IMPACT')
          AND delegated_out_of_scope = TRUE")" "^2$"

chk "J-P14-11 no COMPUTED or PARTIAL indicator is mis-flagged delegated_out_of_scope" \
  "$(q "SELECT count(*) FROM procedures.analytics_indicator_definition
        WHERE tenant_id='$TENANT' AND computation_status IN ('COMPUTED','PARTIAL')
          AND delegated_out_of_scope = TRUE")" "^0$"

# ── A named, real-world safeguard the spec itself calls out ──
chk "J-P14-12 the operator/supervision indicator's anti-league-table constraint is preserved in its own description text" \
  "$(q "SELECT count(*) FROM procedures.analytics_indicator_definition
        WHERE tenant_id='$TENANT' AND indicator_code='OPERATOR_AND_SUPERVISION'
          AND numerator_description ILIKE '%never a league table%'")" "^1$"

# ── Tenant isolation ──
chk "J-P14-13 a second tenant sees no indicators (no seed leaked across tenants)" \
  "$(q "SELECT count(*) FROM procedures.analytics_indicator_definition
        WHERE tenant_id='00000000-0000-4000-8000-000000000002'")" "^0$"

echo
echo "=== P14 analytics proof: $PASS passed, $FAIL failed ==="
[[ $FAIL -eq 0 ]] || exit 1
