#!/usr/bin/env bash
# Ensure inpatient database exists on experience-db (idempotent).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/compose/experience/docker-compose.yml"

exists="$(docker compose -f "$COMPOSE_FILE" exec -T experience-db \
  psql -U impilo -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = 'inpatient'" 2>/dev/null || true)"

if [ "$exists" != "1" ]; then
  echo "[INFO] Creating database inpatient on experience-db..."
  docker compose -f "$COMPOSE_FILE" exec -T experience-db \
    psql -U impilo -d postgres -c "CREATE DATABASE inpatient OWNER impilo;"
  echo "[OK] Database inpatient created"
else
  echo "[OK] Database inpatient already exists"
fi
