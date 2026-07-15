#!/usr/bin/env bash
# ============================================================================
# Operating-theatre trust/scope/safety AUTHZ MATRIX — runtime proof (Wave 6 §16).
#
# THE POINT: the theatre §16 authz matrix (V029 perioperative + V030 clinical-safety
# + V034 completion) is enforced by the REAL tshepo-authz PolicyEngine, and the V034
# rules apply cleanly on a real Postgres via Flyway (a unit test cannot catch a bad
# migration; this rig can). It boots the actual tshepo-authz-service against a rig
# Postgres + Redis and asserts:
#
#   J-TA-1  Flyway applied V034 on a real Postgres and the service booted healthy.
#   J-TA-2  The six V034 theatre §16 ALLOW families are present in tshepo_authz.policy_rule
#           (checklist sign, anaesthesia scores, discharge, discharge/complete,
#            return-to-theatre, emergency case, emergency consent-exception).
#   J-TA-3  Every V034 emergency-surface rule carries the min_loa assurance floor.
#   J-TA-4  /v1/authorize DENIES (403) a request that is missing mandatory trust
#           headers (fail-closed) — the HTTP deny path is live.
#   J-TA-5  /v1/authorize DENIES (403) a CITIZEN actor reaching a theatre path with a
#           valid header set but no PROVIDER-scoped ALLOW (NO_ALLOW_RULE) — the
#           correct-cadre-only negative that is the point of §16.
#
# NOTE: the full HTTP ALLOW path needs a validated JWT session (roles come from the
# bearer token, not client headers — TPL-1 anti-impersonation). Standing up Keycloak
# is out of scope for this rig; the ALLOW-side of the matrix is proven live through the
# real PolicyEngine in TheatreAuthzMatrixTest (18/18). This rig proves the migration,
# boot, rule presence and the HTTP fail-closed deny path.
#
# Requirements: docker, java 21, packaged jar for tshepo-authz-service:
#   mvn -f services/pom.xml -pl tshepo-authz-service -am package -DskipTests
# Usage: EV=<dir> bash scripts/runtime-proof/theatre-authz-journeys.sh
#        KEEP_RIG=1 to leave infra + service running for inspection.
# ============================================================================
set -u
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
EV=${EV:-$REPO/reports/journeys/theatre-authz-proof-$(date +%Y-%m-%d)}
RIGLOG=${RIGLOG:-/tmp/theatre-authz-logs}
mkdir -p "$EV" "$RIGLOG"
TEN=00000000-0000-0000-0000-000000000001
FAC=00000000-0000-4000-8000-0000000000fa
PGPORT=15787; RPORT=16542; PORT=28081
BASE=http://localhost:$PORT
PASS=0; FAIL=0
ok(){ echo "   PASS: $1" | tee -a "$EV/journal.txt"; PASS=$((PASS+1)); }
bad(){ echo "   FAIL: $1" | tee -a "$EV/journal.txt"; FAIL=$((FAIL+1)); }
say(){ echo "== $1" | tee -a "$EV/journal.txt"; }
SQL(){ docker exec ta-rig-pg psql -U impilo -d tshepo_authz -tAc "$1" 2>/dev/null; }

command -v docker >/dev/null || { echo "docker not available — rig cannot run"; exit 2; }
JAR="$(ls "$REPO/services/tshepo-authz-service/target/tshepo-authz-service-"*.jar 2>/dev/null | grep -v original | head -1)"
[ -n "$JAR" ] || { echo "tshepo-authz-service jar missing — run: mvn -f services/pom.xml -pl tshepo-authz-service -am package -DskipTests"; exit 2; }

cleanup(){ [ "${KEEP_RIG:-0}" = "1" ] && return; kill "${SVC_PID:-0}" 2>/dev/null; docker rm -f ta-rig-pg ta-rig-redis >/dev/null 2>&1; }
trap cleanup EXIT

# ── Infra ────────────────────────────────────────────────────────────────────
say "RIG: infra (postgres + redis) + tshepo-authz-service (real PolicyEngine)"
docker rm -f ta-rig-pg ta-rig-redis >/dev/null 2>&1
docker run -d --name ta-rig-pg -e POSTGRES_USER=impilo -e POSTGRES_PASSWORD=impilo -p $PGPORT:5432 postgres:16-alpine >/dev/null
docker run -d --name ta-rig-redis -p $RPORT:6379 redis:7-alpine >/dev/null
sleep 8
docker exec ta-rig-pg psql -U impilo -d postgres -c "CREATE DATABASE tshepo_authz" >/dev/null 2>&1

