#!/usr/bin/env bash
set -Eeuo pipefail

# Disposable H2-export -> Keycloak 25/PostgreSQL -> Keycloak 26.7 rehearsal.
# It never connects to preview databases and retains its dump + evidence locally.

SOURCE_EXPORT="${1:?usage: $0 /path/to/export-directory}"
EXPECTED_USERS="${EXPECTED_USERS:-42}"
KC25_IMAGE="${KEYCLOAK_25_IMAGE:-quay.io/keycloak/keycloak:25.0}"
KC26_IMAGE="${KEYCLOAK_26_IMAGE:-quay.io/keycloak/keycloak:26.7.0}"
PG_IMAGE="${KEYCLOAK_POSTGRES_IMAGE:-postgres:16-alpine}"
STAMP="${MIGRATION_STAMP:-$(date -u +%Y%m%dT%H%M%SZ)}"
EVIDENCE_ROOT="${KEYCLOAK_REHEARSAL_ROOT:-$HOME/impilo-backups/keycloak/rehearsals}"
WORK="$EVIDENCE_ROOT/$STAMP"
NETWORK="impilo-kc-rehearsal-$STAMP"
PG="impilo-kc-pg-$STAMP"
DB_PASSWORD="$(openssl rand -hex 24)"

for command in docker jq sha256sum openssl gpg; do
  command -v "$command" >/dev/null || { echo "missing command: $command" >&2; exit 69; }
done
[[ -d "$SOURCE_EXPORT" ]] || { echo "source export is not a directory: $SOURCE_EXPORT" >&2; exit 66; }
[[ ! -e "$WORK" ]] || { echo "rehearsal evidence path already exists: $WORK" >&2; exit 65; }
install -d -m 0700 "$WORK"
install -d -m 0777 "$WORK/kc25-export" "$WORK/kc25-restore-export" "$WORK/kc26-export"

cleanup() {
  docker rm -f "$PG" >/dev/null 2>&1 || true
  docker network rm "$NETWORK" >/dev/null 2>&1 || true
}
trap cleanup EXIT

