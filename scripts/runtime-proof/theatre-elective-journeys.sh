#!/usr/bin/env bash
# ============================================================================
# Elective operating-theatre journey — runtime proof (J-TH-1..2).
#
# THE POINT: the perioperative episode pipeline (inpatient.procedure_episode +
# TheatreController /internal/v1/theatre/** + ProcedureEpisodeController
# /internal/v1/procedures/**) drives a real elective case end to end —
# intake -> preop (nursing + anaesthesia) -> readiness (ROOM resolves READY
# once a theatre room is assigned; owner-routed TEAM/EQUIPMENT stay honest) ->
# book -> WHO Sign-In -> consent gate -> start -> intra-op -> signed operative
# note -> PACU disposition -> COMPLETED — with DB rows and outbox events proving
# each transition, and the no-fake guarantee honoured for downstream owners that
# are not booted in this rig (varapi/vashandi/asset-registry/OROS/Butano/PCT):
# they degrade to UNAVAILABLE/BLOCKED, never a fabricated READY.
#
#   J-TH-1  full elective happy path (single service, real Postgres) — every
#           state transition asserted in inpatient.procedure_episode /
#           procedure_preop_assessment / procedure_checklist_item /
#           procedure_note / procedure_intraop_event + event_outbox rows.
#   J-TH-2  honesty gates — start is blocked without granted MVUMO consent (409),
#           booking is blocked without readiness (409), and no down owner is ever
#           fabricated READY.
#
# Requirements: docker, java 21, packaged jar for inpatient-service.
#   mvn -f services/pom.xml -pl inpatient-service -am package -DskipTests
# Usage: EV=<dir> bash scripts/runtime-proof/theatre-elective-journeys.sh
#        KEEP_RIG=1 to leave infra + inpatient running for inspection.
# ============================================================================
set -u
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
EV=${EV:-$REPO/reports/journeys/theatre-elective-proof-$(date +%Y-%m-%d)}
RIGLOG=${RIGLOG:-/tmp/theatre-elective-logs}
mkdir -p "$EV" "$RIGLOG"
TEN=00000000-0000-4000-8000-000000000001
FAC=00000000-0000-4000-8000-0000000000fa
ROOM=a3000000-0000-4000-8000-000000000001   # HCH Main Theatre 1 (TUSO seed 20)
SURGEON=PROV-ZW-00020                        # surgeon.moyo (VARAPI seed 18)
ANAES=PROV-ZW-00021                          # anaes.ndlovu
PGPORT=15682; RPORT=16437; INP=http://localhost:28121
PASS=0; FAIL=0
ok(){ echo "   PASS: $1" | tee -a "$EV/journal.txt"; PASS=$((PASS+1)); }
bad(){ echo "   FAIL: $1" | tee -a "$EV/journal.txt"; FAIL=$((FAIL+1)); }
say(){ echo "== $1" | tee -a "$EV/journal.txt"; }
PSQL(){ docker exec theatre-rig-pg psql -U impilo -d inpatient -tAc "$1"; }
# trust headers — actor is the surgeon; oauth disabled for the rig
hdr(){ local a=${1:-$SURGEON}
  echo "-H Content-Type:application/json -H X-Tenant-ID:$TEN -H X-Pod-ID:national-spine -H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H X-Facility-ID:$FAC -H X-Actor-ID:$a -H X-Actor-Type:PROVIDER -H X-Purpose-Of-Use:SURGICAL_CARE -H Idempotency-Key:idem-$(uuidgen)"; }
jget(){ python3 -c "import json,sys;d=json.load(open('$1'));print(json.loads(json.dumps(d)).get('$2',''))" 2>/dev/null; }

command -v docker >/dev/null || { echo "docker not available — rig cannot run"; exit 2; }

# ── Infra + inpatient-service ──────────────────────────────────────────────────
say "RIG: infra + inpatient-service"
docker rm -f theatre-rig-pg theatre-rig-redis >/dev/null 2>&1
docker run -d --name theatre-rig-pg -e POSTGRES_USER=impilo -e POSTGRES_PASSWORD=impilo -p $PGPORT:5432 postgres:16-alpine >/dev/null
docker run -d --name theatre-rig-redis -p $RPORT:6379 redis:7-alpine >/dev/null
sleep 8
docker exec theatre-rig-pg psql -U impilo -d postgres -c "CREATE DATABASE inpatient" >/dev/null 2>&1

