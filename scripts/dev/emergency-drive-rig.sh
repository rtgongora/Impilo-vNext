#!/usr/bin/env bash
# Boot the local estate the emergency browser drive runs against — LOCAL DEVELOPMENT ONLY.
#
# The W15 spec (ui/one-ui-shell/e2e/emergency-pack-w15.spec.ts) mocks nothing in the emergency
# domain: it drives the real surfaces against a real chain, so an episode opened by the spec is a
# row in pct.emergency_episode. That is the point — the two defects this wave fixed (a jsonb column
# bound as a varchar, and a route guard acting before the session had been read) were both
# invisible to unit tests and to any mocked drive.
#
#   next dev ──▶ browser-drive-identity-harness ──▶ experience-bff ──┬─▶ pct-service ─────────┐
#    :3007            :8161                            :8160         └─▶ mental-health-service │
#                                                        │                                     ▼
#                                                        └────────────▶ redis            postgres
#
# The databases are throwaway containers on non-default ports, so this never touches another lane's
# rig or a shared instance. The credentials below are local-only dev values for a container this
# script creates and destroys; nothing here reads or writes a real secret.
#
# Usage:
#   bash scripts/dev/emergency-drive-rig.sh up      # boot everything, wait for readiness
#   bash scripts/dev/emergency-drive-rig.sh down    # stop the services and remove the containers
#   bash scripts/dev/emergency-drive-rig.sh status  # what is up and what is not
#
# Then:
#   cd ui/one-ui-shell && PLAYWRIGHT_SKIP_WEBSERVER=1 PLAYWRIGHT_BASE_URL=http://localhost:3007 \
#     npx playwright test e2e/emergency-pack-w15.spec.ts --project=chromium
set -euo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

PG_CONTAINER="${DRIVE_PG_CONTAINER:-em-drive-pg}"
REDIS_CONTAINER="${DRIVE_REDIS_CONTAINER:-em-drive-redis}"
PG_PORT="${DRIVE_PG_PORT:-15991}"
REDIS_PORT="${DRIVE_REDIS_PORT:-16991}"
PG_USER="${DRIVE_PG_USER:-impilo}"
PG_PASSWORD="${DRIVE_PG_PASSWORD:-localdrive}"

PCT_PORT="${DRIVE_PCT_PORT:-29388}"
MH_PORT="${DRIVE_MH_PORT:-29397}"
BFF_PORT="${DRIVE_BFF_PORT:-8160}"
HARNESS_PORT="${DRIVE_HARNESS_PORT:-8161}"
NEXT_PORT="${DRIVE_NEXT_PORT:-3007}"

LOG_DIR="${DRIVE_LOG_DIR:-/tmp/impilo-emergency-drive}"

say() { printf '%s\n' "$*"; }

wait_for_http() {
  local url="$1" name="$2" attempts="${3:-90}" i
  for ((i = 1; i <= attempts; i++)); do
    if curl -fsS -o /dev/null --max-time 2 "$url" 2>/dev/null; then
      say "  ready: $name"
      return 0
    fi
    sleep 2
  done
  say "  NOT READY: $name ($url)"
  return 1
}

