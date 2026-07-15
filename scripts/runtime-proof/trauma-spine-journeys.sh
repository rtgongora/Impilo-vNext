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
VASH=http://localhost:29387
NOTIF=http://localhost:29200
OROS=http://localhost:29089
VITO=http://localhost:29082
PASS=0; FAIL=0
ok(){ echo "   PASS: $1" | tee -a "$EV/journal.txt"; PASS=$((PASS+1)); }
bad(){ echo "   FAIL: $1" | tee -a "$EV/journal.txt"; FAIL=$((FAIL+1)); }
say(){ echo "== $1" | tee -a "$EV/journal.txt"; }
DAISQL(){ docker exec tr-rig-pg psql -U impilo -d daidzai -tAc "$1"; }
PCTSQL(){ docker exec tr-rig-pg psql -U impilo -d pct -tAc "$1"; }
INPSQL(){ docker exec tr-rig-pg psql -U impilo -d inpatient -tAc "$1"; }
MADISQL(){ docker exec tr-rig-pg psql -U impilo -d madi -tAc "$1"; }
VASHSQL(){ docker exec tr-rig-pg psql -U impilo -d vashandi -tAc "$1"; }
NOTIFSQL(){ docker exec tr-rig-pg psql -U impilo -d notification -tAc "$1"; }
OROSSQL(){ docker exec tr-rig-pg psql -U impilo -d oros -tAc "$1"; }
VITOSQL(){ docker exec tr-rig-pg psql -U impilo -d vito -tAc "$1"; }
hdr(){ local pou=${1:-TREATMENT} ep=${2:-} idem=${3:-idem-$(uuidgen)}
  local base="-H Content-Type:application/json -H X-Tenant-ID:$TEN -H X-Pod-ID:national-spine -H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H X-Facility-ID:$FAC -H X-Actor-ID:$ACTOR -H X-Actor-Type:PROVIDER -H X-Purpose-Of-Use:$pou -H Idempotency-Key:$idem"
  [ -n "$ep" ] && base="$base -H X-Trauma-Episode-ID:$ep"
  echo "$base"; }
# Deliberately malformed trust envelope for the negative-authz journey (missing mandatory X-Tenant-ID).
hdr_bad(){ echo "-H Content-Type:application/json -H X-Pod-ID:national-spine -H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H X-Actor-ID:$ACTOR -H X-Actor-Type:PROVIDER"; }
jget(){ python3 -c "import json,sys;d=json.load(open('$1'));print(d.get('$2','') if isinstance(d,dict) else '')" 2>/dev/null; }
jget2(){ python3 -c "import json;d=json.load(open('$1'));d=d.get('data',d) if isinstance(d,dict) else d;print(d.get('$2','') if isinstance(d,dict) else '')" 2>/dev/null; }

command -v docker >/dev/null || { echo "docker not available — rig cannot run"; exit 2; }

# ── Infra ────────────────────────────────────────────────────────────────────
say "RIG: infra (postgres + redis) + daidzai/pct/inpatient/madi substrate"
docker rm -f tr-rig-pg tr-rig-redis >/dev/null 2>&1
docker run -d --name tr-rig-pg -e POSTGRES_USER=impilo -e POSTGRES_PASSWORD=impilo -p $PGPORT:5432 postgres:16-alpine >/dev/null
docker run -d --name tr-rig-redis -p $RPORT:6379 redis:7-alpine >/dev/null
sleep 8
for db in daidzai pct inpatient madi vashandi notification oros vito; do
  docker exec tr-rig-pg psql -U impilo -d postgres -c "CREATE DATABASE $db" >/dev/null 2>&1
done

svcjar(){ ls "$REPO/services/$1/target/$1-"*.jar 2>/dev/null | grep -v original | head -1; }
for s in daidzai-service pct-service inpatient-service madi-service vashandi-workforce-service notification-service oros-service vito-service; do
  [ -n "$(svcjar "$s")" ] || { echo "$s jar missing — run: mvn -f services/pom.xml -pl daidzai-service,pct-service,inpatient-service,madi-service,vashandi-workforce-service,notification-service,oros-service,vito-service -am package -DskipTests"; exit 2; }
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
# DAIDZAI_PCT_BASE_URL → the EMS ePCR pre-arrival push lands on PCT's ED projection.
boot daidzai-service daidzai 29392 DAIDZAI_KAFKA_EVENTS_ENABLED=false DAIDZAI_PCT_BASE_URL=$PCT
boot vashandi-workforce-service vashandi 29387
boot notification-service notification 29200 KAFKA_OROS_CONSUMER_ENABLED=false
boot oros-service oros 29089
boot vito-service vito 29082 VITO_HMAC_PEPPER=trauma-rig-shs-signing-pepper-at-least-32-bytes-long IMPILO_FEDERATION_ENABLED=false
# PCT resolves the on-call trauma team from VASHANDI + raises delivery-intents via notification-service.
boot pct-service pct 29388 PCT_INTEGRATION_DAIDZAI_BASE_URL=$DAI IMPILO_FEDERATION_ENABLED=false \
  PCT_INTEGRATION_VASHANDI_BASE_URL=$VASH PCT_INTEGRATION_NOTIFICATION_BASE_URL=$NOTIF PCT_INTEGRATION_OROS_BASE_URL=$OROS PCT_INTEGRATION_MADI_BASE_URL=$MADI
boot madi-service madi 29300 MADI_INTEGRATION_DAIDZAI_BASE_URL=$DAI
boot inpatient-service inpatient 29321 INPATIENT_INTEGRATION_DAIDZAI_BASE_URL=$DAI

wait_health $DAI daidzai-service
wait_health $VASH vashandi-workforce-service
wait_health $NOTIF notification-service
wait_health $OROS oros-service
wait_health $VITO vito-service
wait_health $PCT pct-service
wait_health $MADI madi-service
wait_health $INP inpatient-service

