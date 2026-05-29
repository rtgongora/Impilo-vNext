#!/usr/bin/env bash
# Reset sovereign Flyway databases after migration renumbering conflicts.
set -euo pipefail
DB_CONTAINER="${EXPERIENCE_DB_CONTAINER:-experience-experience-db-1}"
for db in surv impilo_nhume impilo_ndila; do
  echo "Resetting database ${db} ..."
  docker exec "${DB_CONTAINER}" psql -U impilo -d postgres -c \
    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '${db}' AND pid <> pg_backend_pid();" >/dev/null || true
  docker exec "${DB_CONTAINER}" psql -U impilo -d postgres -c "DROP DATABASE IF EXISTS \"${db}\";"
  docker exec "${DB_CONTAINER}" psql -U impilo -d postgres -c "CREATE DATABASE \"${db}\" OWNER impilo;"
done
echo "Sovereign Flyway databases reset. Re-run: ./tools/dev/up.sh --sovereign-host --build"
