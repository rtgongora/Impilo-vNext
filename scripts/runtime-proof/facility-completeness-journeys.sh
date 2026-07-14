#!/usr/bin/env bash
# ============================================================================
# Facility profile completeness prompt — runtime proof (J-FC-1..3).
#
# THE POINT: when a facility exists with missing governed fields (esp.
# geocodes), the completeness read model reports the exact gaps, and ONLY an
# ACTIVE facility administrator (or active PIC) can close them through the
# governed complete-profile flow — with per-field facility_history rows
# stamped source=COMPLETENESS_PROMPT + geocode provenance, a version bump,
# and a tuso.facility.updated outbox event. Everyone else is 403 fail-closed.
#
#   J-FC-1  facility seeded with NULL lat/lng/type/ownership + ACTIVE admin
#           appointment → GET completeness lists the missing fields →
#           POST complete-profile (DEVICE_GPS coords + type/ownership/address)
#           → complete=true, facility_history rows w/ COMPLETENESS_PROMPT +
#           geocodeProvenance, version bumped, event_outbox row w/
#           trigger=COMPLETENESS_PROMPT
#   J-FC-2  actor WITHOUT appointment/PIC → 403, zero new history rows,
#           facility untouched
#   J-FC-3  MANUAL low-accuracy geocode → history rows stamped
#           geocodeVerification=UNVERIFIED
#
# Requirements: docker, java 21, packaged jar for tuso-service
# Usage: EV=<dir> bash scripts/runtime-proof/facility-completeness-journeys.sh
#        KEEP_RIG=1 to leave infra + tuso running for inspection.
# ============================================================================
set -u
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
EV=${EV:-$REPO/reports/journeys/facility-completeness-proof-$(date +%Y-%m-%d)}
RIGLOG=${RIGLOG:-/tmp/facility-completeness-logs}
mkdir -p "$EV" "$RIGLOG"
TEN=00000000-0000-4000-8000-000000000001
PGPORT=15681; RPORT=16436; TUSO=http://localhost:28184
PASS=0; FAIL=0
ok(){ echo "   PASS: $1" | tee -a "$EV/journal.txt"; PASS=$((PASS+1)); }
bad(){ echo "   FAIL: $1" | tee -a "$EV/journal.txt"; FAIL=$((FAIL+1)); }
say(){ echo "== $1" | tee -a "$EV/journal.txt"; }
PSQL(){ docker exec fcomp-rig-pg psql -U impilo -d tuso -tAc "$1"; }
# trust headers — X-Actor-ID is the authz subject the completion flow evaluates
hdr(){ local a=${1:-rig-anon} r=${2:-PROVIDER}
  echo "-H Content-Type:application/json -H X-Tenant-ID:$TEN -H X-Pod-ID:national-spine -H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H X-Actor-ID:$a -H X-Actor-Type:$r -H X-Purpose-Of-Use:FACILITY_ADMINISTRATION"; }
jqd(){ python3 -c "import json,sys;r=json.load(sys.stdin);d=r.get('data') if isinstance(r,dict) and 'data' in r else r;print(json.dumps(d))"; }

command -v docker >/dev/null || { echo "docker not available — rig cannot run"; exit 2; }

# ── Infra ────────────────────────────────────────────────────────────────────
say "RIG: infra + tuso"
docker rm -f fcomp-rig-pg fcomp-rig-redis >/dev/null 2>&1
docker run -d --name fcomp-rig-pg -e POSTGRES_USER=impilo -e POSTGRES_PASSWORD=impilo -p $PGPORT:5432 postgres:16-alpine >/dev/null
docker run -d --name fcomp-rig-redis -p $RPORT:6379 redis:7-alpine >/dev/null
sleep 8
docker exec fcomp-rig-pg psql -U impilo -d postgres -c "CREATE DATABASE tuso" >/dev/null 2>&1

jar(){ ls "$REPO/services/tuso-service/target/tuso-service-"*.jar 2>/dev/null | grep -v original | head -1; }
[ -n "$(jar)" ] || { echo "tuso-service jar missing — run: mvn -f services/pom.xml -pl tuso-service -am package -DskipTests"; exit 2; }
env POSTGRES_HOST=localhost POSTGRES_PORT=$PGPORT POSTGRES_USER=impilo POSTGRES_PASSWORD=impilo POSTGRES_DB=tuso \
  IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true SERVER_PORT=28184 SPRING_KAFKA_LISTENER_AUTO_STARTUP=false \
  SPRING_DATA_REDIS_HOST=localhost SPRING_DATA_REDIS_PORT=$RPORT \
  nohup java -jar "$(jar)" > "$RIGLOG/tuso.log" 2>&1 & echo $! > "$RIGLOG/tuso.pid"

