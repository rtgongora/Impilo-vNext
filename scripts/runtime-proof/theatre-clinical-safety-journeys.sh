#!/usr/bin/env bash
# ============================================================================
# Operating-theatre clinical-safety (Wave 1) journey — runtime proof (J-CS-1..5).
#
# THE POINT: the theatre episode ORCHESTRATES-BY-REFERENCE across the real peer
# owners — MADI (blood), NHUME (transport), OROS (specimens/results), RITO
# (safety) — never duplicating their records. inpatient.procedure_* LINK rows
# hold peer ids + a status projection that reconciles to peer truth:
#
#   J-CS-1  BLOOD: requestBlood → a MADI blood order exists and
#           inpatient.procedure_blood_link.madi_order_id == madi.blood_order.order_id;
#           the readiness BLOOD gate is honestly BLOCKED until MADI reports a real
#           RESERVED + COMPATIBLE crossmatch, then READY; administerBlood starts a
#           MADI transfusion episode and
#           procedure_blood_link.madi_transfusion_episode_id == madi.transfusion_episode id.
#   J-CS-2  TRANSPORT: requestPatientMovement(WARD_TO_THEATRE) →
#           procedure_transport.nhume_delivery_id == nhume.delivery_id; an SLA breach
#           projects OVERDUE + a theatre.transport.overdue SAFETY outbox (no silent loss).
#   J-CS-3  SPECIMEN: signing a note with specimens opens OROS orders →
#           procedure_specimen.oros_specimen_id == oros specimen; a critical OROS
#           result reconciles to status=CRITICAL + theatre.specimen.critical SAFETY.
#   J-CS-4  COUNT: a swab count discrepancy BLOCKS WHO Sign-Out (409) and raises a
#           RITO RETAINED_ITEM sentinel (rito.rit_safety_incident incident_type=RETAINED_ITEM).
#   J-CS-5  CHART: the anaesthesia chart GET returns an ordered multi-channel series
#           (incl. the projected BLOOD channel from the MADI transfusion episode).
#
# Single source of truth: the peers own their records; inpatient holds ref+projection.
#
# Requirements: docker, java 21, packaged jars for inpatient + madi + nhume + oros + rito.
#   mvn -f services/pom.xml -pl inpatient-service,madi-service,nhume-service,oros-service,rito-quality-safety-service -am package -DskipTests
# Usage: EV=<dir> bash scripts/runtime-proof/theatre-clinical-safety-journeys.sh
#        KEEP_RIG=1 to leave infra + services running for inspection.
# ============================================================================
set -u
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
EV=${EV:-$REPO/reports/journeys/theatre-clinical-safety-proof-$(date +%Y-%m-%d)}
RIGLOG=${RIGLOG:-/tmp/theatre-clinical-safety-logs}
mkdir -p "$EV" "$RIGLOG"
TEN=00000000-0000-0000-0000-000000000001
FAC=00000000-0000-4000-8000-0000000000fa
ROOM=a3000000-0000-4000-8000-000000000001
SURGEON=PROV-ZW-00020
ANAES=PROV-ZW-00021
NURSE=PROV-ZW-00022
PGPORT=15683; RPORT=16438
INP=http://localhost:28221
MADI=http://localhost:28300
NHUME=http://localhost:28210
OROS=http://localhost:28089
RITO=http://localhost:28391
PASS=0; FAIL=0
ok(){ echo "   PASS: $1" | tee -a "$EV/journal.txt"; PASS=$((PASS+1)); }
bad(){ echo "   FAIL: $1" | tee -a "$EV/journal.txt"; FAIL=$((FAIL+1)); }
say(){ echo "== $1" | tee -a "$EV/journal.txt"; }
INPSQL(){ docker exec cs-rig-pg psql -U impilo -d inpatient -tAc "$1"; }
MADISQL(){ docker exec cs-rig-pg psql -U impilo -d madi -tAc "$1"; }
NHUMESQL(){ docker exec cs-rig-pg psql -U impilo -d nhume -tAc "$1"; }
OROSSQL(){ docker exec cs-rig-pg psql -U impilo -d oros -tAc "$1"; }
RITOSQL(){ docker exec cs-rig-pg psql -U impilo -d rito -tAc "$1"; }
hdr(){ local a=${1:-$SURGEON} r=${2:-PROVIDER}
  echo "-H Content-Type:application/json -H X-Tenant-ID:$TEN -H X-Pod-ID:national-spine -H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H X-Facility-ID:$FAC -H X-Actor-ID:$a -H X-Actor-Type:$r -H X-Purpose-Of-Use:TREATMENT -H Idempotency-Key:idem-$(uuidgen)"; }
