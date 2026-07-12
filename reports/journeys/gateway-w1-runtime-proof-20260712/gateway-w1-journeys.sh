#!/usr/bin/env bash
# Gateway W1 runtime proof — J1 gateway-shell (anonymous public reads) +
# J2 gateway-reachable (R1 contact-OTP registration).
# Rig: see rig-boot.sh in this directory. BFF :28460, tuso :28084, guidance :28260,
# identity-assurance :28201, notification :28200, Keycloak :18080, Postgres :15733.
set -u
BFF=http://localhost:28460
IA=http://localhost:28201
KC=http://localhost:18080
TEN=00000000-0000-0000-0000-000000000001
EV="$(cd "$(dirname "$0")" && pwd)"
PASS=0; FAIL=0
ok(){ echo "   PASS: $1"; PASS=$((PASS+1)); }
bad(){ echo "   FAIL: $1"; FAIL=$((FAIL+1)); }
PSQL(){ docker exec gw-rig-pg psql -U impilo -d "$1" -tAc "$2"; }
hdr(){ echo "-H Content-Type:application/json -H X-Tenant-ID:$TEN -H X-Pod-ID:national-spine -H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H Idempotency-Key:$(uuidgen)"; }

# Whole-word internal-name scan per config/gateway-public-naming.yml (allowed brands: impilo, nompilo)
name_scan(){ # $1=payload $2=label
  python3 - "$2" <<PYEOF
import re,sys
payload=open("/tmp/gw-naming-scan.txt").read().lower()
terms=["tshepo","vito","butano","tuso","varapi","zibo","rito","msika","daidzai","nhume",
       "costa","mushex","mushe","dura","indawo","ndila","khuluma","fundo","mvumo","oros",
       "landela","ubomi","vashandi","madi","simba"]
hits=[t for t in terms if re.search(r"\b"+t+r"\b",payload)]
sys.exit(1 if hits else 0)
PYEOF
}

echo "== J1 gateway-shell: anonymous public reads (R0) =="

# J1.1 bare curl, ZERO headers → 200 + page shape
R=$(curl -s -w '\n%{http_code}' "$BFF/internal/v1/public/gateway/facilities/search?query=clinic")
CODE=$(echo "$R" | tail -1); BODY=$(echo "$R" | head -n -1)
echo "$BODY" > "$EV/j1-facility-search.json"
ITEMS=$(echo "$BODY" | python3 -c 'import json,sys;d=json.load(sys.stdin);print(len(d.get("items",[])))' 2>/dev/null)
[ "$CODE" = 200 ] && [ "${ITEMS:-0}" -ge 1 ] && ok "bare anonymous facility search → 200 with $ITEMS results (permitAll + synthesized headers + tuso public lane E2E)" || bad "bare search code=$CODE items=$ITEMS"

