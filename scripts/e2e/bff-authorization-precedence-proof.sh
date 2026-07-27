#!/usr/bin/env bash
# BFF Authorization precedence — live proof through the real ingress.
#
# The defect (fixed 940e50efd / 9ea3c435b): `trustHeaderForwardingInterceptor` copied the inbound
# Authorization with an unconditional set(). Interceptors run AFTER the client method has built its
# headers, so any bearer a client deliberately pre-set — above all the Keycloak ADMIN token — was
# replaced by whatever token the caller happened to be holding.
#
# WHAT MAKES THIS PROVABLE ON THE ESTATE. Preview runs IMPILO_SECURITY_ALLOW_ANONYMOUS=true, so
# "call the BFF without a token and expect 401" proves nothing here — that is the separately-tracked
# preview posture, not this defect. The discriminator that DOES work is the Keycloak Admin API:
# it rejects an ordinary user token outright. So any BFF flow that must talk to the Admin API can
# only succeed if the admin token survived the interceptor.
#
#   POSITIVE : /auth/register creates a Keycloak user via the impilo-backend admin token, called
#              WITH an inbound user bearer attached — precisely the condition under which the
#              admin token used to be overwritten. Asserts authenticated CONTENT (a created
#              account), never the mere absence of an error.
#   NEGATIVE : the SAME user bearer sent straight at Keycloak's Admin API must be refused. This is
#              what stops the positive being vacuous: if Keycloak accepted user tokens, success
#              above would demonstrate nothing about which token was sent.
#   ALSO     : /auth/login and the contact-OTP lane both moved to the non-intercepted idpRestTemplate
#              in 9ea3c435b; exercised here so a regression in that swap cannot pass unnoticed.
set -uo pipefail
BASE="${PREVIEW_URL:-https://impilo.mohcc.gov.zw}"
TENANT="00000000-0000-4000-8000-000000000001"
RES=(--resolve impilo.mohcc.gov.zw:443:127.0.0.1)
RUN="authprec-$(date +%s)"; PASS=0; FAIL=0
NS="${NS:-impilo-full-preview}"
ccurl(){ curl -sk -m20 "${RES[@]}" "$@"; }
jg(){ python3 -c "import json,sys
d=json.load(sys.stdin);cur=d
for k in '$1'.split('.'):
 cur=cur.get(k) if isinstance(cur,dict) else (cur[int(k)] if isinstance(cur,list) and k.lstrip('-').isdigit() else None)
 if cur is None: break
print(cur if cur is not None else '')" 2>/dev/null; }
ok(){ PASS=$((PASS+1)); echo "  ok: $*"; }
bad(){ FAIL=$((FAIL+1)); echo "  FAIL: $*"; }
hdr(){ echo; echo "== $* =="; }

mkh(){ H=(-H "Content-Type: application/json" -H "X-Tenant-ID: $TENANT" -H "X-Pod-ID: p"
          -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: $RUN"); }

hdr "0. login — AuthSessionController now on the NON-intercepted idpRestTemplate (9ea3c435b)"
mkh
TOK=$(ccurl -X POST "$BASE/internal/v1/auth/login" "${H[@]}" -H "Idempotency-Key: $RUN-l" \
        -d '{"email":"dr.mapfumo","password":"ImpiloTest123!"}' | jg data.attributes.token)
if [ -n "$TOK" ] && [ ${#TOK} -gt 100 ]; then
  ok "ROPC login returned a real JWT (${#TOK} chars) — the IdP-template swap did not break login"
else
  bad "login returned no usable token — idpRestTemplate swap regressed AuthSessionController"; exit 1
fi

hdr "1. NEGATIVE CONTROL — Keycloak's Admin API must REFUSE this user token"
# Without this, the positive below proves nothing: it is only evidence that the admin token
# survived if the user token would have failed in its place.
KCNEG=$(kubectl exec -n "$NS" deploy/oros-service -- sh -lc \
  "curl -s -o /dev/null -w '%{http_code}' -H 'Authorization: Bearer $TOK' \
   http://keycloak:8080/admin/realms/impilo/users?max=1" 2>/dev/null | tr -d '\r')
case "$KCNEG" in
  401|403) ok "Keycloak Admin API rejects the citizen/clinician token (HTTP $KCNEG) — the discriminator is real" ;;
  200)     bad "Keycloak Admin API ACCEPTED a user token (200) — this proof cannot discriminate; STOP" ;;
  *)       bad "unexpected status from Keycloak Admin API: '$KCNEG'" ;;
esac

hdr "2. POSITIVE — /auth/register with an inbound user bearer attached"
# This is the exact confused-deputy condition: a signed-in caller hitting a lane whose client
# method pre-sets its OWN (admin) bearer. Pre-fix, the interceptor replaced it with $TOK, which
# step 1 just proved Keycloak refuses — so registration could not have completed.
EMAIL="authprec+$RUN@impilo.test"
mkh
REG=$(ccurl -X POST "$BASE/internal/v1/auth/register" "${H[@]}" \
        -H "Authorization: Bearer $TOK" \
        -H "Idempotency-Key: $RUN-r" \
        -d "{\"email\":\"$EMAIL\",\"password\":\"ImpiloTest123!\",\"firstName\":\"Auth\",\"lastName\":\"Precedence\"}")
RCODE=$(echo "$REG" | jg error.code)
RTOK=$(echo "$REG" | jg data.attributes.token)
RUSER=$(echo "$REG" | jg data.attributes.user.email)
if [ -n "$RTOK" ] || [ -n "$RUSER" ]; then
  ok "registration completed WITH an inbound bearer present — account created, session issued"
  ok "authenticated content returned (user=${RUSER:-<none>}, token=${RTOK:+yes})"
elif [ "$RCODE" = "USER_EXISTS" ]; then
  ok "Keycloak answered USER_EXISTS — the ADMIN token reached the Admin API (a replaced user token could not have got this far)"
else
  bad "registration failed: code='${RCODE:-?}' body=$(echo "$REG" | head -c 400)"
fi

hdr "3. POSITIVE — the same account can now log in (the user really exists in Keycloak)"
# Guards against a 2xx that created nothing. The account must be independently usable.
mkh
TOK2=$(ccurl -X POST "$BASE/internal/v1/auth/login" "${H[@]}" -H "Idempotency-Key: $RUN-l2" \
        -d "{\"email\":\"$EMAIL\",\"password\":\"ImpiloTest123!\"}" | jg data.attributes.token)
if [ -n "$TOK2" ] && [ ${#TOK2} -gt 100 ]; then
  ok "the newly registered account authenticates — the Keycloak write was real, not a hollow 200"
else
  bad "the registered account cannot log in — registration reported success without creating a user"
fi

hdr "4. contact-OTP lane — AuthContactOtpController, the 4th Keycloak caller (found by the guard)"
# Pre-fix this class sent a password-grant to Keycloak carrying the CALLER'S bearer. Exercised with
# a bearer attached; must still transact rather than 5xx.
mkh
OTP=$(ccurl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/internal/v1/auth/contact/otp/request" \
        "${H[@]}" -H "Authorization: Bearer $TOK" -H "Idempotency-Key: $RUN-o" \
        -d '{"channel":"EMAIL","value":"authprec-otp@impilo.test"}')
case "$OTP" in
  200|202) ok "contact-OTP request transacted (HTTP $OTP) with an inbound bearer present" ;;
  429)     ok "contact-OTP rate-limited (429) — the lane is live and reached its limiter, not broken" ;;
  *)       bad "contact-OTP request returned HTTP $OTP" ;;
esac

hdr "5. no 401/403 from the BFF's own outbound calls since the roll"
SINCE=$(kubectl logs -n "$NS" -l app=experience-bff --since=15m --tail=4000 2>/dev/null \
        | grep -ciE "401 Unauthorized|403 Forbidden|Keycloak.*(admin|token).*fail" || true)
echo "  (advisory only — absence of errors is NOT proof; the assertions above are)"
echo "  matching log lines in the last 15m: ${SINCE:-0}"

echo
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ] || exit 1
