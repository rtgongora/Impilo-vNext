#!/usr/bin/env bash
# BUTANO data must never be deleted on any redeploy or boot.
#
# BUTANO is the shared health record: the longitudinal clinical record for the nation, and the one
# store whose loss cannot be reconstructed from anywhere else. Sovereign services can be rebuilt
# from their own migrations and re-ingested; the SHR is the destination, not a projection.
#
# This guard exists because durability that depends on a framework default, or on nobody adding a
# service name to a reset script, is not durability. Each assertion below corresponds to a way the
# record could be emptied without anyone intending it.
set -uo pipefail
cd "$(dirname "$0")/../.."

FAIL=0
note() { printf '  %s\n' "$1"; }
fail() { printf 'FAIL: %s\n' "$1" >&2; FAIL=1; }

echo "=== BUTANO data durability guard ==="

BUTANO_YML=services/butano-service/src/main/resources/application.yml

# 1. Hibernate must never generate or drop schema for the SHR.
if [ -f "$BUTANO_YML" ]; then
  DDL=$(grep -E "^\s*ddl-auto:" "$BUTANO_YML" | head -1 | awk '{print $2}')
  case "$DDL" in
    validate|none) note "ddl-auto=$DDL" ;;
    "")            fail "butano application.yml declares no ddl-auto; it must be validate or none" ;;
    *)             fail "butano ddl-auto=$DDL — create/update/create-drop can destroy the shared health record" ;;
  esac
else
  fail "butano application.yml not found at $BUTANO_YML"
fi

# 2. Flyway clean must be disabled explicitly, not by default.
if grep -qE "^\s*clean-disabled:\s*true" "$BUTANO_YML" 2>/dev/null; then
  note "flyway clean-disabled=true (explicit)"
else
  fail "butano does not set flyway clean-disabled: true explicitly — the SHR must not rely on a framework default"
fi

# 3. No script may drop or truncate the butano database or its HAPI tables.
#    scripts/dr/ is exempt: restoring from a backup legitimately replaces the database, and that
#    is the one supported path for putting the record back.
DESTRUCTIVE=$(grep -rniE "(drop|truncate)[[:space:]]+(database|table)[^;]*butano|dropdb[^|]*butano|drop[[:space:]]+table[^;]*hfj_" \
                scripts/ deploy/ tools/ 2>/dev/null \
              | grep -v "^scripts/dr/" \
              | grep -v "check-butano-data-durability.sh" || true)
if [ -n "$DESTRUCTIVE" ]; then
  fail "a script can drop or truncate BUTANO data:"
  printf '%s\n' "$DESTRUCTIVE" >&2
else
  note "no script drops or truncates butano outside scripts/dr/"
fi

# 4. Postgres must not run on ephemeral storage. An emptyDir returns the SHR to zero on any pod
#    reschedule, which is how this was lost once before (2026-07-18).
PG_TEMPLATE=deploy/helm/impilo-vnext/templates/postgres.yaml
if [ -f "$PG_TEMPLATE" ]; then
  if grep -qE "^\s*persistentVolumeClaim:" "$PG_TEMPLATE"; then
    note "postgres uses a persistentVolumeClaim"
  else
    fail "postgres template does not mount a persistentVolumeClaim — the SHR would not survive a reschedule"
  fi
  if grep -qE "^\s*emptyDir:" "$PG_TEMPLATE"; then
    fail "postgres template still contains an emptyDir volume"
  fi
else
  fail "postgres template not found at $PG_TEMPLATE"
fi

# 5. A destructive full boot must stay double-gated: an env flag AND a typed phrase.
FULLBOOT=scripts/operator/fullboot.sh
if [ -f "$FULLBOOT" ]; then
  if grep -q "FULL_BOOT_WIPE_DATA" "$FULLBOOT" && grep -q "FB_WIPE_PHRASE" "$FULLBOOT"; then
    note "fullboot wipe is double-gated (env flag + typed phrase)"
  else
    fail "fullboot.sh no longer double-gates the data wipe"
  fi
else
  fail "fullboot.sh not found"
fi

if [ "$FAIL" -ne 0 ]; then
  echo "BUTANO data durability guard: FAILED" >&2
  exit 1
fi
echo "BUTANO data durability guard: OK"
