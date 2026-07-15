#!/usr/bin/env bash
# ============================================================================
# Trauma-care pipeline — canonical episode spine runtime proof (J-TR-0).
#
# THE POINT: one injured patient = ONE canonical trauma_episode_id. DAIDZAI owns
# a thin correlation spine (dai_trauma_episode + read-model dai_trauma_episode_
# phase); each phase owner (PCT ED, inpatient resus, MADI blood) keeps its own
# system-of-record rows and STAMPS the shared id. The mint is an idempotent
# dual-entry contract: DAIDZAI mints on incident triage, PCT mints on ED-first
# walk-in trauma.
#
#   J-TR-0  incident triage mints the episode + stamps the incident; PCT ED
#           visit, inpatient resuscitation_record, and MADI blood_order all
#           carry the SAME trauma_episode_id; the DAIDZAI phase timeline resolves
#           (INCIDENT -> ED -> RESUS -> BLOOD); re-mint on the same origin key is
#           idempotent (no forked spine); and an ED-first walk-in trauma mints a
#           distinct ED_WALK_IN episode via PCT.
#
# Events are proven as DB rows (Kafka listener autostart OFF). Assertions via
# `docker exec tr-rig-pg psql`. PASS/FAIL counters; KEEP_RIG=1 leaves it up.
#
# Requirements: docker, java 21, packaged jars for daidzai + pct + inpatient + madi.
#   mvn -f services/pom.xml -pl daidzai-service,pct-service,inpatient-service,madi-service -am package -DskipTests
# Usage: EV=<dir> bash scripts/runtime-proof/trauma-spine-journeys.sh
#        KEEP_RIG=1 to leave infra + services running for inspection.
# ============================================================================
set -u
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
EV=${EV:-$REPO/reports/journeys/trauma-spine-proof-$(date +%Y-%m-%d)}
RIGLOG=${RIGLOG:-/tmp/trauma-spine-logs}
mkdir -p "$EV" "$RIGLOG"
TEN=00000000-0000-0000-0000-000000000001
FAC=00000000-0000-4000-8000-0000000000fa
ACTOR=PROV-ZW-TR-01
# Unique infra (must not collide with the theatre rig's cs-rig-* / 282xx).
PGPORT=15884; RPORT=16884
DAI=http://localhost:29392
PCT=http://localhost:29388
INP=http://localhost:29321
MADI=http://localhost:29300
PASS=0; FAIL=0
ok(){ echo "   PASS: $1" | tee -a "$EV/journal.txt"; PASS=$((PASS+1)); }
bad(){ echo "   FAIL: $1" | tee -a "$EV/journal.txt"; FAIL=$((FAIL+1)); }
say(){ echo "== $1" | tee -a "$EV/journal.txt"; }
DAISQL(){ docker exec tr-rig-pg psql -U impilo -d daidzai -tAc "$1"; }
PCTSQL(){ docker exec tr-rig-pg psql -U impilo -d pct -tAc "$1"; }
INPSQL(){ docker exec tr-rig-pg psql -U impilo -d inpatient -tAc "$1"; }
MADISQL(){ docker exec tr-rig-pg psql -U impilo -d madi -tAc "$1"; }
hdr(){ local pou=${1:-TREATMENT} ep=${2:-} idem=${3:-idem-$(uuidgen)}
  local base="-H Content-Type:application/json -H X-Tenant-ID:$TEN -H X-Pod-ID:national-spine -H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H X-Facility-ID:$FAC -H X-Actor-ID:$ACTOR -H X-Actor-Type:PROVIDER -H X-Purpose-Of-Use:$pou -H Idempotency-Key:$idem"
  [ -n "$ep" ] && base="$base -H X-Trauma-Episode-ID:$ep"
  echo "$base"; }
