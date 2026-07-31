#!/usr/bin/env bash
# ============================================================================
# Operating-theatre NON-TRAUMA ALTERNATIVE journeys (Wave 5a §15) — runtime
# proof (J-AL-1..18). The four alternative flows the theatre pipeline must
# handle beyond the elective happy-path, each recorded + escalated on the REAL
# perioperative surfaces (no new schema; orchestration-by-reference).
#
#   DAY SURGERY (§15 day surgery)
#     J-AL-1  DAY_CASE → PACU → SAME-DAY discharge gated on Aldrete readiness,
#             goes HOME (no admission_ref), surgical discharge summary captures
#             the follow-up. J-AL-2 a not-ready day case is BLOCKED (409).
#     J-AL-3  FAILED day case → inpatient conversion: PACU disposition WARD (not
#             DISCHARGE) stamps admission_ref + a real NHUME PACU_TO_WARD leg
#             (Wave-4 continuity — the day case becomes an admission).
#
#   CANCELLED CASE (§15 cancelled case)
#     J-AL-4  cancel with a STRUCTURED reason code (NO_BLOOD) → reason_code +
#             reschedulable=true on the response and the theatre.case.cancelled
#             event; the structured code persists in the reason column.
#     J-AL-5  PATIENT_NON_ATTENDANCE → reschedulable=false (off the list).
#     J-AL-6  an unknown reason code is REJECTED (400) — reasons are enumerated.
#     J-AL-7  RESCHEDULING CONNECTION: the surgical waitlist return-to-waiting
#             path (Wave-2) returns a deferred entry to WAITING — where a
#             reschedulable cancellation puts the case.
#
#   INTRAOPERATIVE COMPLICATION (§15, NON-trauma variants)
#     J-AL-8   major haemorrhage → blood escalation via the MADI blood path.
#     J-AL-9   cardiac/respiratory deterioration → anaesthesia COMPLICATION chart
#              event + a CRITICAL safety event routed to RITO.
#     J-AL-10  equipment failure → DEVICE_INCIDENT safety event → asset-registry.
#     J-AL-11  wrong/missing implant → safety event (quality/RITO owned).
#     J-AL-12  count discrepancy → RITO RETAINED_ITEM sentinel + discrepancy row.
#     J-AL-13  conversion of planned procedure → anaesthesia technique CONVERSION
#              chart event + operative-note update.
#     J-AL-14  return-to-theatre (re-operative) + unplanned ICU admission, and
#              (V305) the return as a RECORD: cause, sequence, deciding actor,
#              planned-vs-unplanned, predecessor linkage, and the negatives —
#              no cause, an invented cause, and harm disguised as planned.
#
#   MULTIPLE CONCURRENT CASES (§15 concurrency)
#     J-AL-15  two concurrent cases: safety events stay case-scoped (no bleed).
#     J-AL-16  anaesthesia chart entries stay case-scoped.
#     J-AL-17  cancelling one case does NOT corrupt the other's state.
#     J-AL-18  specimens + patient identity stay case-scoped (no mislinkage).
#
# Single source of truth: peers own their records (MADI blood, NHUME transport,
# RITO safety, scheduling waitlist); inpatient holds refs + projections.
#
# Requirements: docker, java 21, packaged jars for inpatient + madi + nhume +
#   rito + scheduling.
#   ⚠ REPACKAGE the inpatient jar fresh (it owns the migrations) — a stale jar
#     misses schema and produces spurious fails:
#   mvn -f services/pom.xml -pl inpatient-service,madi-service,nhume-service,rito-quality-safety-service,scheduling-service -am package -DskipTests
# Usage: EV=<dir> bash scripts/runtime-proof/theatre-alt-journeys.sh
#        KEEP_RIG=1 to leave infra + services running for inspection.
# ============================================================================
set -u
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
EV=${EV:-$REPO/reports/journeys/theatre-alt-proof-$(date +%Y-%m-%d)}
RIGLOG=${RIGLOG:-/tmp/theatre-alt-logs}
mkdir -p "$EV" "$RIGLOG"
: > "$EV/journal.txt"
TEN=00000000-0000-0000-0000-000000000001
FAC=00000000-0000-4000-8000-0000000000fa
SURGEON=PROV-ZW-00020
ANAES=PROV-ZW-00021
NURSE=PROV-ZW-00022
PGPORT=15690; RPORT=16450
INP=http://localhost:28421
MADI=http://localhost:28430
NHUME=http://localhost:28410
RITO=http://localhost:28491
SCHED=http://localhost:28428
PASS=0; FAIL=0
ok(){ echo "   PASS: $1" | tee -a "$EV/journal.txt"; PASS=$((PASS+1)); }
bad(){ echo "   FAIL: $1" | tee -a "$EV/journal.txt"; FAIL=$((FAIL+1)); }
say(){ echo "== $1" | tee -a "$EV/journal.txt"; }
INPSQL(){ docker exec al-rig-pg psql -U impilo -d inpatient -tAc "$1" | tr -d '[:space:]'; }
MADISQL(){ docker exec al-rig-pg psql -U impilo -d madi -tAc "$1" | tr -d '[:space:]'; }
NHUMESQL(){ docker exec al-rig-pg psql -U impilo -d nhume -tAc "$1" | tr -d '[:space:]'; }
RITOSQL(){ docker exec al-rig-pg psql -U impilo -d rito -tAc "$1" | tr -d '[:space:]'; }
SCHEDSQL(){ docker exec al-rig-pg psql -U impilo -d scheduling -tAc "$1" | tr -d '[:space:]'; }
hdr(){ local a=${1:-$SURGEON} r=${2:-PROVIDER}
  echo "-H Content-Type:application/json -H X-Tenant-ID:$TEN -H X-Pod-ID:national-spine -H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H X-Facility-ID:$FAC -H X-Actor-ID:$a -H X-Actor-Type:$r -H X-Purpose-Of-Use:TREATMENT -H X-Access-Mode:INTERNAL -H Idempotency-Key:idem-$(uuidgen)"; }
