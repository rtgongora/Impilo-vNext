/**
 * Wave 19C — Write-Heavy / Idempotent Command Baseline Load Test (k6)
 *
 * Target: VITO Identity Register — POST /v1/identity/register
 *         VITO Identity Resolve  — POST /v1/identity/resolve (idempotent read-via-POST)
 *
 * Why these endpoints:
 *   - /v1/identity/register is the canonical write-heavy command: creates a new
 *     patient identity (CPID issuance), triggers outbox event, and must be
 *     idempotent on retry (same NID → same CPID).
 *   - /v1/identity/resolve is a POST that resolves an identity — tests the
 *     idempotent query path (no side effects, safe to replay).
 *
 * Idempotency design:
 *   Each VU generates a deterministic identity payload based on VU ID and
 *   iteration counter. The first call creates the record; subsequent calls
 *   with the same payload must return the same CPID (idempotent replay).
 *   This validates the ops-instrumentation idempotency metrics:
 *     - impilo_ops_idempotency_replays
 *     - impilo_ops_idempotency_conflicts
 *
 * SLO targets (from Wave 19B):
 *   VITO: p95 <= 100 ms, p99 <= 300 ms, availability >= 99.9%
 *
 * Prerequisites:
 *   1. Platform running via `./scripts/dev-runtime.sh up`
 *   2. k6 installed
 *
 * Usage:
 *   k6 run tools/load/write-heavy/write-heavy-baseline.js
 */

import http from "k6/http";
import { check, group, sleep } from "k6";
import { Rate, Trend, Counter } from "k6/metrics";

// ---------------------------------------------------------------------------
// Custom metrics
// ---------------------------------------------------------------------------
const registerErrors = new Rate("register_errors");
const resolveErrors = new Rate("resolve_errors");
const registerLatency = new Trend("register_latency", true);
const resolveLatency = new Trend("resolve_latency", true);
const idempotentReplays = new Counter("idempotent_replays");

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------
const VITO_BASE = __ENV.VITO_BASE || __ENV.BASE_URL || "http://localhost:8082";

const TRUST_HEADERS = {
  "X-Tenant-Id": __ENV.TENANT_ID || "tenant-load-test",
  "X-Request-Id": "placeholder",
  "X-Correlation-Id": "k6-corr-write-heavy",
  "X-User-Id": __ENV.USER_ID || "load-test-user",
  "Content-Type": "application/json",
};

// ---------------------------------------------------------------------------
// Load profile
// ---------------------------------------------------------------------------
export const options = {
  scenarios: {
    // Phase 1: Steady writes — 20 VUs creating new identities
    create_phase: {
      executor: "constant-vus",
      vus: 20,
      duration: "3m",
      exec: "createIdentities",
      tags: { phase: "create" },
    },
    // Phase 2: Idempotent replays — same payloads re-submitted
    replay_phase: {
      executor: "constant-vus",
      vus: 30,
      duration: "3m",
      startTime: "4m",
      exec: "replayIdentities",
      tags: { phase: "replay" },
    },
    // Phase 3: Mixed read+write stress
    mixed_stress: {
      executor: "ramping-vus",
      startVUs: 10,
      stages: [
        { duration: "1m", target: 50 },
        { duration: "2m", target: 100 },
        { duration: "1m", target: 100 },
        { duration: "1m", target: 0 },
      ],
      startTime: "8m",
      exec: "mixedWorkload",
      tags: { phase: "mixed" },
    },
  },
  thresholds: {
    "register_latency": [
      { threshold: "p(95)<100", abortOnFail: false },
      { threshold: "p(99)<300", abortOnFail: false },
    ],
    "register_errors": [{ threshold: "rate<0.001", abortOnFail: false }],
    "resolve_latency": [
      { threshold: "p(95)<100", abortOnFail: false },
      { threshold: "p(99)<300", abortOnFail: false },
    ],
    "resolve_errors": [{ threshold: "rate<0.001", abortOnFail: false }],
    "http_req_failed": [{ threshold: "rate<0.001", abortOnFail: false }],
  },
};

// ---------------------------------------------------------------------------
// Payload generators
// ---------------------------------------------------------------------------
function makeRegisterPayload(vu, iter) {
  // Deterministic payload: same VU+iter always produces the same identity
  const nid = `LOADTEST-NID-${String(vu).padStart(4, "0")}-${String(iter).padStart(6, "0")}`;
  return JSON.stringify({
    nationalId: nid,
    firstName: `LoadUser${vu}`,
    lastName: `Iter${iter}`,
    dateOfBirth: "1990-01-15",
    gender: "MALE",
    facilityId: __ENV.FACILITY_ID || "00000000-0000-0000-0000-000000000001",
  });
}

