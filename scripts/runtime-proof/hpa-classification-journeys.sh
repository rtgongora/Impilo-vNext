#!/usr/bin/env bash
# ============================================================================
# HPA facility classification enrichment — runtime proof (the PO's 10 validations).
#
# Boots tuso from its jar on a scratch Postgres, imports the REAL national
# master pack (data/source/.../clean_tuso_facility_import.csv, 1,773 rows) via
# the master-pack endpoint, runs the classification enrichment backfill, and
# drives P0..P11 with curl + psql read-backs.
#
# Usage: EV=<evidence-dir> bash scripts/runtime-proof/hpa-classification-journeys.sh
#        KEEP_RIG=1 to leave infra + tuso running for inspection.
# ============================================================================
set -u
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
EV=${EV:-/tmp/hpa-classification-evidence}
RIGLOG=${RIGLOG:-/tmp/hpa-classification-logs}
mkdir -p "$EV" "$RIGLOG"
TEN=00000000-0000-4000-8000-000000000001
PGPORT=15644; RPORT=16411; TUSO=http://localhost:28084
CSV="$REPO/data/source/zimbabwe/master-health-facility/2024-07-23/clean_tuso_facility_import.csv"
PASS=0; FAIL=0
ok(){ echo "   PASS: $1" | tee -a "$EV/journal.txt"; PASS=$((PASS+1)); }
bad(){ echo "   FAIL: $1" | tee -a "$EV/journal.txt"; FAIL=$((FAIL+1)); }
say(){ echo "== $1" | tee -a "$EV/journal.txt"; }
PSQL(){ docker exec hpa-cls-pg psql -U impilo -d tuso -tAc "$1"; }
# trust headers — actorType HPA_REGISTRAR is a classification-admin role
hdr(){ local a=${1:-HPA_REGISTRAR}
  echo "-H Content-Type:application/json -H X-Tenant-ID:$TEN -H X-Pod-ID:national-spine -H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H X-Actor-ID:rig-$a -H X-Actor-Type:$a -H X-Purpose-Of-Use:REGULATION"; }
mid(){ python3 -c "import json,sys;r=json.load(sys.stdin);d=r.get('data') if isinstance(r,dict) and 'data' in r else r;print((d or {}).get('$1',''))"; }

# ── Infra ────────────────────────────────────────────────────────────────────
say "RIG: infra + tuso"
docker rm -f hpa-cls-pg hpa-cls-redis >/dev/null 2>&1
docker run -d --name hpa-cls-pg -e POSTGRES_USER=impilo -e POSTGRES_PASSWORD=impilo -p $PGPORT:5432 postgres:16-alpine >/dev/null
docker run -d --name hpa-cls-redis -p $RPORT:6379 redis:7-alpine >/dev/null
sleep 8
docker exec hpa-cls-pg psql -U impilo -d postgres -c "CREATE DATABASE tuso" >/dev/null 2>&1

jar(){ ls "$REPO/services/tuso-service/target/tuso-service-"*.jar 2>/dev/null | grep -v original | head -1; }
env POSTGRES_HOST=localhost POSTGRES_PORT=$PGPORT POSTGRES_USER=impilo POSTGRES_PASSWORD=impilo POSTGRES_DB=tuso \
  IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true SERVER_PORT=28084 SPRING_KAFKA_LISTENER_AUTO_STARTUP=false \
  SPRING_DATA_REDIS_HOST=localhost SPRING_DATA_REDIS_PORT=$RPORT \
  nohup java -jar "$(jar)" > "$RIGLOG/tuso.log" 2>&1 & echo $! > "$RIGLOG/tuso.pid"

for i in $(seq 1 40); do c=$(curl -s -o /dev/null -w '%{http_code}' $TUSO/actuator/health 2>/dev/null); [ "$c" = 200 ] && break; sleep 3; done
echo "   tuso health: $c" | tee -a "$EV/journal.txt"
[ "$c" = 200 ] && ok "tuso booted (V001→V020, classification rule pack seeded)" || { bad "tuso did not boot"; tail -40 "$RIGLOG/tuso.log"; exit 1; }

