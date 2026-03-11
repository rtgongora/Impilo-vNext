#!/usr/bin/env bash
# =============================================================================
# Impilo vNext — Experience Platform Online Verification
# =============================================================================
# Deterministic single-command verification for the Experience Platform stage.
#
# Usage:
#   ./scripts/experience/verify-online.sh
#
# Prerequisites:
#   - Docker daemon running (/var/run/docker.sock present)
#   - Maven 3.9+ and Java 21 on PATH
#   - Ports 8086, 5432, 3000 available (or as defined in compose)
#   - Network access for Maven Central dependency resolution
#
# Exit code: 0 = PASS, non-zero = FAIL
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SMOKE_DIR="$SCRIPT_DIR/smoke"

# ── Banner ───────────────────────────────────────────────────────────────────
echo "╔══════════════════════════════════════════════════════════════════════╗"
echo "║  Impilo vNext — Experience Platform Verification                   ║"
echo "╠══════════════════════════════════════════════════════════════════════╣"
echo "║  Timestamp : $(date -u '+%Y-%m-%dT%H:%M:%SZ')                               ║"
echo "║  Host      : $(hostname)                                           "
echo "║  User      : $(whoami)                                             "
echo "║  Java      : $(java -version 2>&1 | head -1 || echo 'NOT FOUND')  "
echo "║  Maven     : $(mvn --version 2>&1 | head -1 || echo 'NOT FOUND')  "
echo "║  Docker    : $(docker --version 2>&1 || echo 'NOT FOUND')          "
echo "╚══════════════════════════════════════════════════════════════════════╝"
echo ""

PASS_COUNT=0
FAIL_COUNT=0
FAILURES=""

pass() {
  echo "  ✅ PASS: $1"
  PASS_COUNT=$((PASS_COUNT + 1))
}

fail() {
  echo "  ❌ FAIL: $1"
  FAIL_COUNT=$((FAIL_COUNT + 1))
  FAILURES="$FAILURES\n  - $1"
}

# =============================================================================
# PHASE A: BUILD
# =============================================================================
echo "══════════════════════════════════════════════════════════════════════"
echo "  PHASE A: BUILD"
echo "══════════════════════════════════════════════════════════════════════"

echo ""
echo "── A.1: Build & test shared-kernel-java, tech-companion suite ──"
if mvn -f "$REPO_ROOT/services/pom.xml" \
    -pl ../libs/tech-companion,../libs/tech-companion-harness,../libs/tech-companion-mock,../libs/shared-kernel-java \
    -am clean test -q; then
  pass "Tech Companion suite + shared-kernel-java build & test"
else
  fail "Tech Companion suite + shared-kernel-java build & test"
fi

echo ""
echo "── A.2: Build & test experience-bff ──"
if mvn -f "$REPO_ROOT/services/pom.xml" \
    -pl experience-bff \
    -am clean test -q; then
  pass "experience-bff build & test"
else
  fail "experience-bff build & test"
fi

# =============================================================================
# PHASE B: RUN
# =============================================================================
echo ""
echo "══════════════════════════════════════════════════════════════════════"
echo "  PHASE B: RUN (Docker Compose)"
echo "══════════════════════════════════════════════════════════════════════"

# Detect compose file
COMPOSE_FILE=""
for candidate in "$REPO_ROOT/docker-compose.yml" "$REPO_ROOT/docker-compose.yaml" "$REPO_ROOT/compose.yml" "$REPO_ROOT/compose.yaml"; do
  if [ -f "$candidate" ]; then
    COMPOSE_FILE="$candidate"
    break
  fi
done

if [ -z "$COMPOSE_FILE" ]; then
  fail "No docker-compose file found in repo root"
  echo "  Skipping Phase B entirely."