start_databases() {
  if ! docker ps --format '{{.Names}}' | grep -qx "$PG_CONTAINER"; then
    docker rm -f "$PG_CONTAINER" >/dev/null 2>&1 || true
    say "starting postgres ($PG_CONTAINER on $PG_PORT)"
    docker run -d --name "$PG_CONTAINER" \
      -e POSTGRES_USER="$PG_USER" \
      -e POSTGRES_PASSWORD="$PG_PASSWORD" \
      -e POSTGRES_DB=postgres \
      -p "127.0.0.1:${PG_PORT}:5432" \
      postgres:16-alpine >/dev/null

    # Each service owns its own database, as it does in the estate. Flyway builds the schema on
    # first boot, so this is a genuine full-migration-chain run rather than a restored snapshot.
    #
    # Retried rather than gated on pg_isready: the official image answers ready during its own
    # bootstrap and then restarts the server, so a CREATE issued on that first window dies with
    # "terminating connection due to administrator command".
    local db i
    for db in pct mental_health; do
      for ((i = 1; i <= 60; i++)); do
        if docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d postgres \
             -c "CREATE DATABASE $db" >/dev/null 2>&1; then
          break
        fi
        if docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$db" -c 'SELECT 1' >/dev/null 2>&1; then
          break
        fi
        sleep 1
      done
    done
  else
    say "postgres already up ($PG_CONTAINER)"
  fi

  if ! docker ps --format '{{.Names}}' | grep -qx "$REDIS_CONTAINER"; then
    docker rm -f "$REDIS_CONTAINER" >/dev/null 2>&1 || true
    # The BFF's idempotency filter reads Redis on every mutating request. Without it every POST
    # 500s before it reaches a controller, which reads in the UI as "the write failed" and says
    # nothing about why.
    say "starting redis ($REDIS_CONTAINER on $REDIS_PORT)"
    docker run -d --name "$REDIS_CONTAINER" -p "127.0.0.1:${REDIS_PORT}:6379" redis:7-alpine >/dev/null
  else
    say "redis already up ($REDIS_CONTAINER)"
  fi
}

require_jar() {
  local jar="$1" module="$2"
  if [[ ! -f "$jar" ]]; then
    say "missing $jar"
    say "  build it first:  cd services && mvn -q -pl $module -am -DskipTests package"
    return 1
  fi
}

start_services() {
  mkdir -p "$LOG_DIR"

  require_jar services/pct-service/target/pct-service-0.1.0-SNAPSHOT.jar pct-service
  require_jar services/mental-health-service/target/mental-health-service-0.1.0-SNAPSHOT.jar mental-health-service
  require_jar services/experience-bff/target/experience-bff-0.1.0-SNAPSHOT.jar experience-bff

  if ! curl -fsS -o /dev/null --max-time 2 "http://localhost:${PCT_PORT}/actuator/health" 2>/dev/null; then
    say "starting pct-service ($PCT_PORT)"
    # pct defaults REDIS_PORT to 6379; without this it hangs forever trying a redis that is not
    # there, and the health endpoint never answers — which is how the previous boot looked like a
    # postgres problem when the database was fine.
    #
    # disable-oauth-for-tests: this box has no Keycloak. Without it every /v1/** call 401s before
    # a controller runs, which the BFF wraps as "PCT … failed" and the UI reports as a retrieval
    # failure — the same shape as a real outage, and indistinguishable from one in a screenshot.
    POSTGRES_HOST=localhost POSTGRES_PORT="$PG_PORT" POSTGRES_USER="$PG_USER" POSTGRES_PASSWORD="$PG_PASSWORD" \
      REDIS_HOST=localhost REDIS_PORT="$REDIS_PORT" \
      nohup java -jar services/pct-service/target/pct-service-0.1.0-SNAPSHOT.jar \
      --server.port="$PCT_PORT" \
      --impilo.security.disable-oauth-for-tests=true \
      > "$LOG_DIR/pct.log" 2>&1 &
  fi

  if ! curl -fsS -o /dev/null --max-time 2 "http://localhost:${MH_PORT}/actuator/health" 2>/dev/null; then
    say "starting mental-health-service ($MH_PORT)"
    POSTGRES_HOST=localhost POSTGRES_PORT="$PG_PORT" POSTGRES_USER="$PG_USER" POSTGRES_PASSWORD="$PG_PASSWORD" \
      REDIS_HOST=localhost REDIS_PORT="$REDIS_PORT" \
      nohup java -jar services/mental-health-service/target/mental-health-service-0.1.0-SNAPSHOT.jar \
      --server.port="$MH_PORT" \
      --impilo.security.disable-oauth-for-tests=true \
      > "$LOG_DIR/mental-health.log" 2>&1 &
  fi

  wait_for_http "http://localhost:${PCT_PORT}/actuator/health" "pct-service"
  wait_for_http "http://localhost:${MH_PORT}/actuator/health" "mental-health-service"

  if ! curl -fsS -o /dev/null --max-time 2 "http://localhost:${BFF_PORT}/actuator/health" 2>/dev/null; then
    say "starting experience-bff ($BFF_PORT)"
    # allow-anonymous plus the excluded resource server: this box has no Keycloak, and the drive is
    # about the emergency surfaces, not about re-proving the token path. The BFF's own tokenless-401
    # tests cover that, and they run in CI where this rig does not.
    ( cd services/experience-bff && \
      PCT_BASE_URL="http://localhost:${PCT_PORT}" \
      MENTAL_HEALTH_BASE_URL="http://localhost:${MH_PORT}" \
      nohup java -jar target/experience-bff-0.1.0-SNAPSHOT.jar \
        --server.port="$BFF_PORT" \
        --impilo.security.allow-anonymous=true \
        --spring.data.redis.port="$REDIS_PORT" \
        --spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration \
        > "$LOG_DIR/experience-bff.log" 2>&1 & )
  fi
  wait_for_http "http://localhost:${BFF_PORT}/actuator/health" "experience-bff" 120

  if ! curl -fsS -o /dev/null --max-time 2 "http://localhost:${HARNESS_PORT}/internal/v1/identity/linked-ids" 2>/dev/null; then
    say "starting identity harness ($HARNESS_PORT)"
    UPSTREAM="http://localhost:${BFF_PORT}" PORT="$HARNESS_PORT" \
      nohup node scripts/dev/browser-drive-identity-harness.mjs > "$LOG_DIR/harness.log" 2>&1 &
  fi
  wait_for_http "http://localhost:${HARNESS_PORT}/internal/v1/identity/linked-ids" "identity harness"

  if ! curl -fsS -o /dev/null --max-time 2 "http://localhost:${NEXT_PORT}/" 2>/dev/null; then
    say "starting next dev ($NEXT_PORT)"
    ( cd ui/one-ui-shell && \
      API_GATEWAY_URL="http://localhost:${HARNESS_PORT}" \
      nohup npx next dev --port "$NEXT_PORT" > "$LOG_DIR/next.log" 2>&1 & )
  fi
  wait_for_http "http://localhost:${NEXT_PORT}/" "next dev" 120
}

