# Read-Heavy Baseline Load Test

## Purpose

Validates Ring 0 read-path latency and availability SLOs under sustained and peak load.

**Endpoints under test:**
- `TUSO GET /v1/internal/facilities/{id}` — facility registry lookup (care-path critical)
- `VITO GET /v1/clients/{healthId}` — MPI client resolution (most-called read on the platform)

## SLO Targets

| Service | Availability | p95 Latency | p99 Latency |
|---------|:------------:|:-----------:|:-----------:|
| TUSO    | >= 99.9%     | <= 50 ms    | <= 150 ms   |
| VITO    | >= 99.9%     | <= 100 ms   | <= 300 ms   |

## Prerequisites

1. Platform running: `./scripts/dev-runtime.sh up`
2. Seed data loaded (at minimum 1 facility + 1 client)
3. [k6](https://k6.io/docs/get-started/installation/) installed

## Usage

```bash
# Minimal run (uses defaults)
k6 run tools/load/read-heavy/read-heavy-baseline.js

# With real seed IDs
k6 run \
  --env TUSO_FACILITY_ID=<uuid> \
  --env VITO_HEALTH_ID=<health-id> \
  tools/load/read-heavy/read-heavy-baseline.js

# Against Envoy gateway
k6 run \
  --env BASE_URL=http://localhost:10000 \
  --env TUSO_FACILITY_ID=<uuid> \
  --env VITO_HEALTH_ID=<health-id> \
  tools/load/read-heavy/read-heavy-baseline.js

# JSON output for CI pipeline
k6 run --out json=results/read-heavy.json tools/load/read-heavy/read-heavy-baseline.js
```

## Load Profile

| Phase        | Duration | VUs     | Purpose                          |
|-------------|----------|---------|----------------------------------|
| Steady-state | 5 min    | 50      | Baseline latency measurement     |
| Ramp-up      | 2 min    | 10→200  | Find saturation point            |
| Hold peak    | 2 min    | 200     | Sustained peak behavior          |
| Ramp-down    | 1 min    | 200→0   | Recovery observation             |

## Interpreting Results

- **Pass**: All thresholds green — latency and error rates within SLO.
- **Warn**: Thresholds yellow during stress phase only — acceptable if steady-state passes.
- **Fail**: Steady-state phase violates SLO thresholds — requires investigation.

## Custom Metrics

| Metric              | Type  | Description                    |
|---------------------|-------|--------------------------------|
| `tuso_read_latency` | Trend | TUSO facility GET duration     |
| `vito_read_latency` | Trend | VITO client GET duration       |
| `tuso_read_errors`  | Rate  | TUSO 5xx error rate            |
| `vito_read_errors`  | Rate  | VITO 5xx error rate            |
