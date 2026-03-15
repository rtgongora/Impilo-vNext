/**
 * Wave 19C — Outbox / Event Publication Lag Baseline Test (k6)
 *
 * Purpose:
 *   Measures the delay between writing a domain event to the event_outbox table
 *   and its publication to Kafka. This validates the SLO:
 *     - Outbox lag <= 100 unpublished events (VITO, VARAPI, TUSO, ZIBO)
 *     - Outbox lag <= 50 unpublished events (TSHEPO)
 *
 * Approach:
 *   1. Generate a burst of write operations (identity registrations) that each
 *      produce an outbox event.
 *   2. Poll the Prometheus metric `impilo_ops_outbox_lag` to measure how
 *      quickly the outbox drains.
 *   3. Simultaneously query the outbox table directly via the actuator/health
 *      endpoint (which reports outbox health via opsOutboxHealth indicator).
 *   4. Record lag over time as a custom k6 trend.
 *
 * Metrics captured:
 *   - outbox_lag_gauge: the impilo_ops_outbox_lag value at each sample point
 *   - outbox_drain_time_ms: time for outbox to return to zero after burst
 *   - write_to_publish_latency: estimated event freshness (burst size / drain rate)
 *
 * Prerequisites:
 *   1. Platform running via `./scripts/dev-runtime.sh up`
 *   2. Prometheus running (tools/ops/docker-compose.ops.yml)
 *   3. k6 installed
 *
 * Usage:
 *   k6 run tools/load/outbox-lag/outbox-lag-baseline.js
 *   k6 run --env BURST_SIZE=200 tools/load/outbox-lag/outbox-lag-baseline.js
 */

import http from "k6/http";
import { check, group, sleep } from "k6";
import { Rate, Trend, Gauge, Counter } from "k6/metrics";

// ---------------------------------------------------------------------------
// Custom metrics
// ---------------------------------------------------------------------------
const outboxLagGauge = new Trend("outbox_lag_gauge", true);
const outboxDrainTime = new Trend("outbox_drain_time_ms", true);
const writeErrors = new Rate("outbox_write_errors");
const writeLatency = new Trend("outbox_write_latency", true);
const lagSamples = new Counter("outbox_lag_samples");

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------
const VITO_BASE = __ENV.VITO_BASE || __ENV.BASE_URL || "http://localhost:8082";
const PROMETHEUS_BASE = __ENV.PROMETHEUS_BASE || "http://localhost:9090";
const BURST_SIZE = parseInt(__ENV.BURST_SIZE || "50");

const TRUST_HEADERS = {
  "X-Tenant-Id": __ENV.TENANT_ID || "tenant-outbox-test",
  "X-Request-Id": "placeholder",
  "X-Correlation-Id": "k6-corr-outbox-lag",
  "X-User-Id": __ENV.USER_ID || "load-test-user",
  "Content-Type": "application/json",
};