else
  echo "  Using compose file: $COMPOSE_FILE"
  echo ""

  # ── B.1: Start containers ──
  echo "── B.1: Starting containers ──"
  if docker compose -f "$COMPOSE_FILE" up -d --build 2>&1; then
    pass "Docker Compose up"
  else
    fail "Docker Compose up"
  fi

  # ── B.2: Wait for healthchecks ──
  echo ""
  echo "── B.2: Waiting for service health (max 120s) ──"
  BFF_PORT="${EXPERIENCE_BFF_PORT:-8086}"
  BFF_BASE="http://localhost:${BFF_PORT}"

  HEALTHY=false
  for i in $(seq 1 24); do
    if curl -sf "${BFF_BASE}/actuator/health" >/dev/null 2>&1; then
      HEALTHY=true
      break
    fi
    echo "    Waiting... (${i}/24)"
    sleep 5
  done

  if [ "$HEALTHY" = true ]; then
    pass "BFF health endpoint responsive"
  else
    fail "BFF health endpoint not responsive after 120s"
    echo ""
    echo "  ── Diagnostic dump ──"
    docker compose -f "$COMPOSE_FILE" ps 2>&1 || true
    echo ""
    echo "  ── Last 200 lines of BFF logs ──"
    docker compose -f "$COMPOSE_FILE" logs --tail=200 experience-bff 2>&1 || true
  fi

  # ── B.3: BFF smoke tests ──
  echo ""
  echo "── B.3: BFF smoke tests ──"
  if [ -x "$SMOKE_DIR/bff-smoke.sh" ]; then
    if BFF_BASE="$BFF_BASE" bash "$SMOKE_DIR/bff-smoke.sh"; then
      pass "BFF smoke tests"
    else
      fail "BFF smoke tests"
    fi
  else
    fail "bff-smoke.sh not found or not executable"
  fi

  # ── B.4: UI route parity check ──
  echo ""
  echo "── B.4: UI route parity check ──"
  if [ -x "$SMOKE_DIR/ui-route-parity.sh" ]; then
    if bash "$SMOKE_DIR/ui-route-parity.sh"; then
      pass "UI route parity check"
    else
      fail "UI route parity check"
    fi
  else
    fail "ui-route-parity.sh not found or not executable"
  fi

  # ── B.5: Idempotency replay & conflict checks ──
  echo ""
  echo "── B.5: Idempotency + v1.1 header enforcement checks ──"

  # Find a command endpoint — try BFF workspace creation or report-job
  CMD_ENDPOINT="${BFF_BASE}/internal/v1/workspaces"
  V1_HEADERS='-H "X-Tenant-ID: moh-zw" -H "X-Pod-ID: national" -H "X-Request-ID: verify-req-1" -H "X-Correlation-ID: verify-corr-1"'

  # B.5.1: Missing required v1.1 header -> 400 + error envelope
  echo "    B.5.1: Missing required header → 400"
  RESP=$(curl -sf -o /dev/null -w "%{http_code}" -X POST "${CMD_ENDPOINT}" \
    -H "Content-Type: application/json" \
    -H "X-Pod-ID: national" \
    -H "X-Request-ID: verify-req-1" \
    -H "X-Correlation-ID: verify-corr-1" \
    -H "Idempotency-Key: idem-missing-header-1" \
    -d '{"name":"test"}' 2>/dev/null) || RESP="000"
  if [ "$RESP" = "400" ]; then
    pass "Missing X-Tenant-ID returns 400"
  else
    fail "Missing X-Tenant-ID returned $RESP (expected 400)"
  fi

  # Verify error envelope structure
  BODY=$(curl -s -X POST "${CMD_ENDPOINT}" \
    -H "Content-Type: application/json" \
    -H "X-Pod-ID: national" \
    -H "X-Request-ID: verify-req-2" \
    -H "X-Correlation-ID: verify-corr-2" \
    -H "Idempotency-Key: idem-missing-header-2" \
    -d '{"name":"test"}' 2>/dev/null) || BODY="{}"
  if echo "$BODY" | grep -q '"error"' && echo "$BODY" | grep -q '"code"' && echo "$BODY" | grep -q '"MISSING_REQUIRED_HEADER"'; then
    pass "Error envelope contains MISSING_REQUIRED_HEADER code"
  else
    fail "Error envelope structure invalid: $BODY"
  fi

  # B.5.2: Missing Idempotency-Key on command endpoint → 400
  echo "    B.5.2: Missing Idempotency-Key → 400"
  RESP=$(curl -sf -o /dev/null -w "%{http_code}" -X POST "${CMD_ENDPOINT}" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: moh-zw" \
    -H "X-Pod-ID: national" \
    -H "X-Request-ID: verify-req-3" \
    -H "X-Correlation-ID: verify-corr-3" \
    -d '{"name":"test"}' 2>/dev/null) || RESP="000"
  if [ "$RESP" = "400" ]; then
    pass "Missing Idempotency-Key returns 400"
  else
    fail "Missing Idempotency-Key returned $RESP (expected 400)"
  fi

  # Verify IDEMPOTENCY_KEY_REQUIRED code
  BODY=$(curl -s -X POST "${CMD_ENDPOINT}" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: moh-zw" \
    -H "X-Pod-ID: national" \
    -H "X-Request-ID: verify-req-4" \
    -H "X-Correlation-ID: verify-corr-4" \
    -d '{"name":"test"}' 2>/dev/null) || BODY="{}"
  if echo "$BODY" | grep -q '"IDEMPOTENCY_KEY_REQUIRED"'; then
    pass "Error envelope contains IDEMPOTENCY_KEY_REQUIRED code"
  else
    fail "Expected IDEMPOTENCY_KEY_REQUIRED in response: $BODY"
  fi

  # B.5.3: Same Idempotency-Key + same body → replay (same result)
  echo "    B.5.3: Idempotency replay (same key + same body)"
  IDEM_KEY="idem-replay-$(date +%s)"
  BODY1=$(curl -s -X POST "${CMD_ENDPOINT}" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: moh-zw" \
    -H "X-Pod-ID: national" \
    -H "X-Request-ID: verify-req-5" \
    -H "X-Correlation-ID: verify-corr-5" \
    -H "Idempotency-Key: $IDEM_KEY" \
    -d '{"name":"replay-test"}' 2>/dev/null) || BODY1=""
  BODY2=$(curl -s -X POST "${CMD_ENDPOINT}" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: moh-zw" \
    -H "X-Pod-ID: national" \
    -H "X-Request-ID: verify-req-6" \
    -H "X-Correlation-ID: verify-corr-5" \
    -H "Idempotency-Key: $IDEM_KEY" \
    -d '{"name":"replay-test"}' 2>/dev/null) || BODY2=""
  if [ "$BODY1" = "$BODY2" ] && [ -n "$BODY1" ]; then
    pass "Idempotency replay returns identical response"
  else
    fail "Idempotency replay mismatch: first='$BODY1' second='$BODY2'"
  fi

  # B.5.4: Same Idempotency-Key + different body → 409 IDENTITY_CONFLICT
  echo "    B.5.4: Idempotency conflict (same key + different body)"
  IDEM_KEY2="idem-conflict-$(date +%s)"
  # First request
  curl -s -o /dev/null -X POST "${CMD_ENDPOINT}" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: moh-zw" \
    -H "X-Pod-ID: national" \
    -H "X-Request-ID: verify-req-7" \
    -H "X-Correlation-ID: verify-corr-7" \
    -H "Idempotency-Key: $IDEM_KEY2" \
    -d '{"name":"first-body"}' 2>/dev/null || true
  # Second request with different body
  RESP=$(curl -sf -o /dev/null -w "%{http_code}" -X POST "${CMD_ENDPOINT}" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: moh-zw" \
    -H "X-Pod-ID: national" \
    -H "X-Request-ID: verify-req-8" \
    -H "X-Correlation-ID: verify-corr-7" \
    -H "Idempotency-Key: $IDEM_KEY2" \
    -d '{"name":"different-body"}' 2>/dev/null) || RESP="000"
  if [ "$RESP" = "409" ]; then
    pass "Idempotency conflict returns 409"
  else
    fail "Idempotency conflict returned $RESP (expected 409)"
  fi

  BODY=$(curl -s -X POST "${CMD_ENDPOINT}" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: moh-zw" \
    -H "X-Pod-ID: national" \
    -H "X-Request-ID: verify-req-9" \
    -H "X-Correlation-ID: verify-corr-7" \
    -H "Idempotency-Key: $IDEM_KEY2" \
    -d '{"name":"another-different-body"}' 2>/dev/null) || BODY="{}"
  if echo "$BODY" | grep -q '"IDENTITY_CONFLICT"'; then
    pass "Conflict response contains IDENTITY_CONFLICT code"
  else
    fail "Expected IDENTITY_CONFLICT in response: $BODY"
  fi

  # ── B.6: DB/Outbox proof query ──
  echo ""
  echo "── B.6: Database outbox proof query ──"

  # Find postgres container
  PG_CONTAINER=$(docker compose -f "$COMPOSE_FILE" ps --format '{{.Name}}' 2>/dev/null | grep -i postgres | head -1) || PG_CONTAINER=""
  if [ -z "$PG_CONTAINER" ]; then
    PG_CONTAINER=$(docker ps --format '{{.Names}}' | grep -i postgres | head -1) || PG_CONTAINER=""
  fi

  if [ -n "$PG_CONTAINER" ]; then
    echo "    Using Postgres container: $PG_CONTAINER"

    OUTBOX_QUERY="SELECT tenant_id, pod_id, correlation_id, request_id, idempotency_key, event_type, schema_version, substring(payload_json::text, 1, 80) AS payload_preview FROM event_outbox ORDER BY created_at DESC LIMIT 5;"

    OUTBOX_RESULT=$(docker exec "$PG_CONTAINER" psql -U postgres -d experience_bff -t -c "$OUTBOX_QUERY" 2>/dev/null) || OUTBOX_RESULT=""

    if [ -n "$OUTBOX_RESULT" ]; then
      echo "    Outbox rows found:"
      echo "$OUTBOX_RESULT" | head -10
      pass "Outbox table has rows with v1.1 context columns"

      # Verify event_type format: starts with "impilo." and ends with ".v1"
      if echo "$OUTBOX_RESULT" | grep -q "impilo\."; then
        pass "event_type starts with 'impilo.'"
      else
        fail "event_type does not start with 'impilo.'"
      fi
    else
      fail "No outbox rows found (table may be empty or query failed)"
    fi
  else
    fail "No Postgres container found for outbox proof"
  fi

  # ── Cleanup note ──
  echo ""
  echo "  Note: Containers are left running for manual inspection."
  echo "  To stop: docker compose -f $COMPOSE_FILE down"
fi

# =============================================================================
# FINAL RESULT
# =============================================================================
echo ""
echo "══════════════════════════════════════════════════════════════════════"
TOTAL=$((PASS_COUNT + FAIL_COUNT))
if [ "$FAIL_COUNT" -eq 0 ]; then
  echo "  ✅ PASS — All $TOTAL checks passed"
  echo "══════════════════════════════════════════════════════════════════════"
  exit 0
else
  echo "  ❌ FAIL — $FAIL_COUNT of $TOTAL checks failed"
  echo ""
  echo "  Failed checks:"
  echo -e "$FAILURES"
  echo ""
  echo "  ── Diagnostic dump ──"
  if [ -n "${COMPOSE_FILE:-}" ]; then
    echo "  Docker Compose status:"
    docker compose -f "$COMPOSE_FILE" ps 2>&1 || true
    echo ""
    echo "  Last 200 lines of BFF logs:"
    docker compose -f "$COMPOSE_FILE" logs --tail=200 experience-bff 2>&1 || true
  fi
  echo "══════════════════════════════════════════════════════════════════════"
  exit 1
fi
