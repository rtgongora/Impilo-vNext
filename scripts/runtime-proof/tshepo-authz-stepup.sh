#!/usr/bin/env bash
#
# Runtime proof for the TSHEPO step-up engine (Wave B / B1, gap G001).
#
# Boots the FULL tshepo-authz Spring context against a real, ephemeral Postgres,
# applies every Flyway migration, and drives StepUpVerificationIT end-to-end
# (issue -> supervisor approve -> verify -> complete; fail-closed without approval;
# attempt-count lockout; replay rejection). This is the artifact behind the
# REAL_PROVEN claim for the supervisor-approval path in
# docs/audits/product-truth-full-gap-register.md (G001, G060, G061).
#
# Why not Testcontainers here: this environment's bundled docker-java client cannot
# negotiate the engine's minimum Docker API version, so we start Postgres via the
# Docker CLI (which works) and point the IT at it with -Dit.pg.url.
#
# Usage:  scripts/runtime-proof/tshepo-authz-stepup.sh
# Exit:   0 = proof green; non-zero = a defect is present (or Docker unavailable).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
NAME="tshepo-it-pg-$$"
PORT="${IT_PG_PORT:-55432}"
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

echo "==> running StepUpVerificationIT against the real database"
mvn -o -f "$REPO_ROOT/services/tshepo-authz-service/pom.xml" clean test \
  -Dtest=StepUpVerificationIT \
  -Dit.pg.url="jdbc:postgresql://127.0.0.1:${PORT}/test" \
  -Dit.pg.user=test -Dit.pg.pass=test \
  -Dsurefire.failIfNoSpecifiedTests=false

echo "==> runtime proof PASSED"