jget(){ python3 -c "import json,sys;d=json.load(open('$1'));print(d.get('$2','') if isinstance(d,dict) else '')" 2>/dev/null; }
jget2(){ python3 -c "import json;d=json.load(open('$1'));d=d.get('data',d) if isinstance(d,dict) else d;print(d.get('$2','') if isinstance(d,dict) else '')" 2>/dev/null; }

command -v docker >/dev/null || { echo "docker not available — rig cannot run"; exit 2; }

# ── Infra ────────────────────────────────────────────────────────────────────
say "RIG: infra (postgres + redis) + daidzai/pct/inpatient/madi substrate"
docker rm -f tr-rig-pg tr-rig-redis >/dev/null 2>&1
docker run -d --name tr-rig-pg -e POSTGRES_USER=impilo -e POSTGRES_PASSWORD=impilo -p $PGPORT:5432 postgres:16-alpine >/dev/null
docker run -d --name tr-rig-redis -p $RPORT:6379 redis:7-alpine >/dev/null
sleep 8
for db in daidzai pct inpatient madi; do
  docker exec tr-rig-pg psql -U impilo -d postgres -c "CREATE DATABASE $db" >/dev/null 2>&1
done

svcjar(){ ls "$REPO/services/$1/target/$1-"*.jar 2>/dev/null | grep -v original | head -1; }
for s in daidzai-service pct-service inpatient-service madi-service; do
  [ -n "$(svcjar "$s")" ] || { echo "$s jar missing — run: mvn -f services/pom.xml -pl daidzai-service,pct-service,inpatient-service,madi-service -am package -DskipTests"; exit 2; }
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

# DAIDZAI is the spine; the phase owners point their DaidzaiEpisodeClient at it.
# kafka-events-enabled=false → the no-Kafka outbox drainer marks daidzai.ems.* rows published.
boot daidzai-service daidzai 29392 DAIDZAI_KAFKA_EVENTS_ENABLED=false
boot pct-service pct 29388 PCT_INTEGRATION_DAIDZAI_BASE_URL=$DAI
boot madi-service madi 29300 MADI_INTEGRATION_DAIDZAI_BASE_URL=$DAI
boot inpatient-service inpatient 29321 INPATIENT_INTEGRATION_DAIDZAI_BASE_URL=$DAI

wait_health $DAI daidzai-service
wait_health $PCT pct-service
wait_health $MADI madi-service
wait_health $INP inpatient-service