function makeResolvePayload(vu, iter) {
  const nid = `LOADTEST-NID-${String(vu).padStart(4, "0")}-${String(iter % 100).padStart(6, "0")}`;
  return JSON.stringify({
    nationalId: nid,
  });
}

function requestHeaders(vu, iter) {
  return Object.assign({}, TRUST_HEADERS, {
    "X-Request-Id": `k6-write-${vu}-${iter}`,
    "X-Idempotency-Key": `k6-idemp-${vu}-${iter}`,
  });
}

// ---------------------------------------------------------------------------
// Scenario executors
// ---------------------------------------------------------------------------
export function createIdentities() {
  const url = `${VITO_BASE}/v1/identity/register`;
  const payload = makeRegisterPayload(__VU, __ITER);
  const headers = requestHeaders(__VU, __ITER);

  const res = http.post(url, payload, {
    headers: headers,
    tags: { name: "vito_identity_register" },
  });

  registerLatency.add(res.timings.duration);
  registerErrors.add(res.status >= 500);

  check(res, {
    "register: status not 5xx": (r) => r.status < 500,
    "register: status 2xx or 409 (conflict=idempotent)": (r) =>
      (r.status >= 200 && r.status < 300) || r.status === 409,
  });

  sleep(Math.random() * 1.5 + 0.3);
}

export function replayIdentities() {
  // Re-send the same payloads that were sent in create_phase
  // This should trigger idempotent replay (same NID → same result)
  const replayVU = (__VU % 20) + 1;
  const replayIter = __ITER % 50;

  const url = `${VITO_BASE}/v1/identity/register`;
  const payload = makeRegisterPayload(replayVU, replayIter);
  const headers = requestHeaders(replayVU, replayIter);

  const res = http.post(url, payload, {
    headers: headers,
    tags: { name: "vito_identity_replay" },
  });

  registerLatency.add(res.timings.duration);
  registerErrors.add(res.status >= 500);

  // 200 or 409 both count as successful idempotent replay
  const isReplay = res.status === 200 || res.status === 409;
  if (isReplay) {
    idempotentReplays.add(1);
  }

  check(res, {
    "replay: status not 5xx": (r) => r.status < 500,
    "replay: idempotent response": (r) =>
      (r.status >= 200 && r.status < 300) || r.status === 409,
  });

  sleep(Math.random() * 1.0 + 0.2);
}

export function mixedWorkload() {
  // 70% resolves (reads), 30% registers (writes)
  if (Math.random() < 0.7) {
    group("Resolve Identity", function () {
      const url = `${VITO_BASE}/v1/identity/resolve`;
      const payload = makeResolvePayload(__VU, __ITER);
      const headers = requestHeaders(__VU, __ITER);

      const res = http.post(url, payload, {
        headers: headers,
        tags: { name: "vito_identity_resolve" },
      });

      resolveLatency.add(res.timings.duration);
      resolveErrors.add(res.status >= 500);

      check(res, {
        "resolve: status not 5xx": (r) => r.status < 500,
      });
    });
  } else {
    group("Register Identity", function () {
      const url = `${VITO_BASE}/v1/identity/register`;
      // Use high iteration numbers to avoid collisions with create_phase
      const payload = makeRegisterPayload(__VU + 1000, __ITER + 100000);
      const headers = requestHeaders(__VU + 1000, __ITER + 100000);

      const res = http.post(url, payload, {
        headers: headers,
        tags: { name: "vito_identity_register_mixed" },
      });

      registerLatency.add(res.timings.duration);
      registerErrors.add(res.status >= 500);

      check(res, {
        "register-mixed: status not 5xx": (r) => r.status < 500,
      });
    });
  }

  sleep(Math.random() * 1.5 + 0.3);
}

// ---------------------------------------------------------------------------
// Setup / Teardown
// ---------------------------------------------------------------------------
export function setup() {
  console.log(`[write-heavy] VITO target: ${VITO_BASE}`);

  const health = http.get(`${VITO_BASE}/actuator/health`, { timeout: "5s" });
  const vitoUp = health.status === 200;

  if (!vitoUp) {
    console.warn(`[write-heavy] VITO health check failed (status=${health.status})`);
  }

  return { vitoUp };
}

export function teardown(data) {
  console.log("[write-heavy] Test complete.");
  console.log(`[write-heavy] VITO was ${data.vitoUp ? "UP" : "DOWN"} at start.`);
}