# ── Seed the VASHANDI trauma on-call pool (rig test data; resolution stays REAL) ─────
# A roster + 4 profiles + 4 shifts tagged virtual_pool_id='trauma-oncall:<FAC>', on-duty NOW.
# J-TR-3 resolves this via the live /virtual-pools/{poolId}/on-duty endpoint — not hardcoded.
POOL="trauma-oncall:$FAC"
seed_vashandi(){
  local roster="a0000000-0000-4000-8000-00000000r001"
  roster="a0000000-0000-4000-8000-0000000000c1"
  VASHSQL "INSERT INTO vashandi.vsh_roster (id, tenant_id, organisation_id, roster_type, period_start, period_end, status)
           VALUES ('$roster','$TEN','$FAC','TRAUMA_ONCALL', current_date - 1, current_date + 7, 'published')
           ON CONFLICT DO NOTHING" >/dev/null
  local i=1
  for role in EM_PHYSICIAN TRAUMA_SURGEON ANAESTHETIST EM_NURSE; do
    local prof; prof=$(printf 'b0000000-0000-4000-8000-0000000000%02d' "$i")
    VASHSQL "INSERT INTO vashandi.vsh_workforce_profile (id, tenant_id, provider_worker_id, worker_type, profession, cadre, current_status)
             VALUES ('$prof','$TEN','PRV-TR-$i','clinical','$role','$role','active') ON CONFLICT DO NOTHING" >/dev/null
    VASHSQL "INSERT INTO vashandi.vsh_shift (id, tenant_id, roster_id, workforce_profile_id, shift_type, start_time, end_time, status, check_in_required, virtual_pool_id, facility_id)
             VALUES (gen_random_uuid(),'$TEN','$roster','$prof','$role', now() - interval '1 hour', now() + interval '8 hours', 'confirmed', false, '$POOL', '$FAC')
             ON CONFLICT DO NOTHING" >/dev/null
    i=$((i+1))
  done
  echo "   seeded vashandi trauma on-call pool ($POOL): $(VASHSQL "SELECT count(*) FROM vashandi.vsh_shift WHERE virtual_pool_id='$POOL'") shifts" | tee -a "$EV/journal.txt"
}
seed_vashandi

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

# ═════ J-TR-2: prehospital ePCR reaches the ED BEFORE arrival ════════════════════
say "J-TR-2: ePCR obs persist + land on the ED pre-arrival projection before arrival"
# Fresh incident via escalation so the mission carries a destination facility (the ED to pre-warn).
curl -sS -o "$EV/incident2.json" $(hdr) -X POST $DAI/internal/v1/daidzai/incidents \
  -d "{\"emergencyCategory\":\"TRAUMA\",\"severity\":\"CRITICAL\",\"description\":\"fall from height\",\"facilityId\":\"$FAC\",\"subjectIdentityMode\":\"ANONYMOUS\"}"
INC2=$(jget "$EV/incident2.json" id)
EP2=$(jget "$EV/incident2.json" traumaEpisodeId)
[ -n "$EP2" ] && ok "escalated incident minted an episode ($EP2)" || bad "no episode on escalated incident: $(cat "$EV/incident2.json")"

curl -sS -o "$EV/ems-dispatch2.json" $(hdr) -X POST $DAI/internal/v1/daidzai/ems/incidents/$INC2/dispatch \
  -d '{"callSign":"AMB-12","ambulanceAssetId":"ASSET-AMB-12","priority":"CRITICAL"}'
MISSION_B=$(jget "$EV/ems-dispatch2.json" id)
[ -n "$MISSION_B" ] && ok "EMS mission dispatched for the incoming patient ($MISSION_B)" || bad "dispatch failed: $(cat "$EV/ems-dispatch2.json")"

# Advance only to EN_ROUTE_FACILITY — the ambulance is inbound but has NOT arrived yet.
for st in ACKNOWLEDGED ACCEPTED EN_ROUTE_SCENE ON_SCENE PATIENT_CONTACT DEPARTED_SCENE EN_ROUTE_FACILITY; do
  curl -sS -o /dev/null $(hdr) -X POST $DAI/internal/v1/daidzai/ems/missions/$MISSION_B/advance -d '{"toState":"'$st'"}'
done
INROUTE=$(DAISQL "SELECT state FROM daidzai.dai_ems_mission WHERE id='$MISSION_B'")
[ "$INROUTE" = "EN_ROUTE_FACILITY" ] && ok "mission is EN_ROUTE_FACILITY (inbound, not yet arrived)" || bad "mission state '$INROUTE' != EN_ROUTE_FACILITY"

# Responder captures the ePCR + timed obs EN ROUTE.
curl -sS -o "$EV/epcr.json" $(hdr) -X POST $DAI/internal/v1/daidzai/ems/missions/$MISSION_B/epcr \
  -d '{"patientHealthId":"TEMP-EMS-B","primarySurvey":{"airway":"patent","breathing":"laboured","circulation":"weak radial"},"narrative":"Fall ~6m, chest + pelvic pain"}'
curl -sS -o /dev/null $(hdr) -X POST $DAI/internal/v1/daidzai/ems/missions/$MISSION_B/epcr/events \
  -d '{"eventType":"VITALS","channel":"OBS","payload":{"hr":128,"sbp":86,"spo2":89,"rr":28}}'
curl -sS -o /dev/null $(hdr) -X POST $DAI/internal/v1/daidzai/ems/missions/$MISSION_B/epcr/events \
  -d '{"eventType":"GCS","channel":"OBS","payload":{"gcs":13,"e":3,"v":4,"m":6}}'
curl -sS -o "$EV/epcr-vitals2.json" $(hdr) -X POST $DAI/internal/v1/daidzai/ems/missions/$MISSION_B/epcr/events \
  -d '{"eventType":"VITALS","channel":"OBS","payload":{"hr":120,"sbp":92,"spo2":93,"rr":24}}'