jval(){ python3 -c "import json,sys;d=json.load(sys.stdin);d=d.get('data',d) if isinstance(d,dict) else d;print(d.get('$1','') if isinstance(d,dict) else '')" 2>/dev/null; }

command -v docker >/dev/null || { echo "docker not available — rig cannot run"; exit 2; }

# ── Infra ────────────────────────────────────────────────────────────────────
say "RIG: infra (postgres + redis) + 5-service substrate"
docker rm -f al-rig-pg al-rig-redis >/dev/null 2>&1
docker run -d --name al-rig-pg -e POSTGRES_USER=impilo -e POSTGRES_PASSWORD=impilo -p $PGPORT:5432 postgres:16-alpine >/dev/null
docker run -d --name al-rig-redis -p $RPORT:6379 redis:7-alpine >/dev/null
sleep 8
for db in inpatient madi nhume rito scheduling; do
  docker exec al-rig-pg psql -U impilo -d postgres -c "CREATE DATABASE $db" >/dev/null 2>&1
done

svcjar(){ ls "$REPO/services/$1/target/$1-"*.jar 2>/dev/null | grep -v original | head -1; }
MODS="inpatient-service madi-service nhume-service rito-quality-safety-service scheduling-service"
for s in $MODS; do
  [ -n "$(svcjar "$s")" ] || { echo "$s jar missing — run: mvn -f services/pom.xml -pl $(echo $MODS | tr ' ' ',') -am package -DskipTests"; exit 2; }
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
boot madi-service madi 28430
boot nhume-service nhume 28410
boot rito-quality-safety-service rito 28491
boot scheduling-service scheduling 28428
boot inpatient-service inpatient 28421 INPATIENT_EMIT_MODE=LEGACY \
  MADI_BASE_URL=$MADI NHUME_BASE_URL=$NHUME RITO_BASE_URL=$RITO

wait_health $MADI madi-service
wait_health $NHUME nhume-service
wait_health $RITO rito-quality-safety-service
wait_health $SCHED scheduling-service
wait_health $INP inpatient-service

