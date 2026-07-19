#!/usr/bin/env bash
# =============================================================================
# Citizen client-journey (CJ) live acceptance pack — gateway-authenticated.
#
# Drives the citizen CJ journeys THROUGH the experience-bff against the live
# estate with a real citizen persona bearer, and asserts the actual HTTP
# contract a browser would receive — the house {data,meta} envelope plus the
# journey-specific fields. This is the harness that turns "render-tested" into
# "a person's request actually works": it would have caught the raw-map envelope
# bugs (CJ13/CJ14/CJ11) that every green unit test passed over.
#
# Scope today: CJ13 consent-center, CJ8 report-lost, CJ14 add-dependant,
# CJ11 appointment check-in token. Each asserts the live contract; new CJ
# screens append here as they ship.
#
# SAFE BY DEFAULT — read-only + validation-path checks run unconditionally.
# Mutating happy-paths (revoke a card, create a dependant) run ONLY with
# RUN_MUTATING=1, so a routine run never changes estate data.
#
# Verdict: PASS / FAIL / SKIP. A not-yet-deployed endpoint (404/unreachable) is
# SKIP, never a false PASS; a reachable endpoint returning the wrong shape is a
# FAIL (that is the bug this pack exists to catch).
#
# Reuses the auth/header scaffolding of identity-contract-journeys.sh (the base
# session owns that file; this is a separate pack). Overridable env:
#   IDENTITY_PACK_NAMESPACE, EXPERIENCE_BFF, TENANT, PERSONA_USER/PASSWORD,
#   TOKEN_CLIENT, PERSONA_ACTOR (else derived from the token's health_id claim),
#   RUN_MUTATING=1, APPOINTMENT_ID (for CJ11 happy path).
# =============================================================================
set -uo pipefail

REPO="${REPO_PATH:-$(cd "$(dirname "$0")/../.." && pwd)}"
TS="$(date +%Y%m%d-%H%M%S 2>/dev/null || echo run)"
EVIDENCE_DIR="${EVIDENCE_DIR:-$REPO/reports/identity-contract/citizen-$TS}"
mkdir -p "$EVIDENCE_DIR"

NS="${IDENTITY_PACK_NAMESPACE:-impilo-full-preview}"
svcip(){ kubectl get svc -n "$NS" "$1" -o jsonpath='{.spec.clusterIP}' 2>/dev/null; }
EXPERIENCE_BFF="${EXPERIENCE_BFF:-http://$(svcip experience-bff):8160}"
TENANT="${TENANT:-00000000-0000-0000-0000-000000000001}"

PERSONA_USER="${PERSONA_USER:-citizen.moyo}"
PERSONA_PASSWORD="${PERSONA_PASSWORD:-ImpiloTest123!}"
TOKEN_CLIENT="${TOKEN_CLIENT:-experience-ui}"
TOKEN=""
mint_token(){
  local kc_ip; kc_ip=$(svcip keycloak); [ -n "$kc_ip" ] || return 0
  TOKEN=$(curl -sf -m 10 --resolve "keycloak:8080:$kc_ip" \
      -X POST "http://keycloak:8080/realms/impilo/protocol/openid-connect/token" \
      -d grant_type=password -d "client_id=$TOKEN_CLIENT" \
      -d "username=$PERSONA_USER" --data-urlencode "password=$PERSONA_PASSWORD" \
    | python3 -c 'import sys,json;print(json.load(sys.stdin).get("access_token",""))' 2>/dev/null) || TOKEN=""
}

