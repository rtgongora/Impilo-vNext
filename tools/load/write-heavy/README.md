# Write-Heavy / Idempotent Command Baseline Load Test

## Purpose

Validates write-path latency, availability, and idempotency guarantees under sustained and replay load.

**Endpoints under test:**
- `VITO POST /v1/identity/register` — patient identity registration (write + outbox event)
- `VITO POST /v1/identity/resolve` — identity resolution (idempotent read-via-POST)

## What This Proves

1. **Write throughput**: VITO can sustain concurrent identity registrations within SLO.
2. **Idempotency**: Re-submitting the same registration payload returns the same result (no duplicates).
3. **Mixed workload**: 70/30 read/write mix does not degrade latency below SLO.

## SLO Targets

| Metric       | Target      |
|-------------|-------------|
| Availability | >= 99.9%    |
| p95 Latency  | <= 100 ms   |
| p99 Latency  | <= 300 ms   |

## Prerequisites

1. Platform running: `./scripts/dev-runtime.sh up`
2. [k6](https://k6.io/docs/get-started/installation/) installed

## Usage

```bash
# Basic run
k6 run tools/load/write-heavy/write-heavy-baseline.js

# Custom VITO target
k6 run --env VITO_BASE=http://localhost:8082 tools/load/write-heavy/write-heavy-baseline.js

# Through Envoy gateway
k6 run --env BASE_URL=http://localhost:10000 tools/load/write-heavy/write-heavy-baseline.js

# JSON output for CI
k6 run --out json=results/write-heavy.json tools/load/write-heavy/write-heavy-baseline.js
```

## Load Profile

| Phase          | Duration | VUs    | Purpose                              |
|---------------|----------|--------|--------------------------------------|
| Create         | 3 min    | 20     | Steady write throughput baseline     |
| Replay         | 3 min    | 30     | Idempotent replay validation         |
| Mixed stress   | 5 min    | 10→100 | Combined read/write saturation test  |

## Custom Metrics

| Metric               | Type    | Description                            |
|----------------------|---------|----------------------------------------|
| `register_latency`   | Trend   | Identity registration duration         |
| `resolve_latency`    | Trend   | Identity resolution duration           |
| `register_errors`    | Rate    | Registration 5xx error rate            |
| `resolve_errors`     | Rate    | Resolution 5xx error rate              |
| `idempotent_replays` | Counter | Count of successful idempotent replays |
