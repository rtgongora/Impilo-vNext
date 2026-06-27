#!/usr/bin/env bash
#
# Runtime proof for the DAGS permit engine (Wave B / B3, gaps G003 + G056).
#
# Boots the full DAGS context against a real, ephemeral Postgres, applies the Flyway
# migrations (V001 + V002 permit_replay + widened permit_token), and proves issue ->
# verify -> replay-reject and tamper-reject end-to-end, with the nonce consumed in the
# database. This is the artifact behind the B3 closure in
# docs/audits/product-truth-full-gap-register.md.
#
# Testcontainers is intentionally not used (this environment's docker-java cannot negotiate
# the engine's minimum Docker API version) — Postgres is started via the Docker CLI.
#
# Usage:  scripts/runtime-proof/dags-permit-enforcement.sh
# Exit:   0 = proof green; non-zero = a defect is present (or Docker unavailable).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
NAME="dags-it-pg-$$"
PORT="${IT_PG_PORT:-55434}"
IMAGE="postgres:16-alpine"

cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT

if ! docker info >/dev/null 2>&1; then
  echo "Docker engine not reachable — cannot run the runtime proof." >&2
  exit 2
fi

echo "==> starting $IMAGE as $NAME on :$PORT"
docker run -d --name "$NAME" \
  -e POSTGRES_DB=test -e POSTGRES_USER=test -e POSTGRES_PASSWORD=test \
  -p "${PORT}:5432" "$IMAGE" >/dev/null

echo "==> waiting for Postgres to accept connections"
ready=no
for _ in $(seq 1 90); do
  if docker exec "$NAME" pg_isready -U test -d test 2>/dev/null | grep -q 'accepting connections'; then
    ready=yes; break
  fi
  sleep 1
done
if [ "$ready" != yes ]; then
  echo "Postgres never became ready:" >&2
  docker logs "$NAME" 2>&1 | tail -20 >&2
  exit 3
fi

echo "==> running PermitEnforcementRuntimeProofIT against the real database"
mvn -o -f "$REPO_ROOT/services/data-access-governance-service/pom.xml" clean test \
  -Dtest=PermitEnforcementRuntimeProofIT \
  -Dit.pg.url="jdbc:postgresql://127.0.0.1:${PORT}/test" \
  -Dit.pg.user=test -Dit.pg.pass=test \
  -Dsurefire.failIfNoSpecifiedTests=false

echo "==> runtime proof PASSED"
