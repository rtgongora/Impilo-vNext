/**
 * Wave 19C — Read-Heavy Baseline Load Test (k6)
 *
 * Target: TUSO Facility Registry — GET /v1/internal/facilities/{id}
 *         VITO Client Registry  — GET /v1/clients/{healthId}
 *
 * Why these endpoints:
 *   - TUSO facility lookup is the most frequent read on the care path
 *     (every shift-start, every encounter, every referral resolves a facility).
 *   - VITO client lookup by healthId is the single most-called read across
 *     the entire platform (MPI resolution).
 *   Both sit on the Ring 0 care-critical path.
 *
 * SLO targets (from Wave 19B ring0-slo-sli-spec.md):
 *   TUSO: p95 <= 50 ms, p99 <= 150 ms, availability >= 99.9%
 *   VITO: p95 <= 100 ms, p99 <= 300 ms, availability >= 99.9%
 *
 * Prerequisites:
 *   1. Platform running via `./scripts/dev-runtime.sh up`
 *   2. Seed data loaded (at least 1 facility and 1 client record)
 *   3. k6 installed: https://k6.io/docs/get-started/installation/
 *
 * Usage:
 *   k6 run tools/load/read-heavy/read-heavy-baseline.js
 *   k6 run --env BASE_URL=http://localhost:10000 tools/load/read-heavy/read-heavy-baseline.js
 *   k6 run --env TUSO_FACILITY_ID=abc-123 --env VITO_HEALTH_ID=HID-001 tools/load/read-heavy/read-heavy-baseline.js
 */

import http from "k6/http";
import { check, group, sleep } from "k6";
import { Rate, Trend } from "k6/metrics";

// ---------------------------------------------------------------------------
// Custom metrics
// ---------------------------------------------------------------------------
const tusoErrors = new Rate("tuso_read_errors");
const vitoErrors = new Rate("vito_read_errors");
const tusoLatency = new Trend("tuso_read_latency", true);
const vitoLatency = new Trend("vito_read_latency", true);

// ---------------------------------------------------------------------------
// Configuration — override with --env flags
// ---------------------------------------------------------------------------
const TUSO_BASE = __ENV.TUSO_BASE || __ENV.BASE_URL || "http://localhost:8084";
const VITO_BASE = __ENV.VITO_BASE || __ENV.BASE_URL || "http://localhost:8082";

// IDs to resolve — callers MUST provide real seeded IDs for meaningful results.
// Defaults are placeholder UUIDs; the test will still run (expect 404s which
// count as non-5xx and therefore do not violate availability SLOs).
const TUSO_FACILITY_ID = __ENV.TUSO_FACILITY_ID || "00000000-0000-0000-0000-000000000001";
const VITO_HEALTH_ID = __ENV.VITO_HEALTH_ID || "HID-000001";

// Trust headers — in production, Envoy injects these via ext_authz.
// For direct service testing we supply synthetic values.
const TRUST_HEADERS = {
  "X-Tenant-Id": __ENV.TENANT_ID || "tenant-load-test",
  "X-Request-Id": "k6-read-heavy-${__VU}-${__ITER}",
  "X-Correlation-Id": "k6-corr-read-heavy",
  "X-User-Id": __ENV.USER_ID || "load-test-user",
  "Content-Type": "application/json",
};