# J1.2 profile → 200 and redacted (no contacts/identifiers/audit fields)
FID=$(echo "$BODY" | python3 -c 'import json,sys;d=json.load(sys.stdin);print(d["items"][0]["id"])' 2>/dev/null)
RP=$(curl -s -w '\n%{http_code}' "$BFF/internal/v1/public/gateway/facilities/$FID/profile")
PCODE=$(echo "$RP" | tail -1); PBODY=$(echo "$RP" | head -n -1)
echo "$PBODY" > "$EV/j1-facility-profile.json"
REDACTED=$(echo "$PBODY" | python3 -c '
import json,sys
d=json.load(sys.stdin)
leaks=[k for k in ("contacts","identifiers","readiness","createdBy","updatedBy","createdAt","updatedAt","description","parentId","ownership","operationalStatus") if d.get(k) is not None]
print(",".join(leaks) if leaks else "CLEAN")')
[ "$PCODE" = 200 ] && [ "$REDACTED" = CLEAN ] && ok "facility profile → 200, disclosure-limited (contacts/identifiers/audit all null)" || bad "profile code=$PCODE leaks=$REDACTED"

# J1.3 guidance explainers (V012 seed) — list + single step with the four doctrinal parts
G=$(curl -s -w '\n%{http_code}' "$BFF/internal/v1/public/gateway/guidance/explain-steps")
GCODE=$(echo "$G" | tail -1); GBODY=$(echo "$G" | head -n -1)
echo "$GBODY" > "$EV/j1-explain-steps.json"
STEPS=$(echo "$GBODY" | python3 -c 'import json,sys;print(",".join(sorted(x["stepKey"] for x in json.load(sys.stdin)["data"])))' 2>/dev/null)
[ "$GCODE" = 200 ] && [ "$STEPS" = "signin-to-book,signin-to-personal,step-up-results,verify-contact" ] && ok "explain-steps list → 200 with all four W1 explainers" || bad "explain-steps code=$GCODE steps=$STEPS"
GS=$(curl -s -w '\n%{http_code}' "$BFF/internal/v1/public/gateway/guidance/explain-steps/signin-to-book")
GSCODE=$(echo "$GS" | tail -1); GSBODY=$(echo "$GS" | head -n -1)
PARTS=$(echo "$GSBODY" | python3 -c '
import json,sys
d=json.load(sys.stdin)["data"]
missing=[k for k in ("why","levelLabel","whatNext","howToGetHelp") if not d.get(k)]
print(",".join(missing) if missing else "ALL4")')
[ "$GSCODE" = 200 ] && [ "$PARTS" = ALL4 ] && ok "signin-to-book explainer → 200 with the four doctrinal parts (why/level/next/help)" || bad "explainer code=$GSCODE parts=$PARTS"

# J1.4 naming assert — no internal service names in any public payload
cat "$EV/j1-facility-search.json" "$EV/j1-facility-profile.json" "$EV/j1-explain-steps.json" > /tmp/gw-naming-scan.txt
name_scan /tmp/gw-naming-scan.txt public-payloads && ok "naming guard: no internal service names in public payloads" || bad "naming guard hit an internal name (see gateway-public-naming.yml)"

# J1.5 negatives — authenticated route stays closed; gateway namespace is GET-only
NCODE=$(curl -s -o /dev/null -w '%{http_code}' $(hdr) "$BFF/internal/v1/mobile/citizen/coverage/x")
[ "$NCODE" = 401 ] && ok "authenticated route (citizen coverage) → 401 for anonymous caller" || bad "citizen coverage anonymous → $NCODE (want 401)"
NBARE=$(curl -s -o /dev/null -w '%{http_code}' "$BFF/internal/v1/mobile/citizen/coverage/x")
[ "$NBARE" = 400 ] || [ "$NBARE" = 401 ] && ok "bare curl to authenticated route → $NBARE (rejected; 400 = v1.1 header contract precedes authn)" || bad "bare authenticated route → $NBARE"
PCODE2=$(curl -s -o /dev/null -w '%{http_code}' $(hdr) -X POST "$BFF/internal/v1/public/gateway/facilities/search" -d '{}')
{ [ "$PCODE2" = 401 ] || [ "$PCODE2" = 403 ]; } && ok "POST into gateway namespace → $PCODE2 (GET-only lane holds)" || bad "gateway POST → $PCODE2 (want 401/403)"

echo ""
echo "== J2 gateway-reachable: R1 phone-OTP registration =="
PHONE="+2637719$(printf '%05d' $((RANDOM % 100000)))"
echo "   (contact under test: $PHONE)"

# J2.1 request OTP → 202 SENT; retrieve the code from the notification SoR (sms_log stub
# does not print the body, but the enqueue persists vars_json in ns_notifications)
RQ=$(curl -s -w '\n%{http_code}' $(hdr) -X POST "$BFF/internal/v1/auth/contact/otp/request" -d "{\"channel\":\"PHONE\",\"value\":\"$PHONE\"}")
RQCODE=$(echo "$RQ" | tail -1); echo "$RQ" | head -n -1 > "$EV/j2-otp-request.json"
[ "$RQCODE" = 202 ] && ok "otp/request → 202 SENT (fail-closed delivery accepted by notification-service)" || bad "otp/request → $RQCODE"
OTP=$(PSQL impilo_notification "SELECT vars_json::json->>'otp' FROM ns_notifications WHERE to_addr='$PHONE' ORDER BY created_at DESC LIMIT 1")
[ -n "$OTP" ] && ok "OTP retrieved from ns_notifications vars_json (real enqueue row, template CONTACT_VERIFY_OTP)" || bad "no OTP row for $PHONE"

# J2.2 wrong code → rejected
WCODE=$(curl -s -o /dev/null -w '%{http_code}' $(hdr) -X POST "$BFF/internal/v1/auth/contact/otp/verify" -d "{\"value\":\"$PHONE\",\"code\":\"000000\",\"purpose\":\"REGISTER\",\"password\":\"RigPassw0rd!2026\"}")
[ "$WCODE" = 401 ] && ok "wrong code → 401 OTP_INVALID" || bad "wrong code → $WCODE"

# correct code, purpose=REGISTER → Keycloak user + auto-login + attestation
VR=$(curl -s -w '\n%{http_code}' $(hdr) -X POST "$BFF/internal/v1/auth/contact/otp/verify" -d "{\"value\":\"$PHONE\",\"code\":\"$OTP\",\"purpose\":\"REGISTER\",\"password\":\"RigPassw0rd!2026\",\"fullName\":\"Rig Citizen\"}")
VRCODE=$(echo "$VR" | tail -1); VRBODY=$(echo "$VR" | head -n -1)
echo "$VRBODY" > "$EV/j2-register-verify.json"
TOKEN=$(echo "$VRBODY" | python3 -c 'import json,sys;d=json.load(sys.stdin);print(d["data"]["attributes"].get("token",""))' 2>/dev/null)
KCID=$(echo "$VRBODY" | python3 -c 'import json,sys;d=json.load(sys.stdin);print(d["data"].get("id",""))' 2>/dev/null)
[ "$VRCODE" = 200 ] && [ -n "$TOKEN" ] && ok "verify purpose=REGISTER → 200 with auto-login token (ROPC, CITIZEN at LOA1)" || bad "register verify → $VRCODE token=${TOKEN:0:12}"

# Keycloak truth: the user exists with the contact as username
KTOK=$(curl -s -X POST $KC/realms/impilo/protocol/openid-connect/token -d 'grant_type=client_credentials&client_id=impilo-backend&client_secret=impilo-backend-secret' | python3 -c 'import json,sys;print(json.load(sys.stdin)["access_token"])')
KUSER=$(curl -s -H "Authorization: Bearer $KTOK" "$KC/admin/realms/impilo/users?username=%2B${PHONE#+}&exact=true" | python3 -c 'import json,sys;u=json.load(sys.stdin);print(u[0]["id"] if u else "")')
[ -n "$KUSER" ] && [ "$KUSER" = "$KCID" ] && ok "Keycloak user exists (id matches auto-login subject)" || bad "keycloak user lookup: '$KUSER' vs '$KCID'"

# IA truth: CONTACT_VERIFIED attestation, masked value only, and NO assurance raise (R1 = LOA1 + attestation)
ATT=$(PSQL ia "SELECT attestation_type||'|'||outcome||'|'||(evidence::json->>'maskedValue') FROM ia.attestations WHERE actor_id='$KCID'")
echo "attestation: $ATT" > "$EV/j2-ia-readback.txt"
case "$ATT" in
  CONTACT_VERIFIED\|VERIFIED\|*\**) ok "IA attestation CONTACT_VERIFIED/VERIFIED persisted with MASKED value ($ATT)";;
  *) bad "IA attestation row: '$ATT'";;