c=000
for i in $(seq 1 60); do c=$(curl -s -o /dev/null -w '%{http_code}' $TUSO/actuator/health 2>/dev/null); [ "$c" = 200 ] && break; sleep 3; done
echo "   tuso health: $c" | tee -a "$EV/journal.txt"
[ "$c" = 200 ] && ok "tuso booted" || { bad "tuso did not boot"; tail -40 "$RIGLOG/tuso.log"; exit 1; }

cleanup(){ if [ -z "${KEEP_RIG:-}" ]; then kill "$(cat "$RIGLOG/tuso.pid")" >/dev/null 2>&1; docker rm -f fcomp-rig-pg fcomp-rig-redis >/dev/null 2>&1; fi }
trap cleanup EXIT

seed_facility(){ # $1 code $2 name — echoes facility id; strips governed fields via SQL
  local id
  id=$(curl -sS $(hdr rig-registrar ADMIN) -X POST $TUSO/v1/internal/facilities \
    -d "{\"name\":\"$2\",\"facilityCode\":\"$1\",\"province\":\"Harare\",\"district\":\"Harare\"}" \
    | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['id'])")
  PSQL "UPDATE tuso.facility SET latitude=NULL, longitude=NULL, facility_type=NULL, ownership=NULL WHERE id=$id" >/dev/null
  echo "$id"
}
grant_admin(){ # $1 facility id $2 actor — ACTIVE FacilityAdminAppointment on the canonical UUID
  local fuuid
  fuuid=$(PSQL "SELECT facility_uuid FROM tuso.facility WHERE id=$1")
  PSQL "INSERT INTO tuso.facility_admin_appointment (facility_uuid, person_health_id, role, approval_state, appointed_by, created_at, updated_at) VALUES ('$fuuid', '$2', 'FACILITY_ADMINISTRATOR', 'ACTIVE', 'rig', now(), now())" >/dev/null
}

# ═════ J-FC-1: missing fields surfaced → governed completion by ACTIVE admin ═
say "J-FC-1: seed facility w/ missing governed fields + ACTIVE admin appointment"
F1=$(seed_facility "RIG-FC-001" "Rig Completeness Clinic")
[ -n "$F1" ] && ok "facility seeded (id=$F1, lat/lng/type/ownership NULL)" || { bad "facility seed failed"; exit 1; }
grant_admin "$F1" "rig-admin-1"
[ "$(PSQL "SELECT count(*) FROM tuso.facility_admin_appointment WHERE approval_state='ACTIVE'")" = 1 ] \
  && ok "ACTIVE admin appointment seeded (rig-admin-1)" || bad "appointment seed failed"

COMP=$(curl -sS $(hdr rig-admin-1) $TUSO/v1/internal/facilities/$F1/completeness | jqd)
echo "   completeness(before): $COMP" >> "$EV/journal.txt"
if echo "$COMP" | grep -Eq '"complete": ?false'; then ok "completeness reports complete=false"; else bad "expected complete=false, got: $COMP"; fi
for f in missing_latitude missing_longitude missing_facility_type missing_ownership missing_address_line1; do
  echo "$COMP" | grep -q "$f" && ok "missing field surfaced: $f" || bad "missing field NOT surfaced: $f"
done
if echo "$COMP" | grep -Eq '"geocodeStatus": ?"MISSING"'; then ok "geocodeStatus=MISSING"; else bad "geocodeStatus not MISSING: $COMP"; fi

VER0=$(PSQL "SELECT version FROM tuso.facility WHERE id=$F1")
BODY='{"facilityType":"CLINIC","ownership":"GOVERNMENT","addressLine1":"12 Chaminuka Rd","city":"Harare","ward":"Ward 7","latitude":-17.8292,"longitude":31.0522,"geocodeProvenance":{"source":"DEVICE_GPS","accuracyMeters":8}}'
HTTP=$(curl -sS -o "$EV/j1-complete.json" -w '%{http_code}' $(hdr rig-admin-1) -X POST $TUSO/v1/internal/facilities/$F1/complete-profile -d "$BODY")
[ "$HTTP" = 200 ] && ok "complete-profile accepted for ACTIVE admin (200)" || bad "complete-profile HTTP $HTTP: $(cat "$EV/j1-complete.json")"
if grep -Eq '"complete": ?true' "$EV/j1-complete.json"; then ok "response verdict complete=true"; else bad "verdict not complete: $(cat "$EV/j1-complete.json")"; fi

