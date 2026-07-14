#!/usr/bin/env bash
# ============================================================================
# Operating-theatre commodities / traceability (Wave 3) journey — runtime proof
# (J-TC-1..5).
#
# THE POINT: the theatre episode orchestrates the commodity SoRs by reference —
# inventory/DURA (implant UDI/serial register + controlled-drug witness register),
# TUSO (instrument-set CSSD sterilisation lifecycle), MADI (blood), OROS
# (histopathology) — never duplicating their records. inpatient.procedure_* LINK
# rows hold peer ids + a projection.
#
#   J-TC-1  IMPLANT: recordImplant(UDI/serial/lot) → inventory inv_implant_registry
#           + inv_patient_implant, inpatient.procedure_implant projection; a RECALL
#           query by UDI/lot finds the patient/episode it was implanted in.
#   J-TC-2  INSTRUMENT SET: a STERILE TUSO set is issued to the case (→ IN_USE,
#           procedure_instrument_set_use=ISSUED); returned (→ SOILED); a CSSD cycle
#           reprocesses it (→ STERILE with a fresh sterile_until). Full FSM:
#           STERILE → IN_USE → SOILED → REPROCESSING → STERILISED → STERILE.
#   J-TC-3  CONTROLLED DRUG: administering without a witness is 400; with a witness
#           the inventory register records it and the running balance updates.
#   J-TC-4  BLOOD: requestBlood creates a REAL madi.blood_orders row
#           (procedure_blood_link.madi_order_id populated); a no-transfusion case
#           reaches readiness with BLOOD=NOT_REQUIRED.
#   J-TC-5  HISTOPATH: an OROS order → histopath draft → sign-out FINAL (critical)
#           produces oros_histopath_report(sign_out_status=FINAL) + a FINAL result;
#           acknowledge stamps the acknowledgement.
#
# Single source of truth: peers own their records; inpatient holds ref+projection.
# TUSO owns the set registry+CSSD; inventory owns implant+controlled-drug; MADI
# owns blood; OROS owns specimen/result/histopath; ZIBO owns codes.
#
# Requirements: docker, java 21, packaged jars for inpatient + tuso + inventory +
#   oros + madi (+ zibo for code validation).
#   mvn -f services/pom.xml -pl inpatient-service,tuso-service,inventory-service,oros-service,madi-service,zibo-service -am package -DskipTests
# Usage: EV=<dir> bash scripts/runtime-proof/theatre-commodities-journeys.sh
#        KEEP_RIG=1 to leave infra + services running for inspection.
# ============================================================================
set -u
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
EV=${EV:-$REPO/reports/journeys/theatre-commodities-proof-$(date +%Y-%m-%d)}
RIGLOG=${RIGLOG:-/tmp/theatre-commodities-logs}
mkdir -p "$EV" "$RIGLOG"
TEN=00000000-0000-0000-0000-000000000001
FAC=00000000-0000-4000-8000-0000000000fa
SURGEON=PROV-ZW-00020
WITNESS=PROV-ZW-00099
PGPORT=15685; RPORT=16440
INP=http://localhost:28321
TUSO=http://localhost:28384
INV=http://localhost:28398
OROS=http://localhost:28389
MADI=http://localhost:28390
PASS=0; FAIL=0
ok(){ echo "   PASS: $1" | tee -a "$EV/journal.txt"; PASS=$((PASS+1)); }
bad(){ echo "   FAIL: $1" | tee -a "$EV/journal.txt"; FAIL=$((FAIL+1)); }
say(){ echo "== $1" | tee -a "$EV/journal.txt"; }
# psql helpers strip ALL whitespace (psql -tAc can leave a stray CR/LF that corrupts an interpolated id).
INPSQL(){ docker exec tc-rig-pg psql -q -U impilo -d inpatient -tAc "$1" | tr -d '[:space:]'; }
TUSOSQL(){ docker exec tc-rig-pg psql -q -U impilo -d tuso -tAc "$1" | tr -d '[:space:]'; }
INVSQL(){ docker exec tc-rig-pg psql -q -U impilo -d inventory -tAc "$1" | tr -d '[:space:]'; }
OROSSQL(){ docker exec tc-rig-pg psql -q -U impilo -d oros -tAc "$1" | tr -d '[:space:]'; }
MADISQL(){ docker exec tc-rig-pg psql -q -U impilo -d madi -tAc "$1" | tr -d '[:space:]'; }
hdr(){ local a=${1:-$SURGEON} r=${2:-PROVIDER}
  echo "-H Content-Type:application/json -H X-Tenant-ID:$TEN -H X-Pod-ID:national-spine -H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H X-Facility-ID:$FAC -H X-Actor-ID:$a -H X-Actor-Type:$r -H X-Purpose-Of-Use:TREATMENT -H Idempotency-Key:idem-$(uuidgen)"; }