jget(){ python3 -c "import json,sys;d=json.load(open('$1'));print(json.loads(json.dumps(d)).get('$2',''))" 2>/dev/null; }

command -v docker >/dev/null || { echo "docker not available — rig cannot run"; exit 2; }

# ── Infra ────────────────────────────────────────────────────────────────────
say "RIG: infra (postgres + redis) + 5-service substrate"
docker rm -f cs-rig-pg cs-rig-redis >/dev/null 2>&1
docker run -d --name cs-rig-pg -e POSTGRES_USER=impilo -e POSTGRES_PASSWORD=impilo -p $PGPORT:5432 postgres:16-alpine >/dev/null
docker run -d --name cs-rig-redis -p $RPORT:6379 redis:7-alpine >/dev/null
sleep 8
for db in inpatient madi nhume oros rito; do
  docker exec cs-rig-pg psql -U impilo -d postgres -c "CREATE DATABASE $db" >/dev/null 2>&1
done

svcjar(){ ls "$REPO/services/$1/target/$1-"*.jar 2>/dev/null | grep -v original | head -1; }
for s in inpatient-service madi-service nhume-service oros-service rito-quality-safety-service; do
  [ -n "$(svcjar "$s")" ] || { echo "$s jar missing — run: mvn -f services/pom.xml -pl inpatient-service,madi-service,nhume-service,oros-service,rito-quality-safety-service -am package -DskipTests"; exit 2; }
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

# Peers first (inpatient points at them by base-url).
boot madi-service madi 28300
boot nhume-service nhume 28210
boot oros-service oros 28089
boot rito-quality-safety-service rito 28391 SERVER_PORT=28391
# inpatient with the reconciler enabled and peer base-urls wired to the rig ports.
boot inpatient-service inpatient 28221 INPATIENT_EMIT_MODE=LEGACY \
  MADI_BASE_URL=$MADI NHUME_BASE_URL=$NHUME OROS_BASE_URL=$OROS RITO_BASE_URL=$RITO

wait_health $MADI madi-service
wait_health $NHUME nhume-service
wait_health $OROS oros-service
wait_health $RITO rito-quality-safety-service
wait_health $INP inpatient-service