# ePCR time-series persists in daidzai.
EPCRROWS=$(DAISQL "SELECT count(*) FROM daidzai.dai_ems_epcr_event WHERE mission_id='$MISSION_B'")
[ "${EPCRROWS:-0}" -ge 3 ] && ok "dai_ems_epcr_event time-series persisted ($EPCRROWS obs)" || bad "expected >=3 ePCR events, got $EPCRROWS"
[ "$(DAISQL "SELECT count(*) FROM daidzai.dai_ems_epcr WHERE mission_id='$MISSION_B' AND trauma_episode_id='$EP2'")" = 1 ] \
  && ok "dai_ems_epcr bound to the mission + trauma episode" || bad "ePCR not bound to mission/episode"

# The obs reached the ED pre-arrival projection BEFORE the ambulance arrived.
PREARR=$(PCTSQL "SELECT count(*) FROM pct.ed_visit WHERE trauma_episode_id='$EP2' AND status='PRE_ARRIVAL' AND pre_arrival_json IS NOT NULL")
[ "$PREARR" = 1 ] && ok "ePCR obs landed on the PCT ed_visit PRE_ARRIVAL projection (before arrival)" || bad "no PRE_ARRIVAL ed_visit projection for episode $EP2 (got $PREARR)"
[ "$(PCTSQL "SELECT ems_mission_ref FROM pct.ed_visit WHERE trauma_episode_id='$EP2' AND status='PRE_ARRIVAL'")" = "$MISSION_B" ] \
  && ok "pre-arrival projection references the EMS mission" || bad "pre-arrival ed_visit missing/incorrect ems_mission_ref"
# The projection snapshot actually carries the latest vitals (sbp 92 from the last obs).
PCTSQL "SELECT pre_arrival_json FROM pct.ed_visit WHERE trauma_episode_id='$EP2' AND status='PRE_ARRIVAL'" > "$EV/prearrival-json.txt"
grep -q '"sbp"' "$EV/prearrival-json.txt" && ok "pre-arrival snapshot carries prehospital vitals" || bad "pre-arrival snapshot has no vitals: $(cat "$EV/prearrival-json.txt")"

# And it is visible on the ED pre-arrival board API before arrival.
curl -sS -o "$EV/prearrival-board.json" $(hdr) "$PCT/v1/ed/pre-arrival?facilityId=$FAC" >/dev/null
python3 -c "import json;r=json.load(open('$EV/prearrival-board.json'));d=r.get('data',r);exit(0 if isinstance(d,list) and len(d)>=1 else 1)" \
  && ok "ED pre-arrival board API lists the inbound patient" || bad "pre-arrival board API empty: $(cat "$EV/prearrival-board.json")"

# ═════ J-TR-3a: ED arrival/handover reuses the PRE_ARRIVAL visit (no duplicate) ══════
say "J-TR-3a: EMS handover transitions the SAME pre-arrival ed_visit to active"
PREV_VISIT=$(PCTSQL "SELECT visit_id FROM pct.ed_visit WHERE trauma_episode_id='$EP2' AND status='PRE_ARRIVAL'")
[ -n "$PREV_VISIT" ] && ok "pre-arrival ed_visit exists pre-handover ($PREV_VISIT, PRE_ARRIVAL)" || bad "no PRE_ARRIVAL visit for $EP2 before handover"
# Advance the J-TR-2 mission the rest of the way: EN_ROUTE_FACILITY → ARRIVED_FACILITY → HANDOVER.
for st in ARRIVED_FACILITY HANDOVER; do
  body='{"toState":"'$st'"}'
  [ "$st" = HANDOVER ] && body='{"toState":"HANDOVER","pctEncounterRef":"'$PREV_VISIT'"}'
  curl -sS -o /dev/null $(hdr) -X POST $DAI/internal/v1/daidzai/ems/missions/$MISSION_B/advance -d "$body"
done
sleep 2  # allow the daidzai→pct arrival push to land
NOWV=$(PCTSQL "SELECT visit_id FROM pct.ed_visit WHERE trauma_episode_id='$EP2'")
NOWSTATUS=$(PCTSQL "SELECT status FROM pct.ed_visit WHERE trauma_episode_id='$EP2'")
NCOUNT=$(PCTSQL "SELECT count(*) FROM pct.ed_visit WHERE trauma_episode_id='$EP2'")
[ "$NOWV" = "$PREV_VISIT" ] && ok "SAME ed_visit row after handover (no duplicate: $NOWV)" || bad "handover created a new visit: $NOWV != $PREV_VISIT"
[ "$NOWSTATUS" != "PRE_ARRIVAL" ] && ok "visit transitioned out of PRE_ARRIVAL (now $NOWSTATUS)" || bad "visit still PRE_ARRIVAL after handover"
[ "$NCOUNT" = 1 ] && ok "exactly one ed_visit for the episode (arrival reused pre-arrival)" || bad "$NCOUNT ed_visit rows for episode $EP2 (duplicate)"

# ═════ J-TR-3b: trauma-team roster → notify-per-member → ack → escalate ═══════════
say "J-TR-3b: trauma-team activation resolves the VASHANDI on-call panel, notifies, acks, escalates"
SEEDED=$(VASHSQL "SELECT count(*) FROM vashandi.vsh_shift WHERE virtual_pool_id='$POOL' AND status IN ('confirmed','checked_in')")
# Fresh ED walk-in at the facility to activate a trauma team on.
curl -sS -o "$EV/team-visit.json" $(hdr) -X POST $PCT/v1/ed/visits \
  -d "{\"patientCpid\":\"TEMP-ED-TEAM\",\"facilityId\":\"$FAC\",\"chiefComplaint\":\"polytrauma\",\"arrivalMode\":\"WALK_IN\"}"