jar(){ ls "$REPO/services/inpatient-service/target/inpatient-service-"*.jar 2>/dev/null | grep -v original | head -1; }
[ -n "$(jar)" ] || { echo "inpatient-service jar missing — run: mvn -f services/pom.xml -pl inpatient-service -am package -DskipTests"; exit 2; }
env POSTGRES_HOST=localhost POSTGRES_PORT=$PGPORT POSTGRES_USER=impilo POSTGRES_PASSWORD=impilo \
  IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true SERVER_PORT=28121 SPRING_KAFKA_LISTENER_AUTO_STARTUP=false \
  INPATIENT_EMIT_MODE=LEGACY \
  SPRING_DATA_REDIS_HOST=localhost SPRING_DATA_REDIS_PORT=$RPORT \
  nohup java -jar "$(jar)" > "$RIGLOG/inpatient.log" 2>&1 & echo $! > "$RIGLOG/inpatient.pid"

c=000
for i in $(seq 1 60); do c=$(curl -s -o /dev/null -w '%{http_code}' $INP/actuator/health 2>/dev/null); [ "$c" = 200 ] && break; sleep 3; done
echo "   inpatient health: $c" | tee -a "$EV/journal.txt"
[ "$c" = 200 ] && ok "inpatient-service booted" || { bad "inpatient did not boot"; tail -40 "$RIGLOG/inpatient.log"; exit 1; }

cleanup(){ if [ -z "${KEEP_RIG:-}" ]; then kill "$(cat "$RIGLOG/inpatient.pid")" >/dev/null 2>&1; docker rm -f theatre-rig-pg theatre-rig-redis >/dev/null 2>&1; fi }
trap cleanup EXIT

# ═════ J-TH-1: elective happy path ═══════════════════════════════════════════
say "J-TH-1: elective theatre case — intake to COMPLETED"

# 1. OROS PROCEDURE intake -> theatre case (OROS not booted -> oros_order_id null, case still created)
HTTP=$(curl -sS -o "$EV/1-intake.json" -w '%{http_code}' $(hdr) -X POST $INP/internal/v1/theatre/cases \
  -d "{\"patientId\":\"CPID-ZW-THEATRE-EL1\",\"procedureName\":\"Elective inguinal hernia repair\",\"procedureCode\":\"0YQ40ZZ\",\"triagePriority\":\"ELECTIVE\",\"surgeonId\":\"$SURGEON\",\"anaesthetistId\":\"$ANAES\"}")
CASE=$(jget "$EV/1-intake.json" id)
[ "$HTTP" = 201 ] && [ -n "$CASE" ] && ok "intake created theatre case ($CASE)" || { bad "intake HTTP $HTTP: $(cat "$EV/1-intake.json")"; exit 1; }
[ "$(PSQL "SELECT status FROM inpatient.procedure_episode WHERE episode_id='$CASE'")" = BOOKED ] && ok "episode row persisted status=BOOKED" || bad "episode not BOOKED"
[ "$(PSQL "SELECT count(*) FROM inpatient.procedure_checklist_item WHERE episode_id='$CASE'")" = 12 ] && ok "WHO checklist seeded (12 items across 3 phases)" || bad "checklist item count != 12"
[ -z "$(jget "$EV/1-intake.json" oros_order_id)" ] && ok "no fabricated OROS order id (owner not booted)" || bad "unexpected oros_order_id"

# 2. Preop — nursing then anaesthesia clearance
curl -sS -o "$EV/2-preop-nursing.json" $(hdr) -X POST $INP/internal/v1/procedures/episodes/$CASE/preop \
  -d '{"assessmentType":"NURSING","fastingVerified":true,"allergiesReviewed":true,"assessedBy":"ward.dube"}' >/dev/null
[ "$(PSQL "SELECT status FROM inpatient.procedure_episode WHERE episode_id='$CASE'")" = PREOP ] && ok "nursing preop advanced episode to PREOP" || bad "episode not PREOP after nursing preop"
curl -sS -o "$EV/2-preop-anaes.json" $(hdr $ANAES) -X POST $INP/internal/v1/procedures/episodes/$CASE/preop \
  -d '{"assessmentType":"ANAESTHESIA","asaClass":"II","mallampatiClass":"I","cormackLehaneGrade":"1","clearedForTheatre":true,"assessedBy":"'$ANAES'"}' >/dev/null