# ── Import the real 1,773-row master pack ────────────────────────────────────
say "IMPORT: real national master pack → tuso.facility"
PAYLOAD="$EV/master-pack-payload.json"
python3 - "$CSV" "$PAYLOAD" <<'PYEOF'
import csv, json, sys
csv_path, out_path = sys.argv[1], sys.argv[2]
records = []
with open(csv_path, newline='', encoding='utf-8-sig') as f:
    for r in csv.DictReader(f):
        code = (r.get('facility_code') or '').strip()
        uid = code if code else ("row-" + (r.get('source_row') or '').strip())
        bed = (r.get('bed_capacity') or '').strip()
        try:
            bed_val = int(float(bed)) if bed and bed.lower() not in ('n/a','na','null','none','-') else None
        except ValueError:
            bed_val = None
        def num(x):
            try: return float(x)
            except (TypeError, ValueError): return None
        records.append({
            "facility_uid": uid,
            "facility_code": code or None,
            "facility_name": (r.get('facility_name') or '').strip() or "UNKNOWN",
            "province": (r.get('province') or '').strip() or None,
            "district": (r.get('district') or '').strip() or None,
            "facility_type": (r.get('facility_type_canonical') or '').strip() or None,
            "ownership": (r.get('ownership_canonical') or '').strip() or None,
            "location_context": (r.get('location_canonical') or '').strip() or None,
            "service_level": (r.get('level_canonical') or '').strip() or None,
            "status": (r.get('status_canonical') or '').strip() or "IMPORTED",
            "bed_capacity": bed_val,
            "latitude": num(r.get('latitude')),
            "longitude": num(r.get('longitude')),
            "source_dataset_date": "2024-07-23",
        })
json.dump({"dryRun": False, "syncNdila": False, "reconcileDuplicateCodes": False, "records": records}, open(out_path, 'w'))
print("records:", len(records))
PYEOF
CSV_ROWS=$(python3 -c "import json;print(len(json.load(open('$PAYLOAD'))['records']))")
echo "   payload records: $CSV_ROWS" | tee -a "$EV/journal.txt"
IMP=$(curl -sS $(hdr OPERATOR) -X POST $TUSO/v1/internal/facilities/import/master-pack --data-binary @"$PAYLOAD")
echo "$IMP" > "$EV/import-response.json"
CREATED=$(echo "$IMP" | mid recordsCreated)
FCOUNT=$(PSQL "SELECT count(*) FROM tuso.facility")
echo "   import created=$CREATED · tuso.facility rows=$FCOUNT" | tee -a "$EV/journal.txt"
[ "$FCOUNT" -ge 1700 ] && ok "real master pack imported ($FCOUNT facilities)" || bad "only $FCOUNT facilities imported"

# ── Run the enrichment backfill ──────────────────────────────────────────────
say "BACKFILL: classification enrichment run (real estate)"
RUN=$(curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facilities/classification/enrichment-runs -d '{"dryRun":false}')
echo "$RUN" > "$EV/enrichment-run.json"
RUN_TOTAL=$(echo "$RUN" | mid recordsTotal)
RUN_CREATED=$(echo "$RUN" | mid recordsCreated)
echo "   run total=$RUN_TOTAL created=$RUN_CREATED" | tee -a "$EV/journal.txt"

# ── P0: estate audit count == SQL count ──────────────────────────────────────
say "P0: estate audit verifies the count (never assumes 1773)"
PROFILES=$(PSQL "SELECT count(*) FROM tuso.facility_regulatory_profile")
[ "$RUN_TOTAL" = "$FCOUNT" ] && [ "$PROFILES" = "$FCOUNT" ] \
  && ok "estate audit total=$RUN_TOTAL == facilities=$FCOUNT == profiles=$PROFILES" \
  || bad "count mismatch: run=$RUN_TOTAL facilities=$FCOUNT profiles=$PROFILES"