TVISIT=$(jget2 "$EV/team-visit.json" visit_id)
curl -sS -o "$EV/team-activate.json" $(hdr) -X POST $PCT/v1/ed/visits/$TVISIT/trauma/activate -d '{"traumaLevel":1,"mechanism":"BLUNT"}'
# Parse trauma id + member ids from the activation response.
python3 - "$EV/team-activate.json" > "$EV/team.env" <<'PY'
import json,sys
d=json.load(open(sys.argv[1])); d=d.get("data",d)
tid=d.get("active_trauma_id","")
members=d.get("trauma_team",[]) or []
print("TRAUMA_ID="+str(tid))
print("MEMBER_COUNT="+str(len(members)))
print("FIRST_MEMBER="+(members[0]["id"] if members else ""))
PY
. "$EV/team.env"
[ -n "$TRAUMA_ID" ] && ok "trauma activated ($TRAUMA_ID)" || bad "trauma activation failed: $(cat "$EV/team-activate.json")"
# Real resolution: one member row per on-duty pool member (matches the seeded panel).
MROWS=$(PCTSQL "SELECT count(*) FROM pct.ed_trauma_team_member WHERE trauma_id='$TRAUMA_ID'")
[ "$MROWS" = "$SEEDED" ] && [ "$MROWS" -ge 1 ] && ok "resolved $MROWS trauma-team members from the live VASHANDI on-call pool (== $SEEDED seeded)" || bad "resolved $MROWS members != $SEEDED seeded pool"
# A REAL notification delivery-intent PER member (not a fabricated 'sent').
NROWS=$(NOTIFSQL "SELECT count(*) FROM ns_notifications WHERE template_key='ED_TRAUMA_TEAM'")
[ "$NROWS" -ge "$SEEDED" ] && ok "notification delivery-intent row per member in ns_notifications ($NROWS, template ED_TRAUMA_TEAM)" || bad "expected >=$SEEDED ED_TRAUMA_TEAM notifications, got $NROWS"
# The honest check: a DISTINCT recipient per rostered member (genuine per-member intent, not one
# blanket 'sent'). Each to_addr is a resolved workforce_profile_id.
DISTINCT_TO=$(NOTIFSQL "SELECT count(DISTINCT to_addr) FROM ns_notifications WHERE template_key='ED_TRAUMA_TEAM'")
[ "$DISTINCT_TO" = "$SEEDED" ] && ok "delivery-intent targets $DISTINCT_TO distinct rostered recipients (per-member, not a blanket send)" || bad "expected $SEEDED distinct recipients, got $DISTINCT_TO"
MEMBER_TO=$(PCTSQL "SELECT workforce_profile_id FROM pct.ed_trauma_team_member WHERE trauma_id='$TRAUMA_ID' ORDER BY workforce_profile_id LIMIT 1")
[ "$(NOTIFSQL "SELECT count(*) FROM ns_notifications WHERE template_key='ED_TRAUMA_TEAM' AND to_addr='$MEMBER_TO'")" -ge 1 ] \
  && ok "a resolved member's profile id is a notification recipient (real targeting)" || bad "member $MEMBER_TO not found as a notification recipient"
[ "$(PCTSQL "SELECT notification_ref FROM pct.ed_trauma_team_member WHERE trauma_id='$TRAUMA_ID' AND notification_ref IS NOT NULL LIMIT 1")" ] \
  && ok "member rows carry the notification delivery ref" || bad "no notification_ref recorded on members"

# Ack ONE member.
curl -sS -o /dev/null $(hdr) -X POST $PCT/v1/ed/trauma/$TRAUMA_ID/team/$FIRST_MEMBER/ack -d '{"ackBy":"'$ACTOR'"}'
[ "$(PCTSQL "SELECT ack_status FROM pct.ed_trauma_team_member WHERE id='$FIRST_MEMBER'")" = "ACKED" ] \
  && ok "member ack recorded (ACKED)" || bad "ack not recorded on member $FIRST_MEMBER"

# Escalate on no-ack (timeout 0 = all still-PENDING escalate immediately).
curl -sS -o "$EV/team-escalate.json" $(hdr) -X POST $PCT/v1/ed/trauma/$TRAUMA_ID/team/escalate -d '{"timeoutSeconds":0}'
ESCALATED=$(PCTSQL "SELECT count(*) FROM pct.ed_trauma_team_member WHERE trauma_id='$TRAUMA_ID' AND ack_status='ESCALATED'")
EXPECT_ESC=$((SEEDED-1))
[ "$ESCALATED" = "$EXPECT_ESC" ] && ok "no-ack members escalated ($ESCALATED == $EXPECT_ESC un-acked)" || bad "escalated $ESCALATED != expected $EXPECT_ESC"
[ "$(PCTSQL "SELECT ack_status FROM pct.ed_trauma_team_member WHERE id='$FIRST_MEMBER'")" = "ACKED" ] \
  && ok "the acked member was NOT escalated" || bad "acked member got escalated"
ESCN=$(NOTIFSQL "SELECT count(*) FROM ns_notifications WHERE template_key='ED_TRAUMA_ESCALATION'")
[ "$ESCN" -ge "$EXPECT_ESC" ] && ok "escalation delivery-intent row per no-ack member ($ESCN, template ED_TRAUMA_ESCALATION)" || bad "expected >=$EXPECT_ESC escalation notifications, got $ESCN"

