#!/usr/bin/env bash
# =============================================================================
# MANDATORY post-deploy check — Keycloak is actually serving.
#
# WHY THIS EXISTS (2026-07-20 → 2026-07-26 incident):
# Keycloak crash-looped on a malformed realm import for SIX DAYS. `endpoints/keycloak`
# was empty for NINE. Every /realms/* request returned 503, so web login and mobile
# login were both completely dead — and nobody noticed, because nothing depends on
# Keycloak until a human tries to sign in. Pods "existed", the Traefik route was
# correct, the site returned 200. The only signal was an empty Service endpoint list.
#
# This check asks the three questions that would have caught it on day one:
#   1. does the Service have at least one ready endpoint?
#   2. does OIDC discovery return 200 through the public edge?
#   3. does it advertise an https issuer? (KC_PROXY_HEADERS=xforwarded — without it
#      Keycloak advertises http:// behind Traefik, and strict issuer-validating
#      clients fail AFTER a successful credential check, which reads as "login
#      broken" while discovery says 200.)
#
# Usage: NAMESPACE=impilo-full-preview bash scripts/full-boot/verify-keycloak-endpoints.sh
# Exit:  0 = Keycloak serving · 1 = auth is down (deploy must be treated as failed)
# =============================================================================
set -uo pipefail
NS="${NAMESPACE:-impilo-full-preview}"
PUBLIC_HOST="${PUBLIC_HOST:-impilo.mohcc.gov.zw}"
# Hairpin: from inside the estate the public IP is not routable back to itself.
LB_IP="${LB_IP:-10.50.1.67}"

fail=0
report() { # report <OK|FAIL> <label> <detail>
  printf '%-5s %-30s %s\n' "$1" "$2" "$3"
  [ "$1" = "FAIL" ] && fail=1
  return 0
}

# --- 1. Service endpoints ----------------------------------------------------
eps="$(kubectl get endpoints keycloak -n "$NS" -o jsonpath='{.subsets[*].addresses[*].ip}' 2>/dev/null)"
if [ -n "$eps" ]; then
  report OK "keycloak endpoints" "$eps"
else
  report FAIL "keycloak endpoints" "<none> — Service has no ready backends"
  # The pod state is the actionable detail; realm-import crashes show as Error/CrashLoop.
  kubectl get pods -n "$NS" -l app=keycloak --no-headers 2>/dev/null | sed 's/^/      pod: /'
fi

# --- 2. OIDC discovery through the public edge -------------------------------
code="$(curl -sS --max-time 15 --resolve "${PUBLIC_HOST}:443:${LB_IP}" \
  -o /tmp/kc-disc.$$ -w '%{http_code}' \
  "https://${PUBLIC_HOST}/realms/impilo/.well-known/openid-configuration" 2>/dev/null)"
if [ "$code" = "200" ]; then
  report OK "OIDC discovery" "HTTP 200"
else
  report FAIL "OIDC discovery" "HTTP ${code:-000} (503 = no healthy Keycloak behind the route)"
fi

# --- 3. Issuer scheme --------------------------------------------------------
if [ "$code" = "200" ]; then
  iss="$(python3 -c "import json;print(json.load(open('/tmp/kc-disc.$$'))['issuer'])" 2>/dev/null)"
  case "$iss" in
    https://*) report OK  "issuer scheme" "$iss" ;;
    "")        report FAIL "issuer scheme" "could not parse issuer from discovery document" ;;
    *)         report FAIL "issuer scheme" "$iss — expected https; set KC_PROXY_HEADERS=xforwarded" ;;
  esac
fi
rm -f /tmp/kc-disc.$$

if [ "$fail" -ne 0 ]; then
  echo ""
  echo "MANDATORY CHECK FAILED: Keycloak is not serving authentication."
  echo "  ALL web and mobile login is down while this is true. Triage:"
  echo "    kubectl -n $NS get pods -l app=keycloak"
  echo "    kubectl -n $NS logs -l app=keycloak --previous --tail=40   # realm-import errors appear here"
  echo "  Most common causes, in order:"
  echo "    1. malformed realm JSON  → bash scripts/guard/check-keycloak-realm-import.sh"
  echo "    2. stale containerd sandbox after an eviction → force-delete the pod"
  echo "    3. placeholder client secrets → bash scripts/keycloak/reconcile-client-secrets.sh"
  echo "  Runbook: docs/runbooks/keycloak-auth-outage-runbook.md"
  exit 1
fi
echo "PASS: Keycloak serving — endpoints present, discovery 200, https issuer."
exit 0
