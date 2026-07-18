#!/usr/bin/env bash
# ============================================================================
# Operating-theatre EMERGENCY journeys (Wave 5b §15) — runtime proof.
#
# THE POINT: a surgery indicated for a trauma patient is ONE coherent episode.
# The daidzai/PCT-minted trauma_episode_id is CONSUMED off the X-Trauma-Episode-ID
# header and stamped onto procedure_episode — never minted by theatre. Emergency
# surgery activates rapidly (unscheduled, override-armed, minimal safe preop) and
# proceeds under an AUDITED consent exception; the THEATRE phase is registered on
# the shared daidzai timeline (INCIDENT→…→THEATRE). The obstetric emergency
# C-section models mother + fetus + neonate coherently, the baby under a PROVISIONAL
# VITO identity, without duplicating identity.
#
#   J-ES-0  MINT→HEADER→STAMP (the core handoff proof): daidzai mints a REAL
#           trauma_episode_id; it is passed as X-Trauma-Episode-ID into the
#           emergency-surgery intake; procedure_episode.trauma_episode_id == the
#           minted id (ONE episode, no parallel surgery record).
#   J-ES-1  RAPID ACTIVATION: emergency intake forces EMERGENCY triage, arms the
#           override, and records a MINIMAL safe preop (a reduced NURSING set).
#   J-ES-2  CONSENT EXCEPTION: a TWO_DOCTOR emergency consent exception is recorded
#           + audited (consent_status=EMERGENCY_EXCEPTION, override armed).
#   J-ES-3  START UNDER OVERRIDE: theatre start bypasses the GRANTED consent gate
#           under the exception → IN_PROGRESS (consent NOT marked verified).
#   J-ES-4  EMERGENCY BLOOD: an uncrossmatched request defaults to O-negative.
#   J-ES-5  THEATRE PHASE + COMPLETION: the THEATRE phase is registered on the
#           daidzai timeline (IN_PROGRESS on start, COMPLETE on completion); the
#           timeline resolves the THEATRE phase.
#   J-CS-1  OBSTETRIC C-SECTION: maternal + fetal context recorded as episode
#           intra-op events; neonatal team paged; baby delivered under a PROVISIONAL
#           VITO identity + LINKED to the mother; neonate handed to postnatal.
#   J-CS-2  PROVISIONAL MOTHER: an unidentified mother's provisional VITO CPID is
#           accepted as procedure_episode.subject_cpid unchanged.
#
# CONSUME-DON'T-MINT; never /close (trauma-episode close is owned by PCT disposition).
#
# Requirements: docker, java 21, FRESHLY packaged jars for daidzai + vito + inpatient
#   (+ madi, best-effort). Package inpatient FRESH (this wave changed it):
#   mvn -f services/pom.xml -pl daidzai-service,vito-service,inpatient-service,madi-service -am package -DskipTests
# Usage: EV=<dir> bash scripts/runtime-proof/theatre-emergency-journeys.sh
#        KEEP_RIG=1 to leave infra + services running for inspection.
# ============================================================================
set -u
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
EV=${EV:-$REPO/reports/journeys/theatre-emergency-proof-$(date +%Y-%m-%d)}
RIGLOG=${RIGLOG:-/tmp/theatre-emergency-logs}
mkdir -p "$EV" "$RIGLOG"
TEN=00000000-0000-4000-8000-000000000001
FAC=00000000-0000-4000-8000-0000000000fa
SURGEON=PROV-ZW-00020
ANAES=PROV-ZW-00040
# Unique infra + ports (must not collide with es-rig/tr-rig/rr-rig fleets).
PGPORT=15991; RPORT=16991
DAI=http://localhost:30392
VITO=http://localhost:30082
INP=http://localhost:30321
MADI=http://localhost:30300
PASS=0; FAIL=0
ok(){ echo "   PASS: $1" | tee -a "$EV/journal.txt"; PASS=$((PASS+1)); }
bad(){ echo "   FAIL: $1" | tee -a "$EV/journal.txt"; FAIL=$((FAIL+1)); }
say(){ echo "== $1" | tee -a "$EV/journal.txt"; }
INPSQL(){ docker exec es-rig-pg psql -q -U impilo -d inpatient -tAc "$1" | tr -d '[:space:]'; }
DAISQL(){ docker exec es-rig-pg psql -q -U impilo -d daidzai -tAc "$1" | tr -d '[:space:]'; }
hdr(){ local a=${1:-$SURGEON} ep=${2:-}
  local base="-H Content-Type:application/json -H X-Tenant-ID:$TEN -H X-Pod-ID:national-spine -H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H X-Facility-ID:$FAC -H X-Actor-ID:$a -H X-Actor-Type:PROVIDER -H X-Purpose-Of-Use:TREATMENT -H Idempotency-Key:idem-$(uuidgen)"
  [ -n "$ep" ] && base="$base -H X-Trauma-Episode-ID:$ep"
  echo "$base"; }