# ═════ J-TR-4a: ABCDE resuscitation time-series reads back on the episode ═════════
say "J-TR-4a: ordered multi-channel resuscitation_event series on the episode"
# Reuse the J-TR-0 activation ($ACT) + episode ($EP): stream ABCDE observations en resus.
i=0
for ch in AIRWAY:INTUBATED BREATHING:SPO2:88:% CIRCULATION:SBP:86:mmHg DISABILITY:GCS:9 EXPOSURE:TEMP:35.1:C DRUG:ADRENALINE:1:mg RHYTHM:PEA CPR:CYCLES:4; do
  chan=$(echo "$ch" | cut -d: -f1); code=$(echo "$ch" | cut -d: -f2); val=$(echo "$ch" | cut -d: -f3); unit=$(echo "$ch" | cut -d: -f4)
  body="{\"channel\":\"$chan\",\"code\":\"$code\""
  [ -n "$val" ] && body="$body,\"valueNumeric\":\"$val\""
  [ -n "$unit" ] && body="$body,\"unit\":\"$unit\""
  body="$body}"
  curl -sS -o /dev/null $(hdr EMERGENCY $EP) -X POST $INP/internal/v1/emergency/$ACT/resuscitation/events -d "$body"
  i=$((i+1))
done
EVN=$(INPSQL "SELECT count(*) FROM inpatient.resuscitation_event WHERE activation_id='$ACT'")
[ "${EVN:-0}" -ge 8 ] && ok "resuscitation_event time-series persisted ($EVN observations)" || bad "expected >=8 resus events, got $EVN"
[ "$(INPSQL "SELECT count(DISTINCT channel) FROM inpatient.resuscitation_event WHERE activation_id='$ACT'")" -ge 5 ] \
  && ok "multi-channel ABCDE series (>=5 distinct channels)" || bad "resus series not multi-channel"
[ "$(INPSQL "SELECT count(*) FROM inpatient.resuscitation_event WHERE activation_id='$ACT' AND trauma_episode_id='$EP'")" = "$EVN" ] \
  && ok "every resus_event re-keyed onto the canonical trauma_episode_id" || bad "resus events not all on episode $EP"
curl -sS -o "$EV/resus-events.json" $(hdr) $INP/internal/v1/emergency/$ACT/resuscitation/events >/dev/null
python3 -c "import json;e=json.load(open('$EV/resus-events.json')).get('events',[]);ts=[str(x.get('recorded_at','')) for x in e];exit(0 if ts==sorted(ts) and len(e)>=8 else 1)" \
  && ok "series reads back ordered by recorded_at" || bad "resus series not ordered/short"

# ═════ J-TR-4b: OROS critical result → episode projection + ack + SAFETY outbox ═══
# HONEST LIMITATION: the OROS critical event payload omits encounter_ref, so the episode correlation
# is a PCT-side order→episode LINK (ed_diagnostic_order), not a self-correlating OROS event. Enriching
# the OROS payload with encounterRef is a post-gate follow-up (an OROS write we deliberately avoid).
say "J-TR-4b: OROS critical diagnostic result reconciles onto the trauma episode (PCT link correlation)"
# PCT places the trauma diagnostics order on OROS (encounter_ref=episode) and keeps the link.
curl -sS -o "$EV/order.json" $(hdr) -X POST $PCT/v1/ed/visits/$VISIT/diagnostics \
  -d '{"orderType":"LAB","testCode":"UECR","priority":"STAT","clinicalNotes":"trauma panel"}'
ORDERID=$(jget2 "$EV/order.json" oros_order_id)
[ -n "$ORDERID" ] && ok "PCT placed the OROS order (encounter_ref=episode) ($ORDERID)" || bad "PCT order-place failed: $(cat "$EV/order.json")"
[ "$(OROSSQL "SELECT encounter_ref FROM oros_orders WHERE order_id='$ORDERID'")" = "$EP" ] \
  && ok "oros_orders.encounter_ref carries the trauma episode id" || bad "order encounter_ref != $EP"
[ "$(PCTSQL "SELECT trauma_episode_id FROM pct.ed_diagnostic_order WHERE oros_order_id='$ORDERID'")" = "$EP" ] \
  && ok "PCT ed_diagnostic_order LINK row maps oros_order_id → episode (authoritative correlation)" || bad "no PCT order link for $ORDERID"

# Post a CRITICAL result on OROS (its own flow flags it + drops a critical event into its outbox).
curl -sS -o "$EV/oros-result.json" $(hdr) -X POST $OROS/v1/orders/$ORDERID/results \
  -d '{"kind":"LAB","summary":{"analyte":"K","value":7.2},"isCritical":true}'
RESULTID=$(jget2 "$EV/oros-result.json" resultId)
[ -n "$RESULTID" ] && ok "OROS critical result posted ($RESULTID)" || bad "OROS result not posted: $(cat "$EV/oros-result.json")"
CRITROWS=$(OROSSQL "SELECT count(*) FROM oros_event_outbox WHERE event_type IN ('CRITICAL_RESULT_POSTED','RESULT_CRITICAL','RESULT_MARKED_CRITICAL')")
[ "${CRITROWS:-0}" -ge 1 ] && ok "OROS critical event in oros_event_outbox (SAFETY source, Kafka off): $CRITROWS" || bad "no OROS critical outbox event"
[ "$(OROSSQL "SELECT is_critical FROM oros_results WHERE result_id='$RESULTID'")" = "t" ] \
  && ok "oros_results.is_critical = true" || bad "result not flagged critical"

# PCT reconciles the critical result onto the episode via the PCT order→episode link.
curl -sS -o "$EV/reconcile.json" $(hdr) -X POST $PCT/v1/ed/diagnostics/reconcile-critical \
  -d "{\"orderId\":\"$ORDERID\",\"resultId\":\"$RESULTID\",\"criticalReason\":\"Serum K+ 7.2 mmol/L\"}"
RECEP=$(jget2 "$EV/reconcile.json" trauma_episode_id)
[ "$RECEP" = "$EP" ] && ok "PCT resolved the episode from the order→episode link (event lacks encounter_ref)" || bad "reconcile episode '$RECEP' != $EP"
[ "$(DAISQL "SELECT count(*) FROM daidzai.dai_trauma_episode_phase WHERE trauma_episode_id='$EP' AND phase='DIAGNOSTICS'")" -ge 1 ] \
  && ok "episode projection: DIAGNOSTICS phase on the timeline" || bad "no DIAGNOSTICS phase on episode $EP"
