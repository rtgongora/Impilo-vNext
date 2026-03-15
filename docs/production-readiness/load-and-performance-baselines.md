# Wave 19C — Load & Performance Baselines

> Date: 2026-03-15
> Scope: Ring 0 services (TSHEPO, VITO, VARAPI, TUSO, ZIBO) — care-path load baselines
> Branch: `claude/review-project-manifest-jb5O0`
> Prerequisites: [Wave 19A Baseline Inventory](wave19a-baseline-inventory.md), [Wave 19B SLI/SLO Spec](ring0-slo-sli-spec.md)

---

## 1. Overview

Wave 19C delivers runnable load/performance harnesses that establish baseline measurements for three critical dimensions:

| Dimension | Harness | What It Proves |
|-----------|---------|----------------|
| Read-heavy throughput | `tools/load/read-heavy/` | Ring 0 read-path latency and availability under sustained + peak load |
| Write-heavy + idempotency | `tools/load/write-heavy/` | Write throughput, idempotent replay correctness, mixed-workload stability |
| Outbox/event publication lag | `tools/load/outbox-lag/` | Outbox publisher keeps pace with write throughput; event freshness SLO met |

Additionally, Wave 19C provides a **data-plane non-blocking verification** script that proves care-path operations remain available when data-platform components are degraded:

| Verification | Script | What It Proves |
|-------------|--------|----------------|
| Data-plane isolation | `scripts/production-readiness/verify-data-plane-nonblocking.sh` | TUSO/VITO care-path commands succeed when data-ingestion/pipeline/warehouse are paused |

---

## 2. Test Tool: k6