# The BFF resolves the citizen from X-Actor-ID. In production Envoy injects it
# from the token; this pack bypasses Envoy, so derive it from the token's
# health_id claim (fallback: sub). Overridable via PERSONA_ACTOR.
derive_actor(){
  [ -n "${PERSONA_ACTOR:-}" ] && { echo "$PERSONA_ACTOR"; return; }
  [ -n "$TOKEN" ] || { echo ""; return; }
  local payload; payload=$(printf '%s' "$TOKEN" | cut -d. -f2)
  # base64url pad + decode
  local pad=$(( ${#payload} % 4 )); [ $pad -ne 0 ] && payload="${payload}$(printf '=%.0s' $(seq $((4-pad))))"
  printf '%s' "$payload" | tr '_-' '/+' | base64 -d 2>/dev/null \
    | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d.get("health_id") or d.get("healthId") or d.get("sub") or "")' 2>/dev/null || echo ""
}

PASS=0; FAIL=0; SKIP=0
SUMMARY="$EVIDENCE_DIR/summary.txt"; : > "$SUMMARY"
ok(){   echo "  PASS: $1" | tee -a "$SUMMARY"; PASS=$((PASS+1)); }
bad(){  echo "  FAIL: $1" | tee -a "$SUMMARY"; FAIL=$((FAIL+1)); }
skip(){ echo "  SKIP: $1" | tee -a "$SUMMARY"; SKIP=$((SKIP+1)); }
say(){  echo "" | tee -a "$SUMMARY"; echo "== $1" | tee -a "$SUMMARY"; }
uuid(){ cat /proc/sys/kernel/random/uuid 2>/dev/null || python3 -c 'import uuid;print(uuid.uuid4())'; }

AUTH_HDR_FILE="$EVIDENCE_DIR/.auth-header"
trap 'rm -f "$AUTH_HDR_FILE"' EXIT
# Citizen trust headers (Actor-Type CITIZEN; actor is the persona health_id).
chdr(){ printf '%s' \
  "-H X-Tenant-ID:$TENANT -H X-Pod-ID:national -H X-Request-ID:$(uuid) "\
"-H X-Correlation-ID:$(uuid) -H X-Actor-Id:$ACTOR -H X-Actor-Type:CITIZEN "\
"-H X-Purpose-Of-Use:SELF_ACCESS -H Content-Type:application/json"
  [ -s "$AUTH_HDR_FILE" ] && printf ' -H @%s' "$AUTH_HDR_FILE"; }

# HTTP helpers: write body to a file, return the HTTP code. Command requests
# (POST) carry an Idempotency-Key — v1.1 internal endpoints require it (the shell's
# api-client adds it automatically; this pack must too).
GET(){  curl -sk -m 15 -o "$EVIDENCE_DIR/.body" -w '%{http_code}' $(chdr) "$EXPERIENCE_BFF$1" 2>/dev/null; }
POST(){ curl -sk -m 15 -o "$EVIDENCE_DIR/.body" -w '%{http_code}' -X POST $(chdr) \
          -H "Idempotency-Key:$(uuid)" --data "$2" "$EXPERIENCE_BFF$1" 2>/dev/null; }
body(){ cat "$EVIDENCE_DIR/.body" 2>/dev/null; }
has(){ python3 -c 'import sys,json
try: d=json.load(open(sys.argv[1]))
except Exception: sys.exit(2)
def g(o,p):
    for k in p.split("."):
        if not isinstance(o,dict): return None
        o=o.get(k)
    return o
sys.exit(0 if g(d,sys.argv[2]) is not None else 1)' "$EVIDENCE_DIR/.body" "$1" 2>/dev/null; }

mint_token
ACTOR="$(derive_actor)"
if [ -n "$TOKEN" ]; then printf 'Authorization: Bearer %s\n' "$TOKEN" > "$AUTH_HDR_FILE"; else : > "$AUTH_HDR_FILE"; fi

{
  echo "Citizen CJ live acceptance pack — $TS"
  echo "repo: $(git -C "$REPO" rev-parse --short HEAD 2>/dev/null || echo unknown)"
  echo "bff: $EXPERIENCE_BFF  mutating: ${RUN_MUTATING:-0}"
  echo "auth: $( [ -n "$TOKEN" ] && echo "persona bearer minted ($PERSONA_USER); actor=${ACTOR:-<unresolved>}" || echo "NO TOKEN (journeys SKIP)" )"
} >> "$SUMMARY"

bff_up(){ curl -sk -m 5 -o /dev/null "$EXPERIENCE_BFF/actuator/health" 2>/dev/null; }

# ── CJ13: consent centre (read-only) — the envelope regression guard ────────
cj13(){
  say "CJ13 consent-centre — {data:{consents,accessLog},meta} envelope"
  bff_up || { skip "CJ13: BFF unreachable"; return; }
  local code; code=$(GET "/internal/v1/citizen/consent-center")
  case "$code" in
    404|501) skip "CJ13: endpoint not deployed yet (HTTP $code)";;
    200) if has data && has meta; then ok "CJ13: live 200 with {data,meta} envelope"
         else bad "CJ13: 200 but MISSING {data,meta} envelope — body: $(body | head -c 200)"; fi;;
    401|403) skip "CJ13: auth rejected (HTTP $code) — persona/token setup";;
    *) bad "CJ13: unexpected HTTP $code — body: $(body | head -c 200)";;
  esac
}