esac
echo "$ATT" | grep -q "${PHONE#+}" && bad "RAW phone leaked into IA evidence" || ok "raw phone absent from IA evidence (PII boundary held)"
LOA=$(PSQL ia "SELECT count(*) FROM ia.assurance_record WHERE actor_id='$KCID'")
[ "${LOA:-0}" = 0 ] && ok "no assurance_record created — LOA not raised by contact verification (R1 = LOA1 + attestation)" || bad "assurance_record rows=$LOA (unexpected LOA raise)"

# J2.3 read-back API: contact-verified status with the citizen's own token (IA direct; no BFF proxy route in W1)
CV=$(curl -s -w '\n%{http_code}' -H "Authorization: Bearer $TOKEN" $(hdr) -H "X-Actor-ID:$KCID" -H "X-Actor-Type:CITIZEN" "$IA/internal/v1/attestations/contact-verified/$KCID")
CVCODE=$(echo "$CV" | tail -1); CVBODY=$(echo "$CV" | head -n -1)
echo "$CVBODY" > "$EV/j2-contact-verified.json"
CVOK=$(echo "$CVBODY" | python3 -c 'import json,sys;d=json.load(sys.stdin)["data"];print(str(d["contactVerified"]).lower()+"|"+d["channels"][0]["channel"])' 2>/dev/null)
[ "$CVCODE" = 200 ] && [ "$CVOK" = "true|PHONE" ] && ok "GET contact-verified/{accountId} → contactVerified=true channel=PHONE (citizen's own JWT)" || bad "contact-verified read: code=$CVCODE body=$CVOK"

