#!/usr/bin/env bash
# ============================================================================
# Khuluma — Wave-1 E2E smoke (brief §E2E), runnable against a live stack.
#
# Drives the acceptance journey over HTTP against khuluma-service:
#   A↔B converse → read receipt → 1:1 call ring → accept → signal → end →
#   membership/policy deny → (audit asserted by the automated KhulumaWave1E2ETest).
#
# Usage:  scripts/e2e/khuluma-wave1-smoke.sh [BASE_URL]
#   BASE_URL defaults to http://localhost:8390 (khuluma-service internal API).
#   Through the BFF instead: pass http://localhost:8160 and the BFF will inject
#   the trust headers (this script sets them directly for the service path).
#
# Requires: curl, python3 (for JSON field extraction). Exits non-zero on failure.
# ============================================================================
set -euo pipefail

BASE="${1:-http://localhost:8390}"
PREFIX="/internal/v1/khuluma"
TENANT="$(python3 -c 'import uuid;print(uuid.uuid4())')"
A="provider-a"; B="provider-b"; X="provider-x"
PASS=0; FAIL=0

jq_field() { python3 -c 'import sys,json;print(json.load(sys.stdin)'"$1"')'; }

# call <method> <actor> <path> <expected-status> [body]  -> echoes body, checks status
call() {
  local method="$1" actor="$2" path="$3" want="$4" body="${5:-}"
  local args=(-s -o /tmp/khuluma_body -w '%{http_code}' -X "$method" "$BASE$PREFIX$path"
    -H "X-Tenant-ID: $TENANT" -H "X-Pod-ID: national"
    -H "X-Request-ID: $(python3 -c 'import uuid;print(uuid.uuid4())')"
    -H "X-Correlation-ID: $(python3 -c 'import uuid;print(uuid.uuid4())')"
    -H "X-Actor-ID: $actor" -H "X-Actor-Type: PROVIDER"
    -H "Idempotency-Key: $(python3 -c 'import uuid;print(uuid.uuid4())')"
    -H "Content-Type: application/json")
  [[ -n "$body" ]] && args+=(--data "$body")
  local code; code="$(curl "${args[@]}")"
  if [[ "$code" == "$want" ]]; then
    echo "  ✓ $method $path → $code"; PASS=$((PASS+1)); cat /tmp/khuluma_body
  else
    echo "  ✗ $method $path → $code (want $want)"; cat /tmp/khuluma_body; FAIL=$((FAIL+1)); echo
  fi
}

echo "== Khuluma Wave-1 smoke against $BASE (tenant $TENANT) =="

echo "1) A creates a patient-linked conversation with B"
CONV="$(call POST "$A" "/conversations" 201 \
  "{\"type\":\"DIRECT\",\"title\":\"Smoke\",\"participants\":[{\"actorId\":\"$B\",\"actorType\":\"PROVIDER\"}],\"links\":[{\"objectType\":\"PATIENT\",\"objectId\":\"cpid-smoke\"}]}" \
  | tail -1 | jq_field '["conversationId"]')"
echo "   conversationId=$CONV"

echo "2) A sends a message"
call POST "$A" "/conversations/$CONV/messages" 201 '{"body":"smoke hello","clientMessageId":"s-1"}' >/dev/null

echo "3) B reads the message list + marks read"
MSG="$(call GET "$B" "/conversations/$CONV/messages" 200 | tail -1 | jq_field '[0]["messageId"]')"
call POST "$B" "/conversations/$CONV/messages/read" 204 "{\"messageId\":\"$MSG\"}" >/dev/null

echo "4) Membership deny — non-participant X cannot post"
call POST "$X" "/conversations/$CONV/messages" 403 '{"body":"intruding"}' >/dev/null

echo "5) A starts a 1:1 call → B accepts → A signals → A ends"
CALL="$(call POST "$A" "/calls" 201 \
  "{\"conversationId\":\"$CONV\",\"callType\":\"AUDIO\",\"callees\":[{\"actorId\":\"$B\",\"actorType\":\"PROVIDER\"}]}" \
  | tail -1 | jq_field '["callId"]')"
call POST "$B" "/calls/$CALL/accept" 200 '{}' >/dev/null
call POST "$A" "/calls/$CALL/signals" 202 '{"signalType":"typing","detail":{"on":true}}' >/dev/null
call POST "$A" "/calls/$CALL/end" 200 '{"reason":"HANGUP"}' >/dev/null

echo "== Result: $PASS passed, $FAIL failed =="
[[ "$FAIL" -eq 0 ]]