All load harnesses use [k6](https://k6.io/) (Grafana Labs), chosen for:

- **JavaScript ES6 scripting** — readable, maintainable, version-controlled test logic
- **Built-in threshold engine** — SLO assertions evaluated by k6, no external tooling needed
- **Prometheus remote-write** — results can feed directly into the observability stack
- **CLI-native** — runs in CI pipelines without browser or GUI dependencies
- **Custom metrics** — per-endpoint latency/error tracking via `Trend`, `Rate`, `Counter`

### Installation

```bash
# macOS
brew install k6

# Linux (Debian/Ubuntu)
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
  --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D68
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" \
  | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update && sudo apt-get install k6

# Docker
docker run --rm -i grafana/k6 run - <tools/load/read-heavy/read-heavy-baseline.js
```

---

## 3. Read-Heavy Baseline

### 3.1 Endpoints Under Test

| Endpoint | Service | Purpose | Care-Path Role |
|----------|---------|---------|---------------|
| `GET /v1/internal/facilities/{id}` | TUSO (8084) | Facility registry lookup | Every shift-start, encounter, and referral resolves a facility |
| `GET /v1/clients/{healthId}` | VITO (8082) | MPI client resolution | Most-called read across the platform |

### 3.2 SLO Targets

| Service | Availability | p95 Latency | p99 Latency |
|---------|:------------:|:-----------:|:-----------:|
| TUSO | >= 99.9% | <= 50 ms | <= 150 ms |
| VITO | >= 99.9% | <= 100 ms | <= 300 ms |

### 3.3 Load Profile

| Phase | Duration | VUs | Purpose |
|-------|----------|-----|---------|
| Steady-state | 5 min | 50 | Baseline latency under normal load |
| Ramp-up | 2 min | 10 → 200 | Find saturation point |
| Hold peak | 2 min | 200 | Sustained peak behavior |
| Ramp-down | 1 min | 200 → 0 | Recovery observation |

### 3.4 Running

```bash
k6 run \
  --env TUSO_FACILITY_ID=<uuid> \
  --env VITO_HEALTH_ID=<health-id> \
  tools/load/read-heavy/read-heavy-baseline.js
```

### 3.5 Custom Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `tuso_read_latency` | Trend | Facility GET duration (ms) |
| `vito_read_latency` | Trend | Client GET duration (ms) |
| `tuso_read_errors` | Rate | TUSO 5xx error rate |
| `vito_read_errors` | Rate | VITO 5xx error rate |

---

## 4. Write-Heavy / Idempotent Command Baseline

### 4.1 Endpoints Under Test

| Endpoint | Service | Purpose | Care-Path Role |
|----------|---------|---------|---------------|
| `POST /v1/identity/register` | VITO (8082) | Patient identity registration | CPID issuance — write + outbox event |
| `POST /v1/identity/resolve` | VITO (8082) | Identity resolution | Idempotent read-via-POST |

### 4.2 Idempotency Verification

The test generates deterministic payloads (`nationalId` = f(VU, iteration)). The first call creates a record; replays with the same `nationalId` must return the same CPID without creating duplicates. This exercises:

- `impilo_ops_idempotency_replays` counter
- `impilo_ops_idempotency_conflicts` counter
- `X-Idempotency-Key` header contract

### 4.3 Load Profile

| Phase | Duration | VUs | Purpose |
|-------|----------|-----|---------|
| Create | 3 min | 20 | Steady write throughput |
| Replay | 3 min | 30 | Idempotent replay validation |
| Mixed stress | 5 min | 10 → 100 | 70/30 read/write saturation |

### 4.4 Running

```bash
k6 run tools/load/write-heavy/write-heavy-baseline.js
```

---

## 5. Outbox / Event Publication Lag Baseline

### 5.1 Measurement Approach

1. **Burst**: Rapidly create `BURST_SIZE` identity registrations (each producing an outbox event)
2. **Monitor**: Poll `impilo_ops_outbox_lag` via Prometheus every 2 seconds
3. **Sustained**: Continuous writes with inline lag sampling

### 5.2 SLO Targets

| Service | Outbox Lag Threshold | Freshness |
|---------|:--------------------:|:---------:|
| TSHEPO | <= 50 events | N/A |
| VITO | <= 100 events | <= 5 s |
| VARAPI | <= 100 events | <= 30 s |
| TUSO | <= 100 events | <= 24 h |
| ZIBO | <= 100 events | <= 24 h |

### 5.3 Data Sources

| Source | Query | Fallback |
|--------|-------|----------|
| Prometheus | `impilo_ops_outbox_lag{application="vito-service"}` | — |
| Actuator health | `GET /actuator/health` → `components.opsOutboxHealth.details.unpublishedCount` | When Prometheus unavailable |
| Direct SQL | `SELECT COUNT(*) FROM vito.event_outbox WHERE published_at IS NULL` | For verification script |

### 5.4 Running

```bash
k6 run --env BURST_SIZE=100 tools/load/outbox-lag/outbox-lag-baseline.js
```

---

## 6. Data-Plane Non-Blocking Verification

### 6.1 What It Proves

The Impilo architecture isolates care-path services from data-platform services via the outbox → Kafka → data-ingestion pipeline. There is **no synchronous dependency** from care-path to data-platform. This script proves that assumption holds under real conditions.

### 6.2 Test Flow

| Phase | Action | Assertion |
|-------|--------|-----------|
| 0 | Environment readiness | TUSO, VITO, TSHEPO all healthy |
| 1 | Baseline reads/writes | Care-path operations succeed with data platform UP |
| 2 | Degrade data platform | Pause `data-ingestion`, `data-pipeline`, `data-warehouse` containers |
| 3 | Care-path operations | 20 TUSO reads + 20 VITO reads + 5 VITO writes — all must succeed |
| 4 | Outbox accumulation | Unpublished events increase (expected — events queue, don't block) |
| 5 | Restore data platform | Unpause containers |
| 6 | Post-restoration | Care-path still works; outbox begins draining |

### 6.3 Running

```bash
./scripts/production-readiness/verify-data-plane-nonblocking.sh

# With specific seed data IDs
TUSO_FACILITY_ID=<uuid> VITO_HEALTH_ID=<id> \
  ./scripts/production-readiness/verify-data-plane-nonblocking.sh
```

### 6.4 Exit Codes

| Code | Meaning |
|------|---------|
| 0 | All checks pass — care path is independent of data plane |
| 1 | Care-path operations failed during data-plane degradation |
| 2 | Environment not ready (services not running) |

---

## 7. CI Integration

All harnesses produce machine-readable output for CI pipeline integration:

```bash
# k6 JSON output
k6 run --out json=results/read-heavy.json tools/load/read-heavy/read-heavy-baseline.js
k6 run --out json=results/write-heavy.json tools/load/write-heavy/write-heavy-baseline.js
k6 run --out json=results/outbox-lag.json tools/load/outbox-lag/outbox-lag-baseline.js

# k6 Prometheus remote-write (if Prometheus is configured with remote-write receiver)
k6 run --out experimental-prometheus-rw tools/load/read-heavy/read-heavy-baseline.js
```

The verification script exits with code 0/1/2 and is directly usable as a CI gate step.

---

## 8. Baseline Recording Protocol

After running each harness against a representative environment:

1. **Capture** the k6 summary output (stdout) and JSON results
2. **Record** the environment configuration (VMs, CPU, RAM, PostgreSQL version, connection pool size)
3. **Tag** the results with the git commit SHA and environment name
4. **Store** results in `results/wave19c/` (not committed to git — add to `.gitignore`)
5. **Compare** future runs against these baselines to detect regressions

---

## 9. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-03-15 | Wave 19C | Initial load and performance baselines |
