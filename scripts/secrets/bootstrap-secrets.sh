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
# Keys: vito-hmac-pepper, dags-signing-key, livekit-api-secret, plus the F6
# cryptographic seeds (Impilo ID encryption, QR/card signing, eligibility HMAC,
# wallet card master key, tshepo-keys KEK).
#
# WARNINGS
#  - vito-hmac-pepper is a PII-pseudonymization pepper. Rotating it on a cluster
#    that already holds VITO data invalidates existing HMACs — preserve it or plan
#    a data migration. This script never overwrites an existing value.
#  - card-print-qr-signing-key-seed is shared with vito so CardAssertionVerifier
#    can derive the matching verify key without a key-exchange step. Rotating it
#    invalidates already-printed cards signed under the old seed.
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

# Derive card-print's Ed25519 public key (base64url, no padding) from the signing
# seed — same SHA-256(seed) → Ed25519 private → public path QrAssertionService and
# CardAssertionVerifier use. Prefers cryptography; falls back to leaving the key
# unset so vito can derive from the shared seed instead.
derive_card_print_public_key() {
  local seed="$1"
  python3 - "$seed" <<'PY' 2>/dev/null || true
import sys, hashlib, base64
seed = sys.argv[1].encode("utf-8")
priv = hashlib.sha256(seed).digest()
try:
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
    pub = Ed25519PrivateKey.from_private_bytes(priv).public_key().public_bytes_raw()
except Exception:
    try:
        from nacl.signing import SigningKey
        pub = SigningKey(priv).verify_key.encode()
    except Exception:
        sys.exit(1)
print(base64.urlsafe_b64encode(pub).decode("ascii").rstrip("="))
PY
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

# F6 cryptographic seeds — previously source-visible DEV fallbacks. Fail-closed
# constructors refuse to start without these; never commit the values.
set_if_absent vito-impilo-id-encryption-key "$(openssl rand -hex 32)"
set_if_absent vito-qr-signing-key-seed "$(openssl rand -hex 32)"
set_if_absent card-print-qr-signing-key-seed "$(openssl rand -hex 32)"
set_if_absent ruvimbo-token-secret "$(openssl rand -hex 32)"
set_if_absent wallet-card-encryption-master-key "$(openssl rand -hex 32)"
# tshepo-keys KEK: 32-byte AES-256 as 64 hex chars (software custody root).
set_if_absent tshepo-keys-kek "$(openssl rand -hex 32)"

# Derive the verify key from the (stable) card-print seed so vito can verify
# without holding the signing seed. If derivation tools are absent, vito falls
# back to the shared seed via CARD_PRINT_QR_SIGNING_KEY_SEED.
card_seed="$(get_key card-print-qr-signing-key-seed)"
if [[ -n "$card_seed" ]]; then
  card_pub="$(derive_card_print_public_key "$card_seed")"
  if [[ -n "$card_pub" ]]; then
    set_if_absent card-print-qr-public-key "$card_pub"
  else
    echo "  card-print-qr-public-key: SKIP (no ed25519 derivation library; vito will use shared seed)"
  fi
fi

# Keycloak/MinIO admin. Usernames are not secret; passwords are randomised on
# fresh clusters. NOTE: Keycloak/MinIO read these only at first init — rotating on
# an initialised cluster needs an admin-API/mc password change, not just a re-seed.
set_if_absent keycloak-admin-user "admin"
set_if_absent keycloak-admin-password "$(openssl rand -hex 24)"
set_if_absent minio-root-user "impilo-preview"
set_if_absent minio-root-password "$(openssl rand -hex 24)"
# Keycloak confidential-client secrets (realm import ${env.*} + app KEYCLOAK_BACKEND_SECRET).
# Applied only at first realm import; rotating an imported realm needs the admin API.
set_if_absent keycloak-client-secret-bff "$(openssl rand -hex 24)"
set_if_absent keycloak-client-secret-ops-console "$(openssl rand -hex 24)"
set_if_absent keycloak-client-secret-admin-cli "$(openssl rand -hex 24)"
set_if_absent keycloak-backend-secret "$(openssl rand -hex 24)"
set_if_absent keycloak-user-admin-secret "$(openssl rand -hex 32)"
set_if_absent keycloak-event-reader-secret "$(openssl rand -hex 32)"
# AES-256 key for BFF-held browser sessions; base64 is required by the runtime.
set_if_absent web-session-encryption-key "$(openssl rand -base64 32 | tr -d '\n')"
set_if_absent keycloak-db-user "keycloak"
set_if_absent keycloak-db-password "$(openssl rand -hex 32)"

# SMTP / SMS gateway credentials are OPERATOR-PROVIDED out-of-band — never
# randomised (a random SMTP password is useless). Placeholders are seeded so
# secretKeyRef-mounted pods schedule; delivery stays honest because
# notification-service only activates real adapters when
# NOTIFICATION_EMAIL_PROVIDER=smtp / NOTIFICATION_SMS_PROVIDER=http are set.
# Provide real values via env on first run or `kubectl patch` later:
#   SMTP_HOST=… SMTP_USERNAME=… SMTP_PASSWORD=… bash scripts/secrets/bootstrap-secrets.sh
set_if_absent smtp-host "${SMTP_HOST:-placeholder-not-configured}"
set_if_absent smtp-username "${SMTP_USERNAME:-placeholder-not-configured}"
set_if_absent smtp-password "${SMTP_PASSWORD:-placeholder-not-configured}"
set_if_absent sms-gateway-url "${SMS_GATEWAY_URL:-placeholder-not-configured}"
set_if_absent sms-gateway-api-key "${SMS_GATEWAY_API_KEY:-placeholder-not-configured}"

# livekit-api-secret: source of truth for the RTC stack. Post-P2 the signing
# secret is NOT inlined in the livekit-config CM (only the api_key *name* is), so
# the CM read below is a legacy fallback and is normally empty — when empty,
# self-generate. The livekit server reads this value back via LIVEKIT_KEYS, so a
# generated secret stays internally consistent across all consumers. Operators
# can pin a specific value via LIVEKIT_API_SECRET. set_if_absent keeps it stable
# across runs once set (never re-randomised).
lk_val="${LIVEKIT_API_SECRET:-$(kubectl get cm -n "$NS" livekit-config \
  -o jsonpath='{.data.livekit\.yaml}' 2>/dev/null | awk '/impilo-preview-key:/{print $2; exit}')}"
lk_val="${lk_val:-$(openssl rand -hex 32)}"
set_if_absent livekit-api-secret "$lk_val"

# livekit-keys = "<apiKey>: <secret>" — consumed by the livekit server LIVEKIT_KEYS
# env (P2). Kept in sync with livekit-api-secret; the apiKey is not a secret.
lk_secret="$(get_key livekit-api-secret)"
[[ -n "$lk_secret" ]] && set_if_absent livekit-keys "${LIVEKIT_API_KEY:-impilo-preview-key}: ${lk_secret}"

echo "Done. Keys: $(kubectl get secret -n "$NS" "$SECRET" \
  -o go-template='{{range $k,$_ := .data}}{{$k}} {{end}}' 2>/dev/null)"
