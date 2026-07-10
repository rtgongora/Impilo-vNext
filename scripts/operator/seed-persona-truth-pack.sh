#!/usr/bin/env bash
# =============================================================================
# Persona Truth Pack seeding + verification (idempotent).
#
# Extends seed-scenario-a-estate.sh to the full journey-completion persona set
# (docs/demo/persona-truth-pack.md): doctor, nurse, records clerk, facility
# admin, second-facility specialist, radiographer, pharmacist, trainer/author,
# learner, citizen, national admin, regulator, IATG officer.
#
#   1. Sovereign domain seeds (incl. 14/15 persona rows) —
#      scripts/deploy/seed-full-preview-sovereign-data.sh
#   2. Keycloak persona users + roles (TRAINER/HIE_ADMIN auto-created) —
#      scripts/operator/reconcile-keycloak-realm-users.sh
#   3. Vashandi workforce profile mirror per provider persona.
#   4. Verification:
#        provider personas — login → health_id anchor → linked-ids providerId →
#                            ACTIVE workforce assignment
#        governance/citizen personas — login succeeds and carries the expected
#                                      realm role in the session role list
#
# Usage (on the preview VM):
#   bash scripts/operator/seed-persona-truth-pack.sh
# Env:
#   FULL_BOOT_NAMESPACE (default impilo-full-preview)
#   PREVIEW_URL         (default http://127.0.0.1 — hairpin-safe on the VM)
#   PERSONA_PASSWORD    (default ImpiloTest123!)
# =============================================================================
set -euo pipefail

REPO="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
NS="${FULL_BOOT_NAMESPACE:-impilo-full-preview}"
PREVIEW_URL="${PREVIEW_URL:-http://127.0.0.1}"
PERSONA_PASSWORD="${PERSONA_PASSWORD:-ImpiloTest123!}"
TENANT_ID="${TENANT_ID:-00000000-0000-4000-8000-000000000001}"

# provider persona -> providerWorkerId:healthId (must match seeds 04/12/14 + realm attributes)
declare -A PROVIDER_PERSONAS=(
  ["dr.mapfumo"]="PROV-ZW-00001:c0000000-0000-4000-8000-000000000001"
  ["nurse.chienda"]="PROV-ZW-00007:c0000000-0000-4000-8000-000000000007"
  ["clerk.dube"]="PROV-ZW-00008:c0000000-0000-4000-8000-000000000008"
  ["dr.gwena"]="PROV-ZW-00009:c0000000-0000-4000-8000-000000000009"
  ["rad.nkomo"]="PROV-ZW-00010:c0000000-0000-4000-8000-000000000010"
  ["pharm.zimba"]="PROV-ZW-00004:c0000000-0000-4000-8000-000000000004"
  ["trainer.chikafu"]="PROV-ZW-00011:c0000000-0000-4000-8000-000000000011"
  ["learner.tembo"]="PROV-ZW-00012:c0000000-0000-4000-8000-000000000012"
)

# governance/citizen persona -> realm role the session must carry
declare -A ROLE_PERSONAS=(
  ["admin.central"]="SYSTEM_ADMIN"
  ["admin.harare"]="FACILITY_ADMIN"
  ["citizen.moyo"]="CITIZEN"
  ["regulator.hpcz"]="HIE_ADMIN"
  ["iatg.gono"]="HIE_ADMIN"
  ["dispatcher.chirwa"]="DISPATCH_COORDINATOR"
  ["courier.banda"]="COURIER"
)

fail() { echo "FAIL: $*" >&2; exit 1; }
info() { echo "==> $*"; }

command -v kubectl >/dev/null || fail "kubectl required"
command -v python3 >/dev/null || fail "python3 required"

info "1/4 sovereign domain seeds (incl. persona truth pack 14/15)"
bash "$REPO/scripts/deploy/seed-full-preview-sovereign-data.sh"

info "2/4 Keycloak persona reconcile (users, passwords, roles, identity-anchor attributes)"
NAMESPACE="$NS" SECRET="${KEYCLOAK_SECRET:-impilo-app-secrets}" \
  bash "$REPO/scripts/operator/reconcile-keycloak-realm-users.sh"

