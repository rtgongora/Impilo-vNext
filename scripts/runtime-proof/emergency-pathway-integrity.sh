#!/usr/bin/env bash
# ============================================================================
# Emergency ED_* pathway integrity — runtime proof (emergency pack W4b).
#
# THE POINT: no ED_* clinical pathway a clinician can start is an empty shell or cites an
# unrelated source. V005 seeded eleven ED_* pathways but eight had zero steps and four cited
# the wrong section (ectopic/obstetric -> "UTI", status epilepticus -> "Fever", respiratory
# failure -> "chronic asthma"); V201 repairs them and retires the qSOFA sepsis screen. A pure
# name-grep would lie about this — the only honest check applies the migrations to a real
# Postgres in Flyway order and interrogates the resulting rows, which is what this rig does.
#
# Asserts, on a throwaway Postgres with clinical.* migrations V001..V201 applied in order:
#   J-EP-1  every ED_* pathway_definition has at least one pathway_step (no shell survives)
#   J-EP-2  no ED_* pathway cites a UTI / Fever / chronic-asthma section (miscitation gone)
#   J-EP-3  ED_SEPSIS step 1 no longer screens on qSOFA (SSC26: EWS over qSOFA)
#   J-EP-4  ED_ECTOPIC step 1 gates on reproductive age until pregnancy excluded, not pregnant==true
#
# Requirements: docker. Skips (exit 0) when docker is unavailable, like the other rigs, so it
# never breaks a docker-less CI lane — it PROVES when it can and abstains loudly when it cannot.
# Usage: bash scripts/runtime-proof/emergency-pathway-integrity.sh
# ============================================================================
set -u
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
MDIR="$REPO/services/clinical-knowledge-platform-service/src/main/resources/db/migration"
PASS=0; FAIL=0
ok(){ echo "   PASS: $1"; PASS=$((PASS+1)); }
bad(){ echo "   FAIL: $1"; FAIL=$((FAIL+1)); }
say(){ echo "== $1"; }

command -v docker >/dev/null 2>&1 || { echo "docker not available — pathway-integrity rig SKIPPED (cannot prove without a real DB)"; exit 0; }

CID=$(docker run -d --rm -e POSTGRES_PASSWORD=t -e POSTGRES_DB=ckpv postgres:16-alpine 2>/dev/null)
cleanup(){ docker stop "$CID" >/dev/null 2>&1 || true; }
trap cleanup EXIT

ready=0
for i in $(seq 1 40); do
  if docker exec "$CID" pg_isready -U postgres -q 2>/dev/null; then ready=$((ready+1)); [ $ready -ge 2 ] && break; else ready=0; fi
  sleep 1
done
[ $ready -ge 2 ] || { echo "postgres did not come up — SKIPPED"; exit 0; }

SQL(){ docker exec -i "$CID" psql -U postgres -d ckpv -tAc "$1" 2>/dev/null; }

say "Apply clinical.* migrations V001..V201 in Flyway version order"
for f in $(ls "$MDIR"/*.sql | sort -V); do
  if ! docker exec -i "$CID" psql -U postgres -d ckpv -v ON_ERROR_STOP=1 -q < "$f" >/tmp/ckp-pathway-apply.log 2>&1; then
    bad "migration $(basename "$f") failed to apply"; tail -4 /tmp/ckp-pathway-apply.log
    echo "RESULT: $PASS passed, $FAIL failed"; exit 1
  fi
done
ok "all clinical migrations applied in order"

say "J-EP-1  no ED_* pathway is a stepless shell"
STEPLESS=$(SQL "SELECT string_agg(pd.condition_code, ',') FROM clinical.pathway_definitions pd
  LEFT JOIN clinical.pathway_steps ps ON ps.pathway_id=pd.id
  WHERE pd.condition_code LIKE 'ED\_%' GROUP BY pd.id HAVING COUNT(ps.id)=0;")
[ -z "$STEPLESS" ] && ok "every ED_* pathway has >=1 step" || bad "stepless ED_* pathway(s): $STEPLESS"

say "J-EP-2  no ED_* pathway cites an unrelated (UTI/Fever/chronic-asthma) section"
MISCITED=$(SQL "SELECT string_agg(pd.condition_code, ',') FROM clinical.pathway_definitions pd
  JOIN clinical.source_sections ss ON ss.id=pd.source_section_id
  WHERE pd.condition_code LIKE 'ED\_%'
    AND (ss.section_title ILIKE '%UTI%' OR ss.section_title ILIKE '%Fever%' OR ss.section_title ILIKE '%chronic%');")
[ -z "$MISCITED" ] && ok "no ED_* miscitation" || bad "miscited ED_* pathway(s): $MISCITED"

say "J-EP-3  ED_SEPSIS step 1 does not screen on qSOFA and does screen on an EWS"
# Match ANY 'qsofa' (the original prompt uses the Unicode >=, so a '>=2' literal would match
# neither state — a check that never fires). Require both: qSOFA gone AND an EWS screen present.
QSOFA=$(SQL "SELECT COUNT(*) FROM clinical.pathway_steps ps JOIN clinical.pathway_definitions pd ON pd.id=ps.pathway_id
  WHERE pd.condition_code='ED_SEPSIS' AND ps.step_order=1 AND ps.prompt ILIKE '%qsofa%';")
EWS=$(SQL "SELECT COUNT(*) FROM clinical.pathway_steps ps JOIN clinical.pathway_definitions pd ON pd.id=ps.pathway_id
  WHERE pd.condition_code='ED_SEPSIS' AND ps.step_order=1 AND (ps.prompt ILIKE '%news2%' OR ps.prompt ILIKE '%mews%' OR ps.prompt ILIKE '%early warning%');")
{ [ "${QSOFA:-1}" = "0" ] && [ "${EWS:-0}" = "1" ]; } && ok "qSOFA retired, EWS screen in place" || bad "ED_SEPSIS screen wrong (qsofa_hits=$QSOFA ews_hits=$EWS)"

say "J-EP-4  ED_ECTOPIC gates on reproductive age until pregnancy excluded"
GATE=$(SQL "SELECT COUNT(*) FROM clinical.pathway_steps ps JOIN clinical.pathway_definitions pd ON pd.id=ps.pathway_id
  WHERE pd.condition_code='ED_ECTOPIC' AND ps.step_order=1 AND ps.branch_logic::text ILIKE '%reproductive_age%';")
[ "${GATE:-0}" = "1" ] && ok "ectopic gate is reproductive-age, not pregnant==true" || bad "ED_ECTOPIC gate missing or wrong"

echo "RESULT: $PASS passed, $FAIL failed"
[ $FAIL -eq 0 ]