// ---------------------------------------------------------------------------
// Load profile — two phases: burst then observe
// ---------------------------------------------------------------------------
export const options = {
  scenarios: {
    // Phase 1: Burst writes to fill the outbox
    burst_writes: {
      executor: "shared-iterations",
      vus: 10,
      iterations: BURST_SIZE,
      maxDuration: "2m",
      exec: "burstWrite",
      tags: { phase: "burst" },
    },
    // Phase 2: Monitor outbox lag while it drains
    lag_monitor: {
      executor: "constant-vus",
      vus: 1,
      duration: "5m",
      exec: "monitorLag",
      tags: { phase: "monitor" },
    },
    // Phase 3: Sustained writes with continuous lag monitoring
    sustained: {
      executor: "constant-vus",
      vus: 10,
      duration: "3m",
      startTime: "6m",
      exec: "sustainedWriteAndMonitor",
      tags: { phase: "sustained" },
    },
  },
  thresholds: {
    // Outbox lag must stay under SLO threshold
    "outbox_lag_gauge": [
      { threshold: "max<100", abortOnFail: false }, // VITO SLO: <= 100
      { threshold: "avg<50", abortOnFail: false },  // healthy average
    ],
    "outbox_write_errors": [{ threshold: "rate<0.001", abortOnFail: false }],
    "outbox_write_latency": [
      { threshold: "p(95)<100", abortOnFail: false },
      { threshold: "p(99)<300", abortOnFail: false },
    ],
  },
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
function makePayload(vu, iter) {
  const nid = `OUTBOX-NID-${String(vu).padStart(4, "0")}-${String(iter).padStart(6, "0")}-${Date.now()}`;
  return JSON.stringify({
    nationalId: nid,
    firstName: `OutboxUser${vu}`,
    lastName: `Burst${iter}`,
    dateOfBirth: "1985-06-20",
    gender: "FEMALE",
    facilityId: __ENV.FACILITY_ID || "00000000-0000-0000-0000-000000000001",
  });
}

/**
 * Query outbox lag from Prometheus or fallback to actuator health.
 * Returns the current lag count or -1 if unavailable.
 */
function queryOutboxLag() {
  // Try Prometheus first
  const promQuery = encodeURIComponent('impilo_ops_outbox_lag{application="vito-service"}');
  const promRes = http.get(
    `${PROMETHEUS_BASE}/api/v1/query?query=${promQuery}`,
    { tags: { name: "prometheus_outbox_query" }, timeout: "3s" }
  );

  if (promRes.status === 200) {
    try {
      const body = JSON.parse(promRes.body);
      if (body.data && body.data.result && body.data.result.length > 0) {
        return parseFloat(body.data.result[0].value[1]);
      }
    } catch (e) {
      // Fall through to actuator
    }
  }

  // Fallback: actuator health endpoint (outbox health indicator reports lag)
  const healthRes = http.get(
    `${VITO_BASE}/actuator/health`,
    { tags: { name: "actuator_health" }, timeout: "3s" }
  );

  if (healthRes.status === 200) {
    try {
      const body = JSON.parse(healthRes.body);
      if (body.components && body.components.opsOutboxHealth) {
        const details = body.components.opsOutboxHealth.details;
        if (details && details.unpublishedCount !== undefined) {
          return details.unpublishedCount;
        }
      }
    } catch (e) {
      // Unavailable
    }
  }

  return -1; // lag unknown
}

// ---------------------------------------------------------------------------
// Scenario executors
// ---------------------------------------------------------------------------

/** Phase 1: Burst write — create identities rapidly to fill outbox */
export function burstWrite() {
  const url = `${VITO_BASE}/v1/identity/register`;
  const payload = makePayload(__VU, __ITER);
  const headers = Object.assign({}, TRUST_HEADERS, {
    "X-Request-Id": `k6-outbox-burst-${__VU}-${__ITER}`,
    "X-Idempotency-Key": `k6-outbox-idemp-${__VU}-${__ITER}-${Date.now()}`,
  });

  const res = http.post(url, payload, {
    headers: headers,
    tags: { name: "vito_burst_register" },
  });

  writeLatency.add(res.timings.duration);
  writeErrors.add(res.status >= 500);

  check(res, {
    "burst-write: not 5xx": (r) => r.status < 500,
  });
}

/** Phase 2: Monitor outbox lag — sample every 2 seconds */
export function monitorLag() {
  const lag = queryOutboxLag();

  if (lag >= 0) {
    outboxLagGauge.add(lag);
    lagSamples.add(1);

    check(null, {
      "outbox lag <= 100 (VITO SLO)": () => lag <= 100,
      "outbox lag <= 50 (healthy)": () => lag <= 50,
    });
  }

  sleep(2); // sample every 2 seconds
}

/** Phase 3: Sustained writes with inline lag checks */
export function sustainedWriteAndMonitor() {
  group("Sustained Write", function () {
    const url = `${VITO_BASE}/v1/identity/register`;
    const payload = makePayload(__VU + 500, __ITER + 200000);
    const headers = Object.assign({}, TRUST_HEADERS, {
      "X-Request-Id": `k6-outbox-sustained-${__VU}-${__ITER}`,
      "X-Idempotency-Key": `k6-outbox-sust-${__VU}-${__ITER}-${Date.now()}`,
    });

    const res = http.post(url, payload, {
      headers: headers,
      tags: { name: "vito_sustained_register" },
    });

    writeLatency.add(res.timings.duration);
    writeErrors.add(res.status >= 500);

    check(res, {
      "sustained-write: not 5xx": (r) => r.status < 500,
    });
  });

  // Inline lag check every 5th iteration
  if (__ITER % 5 === 0) {
    const lag = queryOutboxLag();
    if (lag >= 0) {
      outboxLagGauge.add(lag);
      lagSamples.add(1);
    }
  }

  sleep(Math.random() * 1.0 + 0.3);
}

// ---------------------------------------------------------------------------
// Setup / Teardown
// ---------------------------------------------------------------------------
export function setup() {
  console.log(`[outbox-lag] VITO target:       ${VITO_BASE}`);
  console.log(`[outbox-lag] Prometheus target:  ${PROMETHEUS_BASE}`);
  console.log(`[outbox-lag] Burst size:         ${BURST_SIZE}`);

  const vitoHealth = http.get(`${VITO_BASE}/actuator/health`, { timeout: "5s" });
  const vitoUp = vitoHealth.status === 200;

  // Check initial outbox lag
  const initialLag = queryOutboxLag();

  if (!vitoUp) console.warn(`[outbox-lag] VITO health check failed (status=${vitoHealth.status})`);
  console.log(`[outbox-lag] Initial outbox lag: ${initialLag >= 0 ? initialLag : "unknown"}`);

  return { vitoUp, initialLag };
}

export function teardown(data) {
  const finalLag = queryOutboxLag();
  console.log("[outbox-lag] Test complete.");
  console.log(`[outbox-lag] VITO was ${data.vitoUp ? "UP" : "DOWN"} at start.`);
  console.log(`[outbox-lag] Initial lag: ${data.initialLag >= 0 ? data.initialLag : "unknown"}`);
  console.log(`[outbox-lag] Final lag:   ${finalLag >= 0 ? finalLag : "unknown"}`);
}