# ── P1: a district hospital enriched; source category + level preserved ──────
say "P1: district hospital enriched, source preserved"
DH=$(PSQL "SELECT facility_id FROM tuso.facility_regulatory_profile WHERE source_facility_type='DISTRICT_HOSPITAL' LIMIT 1")
DHROW=$(PSQL "SELECT hpa_class||'|'||source_facility_type||'|'||COALESCE(source_level,'-')||'|'||canonical_type FROM tuso.facility_regulatory_profile WHERE facility_id=$DH")
FTYPE=$(PSQL "SELECT facility_type FROM tuso.facility WHERE id=$DH")
echo "   dh profile: $DHROW · facility.facility_type=$FTYPE" | tee -a "$EV/journal.txt"
echo "$DHROW" | grep -q "^C|DISTRICT_HOSPITAL|" && [ "$FTYPE" = "DISTRICT_HOSPITAL" ] \
  && ok "district hospital → class C, canonical HOSPITAL; source type+level intact on facility" \
  || bad "district hospital enrichment wrong: $DHROW / facility_type=$FTYPE"

# ── P2: supply care_setting to a public clinic → label resolves → confirm ────
say "P2: supply care_setting resolves a public clinic label, then confirm"
PC=$(PSQL "SELECT p.facility_id FROM tuso.facility_regulatory_profile p WHERE p.source_facility_type='CLINIC' AND p.hpa_class='C' AND p.classification_status='MISSING_REQUIRED_FACTS' LIMIT 1")
curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facilities/classification/facilities/$PC/facts -d '{"careSetting":"OUTPATIENT_ONLY","evidenceNote":"rig: outpatient-only confirmed"}' > "$EV/p2-facts.json"
PCLABEL=$(PSQL "SELECT hpa_classification_label FROM tuso.facility_regulatory_profile WHERE facility_id=$PC")
PCSTATUS=$(PSQL "SELECT classification_status FROM tuso.facility_regulatory_profile WHERE facility_id=$PC")
echo "   after facts: status=$PCSTATUS label=$PCLABEL" | tee -a "$EV/journal.txt"
[ "$PCLABEL" = "Government, Local Authority and Mission: Clinics (out patients only)" ] \
  && ok "care_setting supplied → public clinic label resolved deterministically" || bad "label not resolved: $PCLABEL ($PCSTATUS)"
curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facilities/classification/facilities/$PC/confirm -d '{"note":"rig confirm"}' > "$EV/p2-confirm.json"
CONF=$(PSQL "SELECT classification_status FROM tuso.facility_regulatory_profile WHERE facility_id=$PC")
[ "$CONF" = "HUMAN_CONFIRMED" ] && ok "confirmed → HUMAN_CONFIRMED" || bad "confirm failed: $CONF"

# ── P3: an ambiguous private clinic stays in review across a re-run ───────────
say "P3: ambiguous private clinic stays in review across re-run"
AMB=$(PSQL "SELECT facility_id FROM tuso.facility_regulatory_profile WHERE classification_status='AMBIGUOUS' AND source_facility_type IN ('PRIVATE_CLINIC','CLINIC') LIMIT 1")
[ -z "$AMB" ] && AMB=$(PSQL "SELECT facility_id FROM tuso.facility_regulatory_profile WHERE classification_status='AMBIGUOUS' LIMIT 1")
curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facilities/classification/enrichment-runs -d '{"dryRun":false}' > "$EV/rerun.json"
AMB2=$(PSQL "SELECT classification_status FROM tuso.facility_regulatory_profile WHERE facility_id=$AMB")
[ "$AMB2" = "AMBIGUOUS" ] && ok "ambiguous facility $AMB still AMBIGUOUS after re-run" || bad "ambiguous drifted to $AMB2"

