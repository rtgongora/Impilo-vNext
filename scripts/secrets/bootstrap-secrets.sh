#!/usr/bin/env bash
#
# Provision the out-of-band Secret/impilo-app-secrets that supplies application
# signing secrets to services via secretKeyRef (templates/microservice.yaml
# `secretEnv`; refs emitted by generate-full-preview-runtime-values.mjs). Run this
# BEFORE `helm … sync` on a new cluster. Part of P1 of
# docs/security/secrets-management-migration-plan.md.
#
# Idempotent: existing keys are PRESERVED (never silently rotated). Fresh keys are
# generated with `openssl rand`.
#
# Keys: vito-hmac-pepper, dags-signing-key, livekit-api-secret.
#
# WARNINGS
#  - vito-hmac-pepper is a PII-pseudonymization pepper. Rotating it on a cluster
#    that already holds VITO data invalidates existing HMACs — preserve it or plan
#    a data migration. This script never overwrites an existing value.
#  - livekit-api-secret MUST equal the LiveKit server key (livekit-config `keys` /
#    values.livekit.apiSecret) until P2 unifies them; here it is copied from the
#    live livekit-config (or LIVEKIT_API_SECRET env) rather than randomised.
set -euo pipefail

NS="${NAMESPACE:-impilo-full-preview}"
SECRET="impilo-app-secrets"

get_key() { kubectl get secret -n "$NS" "$SECRET" -o jsonpath="{.data.$1}" 2>/dev/null | base64 -d 2>/dev/null || true; }

set_if_absent() { # key value
  local k="$1" v="$2"
  if [[ -n "$(get_key "$k")" ]]; then echo "  $k: preserved"; return; fi
  [[ -n "$v" ]] || { echo "  $k: SKIP (no value)"; return; }
  kubectl patch secret -n "$NS" "$SECRET" --type merge \
    -p "{\"data\":{\"$k\":\"$(printf '%s' "$v" | base64 -w0)\"}}" >/dev/null
  echo "  $k: set"
}

kubectl get secret -n "$NS" "$SECRET" >/dev/null 2>&1 \
  || kubectl create secret generic "$SECRET" -n "$NS" >/dev/null

echo "Provisioning $NS/$SECRET (idempotent):"
set_if_absent vito-hmac-pepper "$(openssl rand -hex 32)"
set_if_absent dags-signing-key "$(openssl rand -hex 32)"
# mushex-hmac-pepper is data-affecting like vito (preserve on existing clusters).
set_if_absent mushex-hmac-pepper "$(openssl rand -hex 32)"
# nhume-webhook-secret: on rotation the partner courier's configured secret must
# be updated to match (per-provider DB secrets take precedence over this fallback).
set_if_absent nhume-webhook-secret "$(openssl rand -hex 32)"

# livekit-api-secret: match the server key, don't randomise.
lk_val="${LIVEKIT_API_SECRET:-$(kubectl get cm -n "$NS" livekit-config \
  -o jsonpath='{.data.livekit\.yaml}' 2>/dev/null | awk '/impilo-preview-key:/{print $2; exit}')}"
set_if_absent livekit-api-secret "$lk_val"

echo "Done. Keys: $(kubectl get secret -n "$NS" "$SECRET" \
  -o go-template='{{range $k,$_ := .data}}{{$k}} {{end}}' 2>/dev/null)"