cleanup(){ if [ -z "${KEEP_RIG:-}" ]; then
  for p in "$RIGLOG"/*.pid; do kill "$(cat "$p")" >/dev/null 2>&1; done
  docker rm -f tr-rig-pg tr-rig-redis >/dev/null 2>&1
fi }
trap cleanup EXIT

# ═════ J-TR-0: canonical episode spine ═══════════════════════════════════════
say "J-TR-0: incident triage mints the episode; all phase owners carry it"

# 1) SOS request -> triage -> incident (DAIDZAI mints the episode on triage).
curl -sS -o "$EV/req.json" $(hdr) -X POST $DAI/internal/v1/daidzai/requests \
  -d '{"requesterType":"BYSTANDER","subjectIdentityMode":"ANONYMOUS","subjectLabel":"adult male RTC","emergencyCategory":"TRAUMA","severity":"CRITICAL","description":"RTC on Harare-Bulawayo highway","lat":-17.83,"lng":31.05,"locationDescription":"highway","channel":"MOBILE"}'
REQ=$(jget "$EV/req.json" id)
[ -n "$REQ" ] && ok "SOS request created ($REQ)" || bad "SOS request not created: $(cat "$EV/req.json")"

curl -sS -o "$EV/incident.json" $(hdr) -X POST $DAI/internal/v1/daidzai/requests/$REQ/triage -d '{}'
INC=$(jget "$EV/incident.json" id)
EP=$(jget "$EV/incident.json" traumaEpisodeId)
[ -n "$INC" ] && ok "incident triaged ($INC)" || bad "triage failed: $(cat "$EV/incident.json")"
[ -n "$EP" ] && ok "incident triage MINTED a trauma episode ($EP)" || bad "no trauma_episode_id on the triaged incident"

# DB: the episode exists, is keyed by the incident id, and stamps the incident row.
[ "$(DAISQL "SELECT count(*) FROM daidzai.dai_trauma_episode WHERE id='$EP' AND origin_key='$INC' AND origin_kind='INCIDENT'")" = 1 ] \
  && ok "dai_trauma_episode row exists (origin_kind=INCIDENT, origin_key=incident)" || bad "no dai_trauma_episode for $EP/$INC"
[ "$(DAISQL "SELECT trauma_episode_id FROM daidzai.dai_emergency_incident WHERE id='$INC'")" = "$EP" ] \
  && ok "dai_emergency_incident stamped with the episode id" || bad "incident row not stamped with $EP"
[ "$(DAISQL "SELECT count(*) FROM daidzai.dai_trauma_episode_phase WHERE trauma_episode_id='$EP' AND phase='INCIDENT'")" -ge 1 ] \
  && ok "timeline seeded with the INCIDENT phase" || bad "no INCIDENT phase on the timeline"

# 2) PCT ED visit inherits the episode id from X-Trauma-Episode-ID.
curl -sS -o "$EV/ed.json" $(hdr TREATMENT $EP) -X POST $PCT/v1/ed/visits \
  -d "{\"patientCpid\":\"TEMP-ED-TR0\",\"facilityId\":\"$FAC\",\"chiefComplaint\":\"RTC polytrauma\",\"arrivalMode\":\"AMBULANCE\"}"
VISIT=$(jget2 "$EV/ed.json" visit_id)
[ -n "$VISIT" ] && ok "ED visit opened ($VISIT)" || bad "ED visit not opened: $(cat "$EV/ed.json")"
[ "$(PCTSQL "SELECT trauma_episode_id FROM pct.ed_visit WHERE visit_id='$VISIT'")" = "$EP" ] \
  && ok "pct.ed_visit carries the SAME trauma_episode_id" || bad "ed_visit episode != $EP"

# 3) inpatient resuscitation_record carries the episode id (EMERGENCY waives care-context).
curl -sS -o "$EV/activation.json" $(hdr EMERGENCY) -X POST $INP/internal/v1/emergency/activate \
  -d '{"patientId":"TEMP-ED-TR0","protocolType":"TRAUMA","location":"RESUS-1"}'
ACT=$(jget "$EV/activation.json" id)
[ -n "$ACT" ] && ok "emergency activation created ($ACT)" || bad "activation not created: $(cat "$EV/activation.json")"
curl -sS -o "$EV/resus.json" $(hdr EMERGENCY $EP) -X POST $INP/internal/v1/emergency/$ACT/resuscitation \
  -d '{"initialRhythm":"PEA","cprCycles":3,"defibrillations":1}'
RESUS=$(jget "$EV/resus.json" id)
[ -n "$RESUS" ] && ok "resuscitation recorded ($RESUS)" || bad "resus not recorded: $(cat "$EV/resus.json")"
[ "$(INPSQL "SELECT trauma_episode_id FROM inpatient.resuscitation_record WHERE resus_id='$RESUS'")" = "$EP" ] \
  && ok "inpatient.resuscitation_record carries the SAME trauma_episode_id" || bad "resus episode != $EP"

# 4) MADI blood order carries the episode id.
curl -sS -o "$EV/blood.json" $(hdr TREATMENT $EP) -X POST $MADI/internal/v1/madi/orders \
  -d '{"patient_cpid":"TEMP-ED-TR0","blood_group":"O-","component_type":"PRBC","units_requested":4}'
ORDER=$(jget "$EV/blood.json" orderId)
[ -n "$ORDER" ] && ok "blood order placed ($ORDER)" || bad "blood order not placed: $(cat "$EV/blood.json")"
[ "$(MADISQL "SELECT trauma_episode_id FROM madi.blood_orders WHERE order_id='$ORDER'")" = "$EP" ] \
  && ok "madi.blood_orders carries the SAME trauma_episode_id" || bad "blood order episode != $EP"

# 5) The DAIDZAI read-model timeline resolves across all four phases.
sleep 2  # allow best-effort phase registrations to land
curl -sS -o "$EV/episode.json" $(hdr) $DAI/internal/v1/daidzai/trauma-episodes/$EP >/dev/null
python3 - "$EV/episode.json" <<'PY' | tee -a "$EV/journal.txt"
import json,sys
r=json.load(open(sys.argv[1]))
phases=[p.get("phase") for p in r.get("timeline",[])]
have=set(phases)
for want in ("INCIDENT","ED","RESUS","BLOOD"):
    print(("   PASS: timeline has the %s phase" % want) if want in have else ("   FAIL: timeline missing the %s phase (have %s)" % (want, phases)))
PY
for want in INCIDENT ED RESUS BLOOD; do
  python3 -c "import json;r=json.load(open('$EV/episode.json'));exit(0 if '$want' in [p.get('phase') for p in r.get('timeline',[])] else 1)" \
    && PASS=$((PASS+1)) || FAIL=$((FAIL+1))
done
# The shared-id invariant: every phase-owner row resolves to exactly this episode.
DISTINCT=$( { PCTSQL "SELECT trauma_episode_id FROM pct.ed_visit WHERE visit_id='$VISIT'";
             INPSQL "SELECT trauma_episode_id FROM inpatient.resuscitation_record WHERE resus_id='$RESUS'";
             MADISQL "SELECT trauma_episode_id FROM madi.blood_orders WHERE order_id='$ORDER'"; } | sort -u | grep -c . )
[ "$DISTINCT" = 1 ] && ok "all phase-owner rows resolve to ONE trauma_episode_id (no fork)" || bad "phase-owner rows carry $DISTINCT distinct episode ids"

# 6) Mint idempotency: re-mint on the same origin key returns the same episode (no fork).
curl -sS -o "$EV/remint.json" -w '%{http_code}' $(hdr) -X POST $DAI/internal/v1/daidzai/trauma-episodes \
  -d "{\"originService\":\"daidzai\",\"originKind\":\"INCIDENT\",\"originKey\":\"$INC\",\"incidentId\":\"$INC\"}" > "$EV/remint.code"
EP2=$(jget "$EV/remint.json" traumaEpisodeId)
[ "$EP2" = "$EP" ] && ok "re-mint on the same origin key returned the SAME episode ($EP2)" || bad "re-mint forked the spine: $EP2 != $EP"
[ "$(DAISQL "SELECT count(*) FROM daidzai.dai_trauma_episode WHERE origin_key='$INC'")" = 1 ] \
  && ok "exactly one episode row for the origin key (idempotent)" || bad "duplicate episode rows for origin $INC"

# 7) Dual-entry mint: ED-first walk-in trauma mints a DISTINCT episode via PCT.
say "J-TR-0b: ED-first walk-in trauma mints a distinct episode via PCT"
curl -sS -o "$EV/ed2.json" $(hdr) -X POST $PCT/v1/ed/visits \
  -d "{\"patientCpid\":\"TEMP-ED-WALK\",\"facilityId\":\"$FAC\",\"chiefComplaint\":\"stab wound\",\"arrivalMode\":\"WALK_IN\"}"
VISIT2=$(jget2 "$EV/ed2.json" visit_id)
[ -n "$VISIT2" ] && ok "ED walk-in visit opened ($VISIT2)" || bad "walk-in visit not opened: $(cat "$EV/ed2.json")"
curl -sS -o "$EV/activate2.json" $(hdr) -X POST $PCT/v1/ed/visits/$VISIT2/trauma/activate -d '{"traumaLevel":1,"mechanism":"PENETRATING"}'
EPW=$(jget2 "$EV/activate2.json" trauma_episode_id)
[ -n "$EPW" ] && [ "$EPW" != "$EP" ] && ok "ED-first walk-in minted a distinct episode ($EPW)" || bad "walk-in did not mint a distinct episode: $EPW"
[ "$(DAISQL "SELECT count(*) FROM daidzai.dai_trauma_episode WHERE id='$EPW' AND origin_kind='ED_WALK_IN' AND origin_service='pct-service'")" = 1 ] \
  && ok "walk-in episode is origin_kind=ED_WALK_IN, origin_service=pct-service" || bad "walk-in episode not recorded as PCT ED_WALK_IN"
[ "$(PCTSQL "SELECT trauma_episode_id FROM pct.ed_visit WHERE visit_id='$VISIT2'")" = "$EPW" ] \
  && ok "walk-in ed_visit stamped with its own episode id" || bad "walk-in ed_visit not stamped with $EPW"

# ═════ J-TR-1: EMS clinical dispatch — state machine + outbox + idempotency ══════
say "J-TR-1: EMS mission walks CREATED→HANDOVER; daidzai.ems.* outbox; dispatch idempotent"
# Dispatch a clinical EMS mission for the incident INC minted in J-TR-0.
IDEM=idem-emsdispatch-$(uuidgen)
curl -sS -o "$EV/ems-dispatch.json" $(hdr TREATMENT "" "$IDEM") -X POST $DAI/internal/v1/daidzai/ems/incidents/$INC/dispatch \
  -d '{"callSign":"AMB-07","ambulanceAssetId":"ASSET-AMB-07","priority":"CRITICAL"}'
MISSION=$(jget "$EV/ems-dispatch.json" id)
MSTATE=$(jget "$EV/ems-dispatch.json" state)
[ -n "$MISSION" ] && ok "EMS mission dispatched ($MISSION)" || bad "dispatch failed: $(cat "$EV/ems-dispatch.json")"
[ "$MSTATE" = "DISPATCHED" ] && ok "mission is DISPATCHED after dispatch (CREATED→DISPATCHED)" || bad "mission state '$MSTATE' != DISPATCHED"
[ "$(DAISQL "SELECT count(*) FROM daidzai.dai_ems_mission WHERE id='$MISSION' AND incident_id='$INC'")" = 1 ] \
  && ok "dai_ems_mission row bound to the incident" || bad "no dai_ems_mission for $MISSION/$INC"
[ "$(DAISQL "SELECT trauma_episode_id FROM daidzai.dai_ems_mission WHERE id='$MISSION'")" = "$EP" ] \
  && ok "mission carries the canonical trauma_episode_id" || bad "mission episode != $EP"

# Walk the validated state machine to HANDOVER.
for st in ACKNOWLEDGED ACCEPTED EN_ROUTE_SCENE ON_SCENE PATIENT_CONTACT DEPARTED_SCENE EN_ROUTE_FACILITY ARRIVED_FACILITY HANDOVER; do
  body='{"toState":"'$st'"}'
  [ "$st" = HANDOVER ] && body='{"toState":"HANDOVER","pctEncounterRef":"'$VISIT'"}'
  curl -sS -o "$EV/ems-$st.json" $(hdr) -X POST $DAI/internal/v1/daidzai/ems/missions/$MISSION/advance -d "$body" >/dev/null
done
FINAL=$(DAISQL "SELECT state FROM daidzai.dai_ems_mission WHERE id='$MISSION'")
[ "$FINAL" = "HANDOVER" ] && ok "EMS mission walked the state machine to HANDOVER" || bad "mission state '$FINAL' != HANDOVER"
[ -n "$(DAISQL "SELECT handover_at FROM daidzai.dai_ems_mission WHERE id='$MISSION' AND handover_at IS NOT NULL")" ] \
  && ok "handover_at timestamp recorded" || bad "handover_at not set at HANDOVER"

# Illegal transition is rejected (HANDOVER cannot jump back to EN_ROUTE_SCENE).
HTTP=$(curl -sS -o /dev/null -w '%{http_code}' $(hdr) -X POST $DAI/internal/v1/daidzai/ems/missions/$MISSION/advance -d '{"toState":"EN_ROUTE_SCENE"}')
[ "$HTTP" != 200 ] && ok "illegal state transition rejected (HTTP $HTTP, not 200)" || bad "illegal transition was accepted (200)"

# Outbox: every transition landed as a daidzai.ems.* row, drained by the no-Kafka drainer.
EMSROWS=$(DAISQL "SELECT count(*) FROM daidzai.dai_outbox WHERE event_type LIKE 'daidzai.ems.%' AND aggregate_id='$MISSION'")
[ "${EMSROWS:-0}" -ge 10 ] && ok "daidzai.dai_outbox has daidzai.ems.* rows ($EMSROWS)" || bad "expected >=10 daidzai.ems.* outbox rows, got $EMSROWS"
[ "$(DAISQL "SELECT count(*) FROM daidzai.dai_outbox WHERE event_type='daidzai.ems.handover' AND aggregate_id='$MISSION'")" -ge 1 ] \
  && ok "daidzai.ems.handover event emitted" || bad "no daidzai.ems.handover outbox row"
# The no-Kafka drainer polls every 2s — wait for it to catch the last batch before asserting.
for i in $(seq 1 12); do
  U=$(DAISQL "SELECT count(*) FROM daidzai.dai_outbox WHERE event_type LIKE 'daidzai.ems.%' AND published_at IS NULL")
  [ "${U:-1}" = 0 ] && break; sleep 1
done
[ "${U:-1}" = 0 ] && ok "no-Kafka drainer marked all daidzai.ems.* rows published" || bad "undrained daidzai.ems.* outbox rows remain ($U)"

# Idempotency: re-dispatch the same incident with the SAME Idempotency-Key AND identical request ⇒
# the companion IdempotencyFilter replays the original mission (no duplicate).
curl -sS -o "$EV/ems-redispatch.json" $(hdr TREATMENT "" "$IDEM") -X POST $DAI/internal/v1/daidzai/ems/incidents/$INC/dispatch \
  -d '{"callSign":"AMB-07","ambulanceAssetId":"ASSET-AMB-07","priority":"CRITICAL"}'
MISSION2=$(jget "$EV/ems-redispatch.json" id)
[ "$MISSION2" = "$MISSION" ] && ok "same Idempotency-Key replays the SAME mission ($MISSION2)" || bad "re-dispatch forked a mission: $MISSION2 != $MISSION"
# Service-level idempotency: a FRESH key on the same incident still returns the one mission (unique per incident).
curl -sS -o "$EV/ems-redispatch2.json" $(hdr) -X POST $DAI/internal/v1/daidzai/ems/incidents/$INC/dispatch \
  -d '{"callSign":"AMB-08","ambulanceAssetId":"ASSET-AMB-08","priority":"CRITICAL"}'
MISSION3=$(jget "$EV/ems-redispatch2.json" id)
[ "$MISSION3" = "$MISSION" ] && ok "fresh-key re-dispatch returns the existing mission (idempotent per incident)" || bad "fresh-key re-dispatch forked: $MISSION3 != $MISSION"
[ "$(DAISQL "SELECT count(*) FROM daidzai.dai_ems_mission WHERE incident_id='$INC'")" = 1 ] \
  && ok "exactly one EMS mission for the incident (no duplicate crew dispatched)" || bad "duplicate EMS missions for incident $INC"

# ── Summary ────────────────────────────────────────────────────────────────────
say "SUMMARY: PASS=$PASS FAIL=$FAIL"
echo "{\"pass\":$PASS,\"fail\":$FAIL}" > "$EV/summary.json"
[ "$FAIL" = 0 ] || exit 1