H1=$(PSQL "SELECT count(*) FROM tuso.facility_history WHERE facility_id=$F1 AND reason LIKE '%COMPLETENESS_PROMPT%'")
[ "$H1" -ge 7 ] && ok "per-field history rows recorded ($H1 w/ source=COMPLETENESS_PROMPT)" || bad "history rows: $H1 (expected >=7)"
HP=$(PSQL "SELECT count(*) FROM tuso.facility_history WHERE facility_id=$F1 AND field_name IN ('latitude','longitude') AND reason LIKE '%geocodeProvenance%' AND reason LIKE '%DEVICE_GPS%'")
[ "$HP" = 2 ] && ok "geocode history rows carry DEVICE_GPS provenance" || bad "geocode provenance rows: $HP (expected 2)"
HU=$(PSQL "SELECT count(*) FROM tuso.facility_history WHERE facility_id=$F1 AND reason LIKE '%UNVERIFIED%'")
[ "$HU" = 0 ] && ok "high-accuracy device capture NOT stamped UNVERIFIED" || bad "unexpected UNVERIFIED stamps: $HU"
VER1=$(PSQL "SELECT version FROM tuso.facility WHERE id=$F1")
[ "$VER1" = "$((VER0+1))" ] && ok "version bumped $VER0 -> $VER1" || bad "version: $VER0 -> $VER1"
OB=$(PSQL "SELECT count(*) FROM tuso.event_outbox WHERE aggregate_id='$F1' AND event_type='tuso.facility.updated' AND payload->>'trigger'='COMPLETENESS_PROMPT'")
[ "$OB" = 1 ] && ok "tuso.facility.updated outbox row w/ trigger=COMPLETENESS_PROMPT" || bad "outbox rows: $OB (expected 1)"
GEO=$(PSQL "SELECT count(*) FROM tuso.facility_geo WHERE facility_id=$F1 AND address_line1='12 Chaminuka Rd' AND ward='Ward 7'")
[ "$GEO" = 1 ] && ok "facility_geo upserted with address basics" || bad "facility_geo rows: $GEO"
COMP2=$(curl -sS $(hdr rig-admin-1) $TUSO/v1/internal/facilities/$F1/completeness | jqd)
if echo "$COMP2" | grep -Eq '"complete": ?true'; then ok "completeness re-read reports complete=true"; else bad "re-read: $COMP2"; fi

# ═════ J-FC-2: stranger is 403 fail-closed, nothing written ══════════════════
say "J-FC-2: actor without appointment/PIC -> 403, no writes"
F2=$(seed_facility "RIG-FC-002" "Rig Stranger Clinic")
H2_BEFORE=$(PSQL "SELECT count(*) FROM tuso.facility_history WHERE facility_id=$F2")
HTTP=$(curl -sS -o "$EV/j2-forbidden.json" -w '%{http_code}' $(hdr rig-stranger-9) -X POST $TUSO/v1/internal/facilities/$F2/complete-profile -d "$BODY")
[ "$HTTP" = 403 ] && ok "stranger rejected with 403" || bad "expected 403, got $HTTP: $(cat "$EV/j2-forbidden.json")"
H2_AFTER=$(PSQL "SELECT count(*) FROM tuso.facility_history WHERE facility_id=$F2")
[ "$H2_BEFORE" = "$H2_AFTER" ] && ok "no history rows written on 403" || bad "history leaked: $H2_BEFORE -> $H2_AFTER"
LAT2=$(PSQL "SELECT latitude FROM tuso.facility WHERE id=$F2")
[ -z "$LAT2" ] && ok "facility untouched (latitude still NULL)" || bad "facility mutated by stranger: lat=$LAT2"

# ═════ J-FC-3: manual low-accuracy geocode stamped UNVERIFIED ════════════════
say "J-FC-3: MANUAL low-accuracy geocode -> UNVERIFIED stamp"
F3=$(seed_facility "RIG-FC-003" "Rig Manual Geo Clinic")
grant_admin "$F3" "rig-admin-3"
BODY3='{"latitude":-18.9707,"longitude":32.6709,"geocodeProvenance":{"source":"MANUAL","accuracyMeters":500}}'
HTTP=$(curl -sS -o "$EV/j3-manual.json" -w '%{http_code}' $(hdr rig-admin-3) -X POST $TUSO/v1/internal/facilities/$F3/complete-profile -d "$BODY3")
[ "$HTTP" = 200 ] && ok "manual geocode accepted (200)" || bad "manual geocode HTTP $HTTP: $(cat "$EV/j3-manual.json")"
HU3=$(PSQL "SELECT count(*) FROM tuso.facility_history WHERE facility_id=$F3 AND field_name IN ('latitude','longitude') AND reason LIKE '%\"geocodeVerification\":\"UNVERIFIED\"%'")
[ "$HU3" = 2 ] && ok "both geocode history rows stamped geocodeVerification=UNVERIFIED" || bad "UNVERIFIED rows: $HU3 (expected 2)"
COMP3=$(curl -sS $(hdr rig-admin-3) $TUSO/v1/internal/facilities/$F3/completeness | jqd)
if echo "$COMP3" | grep -Eq '"geocodeStatus": ?"PRESENT"'; then ok "geocodeStatus=PRESENT after manual capture (verification is history metadata, not a blocker)"; else bad "geocodeStatus after manual: $COMP3"; fi

# ═════ Verdict ═══════════════════════════════════════════════════════════════
say "VERDICT: $PASS passed, $FAIL failed (evidence: $EV/journal.txt)"
[ "$FAIL" = 0 ] || exit 1
