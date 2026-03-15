# Outbox / Event Publication Lag Baseline Test

## Purpose

Measures the delay between writing domain events to the `event_outbox` table and their publication to Kafka. Validates that the outbox publisher keeps up with write throughput and that event freshness SLOs are met.

## How It Works

1. **Burst phase**: Rapidly creates identity registrations (each producing an outbox event) to intentionally fill the outbox.
2. **Monitor phase**: Polls `impilo_ops_outbox_lag` via Prometheus (or actuator health fallback) every 2 seconds to observe drain rate.
3. **Sustained phase**: Continuous writes with inline lag sampling to verify steady-state behavior.

## SLO Targets

| Service | Outbox Lag Threshold | Freshness      |
|---------|:--------------------:|:--------------:|
| TSHEPO  | <= 50 events         | N/A            |
| VITO    | <= 100 events        | <= 5 s         |
| VARAPI  | <= 100 events        | <= 30 s        |
| TUSO    | <= 100 events        | <= 24 h        |
| ZIBO    | <= 100 events        | <= 24 h        |

## Prerequisites

1. Platform running: `./scripts/dev-runtime.sh up`
2. Observability stack: `docker compose -f tools/ops/docker-compose.ops.yml up -d`
3. [k6](https://k6.io/docs/get-started/installation/) installed

## Usage

```bash
# Basic run (50 event burst)
k6 run tools/load/outbox-lag/outbox-lag-baseline.js

# Larger burst
k6 run --env BURST_SIZE=200 tools/load/outbox-lag/outbox-lag-baseline.js

# Custom Prometheus endpoint
k6 run \
  --env PROMETHEUS_BASE=http://localhost:9090 \
  --env BURST_SIZE=100 \
  tools/load/outbox-lag/outbox-lag-baseline.js

# JSON output
k6 run --out json=results/outbox-lag.json tools/load/outbox-lag/outbox-lag-baseline.js
```

## Custom Metrics

| Metric                  | Type    | Description                                      |
|------------------------|---------|--------------------------------------------------|
| `outbox_lag_gauge`     | Trend   | Sampled `impilo_ops_outbox_lag` values over time  |
| `outbox_drain_time_ms` | Trend   | Time for outbox to return to zero after burst     |
| `outbox_write_latency` | Trend   | Write operation duration                          |
| `outbox_write_errors`  | Rate    | Write 5xx error rate                              |
| `outbox_lag_samples`   | Counter | Number of lag measurements taken                  |

## Interpreting Results

- **Pass**: `outbox_lag_gauge` max stays below 100 and averages below 50.
- **Warn**: Lag spikes above 100 during burst but recovers within 30 seconds.
- **Fail**: Lag remains above 100 in the sustained phase — outbox publisher cannot keep up.
