#!/usr/bin/env bash
#
# Runtime proof for the TSHEPO keys-service signing contract (Wave B / a+G059).
#
# Boots the FULL tshepo-keys context against a real, ephemeral Postgres, provisions an
# OFFLINE_CAPABILITY key, calls the real POST /v1/sign with the aligned DTO, and
# cryptographically verifies the returned JWS against the live JWKS (plus the fail-closed
# path for a sensitive purpose with no key). This is the artifact behind the G059 fix and
# the purpose-scoped-key threading (fix-a) in docs/audits/product-truth-full-gap-register.md.
#
# Testcontainers is intentionally not used (this environment's docker-java cannot negotiate
# the engine's minimum Docker API version) — Postgres is started via the Docker CLI.
#
# Usage:  scripts/runtime-proof/tshepo-keys-signing.sh
# Exit:   0 = proof green; non-zero = a defect is present (or Docker unavailable).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
NAME="tshepo-keys-it-pg-$$"
PORT="${IT_PG_PORT:-55433}"
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

echo "==> running SigningRuntimeProofIT against the real database"
mvn -o -f "$REPO_ROOT/services/tshepo-keys-service/pom.xml" clean test \
  -Dtest=SigningRuntimeProofIT \
  -Dit.pg.url="jdbc:postgresql://127.0.0.1:${PORT}/test" \
  -Dit.pg.user=test -Dit.pg.pass=test \
  -Dsurefire.failIfNoSpecifiedTests=false

echo "==> runtime proof PASSED"
