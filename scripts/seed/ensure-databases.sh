#!/bin/sh
# Idempotent CREATE DATABASE for Impilo vNext core schemas.
# Use when an existing Postgres volume predates a new service DB, or run from CI.
# Env: PGHOST (default postgres), POSTGRES_USER, POSTGRES_PASSWORD

set -e
export PGPASSWORD="${POSTGRES_PASSWORD:-changeme}"
HOST="${PGHOST:-postgres}"
USER="${POSTGRES_USER:-impilo}"

until pg_isready -h "$HOST" -U "$USER" -q; do
  echo "ensure-databases: waiting for $HOST..."
  sleep 1
done

for db in experience_bff tshepo vito varapi tuso zibo pct oros pharmacy impilo_learning impilo_khuluma live; do
  exists=$(psql -h "$HOST" -U "$USER" -d postgres -Atqc "SELECT 1 FROM pg_database WHERE datname='$db'" || true)
  if [ "$exists" = "1" ]; then
    echo "ensure-databases: $db exists"
  else
    echo "ensure-databases: creating $db"
    psql -h "$HOST" -U "$USER" -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE $db;"
  fi
done

echo "ensure-databases: complete"
