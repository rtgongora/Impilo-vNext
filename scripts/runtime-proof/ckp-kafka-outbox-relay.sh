#!/usr/bin/env bash
# ============================================================================
# CKP Kafka outbox relay — post-deploy runtime proof (Wave 5.9).
#
# Proves that impilo-full-preview has CLINICAL_KAFKA_RELAY_ENABLED=true on the
# clinical-knowledge-platform pod and that clinical.event_outbox rows drain after
# a multimorbidity assess (the path ClinicalKafkaOutboxRelayIT exercises in CI).
#
# Unit proof:  ClinicalKafkaOutboxRelayIT (embedded Kafka, relay-enabled=true)
# Config proof: deploy/helm/impilo-vnext/values-full-preview.yaml
#               clinical-knowledge-platform-service.env.CLINICAL_KAFKA_RELAY_ENABLED
# This script:  live cluster + Postgres after an authorized full-preview deploy.
#
# Usage:
#   bash scripts/runtime-proof/ckp-kafka-outbox-relay.sh
#   NS=impilo-full-preview bash scripts/runtime-proof/ckp-kafka-outbox-relay.sh
#
# Exit: 0 = relay env confirmed and a fresh outbox row drained; non-zero = blocked.
# ============================================================================
set -uo pipefail

NS="${NS:-impilo-full-preview}"
TENANT="${TENANT:-00000000-0000-4000-8000-000000000001}"
SUBJECT="cpid-ckp-relay-proof-$(date +%s)"
PASS=0; FAIL=0

ok(){ PASS=$((PASS+1)); echo "  PASS  $*"; }
bad(){ FAIL=$((FAIL+1)); echo "  FAIL  $*"; }

CKP_POD="$(kubectl get pod -n "$NS" -l app=clinical-knowledge-platform-service \
  -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)"
if [[ -z "$CKP_POD" ]]; then
  echo "No clinical-knowledge-platform-service pod in namespace $NS." >&2
  echo "Run after full-preview deploy (impilo-full-preview owns CKP; impilo-preview slice does not)." >&2
  exit 2
fi
ok "CKP pod: $CKP_POD"

RELAY="$(kubectl exec -n "$NS" "$CKP_POD" -- sh -c 'echo "${CLINICAL_KAFKA_RELAY_ENABLED:-}"' 2>/dev/null | tr -d '[:space:]')"
if [[ "$RELAY" == "true" ]]; then
  ok "CLINICAL_KAFKA_RELAY_ENABLED=true"
else
  bad "CLINICAL_KAFKA_RELAY_ENABLED is '$RELAY' (expected true) — relay bean will not start"
fi

psql_ckp(){ kubectl exec -n "$NS" deploy/postgres -- psql -U impilo -d clinical_knowledge -tAc "$1" 2>/dev/null; }

UNPUB_BEFORE="$(psql_ckp "SELECT count(*) FROM clinical.event_outbox WHERE published_at IS NULL" || echo "err")"
if [[ "$UNPUB_BEFORE" == "err" ]]; then
  bad "could not query clinical.event_outbox (Flyway / DB name clinical_knowledge?)"
else
  ok "unpublished outbox rows before probe: $UNPUB_BEFORE"
fi

# Write a row via the production assess path (same as ClinicalKafkaOutboxRelayIT).
HTTP="$(kubectl exec -n "$NS" "$CKP_POD" -- sh -c \
  "curl -s -o /tmp/ckp-relay-body.json -w '%{http_code}' -X POST \
   http://localhost:8270/internal/v1/clinical/multimorbidity/assess \
   -H 'Content-Type: application/json' \
   -H 'X-Tenant-ID: $TENANT' \
   -d '{\"subjectCpid\":\"$SUBJECT\",\"medicationCodes\":[\"warfarin\",\"rivaroxaban\"]}'" 2>/dev/null || echo "000")"
if [[ "$HTTP" == "200" ]]; then
  ok "multimorbidity assess returned 200 for $SUBJECT"
else
  bad "multimorbidity assess HTTP $HTTP (expected 200)"
fi

# Relay polls every ~2s; allow several passes before declaring stuck.
DRAINED=no
for _ in $(seq 1 15); do
  STUCK="$(psql_ckp "SELECT count(*) FROM clinical.event_outbox \
    WHERE subject_id LIKE '${SUBJECT}|%' AND published_at IS NULL" || echo "err")"
  if [[ "$STUCK" == "0" ]]; then
    DRAINED=yes
    break
  fi
  sleep 2
done

if [[ "$DRAINED" == yes ]]; then
  ok "outbox row for $SUBJECT drained (published_at set)"
else
  bad "outbox row for $SUBJECT still unpublished after ~30s — relay may be off or Kafka unreachable"
fi

PUB_COUNT="$(psql_ckp "SELECT count(*) FROM clinical.event_outbox \
  WHERE subject_id LIKE '${SUBJECT}|%' AND published_at IS NOT NULL" || echo "0")"
[[ "${PUB_COUNT:-0}" -ge 1 ]] && ok "published row count for probe subject: $PUB_COUNT" \
  || bad "no published row found for probe subject"

echo
echo "Summary: PASS=$PASS FAIL=$FAIL (namespace=$NS subject=$SUBJECT)"
[[ "$FAIL" -eq 0 ]]
