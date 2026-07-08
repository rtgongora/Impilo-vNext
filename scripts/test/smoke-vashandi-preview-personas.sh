#!/usr/bin/env bash
# Smoke-test Vashandi preview personas: session contract workspaces + route existence.
set -euo pipefail

REPO="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
NS="${IMPILO_PREVIEW_NAMESPACE:-impilo-full-preview}"
PREVIEW_URL="${PREVIEW_URL:-https://impilo.mohcc.gov.zw}"
FIXTURES="$REPO/contracts/trust/seeds/preview-vashandi-session-fixtures.json"
PASSWORD="$(python3 -c "import json; print(json.load(open('$FIXTURES'))['previewPassword'])")"
TENANT="00000000-0000-4000-8000-000000000001"

kc_token() {
  local email="$1"
  kubectl exec -n "$NS" deploy/experience-bff -- curl -sS -X POST \
    "http://keycloak:8080/realms/impilo/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password" \
    -d "client_id=experience-ui" \
    -d "username=${email}" \
    -d "password=${PASSWORD}" \
    | python3 -c "import json,sys; print(json.load(sys.stdin).get('access_token',''))"
}

assert_session() {
  local label="$1"
  local email="$2"
  local health_id="$3"
  shift 3
  local expected_csv="$*"

  local token
  token="$(kc_token "$email")"
  if [[ -z "$token" ]]; then
    echo "FAIL ${label}: empty Keycloak token for ${email} (realm import may be pending)"
    return 1
  fi

  local raw visible_json
  raw="$(kubectl exec -n "$NS" deploy/experience-bff -- curl -sS \
    "http://127.0.0.1:8160/internal/v1/session/experience" \
    -H "Authorization: Bearer ${token}" \
    -H "X-Tenant-ID: ${TENANT}" \
    -H "X-Pod-ID: preview-smoke" \
    -H "X-Request-ID: smoke-$(date +%s)-${RANDOM}" \
    -H "X-Correlation-ID: smoke-vashandi" \
    -H "X-Actor-ID: ${health_id}" \
    -H "X-Actor-Type: PROVIDER" \
    -H "X-Purpose-Of-Use: TREATMENT" \
    -H "X-Login-Method: email")"

  visible_json="$(python3 -c "
import json, sys
payload = json.loads(sys.argv[1])
data = payload.get('data') or {}
attrs = data.get('attributes') if isinstance(data, dict) else {}
if not attrs and isinstance(data, dict):
    attrs = data
visible = attrs.get('visibleVashandiWorkspaces') or []
roles = attrs.get('roleTemplates') or []
work = ((attrs.get('tabs') or {}).get('work') or {}).get('visible')
print(json.dumps({'visible': visible, 'roleTemplates': roles, 'workVisible': work}))
" "$raw")"

  python3 -c "
import json, sys
expected = set(filter(None, sys.argv[1].split(',')))
state = json.loads(sys.argv[2])
visible = set(state.get('visible') or [])
missing = sorted(expected - visible)
extra_priv = sorted((visible - expected) & {'vashandi.assignments', 'vashandi.access_review'})
if missing:
    print('FAIL ${label}: missing', missing, 'visible=', sorted(visible), 'roleTemplates=', state.get('roleTemplates'), 'workVisible=', state.get('workVisible'))
    sys.exit(1)
if extra_priv and '${label}' == 'Ordinary Worker':
    print('FAIL ${label}: leaked privileged workspaces', extra_priv)
    sys.exit(1)
print('OK  ${label}: workspaces match fixture (count=%d)' % len(visible))
" "$expected_csv" "$visible_json"
}

echo "==> Vashandi preview persona smoke (namespace=$NS)"

ROUTES=(
  /work/vashandi
  /work/vashandi/workforce
  /work/vashandi/assignments
  /work/vashandi/rosters
  /work/vashandi/attendance
  /work/vashandi/leave-availability
  /work/vashandi/access-review
  /work/vashandi/analytics
)

echo "-- Route existence (auth redirect expected without session)"
for route in "${ROUTES[@]}"; do
  code="$(curl -sS -o /dev/null -w '%{http_code}' "${PREVIEW_URL}${route}")"
  if [[ "$code" =~ ^(200|301|302|307|308|401|403)$ ]]; then
    echo "OK  ${route} -> HTTP ${code}"
  else
    echo "FAIL ${route} -> HTTP ${code}"
    exit 1
  fi
done

echo "-- Session contract workspaces per persona"
assert_session "National Admin" "vashandi.national@mohcc.gov.zw" "c0000000-0000-4000-8000-000000000101" \
  "vashandi.dashboard,vashandi.my_roster,vashandi.my_attendance,vashandi.leave_availability,vashandi.workforce_registry,vashandi.analytics"
assert_session "Facility Manager" "vashandi.facility@mohcc.gov.zw" "c0000000-0000-4000-8000-000000000102" \
  "vashandi.dashboard,vashandi.my_roster,vashandi.my_attendance,vashandi.leave_availability,vashandi.facility_staff,vashandi.rosters,vashandi.assignments,vashandi.analytics"
assert_session "Ordinary Worker" "vashandi.worker@mohcc.gov.zw" "c0000000-0000-4000-8000-000000000103" \
  "vashandi.dashboard,vashandi.my_roster,vashandi.my_attendance,vashandi.leave_availability"
assert_session "HSC Workforce" "vashandi.hsc@mohcc.gov.zw" "c0000000-0000-4000-8000-000000000104" \
  "vashandi.dashboard,vashandi.my_roster,vashandi.my_attendance,vashandi.leave_availability,vashandi.hsc_postings,vashandi.workforce_registry"
assert_session "Access Reviewer" "vashandi.reviewer@mohcc.gov.zw" "c0000000-0000-4000-8000-000000000105" \
  "vashandi.dashboard,vashandi.my_roster,vashandi.my_attendance,vashandi.leave_availability,vashandi.access_review,vashandi.analytics"
assert_session "Citizen negative" "tatenda.moyo@example.com" "b0000000-0000-4000-8000-000000000001" ""

echo "PASS: Vashandi preview smoke complete"
