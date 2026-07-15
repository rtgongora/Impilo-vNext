#!/usr/bin/env bash
# Gateway BE-additions runtime-proof rig.
# Boots daidzai + guidance + experience-bff HEAD jars against scratch infra on UNIQUE ports so it
# can run alongside other rigs. Cached images only (postgres:16-alpine, redis:7-alpine).
#
# Infra (docker):  gw-be-rig-pg (postgres:16-alpine :15933), gw-be-rig-redis (redis:7-alpine :16699)
# Service ports:   daidzai 28492, guidance 28560, experience-bff 28562
#
# All three services boot with oauth disabled (IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true) — the
# rig proves the BE additions' functional wiring end-to-end (callback worklist + verify via the
# AUTHENTICATED /internal/v1/daidzai lane, public status-by-reference + health-info text search via
# the /internal/v1/public/gateway permitAll lanes), not the estate PDP authz (proven in each
# service's own suite; permitAll-genuineness for the public lanes is covered by the w2c rig + the
# check-public-lane guard). serviceAccountBearer() returns null (no Keycloak) so the S2S hop is
# header-only. Kafka is absent: outbox sends fail-and-retry (non-fatal).
set -u
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
LOGS="${RIG_LOGS:-/tmp/gw-be-rig-logs}"
mkdir -p "$LOGS"

PG=gw-be-rig-pg
REDIS=gw-be-rig-redis
PG_PORT=15933
REDIS_PORT=16699
DAIDZAI_PORT=28492
GUIDANCE_PORT=28560
BFF_PORT=28562

echo "[rig] starting scratch infra…"
docker rm -f "$PG" "$REDIS" >/dev/null 2>&1 || true
docker run -d --name "$PG" -p ${PG_PORT}:5432 \
  -e POSTGRES_USER=impilo -e POSTGRES_PASSWORD=impilo -e POSTGRES_DB=daidzai \
  postgres:16-alpine >/dev/null
docker run -d --name "$REDIS" -p ${REDIS_PORT}:6379 redis:7-alpine >/dev/null

echo "[rig] waiting for postgres…"
for i in $(seq 1 30); do
  docker exec "$PG" pg_isready -U impilo >/dev/null 2>&1 && break
  sleep 1
done
# Second database for guidance-service (daidzai + guidance share one postgres container).
# The official postgres image reports pg_isready on a temporary init server before the real
# server restarts, so a single CREATE DATABASE can race and be lost — retry until it sticks.
for i in $(seq 1 20); do
  docker exec "$PG" psql -U impilo -d daidzai -c "CREATE DATABASE guidance;" >/dev/null 2>&1
  if docker exec "$PG" psql -U impilo -d daidzai -tAc \
       "SELECT 1 FROM pg_database WHERE datname='guidance';" 2>/dev/null | grep -q 1; then
    echo "[rig] guidance database ready"; break
  fi
  sleep 1
done

boot() { # name jar port extra_env...
  local name=$1 jar=$2 port=$3; shift 3
  env "$@" nohup java -jar "$jar" --server.port="$port" >"$LOGS/$name.log" 2>&1 &
  echo "$name pid=$! port=$port log=$LOGS/$name.log"
}

echo "[rig] booting daidzai…"
boot daidzai "$ROOT/services/daidzai-service/target/daidzai-service-0.1.0-SNAPSHOT.jar" $DAIDZAI_PORT \
  DB_HOST=localhost DB_PORT=$PG_PORT DB_NAME=daidzai POSTGRES_USER=impilo POSTGRES_PASSWORD=impilo \
  IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true \
  KAFKA_BOOTSTRAP_SERVERS=localhost:19199

echo "[rig] booting guidance…"
boot guidance "$ROOT/services/guidance-service/target/guidance-service-0.1.0-SNAPSHOT.jar" $GUIDANCE_PORT \
  DB_HOST=localhost DB_PORT=$PG_PORT DB_NAME=guidance POSTGRES_USER=impilo POSTGRES_PASSWORD=impilo \
  IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true \
  KAFKA_BOOTSTRAP_SERVERS=localhost:19199

echo "[rig] booting experience-bff…"
boot bff "$ROOT/services/experience-bff/target/experience-bff-0.1.0-SNAPSHOT.jar" $BFF_PORT \
  REDIS_HOST=localhost REDIS_PORT=$REDIS_PORT \
  IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true \
  DAIDZAI_BASE_URL=http://localhost:$DAIDZAI_PORT \
  GUIDANCE_BASE_URL=http://localhost:$GUIDANCE_PORT

wait_health() { # url name
  local url=$1 name=$2
  for i in $(seq 1 90); do
    if curl -fsS "$url" >/dev/null 2>&1; then echo "[rig] $name healthy"; return 0; fi
    sleep 2
  done
  echo "[rig] $name FAILED to become healthy — see $LOGS/$name.log"; return 1
}

wait_health "http://localhost:$DAIDZAI_PORT/actuator/health" daidzai
wait_health "http://localhost:$GUIDANCE_PORT/actuator/health" guidance
wait_health "http://localhost:$BFF_PORT/actuator/health" bff
echo "[rig] boot complete."
