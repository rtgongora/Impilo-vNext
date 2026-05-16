import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BFF_URL = __ENV.BFF_URL || 'http://localhost:48161';
const TENANT_ID = __ENV.TENANT_ID || 'tenant-core-tx-perf';
const TX_ID = __ENV.TX_ID || 'tx-1';

const errorRate = new Rate('errors');
const listLatency = new Trend('core_tx_list_latency', true);
const getLatency = new Trend('core_tx_get_latency', true);
const nextActionsLatency = new Trend('core_tx_next_actions_latency', true);

export const options = {
  stages: [
    { duration: '15s', target: 10 },
    { duration: '30s', target: 30 },
    { duration: '15s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<800', 'p(99)<1200'],
    errors: ['rate<0.05'],
    core_tx_list_latency: ['p(95)<500'],
    core_tx_get_latency: ['p(95)<500'],
    core_tx_next_actions_latency: ['p(95)<400'],
  },
};

function headers() {
  return {
    'X-Tenant-ID': TENANT_ID,
    'X-Pod-ID': 'national-spine',
    'X-Request-ID': `k6-${Date.now()}-${Math.random().toString(36).slice(2)}`,
    'X-Correlation-ID': `k6-corr-${Date.now()}`,
    'Content-Type': 'application/json',
  };
}

export default function () {
  group('CoreTransactionList', function () {
    const res = http.get(`${BFF_URL}/internal/v1/core-transactions?state=TRUST_CONTEXT_ESTABLISHED`, { headers: headers() });
    check(res, {
      'list status 200': (r) => r.status === 200,
      'list has items': (r) => {
        try {
          const body = JSON.parse(r.body);
          return body?.data?.items !== undefined;
        } catch (_) {
          return false;
        }
      },
    });
    listLatency.add(res.timings.duration);
    errorRate.add(res.status !== 200);
  });

  group('CoreTransactionGet', function () {
    const res = http.get(`${BFF_URL}/internal/v1/core-transactions/${TX_ID}`, { headers: headers() });
    check(res, {
      'get status 200': (r) => r.status === 200,
      'get has transaction': (r) => {
        try {
          const body = JSON.parse(r.body);
          return body?.data?.transaction?.id !== undefined;
        } catch (_) {
          return false;
        }
      },
    });
    getLatency.add(res.timings.duration);
    errorRate.add(res.status !== 200);
  });

  group('CoreTransactionNextActionsAlias', function () {
    const res = http.get(`${BFF_URL}/experience/core-transactions/${TX_ID}/next-actions`, { headers: headers() });
    check(res, {
      'next actions status 200': (r) => r.status === 200,
      'next actions has data': (r) => {
        try {
          const body = JSON.parse(r.body);
          return body?.data?.nextActions !== undefined;
        } catch (_) {
          return false;
        }
      },
    });
    nextActionsLatency.add(res.timings.duration);
    errorRate.add(res.status !== 200);
  });

  sleep(0.2);
}