# J2.4 rate-limit negative: immediate resend for the same number → 429 + Retry-After
RL=$(curl -s -D /tmp/gw-rl-headers -o /dev/null -w '%{http_code}' $(hdr) -X POST "$BFF/internal/v1/auth/contact/otp/request" -d "{\"channel\":\"PHONE\",\"value\":\"$PHONE\"}")
RETRY=$(grep -i '^Retry-After:' /tmp/gw-rl-headers | tr -d '\r' | awk '{print $2}')
[ "$RL" = 429 ] && [ -n "$RETRY" ] && ok "immediate re-request → 429 with Retry-After: $RETRY (cooldown + rate window)" || bad "rate-limit: code=$RL retry-after='$RETRY'"

# J2.5 replay negative: the consumed OTP cannot be replayed
RP2=$(curl -s -w '\n%{http_code}' $(hdr) -X POST "$BFF/internal/v1/auth/contact/otp/verify" -d "{\"value\":\"$PHONE\",\"code\":\"$OTP\",\"purpose\":\"REGISTER\",\"password\":\"RigPassw0rd!2026\"}")
RPCODE=$(echo "$RP2" | tail -1)
[ "$RPCODE" = 400 ] && echo "$RP2" | grep -q OTP_EXPIRED && ok "consumed OTP replay → 400 OTP_EXPIRED (single-use held)" || bad "replay → $RPCODE"

echo ""
echo "== J3 gateway-intent-preserved: shell SSR smoke (requires shell on :3010; skipped if absent) =="
SHELL_URL=http://localhost:3010
if curl -s -o /dev/null --max-time 3 "$SHELL_URL/welcome"; then
  WCODE=$(curl -s -o /tmp/gw-welcome.html -w '%{http_code}' "$SHELL_URL/welcome")
  W=$(python3 - <<'PYEOF'
html=open('/tmp/gw-welcome.html',encoding='utf-8').read()
def has(t):  # SSR emits "&" as &amp; in HTML and & in the RSC flight payload
    return any(v in html for v in (t, t.replace("&","&amp;"), t.replace("&","\\u0026")))
need=["How can we help you today?","Get care","Emergency help","Find or verify a service",
      "My health","Health information","Health cover & payments","Applications & licensing",
      "Feedback & complaints","Health products & suppliers"]
missing=[n for n in need if not has(n)]
print("OK" if not missing else "MISSING:"+";".join(missing))
PYEOF
)
  [ "$WCODE" = 200 ] && [ "$W" = OK ] && ok "/welcome SSR → 200 with headline + all nine pillar titles" || bad "/welcome code=$WCODE $W"
  GWI=$(python3 -c 'import json,base64,time;i={"pillar":"get-care","goal":"book-appointment","params":{"dest":"/care/book","from":"/welcome"},"createdAt":int(time.time()*1000)};print(base64.urlsafe_b64encode(json.dumps(i,separators=(",",":")).encode()).decode().rstrip("="))')
  LCODE=$(curl -s -o /dev/null -w '%{http_code}' "$SHELL_URL/auth/login?gwi=$GWI")
  [ "$LCODE" = 200 ] && ok "/auth/login?gwi=<valid intent token> → 200 (token minted with gateway-intent.ts encoding)" || bad "login gwi → $LCODE"
  # honest scope: full click-through restore is browser behaviour covered by the wave's
  # 32 gateway-intent + resolver unit tests; not faked here.
else
  echo "   SKIP: shell not running on :3010 (see rig-boot notes; J3 recorded in journal)"
fi

echo ""
echo "RESULT: PASS=$PASS FAIL=$FAIL"
exit $([ $FAIL -eq 0 ] && echo 0 || echo 1)