info "3/4 Vashandi workforce profile mirror (API-first reconcile upsert per provider persona)"
VASHANDI_IP="$(kubectl get svc vashandi-workforce-service -n "$NS" -o jsonpath='{.spec.clusterIP}')"
for persona in "${!PROVIDER_PERSONAS[@]}"; do
  IFS=: read -r pwid hid <<<"${PROVIDER_PERSONAS[$persona]}"
  body="{\"providerWorkerId\":\"$pwid\",\"healthId\":\"$hid\"}"
  out=$(kubectl exec -n "$NS" deploy/experience-bff -- curl -sS -X POST \
    "http://${VASHANDI_IP}:8167/v1/internal/vashandi/workforce-profiles/reconcile" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: $TENANT_ID" -H "X-Pod-ID: pod-operator" \
    -H "X-Request-ID: seed-$(date +%s%N)" -H "X-Correlation-ID: seed-persona-pack" \
    -H "X-Actor-ID: operator-seed" -H "X-Actor-Type: SYSTEM" \
    -d "$body" 2>&1) || { echo "$out"; fail "vashandi reconcile for $persona"; }
  profile_id=$(echo "$out" | python3 -c "import json,sys; print(json.load(sys.stdin).get('profileId',''))" 2>/dev/null || echo "")
  [[ -n "$profile_id" ]] || { echo "$out"; fail "no profileId for $persona"; }
  echo "  ok: $persona → vashandi profile $profile_id"
done

info "4/4 verification"
login_persona() {
  local persona="$1"
  curl -s -X POST "$PREVIEW_URL/internal/v1/auth/login" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: $TENANT_ID" -H "X-Pod-ID: pod-operator" \
    -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: persona-verify" \
    -H "Idempotency-Key: persona-verify-$persona-$(date +%s%N)" \
    -d "{\"email\":\"$persona\",\"password\":\"$PERSONA_PASSWORD\"}"
}

verify_provider_persona() {
  local persona="$1" pwid="$2" hid="$3"
  local login anchor linked provider assignments count
  login=$(login_persona "$persona")
  anchor=$(echo "$login" | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['attributes']['user']['healthId'])" 2>/dev/null || echo "")
  [[ "$anchor" == "$hid" ]] || fail "$persona anchor mismatch: got '$anchor' want '$hid' (health_id claim missing?)"
  linked=$(curl -s "$PREVIEW_URL/internal/v1/identity/linked-ids" \
    -H "X-Tenant-ID: $TENANT_ID" -H "X-Pod-ID: pod-operator" \
    -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: persona-verify" \
    -H "X-Actor-ID: $hid")
  provider=$(echo "$linked" | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['attributes'].get('providerId',''))" 2>/dev/null || echo "")
  [[ "$provider" == "$pwid" ]] || fail "$persona linked-ids providerId: got '$provider' want '$pwid'"
  assignments=$(curl -s "$PREVIEW_URL/internal/v1/workforce-governance/assignments/search?subjectType=PROVIDER&subjectId=$pwid&status=ACTIVE" \
    -H "X-Tenant-ID: $TENANT_ID" -H "X-Pod-ID: pod-operator" \
    -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: persona-verify" \
    -H "X-Actor-ID: $hid")
  count=$(echo "$assignments" | python3 -c "import json,sys; print(len(json.load(sys.stdin).get('data',[])))" 2>/dev/null || echo 0)
  [[ "$count" -ge 1 ]] || fail "$persona has no ACTIVE workforce assignment"
  echo "  ok: $persona → anchor $hid → $pwid → $count ACTIVE assignment(s)"
}

verify_role_persona() {
  local persona="$1" want_role="$2"
  local login has_role
  login=$(login_persona "$persona")
  has_role=$(echo "$login" | python3 -c "
import json, sys
data = json.load(sys.stdin)['data']['attributes']
roles = data.get('user', {}).get('roles') or data.get('roles') or []
print('yes' if '$want_role' in roles else 'no:' + ','.join(roles))
" 2>/dev/null || echo "no:parse-error")
  [[ "$has_role" == "yes" ]] || fail "$persona login missing role $want_role ($has_role)"
  echo "  ok: $persona → role $want_role present"
}

for persona in "${!PROVIDER_PERSONAS[@]}"; do
  IFS=: read -r pwid hid <<<"${PROVIDER_PERSONAS[$persona]}"
  verify_provider_persona "$persona" "$pwid" "$hid"
done
for persona in "${!ROLE_PERSONAS[@]}"; do
  verify_role_persona "$persona" "${ROLE_PERSONAS[$persona]}"
done

echo "PASS: persona truth pack verified — ${#PROVIDER_PERSONAS[@]} provider + ${#ROLE_PERSONAS[@]} governance/citizen personas"
