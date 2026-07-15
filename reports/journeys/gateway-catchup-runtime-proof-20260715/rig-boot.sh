#!/usr/bin/env bash
# Gateway UI catch-up runtime-proof rig (Phase E of the gateway hardening follow-ups).
# Boots daidzai + guidance + experience-bff HEAD jars against scratch infra on UNIQUE ports so it
# can run alongside other rigs. Cached images only (postgres:16-alpine, redis:7-alpine).
#
# Infra (docker):  gw-e-rig-pg (postgres:16-alpine :15833), gw-e-rig-redis (redis:7-alpine :16799)
# Service ports:   daidzai 28692, guidance 28660, experience-bff 28760
#
# Auth-lane approach (journalled in journal.md CHECK-0):
#   - daidzai + guidance boot with IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true (functional wiring;
#     estate PDP authz is proven in each service's own suite + the w2c public-lane guard).
#   - experience-bff boots FULLY OPEN: the OAuth2 resource-server autoconfig is excluded so NO
#     JwtDecoder bean is registered, and impilo.security.allow-anonymous=true, so SecurityConfig
#     takes its anyRequest().permitAll() branch. This makes BOTH the public gateway lanes AND the
#     authenticated /internal/v1/daidzai/** operator lane reachable through the BFF in-rig, so the
#     BE-1 callback-verify proxy + worklist are proven THROUGH the BFF (stronger than the BE rig,
#     which could only reach them against the daidzai SoR directly). KEYCLOAK_URL points at a closed
#     port so serviceAccountBearer()/ROPC fail fast to null — the S2S hop stays header-only.
# Kafka is intentionally absent: outbox sends fail-and-retry (non-fatal, lazy KafkaTemplate).
set -u
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
LOGS="${RIG_LOGS:-/tmp/gw-e-rig-logs}"
mkdir -p "$LOGS"

PG=gw-e-rig-pg
REDIS=gw-e-rig-redis
PG_PORT=15833
REDIS_PORT=16799
DAIDZAI_PORT=28692
GUIDANCE_PORT=28660
BFF_PORT=28760

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
  KAFKA_BOOTSTRAP_SERVERS=localhost:19899

echo "[rig] booting guidance…"
boot guidance "$ROOT/services/guidance-service/target/guidance-service-0.1.0-SNAPSHOT.jar" $GUIDANCE_PORT \
  DB_HOST=localhost DB_PORT=$PG_PORT DB_NAME=guidance POSTGRES_USER=impilo POSTGRES_PASSWORD=impilo \
  IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true \
  KAFKA_BOOTSTRAP_SERVERS=localhost:19899

echo "[rig] booting experience-bff (fully open: no JwtDecoder + allow-anonymous)…"
boot bff "$ROOT/services/experience-bff/target/experience-bff-0.1.0-SNAPSHOT.jar" $BFF_PORT \
  REDIS_HOST=localhost REDIS_PORT=$REDIS_PORT \
  SPRING_AUTOCONFIGURE_EXCLUDE=org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration \
  IMPILO_SECURITY_ALLOW_ANONYMOUS=true \
  KEYCLOAK_URL=http://127.0.0.1:1 \
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