[ "$(PCTSQL "SELECT count(*) FROM pct.pct_event_outbox WHERE event_type='pct.ed.critical_result'")" -ge 1 ] \
  && ok "SAFETY outbox row emitted (pct.ed.critical_result)" || bad "no pct.ed.critical_result SAFETY outbox row"
[ "$(OROSSQL "SELECT count(*) FROM oros_acknowledgements WHERE order_id='$ORDERID' AND ack_type='CRITICAL'")" -ge 1 ] \
  && ok "critical result acknowledged (ack row in oros_acknowledgements, type=CRITICAL)" || bad "no CRITICAL ack row for order $ORDERID"
[ "$(PCTSQL "SELECT status FROM pct.ed_diagnostic_order WHERE oros_order_id='$ORDERID'")" = "ACKED" ] \
  && ok "PCT order link reconciled to ACKED (read-through projection of OROS truth)" || bad "PCT order link not reconciled"

# ═════ J-TR-5: blood readiness gate honestly BLOCKED until RESERVED+COMPATIBLE ═══
say "J-TR-5: blood gate BLOCKED until MADI RESERVED+COMPATIBLE, then READY"
curl -sS -o "$EV/blood5.json" $(hdr TREATMENT $EP) -X POST $MADI/internal/v1/madi/orders \
  -d '{"patient_cpid":"TEMP-ED-TR0","blood_group":"O-","component_type":"PRBC","units_requested":2}'
BORDER=$(jget "$EV/blood5.json" orderId)
[ -n "$BORDER" ] && ok "trauma blood order placed ($BORDER)" || bad "blood order not placed: $(cat "$EV/blood5.json")"
[ "$(MADISQL "SELECT trauma_episode_id FROM madi.blood_orders WHERE order_id='$BORDER'")" = "$EP" ] \
  && ok "episode references the MADI blood order (trauma_episode_id link)" || bad "blood order not linked to episode"
# Gate is honestly BLOCKED while the order is not reserved.
curl -sS -o "$EV/readiness-blocked.json" $(hdr) "$PCT/v1/ed/blood-readiness?orderId=$BORDER" >/dev/null
[ "$(jget2 "$EV/readiness-blocked.json" status)" = "BLOCKED" ] \
  && ok "blood readiness BLOCKED before reservation (no false-ready)" || bad "readiness not BLOCKED pre-reservation: $(cat "$EV/readiness-blocked.json")"
# Blood bank owns its FSM — the rig moves MADI truth to a RESERVED (COMPATIBLE) order (theatre pattern).
MADISQL "UPDATE madi.blood_orders SET status='RESERVED' WHERE order_id='$BORDER'" >/dev/null
curl -sS -o "$EV/readiness-ready.json" $(hdr) "$PCT/v1/ed/blood-readiness?orderId=$BORDER" >/dev/null
python3 -c "import json;r=json.load(open('$EV/readiness-ready.json')).get('data',{});exit(0 if r.get('status')=='READY' and r.get('crossmatch_status')=='COMPATIBLE' else 1)" \
  && ok "blood readiness READY once MADI RESERVED+COMPATIBLE (read-through)" || bad "readiness not READY after RESERVED: $(cat "$EV/readiness-ready.json")"

# ═════ J-TR-6: unknown patient → VITO merge repoints ALL trauma links (zero orphans) ══
say "J-TR-6: VITO reconcile — every trauma link repoints, zero orphans, per correct column"
# Two VITO provisional identities: the unknown trauma patient (merged) + the confirmed survivor.
curl -sS -o "$EV/prov-merged.json" $(hdr) -X POST $VITO/internal/v1/identities/provisional -d '{"estimated_sex":"M","descriptor":"unknown RTC"}'
curl -sS -o "$EV/prov-surv.json" $(hdr) -X POST $VITO/internal/v1/identities/provisional -d '{"estimated_sex":"M","descriptor":"identified"}'
MHID=$(jget "$EV/prov-merged.json" health_id); MCRID=$(jget "$EV/prov-merged.json" crid)
SHID=$(jget "$EV/prov-surv.json" health_id); SCRID=$(jget "$EV/prov-surv.json" crid)
[ -n "$MHID" ] && [ -n "$SHID" ] && ok "VITO minted provisional (merged $MHID) + survivor ($SHID)" || bad "VITO provisional mint failed"

# Build the unknown patient's trauma footprint across every phase owner (patient = merged Health ID).
curl -sS -o "$EV/inc6.json" $(hdr) -X POST $DAI/internal/v1/daidzai/incidents \
  -d "{\"emergencyCategory\":\"TRAUMA\",\"severity\":\"CRITICAL\",\"description\":\"unknown RTC\",\"facilityId\":\"$FAC\",\"subjectIdentityMode\":\"KNOWN\",\"subjectHealthId\":\"$MHID\"}"