status() {
  local url name
  for entry in \
    "http://localhost:${PCT_PORT}/actuator/health|pct-service" \
    "http://localhost:${MH_PORT}/actuator/health|mental-health-service" \
    "http://localhost:${BFF_PORT}/actuator/health|experience-bff" \
    "http://localhost:${HARNESS_PORT}/internal/v1/identity/linked-ids|identity harness" \
    "http://localhost:${NEXT_PORT}/|next dev"; do
    url="${entry%%|*}"; name="${entry##*|}"
    if curl -fsS -o /dev/null --max-time 2 "$url" 2>/dev/null; then
      say "  up   $name"
    else
      say "  down $name"
    fi
  done
  say "logs: $LOG_DIR"
}

down() {
  # Kill by listening port, never by jar-name pattern. A `pkill -f` of the jar path also matches
  # the shell that is about to start it (the command line contains the same string), which is how
  # an earlier boot silently killed itself before java ever ran.
  fuser -k "${PCT_PORT}/tcp" 2>/dev/null || true
  fuser -k "${MH_PORT}/tcp" 2>/dev/null || true
  fuser -k "${BFF_PORT}/tcp" 2>/dev/null || true
  fuser -k "${HARNESS_PORT}/tcp" 2>/dev/null || true
  docker rm -f "$PG_CONTAINER" "$REDIS_CONTAINER" >/dev/null 2>&1 || true
  say "stopped the java services and removed the containers"
  say "next dev on ${NEXT_PORT} is left running; stop it yourself if you want the port back"
}

case "${1:-up}" in
  up)
    start_databases
    start_services
    say
    status
    ;;
  down) down ;;
  status) status ;;
  *)
    say "usage: $0 [up|down|status]"
    exit 2
    ;;
esac
