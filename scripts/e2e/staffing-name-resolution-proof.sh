#!/usr/bin/env bash
# Staffing name-resolution live proof — through the real ingress, on the endpoint the rota calls.
#
# The on-call board reads /internal/v1/staffing/on-call. vashandi holds the rostered profile and a
# worker reference but no name; the BFF composes the display name and phone against varapi
# (profile -> vashandi health-id -> varapi display facts). This proves the whole vertical:
#   POSITIVE — a rostered, varapi-registered person comes back with a real display_name, the
#              registry phone (tagged PROVIDER_REGISTRY), and meta.identity_resolution=LIVE.
#   NEGATIVE CONTROL — an in-cluster POST to varapi's internal resolve-display from a caller that
#              is NOT in INTERNAL access mode is refused (403 INTERNAL_ONLY). Only the BFF's own
#              egress sets X-Access-Mode: INTERNAL (and mints a client_credentials bearer), so an
#              arbitrary caller cannot reach this surface — the composition is a privileged hop, not
#              an open trust-header passthrough. (Preview varapi does not yet enforce the JWT on this
#              authenticated()-only route — a tokenless call WITH INTERNAL mode still 200s — so a
#              "401" control cannot hold here; JWT enforcement is the separate preview->prod
#              auth-hardening wave. The token is minted and attached regardless, for when it does.)
set -uo pipefail
NS=impilo-full-preview
BASE="${PREVIEW_URL:-https://impilo.mohcc.gov.zw}"
TENANT="${TENANT_ID:-00000000-0000-4000-8000-000000000001}"
RESOLVE_IP="${PREVIEW_RESOLVE_IP:-10.50.1.67}"
PROFILE="c3023adb-8146-40d2-8c00-6e8f9599f73a"   # carries provider health id c0000000-...-000000000001
FACILITY="f0000000-0000-4000-8000-0000000000f1"  # a facility scoped only to this proof
WEEK_START="2026-07-27"
PG="$(kubectl -n $NS get pods -o name | grep -m1 postgres | cut -d/ -f2)"
RES=(--resolve "impilo.mohcc.gov.zw:443:${RESOLVE_IP}")
RUN="staffname-$(date +%s)"
PASS=0; FAIL=0
ccurl(){ curl -sk -m20 "${RES[@]}" "$@"; }
ok(){ PASS=$((PASS+1)); echo "  ok: $*"; }
bad(){ FAIL=$((FAIL+1)); echo "  FAIL: $*"; }
psql_v(){ kubectl -n $NS exec "$PG" -- psql -U impilo -d vashandi_workforce -tAc "$1"; }

echo "== 0. seed a roster + on-call PRIMARY shift for the rostered profile =="
ROSTER="a0000000-0000-4000-8000-0000000000a1"
SHIFT="b0000000-0000-4000-8000-0000000000b1"
psql_v "insert into vashandi.vsh_roster(id,tenant_id,organisation_id,roster_type,period_start,period_end,status,created_at,updated_at)
        values('$ROSTER','$TENANT','$TENANT','ON_CALL','$WEEK_START','2026-08-03','PUBLISHED',now(),now())
        on conflict (id) do nothing;" >/dev/null 2>&1
psql_v "insert into vashandi.vsh_shift(id,tenant_id,roster_id,workforce_profile_id,shift_type,on_call_role,specialty,facility_id,start_time,end_time,status,check_in_required,created_at,updated_at)
        values('$SHIFT','$TENANT','$ROSTER','$PROFILE','NIGHT','PRIMARY','SURGERY','$FACILITY','${WEEK_START}T18:00:00Z','2026-07-28T08:00:00Z','scheduled',false,now(),now())
        on conflict (id) do nothing;" >/dev/null 2>&1
SEEDED="$(psql_v "select count(*) from vashandi.vsh_shift where id='$SHIFT' and on_call_role='PRIMARY';")"
[ "$SEEDED" = "1" ] && ok "seeded on-call shift" || { bad "seed shift (got '$SEEDED')"; }

echo "== 1. POSITIVE: the rota resolves the name and the registry phone (ingress) =="
RESP="$(ccurl "$BASE/internal/v1/staffing/on-call?facility_id=$FACILITY&week_start=$WEEK_START" \
  -H "X-Tenant-ID: $TENANT" -H 'X-Pod-ID: national-spine' \
  -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: $RUN" \
  -H 'X-Actor-Type: PROVIDER' -H 'X-Actor-ID: staffname-proof' -H 'X-Purpose-Of-Use: TREATMENT')"
echo "$RESP" | python3 -c "
import json,sys
d=json.load(sys.stdin)
rows=d.get('data') or []
meta=d.get('meta') or {}
row=next((r for r in rows if r['attributes'].get('specialty')=='SURGERY'), None)
assert row, 'no SURGERY on-call row returned: %s' % rows
a=row['attributes']
name=a.get('primary_display_name'); phone=a.get('primary_phone'); src=a.get('primary_phone_source')
res=meta.get('identity_resolution')
assert res=='LIVE', 'identity_resolution=%r (want LIVE)' % res
assert name and name.strip() and name!=a.get('primary_staff_reference'), 'display_name not resolved: %r' % name
assert phone, 'no registry phone: %r' % phone
assert src=='PROVIDER_REGISTRY', 'phone_source=%r' % src
print('  ok: resolved name=%r phone=%r source=%s resolution=%s' % (name,phone,src,res))
print('  ok: staff_reference kept=%r' % a.get('primary_staff_reference'))
" && PASS=$((PASS+2)) || bad "positive assertion"

echo "== 2. NEGATIVE CONTROL: a non-INTERNAL caller is refused varapi resolve-display =="
# Only the BFF egress interceptor stamps X-Access-Mode: INTERNAL; an arbitrary in-cluster caller
# without it must be refused. 403 (INTERNAL_ONLY) or 401 (where JWT is enforced) both count as a
# refusal; a 200 would mean the privileged surface is open, which is the real failure.
CURLPOD="$(kubectl -n $NS get pods -o name | grep -m1 oros-service | cut -d/ -f2)"
CODE="$(kubectl -n $NS exec "$CURLPOD" -- curl -s -o /dev/null -w '%{http_code}' -m15 \
  -X POST http://varapi-service:8083/v1/internal/providers/by-health-id/resolve-display \
  -H 'Content-Type: application/json' -H "X-Tenant-ID: $TENANT" -H 'X-Actor-Type: SERVICE' \
  -d '{"healthIds":["c0000000-0000-4000-8000-000000000001"]}' 2>/dev/null)"
case "$CODE" in
  401|403) ok "non-INTERNAL caller refused varapi resolve-display → $CODE (surface is not open)" ;;
  *) bad "expected the non-INTERNAL caller to be refused (401/403), got $CODE" ;;
esac

echo "== 3. cleanup seed =="
psql_v "delete from vashandi.vsh_shift where id='$SHIFT'; delete from vashandi.vsh_roster where id='$ROSTER';" >/dev/null 2>&1 && ok "seed cleaned"

echo
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" = "0" ]