INC6=$(jget "$EV/inc6.json" id); EP6=$(jget "$EV/inc6.json" traumaEpisodeId)
curl -sS -o "$EV/edv6.json" $(hdr TREATMENT $EP6) -X POST $PCT/v1/ed/visits -d "{\"patientCpid\":\"$MHID\",\"facilityId\":\"$FAC\",\"arrivalMode\":\"AMBULANCE\"}"
V6=$(jget2 "$EV/edv6.json" visit_id)
curl -sS -o "$EV/ecase6.json" $(hdr) -X POST $PCT/v1/ed/emergency-cases -d "{\"presentingComplaint\":\"unknown trauma\",\"knownHealthId\":\"$MHID\",\"facilityId\":\"$FAC\"}"
curl -sS -o "$EV/act6.json" $(hdr EMERGENCY) -X POST $INP/internal/v1/emergency/activate -d "{\"patientId\":\"$MHID\",\"protocolType\":\"TRAUMA\"}"
curl -sS -o "$EV/bord6.json" $(hdr TREATMENT $EP6) -X POST $MADI/internal/v1/madi/orders -d "{\"patient_cpid\":\"$MHID\",\"blood_group\":\"O-\",\"component_type\":\"PRBC\",\"units_requested\":2}"
curl -sS -o "$EV/case6.json" $(hdr) -X POST $INP/internal/v1/theatre/cases -d "{\"patientId\":\"$MHID\",\"procedureName\":\"Emergency laparotomy\",\"procedureCode\":\"0DTJ4ZZ\",\"triagePriority\":\"URGENT\"}"
# Confirm the footprint is on the tombstoned id before merge.
FOOT=$(( $(DAISQL "SELECT count(*) FROM daidzai.dai_trauma_episode WHERE subject_health_id='$MHID'") + $(PCTSQL "SELECT count(*) FROM pct.ed_visit WHERE patient_cpid='$MHID'") + $(INPSQL "SELECT count(*) FROM inpatient.emergency_activation WHERE subject_cpid='$MHID'") + $(MADISQL "SELECT count(*) FROM madi.blood_orders WHERE patient_cpid='$MHID'") + $(INPSQL "SELECT count(*) FROM inpatient.procedure_episode WHERE subject_cpid='$MHID'") ))
[ "$FOOT" -ge 5 ] && ok "trauma footprint on the provisional Health ID across 5 owners ($FOOT rows)" || bad "footprint incomplete pre-merge ($FOOT)"

# VITO reversible merge: provisional → confirmed survivor (emits vito.merge.executed).
# Merge is NATIONAL-POD-ONLY (FederationAuthorityGuard): send X-Pod-ID:national (not national-spine).
curl -sS -o "$EV/merge6.json" -H Content-Type:application/json -H X-Tenant-ID:$TEN -H X-Pod-ID:national \
  -H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H X-Actor-ID:$ACTOR -H X-Actor-Type:PROVIDER \
  -H X-Purpose-Of-Use:TREATMENT -H Idempotency-Key:idem-$(uuidgen) -X POST $VITO/internal/v1/patients/merge \
  -d "{\"survivor_crid\":\"$SCRID\",\"merged_crids\":[\"$MCRID\"],\"reason\":\"IDENTIFIED\"}"
[ "$(VITOSQL "SELECT count(*) FROM vito.event_outbox WHERE event_type='vito.merge.executed'")" -ge 1 ] \
  && ok "VITO emitted vito.merge.executed (the missing propagation channel)" || bad "no vito.merge.executed event"
VITOSQL "SELECT payload FROM vito.event_outbox WHERE event_type='vito.merge.executed' ORDER BY id DESC LIMIT 1" > "$EV/merge-payload.json"

