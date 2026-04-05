/**
 * Impilo vNext — Experience BFF Performance Test Suite (k6)
 *
 * Tests:
 *   1. Health check baseline latency
 *   2. Queue entries listing under load
 *   3. Encounter listing concurrent reads
 *   4. Finance bill listing throughput
 *   5. Coverage scheme listing
 *   6. Community groups listing
 *   7. Mixed workload simulation
 *
 * Usage:
 *   k6 run tests/performance/k6-experience-bff.js
 *   k6 run --vus 50 --duration 5m tests/performance/k6-experience-bff.js
 *
 * Environment variables:
 *   BFF_URL    — base URL (default: http://localhost:8160)
 *   TENANT_ID  — tenant header (default: tenant-moh-zw)
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BFF_URL = __ENV.BFF_URL || 'http://localhost:8160';
const TENANT_ID = __ENV.TENANT_ID || 'tenant-moh-zw';

// Custom metrics
const errorRate = new Rate('errors');
const queueLatency = new Trend('queue_latency', true);
const encounterLatency = new Trend('encounter_latency', true);
const financeLatency = new Trend('finance_latency', true);

// Test configuration
export const options = {
  stages: [
    { duration: '30s', target: 10 },   // Ramp up
    { duration: '2m', target: 50 },    // Sustained load
    { duration: '1m', target: 100 },   // Peak load
    { duration: '30s', target: 0 },    // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    errors: ['rate<0.05'],
    queue_latency: ['p(95)<300'],
    encounter_latency: ['p(95)<300'],
    finance_latency: ['p(95)<500'],
  },
};

function headers() {
  return {
    'X-Tenant-ID': TENANT_ID,
    'X-Pod-ID': 'national-spine',
    'X-Request-ID': `perf-${Date.now()}-${Math.random().toString(36).slice(2)}`,
    'X-Correlation-ID': `corr-${Date.now()}`,
    'Content-Type': 'application/json',
  };
}

export default function () {
  group('Health Check', function () {
    const res = http.get(`${BFF_URL}/actuator/health`);
    check(res, { 'health 200': (r) => r.status === 200 });
    errorRate.add(res.status !== 200);
  });

  group('Queue Entries', function () {
    const res = http.get(`${BFF_URL}/internal/v1/queue/entries`, { headers: headers() });
    check(res, {
      'queue 200': (r) => r.status === 200,
      'queue has data': (r) => JSON.parse(r.body).data !== undefined,
    });
    queueLatency.add(res.timings.duration);
    errorRate.add(res.status !== 200);
  });

  group('Encounters', function () {
    const res = http.get(`${BFF_URL}/internal/v1/encounters`, { headers: headers() });
    check(res, {
      'encounters 200': (r) => r.status === 200,
      'encounters has data': (r) => JSON.parse(r.body).data !== undefined,
    });
    encounterLatency.add(res.timings.duration);
    errorRate.add(res.status !== 200);
  });

  group('Finance Bills', function () {
    const res = http.get(`${BFF_URL}/internal/v1/finance/bills`, { headers: headers() });
    check(res, { 'finance 200': (r) => r.status === 200 });
    financeLatency.add(res.timings.duration);
    errorRate.add(res.status !== 200);
  });

  group('Coverage Schemes', function () {
    const res = http.get(`${BFF_URL}/internal/v1/coverage/schemes`, { headers: headers() });
    check(res, { 'coverage 200': (r) => r.status === 200 });
    errorRate.add(res.status !== 200);
  });

  group('Community Groups', function () {
    const res = http.get(`${BFF_URL}/internal/v1/community/groups`, { headers: headers() });
    check(res, { 'community 200': (r) => r.status === 200 });
    errorRate.add(res.status !== 200);
  });

  group('Queue Stats', function () {
    const res = http.post(
      `${BFF_URL}/internal/v1/queue/entries/stats`,
      JSON.stringify({ facilityId: '00000000-0000-0000-0000-000000000001' }),
      { headers: headers() }
    );
    check(res, { 'stats 200': (r) => r.status === 200 });
    errorRate.add(res.status !== 200);
  });

  sleep(0.5);
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: '  ', enableColors: true }),
    'tests/performance/results/summary.json': JSON.stringify(data, null, 2),
  };
}

function textSummary(data, opts) {
  const metrics = data.metrics;
  const lines = [
    '='.repeat(60),
    '  Impilo vNext — Performance Test Results',
    '='.repeat(60),
    '',
    `  Total Requests:  ${metrics.http_reqs?.values?.count || 'N/A'}`,
    `  Failed:          ${metrics.http_req_failed?.values?.passes || 0}`,
    `  Avg Duration:    ${(metrics.http_req_duration?.values?.avg || 0).toFixed(1)}ms`,
    `  P95 Duration:    ${(metrics.http_req_duration?.values?.['p(95)'] || 0).toFixed(1)}ms`,
    `  P99 Duration:    ${(metrics.http_req_duration?.values?.['p(99)'] || 0).toFixed(1)}ms`,
    '',
    `  Queue P95:       ${(metrics.queue_latency?.values?.['p(95)'] || 0).toFixed(1)}ms`,
    `  Encounter P95:   ${(metrics.encounter_latency?.values?.['p(95)'] || 0).toFixed(1)}ms`,
    `  Finance P95:     ${(metrics.finance_latency?.values?.['p(95)'] || 0).toFixed(1)}ms`,
    '',
    `  Error Rate:      ${((metrics.errors?.values?.rate || 0) * 100).toFixed(2)}%`,
    '',
    '='.repeat(60),
  ];
  return lines.join('\n');
}
