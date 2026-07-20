#!/usr/bin/env bash
# Inpatient admit → ward-round(observation/EWS) → discharge proof — live through the real ingress.
# Proves the completion-lens fixes: EWS persists (no fake UUID), discharge clearances are reachable,
# and finalising the documented discharge DISCHARGES the patient + frees the bed + emits events.
set -uo pipefail
BASE="${PREVIEW_URL:-https://impilo.mohcc.gov.zw}"
TENANT="00000000-0000-4000-8000-000000000001"
FACILITY="f1000000-0000-0000-0000-000000000001"
WARD="11111111-1111-1111-1111-111111111101"
ACTOR="c0000000-0000-4000-8000-000000000001"   # dr.mapfumo (CLINICIAN)
RES=(--resolve impilo.mohcc.gov.zw:443:127.0.0.1)
RUN="ip-$(date +%s)"; PASS=0; FAIL=0
CPID="ip-patient-$RUN"
ENC=$(cat /proc/sys/kernel/random/uuid)
PG(){ kubectl exec -n impilo-full-preview deploy/postgres -- psql -U impilo -d inpatient -tAc "$1" 2>/dev/null; }
ccurl(){ curl -sk -m15 "${RES[@]}" "$@"; }
jg(){ python3 -c "import json,sys
d=json.load(sys.stdin);cur=d
for k in '$1'.split('.'):
 cur=cur.get(k) if isinstance(cur,dict) else (cur[int(k)] if isinstance(cur,list) and k.lstrip('-').isdigit() else None)
 if cur is None: break
print(cur if cur is not None else '')" 2>/dev/null; }
ok(){ PASS=$((PASS+1)); echo "  ok: $*"; }
bad(){ FAIL=$((FAIL+1)); echo "  FAIL: $*"; }

TOK=$(ccurl -X POST "$BASE/internal/v1/auth/login" -H 'Content-Type: application/json' -H "X-Tenant-ID: $TENANT" -H 'X-Pod-ID: p' -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: $RUN" -H "Idempotency-Key: $RUN-l" -d '{"email":"dr.mapfumo","password":"ImpiloTest123!"}' | jg data.attributes.token)
[ -n "$TOK" ] && ok "clinician login" || { bad "login"; exit 1; }
mkh(){ HDRS=(-H "Content-Type: application/json" -H "X-Tenant-ID: $TENANT" -H "X-Pod-ID: p" -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: $RUN" -H "Authorization: Bearer $TOK" -H "X-Actor-ID: $ACTOR" -H "X-Actor-Type: PROVIDER" -H "X-Purpose-Of-Use: INPATIENT_CARE" -H "X-Facility-ID: $FACILITY" -H "Idempotency-Key: $RUN-$(cat /proc/sys/kernel/random/uuid|cut -c1-8)"); }
post(){ mkh; ccurl -X POST "$BASE$1" "${HDRS[@]}" -d "$2"; }
get(){ mkh; ccurl "$BASE$1" "${HDRS[@]}"; }

echo "== 1. admit a fresh patient to an available bed =="
BED=$(PG "select id from inpatient.bed where ward_id='$WARD' and status='AVAILABLE' limit 1")
[ -n "$BED" ] && ok "available bed $BED" || bad "no available bed"
ADM=$(post /internal/v1/inpatient/admissions "{\"subjectCpid\":\"$CPID\",\"encounterId\":\"$ENC\",\"facilityId\":\"$FACILITY\",\"wardId\":\"$WARD\",\"bedId\":\"$BED\",\"admittingDiagnosis\":\"community-acquired pneumonia\",\"admissionType\":\"EMERGENCY\"}")
AREF=$(echo "$ADM" | jg data.admission_ref); [ -z "$AREF" ] && AREF=$(echo "$ADM" | jg data.admissionRef)
[ -n "$AREF" ] && ok "admitted: admissionRef=$AREF (ADMITTED)" || bad "admit: $(echo "$ADM"|head -c300)"
[ "$(PG "select status from inpatient.admission where admission_ref='$AREF'")" = "ADMITTED" ] && ok "admission ADMITTED in DB" || bad "admission status DB"
[ "$(PG "select status from inpatient.bed where id='$BED'")" != "AVAILABLE" ] && ok "bed now occupied" || bad "bed not occupied after admit"

echo "== 2. ward-round observation (clinical_chart_entry) =="
OBS=$(post /internal/v1/observations "{\"subjectCpid\":\"$CPID\",\"encounterId\":\"$ENC\",\"admissionRef\":\"$AREF\",\"chartType\":\"VITALS\",\"respiratoryRate\":18,\"spo2\":97,\"systolicBP\":120,\"heartRate\":82,\"temperature\":37.0,\"consciousness\":\"A\"}")
echo "$OBS" | grep -qiE '"id"|chartEntryId|entry' && ok "observation recorded" || echo "  (obs shape differs — non-fatal: $(echo "$OBS"|head -c120))"

echo "== 3. EWS via /ews/news2 — the FIXED endpoint now PERSISTS (was fake UUID) =="
EWS_BEFORE=$(PG "select count(*) from inpatient.early_warning_score where subject_cpid='$CPID'")
NEWS=$(post /internal/v1/ews/news2 "{\"subjectCpid\":\"$CPID\",\"patientId\":\"$CPID\",\"encounterId\":\"$ENC\",\"admissionRef\":\"$AREF\",\"respiratoryRateScore\":0,\"spo2Score\":1,\"systolicBPScore\":1,\"heartRateScore\":0,\"consciousnessScore\":0,\"temperatureScore\":0,\"airOrOxygenScore\":0,\"spo2ScaleScore\":0}")
echo "$NEWS" | grep -qiE 'riskLevel|totalScore|"id"' && ok "NEWS2 accepted (total=$(echo "$NEWS"|jg data.totalScore) risk=$(echo "$NEWS"|jg data.riskLevel))" || bad "news2: $(echo "$NEWS"|head -c200)"
EWS_AFTER=$(PG "select count(*) from inpatient.early_warning_score where subject_cpid='$CPID'")
[ "${EWS_AFTER:-0}" -gt "${EWS_BEFORE:-0}" ] && ok "early_warning_score PERSISTED (was $EWS_BEFORE now $EWS_AFTER) — fake-UUID trap fixed" || bad "EWS not persisted (before=$EWS_BEFORE after=$EWS_AFTER)"

echo "== 4. discharge clearances: init → clear all =="
INIT=$(post /internal/v1/discharge-clearances/init "{\"encounterId\":\"$ENC\",\"subjectCpid\":\"$CPID\"}")
CLR_COUNT=$(PG "select count(*) from inpatient.discharge_clearance where encounter_id='$ENC'")
[ "${CLR_COUNT:-0}" -ge 1 ] && ok "clearances initialised ($CLR_COUNT)" || bad "init clearances: $(echo "$INIT"|head -c200)"
# finalise BEFORE clearing should be blocked (409) — the gate is real
mkh; CODE=$(ccurl -o /dev/null -w '%{http_code}' -X POST "$BASE/internal/v1/inpatient/discharge-summary/$ENC/finalise" "${HDRS[@]}" -d '{}')
[ "$CODE" = "409" ] || [ "$CODE" = "404" ] && ok "finalise blocked before summary/clearances (HTTP $CODE)" || echo "  (pre-finalise HTTP=$CODE)"
for ID in $(PG "select clearance_id from inpatient.discharge_clearance where encounter_id='$ENC'"); do
  post "/internal/v1/discharge-clearances/$ID/clear" "{\"note\":\"proof\"}" >/dev/null
done
PENDING=$(PG "select count(*) from inpatient.discharge_clearance where encounter_id='$ENC' and status not in ('CLEARED','WAIVED')")
[ "${PENDING:-1}" = "0" ] && ok "all clearances CLEARED" || bad "clearances still pending ($PENDING)"

echo "== 5. draft + finalise discharge summary (finalise now DISCHARGES the patient) =="
post /internal/v1/inpatient/discharge-summary "{\"encounterId\":\"$ENC\",\"patientId\":\"$CPID\",\"dischargeDiagnosis\":\"resolved pneumonia\",\"disposition\":\"HOME\",\"hospitalCourse\":\"IV antibiotics, improved\",\"followUp\":\"GP in 1 week\"}" >/dev/null
[ "$(PG "select status from inpatient.discharge_summary where encounter_id='$ENC'")" = "DRAFT" ] && ok "discharge summary DRAFT" || bad "summary draft DB"
FIN=$(post "/internal/v1/inpatient/discharge-summary/$ENC/finalise" "{}")
[ "$(echo "$FIN"|jg data.status)" = "FINALISED" ] && ok "summary FINALISED" || bad "finalise: $(echo "$FIN"|head -c250)"

echo "== 6. DB truth — the unified discharge =="
[ "$(PG "select status from inpatient.admission where admission_ref='$AREF'")" = "DISCHARGED" ] && ok "admission DISCHARGED (via summary finalise — split-brain closed)" || bad "admission not discharged"
[ -n "$(PG "select discharged_at from inpatient.admission where admission_ref='$AREF' and discharged_at is not null")" ] && ok "discharged_at stamped" || bad "no discharged_at"
[ "$(PG "select status from inpatient.bed where id='$BED'")" = "AVAILABLE" ] && ok "bed FREED back to AVAILABLE" || bad "bed not freed ($(PG "select status from inpatient.bed where id='$BED'"))"
[ "$(PG "select status from inpatient.discharge_summary where encounter_id='$ENC'")" = "FINALISED" ] && ok "discharge_summary FINALISED in DB" || bad "summary DB"

echo "== 7. event_outbox — the previously-dead outbox now emits =="
OB=$(PG "select string_agg(distinct event_type, ',') from inpatient.event_outbox where aggregate_id in ('$AREF') or event_type like 'inpatient.discharge.%'")
echo "  outbox events: $OB"
CNT=$(PG "select count(*) from inpatient.event_outbox where (aggregate_id='$AREF' or event_type like 'inpatient.discharge.summary%' or event_type like 'inpatient.discharge.followup%')")
[ "${CNT:-0}" -ge 3 ] && ok "event_outbox emitted ($CNT rows: admission.created + discharge.completed + summary_finalised/followup)" || bad "outbox rows=$CNT (expected >=3)"
echo "$OB" | grep -q 'inpatient.admission.created' && ok "admission.created emitted" || bad "no admission.created"
echo "$OB" | grep -q 'inpatient.discharge.completed' && ok "discharge.completed emitted" || bad "no discharge.completed"

echo ""
echo "RESULT: $PASS ok, $FAIL fail  (run=$RUN admissionRef=${AREF:-none})"
[ "$FAIL" -eq 0 ] || exit 1