[ "$(PSQL "SELECT count(*) FROM inpatient.procedure_preop_assessment WHERE episode_id='$CASE'")" = 2 ] && ok "two preop assessments persisted (nursing + anaesthesia)" || bad "preop assessment count != 2"
[ "$(PSQL "SELECT cleared_for_theatre FROM inpatient.procedure_preop_assessment WHERE episode_id='$CASE' AND assessment_type='ANAESTHESIA'")" = t ] && ok "anaesthesia assessment cleared_for_theatre=true" || bad "anaesthesia not cleared"
[ "$(PSQL "SELECT completed FROM inpatient.procedure_checklist_item WHERE episode_id='$CASE' AND item_code='ANAESTHESIA_CHECK'")" = t ] && ok "anaesthesia clearance auto-completed WHO ANAESTHESIA_CHECK" || bad "ANAESTHESIA_CHECK not auto-completed"

# 3. Readiness — ROOM resolves READY once a theatre room is assigned; owners stay honest
HTTP=$(curl -sS -o "$EV/3-readiness.json" -w '%{http_code}' $(hdr) -X POST $INP/internal/v1/theatre/cases/$CASE/readiness \
  -d "{\"theatreRoomId\":\"$ROOM\",\"bloodRequired\":false}")
[ "$HTTP" = 200 ] && ok "readiness evaluated (200)" || bad "readiness HTTP $HTTP"
python3 - "$EV/3-readiness.json" <<'PY' | tee -a "$EV/journal.txt"
import json,sys
r=json.load(open(sys.argv[1])); checks={c["domain"]:c for c in r.get("checks",[])}
room=checks.get("ROOM",{}); team=[c for c in r.get("checks",[]) if c["domain"]=="TEAM"]
print("   PASS: ROOM READY (owner=%s)"%room.get("owner_service") if room.get("status")=="READY" else "   FAIL: ROOM not READY -> %s"%room)
# no down owner may be fabricated READY
fake=[c for c in r.get("checks",[]) if c["domain"] in ("TEAM","EQUIPMENT") and c["status"]=="READY"]
print("   PASS: no fabricated READY for un-booted owners (TEAM/EQUIPMENT honest)" if not fake else "   FAIL: fabricated READY: %s"%fake)
print("   PASS: not bookable yet (owners down, honest)" if r.get("bookable") is False else "   FAIL: unexpectedly bookable")
PY
python3 -c "import json;r=json.load(open('$EV/3-readiness.json'));cs=[c for c in r['checks'] if c['domain']=='ROOM' and c['status']=='READY'];exit(0 if cs else 1)" && PASS=$((PASS+1)) || FAIL=$((FAIL+1))
python3 -c "import json;r=json.load(open('$EV/3-readiness.json'));cs=[c for c in r['checks'] if c['domain'] in ('TEAM','EQUIPMENT') and c['status']=='READY'];exit(0 if not cs else 1)" && PASS=$((PASS+1)) || FAIL=$((FAIL+1))
[ "$(PSQL "SELECT theatre_room_id FROM inpatient.procedure_episode WHERE episode_id='$CASE'")" = "$ROOM" ] && ok "theatre_room_id stamped on episode" || bad "theatre_room_id not stamped"
[ "$(PSQL "SELECT count(*) FROM inpatient.procedure_readiness_check WHERE episode_id='$CASE' AND domain='ROOM' AND status='READY'")" -ge 1 ] && ok "ROOM readiness check row persisted READY" || bad "no ROOM READY readiness row"

# 4. Booking — blocked without override (owners down), then audited emergency override
HTTP=$(curl -sS -o "$EV/4-book-blocked.json" -w '%{http_code}' $(hdr) -X POST $INP/internal/v1/theatre/cases/$CASE/book -d '{}')
[ "$HTTP" = 409 ] && grep -q BOOKING_BLOCKED "$EV/4-book-blocked.json" && ok "booking blocked 409 without readiness (owner blockers)" || bad "expected 409 BOOKING_BLOCKED, got $HTTP"
HTTP=$(curl -sS -o "$EV/4-book.json" -w '%{http_code}' $(hdr) -X POST $INP/internal/v1/theatre/cases/$CASE/book \
  -d '{"emergencyOverride":true,"emergencyOverrideReason":"Elective list; downstream team/equipment owners not in single-service rig, confirmed offline"}')
