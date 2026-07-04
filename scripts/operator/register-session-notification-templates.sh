#!/usr/bin/env bash
# =============================================================================
# Operator batch — register session/comms notification templates (national pod).
#
# The comms hub renders strictly from registered templates, and template
# governance is a national-pod authority. This idempotently self-provisions
# (GET /internal/v1/templates/{key} → 404 → POST) the W2 telemedicine +
# khuluma meeting + learning/live-event session template keys, mirroring the
# fundo-learner-journey step-0 pattern.
#
# Usage:
#   FULL_BOOT_NAMESPACE=impilo-full-preview ./register-session-notification-templates.sh
#
# Env:
#   TENANT_ID            tenant to register under (default: preview tenant)
#   FULL_BOOT_NAMESPACE  k8s namespace (default: impilo-full-preview)
#   NOTIFY_BASE_URL      notification-service base URL as seen from the exec
#                        pod (default: http://notification-service:8200)
#   EXEC_DEPLOY          deployment to exec curl from (default: experience-bff)
# =============================================================================
set -euo pipefail

TENANT_ID="${TENANT_ID:-00000000-0000-4000-8000-000000000001}"
NS="${FULL_BOOT_NAMESPACE:-impilo-full-preview}"
NOTIFY="${NOTIFY_BASE_URL:-http://notification-service:8200}"
EXEC_DEPLOY="${EXEC_DEPLOY:-experience-bff}"
RUN="tpl-$(date +%s)"

# W2 session/comms template keys (IN_APP; subject {{title}}, body {{message}}).
TEMPLATE_KEYS=(
  "rtc.telemedicine.patient-waiting"
  "rtc.telemedicine.session-ready"
  "rtc.telemedicine.appointment-reminder"
  "khuluma.meeting.invite"
  "khuluma.meeting.admission-requested"
  "learning.session.reminder"
  "live.event.starting-soon"
)

PASS=0
FAIL=0
ok()   { PASS=$((PASS+1)); echo "  ok: $*"; }
bad()  { FAIL=$((FAIL+1)); echo "  FAIL: $*" >&2; }

nat_curl() { # nat_curl <method> <url> [json-body] — national-pod template governance
  local method="$1" url="$2" body="${3:-}"
  kubectl exec -n "$NS" "deploy/$EXEC_DEPLOY" -- curl -sS -o /dev/null -w "%{http_code}" -X "$method" "$url" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: $TENANT_ID" -H "X-Pod-ID: national" \
    -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: $RUN" \
    -H "Idempotency-Key: $RUN-$(cat /proc/sys/kernel/random/uuid | cut -c1-8)" \
    ${body:+-d "$body"}
}

echo "== session notification templates → $NOTIFY (namespace $NS, tenant $TENANT_ID)"
for KEY in "${TEMPLATE_KEYS[@]}"; do
  CODE=$(nat_curl GET "$NOTIFY/internal/v1/templates/$KEY")
  case "$CODE" in
    200)
      ok "$KEY already registered"
      ;;
    404)
      CREATE_CODE=$(nat_curl POST "$NOTIFY/internal/v1/templates" \
        "{\"key\":\"$KEY\",\"channel\":\"IN_APP\",\"subject\":\"{{title}}\",\"body\":\"{{message}}\"}")
      if [[ "$CREATE_CODE" == "201" ]]; then
        ok "$KEY registered (national pod)"
      else
        bad "$KEY creation failed (HTTP $CREATE_CODE)"
      fi
      ;;
    *)
      bad "$KEY lookup failed (HTTP $CODE)"
      ;;
  esac
done

echo ""
echo "== done: $PASS ok, $FAIL failed"
[[ "$FAIL" -eq 0 ]] || exit 1