realm_file() {
  grep -l -m1 '"realm"[[:space:]]*:[[:space:]]*"impilo"' "$1"/*.json | head -1
}

signature() {
  local source="$1" target="$2" realm
  realm="$(realm_file "$source")"
  [[ -n "$realm" ]] || { echo "no impilo realm JSON in $source" >&2; exit 70; }
  jq -S '{
    realm,
    users: ((.users // []) | map({
      id, username, enabled, serviceAccountClientId, email, emailVerified, firstName, lastName,
      attributes, realmRoles, clientRoles, requiredActions,
      credentials: ((.credentials // []) | map(select(.type != "password-history")) |
        map({type, credentialData, secretData}) | sort_by(.type))
    }) | sort_by(.id))
  }' "$realm" >"$target"
}

kc_import() {
  docker run --rm --network "$NETWORK" \
    -e KC_DB=postgres \
    -e "KC_DB_URL=jdbc:postgresql://$PG:5432/keycloak" \
    -e KC_DB_USERNAME=keycloak \
    -e "KC_DB_PASSWORD=$DB_PASSWORD" \
    -v "$SOURCE_EXPORT:/migration/import:ro" \
    "$KC25_IMAGE" import --dir /migration/import --override true
}

kc_export() {
  local image="$1" database="$2" host_dir="$3"
  docker run --rm --network "$NETWORK" \
    -e KC_DB=postgres \
    -e "KC_DB_URL=jdbc:postgresql://$PG:5432/$database" \
    -e KC_DB_USERNAME=keycloak \
    -e "KC_DB_PASSWORD=$DB_PASSWORD" \
    -e KC_SPI_CONNECTIONS_JPA__QUARKUS__MIGRATION_STRATEGY=update \
    -v "$host_dir:/migration/export" \
    "$image" export --dir /migration/export --realm impilo --users realm_file
}

echo "PULLING immutable rehearsal dependencies"
docker pull "$KC25_IMAGE" >/dev/null
docker pull "$KC26_IMAGE" >/dev/null
docker pull "$PG_IMAGE" >/dev/null
docker network create "$NETWORK" >/dev/null
docker run -d --name "$PG" --network "$NETWORK" \
  -e POSTGRES_USER=keycloak -e "POSTGRES_PASSWORD=$DB_PASSWORD" -e POSTGRES_DB=keycloak \
  "$PG_IMAGE" >/dev/null
for _ in $(seq 1 60); do
  docker exec "$PG" pg_isready -U keycloak -d keycloak >/dev/null 2>&1 && break
  sleep 2
done
docker exec "$PG" pg_isready -U keycloak -d keycloak >/dev/null

signature "$SOURCE_EXPORT" "$WORK/source.signature.json"
source_users="$(jq '[.users[] | select(.serviceAccountClientId == null)] | length' "$WORK/source.signature.json")"
source_service_accounts="$(jq '[.users[] | select(.serviceAccountClientId != null)] | length' "$WORK/source.signature.json")"
source_realm="$(realm_file "$SOURCE_EXPORT")"
source_password_history="$(jq '[.users[]?.credentials[]? | select(.type == "password-history")] | length' "$source_realm")"
[[ "$source_users" == "$EXPECTED_USERS" ]] || {
  echo "expected $EXPECTED_USERS source users, found $source_users" >&2; exit 70;
}

echo "IMPORTING source into Keycloak 25/PostgreSQL"
kc_import
kc_export "$KC25_IMAGE" keycloak "$WORK/kc25-export"
signature "$WORK/kc25-export" "$WORK/kc25.signature.json"
cmp "$WORK/source.signature.json" "$WORK/kc25.signature.json"

docker exec "$PG" pg_dump -U keycloak -Fc keycloak >"$WORK/keycloak25-preupgrade.dump"
sha256sum "$WORK/keycloak25-preupgrade.dump" >"$WORK/keycloak25-preupgrade.dump.sha256"
docker exec "$PG" createdb -U keycloak -O keycloak rollback
docker exec -i "$PG" pg_restore -U keycloak --no-owner -d rollback <"$WORK/keycloak25-preupgrade.dump"
kc_export "$KC25_IMAGE" rollback "$WORK/kc25-restore-export"
signature "$WORK/kc25-restore-export" "$WORK/kc25-restore.signature.json"
cmp "$WORK/source.signature.json" "$WORK/kc25-restore.signature.json"

echo "UPGRADING disposable PostgreSQL schema with Keycloak 26.7"
kc_export "$KC26_IMAGE" keycloak "$WORK/kc26-export"
signature "$WORK/kc26-export" "$WORK/kc26.signature.json"
cmp "$WORK/source.signature.json" "$WORK/kc26.signature.json"

source_hash="$(sha256sum "$WORK/source.signature.json" | awk '{print $1}')"
dump_hash="$(awk '{print $1}' "$WORK/keycloak25-preupgrade.dump.sha256")"
KEY_ROOT="$EVIDENCE_ROOT/.keys"
install -d -m 0700 "$KEY_ROOT"
key_file="$KEY_ROOT/$STAMP.key"
openssl rand -base64 48 >"$key_file"
chmod 0600 "$key_file"
gpg --batch --yes --pinentry-mode loopback --passphrase-file "$key_file" --cipher-algo AES256 \
  --symmetric --output "$WORK/keycloak25-preupgrade.dump.gpg" "$WORK/keycloak25-preupgrade.dump"
decrypted_hash="$(gpg --batch --quiet --pinentry-mode loopback --passphrase-file "$key_file" \
  --decrypt "$WORK/keycloak25-preupgrade.dump.gpg" | sha256sum | awk '{print $1}')"
[[ "$decrypted_hash" == "$dump_hash" ]] || { echo "encrypted dump verification failed" >&2; exit 70; }
encrypted_dump_hash="$(sha256sum "$WORK/keycloak25-preupgrade.dump.gpg" | awk '{print $1}')"
rm "$WORK/keycloak25-preupgrade.dump"
jq -n \
  --arg stamp "$STAMP" --argjson users "$source_users" --argjson serviceAccounts "$source_service_accounts" \
  --argjson passwordHistory "$source_password_history" \
  --arg sourceHash "$source_hash" --arg dumpHash "$dump_hash" --arg encryptedDumpHash "$encrypted_dump_hash" \
  --arg kc25 "$KC25_IMAGE" --arg kc26 "$KC26_IMAGE" --arg postgres "$PG_IMAGE" \
  '{stamp: $stamp, humanUsers: $users, serviceAccounts: $serviceAccounts,
    sourcePasswordHistoryEntriesNotImportedByKeycloak: $passwordHistory, sourceSignatureSha256: $sourceHash,
    preupgradeDumpContentSha256: $dumpHash, encryptedDumpSha256: $encryptedDumpHash,
    images: {keycloak25: $kc25, keycloak26: $kc26, postgres: $postgres},
    gates: {kc25Import: "PASS", postgresRestore: "PASS", kc267Upgrade: "PASS", userCredentialParity: "PASS"}}
  ' >"$WORK/evidence.json"
chmod 0700 "$WORK"
chmod go-rwx "$WORK"/*.json "$WORK"/*.gpg "$WORK"/*.sha256
echo "REHEARSAL_OK human_users=$source_users service_accounts=$source_service_accounts evidence=$WORK/evidence.json encrypted_dump=$WORK/keycloak25-preupgrade.dump.gpg"
