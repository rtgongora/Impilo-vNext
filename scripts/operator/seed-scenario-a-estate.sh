#!/usr/bin/env bash
# =============================================================================
# Scenario A estate seeding + verification (idempotent).
#
# Aligns the three identity layers the frontline-clinician journey depends on:
#   1. Sovereign domain seeds (facilities, patients, providers, workforce
#      governance assignments) — scripts/deploy/seed-full-preview-sovereign-data.sh
#   2. Keycloak persona users + identity-anchor attributes (health_id,
#      provider_id) — scripts/operator/reconcile-keycloak-realm-users.sh
#   3. Vashandi operational workforce profiles — API-first via the vashandi
#      reconcile upsert (providerWorkerId → profile), per persona.
#
# Then PROVES the chain end-to-end: login → health_id anchor → linked-ids →
# providerId → ACTIVE workforce assignment (drives hasWorkAccess/Work tab).
# Exits non-zero when verification fails.
#
# Usage (on the preview VM):
#   bash scripts/operator/seed-scenario-a-estate.sh
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

# persona -> providerWorkerId:healthId (must match seeds 04/12 + realm attributes)
declare -A PERSONAS=(
  ["dr.mapfumo"]="PROV-ZW-00001:c0000000-0000-4000-8000-000000000001"
  ["nurse.chienda"]="PROV-ZW-00007:c0000000-0000-4000-8000-000000000007"
)

fail() { echo "FAIL: $*" >&2; exit 1; }
info() { echo "==> $*"; }

command -v kubectl >/dev/null || fail "kubectl required"
command -v python3 >/dev/null || fail "python3 required"

info "1/4 sovereign domain seeds"
bash "$REPO/scripts/deploy/seed-full-preview-sovereign-data.sh"

info "2/4 Keycloak persona reconcile (users, passwords, roles, identity-anchor attributes)"
NAMESPACE="$NS" SECRET="${KEYCLOAK_SECRET:-impilo-app-secrets}" \
  bash "$REPO/scripts/operator/reconcile-keycloak-realm-users.sh"

info "3/4 Vashandi workforce profile mirror (API-first reconcile upsert per persona)"
VASHANDI_IP="$(kubectl get svc vashandi-workforce-service -n "$NS" -o jsonpath='{.spec.clusterIP}')"
for persona in "${!PERSONAS[@]}"; do
  IFS=: read -r pwid hid <<<"${PERSONAS[$persona]}"
  body="{\"providerWorkerId\":\"$pwid\",\"healthId\":\"$hid\"}"
  out=$(kubectl exec -n "$NS" deploy/experience-bff -- curl -sS -X POST \
    "http://${VASHANDI_IP}:8167/v1/internal/vashandi/workforce-profiles/reconcile" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: $TENANT_ID" -H "X-Pod-ID: pod-operator" \
    -H "X-Request-ID: seed-$(date +%s%N)" -H "X-Correlation-ID: seed-scenario-a" \
    -H "X-Actor-ID: operator-seed" -H "X-Actor-Type: SYSTEM" \
    -d "$body" 2>&1) || { echo "$out"; fail "vashandi reconcile for $persona"; }
  profile_id=$(echo "$out" | python3 -c "import json,sys; print(json.load(sys.stdin).get('profileId',''))" 2>/dev/null || echo "")
  [[ -n "$profile_id" ]] || { echo "$out"; fail "no profileId for $persona"; }
  echo "  ok: $persona → vashandi profile $profile_id"
done

info "4/4 verification: login → anchor → linked-ids → ACTIVE assignment"
verify_persona() {
  local persona="$1" pwid="$2" hid="$3"
  local login anchor linked provider assignments count
  login=$(curl -s -X POST "$PREVIEW_URL/internal/v1/auth/login" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: $TENANT_ID" -H "X-Pod-ID: pod-operator" \
    -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: seed-verify" \
    -H "Idempotency-Key: seed-verify-$persona-$(date +%s%N)" \
    -d "{\"email\":\"$persona\",\"password\":\"$PERSONA_PASSWORD\"}")
  anchor=$(echo "$login" | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['attributes']['user']['healthId'])" 2>/dev/null || echo "")
  [[ "$anchor" == "$hid" ]] || fail "$persona anchor mismatch: got '$anchor' want '$hid' (health_id claim missing?)"
  linked=$(curl -s "$PREVIEW_URL/internal/v1/identity/linked-ids" \
    -H "X-Tenant-ID: $TENANT_ID" -H "X-Pod-ID: pod-operator" \
    -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: seed-verify" \
    -H "X-Actor-ID: $hid")
  provider=$(echo "$linked" | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['attributes'].get('providerId',''))" 2>/dev/null || echo "")
  [[ "$provider" == "$pwid" ]] || fail "$persona linked-ids providerId: got '$provider' want '$pwid'"
  assignments=$(curl -s "$PREVIEW_URL/internal/v1/workforce-governance/assignments/search?subjectType=PROVIDER&subjectId=$pwid&status=ACTIVE" \
    -H "X-Tenant-ID: $TENANT_ID" -H "X-Pod-ID: pod-operator" \
    -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: seed-verify" \
    -H "X-Actor-ID: $hid")
  count=$(echo "$assignments" | python3 -c "import json,sys; print(len(json.load(sys.stdin).get('data',[])))" 2>/dev/null || echo 0)
  [[ "$count" -ge 1 ]] || fail "$persona has no ACTIVE workforce assignment"
  echo "  ok: $persona → anchor $hid → $pwid → $count ACTIVE assignment(s)"
}
for persona in "${!PERSONAS[@]}"; do
  IFS=: read -r pwid hid <<<"${PERSONAS[$persona]}"
  verify_persona "$persona" "$pwid" "$hid"
done

echo "PASS: Scenario A identity/assignment chain verified for ${#PERSONAS[@]} personas"