# ── Boot the service with Flyway ENABLED (real clean-boot proof) ──────────────
# The duplicate V030 (theatre clinical-safety vs encounter-form) that previously forced a
# psql-apply + Flyway-disabled workaround is now FIXED (clinical-safety renumbered
# V030 -> V035), so Flyway auto-migrates every version on a FRESH DB. A healthy boot IS the
# proof the collision is gone and V034/V035 SQL is valid on real Postgres.
say "BOOT: tshepo-authz (Flyway ENABLED — clean auto-migrate on a fresh DB)"
POSTGRES_HOST=localhost POSTGRES_PORT=$PGPORT POSTGRES_USER=impilo POSTGRES_PASSWORD=impilo \
  REDIS_HOST=localhost REDIS_PORT=$RPORT \
  KAFKA_BOOTSTRAP_SERVERS=localhost:59092 \
  SERVER_PORT=$PORT \
  java -jar "$JAR" > "$RIGLOG/tshepo.log" 2>&1 &
SVC_PID=$!

# Wait for health (up to ~90s)
booted=0
for i in $(seq 1 45); do
  if curl -sf "$BASE/actuator/health" >/dev/null 2>&1; then booted=1; break; fi
  kill -0 "$SVC_PID" 2>/dev/null || { echo "service exited early — see $RIGLOG/tshepo.log"; break; }
  sleep 2
done

# J-TA-1 boot (Flyway auto-migrated a fresh DB — proves no duplicate-version collision)
if [ "$booted" = "1" ]; then ok "J-TA-1 tshepo-authz booted healthy — Flyway applied all migrations clean (no dup V030)"
else bad "J-TA-1 tshepo-authz did not become healthy"; tail -40 "$RIGLOG/tshepo.log" | tee -a "$EV/journal.txt"; echo "PASS=$PASS FAIL=$FAIL"; exit 1; fi

# J-TA-2 the six V034 ALLOW families present
say "J-TA-2 V034 theatre §16 ALLOW families present"
for name in theatre-checklist-sign-nurse theatre-anaesthesia-scores-anaesthetist \
            theatre-discharge-surgeon theatre-discharge-complete-clinician \
            theatre-return-surgeon theatre-emergency-case-surgeon theatre-consent-exception-surgeon; do
  n="$(SQL "SELECT count(*) FROM tshepo_authz.policy_rule WHERE name='$name' AND active=true")"
  [ "$n" = "1" ] && ok "J-TA-2 rule present: $name" || bad "J-TA-2 rule MISSING: $name (count=$n)"
done

# J-TA-3 emergency-surface rules carry the min_loa assurance floor
say "J-TA-3 emergency-surface rules carry min_loa floor"
MINLOA="$(SQL "SELECT count(*) FROM tshepo_authz.policy_rule WHERE name LIKE 'theatre-emergency-%' OR name LIKE 'theatre-consent-exception-%'")"
MINLOA_OK="$(SQL "SELECT count(*) FROM tshepo_authz.policy_rule WHERE (name LIKE 'theatre-emergency-%' OR name LIKE 'theatre-consent-exception-%') AND conditions::text LIKE '%min_loa%'")"
[ -n "$MINLOA" ] && [ "$MINLOA" = "$MINLOA_OK" ] && ok "J-TA-3 all $MINLOA emergency-surface rules carry min_loa" \
  || bad "J-TA-3 min_loa floor missing on some emergency-surface rules ($MINLOA_OK/$MINLOA)"

# J-TA-4 fail-closed: missing mandatory headers -> 403
say "J-TA-4 HTTP deny path — missing mandatory trust headers"
CODE="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/v1/authorize" \
  -H ":path:/internal/v1/theatre/cases/case-1/start" -H ":method:POST")"
[ "$CODE" = "403" ] && ok "J-TA-4 missing-headers request DENIED 403 (fail-closed)" \
  || bad "J-TA-4 expected 403 for missing headers, got $CODE"

# J-TA-5 CITIZEN on a theatre path with a full header set -> 403 (NO_ALLOW_RULE)
say "J-TA-5 HTTP deny path — CITIZEN reaching a theatre endpoint (correct-cadre-only)"
CODE="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/v1/authorize" \
  -H ":path:/internal/v1/theatre/cases/case-1/start" -H ":method:POST" \
  -H "X-Tenant-ID:$TEN" -H "X-Actor-ID:citizen-1" -H "X-Actor-Type:CITIZEN" \
  -H "X-Purpose-Of-Use:TREATMENT" -H "X-Facility-ID:$FAC" -H "X-Correlation-ID:$(uuidgen)")"
[ "$CODE" = "403" ] && ok "J-TA-5 CITIZEN theatre request DENIED 403 (no PROVIDER-scoped ALLOW)" \
  || bad "J-TA-5 expected 403 for CITIZEN theatre request, got $CODE"

# ── Verdict ──────────────────────────────────────────────────────────────────
echo "" | tee -a "$EV/journal.txt"
say "THEATRE-AUTHZ §16 RIG RESULT: PASS=$PASS FAIL=$FAIL"
cp "$RIGLOG/tshepo.log" "$EV/tshepo.log" 2>/dev/null
[ "$FAIL" = "0" ] && exit 0 || exit 1