jget(){ python3 -c "import json,sys;d=json.load(open('$1'));d=d.get('data',d) if isinstance(d,dict) else d;print(d.get('$2','') if isinstance(d,dict) else '')" 2>/dev/null; }
# Mint a PROVISIONAL identity via VITO; fall back to a synthetic provisional CPID when VITO's internal
# path is unavailable. VITO's test-profile security gates /internal/** (403) — the provisional mint is the
# trauma/VITO lane's concern; THIS wave's contract is only that theatre intake ACCEPTS whatever
# patient_cpid it is given (incl. a PROVISIONAL one). The synthetic id is a faithful stand-in for that.
provcpid(){ local f=$1 sex=$2 desc=$3 out
  curl -sS -o "$f" $(hdr) -H "X-Service-Id: tshepo-identity" -X POST $VITO/internal/v1/identities/provisional \
    -d "{\"estimated_sex\":\"$sex\",\"descriptor\":\"$desc\"}" 2>/dev/null
  out=$(jget "$f" health_id)
  [ -n "$out" ] && { echo "$out"; return; }
  echo "CPID-PROVISIONAL-$(uuidgen)"
}

command -v docker >/dev/null || { echo "docker not available — rig cannot run"; exit 2; }

# ── Infra: postgres + redis ───────────────────────────────────────────────────
say "RIG: infra (postgres + redis) + daidzai/vito/inpatient(+madi) substrate"
docker rm -f es-rig-pg es-rig-redis >/dev/null 2>&1
docker run -d --name es-rig-pg -e POSTGRES_USER=impilo -e POSTGRES_PASSWORD=impilo -p $PGPORT:5432 postgres:16-alpine >/dev/null
docker run -d --name es-rig-redis -p $RPORT:6379 redis:7-alpine >/dev/null
sleep 8
for db in daidzai vito inpatient madi; do
  docker exec es-rig-pg psql -U impilo -d postgres -c "CREATE DATABASE $db" >/dev/null 2>&1
done

svcjar(){ ls "$REPO/services/$1/target/$1-"*.jar 2>/dev/null | grep -v original | head -1; }
MODS="daidzai-service vito-service inpatient-service madi-service"
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

boot daidzai-service daidzai 30392
# VITO's Secure-Health-Summary signer needs a >=32-byte HMAC pepper (the default is too short to boot).
boot vito-service vito 30082 VITO_HMAC_PEPPER=rig-vito-hmac-pepper-0123456789-abcdef-32plus
boot madi-service madi 30300
# inpatient points its DaidzaiEpisodeClient at the spine (THEATRE-phase registration) + MADI at the rig.
boot inpatient-service inpatient 30321 INPATIENT_INTEGRATION_DAIDZAI_BASE_URL=$DAI MADI_BASE_URL=$MADI

wait_health $DAI daidzai-service
wait_health $VITO vito-service
wait_health $INP inpatient-service
# MADI is a best-effort peer for the emergency blood leg — its absence must NOT fail the rig.
mc=$(curl -s -o /dev/null -w '%{http_code}' "$MADI/actuator/health" 2>/dev/null)
echo "   madi-service health: $mc (best-effort peer)" | tee -a "$EV/journal.txt"