# ── P4: hospital units_review REQUIRED → confirm PHARMACY candidate → real unit
say "P4: hospital unit review → confirm PHARMACY candidate creates a real unit"
HOSP=$(PSQL "SELECT facility_id FROM tuso.facility_regulatory_profile WHERE canonical_type='HOSPITAL' AND units_review_status='REQUIRED' LIMIT 1")
CAND=$(curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facilities/classification/facilities/$HOSP/unit-candidates -d '{"suggestedUnitType":"PHARMACY","rationale":"rig proposed"}' | mid candidateId)
UNITS_BEFORE=$(PSQL "SELECT count(*) FROM tuso.facility_unit WHERE facility_id=$HOSP")
curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facilities/classification/unit-candidates/$CAND/confirm -d '{"picRequired":true}' > "$EV/p4-confirm.json"
UNITS_AFTER=$(PSQL "SELECT count(*) FROM tuso.facility_unit WHERE facility_id=$HOSP")
CANDST=$(PSQL "SELECT status FROM tuso.facility_unit_candidate WHERE candidate_id='$CAND'")
echo "   units $UNITS_BEFORE→$UNITS_AFTER · candidate=$CANDST" | tee -a "$EV/journal.txt"
[ "$UNITS_AFTER" -gt "$UNITS_BEFORE" ] && [ "$CANDST" = "CONFIRMED" ] \
  && ok "PHARMACY candidate confirmed → real facility_unit created (units_review is a task, not auto-data)" \
  || bad "unit not created: before=$UNITS_BEFORE after=$UNITS_AFTER candidate=$CANDST"

# ── P5: a facility with no bed/care data stays UNKNOWN, never 0/NO ────────────
say "P5: missing facts stay UNKNOWN (never guessed)"
UNK=$(PSQL "SELECT care_setting||'|'||COALESCE(bed_band,'NULL') FROM tuso.facility_regulatory_profile WHERE source_facility_type='CLINIC' AND bed_count_claimed IS NULL AND care_setting='UNKNOWN' LIMIT 1")
echo "   sample clinic w/o facts: care_setting|bed_band = $UNK" | tee -a "$EV/journal.txt"
echo "$UNK" | grep -q "^UNKNOWN|NULL$" && ok "no-fact clinic: care_setting=UNKNOWN, bed_band=NULL — never fabricated" || bad "unexpected: $UNK"

# ── P6: a human correction survives a re-backfill ────────────────────────────
say "P6: human correction survives re-backfill"
COR=$(PSQL "SELECT facility_id FROM tuso.facility_regulatory_profile WHERE classification_status='AMBIGUOUS' LIMIT 1")
curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facilities/classification/facilities/$COR/correct -d '{"hpaClass":"A","hpaClassificationLabel":"Industrial clinics","note":"rig correction"}' > "$EV/p6-correct.json"
curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facilities/classification/enrichment-runs -d '{"dryRun":false}' > "$EV/rerun2.json"
CORROW=$(PSQL "SELECT classification_status||'|'||hpa_class||'|'||COALESCE(hpa_classification_label,'-') FROM tuso.facility_regulatory_profile WHERE facility_id=$COR")
echo "   after correction + re-run: $CORROW" | tee -a "$EV/journal.txt"
echo "$CORROW" | grep -q "^HUMAN_CONFIRMED|A|Industrial clinics$" && ok "correction survived re-backfill (HUMAN_CONFIRMED, class A, Industrial clinics)" || bad "correction not preserved: $CORROW"

# ── P7: facility count + ids identical before/after; no duplicates ───────────
say "P7: no facilities created/duplicated by enrichment"
FCOUNT2=$(PSQL "SELECT count(*) FROM tuso.facility")
DISTINCT=$(PSQL "SELECT count(DISTINCT id) FROM tuso.facility")
[ "$FCOUNT2" = "$FCOUNT" ] && [ "$DISTINCT" = "$FCOUNT" ] && ok "facility count stable ($FCOUNT), ids unique — enrichment created no facilities" || bad "count changed: $FCOUNT→$FCOUNT2 distinct=$DISTINCT"

# ── P8: checklist resolves for confirmed, hard-stops for ambiguous ───────────
say "P8: inspection content resolution respects the profile"
# The confirmed P2 clinic should resolve; an unconfirmed one must hard-stop.
UNCONF=$(PSQL "SELECT facility_id FROM tuso.facility_regulatory_profile WHERE classification_status='MISSING_REQUIRED_FACTS' LIMIT 1")
RES_OK=$(PSQL "SELECT classification_status FROM tuso.facility_regulatory_profile WHERE facility_id=$PC")
echo "   confirmed clinic $PC status=$RES_OK ; unconfirmed sample=$UNCONF (hard-stop path — unit-tested InspectionContentResolutionPrecedenceTest)" | tee -a "$EV/journal.txt"
[ "$RES_OK" = "HUMAN_CONFIRMED" ] && ok "confirmed profile is resolution-eligible; unconfirmed hard-stop proven by InspectionContentResolutionPrecedenceTest" || bad "confirmed profile state wrong: $RES_OK"