[ "$HTTP" = 200 ] && ok "booking accepted under audited override (200)" || bad "override book HTTP $HTTP: $(cat "$EV/4-book.json")"

# 5. WHO Sign-In — complete remaining items
IDS=$(curl -sS $(hdr) $INP/internal/v1/procedures/episodes/$CASE | python3 -c "import json,sys;d=json.load(sys.stdin);print(json.dumps([i['id'] for i in d.get('checklist',[]) if i['phase']=='SIGN_IN']))")
curl -sS -o /dev/null $(hdr) -X POST $INP/internal/v1/procedures/episodes/$CASE/checklist/SIGN_IN \
  -d "{\"itemIds\":$IDS,\"completedBy\":\"coord.mutasa\"}"
DONE=$(PSQL "SELECT count(*) FROM inpatient.procedure_checklist_item WHERE episode_id='$CASE' AND phase='SIGN_IN' AND completed=true")
[ "$DONE" = 4 ] && ok "WHO Sign-In fully completed (4/4 items)" || bad "SIGN_IN completed items = $DONE (expected 4)"

# 6. Consent gate — start blocked without granted MVUMO consent, then granted
HTTP=$(curl -sS -o "$EV/6-start-blocked.json" -w '%{http_code}' $(hdr) -X POST $INP/internal/v1/theatre/cases/$CASE/start -d '{}')
[ "$HTTP" = 409 ] && ok "start blocked 409 without granted MVUMO consent (consent gate)" || bad "expected 409 consent gate, got $HTTP: $(cat "$EV/6-start-blocked.json")"
MVUMO=$(uuidgen)
curl -sS -o /dev/null $(hdr) -X POST $INP/internal/v1/procedures/episodes/$CASE/consent-evidence \
  -d "{\"mvumoConsentRequestId\":\"$MVUMO\",\"consentStatus\":\"GRANTED\"}"
[ "$(PSQL "SELECT consent_status FROM inpatient.procedure_episode WHERE episode_id='$CASE'")" = GRANTED ] && ok "MVUMO consent bound GRANTED" || bad "consent not GRANTED"

# 7. Start theatre
HTTP=$(curl -sS -o "$EV/7-start.json" -w '%{http_code}' $(hdr) -X POST $INP/internal/v1/theatre/cases/$CASE/start -d '{}')
[ "$HTTP" = 200 ] && [ "$(PSQL "SELECT status FROM inpatient.procedure_episode WHERE episode_id='$CASE'")" = IN_PROGRESS ] && ok "theatre started -> IN_PROGRESS" || bad "start HTTP $HTTP; status=$(PSQL "SELECT status FROM inpatient.procedure_episode WHERE episode_id='$CASE'")"

# 8. Intra-op event
curl -sS -o /dev/null $(hdr) -X POST $INP/internal/v1/procedures/episodes/$CASE/intraop/events \
  -d '{"eventType":"INCISION","description":"Skin incision, groin","recordedBy":"'$SURGEON'"}'
[ "$(PSQL "SELECT count(*) FROM inpatient.procedure_intraop_event WHERE episode_id='$CASE'")" -ge 1 ] && ok "intra-op event recorded" || bad "no intra-op event row"

# 9. Operative note — draft then sign (varapi not booted -> scope UNAVAILABLE, local sign still succeeds; butano refs null)
HTTP=$(curl -sS -o "$EV/9-note.json" -w '%{http_code}' $(hdr) -X POST $INP/internal/v1/theatre/cases/$CASE/note \
  -d '{"performedProcedure":"Open inguinal hernia repair (Lichtenstein)","findings":"Indirect hernia, mesh repair","estimatedBloodLossMl":50,"countsCorrect":true,"postopPlan":"Simple analgesia, discharge day 1"}')
[ "$HTTP" = 201 ] && grep -q '"status": *"DRAFT"' "$EV/9-note.json" && ok "operative note drafted (DRAFT)" || bad "note draft HTTP $HTTP"
HTTP=$(curl -sS -o "$EV/9-sign.json" -w '%{http_code}' $(hdr) -X POST $INP/internal/v1/theatre/cases/$CASE/note/sign \
  -d "{\"signedProviderId\":\"$SURGEON\"}")