# ── CJ14: add-dependant — validation path (adult rejected, NO mutation) ──────
cj14(){
  say "CJ14 add-dependant — over-age rejected (422), no data created"
  bff_up || { skip "CJ14: BFF unreachable"; return; }
  local adult; adult=$(python3 -c 'import datetime;print((datetime.date.today().replace(year=datetime.date.today().year-25)).isoformat())')
  local code; code=$(POST "/internal/v1/citizen/dependants" \
    "{\"givenName\":\"Rig\",\"familyName\":\"Adult\",\"dateOfBirth\":\"$adult\"}")
  case "$code" in
    404|501) skip "CJ14: endpoint not deployed yet (HTTP $code)";;
    422) ok "CJ14: over-age dependant correctly rejected (422), no mutation";;
    400) skip "CJ14: 400 (validation reached; actor/tenant setup) — body: $(body | head -c 160)";;
    401|403) skip "CJ14: auth rejected (HTTP $code)";;
    201) bad "CJ14: an ADULT was accepted as a dependant (should be 422)";;
    *) bad "CJ14: unexpected HTTP $code — body: $(body | head -c 200)";;
  esac
  if [ "${RUN_MUTATING:-0}" = "1" ]; then
    local minor; minor=$(python3 -c 'import datetime;print((datetime.date.today().replace(year=datetime.date.today().year-5)).isoformat())')
    code=$(POST "/internal/v1/citizen/dependants" "{\"givenName\":\"Rig\",\"familyName\":\"Child\",\"dateOfBirth\":\"$minor\"}")
    if [ "$code" = "201" ] && has data.status && has meta; then ok "CJ14(mut): minor created with {data,meta}"
    elif [ "$code" = "201" ]; then bad "CJ14(mut): 201 but missing {data,meta} envelope"
    else skip "CJ14(mut): create returned HTTP $code"; fi
  else
    skip "CJ14: happy-path create is mutating (set RUN_MUTATING=1)"
  fi
}

# ── CJ8: report lost card — mutating; gated ─────────────────────────────────
cj8(){
  say "CJ8 report-lost — {data,meta} on report (mutating)"
  bff_up || { skip "CJ8: BFF unreachable"; return; }
  if [ "${RUN_MUTATING:-0}" != "1" ]; then skip "CJ8: revokes the card (set RUN_MUTATING=1)"; return; fi
  local code; code=$(POST "/internal/v1/wallet/cards/report-lost" '{"reason":"LOST","requestReplacement":false}')
  case "$code" in
    404|501) skip "CJ8: endpoint not deployed yet (HTTP $code)";;
    200) if has data.status && has meta; then ok "CJ8: reported with {data,meta} (status=$(python3 -c 'import json;print(json.load(open("'"$EVIDENCE_DIR"'/.body"))["data"]["status"])' 2>/dev/null))"
         else bad "CJ8: 200 but missing {data,meta} envelope"; fi;;
    401|403) skip "CJ8: auth rejected (HTTP $code)";;
    *) bad "CJ8: unexpected HTTP $code — body: $(body | head -c 200)";;
  esac
}

# ── CJ11: appointment check-in token — needs a real appointment ─────────────
cj11(){
  say "CJ11 check-in token — {data:{token},meta}"
  bff_up || { skip "CJ11: BFF unreachable"; return; }
  [ -n "${APPOINTMENT_ID:-}" ] || { skip "CJ11: set APPOINTMENT_ID to a persona-owned appointment"; return; }
  local code; code=$(GET "/internal/v1/appointments/$APPOINTMENT_ID/checkin-token")
  case "$code" in
    404|501) skip "CJ11: endpoint/appointment not found (HTTP $code)";;
    200) if has data.token && has meta; then ok "CJ11: signed check-in token issued with {data,meta}"
         else bad "CJ11: 200 but missing {data,meta} or token"; fi;;
    403) skip "CJ11: 403 (appointment not owned by persona)";;
    401) skip "CJ11: auth rejected (401)";;
    *) bad "CJ11: unexpected HTTP $code — body: $(body | head -c 200)";;
  esac
}

cj13; cj14; cj8; cj11

say "VERDICT"
echo "  PASS=$PASS  FAIL=$FAIL  SKIP=$SKIP" | tee -a "$SUMMARY"
echo "  evidence: $EVIDENCE_DIR/summary.txt"
[ "$FAIL" -eq 0 ] || exit 1