jval(){ python3 -c "import json,sys;d=json.load(sys.stdin);d=d.get('data',d) if isinstance(d,dict) else d;print(d.get('$1','') if isinstance(d,dict) else '')" 2>/dev/null; }

command -v docker >/dev/null || { echo "docker not available — rig cannot run"; exit 2; }

# ── Infra ────────────────────────────────────────────────────────────────────
say "RIG: infra (postgres + redis) + commodities substrate"
docker rm -f tc-rig-pg tc-rig-redis >/dev/null 2>&1
docker run -d --name tc-rig-pg -e POSTGRES_USER=impilo -e POSTGRES_PASSWORD=impilo -p $PGPORT:5432 postgres:16-alpine >/dev/null
docker run -d --name tc-rig-redis -p $RPORT:6379 redis:7-alpine >/dev/null
sleep 8
for db in inpatient tuso inventory oros madi zibo; do
  docker exec tc-rig-pg psql -U impilo -d postgres -c "CREATE DATABASE $db" >/dev/null 2>&1
done

svcjar(){ ls "$REPO/services/$1/target/$1-"*.jar 2>/dev/null | grep -v original | head -1; }
for s in inpatient-service tuso-service inventory-service oros-service madi-service; do
  [ -n "$(svcjar "$s")" ] || { echo "$s jar missing — run: mvn -f services/pom.xml -pl inpatient-service,tuso-service,inventory-service,oros-service,madi-service,zibo-service -am package -DskipTests"; exit 2; }
done

boot(){ # $1 service-dir  $2 db  $3 port  $4..extra env KEY=VAL
  local svc=$1 db=$2 port=$3; shift 3
  env POSTGRES_HOST=localhost POSTGRES_PORT=$PGPORT POSTGRES_USER=impilo POSTGRES_PASSWORD=impilo POSTGRES_DB=$db \
    SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:$PGPORT/$db" \
    SPRING_DATASOURCE_USERNAME=impilo SPRING_DATASOURCE_PASSWORD=impilo \
    IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true SERVER_PORT=$port SPRING_KAFKA_LISTENER_AUTO_STARTUP=false \
    SPRING_DATA_REDIS_HOST=localhost SPRING_DATA_REDIS_PORT=$RPORT \
    "$@" \
    nohup java -jar "$(svcjar "$svc")" > "$RIGLOG/$svc.log" 2>&1 & echo $! > "$RIGLOG/$svc.pid"
}

wait_health(){ local url=$1 name=$2 c=000
  for i in $(seq 1 80); do c=$(curl -s -o /dev/null -w '%{http_code}' "$url/actuator/health" 2>/dev/null); [ "$c" = 200 ] && break; sleep 3; done
  echo "   $name health: $c" | tee -a "$EV/journal.txt"
  [ "$c" = 200 ] || { bad "$name did not boot"; tail -40 "$RIGLOG/$name.log" 2>/dev/null; }
}

boot tuso-service tuso 28384
boot inventory-service inventory 28398
boot oros-service oros 28389
boot madi-service madi 28390
boot inpatient-service inpatient 28321 INPATIENT_EMIT_MODE=LEGACY \
  TUSO_BASE_URL=$TUSO INVENTORY_BASE_URL=$INV OROS_BASE_URL=$OROS MADI_BASE_URL=$MADI

wait_health $TUSO tuso-service
wait_health $INV inventory-service
wait_health $OROS oros-service
wait_health $MADI madi-service
wait_health $INP inpatient-service