[ "$HTTP" = 200 ] && ok "operative note signed (200)" || bad "note sign HTTP $HTTP: $(cat "$EV/9-sign.json")"
[ "$(PSQL "SELECT status FROM inpatient.procedure_note WHERE episode_id='$CASE'")" = SIGNED ] && ok "note row SIGNED" || bad "note not SIGNED"
[ "$(PSQL "SELECT signed_provider_id FROM inpatient.procedure_note WHERE episode_id='$CASE'")" = "$SURGEON" ] && ok "note signed_provider_id = surgeon" || bad "signed_provider_id mismatch"
[ -z "$(PSQL "SELECT butano_procedure_ref FROM inpatient.procedure_note WHERE episode_id='$CASE'")" ] && ok "no fabricated Butano ref (owner not booted)" || bad "unexpected butano_procedure_ref"

# 10. PACU disposition + episode completion
curl -sS -o /dev/null $(hdr) -X POST $INP/internal/v1/procedures/episodes/$CASE/pacu -d '{"recordedBy":"pacu.sibanda"}'
HTTP=$(curl -sS -o "$EV/10-pacu.json" -w '%{http_code}' $(hdr) -X POST $INP/internal/v1/theatre/cases/$CASE/pacu/disposition \
  -d '{"disposition":"WARD","aldreteScore":9,"painScore":2,"recordedBy":"pacu.sibanda"}')
[ "$HTTP" = 200 ] && [ "$(PSQL "SELECT status FROM inpatient.procedure_episode WHERE episode_id='$CASE'")" = RECOVERED ] && ok "PACU disposition WARD -> RECOVERED" || bad "pacu disposition HTTP $HTTP"
curl -sS -o /dev/null $(hdr) -X POST $INP/internal/v1/procedures/episodes/$CASE/complete -d '{}'
[ "$(PSQL "SELECT status FROM inpatient.procedure_episode WHERE episode_id='$CASE'")" = COMPLETED ] && ok "episode COMPLETED" || bad "episode not COMPLETED"

# 11. Outbox — the transition events are durably queued
say "J-TH-1: outbox events"
for ev in theatre.case.intake theatre.readiness.evaluated theatre.case.booked theatre.case.started theatre.note.signed theatre.pacu.disposition; do
  n=$(PSQL "SELECT count(*) FROM inpatient.event_outbox WHERE aggregate_id='$CASE' AND event_type='$ev'")
  [ "$n" -ge 1 ] && ok "outbox row present: $ev" || bad "outbox missing: $ev"
done

# ═════ J-TH-2: honesty on a second case (no-fake / gate re-proof) ════════════
say "J-TH-2: honesty gates on a fresh case"
curl -sS -o "$EV/j2-intake.json" $(hdr) -X POST $INP/internal/v1/theatre/cases \
  -d "{\"patientId\":\"CPID-ZW-THEATRE-EL2\",\"procedureName\":\"Elective cholecystectomy\",\"triagePriority\":\"ELECTIVE\",\"surgeonId\":\"$SURGEON\"}" >/dev/null
C2=$(jget "$EV/j2-intake.json" id)
# readiness with NO room -> ROOM BLOCKED (NO_ROOM), not bookable
curl -sS -o "$EV/j2-readiness.json" $(hdr) -X POST $INP/internal/v1/theatre/cases/$C2/readiness -d '{}' >/dev/null
python3 -c "import json;r=json.load(open('$EV/j2-readiness.json'));room=[c for c in r['checks'] if c['domain']=='ROOM'][0];exit(0 if room['status']=='BLOCKED' else 1)" \
  && ok "no room assigned -> ROOM BLOCKED (NO_ROOM)" || bad "ROOM not BLOCKED without a room"
# start before consent -> 409 (consent gate holds even after checklist bypass attempt)
HTTP=$(curl -sS -o /dev/null -w '%{http_code}' $(hdr) -X POST $INP/internal/v1/theatre/cases/$C2/start -d '{}')
[ "$HTTP" = 409 ] && ok "start blocked 409 on un-prepared case (checklist/consent gate)" || bad "expected 409, got $HTTP"

# ═════ Verdict ═══════════════════════════════════════════════════════════════
say "VERDICT: $PASS passed, $FAIL failed (evidence: $EV/journal.txt)"
[ "$FAIL" = 0 ] || exit 1
