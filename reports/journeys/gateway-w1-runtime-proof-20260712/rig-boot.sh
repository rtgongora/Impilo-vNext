#!/usr/bin/env bash
# Gateway W1 runtime-proof rig — boots the five HEAD jars against scratch infra.
# Infra (docker): gw-rig-pg (postgres:16-alpine :15733), gw-rig-redis (redis:7-alpine :16499),
# gw-rig-keycloak (keycloak:25.0 :18080, realm-impilo-preview.json imported,
# KC_CLIENT_SECRET_BACKEND=impilo-backend-secret).
# Service ports: tuso 28084, guidance 28260, identity-assurance 28201, notification 28200, bff 28460
# (28160 was taken by an unrelated experience-bff process from the main checkout — do not touch it).
set -u
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
LOGS="${RIG_LOGS:-/tmp/gw-rig-logs}"
mkdir -p "$LOGS"

PG_HOST=localhost PG_PORT=15733
KC=http://localhost:18080

common_env=(
  KEYCLOAK_URL=$KC KEYCLOAK_REALM=impilo
  KAFKA_BOOTSTRAP_SERVERS=localhost:19192
)

boot() { # name jar port extra_env...
  local name=$1 jar=$2 port=$3; shift 3
  env "${common_env[@]}" "$@" nohup java -jar "$jar" --server.port="$port" \
    >"$LOGS/$name.log" 2>&1 &
  echo "$name pid=$! port=$port"
}

boot tuso "$ROOT/services/tuso-service/target/tuso-service-0.1.0-SNAPSHOT.jar" 28084 \
  POSTGRES_HOST=$PG_HOST POSTGRES_PORT=$PG_PORT POSTGRES_USER=impilo POSTGRES_PASSWORD=impilo \
  REDIS_HOST=localhost REDIS_PORT=16499

boot guidance "$ROOT/services/guidance-service/target/guidance-service-0.1.0-SNAPSHOT.jar" 28260 \
  DB_HOST=$PG_HOST DB_PORT=$PG_PORT DB_NAME=guidance DB_USER=impilo DB_PASSWORD=impilo

boot ia "$ROOT/services/identity-assurance-service/target/identity-assurance-service-0.1.0-SNAPSHOT.jar" 28201 \
  POSTGRES_HOST=$PG_HOST POSTGRES_PORT=$PG_PORT POSTGRES_USER=impilo POSTGRES_PASSWORD=impilo

boot notification "$ROOT/services/notification-service/target/notification-service-0.1.0-SNAPSHOT.jar" 28200 \
  POSTGRES_HOST=$PG_HOST POSTGRES_PORT=$PG_PORT POSTGRES_USER=impilo POSTGRES_PASSWORD=impilo

boot bff "$ROOT/services/experience-bff/target/experience-bff-0.1.0-SNAPSHOT.jar" 28460 \
  REDIS_HOST=localhost REDIS_PORT=16499 \
  KEYCLOAK_CLIENT_ID=experience-ui KEYCLOAK_BACKEND_CLIENT_ID=impilo-backend \
  KEYCLOAK_BACKEND_SECRET=impilo-backend-secret \
  TUSO_BASE_URL=http://localhost:28084 GUIDANCE_BASE_URL=http://localhost:28260 \
  IDENTITY_ASSURANCE_BASE_URL=http://localhost:28201 NOTIFICATION_BASE_URL=http://localhost:28200
