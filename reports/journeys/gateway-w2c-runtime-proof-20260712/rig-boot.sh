#!/usr/bin/env bash
# Gateway W2c runtime-proof rig — anonymous emergency SOS intake (PD-3 callback gate).
# Boots daidzai + experience-bff HEAD jars against scratch infra on UNIQUE ports so it can
# run alongside another rig. Cached images only (postgres:16-alpine, redis:7-alpine).
#
# Infra (docker):  gw2c-rig-pg (postgres:16-alpine :15833), gw2c-rig-redis (redis:7-alpine :16599)
# Service ports:   daidzai 28392, experience-bff 28462
#
# daidzai boots with oauth disabled (IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true) — the rig
# proves the SOS flow + PD-3 gate, not daidzai's own PDP authz (proven in daidzai's own suite).
# Kafka is intentionally absent: daidzai's KafkaTemplate is lazy, so outbox sends just fail and
# retry (non-fatal); no redpanda needed. The BFF runs with RBAC active (a lazy JwtDecoder is
# built from an unreachable Keycloak) so the /public/gateway/sos permitAll route is genuinely
# exercised; serviceAccountBearer() returns null (no Keycloak) so the S2S hop is header-only.
set -u
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
LOGS="${RIG_LOGS:-/tmp/gw2c-rig-logs}"
mkdir -p "$LOGS"

PG=gw2c-rig-pg
REDIS=gw2c-rig-redis
PG_PORT=15833
REDIS_PORT=16599
DAIDZAI_PORT=28392
BFF_PORT=28462

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

echo "[rig] booting experience-bff…"
boot bff "$ROOT/services/experience-bff/target/experience-bff-0.1.0-SNAPSHOT.jar" $BFF_PORT \
  REDIS_HOST=localhost REDIS_PORT=$REDIS_PORT \
  DAIDZAI_BASE_URL=http://localhost:$DAIDZAI_PORT

wait_health() { # url name
  local url=$1 name=$2
  for i in $(seq 1 60); do
    if curl -fsS "$url" >/dev/null 2>&1; then echo "[rig] $name healthy"; return 0; fi
    sleep 2
  done
  echo "[rig] $name FAILED to become healthy — see $LOGS/$name.log"; return 1
}

wait_health "http://localhost:$DAIDZAI_PORT/actuator/health" daidzai
wait_health "http://localhost:$BFF_PORT/actuator/health" bff
echo "[rig] boot complete."