# Deliver the merge event to every participant's fan-out (Kafka off → internal repoint endpoint).
for pair in "daidzai:$DAI" "pct:$PCT" "inpatient:$INP" "madi:$MADI"; do
  nm=${pair%%:*}; url=${pair#*:}
  curl -sS -o "$EV/repoint-$nm.json" -w "   repoint $nm HTTP %{http_code}\n" $(hdr) -X POST $url/internal/v1/identity/vito-merge --data-binary @"$EV/merge-payload.json" | tee -a "$EV/journal.txt"
done

# Zero orphans: nothing left on the tombstoned id; every link repointed to the survivor, per column.
ORPHANS=$(( $(DAISQL "SELECT count(*) FROM daidzai.dai_trauma_episode WHERE subject_health_id='$MHID'") + $(DAISQL "SELECT count(*) FROM daidzai.dai_emergency_incident WHERE subject_health_id='$MHID'") + $(PCTSQL "SELECT count(*) FROM pct.ed_visit WHERE patient_cpid='$MHID'") + $(PCTSQL "SELECT count(*) FROM pct.emergency_cases WHERE known_health_id='$MHID'") + $(INPSQL "SELECT count(*) FROM inpatient.emergency_activation WHERE subject_cpid='$MHID'") + $(INPSQL "SELECT count(*) FROM inpatient.procedure_episode WHERE subject_cpid='$MHID'") + $(MADISQL "SELECT count(*) FROM madi.blood_orders WHERE patient_cpid='$MHID'") ))
[ "$ORPHANS" = 0 ] && ok "ZERO orphans — no trauma row left on the tombstoned provisional Health ID" || bad "$ORPHANS orphan row(s) still on $MHID"
# Per-column repoint to the survivor (each table on its OWN patient column).
[ "$(DAISQL "SELECT count(*) FROM daidzai.dai_trauma_episode WHERE subject_health_id='$SHID'")" -ge 1 ] && ok "daidzai.dai_trauma_episode.subject_health_id → survivor" || bad "episode not repointed"
[ "$(PCTSQL "SELECT count(*) FROM pct.ed_visit WHERE patient_cpid='$SHID'")" -ge 1 ] && ok "pct.ed_visit.patient_cpid → survivor" || bad "ed_visit not repointed"
[ "$(PCTSQL "SELECT count(*) FROM pct.emergency_cases WHERE known_health_id='$SHID'")" -ge 1 ] && ok "pct.emergency_cases.known_health_id → survivor" || bad "emergency_cases not repointed"
[ "$(INPSQL "SELECT count(*) FROM inpatient.emergency_activation WHERE subject_cpid='$SHID'")" -ge 1 ] && ok "inpatient.emergency_activation.subject_cpid → survivor (resus anchor)" || bad "emergency_activation not repointed"
[ "$(INPSQL "SELECT count(*) FROM inpatient.procedure_episode WHERE subject_cpid='$SHID'")" -ge 1 ] && ok "inpatient.procedure_episode.subject_cpid → survivor (theatre hook auto-collected)" || bad "procedure_episode not repointed — theatre hook not collected"
[ "$(MADISQL "SELECT count(*) FROM madi.blood_orders WHERE patient_cpid='$SHID'")" -ge 1 ] && ok "madi.blood_orders.patient_cpid → survivor" || bad "blood_orders not repointed"

# ═════ J-TR-7: trauma outbox drainage + idempotency ══════════════════════════════
say "J-TR-7: trauma outbox drains; duplicate Idempotency-Key ⇒ no duplicate row"
for i in $(seq 1 12); do
  U=$(DAISQL "SELECT count(*) FROM daidzai.dai_outbox WHERE event_type LIKE 'daidzai.%' AND published_at IS NULL")
  [ "${U:-1}" = 0 ] && break; sleep 1
done
[ "${U:-1}" = 0 ] && ok "all daidzai trauma outbox events drained (no-Kafka drainer)" || bad "undrained daidzai outbox rows remain ($U)"
# Duplicate Idempotency-Key on an EMS dispatch ⇒ the companion filter replays, no duplicate mission.
IDEM7=idem-dup-$(uuidgen)
curl -sS -o "$EV/dup1.json" $(hdr TREATMENT "" "$IDEM7") -X POST $DAI/internal/v1/daidzai/ems/incidents/$INC6/dispatch -d '{"callSign":"AMB-DUP"}'
curl -sS -o "$EV/dup2.json" $(hdr TREATMENT "" "$IDEM7") -X POST $DAI/internal/v1/daidzai/ems/incidents/$INC6/dispatch -d '{"callSign":"AMB-DUP"}'
[ "$(DAISQL "SELECT count(*) FROM daidzai.dai_ems_mission WHERE incident_id='$INC6'")" = 1 ] \
  && ok "duplicate Idempotency-Key ⇒ exactly one mission (no dup row)" || bad "duplicate Idempotency-Key created a duplicate mission"

# ═════ J-TR-8: negative-authz + emergency break-glass (audited, not a bypass) ═════
say "J-TR-8: missing headers rejected; emergency O-neg release is break-glass + audited"
HTTP=$(curl -sS -o "$EV/badauth.json" -w '%{http_code}' $(hdr_bad) "$MADI/internal/v1/madi/orders")
[ "$HTTP" = 400 ] || [ "$HTTP" = 401 ] && ok "missing mandatory header rejected (HTTP $HTTP)" || bad "malformed trust envelope not rejected (got $HTTP)"
# Emergency uncrossmatched release WITHOUT emergency purpose ⇒ DENIED by the shared guard.
HTTP=$(curl -sS -o /dev/null -w '%{http_code}' $(hdr TREATMENT) -X POST $MADI/internal/v1/madi/orders/$BORDER/emergency-release -d '{"reason":"exsanguinating"}')
[ "$HTTP" = 403 ] && ok "emergency O-neg release DENIED without break-glass purpose (403, not a bypass)" || bad "emergency release not denied without break-glass (got $HTTP)"
# WITH emergency purpose ⇒ allowed + elevated break-glass audit event.
HTTP=$(curl -sS -o /dev/null -w '%{http_code}' $(hdr EMERGENCY) -X POST $MADI/internal/v1/madi/orders/$BORDER/emergency-release -d '{"reason":"exsanguinating trauma","bloodUnitId":"ONEG-1"}')
[ "$HTTP" = 200 ] && ok "emergency O-neg release ALLOWED under break-glass (200)" || bad "break-glass release failed (got $HTTP)"
[ "$(MADISQL "SELECT count(*) FROM madi.event_outbox WHERE event_type='EMERGENCY_RELEASE_BREAK_GLASS' AND aggregate_id='$BORDER'")" -ge 1 ] \
  && ok "break-glass release wrote an elevated audit outbox event (override recorded)" || bad "no break-glass audit event"

# ═════ J-TR-9: disposition closes the episode; timeline reconstructs coherent ═════
say "J-TR-9: ED disposition closes the trauma episode (surgery does not)"
# Put the ED journey in a discharge-eligible state (the rig moves the ED workflow truth to IN_SERVICE,
# as a triaged trauma patient in active resus would be — the ED queue lifecycle is PCT-owned + tested
# elsewhere; here we only need the disposition precondition satisfied).
PCTSQL "UPDATE pct.pct_journeys SET state='IN_SERVICE' WHERE journey_id=(SELECT journey_id FROM pct.ed_visit WHERE visit_id='$VISIT')" >/dev/null
curl -sS -o "$EV/dispo.json" $(hdr) -X POST $PCT/v1/ed/visits/$VISIT/disposition -d '{"dispositionType":"ADMIT","diagnosis":"polytrauma"}'
sleep 1  # allow the daidzai close push to land
[ "$(DAISQL "SELECT status FROM daidzai.dai_trauma_episode WHERE id='$EP'")" = "CLOSED" ] \
  && ok "trauma episode CLOSED on ED disposition (G1.11)" || bad "episode not CLOSED after disposition"
[ -n "$(DAISQL "SELECT closed_at FROM daidzai.dai_trauma_episode WHERE id='$EP' AND closed_at IS NOT NULL")" ] \
  && ok "episode closed_at recorded" || bad "closed_at not set"
# Coherent timeline reconstructable from persisted rows.
curl -sS -o "$EV/final-episode.json" $(hdr) $DAI/internal/v1/daidzai/trauma-episodes/$EP >/dev/null
python3 -c "import json;r=json.load(open('$EV/final-episode.json'));t=[p['phase'] for p in r.get('timeline',[])];exit(0 if r.get('status')=='CLOSED' and 'INCIDENT' in t and 'DISPOSITION' in t else 1)" \
  && ok "closed episode timeline reconstructs coherently (INCIDENT..DISPOSITION)" || bad "closed episode timeline incoherent"

# ── Summary ────────────────────────────────────────────────────────────────────
say "SUMMARY: PASS=$PASS FAIL=$FAIL"
echo "{\"pass\":$PASS,\"fail\":$FAIL}" > "$EV/summary.json"
[ "$FAIL" = 0 ] || exit 1