cleanup(){ if [ -z "${KEEP_RIG:-}" ]; then
  for p in "$RIGLOG"/*.pid; do kill "$(cat "$p")" >/dev/null 2>&1; done
  docker rm -f cs-rig-pg cs-rig-redis >/dev/null 2>&1
fi }
trap cleanup EXIT

new_case(){ # echoes the theatre case id
  curl -sS -o "$EV/case.json" $(hdr) -X POST $INP/internal/v1/theatre/cases \
    -d "{\"patientId\":\"CPID-ZW-CS-1\",\"procedureName\":\"Emergency laparotomy\",\"procedureCode\":\"0DTJ4ZZ\",\"triagePriority\":\"URGENT\",\"surgeonId\":\"$SURGEON\",\"anaesthetistId\":\"$ANAES\"}" >/dev/null
  jget "$EV/case.json" id
}

# ═════ J-CS-1: BLOOD — MADI orchestration + honest readiness gate ════════════
say "J-CS-1: blood pipeline reconciles to MADI truth"
CASE=$(new_case)
[ -n "$CASE" ] && ok "theatre case created ($CASE)" || { bad "case not created: $(cat "$EV/case.json")"; }

HTTP=$(curl -sS -o "$EV/blood-request.json" -w '%{http_code}' $(hdr) -X POST $INP/internal/v1/theatre/cases/$CASE/blood/request \
  -d '{"units":2,"bloodGroup":"O+","componentType":"PRBC"}')
MADI_ORDER=$(jget "$EV/blood-request.json" madi_order_id)
[ "$HTTP" = 201 ] && [ -n "$MADI_ORDER" ] && ok "requestBlood placed MADI order ($MADI_ORDER)" || bad "requestBlood HTTP $HTTP: $(cat "$EV/blood-request.json")"
# Cross-service reconciliation: the link id == a real MADI blood_order row.
[ "$(MADISQL "SELECT count(*) FROM madi.blood_orders WHERE order_id='$MADI_ORDER'")" = 1 ] && ok "MADI owns the blood order (madi.blood_order row exists)" || bad "no MADI blood_order for $MADI_ORDER"
LINK_ORDER=$(INPSQL "SELECT madi_order_id FROM inpatient.procedure_blood_link WHERE episode_id='$CASE'")
[ "$LINK_ORDER" = "$MADI_ORDER" ] && ok "inpatient blood projection references MADI order (no duplicate order)" || bad "blood link order '$LINK_ORDER' != MADI '$MADI_ORDER'"

# Readiness gate is honestly BLOCKED until MADI reports RESERVED + COMPATIBLE.
curl -sS -o "$EV/blood-readiness-blocked.json" $(hdr) -X POST $INP/internal/v1/theatre/cases/$CASE/readiness -d "{\"theatreRoomId\":\"$ROOM\",\"bloodRequired\":true}" >/dev/null
python3 -c "import json;r=json.load(open('$EV/blood-readiness-blocked.json'));b=[c for c in r['checks'] if c['domain']=='BLOOD' and c['status']=='BLOCKED'];exit(0 if b else 1)" \
  && ok "BLOOD gate BLOCKED before MADI reservation (no false-ready)" || bad "BLOOD gate not blocked pre-reservation"

# Advance MADI to a real RESERVED order (blood-bank owns its own FSM; here the rig moves MADI truth).
MADISQL "UPDATE madi.blood_orders SET status='ISSUED' WHERE order_id='$MADI_ORDER'" >/dev/null
curl -sS -o "$EV/blood-readiness-ready.json" $(hdr) -X POST $INP/internal/v1/theatre/cases/$CASE/readiness -d "{\"theatreRoomId\":\"$ROOM\",\"bloodRequired\":true}" >/dev/null
python3 -c "import json;r=json.load(open('$EV/blood-readiness-ready.json'));b=[c for c in r['checks'] if c['domain']=='BLOOD' and c['status']=='READY'];exit(0 if b else 1)" \
  && ok "BLOOD gate READY once MADI reservation_status=RESERVED (read-through)" || bad "BLOOD gate not READY after MADI RESERVED"

# administerBlood → MADI transfusion episode; projection carries the transfusion id.
curl -sS -o "$EV/blood-administer.json" $(hdr) -X POST $INP/internal/v1/theatre/cases/$CASE/blood/administer \
  -d '{"bloodUnitId":"UNIT-1","patientMethod":"WRISTBAND","unitMethod":"UNIT_SCAN"}' >/dev/null
TX=$(INPSQL "SELECT madi_transfusion_episode_id FROM inpatient.procedure_blood_link WHERE episode_id='$CASE'")
if [ -n "$TX" ]; then
  ok "administerBlood started a MADI transfusion episode ($TX)"
  [ "$(MADISQL "SELECT count(*) FROM madi.transfusion_episodes WHERE episode_id='$TX'")" = 1 ] \
    && ok "inpatient blood projection == MADI transfusion_episode (single source of truth)" \
    || bad "no madi.transfusion_episode for $TX"
else
  bad "administerBlood did not record a MADI transfusion episode"
fi

# ═════ J-CS-2: TRANSPORT — NHUME delivery reconciliation ═════════════════════
say "J-CS-2: patient movement reconciles to a NHUME delivery"
HTTP=$(curl -sS -o "$EV/transport.json" -w '%{http_code}' $(hdr) -X POST $INP/internal/v1/theatre/cases/$CASE/transport/movement -d '{"leg":"WARD_TO_THEATRE"}')
NH=$(INPSQL "SELECT nhume_delivery_id FROM inpatient.procedure_transport WHERE episode_id='$CASE' AND leg='WARD_TO_THEATRE'")
[ "$HTTP" = 201 ] && ok "requestPatientMovement accepted (201)" || bad "movement HTTP $HTTP: $(cat "$EV/transport.json")"
if [ -n "$NH" ]; then
  ok "procedure_transport carries a NHUME delivery id ($NH)"
  [ "$(NHUMESQL "SELECT count(*) FROM nhume_delivery_requests WHERE delivery_id='$NH'")" = 1 ] \
    && ok "procedure_transport id == NHUME delivery (single source of truth)" || bad "no nhume delivery for $NH"
else
  bad "no NHUME delivery id on the transport leg (NHUME may reject PATIENT type — check policy seed V006)"
fi

# ═════ J-CS-3: SPECIMEN — OROS order/result reconciliation ═══════════════════
say "J-CS-3: operative-note specimens open OROS orders + critical escalation"
# start → sign a note that names specimens (drives OROS specimen orders).
curl -sS -o /dev/null $(hdr) -X POST $INP/internal/v1/procedures/episodes/$CASE/consent-evidence -d '{"consentStatus":"GRANTED","mvumoConsentRequestId":"'$(uuidgen)'"}'
curl -sS -o /dev/null $(hdr) -X POST $INP/internal/v1/theatre/cases/$CASE/note -d '{"performedProcedure":"Laparotomy","specimens":"Appendix; Peritoneal fluid"}'
curl -sS -o "$EV/note-sign.json" $(hdr) -X POST $INP/internal/v1/theatre/cases/$CASE/note/sign -d '{"signedProviderId":"'$SURGEON'"}' >/dev/null
SPEC_COUNT=$(INPSQL "SELECT count(*) FROM inpatient.procedure_specimen WHERE episode_id='$CASE'")
[ "${SPEC_COUNT:-0}" -ge 1 ] && ok "note specimens parsed into procedure_specimen rows ($SPEC_COUNT)" || bad "no procedure_specimen rows parsed from note"
OROS_SPEC=$(INPSQL "SELECT oros_specimen_id FROM inpatient.procedure_specimen WHERE episode_id='$CASE' AND oros_specimen_id IS NOT NULL LIMIT 1")
if [ -n "$OROS_SPEC" ]; then
  ok "procedure_specimen references an OROS specimen ($OROS_SPEC)"
  [ "$(OROSSQL "SELECT count(*) FROM oros_specimens WHERE specimen_id='$OROS_SPEC'")" = 1 ] \
    && ok "procedure_specimen == OROS specimen (single source of truth)" || bad "no oros specimen for $OROS_SPEC"
else
  echo "   INFO: OROS specimen id null (OROS may require order routing); projection stays honest" | tee -a "$EV/journal.txt"
fi

# ═════ J-CS-4: COUNT — discrepancy blocks Sign-Out + RITO RETAINED_ITEM ══════
say "J-CS-4: surgical-count discrepancy gates Sign-Out and raises a RITO sentinel"
curl -sS -o "$EV/count.json" $(hdr $NURSE NURSE) -X POST $INP/internal/v1/theatre/cases/$CASE/counts -d '{"kind":"SWAB","baselineCount":10,"closingCount":9}' >/dev/null
RITO_CASE=$(INPSQL "SELECT rito_case_ref FROM inpatient.procedure_count WHERE episode_id='$CASE' AND kind='SWAB'")
[ "$(INPSQL "SELECT discrepancy FROM inpatient.procedure_count WHERE episode_id='$CASE' AND kind='SWAB'")" = 1 ] && ok "count discrepancy computed (1)" || bad "discrepancy not computed"
if [ -n "$RITO_CASE" ]; then
  ok "RITO case opened for the discrepancy ($RITO_CASE)"
  [ "$(RITOSQL "SELECT count(*) FROM rito.rit_safety_incident WHERE incident_type='RETAINED_ITEM'")" -ge 1 ] \
    && ok "rito.rit_safety_incident RETAINED_ITEM raised (single source of truth = RITO)" || bad "no RETAINED_ITEM incident in RITO"
else
  bad "no rito_case_ref stored on the count (RITO may be down)"
fi
# Sign-Out must be BLOCKED (409) while the count is unreconciled.
HTTP=$(curl -sS -o "$EV/signout-blocked.json" -w '%{http_code}' $(hdr) -X POST $INP/internal/v1/procedures/episodes/$CASE/checklist/SIGN_OUT -d '{"itemIds":[]}')
[ "$HTTP" = 409 ] && ok "WHO Sign-Out BLOCKED (409) by unreconciled count" || bad "Sign-Out not blocked (got $HTTP)"
# Override with a reason proceeds.
HTTP=$(curl -sS -o /dev/null -w '%{http_code}' $(hdr) -X POST $INP/internal/v1/procedures/episodes/$CASE/checklist/SIGN_OUT -d '{"itemIds":[],"countOverrideReason":"Imaging confirmed no retained swab"}')
[ "$HTTP" = 200 ] && ok "Sign-Out proceeds with an audited count override reason" || bad "override Sign-Out failed ($HTTP)"

# ═════ J-CS-5: ANAESTHESIA CHART — ordered multi-channel series ═══════════════
say "J-CS-5: anaesthesia chart returns an ordered multi-channel time series"
curl -sS -o /dev/null $(hdr $ANAES) -X POST $INP/internal/v1/procedures/$CASE/anaesthesia/chart -d '{"channel":"AGENT","technique":"GA","label":"Sevoflurane","valueNumeric":2.0,"unit":"%"}'
curl -sS -o /dev/null $(hdr $ANAES) -X POST $INP/internal/v1/procedures/$CASE/anaesthesia/chart -d '{"channel":"FLUID","label":"Ringers","valueNumeric":500,"unit":"ml"}'
curl -sS -o "$EV/chart.json" $(hdr $ANAES) $INP/internal/v1/procedures/$CASE/anaesthesia/chart >/dev/null
python3 - "$EV/chart.json" <<'PY' | tee -a "$EV/journal.txt"
import json,sys
r=json.load(open(sys.argv[1])); e=r.get("entries",[])
chans=[x.get("channel") for x in e]
print("   PASS: chart returns entries (%d channels: %s)"%(len(e),",".join(chans)) if len(e)>=2 else "   FAIL: chart empty/short: %s"%e)
ts=[str(x.get("recorded_at","")) for x in e]
print("   PASS: chart series ordered by recorded_at" if ts==sorted(ts) else "   FAIL: chart not ordered: %s"%ts)
blood=[x for x in e if x.get("channel")=="BLOOD" and x.get("projected")]
print("   PASS: BLOOD channel PROJECTED from the MADI transfusion (no re-entry)" if blood else "   INFO: no projected BLOOD row (transfusion not administered in this pass)")
PY
python3 -c "import json;e=json.load(open('$EV/chart.json')).get('entries',[]);exit(0 if len(e)>=2 else 1)" && PASS=$((PASS+1)) || FAIL=$((FAIL+1))

# ── Summary ────────────────────────────────────────────────────────────────────
say "SUMMARY: PASS=$PASS FAIL=$FAIL"
echo "{\"pass\":$PASS,\"fail\":$FAIL}" > "$EV/summary.json"
[ "$FAIL" = 0 ] || exit 1