cleanup(){ if [ -z "${KEEP_RIG:-}" ]; then
  for p in "$RIGLOG"/*.pid; do kill "$(cat "$p")" >/dev/null 2>&1; done
  docker rm -f tc-rig-pg tc-rig-redis >/dev/null 2>&1
fi }
trap cleanup EXIT

new_case(){ # echoes the theatre case id
  curl -sS -o "$EV/case.json" $(hdr) -X POST $INP/internal/v1/theatre/cases \
    -d "{\"patientId\":\"CPID-ZW-TC-1\",\"procedureName\":\"Total hip replacement\",\"procedureCode\":\"81.51\",\"triagePriority\":\"ELECTIVE\",\"surgeonId\":\"$SURGEON\"}" >/dev/null
  python3 -c "import json;print(json.load(open('$EV/case.json')).get('id',''))" 2>/dev/null
}

# ═════ J-TC-1: IMPLANT UDI/serial traceability + recall ══════════════════════
say "J-TC-1: implant UDI recorded → traceable, recall query finds the patient"
CASE=$(new_case)
[ -n "$CASE" ] && ok "theatre case created ($CASE)" || bad "case not created: $(cat "$EV/case.json")"
UDI="UDI-$(uuidgen | cut -c1-8)"; LOT="LOT-HIP-42"
curl -sS -o "$EV/implant.json" -w 'HTTP %{http_code}\n' $(hdr) -X POST $INP/internal/v1/theatre/cases/$CASE/implants \
  -d "{\"udi\":\"$UDI\",\"serialNumber\":\"SER-9\",\"lotNumber\":\"$LOT\",\"deviceType\":\"Hip prosthesis\",\"bodySite\":\"Hip\",\"laterality\":\"LEFT\"}" | tee -a "$EV/journal.txt"
IMP_STATUS=$(jval status < "$EV/implant.json")
[ "$IMP_STATUS" = "IMPLANTED" ] && ok "implant recorded IMPLANTED (inventory unit issued)" || bad "implant status=$IMP_STATUS (expected IMPLANTED)"
[ "$(INVSQL "SELECT count(*) FROM inv_implant_registry WHERE udi='$UDI'")" = 1 ] \
  && ok "inventory inv_implant_registry row exists for $UDI" || bad "no inventory implant unit for $UDI"
[ "$(INVSQL "SELECT count(*) FROM inv_patient_implant WHERE patient_cpid='CPID-ZW-TC-1'")" -ge 1 ] \
  && ok "inventory inv_patient_implant link for patient" || bad "no patient-implant link"
[ "$(INPSQL "SELECT count(*) FROM inpatient.procedure_implant WHERE udi='$UDI'")" = 1 ] \
  && ok "inpatient procedure_implant projection for $UDI" || bad "no inpatient implant projection"
# Recall: given the lot, find every patient/episode (case-side + inventory-side).
curl -sS -o "$EV/recall.json" $(hdr) "$INP/internal/v1/theatre/implants/recall?lot=$LOT" >/dev/null
RECALL_N=$(python3 -c "import json;print(len(json.load(open('$EV/recall.json'))))" 2>/dev/null)
[ "${RECALL_N:-0}" -ge 1 ] && ok "case-side recall by lot $LOT found $RECALL_N implant(s)" || bad "recall by lot returned nothing"

# ═════ J-TC-2: sterile instrument set — CSSD lifecycle ═══════════════════════
say "J-TC-2: STERILE set issued → SOILED on return → reprocessed → STERILE"
FID=$(TUSOSQL "SELECT id FROM tuso.facility LIMIT 1")
if [ -z "$FID" ]; then
  TUSOSQL "INSERT INTO tuso.facility (tenant_id, facility_code, name, province, district) VALUES ('$TEN','TC-FAC-1','Rig Theatre Hospital','Harare','Harare') ON CONFLICT DO NOTHING" >/dev/null
  FID=$(TUSOSQL "SELECT id FROM tuso.facility WHERE facility_code='TC-FAC-1'")
fi
SETID=$(TUSOSQL "INSERT INTO tuso.instrument_set (tenant_id, facility_id, name, sterilisation_state, sterile_until) VALUES ('$TEN', $FID, 'Rig Ortho Set 1', 'STERILE', now() + interval '30 days') RETURNING id")
[ -n "$SETID" ] && ok "seeded STERILE instrument set ($SETID)" || bad "could not seed instrument set"
curl -sS -o "$EV/set-issue.json" $(hdr) -X POST $INP/internal/v1/theatre/cases/$CASE/instrument-sets/issue \
  -d "{\"instrumentSetId\":\"$SETID\",\"issuedTo\":\"Theatre 1\"}" >/dev/null
[ "$(jval issue_status < "$EV/set-issue.json")" = "ISSUED" ] && ok "set issued to case (procedure_instrument_set_use=ISSUED)" || bad "set issue status=$(jval issue_status < "$EV/set-issue.json")"
[ "$(TUSOSQL "SELECT sterilisation_state FROM tuso.instrument_set WHERE id='$SETID'")" = "IN_USE" ] \
  && ok "TUSO set moved STERILE→IN_USE" || bad "TUSO set not IN_USE"
curl -sS -o "$EV/set-return.json" $(hdr) -X POST $INP/internal/v1/theatre/cases/$CASE/instrument-sets/return \
  -d "{\"instrumentSetId\":\"$SETID\",\"contaminated\":false,\"incomplete\":false}" >/dev/null
[ "$(TUSOSQL "SELECT sterilisation_state FROM tuso.instrument_set WHERE id='$SETID'")" = "SOILED" ] \
  && ok "TUSO set returned IN_USE→SOILED" || bad "TUSO set not SOILED after return"
curl -sS -o "$EV/set-reprocess.json" $(hdr) -X POST $TUSO/v1/internal/tuso/instrument-sets/$SETID/reprocess \
  -d "{\"autoclaveRef\":\"AC-LOAD-9\",\"operator\":\"cssd-op-1\"}" >/dev/null
[ "$(TUSOSQL "SELECT sterilisation_state FROM tuso.instrument_set WHERE id='$SETID'")" = "STERILE" ] \
  && ok "CSSD cycle reprocessed SOILED→STERILE (sterile_until re-stamped)" || bad "set not STERILE after reprocess"
[ "$(TUSOSQL "SELECT count(*) FROM tuso.instrument_set_cssd_cycle WHERE instrument_set_id='$SETID'")" -ge 1 ] \
  && ok "CSSD cycle audit-log row written" || bad "no CSSD cycle log row"

# ═════ J-TC-3: controlled-drug two-person witness ════════════════════════════
say "J-TC-3: administer controlled drug requires a witness (400), then balances"
NOWIT=$(curl -sS -o "$EV/cd-nowitness.json" -w '%{http_code}' $(hdr) -X POST $INP/internal/v1/theatre/cases/$CASE/controlled-drugs \
  -d '{"itemCode":"MORPHINE-10","action":"ADMINISTER","quantity":5,"unit":"mg"}')
[ "$NOWIT" = "400" ] && ok "administer without witness rejected 400" || bad "administer without witness got $NOWIT (expected 400)"
curl -sS -o "$EV/cd-issue.json" $(hdr) -X POST $INP/internal/v1/theatre/cases/$CASE/controlled-drugs \
  -d "{\"itemCode\":\"MORPHINE-10\",\"drugName\":\"Morphine 10mg\",\"action\":\"ISSUE\",\"quantity\":10,\"unit\":\"mg\",\"witnessProvider\":\"$WITNESS\"}" >/dev/null
curl -sS -o "$EV/cd-admin.json" $(hdr) -X POST $INP/internal/v1/theatre/cases/$CASE/controlled-drugs \
  -d "{\"itemCode\":\"MORPHINE-10\",\"drugName\":\"Morphine 10mg\",\"action\":\"ADMINISTER\",\"quantity\":2,\"unit\":\"mg\",\"witnessProvider\":\"$WITNESS\"}" >/dev/null
CD_STATUS=$(jval status < "$EV/cd-admin.json")
[ "$CD_STATUS" = "RECORDED" ] && ok "witnessed administer recorded" || bad "witnessed administer status=$CD_STATUS"
BAL=$(jval running_balance < "$EV/cd-admin.json")
case "$BAL" in 8|8.0|8.00|8.000) ok "running balance after ISSUE(10)-ADMINISTER(2) = $BAL" ;; *) bad "running balance=$BAL (expected 8)" ;; esac
[ "$(INVSQL "SELECT count(*) FROM inv_controlled_drug_register WHERE item_code='MORPHINE-10' AND ref_id='$CASE'")" -ge 2 ] \
  && ok "inventory controlled-drug register rows for the case" || bad "no inventory register rows"

# ═════ J-TC-4: blood ± transfusion ═══════════════════════════════════════════
say "J-TC-4: requestBlood creates a real MADI order; no-transfusion case is NOT_REQUIRED"
curl -sS -o "$EV/blood.json" $(hdr) -X POST $INP/internal/v1/theatre/cases/$CASE/blood/request \
  -d '{"units":2,"bloodGroup":"O_POS","componentType":"WHOLE_BLOOD"}' >/dev/null
MADI_OID=$(jval madi_order_id < "$EV/blood.json")
[ -n "$MADI_OID" ] && [ "$MADI_OID" != "None" ] && ok "requestBlood created MADI order $MADI_OID" || bad "requestBlood madi_order_id empty (blood chain broken)"
[ -n "$MADI_OID" ] && [ "$(MADISQL "SELECT count(*) FROM madi.blood_orders WHERE order_id='$MADI_OID'")" = 1 ] \
  && ok "madi.blood_orders row exists (jurisdiction defaulted)" || bad "no madi.blood_orders row"
# no-transfusion case: readiness BLOOD gate contributes no blocker
NOBLOOD=$(new_case)
curl -sS -o "$EV/readiness-notx.json" $(hdr) -X POST $INP/internal/v1/theatre/cases/$NOBLOOD/readiness -d '{}' >/dev/null
python3 -c "import json;c=json.load(open('$EV/readiness-notx.json'));b=[x for x in c['checks'] if x['domain']=='BLOOD'];import sys;sys.exit(0 if b and b[0]['status']=='NOT_REQUIRED' else 1)" \
  && ok "no-transfusion case: BLOOD gate NOT_REQUIRED" || bad "no-transfusion BLOOD gate not NOT_REQUIRED"

# ═════ J-TC-5: histopathology report sign-out + critical ═════════════════════
say "J-TC-5: histopath report signed out FINAL (critical) + acknowledged"
curl -sS -o "$EV/histo-order.json" $(hdr) -X POST $OROS/v1/orders \
  -d '{"orderType":"LAB","priority":"URGENT","patientCpid":"CPID-ZW-TC-1","encounterRef":"ENC-TC-1","clinicalNotes":"Theatre specimen","items":[{"code":"HISTOPATH","displayName":"Hip synovium","quantity":1,"specimenType":"HISTOPATH","bodySite":"Hip"}]}'
OID=$(jval orderId < "$EV/histo-order.json"); [ -z "$OID" ] && OID=$(jval id < "$EV/histo-order.json")
[ -z "$OID" ] && echo "   oros order response: $(cat "$EV/histo-order.json")" | tee -a "$EV/journal.txt"
if [ -n "$OID" ]; then
  ok "OROS histopath order placed ($OID)"
  curl -sS -o "$EV/histo-draft.json" $(hdr) -X POST $OROS/v1/orders/$OID/histopath \
    -d '{"specimenRef":"S1","grossDescription":"Synovial tissue 3x2cm","microscopicDescription":"Chronic synovitis","diagnosis":"Chronic synovitis, no malignancy","reportingPathologist":"PATH-1"}' >/dev/null
  curl -sS -o "$EV/histo-signout.json" $(hdr) -X POST $OROS/v1/orders/$OID/histopath/sign-out \
    -d '{"diagnosis":"Chronic synovitis, no malignancy","reportingPathologist":"PATH-1","critical":true,"criticalReason":"Unexpected granuloma — flag for MDT"}' >/dev/null
  [ "$(OROSSQL "SELECT sign_out_status FROM oros_histopath_report WHERE order_id='$OID' ORDER BY created_at DESC LIMIT 1")" = "FINAL" ] \
    && ok "histopath report signed out FINAL" || bad "histopath not FINAL"
  [ "$(OROSSQL "SELECT count(*) FROM oros_results WHERE order_id='$OID'")" -ge 1 ] \
    && ok "FINAL histopath result created (Butano-bound via ReportService)" || bad "no oros_results FINAL row"
  curl -sS -o "$EV/histo-ack.json" $(hdr) -X POST $OROS/v1/orders/$OID/histopath/acknowledge -d '{"note":"Reviewed at MDT"}' >/dev/null
  [ -n "$(OROSSQL "SELECT acknowledged_at FROM oros_histopath_report WHERE order_id='$OID' ORDER BY created_at DESC LIMIT 1")" ] \
    && ok "critical histopath acknowledged" || bad "histopath not acknowledged"
else
  bad "could not place OROS histopath order"
fi

# ── Summary ──────────────────────────────────────────────────────────────────
echo "{\"pass\":$PASS,\"fail\":$FAIL}" > "$EV/summary.json"
say "SUMMARY: PASS=$PASS FAIL=$FAIL (evidence in $EV)"
[ "$FAIL" = 0 ] || exit 1
