# Experience Platform — Online Verification Guide

## Overview

This document provides complete instructions for verifying the Experience Platform stage
on an online machine with Docker and Maven available.

The verification pack was produced because the original development environment lacked:
- Docker daemon (`/var/run/docker.sock` absent)
- Maven dependency resolution (DNS/Mirror unreachable)

All code is committed and complete. This guide enables deterministic verification.

## Prerequisites

| Requirement | Minimum Version | Check Command |
|---|---|---|
| Java | 21 | `java -version` |
| Maven | 3.9+ | `mvn --version` |
| Docker | 24+ | `docker --version` |
| Docker Compose | v2 (plugin) | `docker compose version` |
| curl | any | `curl --version` |
| psql | any (via Docker) | N/A (runs inside container) |

### Port Availability

Ensure these ports are free:
- `8086` — Experience BFF
- `5432` — PostgreSQL (or as defined in docker-compose.yml)
- `3000` — Experience UI (Next.js dev server, if applicable)

## Quick Start (Single Command)

```bash
./scripts/experience/verify-online.sh
```

This runs both phases automatically and prints a final PASS/FAIL banner.

## Manual Phase-by-Phase Execution

### Phase A: Build

**A.1 — Tech Companion Suite + Shared Kernel Java**

```bash
mvn -f services/pom.xml \
  -pl ../libs/tech-companion,../libs/tech-companion-harness,../libs/tech-companion-mock,../libs/shared-kernel-java \
  -am clean test
```

This verifies:
- `tech-companion` library compiles (v1.1 filters, error codes, headers)
- `tech-companion-harness` golden contract tests compile
- `tech-companion-mock` passes all golden contract tests (header enforcement, idempotency, federation, timeout)
- `shared-kernel-java` passes its standalone tests (event envelope, audit ledger)

**A.2 — Experience BFF**

```bash
mvn -f services/pom.xml -pl experience-bff -am clean test
```

This verifies:
- BFF compiles with all dependencies (tech-companion, shared-kernel-java)
- Integration tests pass (Testcontainers → PostgreSQL → Flyway → outbox)
- v1.1 header enforcement, idempotency, and outbox wiring are correct

### Phase B: Run (Docker Compose)

**B.1 — Start Containers**

```bash
docker compose -f docker-compose.yml up -d --build
```

**B.2 — Wait for Health**

```bash
# Poll BFF health until ready (max 120s)
for i in $(seq 1 24); do
  curl -sf http://localhost:8086/actuator/health && break
  sleep 5
done
```

**B.3 — BFF Smoke Tests**

```bash
BFF_BASE=http://localhost:8086 ./scripts/experience/smoke/bff-smoke.sh
```

**B.4 — UI Route Parity**

```bash
./scripts/experience/smoke/ui-route-parity.sh
```

**B.5 — Idempotency + Header Enforcement (curl)**

See `verify-online.sh` Phase B.5 for the full curl sequence, or run:

```bash
# Missing header → 400
curl -s -w "\n%{http_code}" -X POST http://localhost:8086/internal/v1/workspaces \
  -H "Content-Type: application/json" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: test-1" \
  -H "X-Correlation-ID: test-1" \
  -H "Idempotency-Key: test-key-1" \
  -d '{"name":"test"}'

# Missing Idempotency-Key → 400
curl -s -w "\n%{http_code}" -X POST http://localhost:8086/internal/v1/workspaces \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: test-2" \
  -H "X-Correlation-ID: test-2" \
  -d '{"name":"test"}'
```

**B.6 — Outbox Proof Query**

```bash
docker exec <postgres-container> psql -U postgres -d experience_bff -c \
  "SELECT tenant_id, pod_id, correlation_id, request_id, idempotency_key,
          event_type, schema_version, substring(payload_json::text, 1, 80)
   FROM event_outbox ORDER BY created_at DESC LIMIT 5;"
```

Expected: At least one row with `event_type` starting with `impilo.` and all v1.1 context columns populated.

## What the Verification Proves

| Check | Evidence |
|---|---|
| Reactor wiring | `shared-kernel-java` and `experience-bff` both compile in reactor |
| v1.1 header enforcement | Missing header → 400 + MISSING_REQUIRED_HEADER envelope |
| Idempotency | Missing key → 400; replay → same response; conflict → 409 |
| Timeout enforcement | Already-expired deadline → 504 + CLIENT_TIMEOUT_EXCEEDED |
| Federation authority | Private pod on national endpoint → 403 |
| Outbox pattern | Postgres `event_outbox` rows with v1.1 context columns |
| UI route parity | 98 routes in registry, ~96 page.tsx files (within tolerance) |
| Golden contract suite | tech-companion-mock passes all harness tests |

## Troubleshooting

### Maven build fails with dependency resolution errors
Ensure Maven Central is reachable. If behind a proxy:
```bash
export MAVEN_OPTS="-Dhttp.proxyHost=... -Dhttp.proxyPort=..."
```

### Docker containers unhealthy
```bash
docker compose ps
docker compose logs --tail=200 experience-bff
docker compose logs --tail=200 postgres
```

### Outbox table empty after smoke tests
The outbox is populated by successful command requests. Ensure BFF smoke tests pass first,
then re-query. Check BFF logs for SQL errors.