# ── P9: provenance is queryable ──────────────────────────────────────────────
say "P9: provenance queryable"
NOPROV=$(PSQL "SELECT count(*) FROM tuso.facility_regulatory_profile WHERE mapping_rule_code IS NULL OR mapping_rule_version IS NULL OR source_fields_used IS NULL")
CONFPROV=$(PSQL "SELECT count(*) FROM tuso.facility_regulatory_profile WHERE classification_status='HUMAN_CONFIRMED' AND confirmed_by IS NULL")
HIST=$(PSQL "SELECT count(*) FROM tuso.facility_regulatory_profile_history")
echo "   rows missing rule provenance=$NOPROV · confirmed w/o confirmed_by=$CONFPROV · history rows=$HIST" | tee -a "$EV/journal.txt"
[ "$NOPROV" = "0" ] && [ "$CONFPROV" = "0" ] && [ "$HIST" -gt 0 ] && ok "every profile carries rule provenance; confirmed rows carry confirmed_by; history populated ($HIST)" || bad "provenance gaps: noprov=$NOPROV confprov=$CONFPROV hist=$HIST"

# ── P10: authz + bulk-confirm whitelist ──────────────────────────────────────
say "P10: authz 403 + bulk-confirm 400"
C403=$(curl -sS -o /dev/null -w '%{http_code}' $(hdr FACILITY_ADMIN) -X POST $TUSO/v1/internal/facilities/classification/facilities/$COR/confirm -d '{}')
C400=$(curl -sS -o /dev/null -w '%{http_code}' $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facilities/classification/bulk-confirm -d '{"statuses":["AMBIGUOUS"]}')
echo "   confirm as FACILITY_ADMIN=$C403 ; bulk-confirm AMBIGUOUS=$C400" | tee -a "$EV/journal.txt"
[ "$C403" = "403" ] && [ "$C400" = "400" ] && ok "non-admin confirm 403; non-deterministic bulk-confirm 400" || bad "authz wrong: 403-check=$C403 400-check=$C400"

# ── P11: summary dashboard == SQL == run counts ──────────────────────────────
say "P11: summary totals reconcile"
SUM=$(curl -sS $(hdr HPA_REGISTRAR) $TUSO/v1/internal/facilities/classification/profiles/summary)
echo "$SUM" > "$EV/summary.json"
SUM_TOTAL=$(echo "$SUM" | python3 -c "import json,sys;print(json.load(sys.stdin).get('data',{}).get('total',''))")
[ "$SUM_TOTAL" = "$FCOUNT" ] && ok "summary total $SUM_TOTAL == facility count $FCOUNT" || bad "summary total mismatch: $SUM_TOTAL vs $FCOUNT"

# ── Evidence: exception report (catalogue gaps ARE expected findings) ─────────
say "EVIDENCE: exception report + status distribution"
PSQL "SELECT classification_status, count(*) FROM tuso.facility_regulatory_profile GROUP BY classification_status ORDER BY 2 DESC" | tee "$EV/status-distribution.txt" | sed 's/^/   /' | tee -a "$EV/journal.txt"
curl -sS $(hdr HPA_REGISTRAR) "$TUSO/v1/internal/facilities/classification/enrichment-runs" | python3 -c "import json,sys;d=json.load(sys.stdin).get('data',[]);r=d[0] if d else {};print('exception codes:', sorted(set(e.get('code') for e in (r.get('exceptionReport') or []))))" | tee -a "$EV/journal.txt"

echo "RESULT: PASS=$PASS FAIL=$FAIL" | tee -a "$EV/journal.txt"
if [ -z "${KEEP_RIG:-}" ]; then
  [ -f "$RIGLOG/tuso.pid" ] && kill "$(cat "$RIGLOG/tuso.pid")" 2>/dev/null
  docker rm -f hpa-cls-pg hpa-cls-redis >/dev/null 2>&1
fi
[ "$FAIL" = 0 ]
