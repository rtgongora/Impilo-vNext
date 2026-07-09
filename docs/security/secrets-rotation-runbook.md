# Secrets rotation runbook

How to rotate the application secrets now that they live out-of-band in the
`impilo-app-secrets` Secret (+ Keycloak/MinIO creds folded in), rather than in
git. Companion to `docs/security/secrets-management-migration-plan.md`.

## Model

- **Secret**: `impilo-app-secrets` (namespace `impilo-full-preview`), provisioned by
  `scripts/secrets/bootstrap-secrets.sh` (idempotent — preserves existing keys,
  randomises absent ones). Consumers read via `secretKeyRef` / `${env.*}`.
- **Bootstrap is preserve-only**: re-running it never rotates an existing key. To
  rotate, you explicitly overwrite the key and roll the consumers below.

## Rotate a key (general procedure)

```bash
NS=impilo-full-preview
kubectl patch secret -n $NS impilo-app-secrets --type merge \
  -p "{\"data\":{\"<key>\":\"$(printf '%s' '<new-value>' | base64 -w0)\"}}"
# then roll every consumer of that key (see table) so it re-reads the Secret:
kubectl rollout restart deploy/<consumer> -n $NS
```
Secrets are read at **pod start** (or init-container render) — a consumer keeps the
old value until it rolls.

## Per-secret reference

| Key | Consumers to roll | Caveats |
|-----|-------------------|---------|
| `livekit-api-secret` **and** `livekit-keys` | `livekit`, `rtc-gateway-service`, `livekit-egress` | Roll **all three together** and keep `livekit-keys` = `impilo-preview-key: <livekit-api-secret>`. Egress reads via its render initContainer (rolls on restart). |
| `vito-hmac-pepper` | `vito-service` | **Data-affecting** — rotating invalidates every existing VITO pseudonym HMAC. Requires a re-pseudonymisation migration; do NOT rotate casually. |
| `mushex-hmac-pepper` | `mushex-service` | **Data-affecting** (HMAC pepper) — same as vito. |
| `dags-signing-key` | `data-access-governance-service` | Invalidates in-flight permit tokens (short-lived) — safe; brief re-issue. |
| `nhume-webhook-secret` | `nhume-service` | **Coordination** — the partner courier's configured secret must change to match. Per-provider DB secrets take precedence over this shared fallback. |
| `minio-root-password` / `minio-root-user` | `minio`, `document-service`, `rtc-gateway-service`, `livekit-egress` (+ bucket-init) | **Init-only** for MinIO: the running MinIO keeps its original root creds until changed via `mc admin user svcacct`/re-init. Rotating the Secret alone only re-points clients — the server side needs a separate `mc` change. |
| `keycloak-admin-user` / `keycloak-admin-password` | `keycloak` | **Init-only**: Keycloak sets the admin user at first boot. Rotating the Secret does NOT change the live admin password — change it via the admin console/API, then update the Secret to match. |
| `keycloak-client-secret-*`, `keycloak-backend-secret` | `keycloak` (import env), `experience-bff` (backend) | **First-import only** via `${env.*}`. On an already-imported realm, rotate the client secret via the **admin API** (below) AND update `impilo-app-secrets` + roll `experience-bff`. |

## Existing-cluster Keycloak client-secret rotation (admin API)

`${env.*}` substitution only runs at the **first** realm import. To rotate a
confidential client on a running Keycloak:

```bash
# 1. New secret
NEW=$(openssl rand -hex 24)
# 2. Set it on the client in Keycloak (via admin token; client uuid from GET .../clients)
#    POST /admin/realms/impilo/clients/<uuid>/client-secret  (regenerate) OR
#    PUT the client with "secret": "$NEW".
# 3. Mirror into impilo-app-secrets and roll the app consumer(s):
kubectl patch secret -n impilo-full-preview impilo-app-secrets --type merge \
  -p "{\"data\":{\"keycloak-backend-secret\":\"$(printf '%s' "$NEW"|base64 -w0)\"}}"
kubectl rollout restart deploy/experience-bff -n impilo-full-preview
```
Do the same (admin API + Secret + no app consumer) for `impilo-bff`,
`impilo-ops-console`, `impilo-admin-cli`. The operator scripts
(`scripts/operator/reconcile-keycloak-realm-users.sh`, etc.) read the admin creds
from `impilo-app-secrets` (keys `keycloak-admin-*`).

## Before public go-live (recommended rotations)

The migration preserved the *weak preview values* (they are just no longer in git).
Before exposing `impilo.mohcc.gov.zw` publicly, rotate to strong values:
1. `keycloak-admin-password`, `minio-root-password` (admin console / `mc`, then Secret).
2. All `keycloak-client-secret-*` + `keycloak-backend-secret` (admin API, above).
3. `dags-signing-key` (safe random).
4. `livekit-api-secret` (+ `livekit-keys`), roll the three LiveKit consumers.
5. Leave `vito-hmac-pepper` / `mushex-hmac-pepper` unless you run the data migration.
6. Re-run `scripts/secrets/bootstrap-secrets.sh` on any fresh cluster first so it
   generates strong randoms rather than inheriting preview values.