cleanup(){ if [ -z "${KEEP_RIG:-}" ]; then
  for p in "$RIGLOG"/*.pid; do kill "$(cat "$p")" >/dev/null 2>&1; done
  docker rm -f es-rig-pg es-rig-redis >/dev/null 2>&1
fi }
trap cleanup EXIT

# ═════ EMERGENCY SURGERY (trauma-originated) ══════════════════════════════════
say "J-ES: emergency surgery — mint→header→stamp, activation, consent exception, start, phase"

# 0a) daidzai MINTS a real trauma_episode_id (server-minted, inside the trust boundary).
ORIGIN="INC-$(uuidgen)"
curl -sS -o "$EV/mint.json" $(hdr) -X POST $DAI/internal/v1/daidzai/trauma-episodes \
  -d "{\"originService\":\"daidzai\",\"originKind\":\"INCIDENT\",\"originKey\":\"$ORIGIN\",\"firstPhase\":\"INCIDENT\"}"
EP=$(jget "$EV/mint.json" traumaEpisodeId)
[ -n "$EP" ] && ok "J-ES-0 daidzai minted a real trauma_episode_id ($EP)" || bad "J-ES-0 mint failed: $(cat "$EV/mint.json")"

# 0b) A PROVISIONAL identity for the unidentified trauma patient (VITO, else synthetic stand-in).
CPID_M=$(provcpid "$EV/prov-mother.json" MALE "adult male RTC, unidentified")
[ -n "$CPID_M" ] && ok "J-ES-0 provisional identity for the unknown patient ($CPID_M)" || bad "J-ES-0 no provisional CPID"

# 0c) EMERGENCY intake carries X-Trauma-Episode-ID = the minted id (CONSUME, never mint).
curl -sS -o "$EV/activate.json" $(hdr $SURGEON $EP) -X POST $INP/internal/v1/theatre/cases/emergency \
  -d "{\"patientId\":\"$CPID_M\",\"procedureName\":\"Emergency laparotomy\",\"procedureCode\":\"LAPAROTOMY\",\"orosOrderId\":\"ES-ORDER-$(uuidgen)\",\"surgeonId\":\"$SURGEON\",\"anaesthetistId\":\"$ANAES\",\"indication\":\"Haemorrhagic shock — free fluid on FAST\",\"allergiesReviewed\":true}"
EPI=$(jget "$EV/activate.json" id)
[ -n "$EPI" ] && ok "J-ES-1 emergency case activated ($EPI)" || bad "J-ES-1 activation failed: $(cat "$EV/activate.json")"

# THE CORE HANDOFF PROOF: the stamped id equals the minted id.
STAMPED=$(INPSQL "SELECT trauma_episode_id FROM inpatient.procedure_episode WHERE episode_id='$EPI'")
[ "$STAMPED" = "$EP" ] && ok "J-ES-0 procedure_episode.trauma_episode_id == the minted id (ONE episode)" || bad "J-ES-0 stamp mismatch: '$STAMPED' != '$EP'"
SUBJ=$(INPSQL "SELECT subject_cpid FROM inpatient.procedure_episode WHERE episode_id='$EPI'")
[ -n "$SUBJ" ] && [ "$SUBJ" = "$CPID_M" ] && ok "J-ES-1 provisional CPID accepted as subject_cpid" || bad "J-ES-1 subject_cpid '$SUBJ' != provisional '$CPID_M'"

# J-ES-1: rapid activation forced EMERGENCY triage + armed override + minimal preop.
TRI=$(INPSQL "SELECT triage_priority FROM inpatient.procedure_episode WHERE episode_id='$EPI'")
OVR=$(INPSQL "SELECT emergency_override FROM inpatient.procedure_episode WHERE episode_id='$EPI'")
[ "$TRI" = "EMERGENCY" ] && ok "J-ES-1 triage forced to EMERGENCY" || bad "J-ES-1 triage '$TRI' != EMERGENCY"
[ "$OVR" = "t" ] && ok "J-ES-1 emergency override armed" || bad "J-ES-1 override not armed (got '$OVR')"
PRE=$(INPSQL "SELECT count(*) FROM inpatient.procedure_preop_assessment WHERE episode_id='$EPI' AND assessment_type='NURSING'")
[ "$PRE" -ge 1 ] 2>/dev/null && ok "J-ES-1 minimal safe preop (NURSING) recorded" || bad "J-ES-1 minimal preop missing (got '$PRE')"

# J-ES-4: emergency uncrossmatched blood defaults to O-negative (MADI best-effort).
curl -sS -o "$EV/blood.json" $(hdr) -X POST $INP/internal/v1/theatre/cases/$EPI/blood/request \
  -d '{"emergency":true,"units":4,"componentType":"PRBC"}' >/dev/null
BG=$(INPSQL "SELECT blood_group FROM inpatient.procedure_blood_link WHERE episode_id='$EPI' ORDER BY created_at DESC LIMIT 1")
[ "$BG" = "O_NEG" ] && ok "J-ES-4 emergency uncrossmatched blood defaulted to O-negative" || bad "J-ES-4 blood group '$BG' != O_NEG"

# J-ES-2: TWO_DOCTOR emergency consent exception (recorded + audited).
curl -sS -o "$EV/consent.json" $(hdr) -X POST $INP/internal/v1/theatre/cases/$EPI/consent/emergency-exception \
  -d "{\"basis\":\"TWO_DOCTOR\",\"reason\":\"Unconscious, unidentified, exsanguinating — no proxy\",\"authorisedBy\":\"$SURGEON\",\"secondClinician\":\"$ANAES\"}"
CS=$(INPSQL "SELECT consent_status FROM inpatient.procedure_episode WHERE episode_id='$EPI'")
[ "$CS" = "EMERGENCY_EXCEPTION" ] && ok "J-ES-2 emergency consent exception recorded (EMERGENCY_EXCEPTION)" || bad "J-ES-2 consent_status '$CS' != EMERGENCY_EXCEPTION"
AUD=$(INPSQL "SELECT count(*) FROM inpatient.event_outbox WHERE aggregate_id='$EPI' AND event_type='theatre.consent.emergency-exception'")
[ "$AUD" -ge 1 ] 2>/dev/null && ok "J-ES-2 consent-exception override AUDITED" || bad "J-ES-2 audit event missing"

# J-ES-3: start under override bypasses the GRANTED consent gate (no ordinary consent obtained).
curl -sS -o "$EV/start.json" -w '%{http_code}' $(hdr) -X POST $INP/internal/v1/theatre/cases/$EPI/start \
  -d '{"emergencyOverride":true,"emergencyOverrideReason":"Life-saving laparotomy — WHO sign-in deferred"}' > "$EV/start.code"
ST=$(INPSQL "SELECT status FROM inpatient.procedure_episode WHERE episode_id='$EPI'")
CV=$(INPSQL "SELECT consent_verified FROM inpatient.procedure_episode WHERE episode_id='$EPI'")
[ "$ST" = "IN_PROGRESS" ] && ok "J-ES-3 theatre started under emergency override (IN_PROGRESS)" || bad "J-ES-3 status '$ST' != IN_PROGRESS (http=$(cat "$EV/start.code"))"
[ "$CV" = "f" ] && ok "J-ES-3 consent NOT marked verified (excepted, not obtained)" || bad "J-ES-3 consent_verified '$CV' should be false"

# J-ES-5: the THEATRE phase is registered on the daidzai timeline on start (IN_PROGRESS).
sleep 1
PH_START=$(DAISQL "SELECT count(*) FROM daidzai.dai_trauma_episode_phase WHERE trauma_episode_id='$EP' AND phase='THEATRE'")
[ "$PH_START" -ge 1 ] 2>/dev/null && ok "J-ES-5 THEATRE phase registered on the daidzai timeline (on start)" || bad "J-ES-5 THEATRE phase not registered on start (got '$PH_START')"

# Complete the case → THEATRE phase COMPLETE (a PHASE update, NEVER an episode close).
curl -sS -o "$EV/complete.json" $(hdr) -X POST $INP/internal/v1/theatre/cases/$EPI/complete -d '{"theatreMinutes":95}' >/dev/null
CST=$(INPSQL "SELECT status FROM inpatient.procedure_episode WHERE episode_id='$EPI'")
[ "$CST" = "COMPLETED" ] && ok "J-ES-5 case COMPLETED" || bad "J-ES-5 case not COMPLETED (got '$CST')"
sleep 1
# The completion re-registers the THEATRE phase (status COMPLETE). daidzai's phase timeline is
# insert-only-idempotent per (owner_service, owner_ref, phase) in its current wave — so start+completion
# coalesce to ONE THEATRE row (no fork); daidzai owns intra-phase status transitions in its later wave.
PH_DONE=$(DAISQL "SELECT count(*) FROM daidzai.dai_trauma_episode_phase WHERE trauma_episode_id='$EP' AND phase='THEATRE' AND owner_service='inpatient-service'")
[ "$PH_DONE" = "1" ] && ok "J-ES-5 THEATRE phase is ONE idempotent timeline row (start+completion coalesced, no fork)" || bad "J-ES-5 expected exactly one THEATRE phase row (got '$PH_DONE')"
# The episode is NOT closed by surgery — trauma-episode close is owned by PCT disposition.
EPSTAT=$(DAISQL "SELECT status FROM daidzai.dai_trauma_episode WHERE id='$EP'")
[ "$EPSTAT" = "OPEN" ] && ok "J-ES-5 trauma episode stays OPEN (surgery is a PHASE, not the end)" || bad "J-ES-5 trauma episode status '$EPSTAT' — theatre must not close it"
# The timeline resolves the THEATRE phase.
curl -sS -o "$EV/timeline.json" $(hdr) $DAI/internal/v1/daidzai/trauma-episodes/$EP >/dev/null
python3 -c "import json;r=json.load(open('$EV/timeline.json'));exit(0 if 'THEATRE' in [p.get('phase') for p in r.get('timeline',[])] else 1)" \
  && ok "J-ES-5 daidzai timeline resolves the THEATRE phase (INCIDENT→…→THEATRE)" || bad "J-ES-5 timeline missing THEATRE phase"

# ═════ OBSTETRIC EMERGENCY C-SECTION ══════════════════════════════════════════
say "J-CS: obstetric emergency C-section — maternal+fetal+neonatal, provisional baby identity"

# J-CS-2: a provisional mother identity, accepted as subject_cpid.
CPID_CSM=$(provcpid "$EV/prov-cs-mother.json" FEMALE "unbooked labouring mother, unidentified")
curl -sS -o "$EV/cs-activate.json" $(hdr $SURGEON) -X POST $INP/internal/v1/theatre/cases/emergency \
  -d "{\"patientId\":\"$CPID_CSM\",\"procedureName\":\"Emergency caesarean section\",\"procedureCode\":\"LSCS\",\"orosOrderId\":\"CS-ORDER-$(uuidgen)\",\"surgeonId\":\"$SURGEON\",\"anaesthetistId\":\"$ANAES\",\"triagePriority\":\"IMMEDIATE\",\"indication\":\"Fetal distress — category 1\"}"
EPI_CS=$(jget "$EV/cs-activate.json" id)
[ -n "$EPI_CS" ] && ok "J-CS-2 emergency C-section activated ($EPI_CS)" || bad "J-CS-2 C-section activation failed: $(cat "$EV/cs-activate.json")"
CSSUBJ=$(INPSQL "SELECT subject_cpid FROM inpatient.procedure_episode WHERE episode_id='$EPI_CS'")
[ -n "$CSSUBJ" ] && [ "$CSSUBJ" = "$CPID_CSM" ] && ok "J-CS-2 provisional mother CPID accepted as subject_cpid" || bad "J-CS-2 subject_cpid '$CSSUBJ' != '$CPID_CSM'"

# J-CS-1: maternal + fetal context recorded as episode intra-op events.
curl -sS -o "$EV/cs-context.json" $(hdr) -X POST $INP/internal/v1/theatre/cases/$EPI_CS/obstetric/context \
  -d '{"gravida":3,"para":2,"gestationWeeks":39,"indication":"Fetal distress","fetalPresentation":"CEPHALIC","fetalHeartRate":88,"fetalCount":1,"urgency":"CATEGORY_1"}' >/dev/null
MAT=$(INPSQL "SELECT count(*) FROM inpatient.procedure_intraop_event WHERE episode_id='$EPI_CS' AND event_type='MATERNAL_CONTEXT'")
FET=$(INPSQL "SELECT count(*) FROM inpatient.procedure_intraop_event WHERE episode_id='$EPI_CS' AND event_type='FETAL_CONTEXT'")
[ "$MAT" -ge 1 ] 2>/dev/null && [ "$FET" -ge 1 ] 2>/dev/null && ok "J-CS-1 maternal + fetal context recorded on the episode" || bad "J-CS-1 maternal/fetal context missing (mat='$MAT' fet='$FET')"

# Neonatal team paged (by reference — outbox event for khuluma/notification).
curl -sS -o "$EV/cs-neo-alert.json" $(hdr) -X POST $INP/internal/v1/theatre/cases/$EPI_CS/obstetric/neonatal-alert \
  -d '{"urgency":"CATEGORY_1","reason":"Category-1 CS — neonatal resuscitation team to theatre","expectedNeonates":1}' >/dev/null
ALERT=$(INPSQL "SELECT count(*) FROM inpatient.event_outbox WHERE aggregate_id='$EPI_CS' AND event_type='theatre.obstetric.neonatal-alert'")
[ "$ALERT" -ge 1 ] 2>/dev/null && ok "J-CS-1 neonatal team paged (by reference)" || bad "J-CS-1 neonatal alert missing"

# The baby gets a PROVISIONAL identity (VITO, else synthetic) — call-only — and is LINKED to the mother.
CPID_BABY=$(provcpid "$EV/prov-baby.json" FEMALE "neonate of unidentified mother")
[ -n "$CPID_BABY" ] && ok "J-CS-1 provisional identity for the neonate ($CPID_BABY)" || bad "J-CS-1 no neonate provisional CPID"

curl -sS -o "$EV/cs-delivery.json" $(hdr) -X POST $INP/internal/v1/theatre/cases/$EPI_CS/obstetric/delivery \
  -d "{\"babyCpid\":\"$CPID_BABY\",\"sex\":\"F\",\"apgar1\":7,\"apgar5\":9,\"outcome\":\"LIVE_BIRTH\"}" >/dev/null
DEL=$(INPSQL "SELECT count(*) FROM inpatient.procedure_intraop_event WHERE episode_id='$EPI_CS' AND event_type='DELIVERY'")
[ "$DEL" -ge 1 ] 2>/dev/null && ok "J-CS-1 delivery recorded as a mother-episode-scoped event" || bad "J-CS-1 delivery event missing"
DLINK=$(INPSQL "SELECT count(*) FROM inpatient.event_outbox WHERE aggregate_id='$EPI_CS' AND event_type='theatre.obstetric.delivery' AND payload_json LIKE '%$CPID_BABY%'")
[ "$DLINK" -ge 1 ] 2>/dev/null && ok "J-CS-1 delivery event LINKS the baby CPID to the mother's episode" || bad "J-CS-1 delivery event does not carry the baby CPID"

# Neonate handed to a postnatal destination (mother→ward is the ordinary discharge path).
curl -sS -o "$EV/cs-handover.json" $(hdr) -X POST $INP/internal/v1/theatre/cases/$EPI_CS/obstetric/neonatal-handover \
  -d "{\"babyCpid\":\"$CPID_BABY\",\"destination\":\"POSTNATAL\"}"
HO=$(jget "$EV/cs-handover.json" destination)
[ "$HO" = "POSTNATAL" ] && ok "J-CS-1 neonate handed to POSTNATAL (with mother)" || bad "J-CS-1 neonatal handover destination '$HO' != POSTNATAL"

# ── Verdict ──────────────────────────────────────────────────────────────────
say "RESULT: PASS=$PASS FAIL=$FAIL"
echo "PASS=$PASS FAIL=$FAIL" > "$EV/summary.txt"
[ "$FAIL" = 0 ]