// ---------------------------------------------------------------------------
// Load profile — stages model a realistic daily pattern
// ---------------------------------------------------------------------------
export const options = {
  scenarios: {
    // Steady-state baseline: constant 50 VUs for 5 minutes
    steady_state: {
      executor: "constant-vus",
      vus: 50,
      duration: "5m",
      tags: { profile: "steady" },
    },
    // Ramp-up stress: 10 → 200 VUs over 3 minutes, hold 2 minutes, ramp down
    stress: {
      executor: "ramping-vus",
      startVUs: 10,
      stages: [
        { duration: "1m", target: 100 },
        { duration: "1m", target: 200 },
        { duration: "2m", target: 200 },
        { duration: "1m", target: 0 },
      ],
      startTime: "6m", // starts after steady_state
      tags: { profile: "stress" },
    },
  },
  thresholds: {
    // TUSO SLO thresholds
    "tuso_read_latency": [
      { threshold: "p(95)<50", abortOnFail: false },
      { threshold: "p(99)<150", abortOnFail: false },
    ],
    "tuso_read_errors": [{ threshold: "rate<0.001", abortOnFail: false }], // 99.9%

    // VITO SLO thresholds
    "vito_read_latency": [
      { threshold: "p(95)<100", abortOnFail: false },
      { threshold: "p(99)<300", abortOnFail: false },
    ],
    "vito_read_errors": [{ threshold: "rate<0.001", abortOnFail: false }], // 99.9%

    // Global HTTP thresholds
    "http_req_duration": [
      { threshold: "p(95)<200", abortOnFail: false },
    ],
    "http_req_failed": [{ threshold: "rate<0.001", abortOnFail: false }],
  },
};

// ---------------------------------------------------------------------------
// Default function — executed per VU iteration
// ---------------------------------------------------------------------------
export default function () {
  group("TUSO Facility Read", function () {
    const url = `${TUSO_BASE}/v1/internal/facilities/${TUSO_FACILITY_ID}`;
    const res = http.get(url, { headers: TRUST_HEADERS, tags: { name: "tuso_facility_get" } });

    tusoLatency.add(res.timings.duration);
    tusoErrors.add(res.status >= 500);

    check(res, {
      "TUSO status is not 5xx": (r) => r.status < 500,
      "TUSO p95 < 50ms": (r) => r.timings.duration < 50,
    });
  });

  group("VITO Client Read", function () {
    const url = `${VITO_BASE}/v1/clients/${VITO_HEALTH_ID}`;
    const res = http.get(url, { headers: TRUST_HEADERS, tags: { name: "vito_client_get" } });

    vitoLatency.add(res.timings.duration);
    vitoErrors.add(res.status >= 500);

    check(res, {
      "VITO status is not 5xx": (r) => r.status < 500,
      "VITO p95 < 100ms": (r) => r.timings.duration < 100,
    });
  });

  // Simulate realistic think time between iterations
  sleep(Math.random() * 2 + 0.5); // 0.5–2.5s
}

// ---------------------------------------------------------------------------
// Setup — verify targets are reachable before running full load
// ---------------------------------------------------------------------------
export function setup() {
  console.log(`[read-heavy] TUSO target: ${TUSO_BASE}`);
  console.log(`[read-heavy] VITO target: ${VITO_BASE}`);
  console.log(`[read-heavy] Facility ID: ${TUSO_FACILITY_ID}`);
  console.log(`[read-heavy] Health ID:   ${VITO_HEALTH_ID}`);

  const tusoHealth = http.get(`${TUSO_BASE}/actuator/health`, { timeout: "5s" });
  const vitoHealth = http.get(`${VITO_BASE}/actuator/health`, { timeout: "5s" });

  const tusoUp = tusoHealth.status === 200;
  const vitoUp = vitoHealth.status === 200;

  if (!tusoUp) console.warn(`[read-heavy] TUSO health check failed (status=${tusoHealth.status})`);
  if (!vitoUp) console.warn(`[read-heavy] VITO health check failed (status=${vitoHealth.status})`);

  return { tusoUp, vitoUp };
}

// ---------------------------------------------------------------------------
// Teardown — summary
// ---------------------------------------------------------------------------
export function teardown(data) {
  console.log("[read-heavy] Test complete.");
  console.log(`[read-heavy] TUSO was ${data.tusoUp ? "UP" : "DOWN"} at start.`);
  console.log(`[read-heavy] VITO was ${data.vitoUp ? "UP" : "DOWN"} at start.`);
}