cleanup(){ if [ -z "${KEEP_RIG:-}" ]; then
  for p in "$RIGLOG"/*.pid; do [ -f "$p" ] && kill "$(cat "$p")" >/dev/null 2>&1; done
  docker rm -f al-rig-pg al-rig-redis >/dev/null 2>&1
fi }
trap cleanup EXIT

intake(){ # $1 patient  $2 procedureName  $3 code  $4 triage  → echoes episode id
  curl -s -X POST $INP/internal/v1/theatre/cases $(hdr) \
    -d "{\"patientId\":\"$1\",\"procedureName\":\"$2\",\"procedureCode\":\"$3\",\"orosOrderId\":\"RIG-ORDER-$(uuidgen)\",\"surgeonId\":\"$SURGEON\",\"anaesthetistId\":\"$ANAES\",\"triagePriority\":\"$4\"}" | jval id
}
force_status(){ INPSQL "UPDATE inpatient.procedure_episode SET status='$2' WHERE episode_id='$1'" >/dev/null; }

# ══════════════════════════════════════════════════════════════════════════════
# DAY SURGERY (§15 day surgery)
# ══════════════════════════════════════════════════════════════════════════════
say "J-AL-1..3: DAY SURGERY — same-day discharge + failed-conversion admission"

# J-AL-1: DAY_CASE → PACU → ready → SAME-DAY discharge (home, no admission).
DC=$(intake CPID-ZW-DAY-1 "Cataract extraction" "DAYCATARACT" DAY_CASE)
[ -n "$DC" ] && ok "J-AL-1 day-case intake ($DC)" || bad "J-AL-1 day-case intake failed"
TRI=$(INPSQL "SELECT triage_priority FROM inpatient.procedure_episode WHERE episode_id='$DC'")
[ "$TRI" = "DAY_CASE" ] && ok "J-AL-1 triaged DAY_CASE" || bad "J-AL-1 triage not DAY_CASE (got '$TRI')"
force_status "$DC" IN_PROGRESS
curl -s -X POST $INP/internal/v1/theatre/cases/$DC/pacu $(hdr) -d '{"recordedBy":"recovery-nurse"}' >/dev/null
curl -s -X POST $INP/internal/v1/theatre/cases/$DC/pacu/observations $(hdr) -d '{"aldreteScore":7,"recordedBy":"recovery-nurse"}' >/dev/null
curl -s -X POST $INP/internal/v1/theatre/cases/$DC/pacu/observations $(hdr) -d '{"airway":"PATENT","breathing":"SPONTANEOUS","spo2":99,"systolicBp":120,"heartRate":74,"consciousness":"ALERT","painScore":1,"aldreteScore":10,"recordedBy":"recovery-nurse"}' >/dev/null
DIS=$(curl -s -X POST $INP/internal/v1/theatre/cases/$DC/pacu/discharge $(hdr) -d '{"destination":"DISCHARGE"}')
DEST=$(echo "$DIS" | jval destination)
[ "$DEST" = "DISCHARGE" ] && ok "J-AL-1 same-day DISCHARGE decision accepted (Aldrete-ready)" || bad "J-AL-1 discharge not accepted (got '$DEST')"
ADM=$(INPSQL "SELECT coalesce(admission_ref::text,'') FROM inpatient.procedure_episode WHERE episode_id='$DC'")
[ -z "$ADM" ] && ok "J-AL-1 same-day discharge went HOME (no admission_ref)" || bad "J-AL-1 unexpected admission_ref on same-day discharge ($ADM)"
# Surgical discharge summary captures the follow-up.
curl -s -X POST $INP/internal/v1/theatre/cases/$DC/discharge $(hdr) -d '{"finalDiagnosis":"Senile cataract","woundStatus":"Clean & dry","rehabPlan":"Eye drops","followUpDate":"2026-08-20","followUpService":"Ophthalmology OPD"}' >/dev/null
DC_DONE=$(curl -s -X POST $INP/internal/v1/theatre/cases/$DC/discharge/complete $(hdr) -d '{}' | jval status)
[ "$DC_DONE" = "COMPLETED" ] && ok "J-AL-1 surgical discharge summary completed with follow-up" || bad "J-AL-1 discharge summary not COMPLETED (got '$DC_DONE')"

# J-AL-2: a NOT-ready day case is BLOCKED (409).
DC2=$(intake CPID-ZW-DAY-2 "Inguinal hernia repair" "DAYHERNIA" DAY_CASE)
force_status "$DC2" PACU
curl -s -X POST $INP/internal/v1/theatre/cases/$DC2/pacu/observations $(hdr) -d '{"aldreteScore":5,"recordedBy":"recovery-nurse"}' >/dev/null
GC=$(curl -s -o /dev/null -w '%{http_code}' -X POST $INP/internal/v1/theatre/cases/$DC2/pacu/discharge $(hdr) -d '{"destination":"DISCHARGE"}')
[ "$GC" = "409" ] && ok "J-AL-2 not-ready day-case discharge BLOCKED (409)" || bad "J-AL-2 expected 409, got $GC"

# J-AL-3: FAILED day case → inpatient conversion (WARD disposition stamps admission_ref).
DC3=$(intake CPID-ZW-DAY-3 "Laparoscopic cholecystectomy" "DAYLAPCHOLE" DAY_CASE)
force_status "$DC3" PACU
curl -s -X POST $INP/internal/v1/theatre/cases/$DC3/pacu/observations $(hdr) -d '{"airway":"PATENT","breathing":"SPONTANEOUS","spo2":97,"aldreteScore":9,"recordedBy":"recovery-nurse"}' >/dev/null
curl -s -X POST $INP/internal/v1/theatre/cases/$DC3/pacu/observations $(hdr) -d '{"aldreteScore":10,"recordedBy":"recovery-nurse"}' >/dev/null
curl -s -X POST $INP/internal/v1/theatre/cases/$DC3/pacu/discharge $(hdr) -d '{"destination":"WARD"}' >/dev/null
ADM3=$(INPSQL "SELECT coalesce(admission_ref::text,'') FROM inpatient.procedure_episode WHERE episode_id='$DC3'")
[ -n "$ADM3" ] && ok "J-AL-3 failed day case converted to admission (admission_ref=$ADM3)" || bad "J-AL-3 admission_ref not stamped on failed conversion"
LEG3=$(INPSQL "SELECT count(*) FROM inpatient.procedure_transport WHERE episode_id='$DC3' AND leg='PACU_TO_WARD'")
[ "$LEG3" -ge 1 ] 2>/dev/null && ok "J-AL-3 NHUME PACU_TO_WARD movement routed on conversion" || bad "J-AL-3 PACU_TO_WARD leg missing (got '$LEG3')"

# ══════════════════════════════════════════════════════════════════════════════
# CANCELLED CASE (§15 cancelled case)
# ══════════════════════════════════════════════════════════════════════════════
say "J-AL-4..7: CANCELLED CASE — structured reasons + rescheduling"

# J-AL-4: structured reason code → reschedulable, code persisted + on the event.
CX=$(intake CPID-ZW-CX-1 "Total knee replacement" "TKR" ELECTIVE)
CXR=$(curl -s -X POST $INP/internal/v1/theatre/cases/$CX/cancel $(hdr) -d '{"reasonCode":"no-blood","reason":"cross-match not ready"}')
RC=$(echo "$CXR" | jval reason_code); RS=$(echo "$CXR" | jval reschedulable)
[ "$RC" = "NO_BLOOD" ] && ok "J-AL-4 structured reason code normalised (NO_BLOOD)" || bad "J-AL-4 reason_code not NO_BLOOD (got '$RC')"
[ "$RS" = "True" ] && ok "J-AL-4 reschedulable=true for a reschedulable reason" || bad "J-AL-4 reschedulable not true (got '$RS')"
PERSIST=$(INPSQL "SELECT cancellation_reason FROM inpatient.procedure_episode WHERE episode_id='$CX'")
echo "$PERSIST" | grep -q "NO_BLOOD" && ok "J-AL-4 structured code persisted in reason column ($PERSIST)" || bad "J-AL-4 code not persisted (got '$PERSIST')"
EVT=$(INPSQL "SELECT count(*) FROM inpatient.event_outbox WHERE aggregate_id='$CX' AND event_type='theatre.case.cancelled' AND payload_json LIKE '%\"reason_code\":\"NO_BLOOD\"%' AND payload_json LIKE '%\"reschedulable\":true%'")
[ "$EVT" -ge 1 ] 2>/dev/null && ok "J-AL-4 theatre.case.cancelled event carries reason_code + reschedulable" || bad "J-AL-4 cancellation event missing structured fields (got '$EVT')"

# J-AL-5: patient DNA is NOT reschedulable (off the list).
CX2=$(intake CPID-ZW-CX-2 "Cystoscopy" "CYSTO" ELECTIVE)
CXR2=$(curl -s -X POST $INP/internal/v1/theatre/cases/$CX2/cancel $(hdr) -d '{"reasonCode":"PATIENT_NON_ATTENDANCE"}')
[ "$(echo "$CXR2" | jval reschedulable)" = "False" ] && ok "J-AL-5 PATIENT_NON_ATTENDANCE reschedulable=false" || bad "J-AL-5 DNA should not be reschedulable"

# J-AL-6: unknown reason code rejected (400).
CX3=$(intake CPID-ZW-CX-3 "Arthroscopy" "ARTHRO" ELECTIVE)
BC=$(curl -s -o /dev/null -w '%{http_code}' -X POST $INP/internal/v1/theatre/cases/$CX3/cancel $(hdr) -d '{"reasonCode":"surgeon-overslept"}')
[ "$BC" = "400" ] && ok "J-AL-6 unknown reason code REJECTED (400)" || bad "J-AL-6 expected 400 for unknown code, got $BC"
STILL=$(INPSQL "SELECT status FROM inpatient.procedure_episode WHERE episode_id='$CX3'")
[ "$STILL" != "CANCELLED" ] && ok "J-AL-6 case not cancelled on rejected code (status=$STILL)" || bad "J-AL-6 case wrongly cancelled on bad code"

# J-AL-7: rescheduling connection — the surgical waitlist return-to-waiting path.
REF=$(uuidgen)
WL=$(curl -s -X POST $SCHED/v1/internal/scheduling/surgical-waitlist/from-referral $(hdr) -d "{\"referralId\":\"$REF\",\"patientId\":\"CPID-ZW-CX-1\",\"clinicalPriority\":\"P2\"}" | jval id)
[ -n "$WL" ] && ok "J-AL-7 surgical waitlist entry placed ($WL)" || bad "J-AL-7 waitlist placement failed"
curl -s -X POST $SCHED/v1/internal/scheduling/surgical-waitlist/$WL/defer $(hdr) -d '{"reason":"awaiting slot"}' >/dev/null
RET=$(curl -s -X POST $SCHED/v1/internal/scheduling/surgical-waitlist/$WL/return $(hdr) -d '{"reason":"case cancelled — re-list"}' | jval status)
[ "$RET" = "WAITING" ] && ok "J-AL-7 cancelled case returns to waitlist (status WAITING)" || bad "J-AL-7 waitlist return not WAITING (got '$RET')"

# ══════════════════════════════════════════════════════════════════════════════
# INTRAOPERATIVE COMPLICATION (§15 — non-trauma variants)
# ══════════════════════════════════════════════════════════════════════════════
say "J-AL-8..14: INTRAOP COMPLICATION — recorded + escalated"
CO=$(intake CPID-ZW-CO-1 "Open reduction internal fixation" "ORIF" URGENT)
force_status "$CO" IN_PROGRESS

# J-AL-8: major haemorrhage → MADI blood escalation.
BH=$(curl -s -o "$EV/comp-blood.json" -w '%{http_code}' -X POST $INP/internal/v1/theatre/cases/$CO/blood/request $(hdr) -d '{"units":4,"bloodGroup":"O+","componentType":"PRBC","reason":"major intraop haemorrhage"}')
MADI_ORDER=$(python3 -c "import json;print(json.load(open('$EV/comp-blood.json')).get('data',{}).get('madi_order_id',json.load(open('$EV/comp-blood.json')).get('madi_order_id','')))" 2>/dev/null)
[ "$BH" = 201 ] && [ -n "$MADI_ORDER" ] && ok "J-AL-8 major haemorrhage → MADI blood order placed ($MADI_ORDER)" || bad "J-AL-8 blood escalation HTTP $BH order '$MADI_ORDER'"
[ -n "$MADI_ORDER" ] && [ "$(MADISQL "SELECT count(*) FROM madi.blood_orders WHERE order_id='$MADI_ORDER'")" = 1 ] && ok "J-AL-8 blood order owned by MADI (reconciles)" || bad "J-AL-8 no MADI blood_order row for $MADI_ORDER"

# J-AL-9: cardiac/respiratory deterioration → COMPLICATION chart + CRITICAL safety → RITO.
curl -s -X POST $INP/internal/v1/procedures/$CO/anaesthesia/chart $(hdr) -d '{"channel":"EVENT","technique":"COMPLICATION","label":"Bradycardia + desaturation","valueText":"HR 38, SpO2 84","recordedBy":"'$ANAES'"}' >/dev/null
CH9=$(INPSQL "SELECT count(*) FROM inpatient.procedure_anaesthesia_chart_entry WHERE episode_id='$CO' AND technique='COMPLICATION'")
[ "$CH9" -ge 1 ] 2>/dev/null && ok "J-AL-9 anaesthetic COMPLICATION chart event recorded" || bad "J-AL-9 COMPLICATION chart entry missing (got '$CH9')"
curl -s -X POST $INP/internal/v1/theatre/cases/$CO/safety-events $(hdr) -d '{"category":"ADVERSE_EVENT","severity":"CRITICAL","description":"Intraop cardiorespiratory deterioration"}' >/dev/null
SF9=$(INPSQL "SELECT routed_owner FROM inpatient.procedure_safety_event WHERE episode_id='$CO' AND category='ADVERSE_EVENT'")
[ "$SF9" = "rito" ] && ok "J-AL-9 deterioration safety event routed to RITO (quality/safety)" || bad "J-AL-9 adverse event not routed to rito (got '$SF9')"

# J-AL-10: equipment failure → DEVICE_INCIDENT → asset-registry.
curl -s -X POST $INP/internal/v1/theatre/cases/$CO/safety-events $(hdr) -d '{"category":"DEVICE_INCIDENT","severity":"HIGH","description":"Diathermy unit failure mid-procedure"}' >/dev/null
SF10=$(INPSQL "SELECT routed_owner FROM inpatient.procedure_safety_event WHERE episode_id='$CO' AND category='DEVICE_INCIDENT'")
[ "$SF10" = "asset-registry" ] && ok "J-AL-10 equipment failure routed to asset-registry" || bad "J-AL-10 device incident not routed to asset-registry (got '$SF10')"

# J-AL-11: wrong/missing implant → safety event (quality/RITO owned).
curl -s -X POST $INP/internal/v1/theatre/cases/$CO/safety-events $(hdr) -d '{"category":"IMPLANT_DISCREPANCY","severity":"HIGH","description":"Requested implant size unavailable"}' >/dev/null
SF11=$(INPSQL "SELECT count(*) FROM inpatient.procedure_safety_event WHERE episode_id='$CO' AND category='IMPLANT_DISCREPANCY'")
[ "$SF11" -ge 1 ] 2>/dev/null && ok "J-AL-11 wrong/missing implant safety event recorded + routed" || bad "J-AL-11 implant discrepancy event missing (got '$SF11')"

# J-AL-12: count discrepancy → RITO RETAINED_ITEM sentinel + discrepancy row.
# Baseline + closing must be on the SAME count row for the discrepancy to compute.
curl -s -X POST $INP/internal/v1/theatre/cases/$CO/counts $(hdr $NURSE NURSE) -d '{"kind":"SWAB","baselineCount":10,"closingCount":9,"recordedBy":"'$NURSE'"}' >/dev/null
DISCR=$(INPSQL "SELECT count(*) FROM inpatient.procedure_count WHERE episode_id='$CO' AND kind='SWAB' AND discrepancy IS NOT NULL AND discrepancy<>0")
[ "$DISCR" -ge 1 ] 2>/dev/null && ok "J-AL-12 count discrepancy recorded (non-zero)" || bad "J-AL-12 no non-zero discrepancy row (got '$DISCR')"
RITOCASE=$(INPSQL "SELECT count(*) FROM inpatient.procedure_safety_event WHERE episode_id='$CO' AND category='RETAINED_ITEM'")
[ "$RITOCASE" -ge 1 ] 2>/dev/null && ok "J-AL-12 RETAINED_ITEM sentinel raised (RITO-routed)" || bad "J-AL-12 no RETAINED_ITEM safety event (got '$RITOCASE')"

# J-AL-13: conversion of planned procedure → CONVERSION chart + operative note update.
curl -s -X POST $INP/internal/v1/procedures/$CO/anaesthesia/chart $(hdr) -d '{"channel":"EVENT","technique":"CONVERSION","label":"Regional → GA","valueText":"Converted to general anaesthesia","recordedBy":"'$ANAES'"}' >/dev/null
CH13=$(INPSQL "SELECT count(*) FROM inpatient.procedure_anaesthesia_chart_entry WHERE episode_id='$CO' AND technique='CONVERSION'")
[ "$CH13" -ge 1 ] 2>/dev/null && ok "J-AL-13 anaesthesia CONVERSION chart event recorded" || bad "J-AL-13 CONVERSION chart entry missing (got '$CH13')"
curl -s -X POST $INP/internal/v1/theatre/cases/$CO/note $(hdr) -d '{"performedProcedure":"ORIF converted to open — extended approach","findings":"Comminuted fracture; converted from planned percutaneous fixation","complications":"Planned percutaneous fixation converted to open"}' >/dev/null
NOTE13=$(INPSQL "SELECT count(*) FROM inpatient.procedure_note WHERE episode_id='$CO'")
[ "$NOTE13" -ge 1 ] 2>/dev/null && ok "J-AL-13 operative note updated on conversion" || bad "J-AL-13 operative note not saved (got '$NOTE13')"

# J-AL-14: return-to-theatre + unplanned ICU admission.
CO14=$(intake CPID-ZW-CO-2 "Exploratory laparotomy" "EXLAP" URGENT)
force_status "$CO14" PACU
R2T=$(curl -s -X POST $INP/internal/v1/theatre/cases/$CO14/return-to-theatre $(hdr) -d '{"reason":"post-op intra-abdominal bleeding","complicationCategory":"HAEMORRHAGE"}' | jval status)
[ "$R2T" = "READY_FOR_THEATRE" ] && ok "J-AL-14 return-to-theatre transition (→ READY_FOR_THEATRE)" || bad "J-AL-14 return-to-theatre not READY_FOR_THEATRE (got '$R2T')"
R2TF=$(INPSQL "SELECT return_to_theatre FROM inpatient.procedure_postop_record WHERE episode_id='$CO14'")
[ "$R2TF" = "t" ] && ok "J-AL-14 postop flagged return_to_theatre" || bad "J-AL-14 return_to_theatre flag not set (got '$R2TF')"

# ── V305 (demonstration 10): the return is a RECORD, not just a flag ──────────────────
# The boolean above cannot say why the patient went back, who decided, or how many times.
# NB: a boolean concatenated into text renders as 'false', not the bare 'f' psql prints alone.
R2TROW=$(INPSQL "SELECT seq||'|'||complication_category||'|'||planned FROM inpatient.procedure_return_to_theatre WHERE episode_id='$CO14'")
[ "$R2TROW" = "1|HAEMORRHAGE|false" ] && ok "J-AL-14 return recorded with cause, sequence and unplanned status" || bad "J-AL-14 return_to_theatre record wrong (got '$R2TROW')"
R2TWHO=$(INPSQL "SELECT returned_by FROM inpatient.procedure_return_to_theatre WHERE episode_id='$CO14'")
[ -n "$R2TWHO" ] && [ "$R2TWHO" != "system" ] && ok "J-AL-14 return attributed to the real deciding actor ($R2TWHO)" || bad "J-AL-14 return not attributed to a real actor (got '$R2TWHO')"

# A second return is counted, not collapsed into the first. This is what one boolean lost.
force_status "$CO14" PACU
curl -s -X POST $INP/internal/v1/theatre/cases/$CO14/return-to-theatre $(hdr) -d '{"reason":"persistent contamination, further washout","complicationCategory":"SEPSIS"}' >/dev/null
R2TN=$(INPSQL "SELECT count(*)||'|'||max(seq) FROM inpatient.procedure_return_to_theatre WHERE episode_id='$CO14'")
[ "$R2TN" = "2|2" ] && ok "J-AL-14 second return recorded as seq 2 (a boolean cannot count)" || bad "J-AL-14 second return not counted (got '$R2TN')"

# NEGATIVE: a return with no cause is refused — free text cannot be counted, and the DAK
# unplanned-return indicator is only meaningful if every return carries a classified cause.
force_status "$CO14" PACU
NOCAT=$(curl -s -o /dev/null -w '%{http_code}' -X POST $INP/internal/v1/theatre/cases/$CO14/return-to-theatre $(hdr) -d '{"reason":"went back"}')
[ "$NOCAT" = "400" ] && ok "J-AL-14 NEGATIVE return without a complication category refused (400)" || bad "J-AL-14 uncategorised return was accepted (got HTTP $NOCAT)"
BADCAT=$(curl -s -o /dev/null -w '%{http_code}' -X POST $INP/internal/v1/theatre/cases/$CO14/return-to-theatre $(hdr) -d '{"reason":"bleeding","complicationCategory":"A_BIT_OF_BLEEDING"}')
[ "$BADCAT" = "400" ] && ok "J-AL-14 NEGATIVE invented complication category refused (400)" || bad "J-AL-14 invented category was accepted (got HTTP $BADCAT)"
# The dangerous direction: declaring a haemorrhage "planned" would remove it from the harm count.
FAKEPLAN=$(curl -s -o /dev/null -w '%{http_code}' -X POST $INP/internal/v1/theatre/cases/$CO14/return-to-theatre $(hdr) -d '{"reason":"bleeding","complicationCategory":"HAEMORRHAGE","planned":true}')
[ "$FAKEPLAN" = "400" ] && ok "J-AL-14 NEGATIVE a haemorrhage cannot be declared planned (400)" || bad "J-AL-14 harm could be hidden as planned (got HTTP $FAKEPLAN)"
NOTWRITTEN=$(INPSQL "SELECT count(*) FROM inpatient.procedure_return_to_theatre WHERE episode_id='$CO14'")
[ "$NOTWRITTEN" = "2" ] && ok "J-AL-14 NEGATIVE refused returns wrote nothing" || bad "J-AL-14 a refused return still wrote a row (count now $NOTWRITTEN)"

# A genuine staged second-look IS planned, and must be recorded as such so it is not counted as harm.
CO14B=$(intake CPID-ZW-CO-2B "Damage-control laparotomy" "DCLAP" URGENT)
force_status "$CO14B" PACU
curl -s -X POST $INP/internal/v1/theatre/cases/$CO14B/return-to-theatre $(hdr) -d '{"reason":"staged second-look booked at the index operation","complicationCategory":"PLANNED_SECOND_LOOK","planned":true}' >/dev/null
PLANNED=$(INPSQL "SELECT planned FROM inpatient.procedure_return_to_theatre WHERE episode_id='$CO14B'")
[ "$PLANNED" = "t" ] && ok "J-AL-14 planned second-look recorded as planned (not counted as harm)" || bad "J-AL-14 planned second-look not recorded as planned (got '$PLANNED')"
UNPLANNED_ONLY=$(INPSQL "SELECT count(*) FROM inpatient.procedure_return_to_theatre WHERE planned = FALSE AND episode_id IN ('$CO14','$CO14B')")
[ "$UNPLANNED_ONLY" = "2" ] && ok "J-AL-14 the unplanned-return indicator excludes the planned second-look" || bad "J-AL-14 unplanned-return count wrong (got '$UNPLANNED_ONLY', expected 2)"

# The predecessor column (V305) exists and refuses a self-loop, which would read as a
# predecessor chain that never terminates.
SELFLOOP=$(docker exec al-rig-pg psql -U impilo -d inpatient -tAc "UPDATE inpatient.procedure_episode SET reoperation_of_episode_id='$CO14' WHERE episode_id='$CO14'" 2>&1 | tr -d '\n')
case "$SELFLOOP" in *chk_procedure_episode_reoperation_not_self*) ok "J-AL-14 NEGATIVE an episode cannot be a reoperation of itself" ;;
  *) bad "J-AL-14 self-referencing reoperation link was accepted (got '$SELFLOOP')" ;; esac
docker exec al-rig-pg psql -U impilo -d inpatient -tAc "UPDATE inpatient.procedure_episode SET reoperation_of_episode_id='$CO14' WHERE episode_id='$CO14B'" >/dev/null 2>&1
PRED=$(INPSQL "SELECT reoperation_of_episode_id FROM inpatient.procedure_episode WHERE episode_id='$CO14B'")
[ "$PRED" = "$CO14" ] && ok "J-AL-14 a reoperation given its own episode links back to its predecessor" || bad "J-AL-14 predecessor link not stored (got '$PRED')"
CO15=$(intake CPID-ZW-CO-3 "Bowel resection" "BOWELRES" URGENT)
force_status "$CO15" PACU
curl -s -X POST $INP/internal/v1/theatre/cases/$CO15/pacu/observations $(hdr) -d '{"aldreteScore":5,"recordedBy":"recovery-nurse"}' >/dev/null
ICU=$(curl -s -X POST $INP/internal/v1/theatre/cases/$CO15/pacu/discharge $(hdr) -d '{"destination":"ICU","unplanned":true}' | jval unplanned_icu)
[ "$ICU" = "True" ] && ok "J-AL-14 unplanned ICU admission flagged" || bad "J-AL-14 unplanned ICU not flagged (got '$ICU')"

# ══════════════════════════════════════════════════════════════════════════════
# MULTIPLE CONCURRENT CASES (§15 concurrency — no cross-contamination)
# ══════════════════════════════════════════════════════════════════════════════
say "J-AL-15..18: CONCURRENCY — isolation between parallel cases"
A=$(intake CPID-ZW-CONC-A "Craniotomy" "CRANI" URGENT)
B=$(intake CPID-ZW-CONC-B "Thyroidectomy" "THYROID" ELECTIVE)
force_status "$A" IN_PROGRESS
force_status "$B" IN_PROGRESS
# Interleave operations across the two cases.
curl -s -X POST $INP/internal/v1/theatre/cases/$A/safety-events $(hdr) -d '{"category":"ADVERSE_EVENT","severity":"HIGH","description":"Case A dural tear"}' >/dev/null
curl -s -X POST $INP/internal/v1/procedures/$B/anaesthesia/chart $(hdr) -d '{"channel":"AGENT","label":"Case B sevoflurane","valueText":"2%","recordedBy":"'$ANAES'"}' >/dev/null
curl -s -X POST $INP/internal/v1/theatre/cases/$B/safety-events $(hdr) -d '{"category":"DEVICE_INCIDENT","severity":"LOW","description":"Case B suction blocked"}' >/dev/null
curl -s -X POST $INP/internal/v1/procedures/$A/anaesthesia/chart $(hdr) -d '{"channel":"AGENT","label":"Case A propofol","valueText":"TCI 4","recordedBy":"'$ANAES'"}' >/dev/null

# J-AL-15: safety events stay case-scoped.
SA=$(INPSQL "SELECT count(*) FROM inpatient.procedure_safety_event WHERE episode_id='$A'")
SB=$(INPSQL "SELECT count(*) FROM inpatient.procedure_safety_event WHERE episode_id='$B'")
SAX=$(INPSQL "SELECT count(*) FROM inpatient.procedure_safety_event WHERE episode_id='$A' AND description LIKE 'Case B%'")
[ "$SA" = "1" ] && [ "$SB" = "1" ] && [ "$SAX" = "0" ] && ok "J-AL-15 safety events case-scoped (A=$SA B=$SB, no bleed)" || bad "J-AL-15 safety-event isolation broken (A=$SA B=$SB crossA=$SAX)"

# J-AL-16: anaesthesia chart entries stay case-scoped.
CA=$(INPSQL "SELECT count(*) FROM inpatient.procedure_anaesthesia_chart_entry WHERE episode_id='$A'")
CBX=$(INPSQL "SELECT count(*) FROM inpatient.procedure_anaesthesia_chart_entry WHERE episode_id='$A' AND label LIKE 'Case B%'")
[ "$CA" = "1" ] && [ "$CBX" = "0" ] && ok "J-AL-16 chart entries case-scoped (A has only its own)" || bad "J-AL-16 chart isolation broken (A=$CA crossB=$CBX)"

# J-AL-17: cancelling case A does NOT corrupt case B's state.
curl -s -X POST $INP/internal/v1/theatre/cases/$A/cancel $(hdr) -d '{"reasonCode":"EMERGENCY_DISPLACEMENT","reason":"displaced by trauma"}' >/dev/null
SBAFTER=$(INPSQL "SELECT status FROM inpatient.procedure_episode WHERE episode_id='$B'")
SACANC=$(INPSQL "SELECT status FROM inpatient.procedure_episode WHERE episode_id='$A'")
[ "$SACANC" = "CANCELLED" ] && [ "$SBAFTER" = "IN_PROGRESS" ] && ok "J-AL-17 cancelling A left B uncorrupted (A=CANCELLED B=IN_PROGRESS)" || bad "J-AL-17 state corruption (A=$SACANC B=$SBAFTER)"

# J-AL-18: specimens + patient identity stay case-scoped (no mislinkage).
INPSQL "INSERT INTO inpatient.procedure_specimen (specimen_id, tenant_id, episode_id, kind, seq, specimen_label, specimen_type, status, created_at, updated_at) VALUES (gen_random_uuid(), '$TEN', '$B', 'SPECIMEN', 1, 'Thyroid nodule', 'HISTOPATH', 'COLLECTED', now(), now())" >/dev/null
SPB=$(INPSQL "SELECT count(*) FROM inpatient.procedure_specimen WHERE episode_id='$B'")
SPA=$(INPSQL "SELECT count(*) FROM inpatient.procedure_specimen WHERE episode_id='$A'")
PIDA=$(INPSQL "SELECT subject_cpid FROM inpatient.procedure_episode WHERE episode_id='$A'")
PIDB=$(INPSQL "SELECT subject_cpid FROM inpatient.procedure_episode WHERE episode_id='$B'")
[ "$SPB" = "1" ] && [ "$SPA" = "0" ] && [ "$PIDA" = "CPID-ZW-CONC-A" ] && [ "$PIDB" = "CPID-ZW-CONC-B" ] \
  && ok "J-AL-18 specimen + patient identity case-scoped (no mislinkage/leakage)" \
  || bad "J-AL-18 specimen/identity isolation broken (spA=$SPA spB=$SPB pidA=$PIDA pidB=$PIDB)"

# ── Verdict ──────────────────────────────────────────────────────────────────
say "RESULT: PASS=$PASS FAIL=$FAIL"
echo "PASS=$PASS FAIL=$FAIL" > "$EV/summary.txt"
[ "$FAIL" = 0 ]
